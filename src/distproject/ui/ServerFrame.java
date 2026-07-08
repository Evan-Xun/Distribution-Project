package distproject.ui;

import distproject.model.Order;
import distproject.server.ServerApp;
import distproject.server.ServerContext;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;

public class ServerFrame extends JFrame {
    private final JTextField portField = new JTextField("5001");
    private final JTextArea logArea = new JTextArea();
    private final JTextArea orderArea = new JTextArea();
    private final DefaultTableModel queueTableModel = new DefaultTableModel(
            new String[]{"Rank", "Order ID", "Type", "Source", "Wait", "Priority Score", "Items", "Seq"}, 0
    );
    private final JTable queueTable = new JTable(queueTableModel);
    private final DefaultTableModel stationTableModel = new DefaultTableModel(
            new String[]{"Station", "Order ID", "Type", "Source", "Item", "Item Status"}, 0
    );
    private final JTable stationTable = new JTable(stationTableModel);
    private final Timer kitchenViewTimer = new Timer(1000, event -> refreshKitchenView());
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
        queueTable.setEnabled(false);
        stationTable.setEnabled(false);

        JScrollPane logPane = new JScrollPane(logArea);
        JScrollPane queuePane = new JScrollPane(queueTable);
        JScrollPane stationPane = new JScrollPane(stationTable);
        JScrollPane orderPane = new JScrollPane(orderArea);
        logPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        queuePane.setBorder(BorderFactory.createTitledBorder("Kitchen Queue - initial takeaway priority + dine-in aging"));
        stationPane.setBorder(BorderFactory.createTitledBorder("Kitchen Stations"));
        orderPane.setBorder(BorderFactory.createTitledBorder("Received Orders"));

        JPanel rightPanel = new JPanel(new GridLayout(3, 1, 8, 8));
        rightPanel.add(queuePane);
        rightPanel.add(stationPane);
        rightPanel.add(orderPane);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 12, 12));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        centerPanel.add(logPane);
        centerPanel.add(rightPanel);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        kitchenViewTimer.start();
    }

    private void startServer() {
        if (serverApp != null && serverApp.isRunning()) {
            appendLog("Server already running");
            return;
        }

        int port = Integer.parseInt(portField.getText().trim());
        serverApp = new ServerApp(port, this::appendLog, this::refreshOrders);
        serverApp.start();
        refreshKitchenView();
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

            for (Order order : orders) {
                orderBuilder.append(order.toDisplayText()).append(System.lineSeparator());
            }

            orderArea.setText(orderBuilder.toString());
        });
    }

    private void refreshKitchenView() {
        SwingUtilities.invokeLater(() -> {
            queueTableModel.setRowCount(0);
            stationTableModel.setRowCount(0);

            if (serverApp == null) {
                return;
            }

            for (ServerContext.KitchenQueueEntry entry : serverApp.getContext().getKitchenQueueView()) {
                queueTableModel.addRow(new Object[]{
                        entry.rank(),
                        entry.orderId(),
                        entry.orderType(),
                        entry.source(),
                        entry.waitingSeconds() + "s",
                        entry.priorityScore(),
                        entry.itemCount(),
                        entry.sequenceNumber()
                });
            }

            for (ServerApp.KitchenStationStatus status : serverApp.getKitchenStationStatuses()) {
                stationTableModel.addRow(new Object[]{
                        status.stationNumber(),
                        status.orderId(),
                        status.orderType(),
                        status.source(),
                        status.itemName(),
                        status.itemStatus()
                });
            }
        });
    }
}
