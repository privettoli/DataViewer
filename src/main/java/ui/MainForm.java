package ui;

import brain.BytesToStringConverter;
import brain.Constants;
import brain.DatabaseWorker;
import brain.Row;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.apache.hadoop.hbase.HConstants;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class MainForm extends JFrame {
    private static String OS = System.getProperty("os.name").toLowerCase();
    private MainForm thisFrame = this;
    private JPanel mainPanel,
            choosePanel,
            viewPanel;
    private JList<String> tablesJList,
            rowsJList,
            familiesJList;
    private JTable jTable;
    private JButton backButton,
            goButton;
    private JRadioButton asciiRadioButton,
            hexRadioButton,
            utf8RadioButton,
            windows1251RadioButton;
    //private JPanel searchPanel;
    private JButton refreshButton;
    private JTextField searchRowTextField;
    private JProgressBar progressBar;
    private JProgressBar progressBarForRows;
    private DatabaseWorker databaseWorker;
    private ResourceBundle resourceBundle = ResourceBundle.getBundle("MainFormResources");
    private Logger logger = Logger.getLogger(this.getClass());
    private DefaultTableModel tableData;
    private Map<String, DefaultListModel<String>> listModels = new HashMap<>();
    private JMenuBar jMenuBar = new JMenuBar();
    private JMenu jMenuSettings = new JMenu(resourceBundle.getString("settings"));
    private JMenuItem
            jMenuItemChangeHbaseSettings = new JMenuItem(resourceBundle.getString("changeHbaseSettings"));
    private ChangeSettings changeSettingsForm;
    private String[] tablesNames = null;
    private String choosedTable;
    private byte[] selectedRow;
    private String selectedEncoding = HConstants.UTF8_ENCODING;
    private byte[] selectedFamily;

    // Entry-point of this window
    public void start() {
        try {
            Properties log4jProps = new Properties();
            log4jProps.load(getClass().getClassLoader().getResourceAsStream("log4j.properties"));
            PropertyConfigurator.configure(log4jProps);
        } catch (IOException e) {
            BasicConfigurator.configure();
            logger.error(e);
        }
        databaseWorker = new DatabaseWorker();
        databaseWorker.loadProperties();
        logger.info("DatabaseWorker created");
        initLookAndFeel();
        logger.info("InitLookAndFeel ended");
        try {
            initUI();
        } catch (IOException e) {
            logger.error(e);
        }
        logger.info("InitUI ended");
        initUIHandlers();
    }

    private void initUI() throws IOException {
        setContentPane(this.mainPanel);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle(resourceBundle.getString("appTitle") + " " + resourceBundle.getString("appVersion"));

        jTable.setAutoCreateColumnsFromModel(true);
        tableData = new DefaultTableModel();
        jTable.setModel(tableData);
        //SearchTextField.setJTable(jTable);
        //SearchTextField.setTableData(tableData);
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(asciiRadioButton);
        buttonGroup.add(hexRadioButton);
        buttonGroup.add(utf8RadioButton);
        buttonGroup.add(windows1251RadioButton);
        utf8RadioButton.setSelected(true);

        jMenuSettings.add(jMenuItemChangeHbaseSettings);
        jMenuBar.add(jMenuSettings);
        setJMenuBar(jMenuBar);

        refreshButton.setIcon(new ImageIcon(MainForm.class.getClassLoader().getResource("refresh-icon.png")));

        // Move the window to center of the screen
        setLocationRelativeTo(null);
        pack();
        setVisible(true);
    }

    private void initLookAndFeel() {
        String myLookAndFeel = UIManager.getSystemLookAndFeelClassName();
        if (OS.contains("win")) {
            System.out.println("This is windows");
            myLookAndFeel = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
        } else if (OS.contains("linux")) {
            System.out.println("This is linux");
            myLookAndFeel = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
        } else if (OS.contains("mac")) {
            System.out.println("this is mac");
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "WikiTeX");
        } else System.out.println("I don't know what this system is!!!\n" + OS);
        try {
            UIManager.setLookAndFeel(myLookAndFeel);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            logger.error(e);
        }
    }

    private void initUIHandlers() {
        tablesJList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getSource() != tablesJList || ((JList<String>) e.getSource()).getSelectedValue() == null)
                    return;
                choosedTable = ((JList<String>) e.getSource()).getSelectedValue();

                tablesJList.setEnabled(false);
                try {
                    final String[] familiesNames = databaseWorker.getFamilies(choosedTable);
                    familiesJList.setListData(familiesNames);

                    DefaultListModel<String> choosedListModel = listModels.get(choosedTable);
                    if (choosedListModel != null) {
                        rowsJList.setModel(choosedListModel);
                    } else {
                        final DefaultListModel<String> finalChoosedListModel = new DefaultListModel<>();
                        listModels.put(choosedTable, finalChoosedListModel);
                        rowsJList.setModel(finalChoosedListModel);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    databaseWorker.fillRowsToListModel(choosedTable, finalChoosedListModel, selectedEncoding, progressBar);
                                } catch (IOException e1) {
                                    logger.error(e1);
                                }
                            }
                        }).start();
                    }
                } catch (IOException e1) {
                    logger.error(e1);
                }
                rowsJList.setSelectedIndex(0);
                tablesJList.setEnabled(true);
            }
        });

        rowsJList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() || e.getSource() != rowsJList)
                    return;
                rowsJList.setEnabled(false);
                try {
                    selectedRow = selectedEncoding.equals(Constants.HEX) ? BytesToStringConverter.toBytes(((JList<String>) e.getSource()).getSelectedValue()) : rowsJList.getSelectedValue().getBytes(selectedEncoding);
                } catch (Exception e1) {
                    logger.error(e1);
                }
                if (viewPanel.isVisible())
                    loadDataToJTable();
                if (familiesJList.getSelectedIndex() != -1) {
                    goButton.setEnabled(true);
                } else goButton.setEnabled(false);
                rowsJList.setEnabled(true);
            }
        });

        familiesJList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() || e.getSource() != familiesJList)
                    return;
                byte[] bytes = selectedFamily;
                try {
                    selectedFamily = familiesJList.getSelectedValue().getBytes();
                } catch (Exception e1) {
                    selectedFamily = bytes;
                }
                if (rowsJList.getSelectedIndex() != -1) {
                    goButton.setEnabled(true);
                } else goButton.setEnabled(false);
            }
        });

        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                choosePanel.setVisible(true);
                viewPanel.setVisible(false);
            }
        });

        goButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadDataToJTable();
                choosePanel.setVisible(false);
                viewPanel.setVisible(true);
            }
        });

        ActionListener encodingWasChangedListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final String lastEncoding = selectedEncoding;
                if (e.getSource() == utf8RadioButton)
                    selectedEncoding = Constants.UTF8;
                else if (e.getSource() == hexRadioButton)
                    selectedEncoding = Constants.HEX;
                else if (e.getSource() == windows1251RadioButton)
                    selectedEncoding = Constants.CP1251;
                else if (e.getSource() == asciiRadioButton)
                    selectedEncoding = Constants.AHCII;
                for (String tableName : listModels.keySet()) {
                    final DefaultListModel<String> rows = listModels.get(tableName);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            progressBarForRows.setVisible(true);
                            progressBarForRows.setIndeterminate(true);
                            progressBarForRows.setMaximum(rows.getSize());
                            for (int i = 0; i < rows.getSize(); i++) {
                                try {
                                    byte[] row = lastEncoding.equals(Constants.HEX) ? BytesToStringConverter.toBytes(rows.getElementAt(i)) : rows.getElementAt(i).getBytes(lastEncoding);
                                    rows.set(i, BytesToStringConverter.toString(row, selectedEncoding));
                                } catch (Exception e1) {
                                    logger.error(e1);
                                }
                                progressBarForRows.setValue(i);
                            }
                            progressBarForRows.setIndeterminate(false);
                            progressBarForRows.setVisible(false);
                        }
                    }).start();
                }
                if (viewPanel.isVisible())
                    loadDataToJTable();
                try {
                    loadTables();
                } catch (IOException ignored) {
                }

                tablesJList.setSelectedValue(choosedTable, true);
            }
        };

        hexRadioButton.addActionListener(encodingWasChangedListener);
        asciiRadioButton.addActionListener(encodingWasChangedListener);
        utf8RadioButton.addActionListener(encodingWasChangedListener);
        windows1251RadioButton.addActionListener(encodingWasChangedListener);

        jMenuItemChangeHbaseSettings.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (changeSettingsForm == null) {
                    changeSettingsForm = new ChangeSettings();
                    changeSettingsForm.setTitle(resourceBundle.getString("settings"));
                    changeSettingsForm.setDatabaseWorker(databaseWorker);
                    changeSettingsForm.setMainForm(thisFrame);
                    changeSettingsForm.pack();
                    changeSettingsForm.setLocationRelativeTo(null);
                }
                changeSettingsForm.setVisible(true);
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    tablesJList.setEnabled(false);
                    rowsJList.setEnabled(false);
                    familiesJList.setEnabled(false);
                    loadTables();

                    tablesJList.setEnabled(true);
                    rowsJList.setEnabled(true);
                    familiesJList.setEnabled(true);
                } catch (IOException ignored) {
                }
            }
        });

        searchRowTextField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (rowsJList.getModel() == null && rowsJList.getModel().getSize() == 0)
                    return;
                final DefaultListModel<String> rowsModel = listModels.get(choosedTable);
                if (searchRowTextField.getText().isEmpty())
                    rowsJList.setModel(rowsModel);
                rowsJList.setEnabled(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        rowsJList.setSelectedIndex(-1);
                        goButton.setEnabled(false);
                        try {
                            List<String> newRowsNames = new ArrayList<>(rowsModel.getSize());
                            progressBarForRows.setVisible(true);
                            progressBarForRows.setMaximum(rowsModel.getSize() + 10);
                            for (int i = 0; i < rowsModel.getSize(); i++) {
                                progressBarForRows.setValue(i);
                                String elementAt = rowsModel.getElementAt(i);
                                if (elementAt.contains(searchRowTextField.getText()))
                                    newRowsNames.add(elementAt);
                            }
                            progressBarForRows.setValue(rowsModel.getSize() + 3);
                            rowsJList.setListData(newRowsNames.toArray(new String[newRowsNames.size()]));
                            progressBarForRows.setValue(rowsModel.getSize() + 9);
                            progressBarForRows.setVisible(false);
                        } catch (ArrayIndexOutOfBoundsException ignored) {
                        }
                        rowsJList.setEnabled(true);
                    }
                }).start();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                keyTyped(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                keyTyped(e);
            }
        });
    }

    private void loadDataToJTable() {
        Row choosedRowData = null;
        try {
            choosedRowData = databaseWorker.getRow(tablesJList.getSelectedValue(), selectedRow, selectedFamily, selectedEncoding);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        tableData.setRowCount(0);
        tableData.setColumnCount(0);

        if (choosedRowData.getData().length == 0) {
            logger.info("No data in this row");
            return;
        }

        String[] tmpColumnsLink = choosedRowData.getColumns();

        for (String aTmpColumnsLink : tmpColumnsLink) {
            if (aTmpColumnsLink != null)
                tableData.addColumn(aTmpColumnsLink);
        }
        tableData.addRow(choosedRowData.getData());
    }

    public void loadTables() throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    tablesNames = databaseWorker.getTableNames();
                    if (!tablesJList.isEnabled()) {
                        tablesJList.setEnabled(true);
                        familiesJList.setEnabled(true);
                        rowsJList.setEnabled(true);

                        utf8RadioButton.setEnabled(true);
                        asciiRadioButton.setEnabled(true);
                        hexRadioButton.setEnabled(true);
                        windows1251RadioButton.setEnabled(true);
                    }
                } catch (IOException exception) {
                    logger.error(exception);
                    JOptionPane.showMessageDialog(thisFrame,
                            exception.getLocalizedMessage(),
                            exception.getClass().getName(),
                            JOptionPane.ERROR_MESSAGE);
                }

                if (tablesNames != null && tablesNames.length > 0)
                    try {
                        tablesJList.setListData(tablesNames);
                        if (choosedTable == null) {
                            choosedTable = tablesNames[0];
                        }
                        tablesJList.setSelectedValue(choosedTable, true);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        logger.error(e);
                    }
                else {
                    tablesJList.setEnabled(false);
                    familiesJList.setEnabled(false);
                    rowsJList.setEnabled(false);
                    utf8RadioButton.setEnabled(false);
                    asciiRadioButton.setEnabled(false);
                    hexRadioButton.setEnabled(false);
                    windows1251RadioButton.setEnabled(false);
                }
            }
        }).start();
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.setForeground(new Color(-1118482));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new CardLayout(0, 0));
        mainPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        choosePanel = new JPanel();
        choosePanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        choosePanel.setVisible(true);
        panel1.add(choosePanel, "Card1");
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setHorizontalScrollBarPolicy(30);
        choosePanel.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tablesJList = new JList();
        tablesJList.setSelectionMode(0);
        scrollPane1.setViewportView(tablesJList);
        final JScrollPane scrollPane2 = new JScrollPane();
        scrollPane2.setHorizontalScrollBarPolicy(30);
        choosePanel.add(scrollPane2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        familiesJList = new JList();
        familiesJList.setSelectionMode(0);
        scrollPane2.setViewportView(familiesJList);
        goButton = new JButton();
        goButton.setEnabled(false);
        this.$$$loadButtonText$$$(goButton, ResourceBundle.getBundle("MainFormResources").getString("goButtonText"));
        choosePanel.add(goButton, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        viewPanel = new JPanel();
        viewPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        viewPanel.setVisible(false);
        panel1.add(viewPanel, "Card2");
        backButton = new JButton();
        backButton.setText("Назад");
        viewPanel.add(backButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane3 = new JScrollPane();
        viewPanel.add(scrollPane3, new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        jTable = new JTable();
        jTable.setAutoCreateRowSorter(true);
        jTable.setDragEnabled(true);
        jTable.setEditingColumn(-1);
        jTable.setEditingRow(-1);
        jTable.setEnabled(true);
        scrollPane3.setViewportView(jTable);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JScrollPane scrollPane4 = new JScrollPane();
        scrollPane4.setHorizontalScrollBarPolicy(30);
        scrollPane4.setVisible(true);
        panel2.add(scrollPane4, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        rowsJList = new JList();
        rowsJList.setLayoutOrientation(0);
        rowsJList.setSelectionMode(0);
        rowsJList.putClientProperty("html.disable", Boolean.FALSE);
        rowsJList.putClientProperty("List.isFileList", Boolean.FALSE);
        scrollPane4.setViewportView(rowsJList);
        asciiRadioButton = new JRadioButton();
        asciiRadioButton.setText("ASCII");
        panel2.add(asciiRadioButton, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        windows1251RadioButton = new JRadioButton();
        windows1251RadioButton.setText("WINDOWS-1251");
        panel2.add(windows1251RadioButton, new GridConstraints(4, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        utf8RadioButton = new JRadioButton();
        utf8RadioButton.setText("UTF-8");
        panel2.add(utf8RadioButton, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        hexRadioButton = new JRadioButton();
        hexRadioButton.setText("HEX");
        panel2.add(hexRadioButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        refreshButton = new JButton();
        refreshButton.setText("");
        panel2.add(refreshButton, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        searchRowTextField = new JTextField();
        panel2.add(searchRowTextField, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        progressBarForRows = new JProgressBar();
        progressBarForRows.setVisible(false);
        panel2.add(progressBarForRows, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        mainPanel.add(progressBar, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

    //private void createUIComponents() {
    //    searchPanel = new JPanel(new GridLayout(1, 1));
    //}

}
