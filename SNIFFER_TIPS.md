# JADE Sniffer Tips for Multi-Container Systems

## The Problem
When agents communicate across different containers, JADE's Sniffer tool may not show all messages, especially CONFIRM messages. This happens because:

1. **Container Boundaries**: Sniffer has limited visibility across container boundaries
2. **Local AIDs**: Using `ISLOCALNAME` doesn't include container information
3. **Message Routing**: Messages between containers take different paths

## The Solution Applied

### 1. Use Global AIDs
✅ **ClientAgent** now discovers WaiterAgent via DF Service to get global AID
✅ **WaiterAgent** registers with DF Service for discoverability
✅ Both agents explicitly set `msg.setSender(getAID())` for visibility

### 2. Service Registration
WaiterAgent now registers with JADE's Directory Facilitator (DF):
```java
jade.domain.DFAgentDescription dfd = new jade.domain.DFAgentDescription();
dfd.setName(getAID());
jade.domain.FIPAAgentManagement.ServiceDescription sd = new jade.domain.FIPAAgentManagement.ServiceDescription();
sd.setType("waiter-service");
sd.setName("Restaurant-Waiter");
dfd.addServices(sd);
jade.domain.DFService.register(this, dfd);
```

### 3. Global AID Discovery
ClientAgent finds WaiterAgent globally:
```java
jade.domain.DFAgentDescription dfd = new jade.domain.DFAgentDescription();
jade.domain.FIPAAgentManagement.ServiceDescription sd = new jade.domain.FIPAAgentManagement.ServiceDescription();
sd.setType("waiter-service");
dfd.addServices(sd);
jade.domain.DFAgentDescription[] result = jade.domain.DFService.search(this, dfd);
if (result.length > 0) {
    msg.addReceiver(result[0].getName()); // Global AID with container info
}
```

## How to Use Sniffer

### Step 1: Start Sniffer
1. Open JADE GUI
2. Tools → Sniffer Agent

### Step 2: Add Agents to Monitor

**⚠️ IMPORTANT: Do NOT add Client agents to Sniffer!**

ClientAgent moves between 6 containers constantly, causing JADE errors:
```
Agent not found. getContainerID() failed to find agent Client1
```

**✅ Safe to Add (agents that stay in one container):**
- **Boss1** (BossContainer)
- **Waiter1** (WaiterContainer - occasionally moves)
- **Chef1** (ChefContainer)
- **Cashier1** (CashierContainer)
- **Table1, Table2, Table3, Table4** (TableContainer)
- **Enter1** (EntryContainer)
- **Exit1** (ExitContainer)

**❌ Do NOT Add:**
- **Client1, Client2, etc.** (move between 6 containers)

### Step 3: Start Monitoring
1. Click "Start" button in Sniffer
2. Messages will appear as arrows between agents
3. Check console logs for Client messages (Sniffer can't track them)

## Tips for Better Visibility

### ✅ DO:
- Add agents to Sniffer BEFORE starting simulation
- Use global AIDs for cross-container messaging
- Set explicit sender: `msg.setSender(getAID())`
- Use `msg.createReply()` - it preserves global AIDs
- Register services with DF for discoverability

### ❌ DON'T:
- Use `ISLOCALNAME` for agents in different containers
- Add **ClientAgent** to Sniffer (moves between 6 containers)
- Add agents to Sniffer during container transitions
- Rely on Sniffer for agents that frequently move containers
- Start Sniffer tracking after agents are already moving

## Alternative: Use Console Logs
Our agents have detailed DEBUG logs:
```
[DEBUG] Waiter1@WaiterContainer | Sent ORDER_RECEIVED confirmation to Client1
```

These logs show:
- Agent name
- Current container
- Message type
- Recipient/sender info

## Known Limitations

1. **Container Transitions**: Messages sent during `doMove()` might not appear
2. **ClientAgent Cannot Be Tracked**: Sniffer fails with error when tracking agents that move between containers:
   ```
   Agent not found. getContainerID() failed to find agent Client1
   ```
   This is a JADE limitation - agents in transit are temporarily unavailable
3. **Timing**: Very fast message exchanges might be missed
4. **GUI Updates**: Sniffer GUI updates may lag behind actual messages

## Verification Checklist

When debugging message flow:
- ✅ Check console for DEBUG messages
- ✅ Verify sender and receiver are in expected containers
- ✅ Confirm message performative (REQUEST/CONFIRM/INFORM)
- ✅ Check conversation, **visible in Sniffer** (when adding stationary agents):
1. ⚠️ Client → Waiter: ORDER_REQUEST (REQUEST) - **Won't see in Sniffer, check console**
2. ⚠️ Waiter → Client: ORDER_RECEIVED (CONFIRM) - **Won't see in Sniffer, check console**
3. ✅ Waiter → Chef: PREPARE_ORDER (REQUEST) - **Visible** (both stay in containers)
4. ✅ Chef → Waiter: ORDER_READY (INFORM) - **Visible** (both stay in containers)
5. ⚠️ Waiter → Client: FOOD_DELIVERED (INFORM) - **Won't see in Sniffer, check console**
6. ⚠️ Client → Cashier: PAYMENT_REQUEST (REQUEST) - **Won't see in Sniffer, check console**
7. ⚠️ Cashier → Client: PAYMENT_COMPLETE (INFORM) - **Won't see in Sniffer, check console**

**Best Practice**: Use console DEBUG logs to track Client messages, use Sniffer only for stationary agents. **This was missing!**
3. ✅ Waiter → Chef: PREPARE_ORDER (REQUEST)
4. ✅ Chef → Waiter: ORDER_READY (INFORM)
5. ✅ Waiter → Client: FOOD_DELIVERED (INFORM)
6. ✅ Client → Cashier: PAYMENT_REQUEST (REQUEST)
7. ✅ Cashier → Client: PAYMENT_COMPLETE (INFORM)
