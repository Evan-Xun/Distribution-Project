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
        getContentPane().setBackground(AppTheme.APP_BACKGROUND);
        styleInputs();
        AppTheme.styleTable(menuTable);

        JPanel topPanel = buildTopPanel();

        JScrollPane menuPane = new JScrollPane(menuTable);
        AppTheme.stylePane(menuPane, "Menu");

        cartArea.setEditable(false);
        AppTheme.styleTextArea(cartArea, false);
        JScrollPane cartPane = new JScrollPane(cartArea);
        AppTheme.stylePane(cartPane, mode == OrderMode.DINE_IN ? "Shared Cart" : "Your Cart");

        ordersPanel.setBackground(AppTheme.SURFACE_BACKGROUND);
        ordersPanel.setLayout(new BoxLayout(ordersPanel, BoxLayout.Y_AXIS));
        JScrollPane orderStatusPane = new JScrollPane(ordersPanel);
        AppTheme.stylePane(orderStatusPane, "Order Status Updates");

        statusArea.setEditable(false);
        AppTheme.styleTextArea(statusArea, false);
        JScrollPane statusPane = new JScrollPane(statusArea);
        AppTheme.stylePane(statusPane, "Status");

        JPanel rightPanel = new JPanel(new GridLayout(3, 1, 8, 8));
        rightPanel.setOpaque(false);
        rightPanel.add(cartPane);
        rightPanel.add(orderStatusPane);
        rightPanel.add(statusPane);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 12, 12));
        centerPanel.setOpaque(false);
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
        AppTheme.styleSecondaryButton(refreshButton);
        refreshButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(7, 6, 7, 6)));
        AppTheme.styleSoftButton(addButton, AppTheme.SOFT_GREEN, AppTheme.COMPLETED_COLOR, new Color(0xC8, 0xDD, 0xCE));
        AppTheme.styleSoftButton(removeButton, AppTheme.SOFT_RED, AppTheme.ERROR_COLOR, new Color(0xE4, 0xC9, 0xC9));
        AppTheme.styleSoftButton(submitButton, AppTheme.SOFT_BLUE, AppTheme.READY_COLOR, new Color(0xC8, 0xD6, 0xEC));
        AppTheme.styleSoftButton(checkoutButton, AppTheme.SOFT_ORANGE, AppTheme.PREPARING_COLOR, new Color(0xE6, 0xD4, 0xB8));

        JPanel leftActions = new JPanel();
        leftActions.setLayout(new BoxLayout(leftActions, BoxLayout.X_AXIS));
        leftActions.setOpaque(false);
        leftActions.add(refreshButton);
        leftActions.add(Box.createHorizontalStrut(8));
        leftActions.add(addButton);
        leftActions.add(Box.createHorizontalStrut(8));
        leftActions.add(removeButton);
        leftActions.setMaximumSize(leftActions.getPreferredSize());

        JPanel rightActions = new JPanel(new GridLayout(1, 2, 8, 0));
        rightActions.setOpaque(false);
        rightActions.add(submitButton);
        rightActions.add(checkoutButton);
        rightActions.setMaximumSize(rightActions.getPreferredSize());

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        bottomPanel.add(leftActions);
        bottomPanel.add(Box.createHorizontalGlue());
        bottomPanel.add(Box.createHorizontalStrut(28));
        bottomPanel.add(rightActions);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void styleInputs() {
        AppTheme.styleTextField(hostField);
        AppTheme.styleTextField(portField);
        AppTheme.styleTextField(tableField);
    }

    private JPanel buildTopPanel() {
        JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(event -> connect());
        AppTheme.styleSoftButton(connectButton, AppTheme.SOFT_BLUE, AppTheme.TEXT_PRIMARY, new Color(0x9F, 0xB3, 0xC5));

        JPanel topPanel;
        if (mode == OrderMode.DINE_IN) {
            JButton changeTableButton = new JButton("Change Table");
            changeTableButton.addActionListener(event -> changeTable());
            AppTheme.styleSecondaryButton(changeTableButton);

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

        topPanel.setOpaque(false);
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
        String sourceLabel = sourceLabel();
        int choice = JOptionPane.showConfirmDialog(
                this,
                sourceLabel + " checkout total: RM " + String.format("%.2f", total)
                        + System.lineSeparator()
                        + "Submitted orders: " + ordersById.size()
                        + System.lineSeparator()
                        + "Confirm checkout?",
                "Checkout",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            appendStatus(sourceLabel + " checkout cancelled.");
            return;
        }

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
            builder.append(sourceLabel())
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

    private String sourceLabel() {
        int tableNumber = cart.getTableNumber();
        if (tableNumber < 0) {
            return "Takeaway " + Math.abs(tableNumber);
        }
        if (mode == OrderMode.TAKEAWAY) {
            return "Takeaway";
        }
        return "Table " + tableNumber;
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
        card.setBackground(AppTheme.PANEL_BACKGROUND);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JPanel headerRow = new JPanel(new BorderLayout(8, 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JLabel headerLabel = new JLabel(order.getOrderId() + " | " + order.getLocationLabel()
                + " | RM " + String.format("%.2f", order.getTotal()));
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 13f));
        headerLabel.setForeground(AppTheme.TEXT_PRIMARY);
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
            itemRow.setOpaque(false);
            itemRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            JLabel itemLabel = new JLabel(item.getItemName() + " x" + item.getQuantity());
            itemLabel.setForeground(AppTheme.TEXT_PRIMARY);
            itemRow.add(itemLabel, BorderLayout.WEST);
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
        SwingUtilities.invokeLater(() -> appendStatus(message));
    }

    private void showErrorDialog(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void appendStatus(String text) {
        SwingUtilities.invokeLater(() -> statusArea.append(text + System.lineSeparator()));
    }
}
