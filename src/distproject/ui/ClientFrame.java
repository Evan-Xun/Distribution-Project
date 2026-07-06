package distproject.ui;

import distproject.client.ClientApp;
import distproject.model.MenuItem;
import distproject.model.Order;
import distproject.model.OrderItem;
import distproject.model.TableCart;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientFrame extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1");
    private final JTextField portField = new JTextField("5001");
    private final JTextField tableField = new JTextField("1");
    private final JCheckBox takeawayCheckBox = new JCheckBox("Takeaway Priority");
    private final JTextArea cartArea = new JTextArea();
    private final JTextArea orderStatusArea = new JTextArea();
    private final JTextArea statusArea = new JTextArea();
    private final DefaultTableModel menuTableModel = new DefaultTableModel(
            new String[]{"ID", "Name", "Price", "Stock"}, 0
    );
    private final JTable menuTable = new JTable(menuTableModel);

    private final ClientApp clientApp = new ClientApp();
    private final List<MenuItem> currentMenu = new ArrayList<>();
    private TableCart cart = new TableCart(1);
    private boolean connected;

    public ClientFrame() {
        setTitle("Client GUI");
        setSize(950, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        buildUi();
    }

    private void buildUi() {
        JButton connectButton = new JButton("Connect");
        JButton changeTableButton = new JButton("Change Table");
        JButton refreshButton = new JButton("Refresh Menu");
        JButton addButton = new JButton("Add Selected Item");
        JButton submitButton = new JButton("Submit Order");

        connectButton.addActionListener(event -> connect());
        changeTableButton.addActionListener(event -> changeTable());
        refreshButton.addActionListener(event -> requestMenu());
        addButton.addActionListener(event -> addSelectedItem());
        submitButton.addActionListener(event -> submitOrder());

        JPanel topPanel = new JPanel(new GridLayout(3, 4, 8, 8));
        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        topPanel.add(new JLabel("Host"));
        topPanel.add(hostField);
        topPanel.add(new JLabel("Port"));
        topPanel.add(portField);
        topPanel.add(new JLabel("Table No."));
        topPanel.add(tableField);
        topPanel.add(connectButton);
        topPanel.add(changeTableButton);
        topPanel.add(new JLabel("Order Type"));
        topPanel.add(takeawayCheckBox);
        topPanel.add(new JLabel(""));
        topPanel.add(new JLabel(""));

        JScrollPane menuPane = new JScrollPane(menuTable);
        menuPane.setBorder(BorderFactory.createTitledBorder("Menu"));

        cartArea.setEditable(false);
        JScrollPane cartPane = new JScrollPane(cartArea);
        cartPane.setBorder(BorderFactory.createTitledBorder("Shared Cart"));

        orderStatusArea.setEditable(false);
        JScrollPane orderStatusPane = new JScrollPane(orderStatusArea);
        orderStatusPane.setBorder(BorderFactory.createTitledBorder("Order Status Updates"));

        statusArea.setEditable(false);
        JScrollPane statusPane = new JScrollPane(statusArea);
        statusPane.setBorder(BorderFactory.createTitledBorder("Status"));

        JPanel rightPanel = new JPanel(new GridLayout(3, 1, 8, 8));
        rightPanel.add(cartPane);
        rightPanel.add(orderStatusPane);
        rightPanel.add(statusPane);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 12, 12));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        centerPanel.add(menuPane);
        centerPanel.add(rightPanel);

        JPanel bottomPanel = new JPanel(new GridLayout(1, 3, 8, 8));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        bottomPanel.add(refreshButton);
        bottomPanel.add(addButton);
        bottomPanel.add(submitButton);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void connect() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            int tableNumber = Integer.parseInt(tableField.getText().trim());
            cart = new TableCart(tableNumber);
            refreshCartView();
            clientApp.connect(
                    hostField.getText().trim(),
                    port,
                    this::updateMenu,
                    this::appendStatus,
                    this::handleOrderReceived,
                    this::handleTableAssigned,
                    this::handleCartUpdated,
                    this::showErrorDialog
            );
            connected = true;
            clientApp.registerTable(tableNumber);
        } catch (IOException exception) {
            appendStatus("Connect failed: " + exception.getMessage());
        }
    }

    private void changeTable() {
        if (!connected) {
            JOptionPane.showMessageDialog(this, "Please connect to the server first.");
            return;
        }

        try {
            int tableNumber = Integer.parseInt(tableField.getText().trim());
            clientApp.registerTable(tableNumber);
        } catch (IOException exception) {
            appendStatus("Failed to change table: " + exception.getMessage());
        }
    }

    private void requestMenu() {
        try {
            clientApp.requestMenu();
        } catch (IOException exception) {
            appendStatus("Failed to request menu: " + exception.getMessage());
        }
    }

    private void addSelectedItem() {
        int selectedRow = menuTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentMenu.size()) {
            JOptionPane.showMessageDialog(this, "Please select a menu item first.");
            return;
        }

        MenuItem menuItem = currentMenu.get(selectedRow);
        try {
            clientApp.addToSharedCart(menuItem.getId());
        } catch (IOException exception) {
            appendStatus("Failed to add item: " + exception.getMessage());
        }
    }

    private void submitOrder() {
        if (cart.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Cart is empty.");
            return;
        }

        try {
            clientApp.submitOrder(cart, takeawayCheckBox.isSelected());
        } catch (IOException exception) {
            appendStatus("Submit failed: " + exception.getMessage());
        }
    }

    private void updateMenu(List<MenuItem> menuItems) {
        SwingUtilities.invokeLater(() -> {
            currentMenu.clear();
            currentMenu.addAll(menuItems);
            menuTableModel.setRowCount(0);

            for (MenuItem item : menuItems) {
                menuTableModel.addRow(new Object[]{
                        item.getId(),
                        item.getName(),
                        String.format("RM %.2f", item.getPrice()),
                        item.getStock()
                });
            }

            appendStatus("Menu refreshed. Current items: " + menuItems.size());
        });
    }

    private void refreshCartView() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder builder = new StringBuilder();
            builder.append("Table ").append(cart.getTableNumber()).append(System.lineSeparator());

            for (OrderItem item : cart.getItems()) {
                builder.append("- ").append(item).append(System.lineSeparator());
            }

            builder.append(System.lineSeparator())
                    .append("Total: RM ")
                    .append(String.format("%.2f", cart.getTotal()));
            cartArea.setText(builder.toString());
        });
    }

    private void handleOrderReceived(Order order) {
        SwingUtilities.invokeLater(() -> {
            orderStatusArea.append("Order " + order.getOrderId()
                    + " | Table " + order.getTableNumber()
                    + " | " + order.getOrderType()
                    + " | " + order.getStatus()
                    + System.lineSeparator());
            appendStatus("Order " + order.getOrderId() + " status: " + order.getStatus());
        });
    }

    private void handleTableAssigned(Integer tableNumber) {
        SwingUtilities.invokeLater(() -> {
            tableField.setText(String.valueOf(tableNumber));
            cart = new TableCart(tableNumber);
            refreshCartView();
        });
    }

    private void handleCartUpdated(TableCart updatedCart) {
        SwingUtilities.invokeLater(() -> {
            cart = updatedCart;
            refreshCartView();
        });
    }

    private void showErrorDialog(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void appendStatus(String text) {
        SwingUtilities.invokeLater(() -> statusArea.append(text + System.lineSeparator()));
    }
}
