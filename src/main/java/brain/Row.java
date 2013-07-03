package brain;

import java.util.Arrays;

/**
 * Краткое описание класса:
 * Кодер: anatoliy
 * Дата: 30.05.13
 * Время: 20:47
 * Начал - закончи
 */
public class Row {
    private final String[] columns;
    private final String[] data;
    private final String family;

    public Row(String[] columns, String[] data, String family) {
        this.columns = columns;
        this.data = data;
        this.family = family;
    }

    public String[] getColumns() {
        return columns;
    }

    public String[] getData() {
        return data;
    }

    public String getFamily() {
        return family;
    }

    @Override
    public String toString() {
        return "Row{" +
                "columns=" + Arrays.toString(columns) +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
