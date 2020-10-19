import model.Header;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Utils {
    public static final int HEADER_SIZE = 32;
    public final static Charset CHARSET = StandardCharsets.UTF_8;

    public static byte[] readBuffer(SelectionKey key, ByteBuffer buffer, Logger logger) throws IOException {
        // may have sticky TCP packet problem
        // not handle it cuz our data is not sent continuously
        String username = (String) key.attachment();
        var sc = (SocketChannel) key.channel();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (true) {
            buffer.clear();
            int size = sc.read(buffer);
            if (size == -1) {
                logger.info(username + " lost connection " + sc);
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

    /**
     * merge 2 byte arrays
     *
     * @param a byte array
     * @param b byte array
     * @return new array
     */
    public static byte[] merge(byte[] a, byte[] b) {
        byte[] joinedArray = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, joinedArray, a.length, b.length);
        return joinedArray;
    }

    private static boolean isMatch(byte[] pattern, byte[] input, int pos) {
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i] != input[pos + i]) {
                return false;
            }
        }
        return true;
    }

    public static List<byte[]> splitByteArray(byte[] input, byte[] pattern) {
        List<byte[]> l = new LinkedList<>();
        int blockStart = 0;
        for (int i = 0; i < input.length; i++) {
            if (isMatch(pattern, input, i)) {
                l.add(Arrays.copyOfRange(input, blockStart, i));
                blockStart = i + pattern.length;
                i = blockStart;
            }
        }
        l.add(Arrays.copyOfRange(input, blockStart, input.length));
        return l;
    }

    /**
     * split request byte array to header and body data
     *
     * @param array
     * @return
     */
    public static List<byte[]> splitByteArray(byte[] array) {
        return splitByteArray(array, "\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isDataValid(byte[] data, int size) {
        return data.length == size;
    }

    /**
     * generate header to byte array
     *
     * @param type
     * @param size
     * @param status
     * @return
     */
    public static byte[] header(char type, int size, int status) {
        ByteBuffer headerBuffer = ByteBuffer.allocate(HEADER_SIZE);
        headerBuffer.putChar(type);
        headerBuffer.putInt(size);
        headerBuffer.putInt(status);
        return headerBuffer.array();
    }

    /**
     * resolve response header from stream
     *
     * @param in
     * @return
     * @throws IOException
     */
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

    /**
     * read response stream for client
     *
     * @param in
     * @param size
     * @return
     * @throws IOException
     */
    public static byte[] readStream(DataInputStream in, int size) throws IOException {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) in.read();
        }
        return data;
    }

    /**
     * from {@link java.nio.channels.ReadableByteChannel} read lines
     *
     * @param channel ReadableByteChannel
     * @return String stream
     */
    public static Stream<String> lines(ReadableByteChannel channel) {
        BufferedReader br = new BufferedReader(Channels.newReader(channel, CHARSET));
        return br.lines().onClose(() -> {
            try {
                br.close();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    public static Optional<String> getExtension(Path filePath) {
        var fileName = filePath.getFileName().toString();
        return Optional.ofNullable(fileName)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(fileName.lastIndexOf(".") + 1));
    }

}
