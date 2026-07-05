package distproject.client;

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
import java.util.List;
import java.util.function.Consumer;

public class ClientApp {
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private Consumer<List<MenuItem>> menuConsumer;
    private Consumer<String> statusConsumer;
    private Consumer<Order> orderConsumer;
    private Consumer<Integer> tableConsumer;
    private Consumer<TableCart> cartConsumer;
    private Consumer<String> errorConsumer;

    public void connect(String host, int port,
                        Consumer<List<MenuItem>> menuConsumer,
                        Consumer<String> statusConsumer,
                        Consumer<Order> orderConsumer,
                        Consumer<Integer> tableConsumer,
                        Consumer<TableCart> cartConsumer,
                        Consumer<String> errorConsumer) throws IOException {
        this.menuConsumer = menuConsumer;
        this.statusConsumer = statusConsumer;
        this.orderConsumer = orderConsumer;
        this.tableConsumer = tableConsumer;
        this.cartConsumer = cartConsumer;
        this.errorConsumer = errorConsumer;

        socket = new Socket(host, port);
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();
        inputStream = new ObjectInputStream(socket.getInputStream());

        Thread listenerThread = new Thread(this::listen, "client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        status("Connected to server " + host + ":" + port);
        new Thread(() -> {
            try {
                Thread.sleep(150);
                requestMenu();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                status("Failed to auto-load menu: " + exception.getMessage());
            }
        }, "client-menu-request").start();
    }

    public void requestMenu() throws IOException {
        send(new Message(MessageType.REQUEST_MENU, "Please send menu", null));
    }

    public void registerTable(int tableNumber) throws IOException {
        send(new Message(MessageType.REGISTER_TABLE, "Register table", tableNumber));
    }

    public void addToSharedCart(String itemId) throws IOException {
        send(new Message(MessageType.ADD_TO_SHARED_CART, "Add item to shared cart", itemId));
    }

    public void submitOrder(TableCart cart) throws IOException {
        send(new Message(MessageType.SUBMIT_ORDER, "Submit order", cart));
    }

    public void disconnect() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        status("Disconnected");
    }

    private void listen() {
        try {
            while (socket != null && !socket.isClosed()) {
                Object raw = inputStream.readObject();
                if (!(raw instanceof Message message)) {
                    continue;
                }
                handleMessage(message);
            }
        } catch (EOFException ignored) {
            status("Server closed the connection");
        } catch (IOException | ClassNotFoundException exception) {
            status("Connection error: " + exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(Message message) {
        switch (message.getType()) {
            case MENU_DATA -> {
                menuConsumer.accept((List<MenuItem>) message.getPayload());
                status(message.getText());
            }
            case TABLE_ASSIGNED -> {
                Integer tableNumber = (Integer) message.getPayload();
                if (tableConsumer != null) {
                    tableConsumer.accept(tableNumber);
                }
                status(message.getText());
            }
            case CART_UPDATED -> {
                if (cartConsumer != null) {
                    cartConsumer.accept((TableCart) message.getPayload());
                }
                status(message.getText());
            }
            case ORDER_RECEIVED -> {
                Order order = (Order) message.getPayload();
                orderConsumer.accept(order);
                status(message.getText());
            }
            case ERROR -> {
                status("Server error: " + message.getText());
                if (errorConsumer != null) {
                    errorConsumer.accept(message.getText());
                }
            }
            default -> status("Unhandled message: " + message.getType());
        }
    }

    private void send(Message message) throws IOException {
        outputStream.writeObject(message);
        outputStream.flush();
    }

    private void status(String text) {
        if (statusConsumer != null) {
            statusConsumer.accept(text);
        }
    }
}
