package chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ChatServer {
    private final int tcpPort;
    private final int wsPort;
    private ServerSocket tcpServerSocket;
    private ServerSocket wsServerSocket;
    private boolean running = false;

    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final List<String> connectedNicks = Collections.synchronizedList(new ArrayList<>());
    private Consumer<String> logConsumer;

    public ChatServer(int tcpPort, int wsPort, Consumer<String> logConsumer) {
        this.tcpPort = tcpPort;
        this.wsPort = wsPort;
        this.logConsumer = logConsumer != null ? logConsumer : System.out::println;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        running = true;

        tcpServerSocket = new ServerSocket(tcpPort);
        log("Servidor TCP iniciado en el puerto " + tcpPort);

        new Thread(this::listenTcpClients, "TCP-Accept-Thread").start();

        if (wsPort > 0) {
            try {
                wsServerSocket = new ServerSocket(wsPort);
                log("Servidor WebSocket iniciado en el puerto " + wsPort);
                new Thread(this::listenWsClients, "WS-Accept-Thread").start();
            } catch (IOException e) {
                log("Error iniciando servidor WebSocket en el puerto " + wsPort + ": " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        log("Deteniendo servidor...");

        for (ClientHandler client : clients) {
            client.close("Servidor detenido");
        }
        clients.clear();
        connectedNicks.clear();

        try {
            if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
                tcpServerSocket.close();
            }
        } catch (IOException ignored) {}

        try {
            if (wsServerSocket != null && !wsServerSocket.isClosed()) {
                wsServerSocket.close();
            }
        } catch (IOException ignored) {}

        log("Servidor detenido correctamente.");
    }

    public boolean isRunning() {
        return running;
    }

    private void listenTcpClients() {
        while (running) {
            try {
                Socket socket = tcpServerSocket.accept();
                ClientHandler handler = new TcpClientHandler(socket, this);
                new Thread(handler, "ClientHandler-TCP-" + socket.getRemoteSocketAddress()).start();
            } catch (IOException e) {
                if (!running) break;
                log("Error aceptando conexión TCP: " + e.getMessage());
            }
        }
    }

    private void listenWsClients() {
        while (running) {
            try {
                Socket socket = wsServerSocket.accept();
                ClientHandler handler = new WebSocketClientHandler(socket, this);
                new Thread(handler, "ClientHandler-WS-" + socket.getRemoteSocketAddress()).start();
            } catch (IOException e) {
                if (!running) break;
                log("Error aceptando conexión WebSocket: " + e.getMessage());
            }
        }
    }

    public synchronized boolean registerNick(String nick, ClientHandler handler) {
        if (nick == null || nick.trim().isEmpty()) {
            return false;
        }
        String cleanNick = nick.trim();
        synchronized (connectedNicks) {
            for (String existing : connectedNicks) {
                if (existing.equalsIgnoreCase(cleanNick)) {
                    log("Intento de conexión rechazado: Nick '" + cleanNick + "' ya existe.");
                    return false;
                }
            }
            connectedNicks.add(cleanNick);
        }
        clients.add(handler);
        handler.setNick(cleanNick);
        log("Cliente conectado: " + cleanNick + " desde " + handler.getRemoteAddress());

        // Notify client connection accepted
        handler.sendRaw("ACCEPT " + cleanNick);

        // Notify all clients about connection and updated nick list
        broadcast("EVENT " + cleanNick + " se ha conectado.");
        broadcastNickList();
        return true;
    }

    public synchronized void unregisterClient(ClientHandler handler) {
        if (handler == null) return;
        String nick = handler.getNick();
        clients.remove(handler);
        if (nick != null) {
            connectedNicks.remove(nick);
            log("Cliente desconectado: " + nick);
            broadcast("EVENT " + nick + " se ha desconectado.");
            broadcastNickList();
        }
    }

    public void handleMessage(String senderNick, String message) {
        log("Mensaje de [" + senderNick + "]: " + message);
        // Requirement: "Cada vez que un cliente envié un mensaje el servidor reenviara el mensaje a todos los clientes (incluido el propio cliente que lo envió)"
        // Format: "<Nick> dice <mensaje>"
        broadcast("CHAT " + senderNick + " dice " + message);
    }

    public void broadcast(String rawMessage) {
        for (ClientHandler client : clients) {
            client.sendRaw(rawMessage);
        }
    }

    public void broadcastNickList() {
        StringBuilder sb = new StringBuilder("LIST ");
        synchronized (connectedNicks) {
            for (int i = 0; i < connectedNicks.size(); i++) {
                sb.append(connectedNicks.get(i));
                if (i < connectedNicks.size() - 1) {
                    sb.append(",");
                }
            }
        }
        broadcast(sb.toString());
    }

    public void log(String msg) {
        logConsumer.accept(msg);
    }
}
