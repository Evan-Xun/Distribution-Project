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
import java.util.LinkedHashSet;
import java.util.List;
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
        int customerCount = Math.max(2, request.customerCount());
        int removeCount = customerCount / 2;
        int addCount = customerCount - removeCount;

        logHeader("Scenario 1: Same-table concurrent add/remove", logger);
        logger.accept("Customers: " + customerCount + " (add=" + addCount + ", remove=" + removeCount + ")");
        logger.accept("Table: " + request.tableA() + ", item: " + request.itemId());

        List<SimulatedCustomer> customers = openCustomers(request.host(), request.port(), request.tableA(), customerCount);
        try {
            resetTableState(customers, request.tableA(), logger);
            SimulatedCustomer seed = customers.get(0);
            logger.accept("Seeding cart with " + removeCount + " item(s) so removal has valid work...");
            for (int i = 0; i < removeCount; i++) {
                seed.addItem(request.itemId());
            }
            pause(500);

            List<Runnable> actions = new ArrayList<>();
            for (int index = 0; index < addCount; index++) {
                SimulatedCustomer customer = customers.get(index);
                actions.add(() -> safely(() -> customer.addItem(request.itemId()), customer, "add", logger));
            }
            for (int index = addCount; index < customerCount; index++) {
                SimulatedCustomer customer = customers.get(index);
                actions.add(() -> safely(() -> customer.removeItem(request.itemId()), customer, "remove", logger));
            }

            runInParallel(actions, logger);
            pause(700);

            TableCart cart = customers.get(0).latestCart();
            int finalQuantity = cart == null ? 0 : cart.getQuantityForItem(request.itemId());
            int expectedQuantity = removeCount;
            logger.accept("Expected final quantity: " + expectedQuantity);
            logger.accept("Actual final quantity: " + finalQuantity);
            logger.accept(finalQuantity == expectedQuantity
                    ? "PASS: shared cart stayed consistent under concurrent add/remove. Table cart lock demonstrated."
                    : "FAIL: shared cart quantity mismatch. Table cart lock may be broken.");
            logErrors(customers, logger);
        } finally {
            closeCustomers(customers);
        }
    }

    public void runSameTableSubmitConflict(SimulationRequest request, Consumer<String> logger) throws Exception {
        int customerCount = Math.max(2, request.customerCount());
        logHeader("Scenario 2: Same-table concurrent submit", logger);
        logger.accept("Customers: " + customerCount + ", table: " + request.tableA() + ", item: " + request.itemId());

        List<SimulatedCustomer> customers = openCustomers(request.host(), request.port(), request.tableA(), customerCount);
        try {
            resetTableState(customers, request.tableA(), logger);
            SimulatedCustomer seed = customers.get(0);
            logger.accept("Seeding shared cart before concurrent submit...");
            seed.addItem(request.itemId());
            seed.addItem(request.itemId());
            pause(500);

            List<Runnable> actions = new ArrayList<>();
            for (SimulatedCustomer customer : customers) {
                actions.add(() -> safely(() -> customer.submitOrder(false), customer, "submit", logger));
            }

            runInParallel(actions, logger);
            pause(900);

            Set<String> uniqueOrderIds = collectUniqueOrderIds(customers);
            logger.accept("Unique order IDs observed: " + uniqueOrderIds);
            logger.accept(uniqueOrderIds.size() == 1
                    ? "PASS: only one order was created for the table. Duplicate submit prevention demonstrated."
                    : "FAIL: expected exactly one created order. Duplicate submit prevention may be broken.");
            logErrors(customers, logger);
        } finally {
            closeCustomers(customers);
        }
    }

    public void runCrossTableStockConflict(SimulationRequest request, Consumer<String> logger) throws Exception {
        logHeader("Scenario 3: Cross-table stock conflict", logger);
        logger.accept("Table A: " + request.tableA() + ", Table B: " + request.tableB() + ", item: " + request.itemId());

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
            logger.accept("Preparing Table A quantity=" + quantityA + ", Table B quantity=" + quantityB);

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
            logger.accept("Table A success: " + tableASuccess + ", Table B success: " + tableBSuccess);
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
        logger.accept("All simulated customers are ready. Releasing them together...");
        startGate.countDown();
        doneGate.await(8, TimeUnit.SECONDS);
        executor.shutdownNow();
    }

    private void safely(ThrowingRunnable runnable, SimulatedCustomer customer, String label, Consumer<String> logger) {
        try {
            runnable.run();
            logger.accept(customer.name() + " sent " + label);
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
