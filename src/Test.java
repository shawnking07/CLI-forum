import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class Test {
    public static void main(String[] args) throws IOException {
        ByteBuffer fileBuffer = ByteBuffer.allocate(1024);
        FileChannel open = FileChannel.open(Path.of("README.md"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        open.read(fileBuffer);
        System.out.println(Arrays.toString(fileBuffer.array()));
    }
}
