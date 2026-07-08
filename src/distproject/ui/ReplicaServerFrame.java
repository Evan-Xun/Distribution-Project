package distproject.ui;

import distproject.server.ReplicaServerApp;

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
import java.awt.Color;
import java.awt.GridLayout;

public class ReplicaServerFrame extends JFrame {
    private final JTextField portField = new JTextField("6001");
    private final JTextArea logArea = new JTextArea();
    private final JTextArea snapshotArea = new JTextArea();
    private ReplicaServerApp replicaServerApp;

    public ReplicaServerFrame() {
        setTitle("Backup Replica Server");
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        buildUi();
    }

    private void buildUi() {
        getContentPane().setBackground(AppTheme.APP_BACKGROUND);
        AppTheme.styleTextField(portField);

        JButton startButton = new JButton("Start Replica");
        JButton stopButton = new JButton("Stop Replica");
        startButton.addActionListener(event -> startReplica());
        stopButton.addActionListener(event -> stopReplica());
        AppTheme.styleSoftButton(startButton, AppTheme.SOFT_GREEN, AppTheme.COMPLETED_COLOR, new Color(0xC8, 0xDD, 0xCE));
        AppTheme.styleSoftButton(stopButton, AppTheme.SOFT_RED, AppTheme.ERROR_COLOR, new Color(0xE4, 0xC9, 0xC9));

        JPanel topPanel = new JPanel(new GridLayout(1, 4, 8, 8));
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        topPanel.add(new JLabel("Replica Port"));
        topPanel.add(portField);
        topPanel.add(startButton);
        topPanel.add(stopButton);

        logArea.setEditable(false);
        snapshotArea.setEditable(false);
        AppTheme.styleTextArea(logArea, true);
        AppTheme.styleTextArea(snapshotArea, false);

        JScrollPane logPane = new JScrollPane(logArea);
        JScrollPane snapshotPane = new JScrollPane(snapshotArea);
        AppTheme.stylePane(logPane, "Replica Log");
        AppTheme.stylePane(snapshotPane, "Latest Replicated Snapshot");

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 12, 12));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        centerPanel.add(logPane);
        centerPanel.add(snapshotPane);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void startReplica() {
        if (replicaServerApp != null && replicaServerApp.isRunning()) {
            appendLog("Replica server already running");
            return;
        }

        int port = Integer.parseInt(portField.getText().trim());
        replicaServerApp = new ReplicaServerApp(port, this::appendLog, this::showSnapshot);
        replicaServerApp.start();
    }

    private void stopReplica() {
        if (replicaServerApp != null) {
            replicaServerApp.stop();
        }
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text + System.lineSeparator()));
    }

    private void showSnapshot(String snapshot) {
        SwingUtilities.invokeLater(() -> snapshotArea.setText(snapshot));
    }
}
