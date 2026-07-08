package distproject.server;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class ReplicaServerApp {
    private final int port;
    private final Consumer<String> logConsumer;
    private final Consumer<String> snapshotConsumer;
    private final Path dataDirectory = Path.of("data", "replica-server");
    private final Path replicaFile = dataDirectory.resolve("replica_state.txt");
    private ServerSocket serverSocket;
    private volatile boolean running;

    public ReplicaServerApp(int port, Consumer<String> logConsumer, Consumer<String> snapshotConsumer) {
        this.port = port;
        this.logConsumer = logConsumer;
        this.snapshotConsumer = snapshotConsumer;
    }

    public void start() {
        if (running) {
            log("Replica server already running on port " + port);
            return;
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            log("Backup replica server started on port " + port);

            Thread acceptThread = new Thread(this::acceptLoop, "backup-replica-accept-loop");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException exception) {
            log("Failed to start backup replica server: " + exception.getMessage());
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread handlerThread = new Thread(() -> handleSnapshot(socket), "backup-replica-handler");
                handlerThread.setDaemon(true);
                handlerThread.start();
            } catch (IOException exception) {
                if (running) {
                    log("Replica accept loop error: " + exception.getMessage());
                }
            }
        }
    }

    private void handleSnapshot(Socket socket) {
        try (Socket currentSocket = socket;
             InputStreamReader reader = new InputStreamReader(currentSocket.getInputStream(), StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }

            String snapshot = builder.toString();
            Files.createDirectories(dataDirectory);
            Files.writeString(replicaFile, snapshot, StandardCharsets.UTF_8);
            snapshotConsumer.accept(snapshot);
            log("Replica snapshot received and stored at " + replicaFile.toAbsolutePath()
                    + " | " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (IOException exception) {
            log("Failed to store replica snapshot: " + exception.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                log("Backup replica server stopped");
            } catch (IOException exception) {
                log("Failed to stop backup replica server: " + exception.getMessage());
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void log(String text) {
        logConsumer.accept(text);
    }
}
