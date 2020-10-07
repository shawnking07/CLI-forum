import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

public class Client {
    private final static Logger logger = Logger.getLogger(Client.class.getSimpleName());

    private final static int BUFFER_SIZE = 1024;

    private final Selector selector = Selector.open();

    private final ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    private final boolean isLogin = false;

    public Client(int port) throws IOException {
        InetSocketAddress server = new InetSocketAddress(port);
        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.connect(server);
        sc.register(selector, SelectionKey.OP_CONNECT);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new RuntimeException("invalid args size");
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
            if (port < 0 || port > 65535) {
                throw new RuntimeException("invalid port");
            }
        } catch (Exception e) {
            throw new RuntimeException("invalid port number");
        }

        new Client(port).start();

    }

    private byte[] authData() {
//        String username = System.console().readLine("Enter username: ");
//        String password = new String(System.console().readPassword("Enter password: "));

        String username = "test";
        String password = "passwd";

        String header = "0\r\nAUTH\r\n";
        String auth_ = header + "ATH " + username + " " + password + "\r\n";

        return auth_.getBytes();
    }

    public void start() throws IOException {
        Scanner in = new Scanner(System.in);
        while (true) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
//            logger.info("keys=" + keys.size());
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                var sc = (SocketChannel) key.channel();
                if (key.isConnectable()) {
                    logger.info("connected");

                    sc.finishConnect(); // blocking connect
                    sc.register(selector, SelectionKey.OP_WRITE);
                    Utils.writeBuffer(authData(), sc, writeBuffer, BUFFER_SIZE);
                } else if (key.isWritable()) {
                    logger.info("send data");
                    System.out.println("Enter one of the following commands: CRT, MSG, DLT, EDT, LST, RDT, UPD, DWN, RMV, XIT, SHT: ");
                    String input = in.nextLine();
                    Utils.writeBuffer(input.getBytes(), sc, writeBuffer, BUFFER_SIZE);
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                } else if (key.isReadable()) {
                    logger.info("received data");
                    byte[] data = Utils.readBuffer(sc, readBuffer);
                    System.out.println(new String(data));
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }

            }
        }
    }
}
