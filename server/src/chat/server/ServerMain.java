package chat.server;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ServerMain {
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        int tcpPort = 9000;
        int wsPort = 9080;

        if (args.length > 0) {
            try { tcpPort = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }
        if (args.length > 1) {
            try { wsPort = Integer.parseInt(args[1]); } catch (Exception ignored) {}
        }

        System.out.println("=== INICIANDO SERVIDOR PRÁCTICA CHAT DOCKERIZADO ===");
        System.out.println("Puerto TCP: " + tcpPort);
        System.out.println("Puerto WebSocket: " + wsPort);

        ChatServer server = new ChatServer(tcpPort, wsPort, msg -> {
            String time = timeFormat.format(new Date());
            System.out.println("[" + time + "] " + msg);
        });

        try {
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Apagando servidor de chat...");
                server.stop();
            }));
            // Keep main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Error fatal en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
