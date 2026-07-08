package distproject.ui;

import distproject.simulation.SimulationService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.GridLayout;

public class SimulationFrame extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1");
    private final JTextField portField = new JTextField("5001");
    private final JTextField tableAField = new JTextField("9");
    private final JTextField tableBField = new JTextField("10");
    private final JTextField customerCountField = new JTextField("4");
    private final JTextField itemIdField = new JTextField("M005");
    private final JTextArea logArea = new JTextArea();
    private final JButton cartConflictButton = new JButton("Run Same-Table Add/Remove");
    private final JButton submitConflictButton = new JButton("Run Same-Table Submit");
    private final JButton stockConflictButton = new JButton("Run Cross-Table Stock Conflict");

    private final SimulationService simulationService = new SimulationService();

    public SimulationFrame() {
        setTitle("Simulation GUI");
        setSize(980, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        buildUi();
    }

    private void buildUi() {
        JPanel configPanel = new JPanel(new GridLayout(3, 4, 8, 8));
        configPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        configPanel.add(new JLabel("Host"));
        configPanel.add(hostField);
        configPanel.add(new JLabel("Port"));
        configPanel.add(portField);
        configPanel.add(new JLabel("Table No."));
        configPanel.add(tableAField);
        configPanel.add(new JLabel("Table No."));
        configPanel.add(tableBField);
        configPanel.add(new JLabel("Customer Count"));
        configPanel.add(customerCountField);
        configPanel.add(new JLabel("Item ID"));
        configPanel.add(itemIdField);

        JPanel actionPanel = new JPanel(new GridLayout(1, 3, 12, 12));
        actionPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        cartConflictButton.addActionListener(event -> runScenario(Scenario.CART_CONFLICT));
        submitConflictButton.addActionListener(event -> runScenario(Scenario.SAME_TABLE_SUBMIT));
        stockConflictButton.addActionListener(event -> runScenario(Scenario.CROSS_TABLE_STOCK));
        actionPanel.add(cartConflictButton);
        actionPanel.add(submitConflictButton);
        actionPanel.add(stockConflictButton);

        logArea.setEditable(false);
        JScrollPane logPane = new JScrollPane(logArea);
        logPane.setBorder(BorderFactory.createTitledBorder("Simulation Output"));

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(configPanel, BorderLayout.NORTH);
        northPanel.add(actionPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(northPanel, BorderLayout.NORTH);
        add(logPane, BorderLayout.CENTER);
    }

    private void runScenario(Scenario scenario) {
        SimulationService.SimulationRequest request;
        try {
            request = new SimulationService.SimulationRequest(
                    hostField.getText().trim(),
                    Integer.parseInt(portField.getText().trim()),
                    Integer.parseInt(tableAField.getText().trim()),
                    Integer.parseInt(tableBField.getText().trim()),
                    Integer.parseInt(customerCountField.getText().trim()),
                    itemIdField.getText().trim()
            );
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "Please enter valid numeric values for port, tables, and customer count.");
            return;
        }

        logArea.setText("");
        setButtonsEnabled(false);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("Starting scenario: " + scenario.label);
                publish("");
                switch (scenario) {
                    case CART_CONFLICT -> simulationService.runSameTableCartConflict(request, message -> publish(message));
                    case SAME_TABLE_SUBMIT -> simulationService.runSameTableSubmitConflict(request, message -> publish(message));
                    case CROSS_TABLE_STOCK -> simulationService.runCrossTableStockConflict(request, message -> publish(message));
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String chunk : chunks) {
                    logArea.append(chunk + System.lineSeparator());
                }
            }

            @Override
            protected void done() {
                setButtonsEnabled(true);
                try {
                    get();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    logArea.append("Simulation failed: " + cause.getMessage() + System.lineSeparator());
                }
                SwingUtilities.invokeLater(() -> logArea.setCaretPosition(logArea.getDocument().getLength()));
            }
        };
        worker.execute();
    }

    private void setButtonsEnabled(boolean enabled) {
        cartConflictButton.setEnabled(enabled);
        submitConflictButton.setEnabled(enabled);
        stockConflictButton.setEnabled(enabled);
    }

    private enum Scenario {
        CART_CONFLICT("Same-table add/remove"),
        SAME_TABLE_SUBMIT("Same-table submit"),
        CROSS_TABLE_STOCK("Cross-table stock conflict");

        private final String label;

        Scenario(String label) {
            this.label = label;
        }
    }
}
