import distproject.ui.AppTheme;
import distproject.ui.SimulationFrame;

import javax.swing.SwingUtilities;

public class SimulationLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AppTheme.applyTheme();
            new SimulationFrame().setVisible(true);
        });
    }
}
