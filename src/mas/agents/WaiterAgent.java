package mas.agents;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import mas.core.BaseAgent;
import mas.core.QueueManager;
import mas.core.AgentStatus;
import mas.core.TickSystem;
import mas.core.TickDuration;
import mas.core.GridEnvironment;
import mas.core.DebugLogger;

/**
 * WaiterAgent handles table service and order taking.
 * Moves: WaiterContainer → EnvContainer → TableContainer → EnvContainer → ChefContainer → back to client
 */
public class WaiterAgent extends BaseAgent {
    private static final long serialVersionUID = 1L;
    private QueueManager orderQueue;
    private String currentOrder = null;
    private String currentTable = null;
    private String currentClient = null;
    private boolean waitingForChef = false;  // Flag to track if waiting for chef's ORDER_READY
    private int ordersCompleted = 0;  // Track completed orders for debugging
    
    /**
     * Constructor for WaiterAgent with position coordinates
     * @param x X coordinate position
     * @param y Y coordinate position
     */
    public WaiterAgent(double x, double y) {
        super(x, y);
        this.orderQueue = new QueueManager();
    }
    
    /**
     * Default constructor - position set to (0, 0)
     */
    public WaiterAgent() {
        super();
        this.orderQueue = new QueueManager();
    }
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null) {
            initializePosition(args);
        }
        System.out.println("WaiterAgent " + getLocalName() + " initialized at position " + getPositionString());
        
        // Register with DFService for discoverability
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("waiter-service");
            sd.setName("Restaurant-Waiter");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) {
            System.err.println("WaiterAgent: Error registering with DF: " + e.getMessage());
        }
        
        // Initialize status
        status = AgentStatus.WAITER_IDLE;
        
        // Add behavior to handle order requests
        addBehaviour(new OrderHandlerBehaviour());
        
        // Add periodic heartbeat to ensure waiter stays visible in GUI
        addBehaviour(new TickerBehaviour(this, 2000) {
            private static final long serialVersionUID = 1L;
            @Override
            protected void onTick() {
                // Send state to API to keep waiter visible
                sendStateToAPI();
                // Debug log current state
                System.out.println("[HEARTBEAT] " + getDebugInfo() + " | Status: " + status + 
                    ", waitingForChef: " + waitingForChef + 
                    ", currentOrder: " + (currentOrder != null ? "yes" : "no") +
                    ", queueSize: " + orderQueue.size() +
                    ", ordersCompleted: " + ordersCompleted);
            }
        });
    }
    
    private String currentMenuItem = null;
    
    /**
     * Behavior to handle order requests and process them
     */
    private class OrderHandlerBehaviour extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void action() {
            // Check for new messages (orders from clients or ready notifications from chef)
            ACLMessage msg = receive();
            
            if (msg != null && msg.getContent().startsWith("ORDER_REQUEST:")) {
                // Parse order request: ORDER_REQUEST:tableName:clientName:menuItem
                String[] parts = msg.getContent().split(":");
                if (parts.length >= 3) {
                    String tableName = parts[1];
                    String clientName = parts[2];
                    String menuItem = parts.length >= 4 ? parts[3] : "unknown";
                    
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Received ORDER_REQUEST from client " + clientName);
                    DebugLogger.info(getLocalName(), "waiter", getCurrentContainerName(), "Order received from " + clientName + ": " + menuItem);
                    
                    // Send ORDER_RECEIVED confirmation to client
                    // Using createReply() preserves global AID for cross-container messaging
                    ACLMessage confirmMsg = msg.createReply();
                    confirmMsg.setPerformative(ACLMessage.CONFIRM);
                    confirmMsg.setContent("ORDER_RECEIVED:" + tableName + ":" + clientName);
                    confirmMsg.setSender(getAID()); // Explicitly set sender for Sniffer visibility
                    send(confirmMsg);
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Sent ORDER_RECEIVED confirmation to " + clientName + " (AID: " + msg.getSender().getName() + ")");
                    DebugLogger.success(getLocalName(), "waiter", getCurrentContainerName(), "Order confirmed to " + clientName);
                    
                    // Add to order queue
                    orderQueue.enqueue(msg.getContent());
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Order request (" + menuItem + ") added to queue from " + clientName + " at table " + tableName);
                    
                    // Process order if not busy AND not waiting for chef
                    if (currentOrder == null && status == AgentStatus.WAITER_IDLE && !waitingForChef) {
                        processNextOrder();
                    }
                }
            } else if (msg != null && msg.getContent().startsWith("ORDER_READY:")) {
                // Order is ready, deliver to client
                System.out.println("[DEBUG] " + getDebugInfo() + " | Received ORDER_READY from chef: " + msg.getContent());
                waitingForChef = false;  // No longer waiting for chef
                
                // Parse the ORDER_READY message to update current order info if needed
                String[] parts = msg.getContent().split(":");
                if (parts.length >= 4) {
                    currentTable = parts[1];
                    currentClient = parts[2];
                    currentMenuItem = parts[3];
                }
                
                DebugLogger.success(getLocalName(), "waiter", getCurrentContainerName(), "Food ready! Delivering to " + currentClient);
                deliverOrderToClient();
            } else if (msg != null) {
                // Other messages
                System.out.println("[DEBUG] " + getDebugInfo() + " | Received other message: " + msg.getContent() + " (Performative: " + ACLMessage.getPerformative(msg.getPerformative()) + ")");
                // Only process next order if truly idle and not waiting for chef
                if (status == AgentStatus.WAITER_IDLE && !waitingForChef && !orderQueue.isEmpty() && currentOrder == null) {
                    processNextOrder();
                }
            } else {
                block();
            }
        }
        
        /**
         * Process next order from queue
         */
        private void processNextOrder() {
            if (!orderQueue.isEmpty()) {
                String order = orderQueue.dequeue();
                String[] parts = order.split(":");
                if (parts.length >= 3) {
                    currentTable = parts[1];
                    currentClient = parts[2];
                    currentMenuItem = parts.length >= 4 ? parts[3] : "unknown";
                    currentOrder = order;
                    
                    setStatus(AgentStatus.WAITER_GETTING_ORDER);
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Processing order for " + currentClient + " at table " + currentTable + " (Item: " + currentMenuItem + ")");
                    DebugLogger.info(getLocalName(), "waiter", getCurrentContainerName(), "Processing order: " + currentMenuItem + " for " + currentClient);
                    
                    // Move to table to take order
                    moveToTable();
                }
            } else {
                setStatus(AgentStatus.WAITER_IDLE);
            }
        }
        
        /**
         * Move to table to take order
         */
        private void moveToTable() {
            try {
                setStatus(AgentStatus.WAITER_MOVING_TO_TABLE);
                System.out.println("[DEBUG] " + getDebugInfo() + " | Moving to TableContainer for table " + currentTable);
                
                // Move through EnvContainer
                moveToContainer("EnvContainer");
            } catch (Exception e) {
                System.err.println("WaiterAgent: Error moving to table: " + e.getMessage());
            }
        }
        
        /**
         * Move to ChefContainer to deliver order
         */
        private void moveToChefContainer() {
            try {
                setStatus(AgentStatus.WAITER_MOVING_TO_CHEF);
                System.out.println("[DEBUG] " + getDebugInfo() + " | Moving to ChefContainer with order from " + currentClient);
                
                // Move through EnvContainer
                moveToContainer("EnvContainer");
            } catch (Exception e) {
                System.err.println("WaiterAgent: Error moving to chef: " + e.getMessage());
            }
        }
        
        /**
         * Deliver prepared order to client
         */
        private void deliverOrderToClient() {
            try {
                setStatus(AgentStatus.WAITER_SERVING_ORDER);
                System.out.println("[DEBUG] " + getDebugInfo() + " | Order ready, moving to deliver to table " + currentTable);
                
                // Move through EnvContainer
                moveToContainer("EnvContainer");
            } catch (Exception e) {
                System.err.println("WaiterAgent: Error delivering order: " + e.getMessage());
            }
        }
    }
    

    /**
     * Callback when movement is finished. Handles transitions for waiter tasks.
     */
    @Override
    protected void onMovementFinished() {
        String container = getCurrentContainerName();
        System.out.println("[DEBUG] " + getDebugInfo() + " | onMovementFinished in " + container + " with status " + status);
        
        if (status == AgentStatus.WAITER_MOVING_TO_TABLE) {
            if (container.equals("EnvContainer")) {
                if (Math.abs(x - 15.0) < 2.0 && Math.abs(y - 15.0) < 2.0) {
                    moveToContainer("TableContainer");
                }
            } else if (container.equals("TableContainer")) {
                // Already sent ORDER_RECEIVED when request was received
                // Now simulate taking order and move to chef
                System.out.println("[DEBUG] " + getDebugInfo() + " | At table, preparing to deliver order to chef");
                
                long delay = TickDuration.WAITER_TAKING_ORDER.getMilliseconds();
                addBehaviour(new TickerBehaviour(this, delay) {
                    private static final long serialVersionUID = 1L;
                    @Override
                    protected void onTick() {
                        ((WaiterAgent)myAgent).moveToChefContainer();
                        stop();
                    }
                });
            }
        } else if (status == AgentStatus.WAITER_MOVING_TO_CHEF) {
            if (container.equals("EnvContainer")) {
                if (Math.abs(x - 15.0) < 2.0 && Math.abs(y - 15.0) < 2.0) {
                    moveToContainer("ChefContainer");
                }
            } else if (container.equals("ChefContainer")) {
                // Request chef to prepare order
                System.out.println("[DEBUG] " + getDebugInfo() + " | Arrived at chef, requesting order preparation");
                AID chefAID = new AID("Chef1", AID.ISLOCALNAME);
                ACLMessage orderMsg = new ACLMessage(ACLMessage.REQUEST);
                orderMsg.addReceiver(chefAID);
                orderMsg.setContent("PREPARE_ORDER:" + currentTable + ":" + currentClient + ":" + currentMenuItem);
                orderMsg.setConversationId("Order-Step-2");
                orderMsg.setOntology("Restaurant-Service");
                send(orderMsg);
                System.out.println("[DEBUG] " + getDebugInfo() + " | Sent PREPARE_ORDER request to chef for (" + currentMenuItem + ")");
                
                // Mark that we're waiting for the chef - don't process new orders
                waitingForChef = true;
                // Set status to show waiter is waiting, but use a waiting-specific status or keep IDLE
                // Use WAITER_IDLE but the waitingForChef flag prevents new order processing
                setStatus(AgentStatus.WAITER_IDLE);
                System.out.println("[DEBUG] " + getDebugInfo() + " | Waiting for chef to prepare order (waitingForChef=true)");
            }
        } else if (status == AgentStatus.WAITER_SERVING_ORDER) {
            if (container.equals("EnvContainer")) {
                if (Math.abs(x - 15.0) < 2.0 && Math.abs(y - 15.0) < 2.0) {
                    moveToContainer("TableContainer");
                }
            } else if (container.equals("TableContainer")) {
                System.out.println("[DEBUG] " + getDebugInfo() + " | Delivering food to " + currentClient);
                System.out.println("[DEBUG] " + getDebugInfo() + " | Informing client about ready order");
                
                AID clientAID = new AID(currentClient, AID.ISLOCALNAME);
                ACLMessage deliveryMsg = new ACLMessage(ACLMessage.INFORM);
                deliveryMsg.addReceiver(clientAID);
                deliveryMsg.setContent("FOOD_DELIVERED");
                deliveryMsg.setConversationId("Order-Step-4");
                deliveryMsg.setOntology("Restaurant-Service");
                send(deliveryMsg);
                System.out.println("[DEBUG] " + getDebugInfo() + " | Informed client " + currentClient + " ready order delivered: FOOD_DELIVERED");
                
                // Done with this order - increment counter and clear state
                ordersCompleted++;
                System.out.println("[DEBUG] " + getDebugInfo() + " | Order #" + ordersCompleted + " completed for " + currentClient);
                DebugLogger.success(getLocalName(), "waiter", getCurrentContainerName(), "Delivered order #" + ordersCompleted + " to " + currentClient);
                
                currentOrder = null;
                currentTable = null;
                currentClient = null;
                currentMenuItem = null;
                waitingForChef = false;  // Ensure flag is cleared
                setStatus(AgentStatus.WAITER_IDLE);
                
                // Process next order if any - with small delay to ensure state is stable
                addBehaviour(new TickerBehaviour(this, 500) {
                    private static final long serialVersionUID = 1L;
                    @Override
                    protected void onTick() {
                        triggerNextOrder();
                        stop();
                    }
                });
            }
        }
    }

    /**
     * Override afterMove to handle next steps after container transition
     */
    @Override
    protected void afterMove() {
        super.afterMove();
        System.out.println("[DEBUG] " + getDebugInfo() + " | afterMove hook called, status=" + status);
        
        String container = getCurrentContainerName();
        if (container.equals("EnvContainer")) {
            moveTo(15.0, 15.0); // Move to transit point
        } else if (container.equals("TableContainer")) {
            if (currentTable != null) {
                int tableNum = Integer.parseInt(currentTable.replace("Table", ""));
                double tableX = GridEnvironment.TABLE_BASE_X + (tableNum - 1) * GridEnvironment.TABLE_SPACING_X;
                double tableY = GridEnvironment.TABLE_Y;
                moveTo(tableX, tableY);
            } else {
                // No current table, just move to default position
                moveTo(GridEnvironment.TABLE_BASE_X, GridEnvironment.TABLE_Y);
            }
        } else if (container.equals("ChefContainer")) {
            moveTo(GridEnvironment.CHEF1_X, GridEnvironment.CHEF1_Y);
        }
        
        // Ensure we're still registered with tick system after move
        sendStateToAPI();
    }
    
    /**
     * Trigger next order processing from anywhere
     */
    public void triggerNextOrder() {
        System.out.println("[DEBUG] " + getDebugInfo() + " | triggerNextOrder called - status=" + status + 
            ", waitingForChef=" + waitingForChef + ", queueSize=" + orderQueue.size() + 
            ", currentOrder=" + (currentOrder != null));
        
        // Only process if truly idle and not waiting for chef
        if (status == AgentStatus.WAITER_IDLE && !waitingForChef && currentOrder == null && !orderQueue.isEmpty()) {
            // Send ourselves a dummy message to wake up the CyclicBehaviour
            ACLMessage dummy = new ACLMessage(ACLMessage.NOT_UNDERSTOOD);
            dummy.addReceiver(getAID());
            dummy.setContent("TRIGGER_NEXT_ORDER");
            send(dummy);
            System.out.println("[DEBUG] " + getDebugInfo() + " | Sent trigger message to process next order");
        } else if (!orderQueue.isEmpty()) {
            System.out.println("[DEBUG] " + getDebugInfo() + " | Cannot process next order yet - busy or waiting");
        }
    }
    
    // Helper to call from inner class
    private void moveToChefContainer() {
        try {
            setStatus(AgentStatus.WAITER_MOVING_TO_CHEF);
            System.out.println("[DEBUG] " + getDebugInfo() + " | Moving to ChefContainer");
            moveToContainer("EnvContainer");
        } catch (Exception e) {}
    }
}
