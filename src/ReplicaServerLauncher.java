import distproject.ui.AppTheme;
import distproject.ui.ReplicaServerFrame;

import javax.swing.SwingUtilities;

public class ReplicaServerLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AppTheme.applyTheme();
            new ReplicaServerFrame().setVisible(true);
        });
    }
}
