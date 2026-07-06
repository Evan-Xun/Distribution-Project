package distproject.ui;

import distproject.model.Order;
import distproject.server.ServerApp;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;

public class ServerFrame extends JFrame {
    private final JTextField portField = new JTextField("5001");
    private final JTextArea logArea = new JTextArea();
    private final JTextArea orderArea = new JTextArea();
    private final JTextArea kitchenArea = new JTextArea();
    private ServerApp serverApp;

    public ServerFrame() {
        setTitle("Server GUI");
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        buildUi();
    }

    private void buildUi() {
        JButton startButton = new JButton("Start Server");
        JButton stopButton = new JButton("Stop Server");
        JButton restoreButton = new JButton("Restore Backup File");

        startButton.addActionListener(event -> startServer());
        stopButton.addActionListener(event -> stopServer());
        restoreButton.addActionListener(event -> restoreBackupFile());

        JPanel topPanel = new JPanel(new GridLayout(1, 5, 8, 8));
        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        topPanel.add(new JLabel("Port"));
        topPanel.add(portField);
        topPanel.add(startButton);
        topPanel.add(stopButton);
        topPanel.add(restoreButton);

        logArea.setEditable(false);
        orderArea.setEditable(false);
        kitchenArea.setEditable(false);

        JScrollPane logPane = new JScrollPane(logArea);
        JScrollPane orderPane = new JScrollPane(orderArea);
        JScrollPane kitchenPane = new JScrollPane(kitchenArea);
        logPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        orderPane.setBorder(BorderFactory.createTitledBorder("Received Orders"));
        kitchenPane.setBorder(BorderFactory.createTitledBorder("Kitchen Queue / FCFS Scheduling"));

        JPanel centerPanel = new JPanel(new GridLayout(1, 3, 12, 12));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        centerPanel.add(logPane);
        centerPanel.add(orderPane);
        centerPanel.add(kitchenPane);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void startServer() {
        if (serverApp != null && serverApp.isRunning()) {
            appendLog("Server already running");
            return;
        }

        int port = Integer.parseInt(portField.getText().trim());
        serverApp = new ServerApp(port, this::appendLog, this::refreshOrders);
        serverApp.start();
    }

    private void stopServer() {
        if (serverApp != null) {
            serverApp.stop();
        }
    }

    private void restoreBackupFile() {
        if (serverApp == null) {
            appendLog("Start the server before restoring backup.");
            return;
        }
        serverApp.restoreBackupFile();
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text + System.lineSeparator()));
    }

    private void refreshOrders(List<Order> orders) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder orderBuilder = new StringBuilder();
            StringBuilder kitchenBuilder = new StringBuilder();

            for (Order order : orders) {
                orderBuilder.append(order.toDisplayText()).append(System.lineSeparator());
                kitchenBuilder.append(order.getOrderId())
                        .append(" | Table ").append(order.getTableNumber())
                        .append(" | ").append(order.getStatus())
                        .append(" | Total RM ").append(String.format("%.2f", order.getTotal()))
                        .append(System.lineSeparator());
            }

            orderArea.setText(orderBuilder.toString());
            kitchenArea.setText(kitchenBuilder.toString());
        });
    }
}
