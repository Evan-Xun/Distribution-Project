package distproject.server;

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
import java.util.function.Consumer;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerContext context;
    private final Consumer<String> logConsumer;
    private final Runnable orderListPublisher;
    private final Runnable persistencePublisher;
    private int currentTableNumber;
    private boolean tableAssigned;
    private ObjectOutputStream outputStream;

    public ClientHandler(Socket socket, ServerContext context, Consumer<String> logConsumer,
                         Runnable orderListPublisher, Runnable persistencePublisher) {
        this.socket = socket;
        this.context = context;
        this.logConsumer = logConsumer;
        this.orderListPublisher = orderListPublisher;
        this.persistencePublisher = persistencePublisher;
    }

    @Override
    public void run() {
        context.addClient(this);
        log("Client connected: " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort()
                + " | active clients = " + context.getClientCount());

        try (ObjectOutputStream stream = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
            this.outputStream = stream;
            outputStream.flush();

            while (!socket.isClosed()) {
                Object raw = inputStream.readObject();
                if (!(raw instanceof Message message)) {
                    continue;
                }
                handleMessage(message, outputStream);
            }
        } catch (EOFException ignored) {
            log("Client disconnected: " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
        } catch (IOException | ClassNotFoundException exception) {
            log("Client handler error: " + exception.getMessage());
        } finally {
            context.removeClient(this);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            outputStream = null;
            log("Connection closed | active clients = " + context.getClientCount());
        }
    }

    private void handleMessage(Message message, ObjectOutputStream outputStream) throws IOException {
        switch (message.getType()) {
            case REQUEST_MENU -> {
                log("Request received: REQUEST_MENU");
                sendMessage(outputStream, new Message(
                        MessageType.MENU_DATA,
                        "Latest menu (" + context.getMenuSnapshot().size() + " items)",
                        context.getMenuSnapshot()
                ));
            }
            case REGISTER_TABLE -> {
                if (!(message.getPayload() instanceof Integer tableNumber) || tableNumber <= 0) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, "Invalid table number", null));
                    return;
                }

                currentTableNumber = tableNumber;
                tableAssigned = true;
                context.registerTable(tableNumber, this);
                log("Client assigned to table " + tableNumber
                        + " | clients at this table = " + context.getTableClientCount(tableNumber));
                sendMessage(outputStream, new Message(
                        MessageType.TABLE_ASSIGNED,
                        "Joined table " + tableNumber,
                        tableNumber
                ));
                sendMessage(outputStream, new Message(
                        MessageType.CART_UPDATED,
                        "Shared cart synced for table " + tableNumber,
                        context.getTableCartSnapshot(tableNumber)
                ));
            }
            case REGISTER_TAKEAWAY -> {
                int virtualId = context.registerTakeawayCustomer(this);
                currentTableNumber = virtualId;
                tableAssigned = true;
                log("Client registered as takeaway customer (internal id " + virtualId + ")");
                sendMessage(outputStream, new Message(
                        MessageType.TABLE_ASSIGNED,
                        "Takeaway order started",
                        virtualId
                ));
                sendMessage(outputStream, new Message(
                        MessageType.CART_UPDATED,
                        "Takeaway cart synced",
                        context.getTableCartSnapshot(virtualId)
                ));
            }
            case ADD_TO_SHARED_CART -> {
                if (!tableAssigned) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, "Please join a table first", null));
                    return;
                }
                if (!(message.getPayload() instanceof String itemId)) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, "Invalid menu item", null));
                    return;
                }

                MenuItem menuItem = context.findMenuItemById(itemId);
                if (menuItem == null) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, "Menu item not found", null));
                    return;
                }

                log("Cart lock acquired for table " + currentTableNumber + " while adding " + menuItem.getName());
                ServerContext.CartUpdateResult result = context.addItemToTableCart(currentTableNumber, menuItem);
                if (!result.isSuccess()) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, result.getErrorMessage(), null));
                    log("Add to cart rejected for table " + currentTableNumber + ": " + result.getErrorMessage());
                    log("Cart lock released for table " + currentTableNumber);
                    return;
                }
                TableCart updatedCart = result.getUpdatedCart();
                log("Shared cart updated: table " + currentTableNumber + " added " + menuItem.getName());
                log("Cart lock released for table " + currentTableNumber);
                broadcastCartUpdate(currentTableNumber, updatedCart);
            }
            case REMOVE_FROM_SHARED_CART -> {
                if (!tableAssigned) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, "Please join a table first", null));
                    return;
                }
                if (!(message.getPayload() instanceof String itemId)) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, "Invalid menu item", null));
                    return;
                }

                MenuItem menuItem = context.findMenuItemById(itemId);
                if (menuItem == null) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, "Menu item not found", null));
                    return;
                }

                log("Cart lock acquired for table " + currentTableNumber + " while removing " + menuItem.getName());
                ServerContext.CartUpdateResult result = context.removeItemFromTableCart(currentTableNumber, itemId);
                if (!result.isSuccess()) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, result.getErrorMessage(), null));
                    log("Remove from cart rejected for table " + currentTableNumber + ": " + result.getErrorMessage());
                    log("Cart lock released for table " + currentTableNumber);
                    return;
                }
                TableCart updatedCart = result.getUpdatedCart();
                log("Shared cart updated: table " + currentTableNumber + " removed one " + menuItem.getName());
                log("Cart lock released for table " + currentTableNumber);
                broadcastCartUpdate(currentTableNumber, updatedCart);
            }
            case SUBMIT_ORDER -> {
                log("Request received: SUBMIT_ORDER");
                if (!tableAssigned) {
                    sendMessage(outputStream, new Message(MessageType.ERROR, "Please join a table first", null));
                    return;
                }
                if (!context.beginSubmit(currentTableNumber)) {
                    sendMessage(outputStream, new Message(
                            MessageType.ERROR,
                            "Another client is already submitting the order for table " + currentTableNumber,
                            null
                    ));
                    return;
                }

                try {
                    boolean takeaway = Boolean.TRUE.equals(message.getPayload());
                    log("Cart lock acquired for table " + currentTableNumber + " during submit");
                    log("Stock lock acquired for table " + currentTableNumber + " during submit");
                    ServerContext.SubmitResult result = context.submitOrderAtomically(currentTableNumber, takeaway);
                    if (!result.isSuccess()) {
                        sendMessage(outputStream, new Message(MessageType.ERROR, result.getErrorMessage(), null));
                        return;
                    }

                    Order order = result.getOrder();
                    orderListPublisher.run();
                    persistencePublisher.run();
                    for (ClientHandler handler : context.getTableClients(currentTableNumber)) {
                        handler.sendMessage(new Message(
                                MessageType.ORDER_RECEIVED,
                                "Order " + order.getOrderId() + " submitted as " + order.getOrderType()
                                        + " and queued as PENDING",
                                order
                        ));
                    }
                    context.enqueueKitchenOrder(order);
                    log("Order " + order.getOrderId() + " entered priority kitchen queue as "
                            + order.getOrderType() + " with PENDING status.");

                    broadcastCartUpdate(currentTableNumber, result.getClearedCart());
                    broadcastMenuUpdate(result.getUpdatedMenu());
                } finally {
                    log("Stock lock released for table " + currentTableNumber);
                    log("Cart lock released for table " + currentTableNumber);
                    context.endSubmit(currentTableNumber);
                }
            }
            default -> sendMessage(outputStream, new Message(MessageType.ERROR, "Unsupported message type", null));
        }
    }

    private synchronized void sendMessage(ObjectOutputStream outputStream, Message message) throws IOException {
        outputStream.reset();
        outputStream.writeObject(message);
        outputStream.flush();
    }

    public synchronized void sendMessage(Message message) throws IOException {
        if (socket.isClosed() || outputStream == null) {
            return;
        }
        sendMessage(outputStream, message);
    }

    private void broadcastCartUpdate(int tableNumber, TableCart updatedCart) {
        for (ClientHandler handler : context.getTableClients(tableNumber)) {
            try {
                handler.sendMessage(new Message(
                        MessageType.CART_UPDATED,
                        "Shared cart synced for table " + tableNumber,
                        updatedCart.copy()
                ));
            } catch (IOException exception) {
                log("Failed to sync cart for table " + tableNumber + ": " + exception.getMessage());
            }
        }
    }

    private void broadcastMenuUpdate(java.util.List<MenuItem> updatedMenu) {
        for (ClientHandler handler : context.getAllClients()) {
            try {
                handler.sendMessage(new Message(
                        MessageType.MENU_DATA,
                        "Menu updated after order submission",
                        updatedMenu
                ));
            } catch (IOException exception) {
                log("Failed to sync menu: " + exception.getMessage());
            }
        }
    }

    private void log(String text) {
        logConsumer.accept(text);
    }
}
