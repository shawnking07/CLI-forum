import model.Operation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Server {
    private final static Logger logger = Logger.getLogger(Server.class.getSimpleName());

    private final static int BUFFER_SIZE = 1024;
    private final static Charset CHARSET = StandardCharsets.UTF_8;

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

        Stream<String> lines = Files.lines(Path.of("credentials.txt"));
        credentialsList = lines.collect(Collectors.toList());
    }

    public static void main(String[] args) throws IOException {
        new Server(8088).start();
    }

    private byte[] generateResponse(int code, int length, char type, byte[] data) {
        byte[] header = Utils.header(type, length, code);
        return Utils.merge(header, data);
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
        byte[] data;
        try {
            data = Utils.readBuffer(sc, readBuffer, logger);
        } catch (IOException e) {
            logger.warning(e.getMessage());
            return;
        }
        String strData = new String(data, CHARSET);
        String[] split = strData.split("\\r?\\n");
        int dataSize = Integer.parseInt(split[0]);
        String dataType = split[1];
        var op_param = split[2].split("\\s+", 2);
        Operation operation;
        try {
            operation = Operation.fromString(op_param[0]);
        } catch (IllegalArgumentException e) {
            var errorMsg = e.getLocalizedMessage();
            Utils.writeBuffer(generateResponse(500, errorMsg.length(), 's', errorMsg.getBytes(CHARSET)),
                    sc,
                    writeBuffer,
                    BUFFER_SIZE);
            key.attach(username);
            logger.info("WRONG COMMAND!");
            return;
        }
        String param = op_param[1];

        switch (operation) {
            case AUTH:
                if (credentialsList.contains(param)) {
                    username = param.split("\\s+")[0];
                    String msg = "Welcome " + username + " !\n";
                    Utils.writeBuffer(generateResponse(200, msg.length(), 's', msg.getBytes(CHARSET)),
                            sc,
                            writeBuffer,
                            BUFFER_SIZE);
                    key.attach(username);
                    logger.info(username + " successful login");
                } else {
                    String msg = "Invalid password\n";
                    Utils.writeBuffer(generateResponse(403, msg.length(), 's', msg.getBytes(CHARSET)),
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
                    Utils.writeBuffer(threadData.getBytes(CHARSET), thread, fileBuffer, BUFFER_SIZE);
                } catch (FileAlreadyExistsException e) {
                    logger.info("Thread " + param + " exists");
                }
                break;
            default:

        }
    }

    public void start() throws IOException {
        logger.info("Waiting for clients");
        while (true) {
            if (selector.select(100) == 0) {
                continue;
            }
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
                    try {
                        resolveCommand(key);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
