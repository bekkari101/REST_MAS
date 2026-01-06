package mas.agents;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import mas.core.BaseAgent;
import mas.core.AgentStatus;
import mas.core.TickSystem;
import mas.core.TickDuration;
import mas.core.GridEnvironment;
import mas.core.Menu;
import mas.core.DebugLogger;

/**
 * ClientAgent represents customers in the restaurant.
 * Moves through: ClientContainer → EntryContainer → EnvContainer → TableContainer → CashierContainer → ExitContainer
 */
public class ClientAgent extends BaseAgent {
    private static final long serialVersionUID = 1L;
    private String currentContainer = "ClientContainer";
    private String assignedTable = null;
    private boolean hasOrdered = false;
    private boolean hasEaten = false;
    private boolean hasPaid = false;
    private int moveTicksRemaining = 0;  // Ticks remaining for current movement
    
    /**
     * Override getDebugInfo to use our currentContainer field instead of querying JADE
     */
    @Override
    protected String getDebugInfo() {
        return getLocalName() + " [Container: " + currentContainer + "@" + (here() != null ? here().getName() : "unknown") + ", Position: " + getPositionString() + "]";
    }
    
    /**
     * Constructor for ClientAgent with position coordinates
     * @param x X coordinate position
     * @param y Y coordinate position
     */
    public ClientAgent(double x, double y) {
        super(x, y);
    }
    
    /**
     * Default constructor - position set to (0, 0)
     */
    public ClientAgent() {
        super();
    }
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null) {
            initializePosition(args);
        }
        System.out.println("==========================================");
        System.out.println("ClientAgent " + getLocalName() + " initialized at position " + getPositionString());
        System.out.println("Current container: " + currentContainer);
        System.out.println("==========================================");
        
        // Initialize status
        status = AgentStatus.WAITING_IN_QUEUE;
        DebugLogger.info(getLocalName(), "client", currentContainer, "Agent initialized and ready");
        
        // Register with DFService
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("client-agent");
            sd.setName("ClientAgent");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) {
            System.err.println("ClientAgent: Error registering with DF: " + e.getMessage());
        }
        
        // Add behavior to handle messages
        addBehaviour(new ClientMessageHandler());
        
        // Add behavior to wait for simulation to start before moving
        addBehaviour(new jade.core.behaviours.TickerBehaviour(this, 1000) { // Check every second
            private static final long serialVersionUID = 1L;
            private boolean hasStarted = false;
            
            @Override
            protected void onTick() {
                // Wait for TickSystem to start before beginning movement
                if (!hasStarted && TickSystem.getInstance().isRunning()) {
                    hasStarted = true;
                    System.out.println("==========================================");
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Simulation started, beginning movement to EntryContainer");
                    System.out.println("==========================================");
                    DebugLogger.success(getLocalName(), "client", currentContainer, "Simulation started! Moving to entrance");
                    moveToEntryContainer();
                    stop(); // Stop this behavior once we've started
                }
            }
        });
    }
    
    /**
     * Move client to EntryContainer
     */
    private void moveToEntryContainer() {
        try {
            setStatus(AgentStatus.MOVING_TO_TABLE);
            System.out.println("[DEBUG] " + getDebugInfo() + " | Moving to EntryContainer");
            DebugLogger.info(getLocalName(), "client", currentContainer, "Moving to restaurant entrance");
            
            // Actually move to EntryContainer using JADE mobility
            // Post-move logic is now in afterMove()
            System.out.println("[DEBUG] " + getDebugInfo() + " | Triggering doMove to EntryContainer...");
            moveToContainer("EntryContainer");
            
        } catch (Exception e) {
            System.err.println("ClientAgent: Error moving to EntryContainer: " + e.getMessage());
        }
    }
    
    /**
     * Get table coordinates for a given table number
     */
    private double[] getTableCoordinates(int tableNumber) {
        double tableX = GridEnvironment.TABLE_BASE_X + (tableNumber - 1) * GridEnvironment.TABLE_SPACING_X;
        double tableY = GridEnvironment.TABLE_Y;
        return new double[]{tableX, tableY};
    }
    
    /**
     * Check if current position is at/near any table coordinate
     * @return Table number (1-4) if at table, 0 otherwise
     */
    private int checkIfAtTableCoordinate() {
        double tolerance = 1.5; // Within 1.5 units of table position
        for (int i = 1; i <= 4; i++) {
            double[] tablePos = getTableCoordinates(i);
            double distX = Math.abs(x - tablePos[0]);
            double distY = Math.abs(y - tablePos[1]);
            if (distX <= tolerance && distY <= tolerance) {
                System.out.println("[DEBUG] " + getDebugInfo() + " | At table " + i + " coordinates (distance: " + distX + "," + distY + ")");
                return i;
            }
        }
        return 0;
    }
    
    /**
     * Move client through EnvContainer to TableContainer
     */
    private void moveToTable(String tableName) {
        assignedTable = tableName;
        setStatus(AgentStatus.MOVING_TO_TABLE);
        System.out.println("[DEBUG] " + getDebugInfo() + " | Moving to TableContainer (Table: " + tableName + ")");
        DebugLogger.success(getLocalName(), "client", currentContainer, "Assigned to " + tableName + ", walking to table");
        
        String container = getCurrentContainerName();
        if (container.equals("EnvContainer")) {
            int tableNum = Integer.parseInt(tableName.replace("Table", ""));
            double[] tablePos = getTableCoordinates(tableNum);
            moveTo(tablePos[0], tablePos[1]);
        } else if (container.equals("TableContainer")) {
            onMovementFinished(); // Already there
        } else {
            moveToContainer("EnvContainer");
        }
    }
    
    /**
     * Move directly to TableContainer (helper method)
     */
    private void moveToTableContainerDirectly(String tableName, double tableX, double tableY) {
        System.out.println("[DEBUG] " + getDebugInfo() + " | Moving to TableContainer for table " + tableName);
        moveToContainer("TableContainer");
    }
    
    /**
     * Request menu from TableAgent
     */
    private void requestMenu() {
        try {
            System.out.println("[DEBUG] " + getDebugInfo() + " | Requesting menu from " + assignedTable);
            AID tableAID = new AID(assignedTable, AID.ISLOCALNAME);
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(tableAID);
            msg.setContent("GET_MENU");
            msg.setConversationId("Menu-Retrieval");
            msg.setOntology("Restaurant-Init");
            send(msg);
        } catch (Exception e) {
            System.err.println("ClientAgent: Error requesting menu: " + e.getMessage());
        }
    }
    
    /**
     * Request waiter service with chosen item
     */
    private void requestWaiter() {
        setStatus(AgentStatus.ORDERING);
        System.out.println("[DEBUG] " + getDebugInfo() + " | Requesting waiter service for " + chosenItem);
        System.out.println("[DEBUG] " + getDebugInfo() + " | Informing waiter about order: ORDER_REQUEST");
        DebugLogger.info(getLocalName(), "client", currentContainer, "Ordering food: " + chosenItem);
        
        // Add order request to waiter queue
        // Use global AID for cross-container messaging (helps with Sniffer visibility)
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        try {
            // Try to find waiter agent globally
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("waiter-service");
            dfd.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, dfd);
            if (result.length > 0) {
                msg.addReceiver(result[0].getName()); // Global AID
            } else {
                // Fallback to local name
                msg.addReceiver(new AID("Waiter1", AID.ISLOCALNAME));
            }
        } catch (Exception e) {
            // Fallback to local name
            msg.addReceiver(new AID("Waiter1", AID.ISLOCALNAME));
        }
        msg.setContent("ORDER_REQUEST:" + assignedTable + ":" + getLocalName() + ":" + chosenItem);
        msg.setConversationId("Food-Ordering");
        msg.setOntology("Restaurant-Service");
        msg.setSender(getAID()); // Explicitly set sender for cross-container visibility
        send(msg);
        System.out.println("[DEBUG] " + getDebugInfo() + " | Sent order inform to waiter: " + msg.getContent());
        
        hasOrdered = true;
    }
    
    /**
     * Move to CashierContainer for payment
     */
    private void moveToCashier() {
        setStatus(AgentStatus.MOVING_TO_CASHIER);
        System.out.println("[DEBUG] " + getDebugInfo() + " | Moving to CashierContainer");
        DebugLogger.info(getLocalName(), "client", currentContainer, "Finished eating, going to pay");
        
        String container = getCurrentContainerName();
        if (container.equals("EnvContainer")) {
            moveTo(60.0, 60.0); // Move toward cashier area (cashier is at 80,80)
        } else {
            moveToContainer("EnvContainer");
        }
    }
    
    /**
     * Move to ExitContainer
     */
    private void moveToExit() {
        setStatus(AgentStatus.MOVING_TO_EXIT);
        System.out.println("[DEBUG] " + getDebugInfo() + " | Moving to ExitContainer");
        
        String container = getCurrentContainerName();
        if (container.equals("EnvContainer")) {
            moveTo(90.0, 90.0); // Move toward exit area (exit is at 100,100)
        } else {
            moveToContainer("EnvContainer");
        }
    }
    
    /**
     * Ensure client is in correct container based on coordinates
     * Only check when NOT moving to avoid false positives during transit
     */
    private void ensureCorrectContainer() {
        try {
            // Only validate when not moving - during movement, container transitions are expected
            if (isMoving()) {
                return;
            }
            
            String expectedContainer = getExpectedContainerForPosition();
            if (!currentContainer.equals(expectedContainer)) {
                // Only log if we're significantly off - allow some tolerance
                System.out.println("[DEBUG] " + getDebugInfo() + " | Container check - Current: " + currentContainer + ", Expected: " + expectedContainer);
            }
        } catch (Exception e) {
            // Silently ignore - this is just a check
        }
    }
    
    /**
     * Get expected container name based on current position and status
     */
    private String getExpectedContainerForPosition() {
        // Entry coordinates
        if (Math.abs(x - GridEnvironment.ENTRY_X) < 1.0 && Math.abs(y - GridEnvironment.ENTRY_Y) < 1.0) {
            return "EntryContainer";
        }
        
        // Table coordinates
        if (y >= GridEnvironment.TABLE_Y - 5 && y <= GridEnvironment.TABLE_Y + 5) {
            // Check if we're at a specific table position
            if (assignedTable != null) {
                int tableNum = Integer.parseInt(assignedTable.replace("Table", ""));
                double[] tablePos = getTableCoordinates(tableNum);
                // If we're very close to table position, we should be in TableContainer
                if (Math.abs(x - tablePos[0]) < 1.0 && Math.abs(y - tablePos[1]) < 1.0) {
                    return "TableContainer";
                }
            }
        }
        
        // Cashier coordinates
        if (Math.abs(x - GridEnvironment.CASHIER_X) < 2.0 && Math.abs(y - GridEnvironment.CASHIER_Y) < 2.0) {
            return "CashierContainer";
        }

        // Exit coordinates
        if (Math.abs(x - GridEnvironment.EXIT_X) < 5.0 && Math.abs(y - GridEnvironment.EXIT_Y) < 5.0) {
            return "ExitContainer";
        }
        
        // Default: EnvContainer for movement/transit
        return "EnvContainer";
    }
    
    
    private String chosenItem = null;
    
    /**
     * Handle incoming messages
     */
    private class ClientMessageHandler extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String content = msg.getContent();
                
                if (content.startsWith("MOVE_TO_TABLE:")) {
                    String tableName = content.substring("MOVE_TO_TABLE:".length());
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Received MOVE_TO_TABLE message for table: " + tableName);
                    moveToTable(tableName);
                } else if (content.startsWith("MENU:")) {
                    String menuItems = content.substring("MENU:".length());
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Received menu: " + menuItems);
                    
                    // Choose a random item from the menu
                    String[] items = menuItems.split(",");
                    if (items.length > 0 && !items[0].equals("NONE")) {
                        java.util.Random rand = new java.util.Random();
                        chosenItem = items[rand.nextInt(items.length)].trim();
                        System.out.println("[DEBUG] " + getDebugInfo() + " | Decided to order: " + chosenItem);
                        requestWaiter();
                    } else {
                        System.err.println("[WARNING] " + getDebugInfo() + " | Menu is empty or not available. Retrying in 2 seconds...");
                        // Use a one-shot delay to retry
                        addBehaviour(new TickerBehaviour(myAgent, 2000) {
                            @Override
                            protected void onTick() {
                                if (chosenItem == null) {
                                    requestMenu();
                                }
                                stop();
                            }
                        });
                    }
                } else if (content.equals("FOOD_DELIVERED")) {
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Received FOOD_DELIVERED inform from waiter");
                    setStatus(AgentStatus.EATING);
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Food (" + (chosenItem != null ? chosenItem : "unknown") + ") delivered, eating...");
                    hasEaten = true;
                    
                    // After eating (simulated by ticks), move to cashier
                    addBehaviour(new TickerBehaviour(myAgent, TickDuration.CLIENT_EATING.getMilliseconds()) {
                        private int ticksRemaining = 1; // Simulation of eating for 1 "tick" duration
                        @Override
                        protected void onTick() {
                            if (--ticksRemaining <= 0) {
                                moveToCashier();
                                stop();
                            }
                        }
                    });
                } else if (content.equals("PAYMENT_COMPLETE")) {
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Received PAYMENT_COMPLETE inform from cashier");
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Payment complete, leaving restaurant");
                    hasPaid = true;
                    // Free the table BEFORE moving to exit
                    if (assignedTable != null) {
                        freeTable();
                    } else {
                        System.err.println("[WARNING] ClientAgent " + getLocalName() + ": Payment complete but no assigned table to free!");
                    }
                    // Move to exit
                    moveToExit();
                }
            } else {
                block();
            }
        }
    }

    /**
     * Callback when movement is finished. Handles transitions between containers.
     */
    @Override
    protected void onMovementFinished() {
        String container = getCurrentContainerName();
        System.out.println("[DEBUG] " + getDebugInfo() + " | onMovementFinished in " + container + " with status " + status);
        
        // Handle cashier and exit transit in EnvContainer
        if (container.equals("EnvContainer")) {
            if (status == AgentStatus.MOVING_TO_CASHIER) {
                // Check if reached cashier transit point
                if (Math.abs(x - 60.0) < 2.0 && Math.abs(y - 60.0) < 2.0) {
                    moveToContainer("CashierContainer");
                }
            } else if (status == AgentStatus.MOVING_TO_EXIT) {
                // Check if reached exit transit point
                if (Math.abs(x - 90.0) < 2.0 && Math.abs(y - 90.0) < 2.0) {
                    moveToContainer("ExitContainer");
                }
            }
        }
        
        if (status == AgentStatus.MOVING_TO_TABLE) {
            if (container.equals("EntryContainer")) {
                // Reached entry point, wait for assignment (already joined queue in afterMove)
                System.out.println("[DEBUG] " + getDebugInfo() + " | Reached entrance, waiting in queue.");
            } else if (container.equals("EnvContainer")) {
                int atTable = checkIfAtTableCoordinate();
                if (assignedTable != null) {
                    int tableNum = Integer.parseInt(assignedTable.replace("Table", ""));
                    if (atTable == tableNum) {
                        moveToTableContainerDirectly(assignedTable, x, y);
                    } else {
                        // Not there yet, or pathfinding finished early? 
                        // Re-trigger movement if needed, but normally should be there.
                        System.out.println("[DEBUG] " + getDebugInfo() + " | Reached end of path in EnvContainer, but not at table " + tableNum + ". Current at: " + atTable);
                        double[] tablePos = getTableCoordinates(tableNum);
                        moveTo(tablePos[0], tablePos[1]);
                    }
                }
            } else if (container.equals("TableContainer")) {
                System.out.println("[DEBUG] " + getDebugInfo() + " | Reached TableContainer, moving to table " + assignedTable);
                setStatus(AgentStatus.AT_TABLE);
                requestMenu();
            }
        } else if (status == AgentStatus.MOVING_TO_CASHIER) {
            if (container.equals("EnvContainer")) {
                // Reached transit point toward cashier?
                if (Math.abs(x - 60.0) < 2.0 && Math.abs(y - 60.0) < 2.0) {
                    moveToContainer("CashierContainer");
                }
            } else if (container.equals("CashierContainer")) {
                setStatus(AgentStatus.PAYING);
                requestPayment();
            }
        } else if (status == AgentStatus.MOVING_TO_EXIT) {
            if (container.equals("EnvContainer")) {
                 // Reached transit point toward exit?
                 if (Math.abs(x - 90.0) < 2.0 && Math.abs(y - 90.0) < 2.0) {
                    moveToContainer("ExitContainer");
                }
            } else if (container.equals("ExitContainer")) {
                // Check if we've reached the exit position
                if (Math.abs(x - GridEnvironment.EXIT_X) < 1.0 && Math.abs(y - GridEnvironment.EXIT_Y) < 1.0) {
                    setStatus(AgentStatus.EXITING);
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Reached exit position, thank you!");
                    // Client will be killed by ExitAgent on next check (every 2 seconds)
                } else {
                    System.out.println("[DEBUG] " + getDebugInfo() + " | In ExitContainer but not at exit position yet");
                }
            }
        }
    }

    /**
     * Override afterMove to handle logic after a container transition
     */
    @Override
    protected void afterMove() {
        super.afterMove();
        currentContainer = getCurrentContainerName();
        System.out.println("[DEBUG] " + getDebugInfo() + " | afterMove hook called, initiating next actions in " + currentContainer);
        
        if (currentContainer.equals("EntryContainer")) {
            // Move to entry position
            moveTo(GridEnvironment.ENTRY_X, GridEnvironment.ENTRY_Y);
            
            // Notify EnterAgent to add to queue
            // Use ISLOCALNAME as Enter1 is expected on the same platform
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID("Enter1", AID.ISLOCALNAME));
            msg.setContent("JOIN_QUEUE");
            msg.setConversationId("Client-Queue");
            msg.setOntology("Restaurant-Init");
            send(msg);
            System.out.println("[DEBUG] " + getDebugInfo() + " | Sent JOIN_QUEUE to Enter1");
        } else if (currentContainer.equals("EnvContainer")) {
            if (status == AgentStatus.MOVING_TO_TABLE) {
                if (assignedTable != null) {
                    int tableNum = Integer.parseInt(assignedTable.replace("Table", ""));
                    double[] tablePos = getTableCoordinates(tableNum);
                    moveTo(tablePos[0], tablePos[1]);
                }
            } else if (status == AgentStatus.MOVING_TO_CASHIER) {
                moveTo(60.0, 60.0); // Transit point toward cashier
            } else if (status == AgentStatus.MOVING_TO_EXIT) {
                moveTo(90.0, 90.0); // Transit point toward exit
            }
        } else if (currentContainer.equals("TableContainer")) {
            if (assignedTable != null) {
                int tableNum = Integer.parseInt(assignedTable.replace("Table", ""));
                double[] tablePos = getTableCoordinates(tableNum);
                moveTo(tablePos[0], tablePos[1]);
            }
        } else if (currentContainer.equals("CashierContainer")) {
            moveTo(GridEnvironment.CASHIER_X, GridEnvironment.CASHIER_Y);
        } else if (currentContainer.equals("ExitContainer")) {
            moveTo(GridEnvironment.EXIT_X, GridEnvironment.EXIT_Y);
        }
    }
    
    /**
     * Request payment from cashier
     */
    private void requestPayment() {
        // Calculate amount based on menu item
        Menu menuItem = Menu.getByName(chosenItem);
        double amount = (menuItem != null) ? menuItem.getPrice() : 10.0;
        
        System.out.println("[DEBUG] " + getDebugInfo() + " | Requesting payment from cashier for item: " + chosenItem + " ($" + amount + ")");
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(new AID("Cashier1", AID.ISLOCALNAME));
        msg.setContent("PAYMENT_REQUEST:" + assignedTable + ":" + getLocalName() + ":" + amount);
        msg.setConversationId("Payment-Flow");
        msg.setOntology("Restaurant-Service");
        send(msg);
        System.out.println("[DEBUG] " + getDebugInfo() + " | Sent PAYMENT_REQUEST to cashier: " + msg.getContent());
    }
    
    /**
     * Inform table agent that the table is free
     */
    private void freeTable() {
        System.out.println("[DEBUG] " + getDebugInfo() + " | Sending FREE_TABLE message to " + assignedTable);
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        // Use ISLOCALNAME - JADE handles cross-container messaging within the same platform
        msg.addReceiver(new AID(assignedTable, AID.ISLOCALNAME));
        msg.setContent("FREE_TABLE");
        msg.setConversationId("Table-Mgt");
        msg.setOntology("Restaurant-Service");
        send(msg);
        System.out.println("[DEBUG] " + getDebugInfo() + " | FREE_TABLE message sent to " + assignedTable);
    }
}
