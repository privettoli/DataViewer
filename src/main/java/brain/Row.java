package brain;

import java.util.Arrays;
import java.util.List;

/**
 * Краткое описание класса:
 * Кодер: anatoliy
 * Дата: 30.05.13
 * Время: 20:47
 * Начал - закончи
 */
public class Row {
    private final String[] columns;
    private final byte[][] data;

    public Row(String[] columns, List<byte[]> data) {
        this.columns = columns;
        this.data = new byte[data.size()][];
        int i = 0;
        for (byte[] bytes : data) {
            this.data[i++] = bytes;
        }
    }

    public String[] getColumns() {
        return columns;
    }

    @Override
    public String toString() {
        return "Row{" +
                "columns=" + Arrays.toString(columns) +
                ", data=" + Arrays.toString(data) +
                '}';
    }

    public byte[][] getData() {
        return data;
    }
}
