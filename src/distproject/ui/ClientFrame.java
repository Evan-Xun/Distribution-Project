package distproject.ui;

import distproject.client.ClientApp;
import distproject.model.MenuItem;
import distproject.model.Order;
import distproject.model.OrderItem;
import distproject.model.TableCart;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClientFrame extends JFrame {
    public enum OrderMode { DINE_IN, TAKEAWAY }

    private final OrderMode mode;

    private final JTextField hostField = new JTextField("127.0.0.1");
    private final JTextField portField = new JTextField("5001");
    private final JTextField tableField = new JTextField("1");
    private final JTextArea cartArea = new JTextArea();
    private final JPanel ordersPanel = new JPanel();
    private final JTextArea statusArea = new JTextArea();
    private final DefaultTableModel menuTableModel = new DefaultTableModel(
            new String[]{"ID", "Name", "Price", "Stock"}, 0
    );
    private final JTable menuTable = new JTable(menuTableModel);

    private final ClientApp clientApp = new ClientApp();
    private final List<MenuItem> currentMenu = new ArrayList<>();
    private final Map<String, Order> ordersById = new LinkedHashMap<>();
    private TableCart cart = new TableCart(1);
    private boolean connected;

    public ClientFrame(OrderMode mode) {
        this.mode = mode;
        setTitle(mode == OrderMode.DINE_IN ? "Client GUI - Dine In" : "Client GUI - Takeaway");
        setSize(950, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        buildUi();
    }

    private void buildUi() {
        JPanel topPanel = buildTopPanel();

        JScrollPane menuPane = new JScrollPane(menuTable);
        menuPane.setBorder(BorderFactory.createTitledBorder("Menu"));

        cartArea.setEditable(false);
        JScrollPane cartPane = new JScrollPane(cartArea);
        cartPane.setBorder(BorderFactory.createTitledBorder(
                mode == OrderMode.DINE_IN ? "Shared Cart" : "Your Cart"));

        ordersPanel.setLayout(new BoxLayout(ordersPanel, BoxLayout.Y_AXIS));
        JScrollPane orderStatusPane = new JScrollPane(ordersPanel);
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

        JButton refreshButton = new JButton("Refresh Menu");
        JButton addButton = new JButton("Add Selected Item");
        JButton removeButton = new JButton("Remove Selected Item");
        JButton submitButton = new JButton("Submit Order");
        JButton checkoutButton = new JButton("Checkout");
        refreshButton.addActionListener(event -> requestMenu());
        addButton.addActionListener(event -> addSelectedItem());
        removeButton.addActionListener(event -> removeSelectedItem());
        submitButton.addActionListener(event -> submitOrder());
        checkoutButton.addActionListener(event -> checkout());

        JPanel bottomPanel = new JPanel(new GridLayout(1, 5, 8, 8));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        bottomPanel.add(refreshButton);
        bottomPanel.add(addButton);
        bottomPanel.add(removeButton);
        bottomPanel.add(submitButton);
        bottomPanel.add(checkoutButton);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel buildTopPanel() {
        JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(event -> connect());

        JPanel topPanel;
        if (mode == OrderMode.DINE_IN) {
            JButton changeTableButton = new JButton("Change Table");
            changeTableButton.addActionListener(event -> changeTable());

            topPanel = new JPanel(new GridLayout(2, 4, 8, 8));
            topPanel.add(new JLabel("Host"));
            topPanel.add(hostField);
            topPanel.add(new JLabel("Port"));
            topPanel.add(portField);
            topPanel.add(new JLabel("Table No."));
            topPanel.add(tableField);
            topPanel.add(changeTableButton);
            topPanel.add(connectButton);
        } else {
            topPanel = new JPanel(new GridLayout(1, 5, 8, 8));
            topPanel.add(new JLabel("Host"));
            topPanel.add(hostField);
            topPanel.add(new JLabel("Port"));
            topPanel.add(portField);
            topPanel.add(connectButton);
        }

        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return topPanel;
    }

    private void connect() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            clientApp.connect(
                    hostField.getText().trim(),
                    port,
                    this::updateMenu,
                    this::appendStatus,
                    this::handleOrderReceived,
                    this::handleTableAssigned,
                    this::handleCartUpdated,
                    this::showErrorDialog,
                    this::handleCheckoutCompleted
            );
            connected = true;

            if (mode == OrderMode.DINE_IN) {
                int tableNumber = Integer.parseInt(tableField.getText().trim());
                cart = new TableCart(tableNumber);
                refreshCartView();
                clientApp.registerTable(tableNumber);
            } else {
                clientApp.registerTakeaway();
            }
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

    private void removeSelectedItem() {
        int selectedRow = menuTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentMenu.size()) {
            JOptionPane.showMessageDialog(this, "Please select a menu item first.");
            return;
        }

        MenuItem menuItem = currentMenu.get(selectedRow);
        try {
            clientApp.removeFromSharedCart(menuItem.getId());
        } catch (IOException exception) {
            appendStatus("Failed to remove item: " + exception.getMessage());
        }
    }

    private void submitOrder() {
        if (cart.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Cart is empty.");
            return;
        }

        try {
            clientApp.submitOrder(cart, mode == OrderMode.TAKEAWAY);
        } catch (IOException exception) {
            appendStatus("Submit failed: " + exception.getMessage());
        }
    }

    private void checkout() {
        if (ordersById.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No submitted orders to checkout yet.");
            return;
        }

        double total = ordersById.values().stream().mapToDouble(Order::getTotal).sum();
        String sourceLabel = cart.getTableNumber() < 0
                ? "Takeaway " + Math.abs(cart.getTableNumber())
                : "Table " + cart.getTableNumber();
        JOptionPane.showMessageDialog(
                this,
                sourceLabel + " checkout total: RM " + String.format("%.2f", total)
                        + System.lineSeparator()
                        + "Submitted orders: " + ordersById.size(),
                "Checkout",
                JOptionPane.INFORMATION_MESSAGE
        );
        appendStatus(sourceLabel + " checkout total: RM " + String.format("%.2f", total));
        try {
            clientApp.checkout();
        } catch (IOException exception) {
            appendStatus("Checkout failed: " + exception.getMessage());
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
            builder.append(mode == OrderMode.DINE_IN ? "Table " + cart.getTableNumber() : "Takeaway Cart")
                    .append(System.lineSeparator());

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
            ordersById.put(order.getOrderId(), order);
            renderOrders();
            appendStatus("Order " + order.getOrderId() + " status: " + order.getStatus());
        });
    }

    private void renderOrders() {
        ordersPanel.removeAll();

        for (Order order : ordersById.values()) {
            ordersPanel.add(buildOrderCard(order));
            ordersPanel.add(Box.createVerticalStrut(10));
        }

        ordersPanel.revalidate();
        ordersPanel.repaint();
    }

    private JPanel buildOrderCard(Order order) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDD, 0xDD, 0xDD)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JPanel headerRow = new JPanel(new BorderLayout(8, 0));
        headerRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JLabel headerLabel = new JLabel(order.getOrderId() + " | " + order.getLocationLabel()
                + " | RM " + String.format("%.2f", order.getTotal()));
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 13f));
        headerRow.add(headerLabel, BorderLayout.WEST);

        AppTheme.StatusChip aggregateChip = new AppTheme.StatusChip(
                order.getStatus(),
                AppTheme.colorForStatus(order.getStatus()));
        JPanel chipHolder = new JPanel(new BorderLayout());
        chipHolder.setOpaque(false);
        chipHolder.setPreferredSize(new Dimension(104, 30));
        chipHolder.setMaximumSize(new Dimension(104, 30));
        chipHolder.add(aggregateChip, BorderLayout.CENTER);
        headerRow.add(chipHolder, BorderLayout.EAST);
        card.add(headerRow);
        card.add(Box.createVerticalStrut(6));

        for (OrderItem item : order.getItems()) {
            JPanel itemRow = new JPanel(new BorderLayout(8, 0));
            itemRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            itemRow.add(new JLabel(item.getItemName() + " x" + item.getQuantity()), BorderLayout.WEST);
            itemRow.add(new AppTheme.ItemProgress(item.getStatus()), BorderLayout.EAST);
            card.add(itemRow);
        }

        return card;
    }

    private void handleTableAssigned(Integer tableNumber) {
        SwingUtilities.invokeLater(() -> {
            if (mode == OrderMode.DINE_IN) {
                tableField.setText(String.valueOf(tableNumber));
            }
            ordersById.clear();
            cart = new TableCart(tableNumber);
            refreshCartView();
            renderOrders();
        });
    }

    private void handleCartUpdated(TableCart updatedCart) {
        SwingUtilities.invokeLater(() -> {
            cart = updatedCart;
            refreshCartView();
        });
    }

    private void handleCheckoutCompleted(String message) {
        SwingUtilities.invokeLater(() -> {
            ordersById.clear();
            renderOrders();
            appendStatus(message);
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
