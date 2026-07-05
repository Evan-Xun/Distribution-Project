package distproject.server;

import distproject.model.Order;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ServerApp {
    private final int port;
    private final Consumer<String> logConsumer;
    private final Consumer<Order> orderConsumer;
    private final ServerContext context;
    private final ExecutorService clientPool;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public ServerApp(int port, Consumer<String> logConsumer, Consumer<Order> orderConsumer) {
        this.port = port;
        this.logConsumer = logConsumer;
        this.orderConsumer = orderConsumer;
        this.context = new ServerContext();
        this.clientPool = Executors.newCachedThreadPool();
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
                clientPool.submit(new ClientHandler(socket, context, this::log, orderConsumer));
            } catch (IOException exception) {
                if (running) {
                    log("Accept loop error: " + exception.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
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

    private void log(String text) {
        logConsumer.accept(text);
    }
}
