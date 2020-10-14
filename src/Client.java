import model.Header;
import model.Operation;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class Client {
    private final static Logger logger = Logger.getLogger(Client.class.getSimpleName());

    private final static Charset CHARSET = StandardCharsets.UTF_8;

    private final BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));

    private final Lock lock = new ReentrantLock(); // Lock for isLogin flag
    private final Socket socket;
    private boolean isLogin = false;

    public Client(int port) throws IOException {
        socket = new Socket("127.0.0.1", port);

        Runnable readProcess = () -> {
            while (true) {
                try {
                    DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                    resolveResponse(inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(readProcess).start();
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
        client.writeProcess();
    }

    private byte[] authData() {
//        String username = System.console().readLine("Enter username: ");
//        String password = new String(System.console().readPassword("Enter password: "));

        String username = "Yoda";
        String password = "jedi*knight";

        String header = "0\r\nAUTH\r\n";
        String auth_ = header + "ATH " + username + " " + password + "\r\n";

        return auth_.getBytes(StandardCharsets.UTF_8);
    }

    public void writeProcess() throws IOException {
        while (true) {
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            if (isLogin) {
                System.out.println("Enter one of the following commands: CRT, MSG, DLT, EDT, LST, RDT, UPD, DWN, RMV, XIT, SHT:");
                String input = inReader.readLine();
                var splits = input.split("\\s+");
                String cmd = splits[0];
                Operation operation;
                try {
                    operation = Operation.fromString(cmd);
                } catch (IllegalArgumentException e) {
                    logger.warning(e.getLocalizedMessage());
                    continue;
                }
                switch (operation) {
                    case CREATE_THREAD:
                        String threadTitle = splits[1];

                }

            } else {
                outputStream.write(authData());
            }
        }
    }

    private byte[] generateRequest(Operation op, String param, String dataType, byte[] data) {

    }


    private void resolveResponse(DataInputStream in) throws IOException {
        Header header = Utils.resolveHeader(in);

        byte[] attachedData = Utils.readStream(in, header.getSize());

        lock.lock();

        switch (header.getStatus()) {
            case 403:
                try {
                    this.isLogin = false;
                } finally {
                    lock.unlock();
                }
                System.out.println(new String(attachedData, CHARSET));
                break;
            case 200:
                try {
                    this.isLogin = true;
                } finally {
                    lock.unlock();
                }
                System.out.println(new String(attachedData, CHARSET));
                break;
            default:
                System.out.println(new String(attachedData, CHARSET));
        }

    }

}
