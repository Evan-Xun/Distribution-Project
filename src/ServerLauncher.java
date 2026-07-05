import distproject.ui.ServerFrame;

import javax.swing.SwingUtilities;

public class ServerLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerFrame().setVisible(true));
    }
}
