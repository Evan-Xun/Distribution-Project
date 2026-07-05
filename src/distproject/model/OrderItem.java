package distproject.model;

import java.io.Serializable;

public class OrderItem implements Serializable {
    private final String itemId;
    private final String itemName;
    private final double unitPrice;
    private int quantity;

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

    public OrderItem copy() {
        return new OrderItem(itemId, itemName, unitPrice, quantity);
    }

    @Override
    public String toString() {
        return itemName + " x" + quantity + " = RM " + String.format("%.2f", getSubtotal());
    }
}
