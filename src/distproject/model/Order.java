package distproject.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Order implements Serializable {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PREPARING = "PREPARING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private final String orderId;
    private final int tableNumber;
    private final List<OrderItem> items;
    private final boolean takeaway;
    private final long sequenceNumber;
    private String status;
    private final String createdAt;

    public Order(int tableNumber, List<OrderItem> items, boolean takeaway, long sequenceNumber) {
        this.orderId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.tableNumber = tableNumber;
        this.items = new ArrayList<>(items);
        this.takeaway = takeaway;
        this.sequenceNumber = sequenceNumber;
        this.status = STATUS_PENDING;
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String getOrderId() {
        return orderId;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public boolean isTakeaway() {
        return takeaway;
    }

    public String getOrderType() {
        return takeaway ? "TAKEAWAY" : "DINE_IN";
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public double getTotal() {
        return items.stream().mapToDouble(OrderItem::getSubtotal).sum();
    }

    public String toDisplayText() {
        StringBuilder builder = new StringBuilder();
        builder.append("Order ").append(orderId)
                .append(" | Table ").append(tableNumber)
                .append(" | ").append(getOrderType())
                .append(" | ").append(status)
                .append(" | ").append(createdAt)
                .append(System.lineSeparator());

        for (OrderItem item : items) {
            builder.append("  - ").append(item).append(System.lineSeparator());
        }

        builder.append("  Total: RM ").append(String.format("%.2f", getTotal()))
                .append(System.lineSeparator());
        return builder.toString();
    }
}
