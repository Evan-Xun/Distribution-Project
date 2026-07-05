import javax.swing.JButton;
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
            JFrame frame = new JFrame("Distribution Project Launcher");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(420, 180);
            frame.setLocationRelativeTo(null);

            JLabel label = new JLabel("Choose which app to open", SwingConstants.CENTER);

            JButton serverButton = new JButton("Open Server");
            serverButton.addActionListener(event -> ServerLauncher.main(new String[0]));

            JButton clientButton = new JButton("Open Client");
            clientButton.addActionListener(event -> ClientLauncher.main(new String[0]));

            JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 12, 0));
            buttonPanel.add(serverButton);
            buttonPanel.add(clientButton);

            frame.setLayout(new BorderLayout(12, 12));
            frame.add(label, BorderLayout.CENTER);
            frame.add(buttonPanel, BorderLayout.SOUTH);
            frame.setVisible(true);
        });
    }
}
