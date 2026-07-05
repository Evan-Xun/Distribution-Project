import distproject.ui.ClientFrame;

import javax.swing.SwingUtilities;

public class ClientLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientFrame().setVisible(true));
    }
}
