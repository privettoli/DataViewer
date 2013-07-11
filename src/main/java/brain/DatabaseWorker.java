package brain;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Краткое описание класса:
 * Кодер: anatoliy
 * Дата: 29.05.13
 * Время: 0:49
 * Начал - закончи
 */
public class DatabaseWorker {
    private final Configuration configuration = HBaseConfiguration.create();
    private final Logger logger = Logger.getLogger(DatabaseWorker.class);
    private final Preferences preferences = Preferences.userNodeForPackage(DatabaseWorker.class);
    private Map<String, Map<String, Map<String, Row>>> cache = new ConcurrentHashMap<>();

    public String getSettingValue(String key) {
        return configuration.get(key);
    }

    public void loadProperties() {
        logger.trace("Creating DatabaseWorker");
        try {
            for (String key : preferences.keys()) {
                configuration.set(key, preferences.get(key, null));
            }
        } catch (BackingStoreException e) {
            logger.error(e);
        }
    }

    /**
     * Сохраняет настройку сразу в preference и в configuration
     *
     * @param key   ключ
     * @param value значение
     */
    public void setSetting(String key, String value) {
        configuration.set(key, value);
        cache = new HashMap<>();
        preferences.put(key, value);
    }

    public synchronized Row getRow(String tableName, byte[] rowName, byte[] familyName) throws IOException, InterruptedException {
        while (cache == null || tableName == null) {
            Thread.sleep(100L);
        }
        HTable hTable = new HTable(configuration, tableName);
        List<String> qualifiersNames = new LinkedList<>();
        List<byte[]> data = new LinkedList<>();

        Get get = new Get(rowName);
        get.addFamily(familyName);
        Result result = hTable.get(get);
        if (result.getFamilyMap(familyName) == null)
            return null;
        for (byte[] bytes : result.getFamilyMap(familyName).keySet()) {
            qualifiersNames.add(Bytes.toString(bytes));
            data.add(result.getValue(familyName, bytes));
        }
        return new Row(qualifiersNames.toArray(new String[qualifiersNames.size()]), data);
    }

    /**
     * @return имена всех таблиц в базе данных
     */
    public String[] getTableNames() throws IOException {
        if (cache.size() != 0) {
            return cache.keySet().toArray(new String[cache.keySet().size()]);
        }
        HBaseAdmin hBaseAdmin = new HBaseAdmin(configuration);
        HTableDescriptor[] hTableDescriptors = hBaseAdmin.listTables();
        String[] names = new String[hTableDescriptors.length];
        for (int i = 0; i < hTableDescriptors.length; ++i) {
            names[i] = hTableDescriptors[i].getNameAsString();
        }
        for (String name : names) {
            cache.put(name, new HashMap<String, Map<String, Row>>());
        }
        return names;
    }

    public void fillRowsToListModel(String tableName, final JProgressBar progressBar, final DefaultListModel<String> hexListModel, final DefaultListModel<String> ahciiListModel, final DefaultListModel<String> cp1251ListModel, final DefaultListModel<String> utf8ListModel) throws IOException {
        progressBar.setVisible(true);
        progressBar.setValue(0);
        progressBar.setMaximum(2);
        HTable table = new HTable(configuration, tableName);
        Scan scan = new Scan();
        scan.setMaxVersions();
        scan.setFilter(new FirstKeyOnlyFilter());
        ResultScanner scanner = table.getScanner(scan);
        for (final Result rr : scanner) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    try {
                        hexListModel.addElement(BytesToStringConverter.toString(rr.getRow(), Constants.HEX));
                        ahciiListModel.addElement(BytesToStringConverter.toString(rr.getRow(), Constants.AHCII));
                        cp1251ListModel.addElement(BytesToStringConverter.toString(rr.getRow(), Constants.CP1251));
                        utf8ListModel.addElement(Bytes.toString(rr.getRow()));
                        int max = progressBar.getMaximum();
                        progressBar.setMaximum(max + max / 10);
                        progressBar.setValue(max);
                    } catch (UnsupportedEncodingException e) {
                        logger.error(e);
                    }
                }
            });
        }
        progressBar.setVisible(false);
    }

    /**
     * @param tableName имя таблицы
     * @return массив информаций о семействах столбцов
     */
    public String[] getFamilies(String tableName) throws IOException {
        if (tableName == null)
            return null;

        Map<String, Map<String, Row>> table = cache.get(tableName);
        if (table.keySet().size() > 0) {
            return table.keySet().toArray(new String[table.size()]);
        }

        HTable hTable = new HTable(configuration, tableName);
        Collection<HColumnDescriptor> hColumnDiscriptors = hTable.getTableDescriptor().getFamilies();
        List<HColumnDescriptor> result = new ArrayList<>(hColumnDiscriptors.size());
        table = new HashMap<>();
        cache.put(tableName, table);
        for (HColumnDescriptor hColumnDiscriptor : hColumnDiscriptors) {
            result.add(hColumnDiscriptor);
            table.put(hColumnDiscriptor.getNameAsString(), new HashMap<String, Row>());
        }
        return getFamiliesNames(result);
    }

    /**
     * @param families discriptor-ы семейств
     * @return имена семейств
     */
    private String[] getFamiliesNames(List<HColumnDescriptor> families) {
        if (families == null)
            return null;
        String[] familiesNames = new String[families.size()];
        for (int i = 0; i < families.size(); ++i) {
            familiesNames[i] = families.get(i).getNameAsString();
        }
        return familiesNames;
    }

    public void changeTheCell(String choosedTable, byte[] selectedFamily, byte[] selectedRow, byte[] qualifier, byte[] newValue) throws IOException {
        HTable hTable = new HTable(configuration, choosedTable);
        Put put = new Put(selectedRow);
        put.add(selectedFamily, qualifier, newValue);
        hTable.put(put);
    }
}
