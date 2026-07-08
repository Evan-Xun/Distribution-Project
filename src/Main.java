import distproject.ui.AppTheme;

import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AppTheme.applyTheme();

            JFrame frame = new JFrame("Distribution Project Launcher");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(560, 220);
            frame.setLocationRelativeTo(null);

            JLabel label = new JLabel("Choose which app to open", SwingConstants.CENTER);
            label.setForeground(AppTheme.TEXT_PRIMARY);
            label.setFont(label.getFont().deriveFont(16f));

            JButton serverButton = new JButton("Open Server");
            serverButton.addActionListener(event -> ServerLauncher.main(new String[0]));

            JButton clientButton = new JButton("Open Client");
            clientButton.addActionListener(event -> ClientLauncher.main(new String[0]));

            JButton simulationButton = new JButton("Open Simulation");
            simulationButton.addActionListener(event -> SimulationLauncher.main(new String[0]));

            JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 10, 0));
            buttonPanel.setOpaque(false);
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 24, 24, 24));
            buttonPanel.add(serverButton);
            buttonPanel.add(clientButton);
            buttonPanel.add(simulationButton);

            JPanel contentPanel = new JPanel(new BorderLayout(12, 12));
            contentPanel.setBackground(AppTheme.APP_BACKGROUND);
            contentPanel.setBorder(BorderFactory.createEmptyBorder(16, 18, 0, 18));
            contentPanel.add(label, BorderLayout.CENTER);
            contentPanel.add(buttonPanel, BorderLayout.SOUTH);

            frame.setContentPane(contentPanel);
            frame.setVisible(true);
        });
    }
}
