package distproject.model;

import java.io.Serializable;

public class MenuItem implements Serializable {
    private final String id;
    private final String name;
    private final double price;
    private int stock;

    public MenuItem(String id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public MenuItem copy() {
        return new MenuItem(id, name, price, stock);
    }

    @Override
    public String toString() {
        return name + " (RM " + String.format("%.2f", price) + ", stock " + stock + ")";
    }
}
