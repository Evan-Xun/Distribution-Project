package distproject.simulation;

import distproject.message.Message;
import distproject.message.MessageType;
import distproject.model.Order;
import distproject.model.TableCart;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurrentOrderingSimulation {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 5001;
    private static final int DEFAULT_TABLE = 9;
    private static final int DEFAULT_CUSTOMERS = 4;
    private static final String DEFAULT_ITEM_ID = "M004";
    private static final String DEFAULT_SCENARIO = "both";

    public static void main(String[] args) throws Exception {
        Config config = Config.fromArgs(args);
        System.out.println("=== Concurrent Ordering Simulation ===");
        System.out.println("Host: " + config.host + ":" + config.port);
        System.out.println("Table: " + config.tableNumber);
        System.out.println("Customers: " + config.customerCount);
        System.out.println("Item for concurrent add: " + config.itemId);
        System.out.println("Scenario: " + config.scenario);
        System.out.println();

        List<SimulatedCustomer> customers = new ArrayList<>();
        try {
            for (int i = 1; i <= config.customerCount; i++) {
                SimulatedCustomer customer = new SimulatedCustomer("Customer-" + i, config.host, config.port);
                customer.connectAndRegisterTable(config.tableNumber);
                customers.add(customer);
            }

            if (config.runsAddScenario()) {
                runConcurrentAddScenario(customers, config.itemId);
            }
            if (config.runsSubmitScenario()) {
                if (!config.runsAddScenario()) {
                    prepareCartForSubmitScenario(customers, config.itemId);
                }
                runConcurrentSubmitScenario(customers);
            }
        } finally {
            for (SimulatedCustomer customer : customers) {
                customer.close();
            }
        }
    }

    private static void prepareCartForSubmitScenario(List<SimulatedCustomer> customers, String itemId)
            throws IOException, InterruptedException {
        System.out.println("--- Preparing cart for submit scenario ---");
        SimulatedCustomer seedCustomer = customers.get(0);
        seedCustomer.addItem(itemId);
        Thread.sleep(300);
        syncQueuedBroadcasts(customers, 200);
        TableCart finalCart = seedCustomer.getLatestCart();
        int seededQuantity = finalCart == null ? 0 : finalCart.getQuantityForItem(itemId);
        System.out.println("Seeded cart quantity for " + itemId + ": " + seededQuantity);
        System.out.println();
    }

    private static void runConcurrentAddScenario(List<SimulatedCustomer> customers, String itemId) throws InterruptedException {
        System.out.println("--- Scenario 1: Concurrent add to the same shared cart ---");
        CountDownLatch readyGate = new CountDownLatch(customers.size());
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(customers.size());
        ExecutorService executor = Executors.newFixedThreadPool(customers.size());

        for (SimulatedCustomer customer : customers) {
            executor.submit(() -> {
                readyGate.countDown();
                await(startGate);
                try {
                    customer.addItem(itemId);
                    System.out.println(customer.getName() + " sent ADD_TO_SHARED_CART for " + itemId);
                } catch (IOException exception) {
                    customer.recordError("Add failed: " + exception.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        readyGate.await(5, TimeUnit.SECONDS);
        System.out.println("All customers are ready. Releasing them together...");
        startGate.countDown();
        doneGate.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        Thread.sleep(500);
        syncQueuedBroadcasts(customers, 300);
        TableCart finalCart = customers.get(0).getLatestCart();
        int finalQuantity = finalCart == null ? 0 : finalCart.getQuantityForItem(itemId);

        System.out.println("Expected quantity for " + itemId + ": " + customers.size());
        System.out.println("Actual quantity in shared cart: " + finalQuantity);
        System.out.println(finalQuantity == customers.size()
                ? "Result: no lost update detected. Table cart locking worked."
                : "Result: quantity mismatch. Investigate cart locking.");
        printCustomerErrors(customers);
        System.out.println();
    }

    private static void runConcurrentSubmitScenario(List<SimulatedCustomer> customers) throws InterruptedException, IOException {
        System.out.println("--- Scenario 2: Concurrent submit on the same table ---");
        CountDownLatch readyGate = new CountDownLatch(customers.size());
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(customers.size());
        ExecutorService executor = Executors.newFixedThreadPool(customers.size());

        for (SimulatedCustomer customer : customers) {
            executor.submit(() -> {
                readyGate.countDown();
                await(startGate);
                try {
                    customer.submitOrder(false);
                    System.out.println(customer.getName() + " sent SUBMIT_ORDER");
                } catch (IOException exception) {
                    customer.recordError("Submit failed: " + exception.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        readyGate.await(5, TimeUnit.SECONDS);
        System.out.println("All customers are ready to submit. Releasing them together...");
        startGate.countDown();
        doneGate.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        Thread.sleep(800);
        syncQueuedBroadcasts(customers, 400);
        Set<String> uniqueOrderIds = new LinkedHashSet<>();
        for (SimulatedCustomer customer : customers) {
            uniqueOrderIds.addAll(customer.getObservedOrderIds());
        }

        System.out.println("Unique order IDs created: " + uniqueOrderIds.size() + " -> " + uniqueOrderIds);
        System.out.println(uniqueOrderIds.size() == 1
                ? "Result: only one order was created for the table."
                : "Result: more than one order was created. Investigate submit locking.");
        System.out.println("Expected behavior: one winner creates the order, while other concurrent submits are rejected or become invalid after the cart is cleared.");
        printCustomerErrors(customers);
        System.out.println();
    }

    private static void syncQueuedBroadcasts(List<SimulatedCustomer> customers, long millisPerCustomer) {
        for (SimulatedCustomer customer : customers) {
            customer.drainMessages(millisPerCustomer);
        }
    }

    private static void printCustomerErrors(List<SimulatedCustomer> customers) {
        for (SimulatedCustomer customer : customers) {
            List<String> errors = customer.getErrors();
            if (!errors.isEmpty()) {
                System.out.println(customer.getName() + " errors:");
                for (String error : errors) {
                    System.out.println("  - " + error);
                }
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Config {
        private final String host;
        private final int port;
        private final int tableNumber;
        private final int customerCount;
        private final String itemId;
        private final String scenario;

        private Config(String host, int port, int tableNumber, int customerCount, String itemId, String scenario) {
            this.host = host;
            this.port = port;
            this.tableNumber = tableNumber;
            this.customerCount = customerCount;
            this.itemId = itemId;
            this.scenario = scenario;
        }

        private static Config fromArgs(String[] args) {
            String host = args.length > 0 ? args[0] : DEFAULT_HOST;
            int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
            int tableNumber = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_TABLE;
            int customerCount = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_CUSTOMERS;
            String itemId = args.length > 4 ? args[4] : DEFAULT_ITEM_ID;
            String scenario = args.length > 5 ? args[5].trim().toLowerCase() : DEFAULT_SCENARIO;
            if (!scenario.equals("both") && !scenario.equals("add") && !scenario.equals("submit")) {
                throw new IllegalArgumentException("Scenario must be one of: both, add, submit");
            }
            return new Config(host, port, tableNumber, customerCount, itemId, scenario);
        }

        private boolean runsAddScenario() {
            return scenario.equals("both") || scenario.equals("add");
        }

        private boolean runsSubmitScenario() {
            return scenario.equals("both") || scenario.equals("submit");
        }
    }

    private static class SimulatedCustomer {
        private final String name;
        private final String host;
        private final int port;
        private final List<String> errors = new CopyOnWriteArrayList<>();

        private Socket socket;
        private ObjectOutputStream outputStream;
        private ObjectInputStream inputStream;
        private TableCart latestCart;
        private final Set<String> observedOrderIds = new LinkedHashSet<>();

        private SimulatedCustomer(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        private void connectAndRegisterTable(int tableNumber) throws IOException, InterruptedException {
            socket = new Socket(host, port);
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            inputStream = new ObjectInputStream(socket.getInputStream());

            send(new Message(MessageType.REGISTER_TABLE, "Register table", tableNumber));
            awaitRegistrationSync();
        }

        private void awaitRegistrationSync() throws IOException, InterruptedException {
            boolean assigned = false;
            boolean cartSynced = false;
            while (!assigned || !cartSynced) {
                Message message = readMessage();
                if (message == null) {
                    throw new EOFException("Connection closed while waiting for registration sync");
                }
                switch (message.getType()) {
                    case TABLE_ASSIGNED -> assigned = true;
                    case CART_UPDATED -> {
                        latestCart = ((TableCart) message.getPayload()).copy();
                        cartSynced = true;
                    }
                    case MENU_DATA -> {
                    }
                    case ERROR -> errors.add(message.getText());
                    default -> {
                    }
                }
            }
        }

        private void addItem(String itemId) throws IOException {
            send(new Message(MessageType.ADD_TO_SHARED_CART, "Add item to shared cart", itemId));
            drainMessages(250);
        }

        private void submitOrder(boolean takeaway) throws IOException {
            send(new Message(MessageType.SUBMIT_ORDER, "Submit order", takeaway));
            drainMessages(400);
        }

        private void drainMessages(long millis) {
            long deadline = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < deadline) {
                try {
                    socket.setSoTimeout(Math.max(1, (int) (deadline - System.currentTimeMillis())));
                    Message message = readMessage();
                    if (message == null) {
                        return;
                    }
                    handleMessage(message);
                } catch (IOException exception) {
                    return;
                }
            }
        }

        private Message readMessage() throws IOException {
            try {
                Object raw = inputStream.readObject();
                if (raw instanceof Message message) {
                    return message;
                }
                return null;
            } catch (ClassNotFoundException exception) {
                throw new IOException("Failed to decode server message", exception);
            }
        }

        private void handleMessage(Message message) {
            switch (message.getType()) {
                case CART_UPDATED -> latestCart = ((TableCart) message.getPayload()).copy();
                case ORDER_RECEIVED -> observedOrderIds.add(((Order) message.getPayload()).getOrderId());
                case ERROR -> errors.add(message.getText());
                default -> {
                }
            }
        }

        private synchronized void send(Message message) throws IOException {
            outputStream.writeObject(message);
            outputStream.flush();
        }

        private String getName() {
            return name;
        }

        private TableCart getLatestCart() {
            return latestCart == null ? null : latestCart.copy();
        }

        private void recordError(String error) {
            errors.add(error);
        }

        private List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        private Set<String> getObservedOrderIds() {
            return Collections.unmodifiableSet(observedOrderIds);
        }

        private void close() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
