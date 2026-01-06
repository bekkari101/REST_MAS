# Restaurant Simulation Scenario - Detailed Workflow

## Overview
This document describes the complete workflow of the multi-agent restaurant simulation system, including client movement, table management, ordering, and payment processes.

## Agent Movement Flow

### 1. Client Entry and Queuing System

#### Initial State
- Clients start in **ClientContainer**
- Clients move to **EnterContainer** to join the restaurant

#### EnterContainer Queue Management
- When a client agent enters **EnterContainer**, they are added to a **FIFO (First In First Out) queue**
- The **EnterAgent** continuously checks the queue and processes clients in order

#### Table Availability Check
- **EnterAgent** checks **TableContainer** for available tables
- Each table has a status: `available = true` (free) or `available = false` (occupied)
- **EnterAgent** queries all tables in TableContainer to find one with `available = true`

#### Table Assignment
- **If table is available (true):**
  - Client is assigned to that table
  - Client moves from EnterContainer → EnvContainer → TableContainer
  - Table status is set to `available = false` (occupied)
  
- **If no table is available (all false):**
  - Client remains in the queue in EnterContainer
  - EnterAgent continues checking periodically until a table becomes available

## 2. Complete Client Movement Path

### Entry Flow
```
ClientContainer → EnterContainer → EnvContainer → TableContainer
```

### Exit Flow
```
TableContainer → CashierContainer → ExitContainer
```

## 3. Ordering and Service Workflow

### Step 1: Client Arrives at Table
- Client agent moves to **TableContainer** and occupies a table
- Table status changes to `available = false`

### Step 2: Order Request
- When table is occupied (status = false), client adds a **request to WaiterQueue**
- WaiterQueue is managed in **WaiterContainer**

### Step 3: Waiter Service
- Waiter agent picks up request from WaiterQueue
- Waiter moves: **WaiterContainer → EnvContainer → TableContainer**
- Waiter arrives at the table and identifies which client agent is there
- Waiter takes the order from the client

### Step 4: Order Processing
- After taking order, waiter exits TableContainer
- Waiter moves: **TableContainer → EnvContainer → ChefContainer**
- Waiter delivers order to ChefContainer (adds to chef's order queue)
- Waiter waits for order to be prepared

### Step 5: Food Delivery
- Waiter retrieves prepared order from ChefContainer
- Waiter moves: **ChefContainer → EnvContainer → TableContainer**
- Waiter delivers food to the client at the table

### Step 6: Payment
- Client finishes eating
- Client moves: **TableContainer → EnvContainer → CashierContainer**
- Client pays at CashierContainer
- After payment, table status is set back to `available = true`

### Step 7: Exit
- Client moves: **CashierContainer → EnvContainer → ExitContainer**
- ExitAgent checks every 11 seconds and kills client agents in ExitContainer
- Client is removed from the system

## Complete Workflow Diagram

```
┌─────────────────┐
│ ClientContainer │ (Initial state)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ EnterContainer  │ (Join FIFO queue)
│  - EnterAgent   │ (Checks table availability)
└────────┬────────┘
         │
         │ Table Available?
         │ Yes ▼
         │
┌─────────────────┐
│  EnvContainer   │ (Transit container)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ TableContainer  │ (Client sits at table)
│  - TableAgent   │ (Status: available = false)
└────────┬────────┘
         │
         │ Client orders
         ▼
┌─────────────────┐
│ WaiterContainer │ (WaiterQueue: Add order request)
└────────┬────────┘
         │
         │ Waiter picks order
         ▼
┌─────────────────┐
│  EnvContainer   │ (Waiter transit)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ TableContainer  │ (Waiter takes order from client)
└────────┬────────┘
         │
         │ Waiter exits
         ▼
┌─────────────────┐
│  EnvContainer   │ (Waiter transit)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ ChefContainer   │ (Waiter delivers order, chef prepares)
└────────┬────────┘
         │
         │ Food ready
         ▼
┌─────────────────┐
│  EnvContainer   │ (Waiter transit with food)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ TableContainer  │ (Waiter delivers food to client)
└────────┬────────┘
         │
         │ Client finishes eating
         ▼
┌─────────────────┐
│  EnvContainer   │ (Client transit)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│CashierContainer │ (Client pays)
└────────┬────────┘
         │
         │ Payment complete
         │ Table status: available = true
         ▼
┌─────────────────┐
│  EnvContainer   │ (Client transit)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ ExitContainer   │ (Client exits)
│  - ExitAgent    │ (Kills client every 11 seconds)
└─────────────────┘
```

## Key Components

### Containers
1. **ClientContainer**: Initial client waiting area
2. **EnterContainer**: Entry point with FIFO queue management
3. **EnvContainer**: Transit container for agent movement
4. **TableContainer**: Dining area with tables
5. **WaiterContainer**: Waiter management and order queue
6. **ChefContainer**: Kitchen for food preparation
7. **CashierContainer**: Payment processing
8. **ExitContainer**: Final exit point
9. **BossContainer**: Management and oversight

### Agents
- **EnterAgent**: Manages client queue and table assignment
- **TableAgent**: Represents tables with availability status
- **ClientAgent**: Customers moving through the system
- **WaiterAgent**: Takes orders, delivers to chef, serves food
- **ChefAgent**: Prepares food orders
- **CashierAgent**: Processes payments
- **ExitAgent**: Removes clients from system (every 11 seconds)

## State Management

### Table Status
- `available = true`: Table is free, can accept new client
- `available = false`: Table is occupied, client is dining

### Queue Management
- **EnterContainer Queue**: FIFO queue for clients waiting for tables
- **WaiterQueue**: Queue of order requests from clients at tables

## Timing
- **ExitAgent Check Interval**: Every 2 seconds
- **EnterAgent Check Interval**: Continuous monitoring of queue and table availability

