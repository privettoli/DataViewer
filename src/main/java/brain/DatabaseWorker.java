package brain;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.*;
import org.apache.hadoop.hbase.filter.RowFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.IOException;
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
    /**
     * String - имя таблицы<br>
     * Карта {<br>
     * String - имя семейства колонок<br>
     * Row - строка<br>
     * }
     */
    private Map<String, Map<String, Map<byte[], Row>>> cache = new ConcurrentHashMap<>();

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

    public Row getRow(String tableName, byte[] rowName, byte[] familyName, String encoding) throws IOException {
        String familyNameAsString = Bytes.toString(familyName);

        Map<String, Map<byte[], Row>> table = cache.get(tableName);

        if (!table.containsKey(familyNameAsString))
            table.put(familyNameAsString, new HashMap<byte[], Row>());

        Row row = cache.get(tableName).get(familyNameAsString).get(rowName);
        if (row != null && row.getData() != null) {
            return row;
        }

        List<String> columns = new LinkedList<>();
        List<String> data = new LinkedList<>();

        HTable hTable = new HTable(configuration, tableName);
        Scan scan = new Scan();

        FilterList list = new FilterList(FilterList.Operator.MUST_PASS_ALL);

        FamilyFilter oneFamilyFilter = new FamilyFilter(CompareFilter.CompareOp.EQUAL, new BinaryComparator(familyName));
        RowFilter oneRowFilter = new RowFilter(CompareFilter.CompareOp.EQUAL, new BinaryComparator(rowName));

        list.addFilter(oneFamilyFilter);
        list.addFilter(oneRowFilter);

        scan.setFilter(list);

        ResultScanner scanner = hTable.getScanner(scan);
        for (Result result : scanner) {
            byte[] qualifier = result.getFamilyMap(familyName).firstKey();
            System.out.println(Bytes.toString(qualifier));
            byte[] rowData = result.getColumnLatest(familyName, qualifier).getValue();
            String columnName = Bytes.toString(qualifier);
            columns.add(columnName);
            data.add(BytesToStringConverter.toString(rowData, encoding));
        }
        row = new Row(columns.toArray(new String[columns.size()]), data.toArray(new String[data.size()]), Bytes.toString(familyName));
        table.get(familyNameAsString).put(familyName, row);
        return row;
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
            cache.put(name, new HashMap<String, Map<byte[], Row>>());
        }
        return names;
    }

    public void fillRowsToListModel(String tableName, DefaultListModel<String> listModel, String encoding, JProgressBar progressBar) throws IOException {
        progressBar.setVisible(true);
        progressBar.setValue(0);
        progressBar.setMaximum(2);
        HTable table = new HTable(configuration, tableName);
        Scan scan = new Scan();
        scan.setMaxVersions();
        scan.setFilter(new FirstKeyOnlyFilter());
        ResultScanner scanner = table.getScanner(scan);
        for (Result rr : scanner) {
            listModel.addElement(BytesToStringConverter.toString(rr.getRow(), encoding));
            progressBar.setValue(progressBar.getMaximum());
            progressBar.setMaximum(progressBar.getMaximum() + 1);
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

        Map<String, Map<byte[], Row>> table = cache.get(tableName);
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
            table.put(hColumnDiscriptor.getNameAsString(), new HashMap<byte[], Row>());
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
}
