import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class Test {
    private final ExecutorService executorService = Executors.newFixedThreadPool(8);

    ReentrantLock lock = new ReentrantLock();

    int count = 0;

    public Test() {

    }


    public static void main(String[] args) throws InterruptedException, IOException {

    }
}
