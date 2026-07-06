package distproject.server;

import distproject.model.MenuItem;
import distproject.model.Order;
import distproject.model.OrderItem;
import distproject.model.TableCart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class ServerContext {
    private static final Comparator<Order> KITCHEN_ORDER_COMPARATOR = Comparator
            .comparing(Order::isTakeaway)
            .reversed()
            .thenComparingLong(Order::getSequenceNumber);

    private final List<MenuItem> menuItems = new CopyOnWriteArrayList<>();
    private final List<Order> orders = new CopyOnWriteArrayList<>();
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<Integer, Set<ClientHandler>> tableClients = new ConcurrentHashMap<>();
    private final Map<Integer, TableCart> tableCarts = new ConcurrentHashMap<>();
    private final Map<Integer, ReentrantLock> tableLocks = new ConcurrentHashMap<>();
    private final ReentrantLock stockLock = new ReentrantLock();
    private final Set<Integer> submittingTables = ConcurrentHashMap.newKeySet();
    private final AtomicLong orderSequence = new AtomicLong();
    private final PriorityBlockingQueue<Order> kitchenQueue = new PriorityBlockingQueue<>(11, KITCHEN_ORDER_COMPARATOR);

    public ServerContext() {
        menuItems.add(new MenuItem("M001", "Fried Rice", 8.50, 20));
        menuItems.add(new MenuItem("M002", "Chicken Chop", 14.00, 12));
        menuItems.add(new MenuItem("M003", "Mee Goreng", 9.50, 18));
        menuItems.add(new MenuItem("M004", "Lemon Tea", 3.50, 30));
        menuItems.add(new MenuItem("M005", "Cheesecake", 6.00, 10));
    }

    public List<MenuItem> getMenuSnapshot() {
        List<MenuItem> snapshot = new ArrayList<>();
        for (MenuItem menuItem : menuItems) {
            snapshot.add(menuItem.copy());
        }
        return Collections.unmodifiableList(snapshot);
    }

    public void addOrder(Order order) {
        orders.add(order);
    }

    public List<Order> getOrdersSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(orders));
    }

    public void enqueueKitchenOrder(Order order) {
        kitchenQueue.offer(order);
    }

    public Order takeNextKitchenOrder() throws InterruptedException {
        return kitchenQueue.take();
    }

    public List<Order> getKitchenQueueSnapshot() {
        List<Order> snapshot = new ArrayList<>(kitchenQueue);
        snapshot.sort(KITCHEN_ORDER_COMPARATOR);
        return Collections.unmodifiableList(snapshot);
    }

    public void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
    }

    public void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        unregisterTable(clientHandler);
    }

    public int getClientCount() {
        return clients.size();
    }

    public void registerTable(int tableNumber, ClientHandler clientHandler) {
        unregisterTable(clientHandler);
        tableClients.computeIfAbsent(tableNumber, ignored -> ConcurrentHashMap.newKeySet()).add(clientHandler);
        tableCarts.computeIfAbsent(tableNumber, TableCart::new);
        tableLocks.computeIfAbsent(tableNumber, ignored -> new ReentrantLock());
    }

    public void unregisterTable(ClientHandler clientHandler) {
        for (Set<ClientHandler> handlers : tableClients.values()) {
            handlers.remove(clientHandler);
        }
        tableClients.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public int getTableClientCount(int tableNumber) {
        return tableClients.getOrDefault(tableNumber, Collections.emptySet()).size();
    }

    public CartUpdateResult addItemToTableCart(int tableNumber, MenuItem menuItem) {
        ReentrantLock tableLock = getTableLock(tableNumber);
        tableLock.lock();
        try {
            TableCart cart = tableCarts.computeIfAbsent(tableNumber, TableCart::new);
            if (menuItem.getStock() <= 0) {
                return CartUpdateResult.error("Insufficient stock for " + menuItem.getName());
            }
            if (cart.getQuantityForItem(menuItem.getId()) >= menuItem.getStock()) {
                return CartUpdateResult.error("Cannot add more " + menuItem.getName() + ". Stock is insufficient.");
            }
            cart.addItem(menuItem);
            return CartUpdateResult.success(cart.copy());
        } finally {
            tableLock.unlock();
        }
    }

    public TableCart getTableCartSnapshot(int tableNumber) {
        ReentrantLock tableLock = getTableLock(tableNumber);
        tableLock.lock();
        try {
            return tableCarts.computeIfAbsent(tableNumber, TableCart::new).copy();
        } finally {
            tableLock.unlock();
        }
    }

    public TableCart clearTableCart(int tableNumber) {
        ReentrantLock tableLock = getTableLock(tableNumber);
        tableLock.lock();
        try {
            TableCart cart = tableCarts.computeIfAbsent(tableNumber, TableCart::new);
            cart.clear();
            return cart.copy();
        } finally {
            tableLock.unlock();
        }
    }

    public MenuItem findMenuItemById(String itemId) {
        return menuItems.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    public Set<ClientHandler> getTableClients(int tableNumber) {
        return Collections.unmodifiableSet(tableClients.getOrDefault(tableNumber, Collections.emptySet()));
    }

    public List<ClientHandler> getAllClients() {
        return new ArrayList<>(clients);
    }

    public boolean beginSubmit(int tableNumber) {
        synchronized (submittingTables) {
            if (submittingTables.contains(tableNumber)) {
                return false;
            }
            submittingTables.add(tableNumber);
            return true;
        }
    }

    public void endSubmit(int tableNumber) {
        synchronized (submittingTables) {
            submittingTables.remove(tableNumber);
        }
    }

    public ReentrantLock getTableLock(int tableNumber) {
        return tableLocks.computeIfAbsent(tableNumber, ignored -> new ReentrantLock());
    }

    public ReentrantLock getStockLock() {
        return stockLock;
    }

    public SubmitResult submitOrderAtomically(int tableNumber, boolean takeaway) {
        ReentrantLock tableLock = getTableLock(tableNumber);
        tableLock.lock();
        stockLock.lock();
        try {
            TableCart cart = tableCarts.computeIfAbsent(tableNumber, TableCart::new);
            if (cart.isEmpty()) {
                return SubmitResult.error("Cart is empty");
            }

            Set<String> seen = new HashSet<>();
            for (OrderItem orderItem : cart.getItems()) {
                if (!seen.add(orderItem.getItemId())) {
                    continue;
                }
                MenuItem menuItem = findMenuItemById(orderItem.getItemId());
                if (menuItem == null) {
                    return SubmitResult.error("Menu item not found: " + orderItem.getItemName());
                }
                if (menuItem.getStock() < orderItem.getQuantity()) {
                    return SubmitResult.error("Not enough stock for " + orderItem.getItemName());
                }
            }

            for (OrderItem orderItem : cart.getItems()) {
                MenuItem menuItem = findMenuItemById(orderItem.getItemId());
                menuItem.setStock(menuItem.getStock() - orderItem.getQuantity());
            }

            Order order = new Order(cart.getTableNumber(), cart.getItems(), takeaway, orderSequence.incrementAndGet());
            orders.add(order);
            cart.clear();
            return SubmitResult.success(order, getMenuSnapshot(), cart.copy());
        } finally {
            stockLock.unlock();
            tableLock.unlock();
        }
    }

    public static class SubmitResult {
        private final boolean success;
        private final String errorMessage;
        private final Order order;
        private final List<MenuItem> updatedMenu;
        private final TableCart clearedCart;

        private SubmitResult(boolean success, String errorMessage, Order order, List<MenuItem> updatedMenu, TableCart clearedCart) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.order = order;
            this.updatedMenu = updatedMenu;
            this.clearedCart = clearedCart;
        }

        public static SubmitResult success(Order order, List<MenuItem> updatedMenu, TableCart clearedCart) {
            return new SubmitResult(true, null, order, updatedMenu, clearedCart);
        }

        public static SubmitResult error(String errorMessage) {
            return new SubmitResult(false, errorMessage, null, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Order getOrder() {
            return order;
        }

        public List<MenuItem> getUpdatedMenu() {
            return updatedMenu;
        }

        public TableCart getClearedCart() {
            return clearedCart;
        }
    }

    public static class CartUpdateResult {
        private final boolean success;
        private final String errorMessage;
        private final TableCart updatedCart;

        private CartUpdateResult(boolean success, String errorMessage, TableCart updatedCart) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.updatedCart = updatedCart;
        }

        public static CartUpdateResult success(TableCart updatedCart) {
            return new CartUpdateResult(true, null, updatedCart);
        }

        public static CartUpdateResult error(String errorMessage) {
            return new CartUpdateResult(false, errorMessage, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public TableCart getUpdatedCart() {
            return updatedCart;
        }
    }
}
