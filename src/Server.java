import model.Operation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Server {
    // TODO: Set worker thread to consume request queue
    private final static Logger logger = Logger.getLogger(Server.class.getSimpleName());

    private final static int BUFFER_SIZE = 1024;

    private final Selector selector = Selector.open();

    private final ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer fileBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private final List<String> credentialsList;

    private final Pattern fileMsgSerial = Pattern.compile("^([0-9]+)\\s(\\S+): (\\S+)");

    private final String adminPassword;

    private final List<Path> threadFiles = new ArrayList<>();
    private final List<Path> uploadedFiles = new ArrayList<>();
    private final Path credentials;

    /**
     * Server Class
     *
     * @param port server port
     * @throws IOException Any I/O error
     */
    public Server(int port, String adminPassword) throws IOException {
        this.adminPassword = adminPassword;
        InetSocketAddress serverAddr = new InetSocketAddress(port);
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.bind(serverAddr);
        server.register(selector, SelectionKey.OP_ACCEPT);

        credentials = Path.of("credentials.txt");
        Stream<String> lines = Files.lines(credentials);
        credentialsList = lines.collect(Collectors.toList());
    }

    public static void main(String[] args) throws IOException {
        String adminPassword = args[1];
        new Server(Integer.parseInt(args[0]), adminPassword).start();
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
            data = Utils.readBuffer(key, readBuffer, logger);
        } catch (IOException e) {
            return;
        }
        String strData = new String(data, Utils.CHARSET);
        String[] split = strData.split("\\r?\\n");
        int dataSize = Integer.parseInt(split[0]);
        String dataType = split[1];
        var op_param = split[2].split("\\s+", 2);
        Operation operation;
        try {
            operation = Operation.fromString(op_param[0]);
        } catch (IllegalArgumentException e) {
            var errorMsg = e.getLocalizedMessage();
            Utils.writeBuffer(generateResponse(500, errorMsg.length(), 's', errorMsg.getBytes(Utils.CHARSET)),
                    sc,
                    writeBuffer,
                    BUFFER_SIZE);
            key.attach(username);
            logger.info("WRONG COMMAND!\n");
            return;
        }
        String param = op_param[1].trim();

        byte[] attached = new byte[0];
        if (dataSize > 0) {
            attached = Utils.splitByteArray(data).get(1);
        }

        if (operation != Operation.AUTH) {
            logger.info(username + " issued " + operation.label + "\n");
        }

        String msg;

        try {
            switch (operation) {
                case AUTH:
                    if (credentialsList.contains(param)) {
                        username = param.split("\\s+")[0];
                        msg = "Welcome " + username + " !\n";
                        Utils.writeBuffer(generateResponse(200, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                                sc,
                                writeBuffer,
                                BUFFER_SIZE);
                        key.attach(username);
                        logger.info(username + " successful login\n");
                    } else {
                        msg = "Invalid password\n";
                        Utils.writeBuffer(generateResponse(403, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                                sc,
                                writeBuffer,
                                BUFFER_SIZE);
                        logger.info("Incorrect password\n");
                    }
                    break;
                case CREATE_THREAD:
                    var paramPath = Path.of("./" + param);
                    if (Files.isDirectory(paramPath)) throw new RuntimeException("invalid thread name!");
                    try (FileChannel thread = FileChannel.open(paramPath,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)) {

                        String threadData = username + "\n";
                        Utils.writeBuffer(threadData.getBytes(Utils.CHARSET), thread, fileBuffer, BUFFER_SIZE);
                        threadFiles.add(paramPath);


                        logger.info("Thread " + param + " created by " + username + "\n");
                        msg = "Thread " + param + " created!\n";
                        Utils.writeBuffer(generateResponse(200, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                                sc,
                                writeBuffer,
                                BUFFER_SIZE);
                    } catch (FileAlreadyExistsException e) {
                        msg = "Thread " + param + " exists!\n";
                        throw new RuntimeException(msg);
                    }
                    break;
                case POST_MESSAGE:
                    try (FileChannel thread = FileChannel.open(Path.of("./" + param),
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE)) {
                        // file pointer will move to end
//                    FileLock lock = thread.lock(0, Long.MAX_VALUE, true);
                        // lock file for multithreading consumption

                        // generate current serial number from thread file
                        int currentSerial = getCurrentSerial(thread);

                        String currentLine = currentSerial + " " + username + ": " + new String(attached, Utils.CHARSET) + "\n";
                        Utils.writeBuffer(currentLine.getBytes(Utils.CHARSET), thread, fileBuffer, BUFFER_SIZE);
                        logger.info(username + " posted to " + param + "\n");
                        msg = "Message posted to " + param + "\n";
                        Utils.writeBuffer(generateResponse(200, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                                sc,
                                writeBuffer,
                                BUFFER_SIZE);
//                    lock.release();
                    } catch (NoSuchFileException e) {
                        msg = "Thread " + param + " does not exist!\n";
                        throw new RuntimeException(msg);
                    }
                    break;
                case DELETE_MESSAGE:
                    // dummy delete process
                    modifyThreadMsg(username, sc, param, null);
                    break;
                case EDIT_MESSAGE:
                    modifyThreadMsg(username, sc, param, new String(attached, Utils.CHARSET));
                    break;
                case LIST_THREADS:
                    msg = threadFiles.stream()
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .collect(Collectors.joining("\n")) + "\n";
                    logger.info("List threads: " + threadFiles.stream()
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .collect(Collectors.joining(", ")) + "\n");
                    Utils.writeBuffer(generateResponse(200, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                            sc,
                            writeBuffer,
                            BUFFER_SIZE);
                    break;
                case READ_THREADS:
                    try (FileChannel thread = FileChannel.open(Path.of("./" + param),
                            StandardOpenOption.READ)) {

                        String threadContent = Utils.lines(thread)
                                .skip(1)
                                .collect(Collectors.joining("\n")) + "\n";

                        logger.info("Thread " + param + " read by " + username + "\n");
                        Utils.writeBuffer(generateResponse(200, threadContent.length(), 's', threadContent.getBytes(Utils.CHARSET)),
                                sc,
                                writeBuffer,
                                BUFFER_SIZE);
                    } catch (NoSuchFileException e) {
                        msg = "Thread " + param + " does not exists!\n";
                        throw new RuntimeException(msg);
                    }
                    break;
                case UPLOAD_FILE:
                    String[] split1 = param.split("\\s+", 2);
                    String threadTitle = split1[0];
                    String filename = split1[1];
                    try (FileChannel thread = FileChannel.open(Path.of("./" + threadTitle),
                            StandardOpenOption.APPEND)) {
//                    FileLock lock = thread.lock(0, Long.MAX_VALUE, true);
                        // lock file for multithreading consumption

                        // write file
                        Path uploadFile = Path.of("./" + threadTitle + "-" + filename);
                        Files.write(uploadFile, attached,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE);
                        uploadedFiles.add(uploadFile);

                        String currentLine = username + " uploaded " + filename + "\n";
                        Utils.writeBuffer(currentLine.getBytes(Utils.CHARSET), thread, fileBuffer, BUFFER_SIZE);
                        logger.info(username + " uploaded " + filename + " to " + threadTitle + "\n");
                        msg = filename + " uploaded to " + threadTitle + "\n";
                        Utils.writeBuffer(generateResponse(200, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                                sc,
                                writeBuffer,
                                BUFFER_SIZE);
//                    lock.release();
                    } catch (NoSuchFileException e) {
                        msg = "Thread or file does not exist!\n";
                        throw new RuntimeException(msg);
                    }
                    break;
                case DOWNLOAD_FILE:
                    String[] split2 = param.split("\\s+", 2);
                    String threadTitle2 = split2[0];
                    String filename2 = threadTitle2 + "-" + split2[1];
                    try {
                        byte[] fileBytes = Files.readAllBytes(Path.of("./" + filename2));
                        byte[] merge = Utils.merge((split2[1] + "\r\n\r\n").getBytes(Utils.CHARSET), fileBytes);
                        logger.info(username + " download file " + split2[1] + "\n");
                        Utils.writeBuffer(generateResponse(200, merge.length, 'b', merge),
                                sc,
                                writeBuffer,
                                BUFFER_SIZE);
                    } catch (NoSuchFileException e) {
                        msg = "Thread or file does not exist!\n";
                        throw new RuntimeException(msg);
                    }

                    break;
                case REMOVE_THREAD:
                    // delete thread file
                    try {
                        String createdName = Files.lines(Path.of("./" + param))
                                .findFirst()
                                .orElse("");
                        if (!createdName.equals(username)) {
                            throw new RuntimeException(param + " is created by another user\n");
                        }
                    } catch (IOException e) {
                        msg = "Thread " + param + " does not exists!\n";
                        throw new RuntimeException(msg);
                    }
                    // delete uploaded file
                    logger.info(username + " remove thread " + param + "\n");
                    threadFiles.stream()
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .filter(v -> v.startsWith(param))
                            .map(Path::of)
                            .forEach(v -> {
                                try {
                                    Files.delete(v);
                                } catch (IOException e) {
                                    throw new RuntimeException(e.getLocalizedMessage());
                                }
                            });
                    msg = param + " all removed\n";
                    Utils.writeBuffer(generateResponse(200, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                            sc,
                            writeBuffer,
                            BUFFER_SIZE);

                    break;
                case EXIT:
                    break;
                case SHUTDOWN:
                    if (!param.equals(adminPassword)) {
                        throw new RuntimeException("Wrong password!\n");
                    }
                    logger.info(username + " shutdown system\n");

                    String finalMsg = "Goodbye. Server shutting down...\n";

                    Utils.writeBuffer(generateResponse(233, finalMsg.length(), 'x', finalMsg.getBytes(Utils.CHARSET)),
                            sc,
                            writeBuffer,
                            BUFFER_SIZE);

                    getConnectedChannel().forEach(k -> {
                        try {
                            Utils.writeBuffer(generateResponse(233, finalMsg.length(), 'x', finalMsg.getBytes(Utils.CHARSET)),
                                    (SocketChannel) k.channel(),
                                    writeBuffer,
                                    BUFFER_SIZE);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    threadFiles.forEach(v -> {
                        try {
                            Files.delete(v);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    uploadedFiles.forEach(v -> {
                        try {
                            Files.delete(v);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    Files.delete(credentials);
//                    keys.forEach(SelectionKey::cancel);
//                    selector.close();
                    System.exit(0);

                    break;
            }
        } catch (Exception e) {
            logger.info(e.getLocalizedMessage());
            Utils.writeBuffer(generateResponse(500, e.getLocalizedMessage().length(), 's', e.getLocalizedMessage().getBytes(Utils.CHARSET)),
                    sc,
                    writeBuffer,
                    BUFFER_SIZE);
        }
    }

    /**
     * generate current serial number
     *
     * @param thread
     * @return
     */
    private int getCurrentSerial(FileChannel thread) {
        return Utils.lines(thread)
                .map(v -> {
                    Matcher matcher = fileMsgSerial.matcher(v);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1)) + 1;
                    } else {
                        return 1;
                    }
                })
                .reduce((a, b) -> b)
                .orElse(1);
    }

    private void modifyThreadMsg(String username, SocketChannel sc, String param, String newMsg) throws IOException {
        var split1 = param.split("\\s+");
        try (FileChannel thread = FileChannel.open(Path.of(split1[0]),
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {
//                    FileLock lock = thread.lock(0, Long.MAX_VALUE, true);
            // lock file for multithreading consumption
            String messageNumber = split1[1];
            List<String> lines = Utils.lines(thread).collect(Collectors.toList());
            Optional<String> line = lines.stream()
                    .filter(v -> {
                        var matcher = fileMsgSerial.matcher(v);
                        if (matcher.find()) return matcher.group(1).equals(messageNumber);
                        else return false;
                    })
                    .findAny();
            if (line.isEmpty()) {
                String msg = "Message number " + messageNumber + " does not exist\n";
                logger.info(msg);
                Utils.writeBuffer(generateResponse(500, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                        sc,
                        writeBuffer,
                        BUFFER_SIZE);
                return;
            }

            line = lines.stream()
                    .filter(v -> {
                        var matcher = fileMsgSerial.matcher(v);
                        if (matcher.find()) return matcher.group(2).equals(username);
                        else return false;
                    })
                    .findAny();
            if (line.isEmpty()) {
                String msg = "Not your message\n";
                logger.info(msg);
                Utils.writeBuffer(generateResponse(500, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                        sc,
                        writeBuffer,
                        BUFFER_SIZE);
                return;
            }

            if (newMsg == null) {
                String fileContent = lines.stream()
                        .filter(v -> {
                            var matcher = fileMsgSerial.matcher(v);
                            if (matcher.find()) return !matcher.group(2).equals(username) ||
                                    !matcher.group(1).equals(messageNumber);
                            else return true;
                        })
                        .collect(Collectors.joining("\n")) + "\n";
                // dummy delete line
                thread.truncate(0);
                Utils.writeBuffer(fileContent.getBytes(Utils.CHARSET), thread, fileBuffer, BUFFER_SIZE);

                logger.info(messageNumber + " deleted by " + username);
                String msg = "Successfully deleted message " + messageNumber + "\n";
                Utils.writeBuffer(generateResponse(200, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                        sc,
                        writeBuffer,
                        BUFFER_SIZE);
            } else {
                String fileContent = lines.stream()
                        .map(v -> {
                            var matcher = fileMsgSerial.matcher(v);
                            if (matcher.find() &&
                                    matcher.group(2).equals(username) &&
                                    matcher.group(1).equals(messageNumber)) {
                                return messageNumber + " " + username + ": " + newMsg;
                            } else {
                                return v;
                            }
                        })
                        .collect(Collectors.joining("\n")) + "\n";
                // dummy delete line
                thread.truncate(0);
                Utils.writeBuffer(fileContent.getBytes(Utils.CHARSET), thread, fileBuffer, BUFFER_SIZE);

                logger.info(messageNumber + " edited by " + username);
                String msg = "Successfully edited message " + messageNumber + "\n";
                Utils.writeBuffer(generateResponse(200, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                        sc,
                        writeBuffer,
                        BUFFER_SIZE);
            }
//                    lock.release();
        } catch (NoSuchFileException e) {
            String msg = "Thread " + param + " does not exist!\n";
            logger.info(msg);
            Utils.writeBuffer(generateResponse(500, msg.length(), 's', msg.getBytes(Utils.CHARSET)),
                    sc,
                    writeBuffer,
                    BUFFER_SIZE);
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
