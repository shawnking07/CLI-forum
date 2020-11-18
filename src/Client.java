import model.Header;
import model.Operation;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class Client {
    private final static Logger logger = Logger.getLogger(Client.class.getSimpleName());

    private final BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));

    private final Socket socket;
    private boolean isLogin = false;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private Thread readThread;

    public Client(int port) throws IOException {
        socket = new Socket("127.0.0.1", port);
        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());
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

        Client client = new Client(port);

        client.start();
    }

    private byte[] authData() {
        String username = System.console().readLine("Enter username: ");
        String password = new String(System.console().readPassword("Enter password: "));

//        String username = "Yoda";
//        String password = "jedi*knight";

        String header = "0\r\nAUTH\r\n";
        String auth_ = header + "ATH " + username + " " + password + "\r\n";

        return auth_.getBytes(StandardCharsets.UTF_8);
    }

    public void start() throws IOException {

        while (!isLogin) {
            outputStream.write(authData());
            resolveResponse(inputStream);
        }

        Runnable readProcess = () -> {
            while (true) {
                try {
                    resolveResponse(inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        readThread = new Thread(readProcess);
        readThread.start();

        while (true) {
            System.out.printf("Enter one of the following commands: CRT, MSG, DLT, EDT, LST, RDT, UPD, DWN, RMV, XIT, SHT: ");
            String input = inReader.readLine();
            var splits = input.split("\\s+", 2);
            String cmd = splits[0];
            Operation operation;
            try {
                operation = Operation.fromString(cmd);
            } catch (IllegalArgumentException e) {
                logger.warning(e.getLocalizedMessage());
                continue;
            }
            byte[] request = new byte[0];
            try {
                String[] params;
                switch (operation) {
                    case AUTH:
                        break;
                    case CREATE_THREAD:
                    case DELETE_MESSAGE:
                    case READ_THREADS:
                    case DOWNLOAD_FILE:
                    case REMOVE_THREAD:
                    case SHUTDOWN:
                        request = generateRequest(operation, splits[1], "text", new byte[0]);
                        break;
                    case POST_MESSAGE:
                        params = splits[1].split("\\s+", 2);
                        request = generateRequest(operation, params[0], "text", params[1].getBytes(Utils.CHARSET));
                        break;
                    case EDIT_MESSAGE:
                        params = splits[1].split("\\s+", 3);
                        request = generateRequest(operation, params[0] + " " + params[1], "text", params[2].getBytes(Utils.CHARSET));
                        break;
                    case LIST_THREADS:
                        request = generateRequest(operation, null, "text", new byte[0]);
                        break;
                    case UPLOAD_FILE:
                        params = splits[1].split("\\s+", 2);
                        Path filePath = Path.of(params[1]);
                        byte[] fileBytes = Files.readAllBytes(filePath);
                        String extension = Utils.getExtension(filePath).orElse("");
                        request = generateRequest(operation, params[0] + " " + params[1], extension, fileBytes);
                        break;
                    case EXIT:
                        outputStream.close();
                        inputStream.close();
                        readThread.interrupt();
                        socket.close();
                        System.out.println("Goodbye!\n");
                        System.exit(0);
                        break;
                    default:
                        continue;
                }
                outputStream.write(request);
            } catch (ArrayIndexOutOfBoundsException e) {
                logger.warning("error input\n");
            }
        }
    }

    private byte[] generateRequest(Operation op, String param, String dataType, byte[] data) {
        String header = data.length + "\r\n" +
                dataType + "\r\n" +
                op.label + " " + param + "\r\n" +
                "\r\n";
        return Utils.merge(header.getBytes(Utils.CHARSET), data);
    }


    private void resolveResponse(DataInputStream in) throws IOException {
        Header header = Utils.resolveHeader(in);
        if (header.getSize() == -1) {
            System.out.println("Server is down!");
            System.exit(0);
        }

        byte[] attachedData = Utils.readStream(in, header.getSize());

        switch (header.getStatus()) {
            case 403:
                this.isLogin = false;
                System.out.println(new String(attachedData, Utils.CHARSET));
                break;
            case 200:
                this.isLogin = true;
                if (header.getType() == 's') {
                    System.out.println(new String(attachedData, Utils.CHARSET));
                } else if (header.getType() == 'b') {
                    var bytes = Utils.splitByteArray(attachedData);
                    String filename = new String(bytes.get(0));
                    Files.write(Path.of(filename), bytes.get(1));
                    System.out.println("Saved " + filename + "\n");
                }
                break;
            case 233:
                // shutdown code
                System.out.println(new String(attachedData, Utils.CHARSET));
                readThread.interrupt();
                inputStream.close();
                outputStream.close();
                socket.close();
                System.exit(0);
            case 500:
                System.out.print("Error: ");
                System.out.println(new String(attachedData, Utils.CHARSET));
                break;
            default:
                System.out.println(new String(attachedData, Utils.CHARSET));
        }

    }

}
