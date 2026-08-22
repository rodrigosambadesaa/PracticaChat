package chat.server;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSocketClientHandler extends ClientHandler {
    private final Socket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean connected = true;

    public WebSocketClientHandler(Socket socket, ChatServer server) {
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
            in = socket.getInputStream();
            out = socket.getOutputStream();

            if (!doHandshake()) {
                close("Handshake WebSocket fallido");
                return;
            }

            while (connected) {
                String message = readFrame();
                if (message == null) break;
                message = message.trim();
                if (message.isEmpty()) continue;

                if (message.startsWith("NICK ")) {
                    String requestedNick = message.substring(5).trim();
                    if (!server.registerNick(requestedNick, this)) {
                        sendRaw("ERROR ERROR: Nick Existente");
                        break;
                    }
                } else if (message.startsWith("MSG ")) {
                    String chatMsg = message.substring(4);
                    if (nick != null) {
                        server.handleMessage(nick, chatMsg);
                    }
                } else if (message.equals("DISCONNECT")) {
                    break;
                }
            }
        } catch (Exception e) {
            // Error or socket closed
        } finally {
            close("WebSocket cerrado");
        }
    }

    private boolean doHandshake() throws Exception {
        Scanner s = new Scanner(in, "UTF-8").useDelimiter("\r\n\r\n");
        if (!s.hasNext()) return false;
        String data = s.next();

        Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
        if (match.find()) {
            String key = match.group(1).trim();
            byte[] responseKey = MessageDigest.getInstance("SHA-1").digest(
                    (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8));
            String accept = Base64.getEncoder().encodeToString(responseKey);

            byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8);

            out.write(response);
            out.flush();
            return true;
        }
        return false;
    }

    private String readFrame() throws IOException {
        int b1 = in.read();
        if (b1 == -1) return null;
        int b2 = in.read();
        if (b2 == -1) return null;

        int opcode = b1 & 0x0F;
        if (opcode == 0x8) { // Connection Close frame
            return null;
        }

        boolean masked = (b2 & 0x80) != 0;
        long payloadLen = b2 & 0x7F;

        if (payloadLen == 126) {
            int byte1 = in.read();
            int byte2 = in.read();
            if (byte1 == -1 || byte2 == -1) return null;
            payloadLen = ((byte1 & 0xFF) << 8) | (byte2 & 0xFF);
        } else if (payloadLen == 127) {
            long len = 0;
            for (int i = 0; i < 8; i++) {
                int b = in.read();
                if (b == -1) return null;
                len = (len << 8) | (b & 0xFF);
            }
            payloadLen = len;
        }

        byte[] key = new byte[4];
        if (masked) {
            if (in.read(key, 0, 4) < 4) return null;
        }

        byte[] payload = new byte[(int) payloadLen];
        int bytesRead = 0;
        while (bytesRead < payloadLen) {
            int read = in.read(payload, bytesRead, (int) payloadLen - bytesRead);
            if (read == -1) return null;
            bytesRead += read;
        }

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ key[i % 4]);
            }
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    @Override
    public synchronized void sendRaw(String message) {
        if (out != null && connected) {
            try {
                byte[] rawData = message.getBytes(StandardCharsets.UTF_8);
                int len = rawData.length;

                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                frame.write(0x81); // Text frame, FIN bit set

                if (len <= 125) {
                    frame.write(len);
                } else if (len <= 65535) {
                    frame.write(126);
                    frame.write((len >> 8) & 0xFF);
                    frame.write(len & 0xFF);
                } else {
                    frame.write(127);
                    for (int i = 7; i >= 0; i--) {
                        frame.write((int) ((len >> (i * 8)) & 0xFF));
                    }
                }

                frame.write(rawData);
                out.write(frame.toByteArray());
                out.flush();
            } catch (IOException e) {
                close("Error de escritura en WebSocket");
            }
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
