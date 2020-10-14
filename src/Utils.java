import model.Header;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

public class Utils {
    public static final int HEADER_SIZE = 32;

    public static byte[] readBuffer(ReadableByteChannel sc, ByteBuffer buffer, Logger logger) throws IOException {
        // may have sticky TCP packet problem
        // not handle it cuz our data is not sent continuously
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (true) {
            buffer.clear();
            int size = sc.read(buffer);
            if (size == -1) {
                logger.info("lost connection " + sc);
                sc.close();
                throw new IOException("lost connection");
            }
            if (size == 0) break;
            buffer.flip();
            byteArrayOutputStream.write(buffer.array(), 0, buffer.limit());
        }
        return byteArrayOutputStream.toByteArray();
    }

    public static void writeBuffer(byte[] data, WritableByteChannel sc, ByteBuffer buffer, int buffer_size) throws IOException {
        for (int i = 0; i < data.length / buffer_size + 1; i++) {
            buffer.clear();
            buffer.put(data, i * buffer_size, i == data.length / buffer_size ? data.length % buffer_size : buffer_size);
            buffer.flip();
            sc.write(buffer);
        }
    }

    public static byte[] merge(byte[] a, byte[] b) {
        byte[] joinedArray = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, joinedArray, a.length, b.length);
        return joinedArray;
    }

    public static List<byte[]> splitByteArray(byte[] array, byte[] delimiter) {
        List<byte[]> byteArrays = new LinkedList<>();
        if (delimiter.length == 0) {
            return byteArrays;
        }
        int begin = 0;

        outer:
        for (int i = 0; i < array.length - delimiter.length + 1; i++) {
            for (int j = 0; j < delimiter.length; j++) {
                if (array[i + j] != delimiter[j]) {
                    continue outer;
                }
            }
            byteArrays.add(Arrays.copyOfRange(array, begin, i));
            begin = i + delimiter.length;
        }
        byteArrays.add(Arrays.copyOfRange(array, begin, array.length));
        return byteArrays;
    }

    public static List<byte[]> splitByteArray(byte[] array) {
        return splitByteArray(array, "\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isDataValid(byte[] data, int size) {
        return data.length == size;
    }

    public static byte[] header(char type, int size, int status) {
        ByteBuffer headerBuffer = ByteBuffer.allocate(HEADER_SIZE);
        headerBuffer.putChar(type);
        headerBuffer.putInt(size);
        headerBuffer.putInt(status);
        return headerBuffer.array();
    }

    public static Header resolveHeader(DataInputStream in) throws IOException {
        ByteBuffer headerBuffer = ByteBuffer.allocate(HEADER_SIZE);
        for (int i = 0; i < HEADER_SIZE; i++) {
            headerBuffer.put((byte) in.read());
        }
        headerBuffer.flip();
        char type = headerBuffer.getChar();
        int size = headerBuffer.getInt();
        int status = headerBuffer.getInt();
        return new Header(type, size, status);
    }

    public static byte[] readStream(DataInputStream in, int size) throws IOException {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) in.read();
        }
        return data;
    }

}
