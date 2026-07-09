package distproject.server;

import distproject.model.MenuItem;
import distproject.model.Order;
import distproject.model.OrderItem;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public class PersistenceManager {
    private static final String REPLICA_HOST = "127.0.0.1";
    private static final int REPLICA_PORT = 6001;

    private final Path dataDirectory = Path.of("data");
    private final Path mainFile = dataDirectory.resolve("main_state.txt");
    private final Path backupFile = dataDirectory.resolve("backup_state.txt");

    public synchronized void saveState(List<Order> orders, List<MenuItem> menuItems, Consumer<String> logConsumer) {
        try {
            String snapshot = buildSnapshot(orders, menuItems);
            Files.createDirectories(dataDirectory);
            Files.writeString(mainFile, snapshot, StandardCharsets.UTF_8);
            logConsumer.accept("Main file saved: " + mainFile.toAbsolutePath());

            Files.copy(mainFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            logConsumer.accept("Replication completed: backup file updated at " + backupFile.toAbsolutePath());
            replicateToBackupServer(snapshot, logConsumer);
        } catch (IOException exception) {
            logConsumer.accept("Persistence/replication failed: " + exception.getMessage());
        }
    }

    public synchronized void restoreBackupToMain(Consumer<String> logConsumer) {
        try {
            if (!Files.exists(backupFile)) {
                logConsumer.accept("Restore failed: backup file does not exist yet.");
                return;
            }

            Files.createDirectories(dataDirectory);
            Files.copy(backupFile, mainFile, StandardCopyOption.REPLACE_EXISTING);
            logConsumer.accept("Restore completed: backup file copied to main file.");
        } catch (IOException exception) {
            logConsumer.accept("Restore failed: " + exception.getMessage());
        }
    }

    private void replicateToBackupServer(String snapshot, Consumer<String> logConsumer) {
        try (Socket socket = new Socket(REPLICA_HOST, REPLICA_PORT);
             OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(snapshot);
            writer.flush();
            socket.shutdownOutput();
            logConsumer.accept("Distributed replica server synchronized at " + REPLICA_HOST + ":" + REPLICA_PORT);
        } catch (IOException exception) {
            logConsumer.accept("Distributed replica server unavailable at " + REPLICA_HOST + ":" + REPLICA_PORT
                    + " (" + exception.getMessage() + ")");
        }
    }

    private String buildSnapshot(List<Order> orders, List<MenuItem> menuItems) {
        StringBuilder builder = new StringBuilder();
        builder.append("Snapshot generated at ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("[ORDERS]")
                .append(System.lineSeparator());

        for (Order order : orders) {
            builder.append("ORDER|")
                    .append(order.getOrderId()).append('|')
                    .append(order.getTableNumber()).append('|')
                    .append(order.getOrderType()).append('|')
                    .append(order.getStatus()).append('|')
                    .append(order.getCreatedAt()).append('|')
                    .append(String.format("%.2f", order.getTotal()))
                    .append(System.lineSeparator());

            for (OrderItem item : order.getItems()) {
                builder.append("ITEM|")
                        .append(item.getItemId()).append('|')
                        .append(item.getItemName()).append('|')
                        .append(item.getQuantity()).append('|')
                        .append(item.getStatus()).append('|')
                        .append(String.format("%.2f", item.getUnitPrice())).append('|')
                        .append(String.format("%.2f", item.getSubtotal()))
                        .append(System.lineSeparator());
            }
        }

        builder.append(System.lineSeparator())
                .append("[MENU_STOCK]")
                .append(System.lineSeparator());

        for (MenuItem item : menuItems) {
            builder.append("MENU|")
                    .append(item.getId()).append('|')
                    .append(item.getName()).append('|')
                    .append(String.format("%.2f", item.getPrice())).append('|')
                    .append(item.getStock())
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }
}
