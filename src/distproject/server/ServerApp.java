package distproject.server;

import distproject.model.Order;
import distproject.model.OrderItem;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ServerApp {
    private final int port;
    private final Consumer<String> logConsumer;
    private final Consumer<List<Order>> orderListConsumer;
    private final ServerContext context;
    private final ExecutorService clientPool;
    private final ExecutorService kitchenStationPool;
    private final Map<Integer, KitchenStationStatus> stationStatuses = new ConcurrentHashMap<>();
    private final PersistenceManager persistenceManager;
    private ServerSocket serverSocket;
    private Thread schedulerThread;
    private volatile boolean running;

    public ServerApp(int port, Consumer<String> logConsumer, Consumer<List<Order>> orderListConsumer) {
        this.port = port;
        this.logConsumer = logConsumer;
        this.orderListConsumer = orderListConsumer;
        this.context = new ServerContext();
        this.clientPool = Executors.newCachedThreadPool();
        this.kitchenStationPool = Executors.newFixedThreadPool(2, new KitchenStationThreadFactory());
        stationStatuses.put(1, KitchenStationStatus.idle(1));
        stationStatuses.put(2, KitchenStationStatus.idle(2));
        this.persistenceManager = new PersistenceManager();
    }

    public void start() {
        if (running) {
            log("Server already running on port " + port);
            return;
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            log("Server started on port " + port);
            startKitchenScheduler();
            publishOrders();
            persistState();

            Thread acceptThread = new Thread(this::acceptLoop, "server-accept-loop");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException exception) {
            log("Failed to start server: " + exception.getMessage());
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                clientPool.submit(new ClientHandler(socket, context, this::log, this::publishOrders, this::persistState));
            } catch (IOException exception) {
                if (running) {
                    log("Accept loop error: " + exception.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
        if (schedulerThread != null) {
            schedulerThread.interrupt();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                log("Server stopped");
            } catch (IOException exception) {
                log("Failed to stop server: " + exception.getMessage());
            }
        }
        clientPool.shutdownNow();
        kitchenStationPool.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    public ServerContext getContext() {
        return context;
    }

    public List<KitchenStationStatus> getKitchenStationStatuses() {
        List<KitchenStationStatus> statuses = new ArrayList<>(stationStatuses.values());
        statuses.sort((first, second) -> Integer.compare(first.stationNumber(), second.stationNumber()));
        return statuses;
    }

    public void restoreBackupFile() {
        persistenceManager.restoreBackupToMain(this::log);
    }

    private void startKitchenScheduler() {
        schedulerThread = new Thread(this::runKitchenScheduler, "kitchen-priority-scheduler");
        schedulerThread.setDaemon(true);
        schedulerThread.start();
        log("Scheduling started: takeaway base score = 100, dine-in base score = 70, waiting adds +10 every 10 seconds, same score uses FCFS; 2 kitchen stations are active.");
    }

    private void runKitchenScheduler() {
        while (running) {
            try {
                Order order = context.takeNextKitchenOrder();
                log("Priority scheduler selected " + order.getOrderType() + " order " + order.getOrderId()
                        + " from " + order.getLocationLabel());
                updateOrderStatus(order, Order.STATUS_PROCESSING);
                submitKitchenItemTasks(order).await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private CountDownLatch submitKitchenItemTasks(Order order) {
        List<OrderItem> items = new ArrayList<>(order.getItems());
        CountDownLatch orderDone = new CountDownLatch(items.size());
        for (OrderItem item : items) {
            kitchenStationPool.submit(() -> processKitchenItem(order, item, orderDone));
        }
        return orderDone;
    }

    private void processKitchenItem(Order order, OrderItem item, CountDownLatch orderDone) {
        int stationNumber = currentStationNumber();
        try {
            log("Kitchen station accepted " + item.getItemName() + " for order " + order.getOrderId()
                    + " | current item status = PENDING");
            KitchenTiming timing = KitchenTiming.forItem(item);
            updateStationStatus(stationNumber, order, item);

            updateItemStatus(order, item, OrderItem.STATUS_PREPARING);
            updateStationStatus(stationNumber, order, item);

            pauseForDemo(timing.preparingMillis());

            updateItemStatus(order, item, OrderItem.STATUS_READY);
            updateStationStatus(stationNumber, order, item);

            pauseForDemo(timing.readyBeforeServeMillis());

            updateItemStatus(order, item, OrderItem.STATUS_COMPLETED);
            updateStationStatus(stationNumber, order, item);
            completeOrderIfAllItemsServed(order);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            stationStatuses.put(stationNumber, KitchenStationStatus.idle(stationNumber));
            orderDone.countDown();
        }
    }

    private void pauseForDemo(long milliseconds) throws InterruptedException {
        Thread.sleep(milliseconds);
    }

    private void updateItemStatus(Order order, OrderItem item, String status) {
        synchronized (order) {
            item.setStatus(status);
            log("Item " + item.getItemName() + " in order " + order.getOrderId() + " -> " + status);
            broadcastOrderStatus(order);
            publishOrders();
            persistState();
        }
    }

    private void completeOrderIfAllItemsServed(Order order) {
        synchronized (order) {
            if (!order.isAllItemsCompleted() || Order.STATUS_COMPLETED.equals(order.getStatus())) {
                return;
            }
            updateOrderStatus(order, Order.STATUS_COMPLETED);
        }
    }

    private void updateOrderStatus(Order order, String status) {
        order.setStatus(status);
        log("Scheduling status update: order " + order.getOrderId() + " -> " + status);
        broadcastOrderStatus(order);
        publishOrders();
        persistState();
    }

    private void broadcastOrderStatus(Order order) {
        if (!context.shouldSyncOrderToTable(order)) {
            return;
        }
        for (ClientHandler handler : context.getTableClients(order.getTableNumber())) {
            try {
                handler.sendMessage(new distproject.message.Message(
                        distproject.message.MessageType.ORDER_STATUS_UPDATED,
                        "Order " + order.getOrderId() + " status changed to " + order.getStatus(),
                        order
                ));
            } catch (IOException exception) {
                log("Failed to sync order status: " + exception.getMessage());
            }
        }
    }

    private void publishOrders() {
        orderListConsumer.accept(context.getOrdersSnapshot());
    }

    private void persistState() {
        persistenceManager.saveState(context.getOrdersSnapshot(), context.getMenuSnapshot(), this::log);
    }

    private void log(String text) {
        logConsumer.accept(text);
    }

    private int currentStationNumber() {
        String threadName = Thread.currentThread().getName();
        int markerIndex = threadName.lastIndexOf('-');
        if (markerIndex < 0 || markerIndex == threadName.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(threadName.substring(markerIndex + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void updateStationStatus(int stationNumber, Order order, OrderItem item) {
        if (stationNumber <= 0) {
            return;
        }
        stationStatuses.put(stationNumber, new KitchenStationStatus(
                stationNumber,
                order.getOrderId(),
                order.getOrderType(),
                order.getSourceLabel(),
                item.getItemName(),
                item.getStatus()
        ));
    }

    private record KitchenTiming(long preparingMillis, long readyBeforeServeMillis) {
        private static KitchenTiming forItem(OrderItem item) {
            return switch (item.getItemId()) {
                case "M001" -> new KitchenTiming(10400L, 4800L);  // Fried Rice
                case "M002" -> new KitchenTiming(16400L, 7000L);  // Chicken Chop
                case "M003" -> new KitchenTiming(12400L, 5600L);  // Mee Goreng
                case "M004" -> new KitchenTiming(4400L, 3200L);   // Lemon Tea
                case "M005" -> new KitchenTiming(6600L, 4200L);   // Cheesecake
                default -> fallbackTiming(item);
            };
        }

        private static KitchenTiming fallbackTiming(OrderItem item) {
            int offset = Math.abs(item.getItemId().hashCode() % 4);
            return new KitchenTiming(
                    9000L + offset * 1800L,
                    4000L + offset * 1000L
            );
        }
    }

    public record KitchenStationStatus(
            int stationNumber,
            String orderId,
            String orderType,
            String source,
            String itemName,
            String itemStatus
    ) {
        private static KitchenStationStatus idle(int stationNumber) {
            return new KitchenStationStatus(stationNumber, "-", "-", "-", "Idle", "-");
        }
    }

    private static class KitchenStationThreadFactory implements ThreadFactory {
        private final AtomicInteger stationCounter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "kitchen-station-" + stationCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
