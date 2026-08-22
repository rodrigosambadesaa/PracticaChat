package chat.server;

public abstract class ClientHandler implements Runnable {
    protected final ChatServer server;
    protected String nick;

    public ClientHandler(ChatServer server) {
        this.server = server;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public abstract String getRemoteAddress();
    public abstract void sendRaw(String message);
    public abstract void close(String reason);
}
