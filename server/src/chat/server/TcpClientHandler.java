package chat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpClientHandler extends ClientHandler {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected = true;

    public TcpClientHandler(Socket socket, ChatServer server) {
        super(server);
        this.socket = socket;
    }

    @Override
    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while (connected && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("NICK ")) {
                    String requestedNick = line.substring(5).trim();
                    if (!server.registerNick(requestedNick, this)) {
                        out.println("ERROR ERROR: Nick Existente");
                        break;
                    }
                } else if (line.startsWith("MSG ")) {
                    String message = line.substring(4);
                    if (nick != null) {
                        server.handleMessage(nick, message);
                    }
                } else if (line.equals("DISCONNECT")) {
                    break;
                }
            }
        } catch (IOException e) {
            // Socket closed or connection reset
        } finally {
            close("Conexión finalizada");
        }
    }

    @Override
    public void sendRaw(String message) {
        if (out != null && connected) {
            out.println(message);
        }
    }

    @Override
    public synchronized void close(String reason) {
        if (!connected) return;
        connected = false;
        server.unregisterClient(this);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }
}
