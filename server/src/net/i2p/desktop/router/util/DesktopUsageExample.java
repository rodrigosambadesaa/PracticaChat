package net.i2p.desktop.router.util.example;

import net.i2p.desktop.router.util.ConnectivityAndInternetAccess;
import net.i2p.desktop.router.util.ConnectivityAndInternetAccess.NetworkObserverConfig;
import net.i2p.desktop.router.util.NetworkObserver;
import java.time.Duration;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Font;

public final class DesktopUsageExample {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Connectivity Check Desktop");
            JLabel label = new JLabel("Observing network state...", SwingConstants.CENTER);
            label.setFont(new Font("SansSerif", Font.PLAIN, 16));
            frame.add(label);
            frame.setSize(500, 150);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);

            // Iniciar observador ligero de red (0 tráfico mientras la red está estable)
            NetworkObserverConfig config = NetworkObserverConfig.builder()
                    .pollInterval(Duration.ofSeconds(3))
                    .automaticDiagnosticsOnNetworkChanges(false)
                    .build();

            NetworkObserver observer = ConnectivityAndInternetAccess.observeNetwork(config, state -> {
                SwingUtilities.invokeLater(() -> {
                    if (state.hasUsableInterface()) {
                        label.setText("Network active: " + state.getUsableInterfaces().size() + " interface(s)");
                    } else {
                        label.setText("No network connection");
                    }
                });
            });

            // Actualizar etiqueta inicial
            label.setText("Initial state: " + observer.getLastKnownNetworkState().getUsableInterfaces().size() + " interface(s)");
        });
    }
}
