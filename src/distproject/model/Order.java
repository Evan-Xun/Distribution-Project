package distproject.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Order implements Serializable {
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private final String orderId;
    private final int tableNumber;
    private final List<OrderItem> items;
    private final boolean takeaway;
    private final long sequenceNumber;
    private final long queuedAtMillis;
    private String status;
    private final String createdAt;

    public Order(int tableNumber, List<OrderItem> items, boolean takeaway, long sequenceNumber) {
        this.orderId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.tableNumber = tableNumber;
        this.items = new ArrayList<>(items);
        this.takeaway = takeaway;
        this.sequenceNumber = sequenceNumber;
        this.queuedAtMillis = System.currentTimeMillis();
        this.status = STATUS_PROCESSING;
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

    public long getQueuedAtMillis() {
        return queuedAtMillis;
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

    public boolean isAllItemsCompleted() {
        return items.stream().allMatch(item -> OrderItem.STATUS_COMPLETED.equals(item.getStatus()));
    }

    public String getLocationLabel() {
        return getSourceLabel();
    }

    public String getSourceLabel() {
        return takeaway ? "Takeaway " + Math.abs(tableNumber) : "Table " + tableNumber;
    }

    public String toDisplayText() {
        StringBuilder builder = new StringBuilder();
        builder.append("Order ").append(orderId)
                .append(" | ").append(getLocationLabel())
                .append(" | ").append(getOrderType())
                .append(" | ").append(status)
                .append(" | ").append(createdAt)
                .append(System.lineSeparator());

        for (OrderItem item : items) {
            builder.append("  - ").append(item)
                    .append(" | ").append(item.getStatus())
                    .append(System.lineSeparator());
        }

        builder.append("  Total: RM ").append(String.format("%.2f", getTotal()))
                .append(System.lineSeparator());
        return builder.toString();
    }
}
