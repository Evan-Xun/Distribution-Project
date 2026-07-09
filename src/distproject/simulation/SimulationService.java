package distproject.simulation;

import distproject.message.Message;
import distproject.message.MessageType;
import distproject.model.MenuItem;
import distproject.model.Order;
import distproject.model.TableCart;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SimulationService {
    public void runSameTableCartConflict(SimulationRequest request, Consumer<String> logger) throws Exception {
        int customerCount = 2;

        logger.accept("Scenario 1: Same-table concurrent add");
        logger.accept("table " + request.tableA() + " | customers: Customer A, Customer B");

        List<SimulatedCustomer> customers = openCustomers(request.host(), request.port(), request.tableA(), customerCount);
        try {
            resetTableState(customers, request.tableA(), ignored -> {
            });
            SimulatedCustomer customerA = customers.get(0);
            SimulatedCustomer customerB = customers.get(1);

            MenuItem item = findMenuItem(customerA.awaitMenuSnapshot(), request.itemId());
            if (item == null) {
                throw new IllegalStateException("Menu item not found: " + request.itemId());
            }
            if (item.getStock() < customerCount) {
                logger.accept("Cannot run demo: " + item.getName() + " stock is " + item.getStock()
                        + ", but the demo needs " + customerCount + ".");
                return;
            }
            logger.accept("selected item: " + item.getName() + " (" + item.getId() + ")");

            logger.accept("");
            logger.accept("Initial shared cart");
            logger.accept(formatCartForLog(customerA.latestCart()));

            runHiddenConcurrentAdds(customerA, customerB, request.itemId(), item.getName(), logger);

            TableCart cartA = customerA.awaitCartQuantity(request.itemId(), customerCount);
            TableCart cartB = customerB.awaitCartQuantity(request.itemId(), customerCount);
            int quantityA = cartA == null ? 0 : cartA.getQuantityForItem(request.itemId());
            int quantityB = cartB == null ? 0 : cartB.getQuantityForItem(request.itemId());

            logger.accept("");
            logger.accept("Final shared cart observed by Customer A");
            logger.accept(formatCartForLog(cartA));
            logger.accept("");
            logger.accept("Final shared cart observed by Customer B");
            logger.accept(formatCartForLog(cartB));
            logger.accept("");
            logger.accept("Expected quantity: " + customerCount);
            logger.accept("Observed quantity: Customer A = " + quantityA + ", Customer B = " + quantityB);
            logger.accept(quantityA == customerCount && quantityB == customerCount
                    ? "PASS: both customers observed the same synchronized cart."
                    : "FAIL: customers did not observe the expected synchronized cart.");
            logErrors(customers, logger);
        } finally {
            closeCustomers(customers);
        }
    }

    public void runSameTableSubmitConflict(SimulationRequest request, Consumer<String> logger) throws Exception {
        int customerCount = Math.max(2, request.customerCount());
        logger.accept("Scenario 2: Same-table concurrent submit");
        logger.accept("table " + request.tableA() + " | customers: " + customerLabels(customerCount));

        List<SimulatedCustomer> customers = openCustomers(request.host(), request.port(), request.tableA(), customerCount);
        try {
            resetTableState(customers, request.tableA(), ignored -> {
            });
            SimulatedCustomer seed = customers.get(0);

            MenuItem item = findMenuItem(seed.awaitMenuSnapshot(), request.itemId());
            if (item == null) {
                throw new IllegalStateException("Menu item not found: " + request.itemId());
            }
            if (item.getStock() < 1) {
                logger.accept("Cannot run demo: " + item.getName() + " has no stock.");
                return;
            }
            logger.accept("selected item: " + item.getName() + " (" + item.getId() + ")");

            logger.accept("");
            logger.accept("Initial shared cart");
            logger.accept(formatCartForLog(seed.latestCart()));

            seed.addItem(request.itemId());
            TableCart preparedCart = seed.awaitCartQuantity(request.itemId(), 1);
            logger.accept("");
            logger.accept("Cart before submit");
            logger.accept(formatCartForLog(preparedCart));

            List<Runnable> actions = new ArrayList<>();
            for (SimulatedCustomer customer : customers) {
                actions.add(() -> safely(() -> customer.submitOrder(false), customer, "submitted order", logger));
            }

            logger.accept("");
            logger.accept("Concurrent customer actions");
            runInParallel(actions, logger);
            pause(900);

            Set<String> uniqueOrderIds = collectUniqueOrderIds(customers);
            logger.accept("");
            logger.accept("Orders created");
            logger.accept(uniqueOrderIds.isEmpty() ? "none" : String.join(", ", uniqueOrderIds));
            logger.accept("");
            logger.accept("Expected created orders: 1");
            logger.accept("Observed created orders: " + uniqueOrderIds.size());
            logger.accept(uniqueOrderIds.size() == 1
                    ? "PASS: only one order was created for the table."
                    : "FAIL: expected exactly one created order.");
            logErrors(customers, logger);
        } finally {
            closeCustomers(customers);
        }
    }

    public void runCrossTableStockConflict(SimulationRequest request, Consumer<String> logger) throws Exception {
        logHeader("Scenario 3: Cross-table stock conflict", logger);
        logger.accept("table " + request.tableA() + " and table " + request.tableB()
                + " competing for item " + request.itemId());

        List<SimulatedCustomer> tableACustomers = openCustomers(request.host(), request.port(), request.tableA(), 1);
        List<SimulatedCustomer> tableBCustomers = openCustomers(request.host(), request.port(), request.tableB(), 1);
        try {
            resetTableState(tableACustomers, request.tableA(), logger);
            resetTableState(tableBCustomers, request.tableB(), logger);
            SimulatedCustomer customerA = tableACustomers.get(0);
            SimulatedCustomer customerB = tableBCustomers.get(0);

            List<MenuItem> menu = customerA.awaitMenuSnapshot();
            MenuItem item = findMenuItem(menu, request.itemId());
            if (item == null) {
                throw new IllegalStateException("Menu item not found: " + request.itemId());
            }
            int stock = item.getStock();
            if (stock < 1) {
                throw new IllegalStateException("Selected item has no stock: " + request.itemId());
            }

            int quantityA = Math.max(1, stock / 2);
            int quantityB = stock - quantityA + 1;
            logger.accept("Current stock for " + item.getName() + ": " + stock);
            logger.accept("Preparing table " + request.tableA() + " quantity=" + quantityA
                    + ", table " + request.tableB() + " quantity=" + quantityB);

            for (int i = 0; i < quantityA; i++) {
                customerA.addItem(request.itemId());
            }
            for (int i = 0; i < quantityB; i++) {
                customerB.addItem(request.itemId());
            }
            pause(700);

            List<Runnable> actions = List.of(
                    () -> safely(() -> customerA.submitOrder(false), customerA, "submit-table-a", logger),
                    () -> safely(() -> customerB.submitOrder(false), customerB, "submit-table-b", logger)
            );
            runInParallel(actions, logger);
            pause(900);

            Set<String> uniqueOrderIds = new LinkedHashSet<>();
            uniqueOrderIds.addAll(customerA.observedOrderIds());
            uniqueOrderIds.addAll(customerB.observedOrderIds());

            boolean tableASuccess = !customerA.observedOrderIds().isEmpty();
            boolean tableBSuccess = !customerB.observedOrderIds().isEmpty();

            logger.accept("Unique order IDs observed: " + uniqueOrderIds);
            logger.accept("table " + request.tableA() + " success: " + tableASuccess
                    + ", table " + request.tableB() + " success: " + tableBSuccess);
            logger.accept(uniqueOrderIds.size() == 1 && tableASuccess != tableBSuccess
                    ? "PASS: only one table consumed the contested stock. Global stock lock demonstrated."
                    : "FAIL: stock conflict did not resolve as expected.");
            logErrors(tableACustomers, logger);
            logErrors(tableBCustomers, logger);
        } finally {
            closeCustomers(tableACustomers);
            closeCustomers(tableBCustomers);
        }
    }

    private List<SimulatedCustomer> openCustomers(String host, int port, int tableNumber, int customerCount) throws Exception {
        List<SimulatedCustomer> customers = new ArrayList<>();
        try {
            for (int index = 1; index <= customerCount; index++) {
                SimulatedCustomer customer = new SimulatedCustomer("Customer-" + index, host, port);
                customer.connectAndRegisterTable(tableNumber);
                customers.add(customer);
            }
            return customers;
        } catch (Exception exception) {
            closeCustomers(customers);
            throw exception;
        }
    }

    private void closeCustomers(List<SimulatedCustomer> customers) {
        for (SimulatedCustomer customer : customers) {
            customer.close();
        }
    }

    private String customerLabels(int customerCount) {
        List<String> labels = new ArrayList<>();
        for (int index = 1; index <= customerCount; index++) {
            labels.add("Customer-" + index);
        }
        return String.join(", ", labels);
    }

    private String formatCartForLog(TableCart cart) {
        StringBuilder builder = new StringBuilder();
        if (cart == null || cart.isEmpty()) {
            builder.append("Cart items: empty");
            builder.append(System.lineSeparator()).append("Total: RM 0.00");
            return builder.toString();
        }

        for (OrderItemView item : compactCartItems(cart)) {
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator());
            }
            builder.append(item.name()).append(" x").append(item.quantity())
                    .append(" = RM ").append(String.format("%.2f", item.subtotal()));
        }
        builder.append(System.lineSeparator())
                .append("Total: RM ")
                .append(String.format("%.2f", cart.getTotal()));
        return builder.toString();
    }

    private List<OrderItemView> compactCartItems(TableCart cart) {
        Map<String, OrderItemView> items = new LinkedHashMap<>();
        for (distproject.model.OrderItem item : cart.getItems()) {
            OrderItemView existing = items.get(item.getItemId());
            if (existing == null) {
                items.put(item.getItemId(), new OrderItemView(
                        item.getItemName(),
                        item.getQuantity(),
                        item.getSubtotal()
                ));
            } else {
                items.put(item.getItemId(), new OrderItemView(
                        existing.name(),
                        existing.quantity() + item.getQuantity(),
                        existing.subtotal() + item.getSubtotal()
                ));
            }
        }
        return new ArrayList<>(items.values());
    }

    private void runHiddenConcurrentAdds(SimulatedCustomer customerA, SimulatedCustomer customerB,
                                         String itemId, String itemName, Consumer<String> logger) throws Exception {
        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);
        AtomicReference<Exception> failure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> runHiddenAdd("Customer A", customerA, itemId, itemName, readyGate, startGate, doneGate, failure, logger));
        executor.submit(() -> runHiddenAdd("Customer B", customerB, itemId, itemName, readyGate, startGate, doneGate, failure, logger));

        readyGate.await(5, TimeUnit.SECONDS);
        logger.accept("");
        logger.accept("Concurrent customer actions");
        startGate.countDown();
        doneGate.await(8, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private void runHiddenAdd(String label, SimulatedCustomer customer, String itemId, String itemName,
                              CountDownLatch readyGate, CountDownLatch startGate, CountDownLatch doneGate,
                              AtomicReference<Exception> failure, Consumer<String> logger) {
        try {
            readyGate.countDown();
            startGate.await();
            customer.addItem(itemId);
            logger.accept(label + " added " + itemName);
        } catch (Exception exception) {
            failure.compareAndSet(null, exception);
            logger.accept(label + " add failed: " + exception.getMessage());
        } finally {
            doneGate.countDown();
        }
    }

    private record OrderItemView(String name, int quantity, double subtotal) {
    }

    private void resetTableState(List<SimulatedCustomer> customers, int tableNumber, Consumer<String> logger) throws Exception {
        if (customers.isEmpty()) {
            return;
        }

        TableCart existingCart = customers.get(0).latestCart();
        if (existingCart != null && !existingCart.isEmpty()) {
            logger.accept("Found existing shared cart state for table " + tableNumber + ". Resetting before simulation...");
        } else {
            logger.accept("Resetting table " + tableNumber + " to a clean state before simulation...");
        }

        customers.get(0).checkoutTable();
        for (SimulatedCustomer customer : customers) {
            customer.awaitCartEmpty();
            customer.clearObservedState();
        }
        pause(300);
    }

    private void runInParallel(List<Runnable> actions, Consumer<String> logger) throws InterruptedException {
        CountDownLatch readyGate = new CountDownLatch(actions.size());
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(actions.size());
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());

        for (Runnable action : actions) {
            executor.submit(() -> {
                readyGate.countDown();
                await(startGate);
                try {
                    action.run();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        readyGate.await(5, TimeUnit.SECONDS);
        startGate.countDown();
        doneGate.await(8, TimeUnit.SECONDS);
        executor.shutdownNow();
    }

    private void safely(ThrowingRunnable runnable, SimulatedCustomer customer, String label, Consumer<String> logger) {
        try {
            runnable.run();
            logger.accept(customer.name() + " " + label);
        } catch (Exception exception) {
            customer.recordError(label + " failed: " + exception.getMessage());
            logger.accept(customer.name() + " " + label + " error: " + exception.getMessage());
        }
    }

    private Set<String> collectUniqueOrderIds(List<SimulatedCustomer> customers) {
        Set<String> unique = new LinkedHashSet<>();
        for (SimulatedCustomer customer : customers) {
            unique.addAll(customer.observedOrderIds());
        }
        return unique;
    }

    private MenuItem findMenuItem(List<MenuItem> menu, String itemId) {
        for (MenuItem menuItem : menu) {
            if (menuItem.getId().equals(itemId)) {
                return menuItem;
            }
        }
        return null;
    }

    private void logErrors(List<SimulatedCustomer> customers, Consumer<String> logger) {
        for (SimulatedCustomer customer : customers) {
            if (customer.errors().isEmpty()) {
                continue;
            }
            logger.accept(customer.name() + " errors:");
            for (String error : customer.errors()) {
                logger.accept("  - " + error);
            }
        }
    }

    private void logHeader(String title, Consumer<String> logger) {
        logger.accept("==================================================");
        logger.accept(title);
        logger.accept("==================================================");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void pause(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public record SimulationRequest(String host, int port, int tableA, int tableB, int customerCount, String itemId) {
    }

    private static class SimulatedCustomer {
        private final String name;
        private final String host;
        private final int port;
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private final Set<String> observedOrderIds = ConcurrentHashMap.newKeySet();
        private final AtomicReference<TableCart> latestCart = new AtomicReference<>();
        private final AtomicReference<List<MenuItem>> latestMenu = new AtomicReference<>(Collections.emptyList());
        private final CountDownLatch registrationReady = new CountDownLatch(2);
        private final CountDownLatch firstMenuReady = new CountDownLatch(1);

        private volatile boolean running;
        private Socket socket;
        private ObjectOutputStream outputStream;
        private ObjectInputStream inputStream;
        private Thread listenerThread;

        private SimulatedCustomer(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        private void connectAndRegisterTable(int tableNumber) throws Exception {
            socket = new Socket(host, port);
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            inputStream = new ObjectInputStream(socket.getInputStream());
            running = true;
            listenerThread = new Thread(this::listen, name + "-listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            send(new Message(MessageType.REGISTER_TABLE, "Register table", tableNumber));
            send(new Message(MessageType.REQUEST_MENU, "Please send menu", null));

            if (!registrationReady.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for registration sync");
            }
            if (!firstMenuReady.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for initial menu sync");
            }
        }

        private void addItem(String itemId) throws IOException {
            send(new Message(MessageType.ADD_TO_SHARED_CART, "Add item to shared cart", itemId));
        }

        private void removeItem(String itemId) throws IOException {
            send(new Message(MessageType.REMOVE_FROM_SHARED_CART, "Remove item from shared cart", itemId));
        }

        private void submitOrder(boolean takeaway) throws IOException {
            send(new Message(MessageType.SUBMIT_ORDER, "Submit order", takeaway));
        }

        private void checkoutTable() throws IOException {
            send(new Message(MessageType.CHECKOUT_REQUEST, "Reset table before simulation", null));
        }

        private List<MenuItem> awaitMenuSnapshot() throws InterruptedException {
            firstMenuReady.await(3, TimeUnit.SECONDS);
            return latestMenu.get();
        }

        private void awaitCartEmpty() throws InterruptedException {
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(3);
            while (System.currentTimeMillis() < deadline) {
                TableCart cart = latestCart.get();
                if (cart != null && cart.isEmpty()) {
                    return;
                }
                Thread.sleep(50);
            }
            throw new IllegalStateException("Timed out waiting for cart reset");
        }

        private TableCart awaitCartQuantity(String itemId, int expectedQuantity) throws InterruptedException {
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
            TableCart lastCart = null;
            while (System.currentTimeMillis() < deadline) {
                TableCart cart = latestCart.get();
                if (cart != null) {
                    lastCart = cart.copy();
                    if (cart.getQuantityForItem(itemId) >= expectedQuantity) {
                        return cart.copy();
                    }
                }
                Thread.sleep(50);
            }
            return lastCart;
        }

        private void listen() {
            try {
                while (running && socket != null && !socket.isClosed()) {
                    Object raw = inputStream.readObject();
                    if (raw instanceof Message message) {
                        handleMessage(message);
                    }
                }
            } catch (SocketTimeoutException | EOFException ignored) {
            } catch (IOException | ClassNotFoundException exception) {
                if (running) {
                    recordError("listener error: " + exception.getMessage());
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void handleMessage(Message message) {
            switch (message.getType()) {
                case TABLE_ASSIGNED -> registrationReady.countDown();
                case CART_UPDATED -> {
                    latestCart.set(((TableCart) message.getPayload()).copy());
                    registrationReady.countDown();
                }
                case MENU_DATA -> {
                    List<MenuItem> snapshot = new ArrayList<>();
                    for (MenuItem item : (List<MenuItem>) message.getPayload()) {
                        snapshot.add(item.copy());
                    }
                    latestMenu.set(Collections.unmodifiableList(snapshot));
                    firstMenuReady.countDown();
                }
                case ORDER_RECEIVED -> observedOrderIds.add(((Order) message.getPayload()).getOrderId());
                case ERROR -> recordError(message.getText());
                default -> {
                }
            }
        }

        private synchronized void send(Message message) throws IOException {
            outputStream.writeObject(message);
            outputStream.flush();
        }

        private void close() {
            running = false;
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }

        private void recordError(String error) {
            errors.add(error);
        }

        private void clearObservedState() {
            observedOrderIds.clear();
            errors.clear();
        }

        private String name() {
            return name;
        }

        private TableCart latestCart() {
            TableCart cart = latestCart.get();
            return cart == null ? null : cart.copy();
        }

        private Set<String> observedOrderIds() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(observedOrderIds));
        }

        private List<String> errors() {
            return Collections.unmodifiableList(errors);
        }
    }
}
