# 🐛 Debug Console Guide

## Overview
The Debug Console is a powerful real-time monitoring tool integrated into the Neon Bistro Sim web dashboard. It provides colored, filterable debug messages from all agents in the system, giving you complete visibility into the restaurant simulation.

## Features

### 🎨 Color-Coded Message Levels
- **DEBUG** (Gray): Technical details and state information
- **INFO** (Blue): General information and status updates
- **SUCCESS** (Green): Successful operations and completions
- **WARNING** (Orange): Important events like cooking
- **ERROR** (Red): Problems and failures

### 🔍 Filtering
Click any level button to filter messages:
- **ALL**: Show all messages (default)
- **DEBUG**: Only technical details
- **INFO**: Only informational messages
- **SUCCESS**: Only successful operations
- **WARNING**: Only warnings
- **ERROR**: Only errors

### 📜 Auto-Scroll
- **AUTO-SCROLL ✓** (Green): Automatically scroll to latest messages
- **AUTO-SCROLL ✗** (Gray): Manual scrolling enabled
- Click to toggle between modes

### 🧹 Clear Console
Click the **CLEAR** button to remove all debug messages from the console.

## Message Format

Each debug message displays:
```
[HH:MM:SS.s] AgentID LEVEL [Container] Message
```

Example:
```
[14:23:45.3] Client1 INFO [TableContainer] Ordering food: Pizza
```

## Agent Activity You'll See

### 🚶 Client Agents (Green)
- Agent initialization
- Moving to restaurant entrance
- Table assignment
- Food ordering
- Eating
- Payment processing
- Leaving restaurant

### 🏃 Waiter Agents (Blue)
- Receiving order requests
- Confirming orders to clients
- Processing orders in queue
- Delivering food
- Order completion

### 👨‍🍳 Chef Agents (Orange)
- Receiving cooking requests
- Cooking food (WARNING level)
- Order completion (SUCCESS level)
- Notifying waiters

### 💰 Cashier Agents (Purple)
- Processing payments
- Payment confirmations
- Reporting profits to boss

## How It Works

### Backend (Flask API)
The Flask API (`api/app.py`) provides these endpoints:

1. **POST /debug**: Agents send debug messages here
   ```json
   {
     "agent_id": "Client1",
     "agent_type": "client",
     "container": "TableContainer",
     "level": "INFO",
     "message": "Ordering food: Pizza",
     "status": "ORDERING"
   }
   ```

2. **GET /debug**: Frontend fetches latest messages
   - Optional query params: `agent_id`, `level`, `limit`
   - Example: `/debug?level=ERROR&limit=50`

3. **POST /debug/clear**: Clear all debug messages

### Frontend (HTML/JavaScript)
The debug console updates every 100ms:
- Fetches new messages from `/debug`
- Applies current filter
- Renders colored messages
- Auto-scrolls if enabled

### Java Agents (DebugLogger)
Agents use the `DebugLogger` utility class:

```java
import mas.core.DebugLogger;

// Simple logging
DebugLogger.info(getLocalName(), "client", currentContainer, "Ordering food");

// With log level
DebugLogger.success(getLocalName(), "waiter", container, "Order delivered!");
DebugLogger.warning(getLocalName(), "chef", container, "Cooking Pizza...");
DebugLogger.error(getLocalName(), "client", container, "Failed to order");

// Full control
DebugLogger.log(agentId, agentType, container, Level.INFO, message, status);
```

## Implementation Examples

### ClientAgent
```java
// On initialization
DebugLogger.info(getLocalName(), "client", currentContainer, "Agent initialized and ready");

// On table assignment
DebugLogger.success(getLocalName(), "client", currentContainer, "Assigned to " + tableName + ", walking to table");

// On ordering
DebugLogger.info(getLocalName(), "client", currentContainer, "Ordering food: " + chosenItem);

// On payment
DebugLogger.info(getLocalName(), "client", currentContainer, "Finished eating, going to pay");
```

### WaiterAgent
```java
// On receiving order
DebugLogger.info(getLocalName(), "waiter", getCurrentContainerName(), "Order received from " + clientName + ": " + menuItem);

// On confirmation
DebugLogger.success(getLocalName(), "waiter", getCurrentContainerName(), "Order confirmed to " + clientName);

// On delivery
DebugLogger.success(getLocalName(), "waiter", getCurrentContainerName(), "Food delivered to " + clientName);
```

### ChefAgent
```java
// On cooking
DebugLogger.warning(getLocalName(), "chef", getCurrentContainerName(), "Cooking " + menuItem + " for " + clientName);

// On completion
DebugLogger.success(getLocalName(), "chef", getCurrentContainerName(), menuItem + " is ready! Notifying waiter");
```

### CashierAgent
```java
// On payment
DebugLogger.info(getLocalName(), "cashier", getCurrentContainerName(), "Processing payment: $" + amount + " from " + clientName);

// On completion
DebugLogger.success(getLocalName(), "cashier", getCurrentContainerName(), "Payment complete: $" + amount + " from " + clientName);
```

## Usage Tips

### Debugging Agent Flow
1. Filter by **INFO** to see the normal workflow
2. Filter by **SUCCESS** to track completions
3. Filter by **ERROR** to quickly find problems
4. Use **ALL** to see everything

### Performance Monitoring
- Watch message frequency to detect busy agents
- SUCCESS messages indicate workflow completion
- WARNING messages (orange) show active work

### Understanding Agent Communication
- Each message shows the container location
- Message timestamps help track timing
- Agent type colors match the grid visualization

### Best Practices
1. Start Flask API first: `python api/app.py`
2. Then start JADE: `start_all.bat`
3. Open browser: `http://localhost:5001`
4. Click "INITIALIZE SYSTEM" to create agents
5. Click "PLAY" to start simulation
6. Watch the debug console fill with activity!

## Customization

### Adding More Debug Logs
1. Import DebugLogger in your agent:
   ```java
   import mas.core.DebugLogger;
   ```

2. Add logging at key points:
   ```java
   DebugLogger.info(getLocalName(), "agent-type", getCurrentContainerName(), "Your message");
   ```

3. Choose appropriate level:
   - `debug()`: Technical details
   - `info()`: General information
   - `success()`: Achievements
   - `warning()`: Important events
   - `error()`: Problems

### Disabling Debug Logging
To disable debug logging (for performance):
```java
DebugLogger.setEnabled(false);
```

## Troubleshooting

### Messages Not Appearing
1. Check Flask API is running: `http://localhost:5001`
2. Verify agents are initialized and playing
3. Check browser console for errors (F12)
4. Ensure no firewall blocking port 5001

### Too Many Messages
1. Use filters to reduce clutter
2. Click CLEAR to reset
3. Reduce log frequency in agents
4. Increase `MAX_DEBUG_MESSAGES` in `app.py`

### Performance Issues
1. Disable auto-scroll for large message volumes
2. Clear messages periodically
3. Filter to specific levels
4. Consider disabling DebugLogger in production

## Technical Details

### Message Storage
- Messages stored in-memory on Flask server
- Maximum 500 messages (configurable via `MAX_DEBUG_MESSAGES`)
- Circular buffer: oldest messages removed automatically
- No persistent storage (resets on server restart)

### Update Frequency
- Frontend polls every 100ms
- Minimal network overhead (only new messages sent)
- Timestamp-based incremental updates

### Browser Compatibility
- Tested on Chrome, Firefox, Edge
- Requires ES6+ JavaScript support
- CSS Grid layout

## Summary

The Debug Console provides:
✅ Real-time agent monitoring
✅ Color-coded message levels
✅ Filterable by level
✅ Container location tracking
✅ Auto-scroll capability
✅ Clean, modern UI
✅ Easy integration with agents

Perfect for development, debugging, and demonstrations!
