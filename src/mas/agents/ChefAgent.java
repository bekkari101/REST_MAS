package mas.agents;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import mas.core.BaseAgent;
import mas.core.QueueManager;
import mas.core.AgentStatus;
import mas.core.TickDuration;
import mas.core.DebugLogger;

/**
 * ChefAgent prepares food orders.
 * Receives orders from waiters and prepares them.
 */
public class ChefAgent extends BaseAgent {
    private static final long serialVersionUID = 1L;
    private QueueManager orderQueue;
    private boolean isPreparing = false;
    private String currentOrder = null;
    
    /**
     * Constructor for ChefAgent with position coordinates
     * @param x X coordinate position
     * @param y Y coordinate position
     */
    public ChefAgent(double x, double y) {
        super(x, y);
        this.orderQueue = new QueueManager();
    }
    
    /**
     * Default constructor - position set to (0, 0)
     */
    public ChefAgent() {
        super();
        this.orderQueue = new QueueManager();
    }
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null) {
            initializePosition(args);
        }
        System.out.println("ChefAgent " + getLocalName() + " initialized at position " + getPositionString());
        
        // Initialize status
        status = AgentStatus.CHEF_IDLE;
        
        // Add behavior to handle order preparation
        addBehaviour(new OrderPreparationBehaviour());
    }
    
    /**
     * Behavior to handle order preparation
     */
    private class OrderPreparationBehaviour extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void action() {
            // Check for new orders
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = receive(mt);
            
            if (msg != null && msg.getContent().startsWith("PREPARE_ORDER:")) {
                // Parse order: PREPARE_ORDER:tableName:clientName:menuItem
                String order = msg.getContent();
                System.out.println("[DEBUG] " + getDebugInfo() + " | Received PREPARE_ORDER request from waiter");
                String[] parts = order.split(":");
                String menuItem = parts.length >= 4 ? parts[3] : "unknown";
                DebugLogger.info(getLocalName(), "chef", getCurrentContainerName(), "New order to prepare: " + menuItem);
                orderQueue.enqueue(order);
                System.out.println("[DEBUG] " + getDebugInfo() + " | Order request added to queue: " + order);
                
                // Start preparing if not busy
                if (!isPreparing) {
                    prepareNextOrder();
                }
            } else if (msg != null && msg.getContent().startsWith("WAITER_ORDER_REQUEST:")) {
                // Waiter has arrived and is requesting the order
                String[] parts = msg.getContent().split(":");
                if (parts.length >= 2) {
                    String orderInfo = parts[1];
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Waiter arrived requesting order: " + orderInfo);
                    // Acknowledge waiter's arrival
                    ACLMessage ack = msg.createReply();
                    ack.setPerformative(ACLMessage.INFORM);
                    ack.setContent("CHEF_ACKNOWLEDGED:" + orderInfo);
                    send(ack);
                }
            } else {
                block();
            }
        }
        
        /**
         * Prepare next order from queue
         */
        private void prepareNextOrder() {
            if (!orderQueue.isEmpty() && !isPreparing) {
                currentOrder = orderQueue.dequeue();
                isPreparing = true;
                
                String[] parts = currentOrder.split(":");
                if (parts.length >= 3) {
                    String tableName = parts[1];
                    String clientName = parts[2];
                    String menuItem = parts.length >= 4 ? parts[3] : "unknown";
                    
                    setStatus(AgentStatus.CHEF_PREPARING_ORDER);
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Starting to prepare " + menuItem + " for " + clientName + " at table " + tableName);
                    DebugLogger.warning(getLocalName(), "chef", getCurrentContainerName(), "Cooking " + menuItem + " for " + clientName);
                    
                    // Simulate food preparation time using a TickerBehaviour
                    addBehaviour(new jade.core.behaviours.TickerBehaviour(myAgent, TickDuration.CHEF_PREPARING_ORDER.getMilliseconds()) {
                        private int ticks = 1;
                        @Override
                        protected void onTick() {
                            if (--ticks <= 0) {
                                setStatus(AgentStatus.CHEF_IDLE);
                                System.out.println("[DEBUG] " + getDebugInfo() + " | Order ready (" + menuItem + ") for " + clientName + " at table " + tableName);
                                System.out.println("[DEBUG] " + getDebugInfo() + " | Informing waiter that food is ready");
                                DebugLogger.success(getLocalName(), "chef", getCurrentContainerName(), menuItem + " is ready! Notifying waiter");
                                
                                // Notify waiter that order is ready
                                AID waiterAID = new AID("Waiter1", AID.ISLOCALNAME);
                                ACLMessage readyMsg = new ACLMessage(ACLMessage.INFORM);
                                readyMsg.addReceiver(waiterAID);
                                readyMsg.setContent("ORDER_READY:" + tableName + ":" + clientName + ":" + menuItem);
                                readyMsg.setConversationId("Order-Step-3");
                                readyMsg.setOntology("Restaurant-Service");
                                send(readyMsg);
                                System.out.println("[DEBUG] " + getDebugInfo() + " | Sent ORDER_READY inform to waiter for table " + tableName + ", client " + clientName + " (" + menuItem + ")");
                                
                                // Clear current order
                                currentOrder = null;
                                isPreparing = false;
                                
                                // Prepare next order if available
                                prepareNextOrder();
                                
                                stop();
                            }
                        }
                    });
                }
            }
        }
    }
}
