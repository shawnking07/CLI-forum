import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;

public class Utils {
    public static byte[] readBuffer(ReadableByteChannel sc, ByteBuffer buffer) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (true) {
            buffer.clear();
            int size = sc.read(buffer);
            if (size == -1) break;
//            readBuffer.flip();
            byteArrayOutputStream.write(buffer.array(), 0, buffer.position());
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
}
