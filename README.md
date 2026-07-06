# Distribution Project

Java-based distributed client-server restaurant ordering system with multiple GUI clients, shared table carts, synchronization, locking, and stock consistency control.

## Overview

This project implements a small business client-server system for a restaurant ordering scenario. The system is developed with Java Swing for the GUI and Java Socket programming for distributed communication between server and clients.

The server manages:

- client connections
- shared cart state by table
- menu stock
- order submission
- concurrency control

Each client can:

- connect to the server
- select a table number
- view the menu
- join a shared cart for the same table
- add items into the shared cart
- submit the order

## Implemented Distributed Features

### 1. Multi-client client-server communication

- Multiple clients can connect to one server at the same time.
- Each client uses its own socket connection.
- The server uses a multi-threaded design and assigns one handler thread to each client.

This demonstrates the basic distributed system architecture and concurrency foundation.

### 2. Shared cart synchronization by table

- Clients that join the same table number share one server-side cart.
- When one client adds an item, the updated cart is broadcast to all clients in the same table.
- All clients in the same table see the same cart contents.

This demonstrates synchronization of shared distributed state.

### 3. Table-based order submission

- A shared cart belongs to a table instead of a single local client.
- Any client in the same table can submit the order.
- After successful submission, the shared cart is cleared for all clients in that table.

This demonstrates multiple clients collaboratively placing one order.

### 4. Stock deduction and stock synchronization

- When an order is submitted successfully, the server deducts menu stock.
- The updated menu is broadcast to all connected clients.
- All clients see the latest stock values after order submission.

This demonstrates distributed consistency for shared menu stock.

### 5. Kitchen order priority scheduling

- Submitted orders enter a server-side kitchen order queue.
- The kitchen scheduler gives takeaway orders higher priority than dine-in orders.
- Orders with the same type are processed using FCFS (First-Come, First-Served).
- Order status changes through `PENDING -> PREPARING -> READY -> COMPLETED`.
- The server GUI displays the kitchen queue and live status changes.

This demonstrates priority scheduling in a distributed small-business workflow.

### 6. Order status synchronization

- When the server changes an order status, clients at the same table receive an update.
- Each client can see the latest order status in the client GUI.
- Server logs show each scheduling and status update event.

This demonstrates synchronization of distributed order state after submission.

### 7. Main file persistence and backup replication

- Orders and menu stock are saved to `data/main_state.txt`.
- The main state file is copied to `data/backup_state.txt` after each important update.
- The server log reports main file saving and backup replication events.
- The server GUI includes a restore action that copies the backup file back to the main file.

This demonstrates data persistence and a simple backup replication mechanism.

## Implemented Distributed Mechanisms

### Concurrency

- The server accepts multiple client connections concurrently.
- Each connected client is processed by a separate thread.
- Multiple clients can operate at the same time without blocking the whole server.

### Synchronization

- Shared cart state is maintained on the server side.
- Cart updates are synchronized and broadcast back to all clients in the same table.
- Menu stock updates are synchronized and broadcast to all connected clients.

### Locking

The system currently demonstrates three locking-related mechanisms:

#### 1. Cart locking

- Each table has its own cart lock.
- Only one operation at a time can modify the shared cart of the same table.
- This prevents inconsistent cart state when multiple clients add items or submit orders at the same time.

#### 2. Stock locking

- A global stock lock protects menu stock updates.
- Stock checking and stock deduction happen inside a protected critical section.
- This prevents stock inconsistencies when orders are submitted concurrently.

#### 3. Duplicate submit prevention

- The system tracks tables that are currently submitting an order.
- If two clients from the same table try to submit simultaneously, only one submission is accepted.
- This prevents duplicate order creation.

### Scheduling

- The server uses a kitchen queue to schedule submitted orders.
- Takeaway orders are processed before dine-in orders to support delivery priority.
- Orders with the same type still follow FCFS order, which keeps the scheduling result fair and easy to explain.
- The scheduler runs in a background thread so client communication can continue concurrently.

### Replication

- The server writes order and stock state to a main file.
- The server then replicates the same state to a backup file.
- The backup file can be copied back to the main file from the server GUI.

## Validation Rules Already Implemented

- A client must join a valid table before using shared-cart features.
- If a menu item's stock is `0`, it cannot be added to the cart.
- A client cannot add quantity beyond the currently available stock.
- If stock is insufficient, the client receives an error message and a popup dialog.
- If another client is already submitting the same table's order, duplicate submission is rejected.

## GUI Components

### Server GUI

- start/stop server
- server log display
- received order display

### Client GUI

- connect to server
- select or change table number
- refresh menu
- shared cart display
- add selected menu item
- submit order
- popup error feedback

## Main Classes

- `ServerLauncher`: launches the server GUI
- `ClientLauncher`: launches the client GUI
- `ServerApp`: server socket lifecycle
- `ClientHandler`: per-client request handling
- `ServerContext`: shared distributed state, cart management, stock control, locking
- `ClientApp`: client networking logic
- `ServerFrame`: server GUI
- `ClientFrame`: client GUI

## How To Run

1. Open the project in IntelliJ IDEA.
2. Run `ServerLauncher`.
3. Run one or more `ClientLauncher` instances.
4. Connect clients to the server using the default port `5001`.
5. Use the same table number on multiple clients to test shared-cart synchronization.

## Suggested Demo Flow

1. Start one server and two clients.
2. Let both clients join the same table.
3. Add an item from Client A and show that Client B sees the same shared cart update.
4. Add more items from Client B and show synchronization back to Client A.
5. Submit the order from one client.
6. Show that:
   - the cart is cleared for both clients
   - stock is deducted
   - the updated menu is synchronized
   - duplicate submission is prevented
7. Submit one dine-in order and one takeaway order, then watch the takeaway order receive priority in the kitchen queue.
8. Show the client receiving `PENDING`, `PREPARING`, `READY`, and `COMPLETED` status updates.
9. Open the runtime data folder and show that the main file and backup file are generated.
10. Use the server restore action to demonstrate backup recovery.

## Current Scope

The current version already demonstrates:

- distributed client-server design
- multiple client concurrency
- shared cart synchronization
- cart locking
- stock locking
- duplicate submit prevention
- stock consistency after order submission
- takeaway-priority kitchen order scheduling
- order status synchronization
- main file persistence
- backup file replication

This provides a solid foundation for the final report and presentation demo.
