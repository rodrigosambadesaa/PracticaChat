package chat.desktop;

import net.i2p.desktop.router.util.ConnectivityAndInternetAccess;
import net.i2p.desktop.router.util.NetworkChangeListener;
import net.i2p.desktop.router.util.NetworkObserver;
import net.i2p.desktop.router.util.NetworkState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

public class ClienteChatGUI extends JFrame {
    // UI Panels (Single Window simulation)
    private JPanel pnlConnection;
    private JPanel pnlChat;

    // Connection components
    private JTextField txtHost;
    private JTextField txtPuerto;
    private JTextField txtNick;
    private JButton btnConectar;
    private JLabel lblNetworkStatus;
    private JButton btnCheckInternet;

    // Chat components
    private JTextArea txtChat;
    private JTextField txtMensaje;
    private JButton btnEnviar;
    private JButton btnDesconectar;
    private JList<String> lstNicks;
    private DefaultListModel<String> modelNicks;

    // Socket & Thread
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread receiveThread;
    private volatile boolean isConnected = false;
    private String currentNick = "";

    // Gist Desktop Connectivity Monitor
    private NetworkObserver networkObserver;

    public ClienteChatGUI() {
        setTitle("Cliente Chat: Conexión");
        setSize(650, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initConnectivity();
        initUI();
    }

    private void initConnectivity() {
        try {
            ConnectivityAndInternetAccess.NetworkObserverConfig config = ConnectivityAndInternetAccess.NetworkObserverConfig.builder()
                    .pollInterval(java.time.Duration.ofSeconds(3))
                    .automaticDiagnosticsOnNetworkChanges(false)
                    .build();

            networkObserver = ConnectivityAndInternetAccess.observeNetwork(
                config,
                new NetworkChangeListener() {
                    @Override
                    public void onNetworkStateChanged(NetworkState state) {
                        SwingUtilities.invokeLater(() -> {
                            boolean usable = state.hasUsableInterface();
                            int count = state.getUsableInterfaces().size();
                            if (lblNetworkStatus != null) {
                                lblNetworkStatus.setText("Red: " + (usable ? "Disponible (" + count + " interfaces)" : "Sin red"));
                                lblNetworkStatus.setForeground(usable ? new Color(0, 128, 0) : Color.RED);
                            }
                        });
                    }
                }
            );
        } catch (Exception e) {
            System.err.println("No se pudo iniciar el observador de conectividad: " + e.getMessage());
        }
    }

    private void initUI() {
        setLayout(new CardLayout());

        // -------------------------------------------------------------
        // PANEL 1: CONEXION (Antes de conectarse)
        // -------------------------------------------------------------
        pnlConnection = new JPanel(new GridBagLayout());
        pnlConnection.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblTitle = new JLabel("Conexión al Servidor de Chat", SwingConstants.CENTER);
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        pnlConnection.add(lblTitle, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        pnlConnection.add(new JLabel("Host / IP:"), gbc);
        txtHost = new JTextField("localhost", 15);
        gbc.gridx = 1; gbc.gridy = 1;
        pnlConnection.add(txtHost, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        pnlConnection.add(new JLabel("Puerto:"), gbc);
        txtPuerto = new JTextField("9000", 15);
        gbc.gridx = 1; gbc.gridy = 2;
        pnlConnection.add(txtPuerto, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        pnlConnection.add(new JLabel("Nick:"), gbc);
        txtNick = new JTextField(15);
        gbc.gridx = 1; gbc.gridy = 3;
        pnlConnection.add(txtNick, gbc);

        btnConectar = new JButton("Conectar");
        btnConectar.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnConectar.setBackground(new Color(41, 128, 185));
        btnConectar.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        pnlConnection.add(btnConectar, gbc);

        // Network Status Badge (Gist Desktop integration)
        JPanel pnlNet = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        lblNetworkStatus = new JLabel("Red: Comprobando...", SwingConstants.CENTER);
        btnCheckInternet = new JButton("Diagnóstico Red");
        btnCheckInternet.setMargin(new Insets(2, 6, 2, 6));
        pnlNet.add(lblNetworkStatus);
        pnlNet.add(btnCheckInternet);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        pnlConnection.add(pnlNet, gbc);

        // -------------------------------------------------------------
        // PANEL 2: CHAT (Una vez conectado)
        // -------------------------------------------------------------
        pnlChat = new JPanel(new BorderLayout(10, 10));
        pnlChat.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top bar in Chat View: Desconectar button
        JPanel pnlChatTop = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnDesconectar = new JButton("Desconectar");
        pnlChatTop.add(btnDesconectar);
        pnlChat.add(pnlChatTop, BorderLayout.NORTH);

        // Center Chat Display
        txtChat = new JTextArea();
        txtChat.setEditable(false);
        txtChat.setLineWrap(true);
        txtChat.setWrapStyleWord(true);
        JScrollPane scrollChat = new JScrollPane(txtChat);
        scrollChat.setBorder(BorderFactory.createTitledBorder("Conversación"));
        pnlChat.add(scrollChat, BorderLayout.CENTER);

        // Right Panel: Online Nicks List
        modelNicks = new DefaultListModel<>();
        lstNicks = new JList<>(modelNicks);
        JScrollPane scrollNicks = new JScrollPane(lstNicks);
        scrollNicks.setPreferredSize(new Dimension(150, 0));
        scrollNicks.setBorder(BorderFactory.createTitledBorder("Conectados"));
        pnlChat.add(scrollNicks, BorderLayout.EAST);

        // Bottom Panel: Message input + Enviar button
        JPanel pnlBottom = new JPanel(new BorderLayout(5, 5));
        txtMensaje = new JTextField();
        btnEnviar = new JButton("Enviar");
        pnlBottom.add(txtMensaje, BorderLayout.CENTER);
        pnlBottom.add(btnEnviar, BorderLayout.EAST);
        pnlChat.add(pnlBottom, BorderLayout.SOUTH);

        // Add panels to CardLayout frame
        add(pnlConnection, "CONNECTION");
        add(pnlChat, "CHAT");

        // Action Listeners
        ActionListener conectarAction = e -> conectar();
        btnConectar.addActionListener(conectarAction);
        txtNick.addActionListener(conectarAction);

        ActionListener enviarAction = e -> enviarMensaje();
        btnEnviar.addActionListener(enviarAction);
        txtMensaje.addActionListener(enviarAction);

        btnDesconectar.addActionListener(e -> desconectar());

        btnCheckInternet.addActionListener(e -> runInternetDiagnostic());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                desconectar();
                if (networkObserver != null) {
                    networkObserver.close();
                }
            }
        });
    }

    private void runInternetDiagnostic() {
        btnCheckInternet.setEnabled(false);
        lblNetworkStatus.setText("Diagnóstico en curso...");
        ConnectivityAndInternetAccess.checkInternetAsync(result -> {
            SwingUtilities.invokeLater(() -> {
                btnCheckInternet.setEnabled(true);
                if (result.isReachable()) {
                    lblNetworkStatus.setText("Internet OK (" + result.getReachedHost() + ")");
                    lblNetworkStatus.setForeground(new Color(0, 128, 0));
                } else {
                    lblNetworkStatus.setText("Sin acceso a Internet");
                    lblNetworkStatus.setForeground(Color.RED);
                }
            });
        });
    }

    private void conectar() {
        String host = txtHost.getText().trim();
        String portStr = txtPuerto.getText().trim();
        String nick = txtNick.getText().trim();

        if (host.isEmpty() || portStr.isEmpty() || nick.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debe completar todos los campos", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Puerto inválido", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // Send NICK request
            writer.println("NICK " + nick);

            // Read server response
            String response = reader.readLine();
            if (response != null && response.startsWith("ACCEPT ")) {
                isConnected = true;
                currentNick = nick;

                // PDF Requirement: Window title changes after connection
                setTitle("Cliente Chat conectado: Nick " + currentNick);

                // Show Chat view
                CardLayout cl = (CardLayout) getContentPane().getLayout();
                cl.show(getContentPane(), "CHAT");

                // Start receiver thread
                receiveThread = new Thread(this::listenServer, "Client-Receiver");
                receiveThread.start();
            } else {
                // PDF Requirement: JOptionPane.showMessageDialog(null,"ERROR: Nick Existente");
                socket.close();
                JOptionPane.showMessageDialog(null, "ERROR: Nick Existente");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error de conexión: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void listenServer() {
        try {
            String line;
            while (isConnected && (line = reader.readLine()) != null) {
                final String msg = line.trim();
                SwingUtilities.invokeLater(() -> processServerMessage(msg));
            }
        } catch (IOException e) {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> {
                    appendChat("--- Conexión perdida con el servidor ---");
                    desconectar();
                });
            }
        }
    }

    private void processServerMessage(String msg) {
        if (msg.startsWith("CHAT ")) {
            String chatContent = msg.substring(5);
            appendChat(chatContent);
        } else if (msg.startsWith("EVENT ")) {
            String eventContent = msg.substring(6);
            appendChat("*** " + eventContent + " ***");
        } else if (msg.startsWith("LIST ")) {
            String nicksStr = msg.substring(5);
            modelNicks.clear();
            if (!nicksStr.isEmpty()) {
                String[] nicks = nicksStr.split(",");
                for (String n : nicks) {
                    modelNicks.addElement(n.trim());
                }
            }
        }
    }

    private void enviarMensaje() {
        String msg = txtMensaje.getText().trim();
        if (!msg.isEmpty() && isConnected && writer != null) {
            writer.println("MSG " + msg);
            txtMensaje.setText("");
        }
    }

    private void desconectar() {
        if (isConnected) {
            isConnected = false;
            if (writer != null) {
                writer.println("DISCONNECT");
            }
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        // PDF Requirement: Return to original connection view
        setTitle("Cliente Chat: Conexión");
        modelNicks.clear();
        txtChat.setText("");

        CardLayout cl = (CardLayout) getContentPane().getLayout();
        cl.show(getContentPane(), "CONNECTION");
    }

    private void appendChat(String text) {
        txtChat.append(text + "\n");
        txtChat.setCaretPosition(txtChat.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new ClienteChatGUI().setVisible(true);
        });
    }
}
