package mas.agents;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import mas.core.BaseAgent;
import mas.core.AgentStatus;
import mas.core.TickDuration;

import mas.core.Menu;
import mas.core.DebugLogger;

/**
 * CashierAgent manages payments and billing.
 * Processes payments and frees tables after payment.
 */
public class CashierAgent extends BaseAgent {
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructor for CashierAgent with position coordinates
     * @param x X coordinate position
     * @param y Y coordinate position
     */
    public CashierAgent(double x, double y) {
        super(x, y);
    }
    
    /**
     * Default constructor - position set to (0, 0)
     */
    public CashierAgent() {
        super();
    }
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null) {
            initializePosition(args);
        }
        System.out.println("CashierAgent " + getLocalName() + " initialized at position " + getPositionString());
        
        // Initialize status
        status = AgentStatus.CASHIER_IDLE;
        
        // Add behavior to handle payment requests
        addBehaviour(new PaymentHandlerBehaviour());
    }
    
    /**
     * Behavior to handle payment processing
     */
    private class PaymentHandlerBehaviour extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = receive(mt);
            
            if (msg != null && msg.getContent().startsWith("PAYMENT_REQUEST:")) {
                // Parse payment: PAYMENT_REQUEST:tableName:clientName:amount
                String[] parts = msg.getContent().split(":");
                if (parts.length >= 4) {
                    String tableName = parts[1];
                    String clientName = parts[2];
                    double amount = Double.parseDouble(parts[3]);
                    
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Received PAYMENT_REQUEST from client " + clientName + " for $" + amount);
                    setStatus(AgentStatus.CASHIER_PROCESSING_PAYMENT);
                    System.out.println("[DEBUG] " + getDebugInfo() + " | Processing payment of $" + amount + " from " + clientName + " at table " + tableName);
                    DebugLogger.info(getLocalName(), "cashier", getCurrentContainerName(), "Processing payment: $" + amount + " from " + clientName);
                    
                    // Simulate payment processing using TickerBehaviour
                    addBehaviour(new jade.core.behaviours.TickerBehaviour(myAgent, TickDuration.CASHIER_PROCESSING_PAYMENT.getMilliseconds()) {
                        private int ticks = 1;
                        @Override
                        protected void onTick() {
                            if (--ticks <= 0) {
                                setStatus(AgentStatus.CASHIER_IDLE);
                                System.out.println("[DEBUG] " + getDebugInfo() + " | Payment of $" + amount + " processed successfully for " + clientName);
                                System.out.println("[DEBUG] " + getDebugInfo() + " | Sending PAYMENT_COMPLETE confirmation");
                                DebugLogger.success(getLocalName(), "cashier", getCurrentContainerName(), "Payment complete: $" + amount + " from " + clientName);
                                
                                // Notify client that payment is complete
                                ACLMessage reply = msg.createReply();
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent("PAYMENT_COMPLETE");
                                reply.setConversationId("Payment-Workflow");
                                reply.setOntology("Restaurant-Service");
                                send(reply);
                                System.out.println("[DEBUG] " + getDebugInfo() + " | Sent PAYMENT_COMPLETE inform to client " + clientName);
                                
                                // Report profit to BossAgent (estimate cost as 40% of price)
                                double cost = amount * 0.4;
                                double profit = amount - cost;
                                AID bossAID = new AID("Boss1", AID.ISLOCALNAME);
                                ACLMessage profitMsg = new ACLMessage(ACLMessage.INFORM);
                                profitMsg.addReceiver(bossAID);
                                profitMsg.setContent("PROFIT_REPORT:" + amount + ":" + cost + ":" + profit);
                                profitMsg.setConversationId("Profit-Oversight");
                                profitMsg.setOntology("Financial-Report");
                                send(profitMsg);
                                System.out.println("[DEBUG] " + getDebugInfo() + " | Reported profit of $" + profit + " to Boss");
                                
                                // Free the table
                                System.out.println("CashierAgent: Table " + tableName + " will be freed by client");
                                
                                stop();
                            }
                        }
                    });
                }
            } else {
                block();
            }
        }
    }
}
