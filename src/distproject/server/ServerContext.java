package distproject.server;

import distproject.model.MenuItem;
import distproject.model.Order;
import distproject.model.OrderItem;
import distproject.model.TableCart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ServerContext {
    private static final int TAKEAWAY_BASE_PRIORITY = 100;
    private static final int DINE_IN_BASE_PRIORITY = 70;
    private static final int AGING_PRIORITY_STEP = 10;
    private static final long AGING_INTERVAL_MILLIS = 10000L;

    private final List<MenuItem> menuItems = new CopyOnWriteArrayList<>();
    private final List<Order> orders = new CopyOnWriteArrayList<>();
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<Integer, Set<ClientHandler>> tableClients = new ConcurrentHashMap<>();
    private final Map<Integer, TableCart> tableCarts = new ConcurrentHashMap<>();
    private final Map<Integer, Long> tableCheckoutCutoffs = new ConcurrentHashMap<>();
    private final Map<Integer, ReentrantLock> tableLocks = new ConcurrentHashMap<>();
    private final ReentrantLock stockLock = new ReentrantLock();
    private final Set<Integer> submittingTables = ConcurrentHashMap.newKeySet();
    private final AtomicLong orderSequence = new AtomicLong();
    private final List<Order> kitchenQueue = new ArrayList<>();
    private final Object kitchenQueueLock = new Object();
    private final AtomicInteger takeawayIdGenerator = new AtomicInteger(-1);

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

    public List<Order> getOrdersForTableSnapshot(int tableNumber) {
        List<Order> tableOrders = new ArrayList<>();
        long checkoutCutoff = tableCheckoutCutoffs.getOrDefault(tableNumber, 0L);
        for (Order order : orders) {
            if (order.getTableNumber() == tableNumber && order.getSequenceNumber() > checkoutCutoff) {
                tableOrders.add(order);
            }
        }
        return Collections.unmodifiableList(tableOrders);
    }

    public void checkoutTable(int tableNumber) {
        long latestSequenceForTable = tableCheckoutCutoffs.getOrDefault(tableNumber, 0L);
        for (Order order : orders) {
            if (order.getTableNumber() == tableNumber) {
                latestSequenceForTable = Math.max(latestSequenceForTable, order.getSequenceNumber());
            }
        }
        tableCheckoutCutoffs.put(tableNumber, latestSequenceForTable);
        clearTableCart(tableNumber);
    }

    public boolean shouldSyncOrderToTable(Order order) {
        long checkoutCutoff = tableCheckoutCutoffs.getOrDefault(order.getTableNumber(), 0L);
        return order.getSequenceNumber() > checkoutCutoff;
    }

    public void enqueueKitchenOrder(Order order) {
        synchronized (kitchenQueueLock) {
            kitchenQueue.add(order);
            kitchenQueueLock.notifyAll();
        }
    }

    public Order takeNextKitchenOrder() throws InterruptedException {
        synchronized (kitchenQueueLock) {
            while (kitchenQueue.isEmpty()) {
                kitchenQueueLock.wait();
            }
            int selectedIndex = 0;
            Order selectedOrder = kitchenQueue.get(0);
            long now = System.currentTimeMillis();

            for (int index = 1; index < kitchenQueue.size(); index++) {
                Order candidate = kitchenQueue.get(index);
                if (compareKitchenPriority(candidate, selectedOrder, now) < 0) {
                    selectedIndex = index;
                    selectedOrder = candidate;
                }
            }
            return kitchenQueue.remove(selectedIndex);
        }
    }

    public List<Order> getKitchenQueueSnapshot() {
        synchronized (kitchenQueueLock) {
            List<Order> snapshot = new ArrayList<>(kitchenQueue);
            long now = System.currentTimeMillis();
            snapshot.sort((first, second) -> compareKitchenPriority(first, second, now));
            return Collections.unmodifiableList(snapshot);
        }
    }

    public List<KitchenQueueEntry> getKitchenQueueView() {
        synchronized (kitchenQueueLock) {
            List<Order> snapshot = new ArrayList<>(kitchenQueue);
            long now = System.currentTimeMillis();
            snapshot.sort((first, second) -> compareKitchenPriority(first, second, now));

            List<KitchenQueueEntry> entries = new ArrayList<>();
            for (int index = 0; index < snapshot.size(); index++) {
                Order order = snapshot.get(index);
                entries.add(new KitchenQueueEntry(
                        index + 1,
                        order.getOrderId(),
                        order.getOrderType(),
                        order.getSourceLabel(),
                        Math.max(0L, (now - order.getQueuedAtMillis()) / 1000L),
                        calculateKitchenPriority(order, now),
                        order.getItems().size(),
                        order.getSequenceNumber()
                ));
            }
            return Collections.unmodifiableList(entries);
        }
    }

    private int compareKitchenPriority(Order first, Order second, long now) {
        int firstScore = calculateKitchenPriority(first, now);
        int secondScore = calculateKitchenPriority(second, now);
        if (firstScore != secondScore) {
            return Integer.compare(secondScore, firstScore);
        }
        return Long.compare(first.getSequenceNumber(), second.getSequenceNumber());
    }

    private int calculateKitchenPriority(Order order, long now) {
        int basePriority = order.isTakeaway() ? TAKEAWAY_BASE_PRIORITY : DINE_IN_BASE_PRIORITY;
        long waitedMillis = Math.max(0L, now - order.getQueuedAtMillis());
        int agingBonus = (int) (waitedMillis / AGING_INTERVAL_MILLIS) * AGING_PRIORITY_STEP;
        return basePriority + agingBonus;
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

    public int registerTakeawayCustomer(ClientHandler clientHandler) {
        int virtualId = takeawayIdGenerator.getAndDecrement();
        registerTable(virtualId, clientHandler);
        return virtualId;
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

    public CartUpdateResult addItemToTableCart(int tableNumber, MenuItem menuItem, Consumer<String> logConsumer) {
        ReentrantLock tableLock = getTableLock(tableNumber);
        tableLock.lock();
        log(logConsumer, "Cart lock acquired for table " + tableNumber + " while adding " + menuItem.getName());
        try {
            TableCart cart = tableCarts.computeIfAbsent(tableNumber, TableCart::new);
            if (menuItem.getStock() <= 0) {
                return CartUpdateResult.error("Insufficient stock for " + menuItem.getName());
            }
            if (cart.getQuantityForItem(menuItem.getId()) >= menuItem.getStock()) {
                return CartUpdateResult.error("Cannot add more " + menuItem.getName() + ". Stock is insufficient.");
            }
            cart.addItem(menuItem);
            log(logConsumer, "Shared cart updated: table " + tableNumber + " added " + menuItem.getName());
            return CartUpdateResult.success(cart.copy());
        } finally {
            tableLock.unlock();
            log(logConsumer, "Cart lock released for table " + tableNumber);
        }
    }

    public CartUpdateResult removeItemFromTableCart(int tableNumber, MenuItem menuItem, Consumer<String> logConsumer) {
        ReentrantLock tableLock = getTableLock(tableNumber);
        tableLock.lock();
        log(logConsumer, "Cart lock acquired for table " + tableNumber + " while removing " + menuItem.getName());
        try {
            TableCart cart = tableCarts.computeIfAbsent(tableNumber, TableCart::new);
            if (!cart.removeOneItem(menuItem.getId())) {
                return CartUpdateResult.error("Item not found in shared cart");
            }
            log(logConsumer, "Shared cart updated: table " + tableNumber + " removed one " + menuItem.getName());
            return CartUpdateResult.success(cart.copy());
        } finally {
            tableLock.unlock();
            log(logConsumer, "Cart lock released for table " + tableNumber);
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

    public SubmitResult submitOrderAtomically(int tableNumber, boolean takeaway, Consumer<String> logConsumer) {
        ReentrantLock tableLock = getTableLock(tableNumber);
        tableLock.lock();
        log(logConsumer, "Cart lock acquired for table " + tableNumber + " during submit");
        stockLock.lock();
        log(logConsumer, "Stock lock acquired for table " + tableNumber + " during submit");
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
            log(logConsumer, "Stock lock released for table " + tableNumber);
            tableLock.unlock();
            log(logConsumer, "Cart lock released for table " + tableNumber);
        }
    }

    private void log(Consumer<String> logConsumer, String message) {
        if (logConsumer != null) {
            logConsumer.accept(message);
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

    public record KitchenQueueEntry(
            int rank,
            String orderId,
            String orderType,
            String source,
            long waitingSeconds,
            int priorityScore,
            int itemCount,
            long sequenceNumber
    ) {
    }
}
