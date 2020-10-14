import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class Test {
    private final ExecutorService executorService = Executors.newFixedThreadPool(8);

    ReentrantLock lock = new ReentrantLock();

    int count = 0;

    public Test() {

    }


    public static void main(String[] args) throws InterruptedException {
        ByteBuffer headerBuffer = ByteBuffer.allocate(32);
        headerBuffer.putChar('s');
        headerBuffer.putInt(15);
        headerBuffer.putInt(200);

        headerBuffer.flip();

        headerBuffer.getChar();
        headerBuffer.getInt();
        var code = headerBuffer.getInt();
        System.out.println(code);
    }
}
