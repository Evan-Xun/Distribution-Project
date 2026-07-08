package distproject.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TableCart implements Serializable {
    private final int tableNumber;
    private final List<OrderItem> items;

    public TableCart(int tableNumber) {
        this.tableNumber = tableNumber;
        this.items = new ArrayList<>();
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void addItem(MenuItem menuItem) {
        for (OrderItem item : items) {
            if (item.getItemId().equals(menuItem.getId())) {
                item.incrementQuantity(1);
                return;
            }
        }
        items.add(new OrderItem(menuItem.getId(), menuItem.getName(), menuItem.getPrice(), 1));
    }

    public boolean removeOneItem(String itemId) {
        for (int index = 0; index < items.size(); index++) {
            OrderItem item = items.get(index);
            if (!item.getItemId().equals(itemId)) {
                continue;
            }
            if (item.getQuantity() <= 1) {
                items.remove(index);
            } else {
                item.incrementQuantity(-1);
            }
            return true;
        }
        return false;
    }

    public int getQuantityForItem(String itemId) {
        for (OrderItem item : items) {
            if (item.getItemId().equals(itemId)) {
                return item.getQuantity();
            }
        }
        return 0;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public double getTotal() {
        return items.stream().mapToDouble(OrderItem::getSubtotal).sum();
    }

    public void clear() {
        items.clear();
    }

    public TableCart copy() {
        TableCart copy = new TableCart(tableNumber);
        for (OrderItem item : items) {
            copy.getItems().add(item.copy());
        }
        return copy;
    }
}
