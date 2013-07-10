package brain;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * Краткое описание класса:
 * Кодер: anatoliy
 * Дата: 19.06.13
 * Время: 17:36
 * Начал - закончи
 */
public class BytesToStringConverter {
    final private static char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String toString(byte[] bytes, String encoding) throws UnsupportedEncodingException {
        if (!encoding.toLowerCase().equals("hex"))
            return new String(bytes, encoding);
        else return toHex(bytes);
    }

    private static String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int i = 0; i < bytes.length; i++) {
            v = bytes[i] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] toBytes(String string, String selectedEncoding) throws Exception {
        if (string == null) {
            throw new Exception("string is null");
        }
        byte[] data;
        switch (selectedEncoding) {
            case Constants.HEX:
                int len = string.length();
                data = new byte[len / 2];
                for (int i = 0; i < len; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(string.charAt(i), 16) << 4)
                            + Character.digit(string.charAt(i + 1), 16));
                }
                break;
            default:
                if (Charset.isSupported(selectedEncoding))
                    data = string.getBytes(selectedEncoding);
                else return null;
        }
        return data;
    }
}
