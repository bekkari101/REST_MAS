# Communication Flow Documentation

This document describes the message communication protocol between agents in the restaurant simulation system.

## 1. Order Request Flow

### Client → Waiter: ORDER_REQUEST
**Message Type:** REQUEST  
**Format:** `ORDER_REQUEST:table:client:item`  
**Example:** `ORDER_REQUEST:Table1:Client1:Pizza`

- Client sends order request to waiter
- Includes table name, client name, and menu item

### Waiter → Client: ORDER_RECEIVED
**Message Type:** CONFIRM  
**Format:** `ORDER_RECEIVED:table:client`  
**Example:** `ORDER_RECEIVED:Table1:Client1`

- Waiter confirms receipt of order
- Client knows order is being processed

## 2. Order Processing Flow

### Waiter → Chef: PREPARE_ORDER
**Message Type:** REQUEST  
**Format:** `PREPARE_ORDER:table:client:item`  
**Example:** `PREPARE_ORDER:Table1:Client1:Pizza`

- Waiter requests chef to prepare the order
- Includes full order details

### Chef → Waiter: ORDER_READY
**Message Type:** INFORM  
**Format:** `ORDER_READY:table:client:item`  
**Example:** `ORDER_READY:Table1:Client1:Pizza`

- Chef informs waiter that food is prepared
- Waiter can now deliver to client

## 3. Food Delivery Flow

### Waiter → Client: FOOD_DELIVERED
**Message Type:** INFORM  
**Format:** `FOOD_DELIVERED`

- Waiter delivers food to client
- Client can start eating

## 4. Payment Flow

### Client → Cashier: PAYMENT_REQUEST
**Message Type:** REQUEST  
**Format:** `PAYMENT_REQUEST:table:client:amount`  
**Example:** `PAYMENT_REQUEST:Table1:Client1:12.50`

- Client requests payment processing
- Includes table, client name, and payment amount

### Cashier → Client: PAYMENT_COMPLETE
**Message Type:** INFORM  
**Format:** `PAYMENT_COMPLETE`

- Cashier confirms payment is complete
- Client can now leave restaurant

## Message Performatives Used

- **REQUEST**: Used for requesting actions (ORDER_REQUEST, PREPARE_ORDER, PAYMENT_REQUEST)
- **CONFIRM**: Used for confirming receipt (ORDER_RECEIVED)
- **INFORM**: Used for notifying status (ORDER_READY, FOOD_DELIVERED, PAYMENT_COMPLETE)

## Implementation Details

### Files Modified
1. **ClientAgent.java**
   - Sends ORDER_REQUEST (REQUEST)
   - Receives ORDER_RECEIVED (CONFIRM)
   - Receives FOOD_DELIVERED (INFORM)
   - Sends PAYMENT_REQUEST (REQUEST) with calculated amount
   - Receives PAYMENT_COMPLETE (INFORM)

2. **WaiterAgent.java**
   - Receives ORDER_REQUEST (REQUEST)
   - Sends ORDER_RECEIVED (CONFIRM)
   - Sends PREPARE_ORDER (REQUEST) to chef
   - Receives ORDER_READY (INFORM) from chef
   - Sends FOOD_DELIVERED (INFORM) to client

3. **ChefAgent.java**
   - Receives PREPARE_ORDER (REQUEST)
   - Sends ORDER_READY (INFORM) with full order details

4. **CashierAgent.java**
   - Receives PAYMENT_REQUEST (REQUEST)
   - Sends PAYMENT_COMPLETE (INFORM)
   - Reports profit to BossAgent

## Benefits of This Protocol

1. **Clear Request-Response Pattern**: Each request gets an acknowledgment
2. **Proper ACL Performatives**: Uses JADE's standard message types correctly
3. **Traceable Flow**: Each step is logged and can be tracked
4. **Error Handling**: Agents can detect missing confirmations
5. **Scalability**: Easy to add new message types or agents
