# Distribution Project

Java-based distributed client-server restaurant ordering system with multiple GUI clients, shared table carts, synchronization, locking, and stock consistency control.

## Overview

This project implements a small business client-server system for a restaurant ordering scenario. The system is developed with Java Swing for the GUI and Java Socket programming for distributed communication between server and clients.

The project includes a main launcher, a server GUI, dine-in and takeaway client modes, a backup replica server GUI, a simulation GUI, and a command-line concurrency simulation script.

The server manages:

- client connections
- shared cart state by table
- menu stock
- order submission
- concurrency control

Each client can:

- connect to the server
- select a table number for dine-in orders
- use a separate takeaway mode
- view the menu
- join a shared cart for the same table
- add items into the shared cart
- remove items from the shared cart
- submit the order
- check out submitted orders

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
- The kitchen scheduler gives takeaway orders a higher initial priority than dine-in orders.
- Dine-in orders receive an aging bonus while waiting, so long-waiting orders can move up in priority.
- Orders with the same priority are processed using FCFS (First-Come, First-Served).
- Order status changes through `PENDING -> PREPARING -> READY -> COMPLETED`.
- The server GUI displays the kitchen queue, kitchen stations, received orders, and live status changes.

This demonstrates priority scheduling in a distributed small-business workflow.

### 6. Order status synchronization

- When the server changes an order status, clients at the same table receive an update.
- Each client can see the latest order status in the client GUI.
- Server logs show each scheduling and status update event.

This demonstrates synchronization of distributed order state after submission.

### 7. Main file persistence and distributed backup replication

- Orders and menu stock are saved to `data/main_state.txt`.
- The main state file is copied to `data/backup_state.txt` after each important update.
- The primary server also sends the same state snapshot to an independent backup replica server on port `6001`.
- The backup replica server stores the received snapshot in `data/replica-server/replica_state.txt`.
- The replica server GUI displays the latest replicated snapshot and replica log events.
- The server log reports main file saving, local backup replication, and distributed replica synchronization events.
- The server GUI includes a restore action that copies the backup file back to the main file.

This demonstrates both local backup replication and socket-based distributed replica synchronization.

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
- Takeaway orders receive higher initial priority to support delivery priority.
- Dine-in orders receive an aging priority bonus while waiting, which prevents them from being ignored when takeaway orders keep arriving.
- Orders with the same priority still follow FCFS order, which keeps the scheduling result fair and easy to explain.
- The scheduler runs in a background thread so client communication can continue concurrently.

### Replication

- The server writes order and stock state to a main file.
- The server then replicates the same state to a local backup file.
- The server also sends the same state snapshot to a separate backup replica server over a socket connection.
- The replica server receives the snapshot and stores it independently in its own replica data folder.
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
- kitchen queue display
- kitchen station display
- received order display
- restore backup file action

### Client GUI

- connect to server
- choose dine-in or takeaway mode
- select or change table number
- refresh menu
- shared cart display
- add selected menu item
- remove selected menu item
- submit order
- checkout submitted orders
- order status update display
- popup error feedback

### Backup Replica Server GUI

- start/stop replica server
- listen on replica port `6001`
- replica log display
- latest replicated snapshot display

## Main Classes

- `Main`: opens the main launcher for server, client, simulation, and replica windows
- `ServerLauncher`: launches the server GUI
- `ClientLauncher`: launches the client mode selector
- `SimulationLauncher`: launches the simulation GUI
- `ReplicaServerLauncher`: launches the backup replica server GUI
- `ServerApp`: server socket lifecycle
- `ClientHandler`: per-client request handling
- `ServerContext`: shared distributed state, cart management, stock control, locking
- `PersistenceManager`: saves the main state file, copies it to the local backup file, and sends snapshots to the replica server
- `ReplicaServerApp`: backup replica socket server that receives and stores replicated snapshots
- `ClientApp`: client networking logic
- `ServerFrame`: server GUI
- `ReplicaServerFrame`: backup replica server GUI
- `OrderModeSelector`: lets the user choose dine-in or takeaway mode
- `ClientFrame`: client GUI
- `SimulationFrame`: one-click simulation GUI
- `SimulationService`: simulation GUI scenario runner
- `ConcurrentOrderingSimulation`: command-line concurrency simulation runner

## How To Run

1. Open the project in IntelliJ IDEA.
2. Make sure `lib/flatlaf-3.4.jar` is included on the classpath.
3. Run `Main` to open the launcher, then choose Server, Client, Simulation, or Replica.
4. For the full replication demo, open `Replica` first and start the replica server on port `6001`.
5. Open `Server` and start the primary server on the default port `5001`.
6. Open one or more `Client` windows.
7. Choose `Dine In` to test shared carts by table, or choose `Takeaway` to test takeaway priority scheduling.
8. Use the same table number on multiple dine-in clients to test shared-cart synchronization.
9. Run `SimulationLauncher` or choose `Simulation` from `Main` to open the simulation GUI for concurrency-conflict demos.

## Simulation GUI

The project includes a dedicated simulation panel for one-click concurrency demos.

Available scenarios:

- `Same-table add/remove`: multiple simulated customers on the same table add and remove the same item concurrently
- `Same-table submit`: multiple simulated customers on the same table submit the same shared cart concurrently
- `Cross-table stock conflict`: two different tables submit competing orders for the same item when the combined requested quantity exceeds stock

These scenarios are designed to demonstrate:

- table-level cart locking
- duplicate submit prevention
- global stock protection during atomic order submission

## Concurrency Simulation Script

To demonstrate concurrency and locking more clearly, the project also includes a command-line simulator:

- Run `distproject.simulation.ConcurrentOrderingSimulation`
- Or use the one-command helper script: `./run_simulation.sh`
- Optional arguments: `host port tableNumber customerCount itemId scenario`
- Scenario options: `both`, `add`, `submit`
- Example: `127.0.0.1 5001 9 4 M004 both`

Quick examples:

- `./run_simulation.sh`
- `./run_simulation.sh 127.0.0.1 5001 3 6 M001 add`
- `./run_simulation.sh 127.0.0.1 5001 3 6 M001 submit`

What it demonstrates:

- multiple simulated customers join the same table and add the same item concurrently
- the final shared cart quantity should match the total number of concurrent add requests
- multiple simulated customers then submit the same table order concurrently
- only one actual order should be created for the table, while other submissions are rejected or become invalid after the cart is cleared

This gives a repeatable way to show:

- multi-client concurrency
- table-level cart locking
- duplicate submit prevention
- atomic order submission and stock protection

## Suggested Demo Flow

1. Start the backup replica server on port `6001`.
2. Start one primary server and two clients.
3. Choose `Dine In` for both clients and let both clients join the same table.
4. Add an item from Client A and show that Client B sees the same shared cart update.
5. Add more items from Client B and show synchronization back to Client A.
6. Remove an item and show that the shared cart stays synchronized.
7. Submit the order from one client.
8. Open the order status panel and show the order moving through `PENDING`, `PREPARING`, `READY`, and `COMPLETED`.
9. Show that:
   - the cart is cleared for both clients
   - stock is deducted
   - the updated menu is synchronized
   - duplicate submission is prevented
10. Submit one dine-in order and one takeaway order, then compare their priority in the kitchen queue.
11. Show that takeaway starts with higher priority while dine-in orders can gain priority through waiting time.
12. Use checkout to clear submitted-order billing for a table or takeaway customer.
13. Open the runtime data folder and show that `data/main_state.txt` and `data/backup_state.txt` are generated.
14. Show the backup replica server receiving and displaying the latest replicated snapshot from `data/replica-server/replica_state.txt`.
15. Use the server restore action to demonstrate backup recovery from the local backup file.

## Current Scope

The current version already demonstrates:

- distributed client-server design
- multiple client concurrency
- shared cart synchronization
- cart locking
- stock locking
- duplicate submit prevention
- stock consistency after order submission
- takeaway-priority kitchen order scheduling with dine-in aging
- order status synchronization
- dine-in and takeaway client modes
- checkout flow
- main file persistence
- backup file replication
- independent backup replica server synchronization

This provides a solid foundation for the final report and presentation demo.
