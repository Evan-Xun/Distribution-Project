package distproject.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

public class OrderModeSelector extends JFrame {
    public OrderModeSelector() {
        setTitle("Welcome");
        setSize(420, 220);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        buildUi();
    }

    private void buildUi() {
        JLabel label = new JLabel("How would you like to order?", SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));

        JButton dineInButton = new JButton("Dine In");
        dineInButton.addActionListener(event -> openClient(ClientFrame.OrderMode.DINE_IN));

        JButton takeawayButton = new JButton("Takeaway");
        takeawayButton.addActionListener(event -> openClient(ClientFrame.OrderMode.TAKEAWAY));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 16, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 24, 24, 24));
        buttonPanel.add(dineInButton);
        buttonPanel.add(takeawayButton);

        setLayout(new BorderLayout(12, 12));
        add(label, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void openClient(ClientFrame.OrderMode mode) {
        dispose();
        new ClientFrame(mode).setVisible(true);
    }
}
