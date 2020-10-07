import enumObj.Operation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Server {
    private final static Logger logger = Logger.getLogger(Server.class.getSimpleName());

    private final static int BUFFER_SIZE = 1024;

    private final Selector selector = Selector.open();

    private final ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer fileBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private final List<String> credentialsList;

    /**
     * Server Class
     *
     * @param port server port
     * @throws IOException Any I/O error
     */
    public Server(int port) throws IOException {
        InetSocketAddress serverAddr = new InetSocketAddress(port);
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.bind(serverAddr);
        server.register(selector, SelectionKey.OP_ACCEPT);

        FileChannel credentials = FileChannel.open(Path.of("./credentials.txt"));
        byte[] file = Utils.readBuffer(credentials, fileBuffer);
        credentialsList = Arrays.asList(new String(file).split("\\n"));
        credentials.close();
    }

    public static void main(String[] args) throws IOException {
        new Server(8088).start();
    }

    /**
     * generate response byte array
     *
     * @param code   response code
     * @param length content-size
     * @param type   content-type
     * @param data   content data
     * @return byte array
     */
    private byte[] generateResponse(int code, int length, String type, byte[] data) {
        String header = code + "\r\n" +
                length + "\r\n" +
                type + "\r\n";
        return Utils.merge(header.getBytes(), data);
    }

    private Set<SelectionKey> getConnectedChannel() {
        return selector.keys().stream()
                .filter(v -> v.channel() instanceof SocketChannel && v.channel().isOpen())
                .collect(Collectors.toSet());
    }

    private Optional<SelectionKey> getConnectedChannel(String username) {
        return selector.keys().stream()
                .filter(v -> v.channel() instanceof SocketChannel && v.channel().isOpen())
                .filter(v -> v.attachment().equals(username))
                .findAny();
    }

    private void resolveCommand(SelectionKey key) throws IOException {
        String username = (String) key.attachment();
        var sc = (SocketChannel) key.channel();
        byte[] data = Utils.readBuffer(sc, readBuffer);
        String strData = new String(data);
        String[] split = strData.split("\\r?\\n");
        int dataSize = Integer.parseInt(split[0]);
        String dataType = split[1];
        var op_param = split[2].split("\\s+", 2);
        Operation operation = Operation.valueOf(op_param[0]);
        String param = op_param[1];

        switch (operation) {
            case AUTH:
                if (credentialsList.contains(param)) {
                    String msg = "OK";
                    Utils.writeBuffer(generateResponse(200, msg.length(), "text", msg.getBytes()),
                            sc,
                            writeBuffer,
                            BUFFER_SIZE);
                    username = param.split("\\s+")[0];
                    key.attach(username);
                    logger.info(username + " successful login");
                } else {
                    String msg = "Invalid password";
                    Utils.writeBuffer(generateResponse(403, msg.length(), "text", msg.getBytes()),
                            sc,
                            writeBuffer,
                            BUFFER_SIZE);
                    logger.info("Incorrect password");
                }
                break;
            case CREATE_THREAD:
                try {
                    FileChannel thread = FileChannel.open(Path.of(param),
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                    String threadData = username + "\n";
                    Utils.writeBuffer(threadData.getBytes(), thread, fileBuffer, BUFFER_SIZE);
                } catch (FileAlreadyExistsException e) {
                    logger.info("Thread " + param + " exists");
                }

        }
    }

    public void start() throws IOException {
        Scanner in = new Scanner(System.in);
        logger.info("Waiting for clients");
        while (true) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    var server = (ServerSocketChannel) key.channel();
                    SocketChannel client = server.accept();
                    logger.info("accept connection from " + client);
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                } else if (key.isReadable()) {
                    var client = (SocketChannel) key.channel();
                    logger.info("received data from " + client);
                    readBuffer.clear();
                    int size = client.read(readBuffer);
                    if (size == -1) {
                        logger.info("lost connection: " + client);
                        client.close();
                    }
//                        readBuffer.flip();
                    System.out.println(new String(readBuffer.array(), 0, readBuffer.position()));


                }
            }
        }
    }
}
