package distproject.server;

import distproject.model.Order;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ServerApp {
    private final int port;
    private final Consumer<String> logConsumer;
    private final Consumer<List<Order>> orderListConsumer;
    private final ServerContext context;
    private final ExecutorService clientPool;
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
    }

    public boolean isRunning() {
        return running;
    }

    public ServerContext getContext() {
        return context;
    }

    public void restoreBackupFile() {
        persistenceManager.restoreBackupToMain(this::log);
    }

    private void startKitchenScheduler() {
        schedulerThread = new Thread(this::runKitchenScheduler, "kitchen-fcfs-scheduler");
        schedulerThread.setDaemon(true);
        schedulerThread.start();
        log("Scheduling started: kitchen queue uses FCFS order processing.");
    }

    private void runKitchenScheduler() {
        while (running) {
            try {
                Order order = context.takeNextKitchenOrder();
                log("FCFS scheduler selected order " + order.getOrderId()
                        + " from table " + order.getTableNumber());

                updateOrderStatus(order, Order.STATUS_PREPARING);
                pauseForDemo(2500);
                updateOrderStatus(order, Order.STATUS_READY);
                pauseForDemo(1500);
                updateOrderStatus(order, Order.STATUS_COMPLETED);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void pauseForDemo(long milliseconds) throws InterruptedException {
        Thread.sleep(milliseconds);
    }

    private void updateOrderStatus(Order order, String status) {
        order.setStatus(status);
        log("Scheduling status update: order " + order.getOrderId() + " -> " + status);
        broadcastOrderStatus(order);
        publishOrders();
        persistState();
    }

    private void broadcastOrderStatus(Order order) {
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
}
