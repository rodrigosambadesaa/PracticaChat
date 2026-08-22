package chat.desktop;

import chat.server.ChatServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServidorChatGUI extends JFrame {
    private JTextField txtPuerto;
    private JButton btnEncender;
    private JTextArea txtLog;
    private ChatServer chatServer;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public ServidorChatGUI() {
        setTitle("Servidor Chat");
        setSize(500, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // Top Panel: Port input + Encender button
        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        pnlTop.add(new JLabel("Puerto:"));

        txtPuerto = new JTextField("9000", 10);
        pnlTop.add(txtPuerto);

        btnEncender = new JButton("Encender");
        pnlTop.add(btnEncender);

        add(pnlTop, BorderLayout.NORTH);

        // Center Panel: JTextArea for logs
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtLog.setBackground(new Color(245, 245, 245));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Eventos del Servidor"));

        add(scrollLog, BorderLayout.CENTER);

        // Event listener for Encender button
        ActionListener encenderListener = e -> toggleServer();
        btnEncender.addActionListener(encenderListener);
        txtPuerto.addActionListener(encenderListener);

        // Window listener to close server cleanly
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (chatServer != null) {
                    chatServer.stop();
                }
            }
        });
    }

    private void toggleServer() {
        if (chatServer != null && chatServer.isRunning()) {
            chatServer.stop();
            btnEncender.setText("Encender");
            txtPuerto.setEnabled(true);
            appendLog("Servidor apagado por el usuario.");
        } else {
            String portStr = txtPuerto.getText().trim();
            try {
                int port = Integer.parseInt(portStr);
                int wsPort = port + 80; // Default WebSocket port (e.g. 9080)
                chatServer = new ChatServer(port, wsPort, this::appendLog);
                chatServer.start();
                btnEncender.setText("Apagar");
                txtPuerto.setEnabled(false);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Número de puerto inválido", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error al iniciar el servidor: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void appendLog(String logMessage) {
        SwingUtilities.invokeLater(() -> {
            String time = timeFormat.format(new Date());
            txtLog.append("[" + time + "] " + logMessage + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new ServidorChatGUI().setVisible(true);
        });
    }
}
