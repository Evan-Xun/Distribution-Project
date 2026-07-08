package distproject.model;

import java.io.Serializable;

public class OrderItem implements Serializable {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PREPARING = "PREPARING";
    public static final String STATUS_READY = "READY";

    private final String itemId;
    private final String itemName;
    private final double unitPrice;
    private int quantity;
    private String status = STATUS_PENDING;

    public OrderItem(String itemId, String itemName, double unitPrice, int quantity) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void incrementQuantity(int amount) {
        quantity += amount;
    }

    public double getSubtotal() {
        return unitPrice * quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OrderItem copy() {
        OrderItem copy = new OrderItem(itemId, itemName, unitPrice, quantity);
        copy.status = this.status;
        return copy;
    }

    @Override
    public String toString() {
        return itemName + " x" + quantity + " = RM " + String.format("%.2f", getSubtotal());
    }
}
