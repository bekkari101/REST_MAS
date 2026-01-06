package mas.agents;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import mas.core.BaseAgent;
import mas.core.DebugLogger;

/**
 * TableAgent represents a table in the restaurant.
 * Manages availability status (available = true/false).
 */
public class TableAgent extends BaseAgent {
    private static final long serialVersionUID = 1L;
    private boolean available;  // true = available, false = occupied
    private String currentClient = null;
    
    /**
     * Constructor for TableAgent with position coordinates
     * @param x X coordinate position
     * @param y Y coordinate position
     */
    public TableAgent(double x, double y) {
        super(x, y);
        this.available = true; // Initially available
    }
    
    /**
     * Default constructor - position set to (0, 0)
     */
    public TableAgent() {
        super();
        this.available = true;
    }
    
    /**
     * Check if the table is available
     * @return true if available, false if occupied
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * Set the available status
     * @param available true if available, false if occupied
     */
    public void setAvailable(boolean available) {
        this.available = available;
        System.out.println("TableAgent " + getLocalName() + ": Status changed to " + (available ? "AVAILABLE" : "OCCUPIED"));
        String status = available ? "Now AVAILABLE" : "Now OCCUPIED by " + currentClient;
        DebugLogger.Level level = available ? DebugLogger.Level.SUCCESS : DebugLogger.Level.INFO;
        DebugLogger.log(getLocalName(), "table", "TableContainer", level, status);
    }
    
    /**
     * Get current client at table
     * @return Client name or null
     */
    public String getCurrentClient() {
        return currentClient;
    }
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null) {
            initializePosition(args);
        }
        System.out.println("TableAgent " + getLocalName() + " initialized at position " + getPositionString() + " (Available: " + available + ")");
        
        // Register with DFService
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("table-service");
            sd.setName("TableAgent");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) {
            System.err.println("TableAgent: Error registering with DF: " + e.getMessage());
        }
        
        // Add behavior to handle messages
        addBehaviour(new TableMessageHandler());
    }
    
    private String dailyMenu = "NONE";
    
    /**
     * Handle incoming messages
     */
    private class TableMessageHandler extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void action() {
            // Receive any message
            ACLMessage msg = receive();
            
            if (msg != null) {
                String content = msg.getContent();
                int performative = msg.getPerformative();
                
                // 1. Handle availability queries
                if (performative == ACLMessage.QUERY_REF && "CHECK_AVAILABILITY".equals(content)) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setConversationId("Table-Availability");
                    reply.setOntology("Restaurant-Init");
                    String status = available ? "AVAILABLE" : "OCCUPIED";
                    reply.setContent(status);
                    send(reply);
                    System.out.println("[DEBUG] TableAgent " + getLocalName() + " | Responded to availability check: " + status);
                    return;
                }
                
                // 2. Handle occupation requests
                if (content.startsWith("OCCUPY:")) {
                    String clientName = content.substring("OCCUPY:".length());
                    
                    if (!available) {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.FAILURE);
                        reply.setContent("ALREADY_OCCUPIED");
                        reply.setConversationId("Table-Assignment");
                        reply.setOntology("Restaurant-Init");
                        send(reply);
                        System.out.println("TableAgent " + getLocalName() + ": Rejected occupation by " + clientName + " - already occupied by " + currentClient);
                    } else {
                        currentClient = clientName;
                        setAvailable(false);
                        System.out.println("TableAgent " + getLocalName() + ": Occupied by " + clientName);
                        DebugLogger.success(getLocalName(), "table", "TableContainer", clientName + " seated at table");
                        
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(performative == ACLMessage.REQUEST ? ACLMessage.CONFIRM : ACLMessage.INFORM);
                        reply.setContent("OCCUPIED");
                        reply.setConversationId("Table-Assignment");
                        reply.setOntology("Restaurant-Init");
                        send(reply);
                    }
                    return;
                }
                
                // 3. Handle freeing table
                if ("FREE_TABLE".equals(content)) {
                    if (!available) {
                        String freedClient = currentClient;
                        currentClient = null;
                        setAvailable(true);
                        System.out.println("[DEBUG] TableAgent " + getLocalName() + " [Container: " + getCurrentContainerName() + ", Position: " + getPositionString() + "] | Table freed by " + (freedClient != null ? freedClient : "unknown") + ", now AVAILABLE");
                    } else {
                        System.out.println("[WARNING] TableAgent " + getLocalName() + ": Received FREE_TABLE but table was already available");
                    }
                    return;
                }
                
                // 4. Handle client inquiry
                if ("GET_CLIENT".equals(content)) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("CLIENT:" + (currentClient != null ? currentClient : "NONE"));
                    send(reply);
                    return;
                }

                // 5. Handle daily menu update from Boss
                if (content.startsWith("DAILY_MENU:")) {
                    dailyMenu = content.substring("DAILY_MENU:".length());
                    System.out.println("TableAgent " + getLocalName() + ": Updated daily menu to: " + dailyMenu);
                    DebugLogger.info(getLocalName(), "table", "TableContainer", "Menu updated: " + dailyMenu);
                    return;
                }

                // 6. Handle GET_MENU request from Client
                if ("GET_MENU".equals(content)) {
                    if ("NONE".equals(dailyMenu)) {
                        requestMenuFromBoss();
                    }
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("MENU:" + dailyMenu);
                    reply.setConversationId("Menu-Retrieval");
                    reply.setOntology("Restaurant-Init");
                    send(reply);
                    return;
                }
                
                // 7. Unknown message
                System.out.println("[DEBUG] TableAgent " + getLocalName() + ": Received unknown message: " + content + " (Performative: " + ACLMessage.getPerformative(performative) + ")");
            } else {
                block();
            }
        }
    }

    /**
     * Request the daily menu from the BossAgent
     */
    private void requestMenuFromBoss() {
        try {
            AID bossAID = new AID("Boss1", AID.ISLOCALNAME);
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(bossAID);
            msg.setContent("GET_MENU");
            msg.setConversationId("Menu-Request");
            msg.setOntology("Restaurant-Init");
            send(msg);
            System.out.println("TableAgent " + getLocalName() + ": Requested menu from Boss1");
        } catch (Exception e) {
            System.err.println("TableAgent: Error requesting menu from boss: " + e.getMessage());
        }
    }
}
