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
import mas.core.QueueManager;
import mas.core.TickDuration;
import mas.core.DebugLogger;

/**
 * EnterAgent manages client entry into the restaurant.
 * Maintains FIFO queue and assigns clients to available tables.
 */
public class EnterAgent extends BaseAgent {
    private static final long serialVersionUID = 1L;
    private QueueManager clientQueue;
    private long lastAssignmentTime = 0; // Track last table assignment time
    private static final long ASSIGNMENT_COOLDOWN_MS = TickDuration.ASSIGNMENT_COOLDOWN.getMilliseconds();
    
    /**
     * Constructor for EnterAgent with position coordinates
     * @param x X coordinate position
     * @param y Y coordinate position
     */
    public EnterAgent(double x, double y) {
        super(x, y);
        this.clientQueue = new QueueManager();
    }
    
    /**
     * Default constructor - position set to (0, 0)
     */
    public EnterAgent() {
        super();
        this.clientQueue = new QueueManager();
    }
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null) {
            initializePosition(args);
        }
        System.out.println("EnterAgent " + getLocalName() + " initialized at position " + getPositionString());
        
        // Register with DFService
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("enter-service");
            sd.setName("EnterAgent");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) {
            System.err.println("EnterAgent: Error registering with DF: " + e.getMessage());
        }
        
        // Add behavior to handle client registration
        addBehaviour(new ClientRegistrationBehaviour());
        
        // Add behavior to process queue and assign tables
        addBehaviour(new TableAssignmentBehaviour(this, TickDuration.ENTER_AGENT_CHECK_INTERVAL.getMilliseconds()));
    }
    
    /**
     * Behavior to handle client registration in queue
     */
    private class ClientRegistrationBehaviour extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void action() {
            // Only receive JOIN_QUEUE messages, ignore table responses
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchContent("JOIN_QUEUE")
            );
            ACLMessage msg = receive(mt);
            if (msg != null) {
                String clientName = msg.getSender().getLocalName();
                System.out.println("[DEBUG] EnterAgent: Received JOIN_QUEUE from " + clientName);
                clientQueue.enqueue(clientName);
                System.out.println("[DEBUG] EnterAgent: Client " + clientName + " added to queue. Queue size: " + clientQueue.size());
                DebugLogger.info(getLocalName(), "enter", "EntryContainer", clientName + " joined queue (" + clientQueue.size() + " waiting)");
            } else {
                block();
            }
        }
    }
    
    /**
     * Behavior to check for available tables and assign clients using a non-blocking state machine
     */
    private class TableAssignmentBehaviour extends TickerBehaviour {
        private static final long serialVersionUID = 1L;
        
        private enum AssignmentState { IDLE, QUERYING, WAITING_FOR_QUERY_RESPONSES, OCCUPYING, WAITING_FOR_OCCUPY_CONFIRMATION }
        private AssignmentState state = AssignmentState.IDLE;
        private long stateStartTime = 0;
        private String currentCandidateTable = null;
        private String currentClient = null;
        private java.util.Map<String, String> tableResponses = new java.util.HashMap<>();
        private int expectedResponseCount = 0;

        public TableAssignmentBehaviour(EnterAgent agent, long period) {
            super(agent, period);
        }
        
        @Override
        protected void onTick() {
            if (!mas.core.TickSystem.getInstance().isRunning()) return;

            long currentTime = System.currentTimeMillis();
            
            switch (state) {
                case IDLE:
                    if (!clientQueue.isEmpty() && (currentTime - lastAssignmentTime >= ASSIGNMENT_COOLDOWN_MS)) {
                        startQuerying();
                    }
                    break;
                    
                case QUERYING:
                    // This state is just a transition
                    break;
                    
                case WAITING_FOR_QUERY_RESPONSES:
                    collectQueryResponses(currentTime);
                    break;
                    
                case OCCUPYING:
                    // This state is just a transition
                    break;
                    
                case WAITING_FOR_OCCUPY_CONFIRMATION:
                    checkOccupyConfirmation(currentTime);
                    break;
            }
        }
        
        private void startQuerying() {
            try {
                System.out.println("[DEBUG] EnterAgent: Starting table query. Queue size: " + clientQueue.size());
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("table-service");
                template.addServices(sd);
                
                DFAgentDescription[] results = DFService.search(myAgent, template);
                if (results == null || results.length == 0) {
                    lastAssignmentTime = System.currentTimeMillis(); // Cooldown even on failure to avoid spamming
                    return;
                }
                
                tableResponses.clear();
                expectedResponseCount = results.length;
                
                for (DFAgentDescription desc : results) {
                    AID tableAID = desc.getName();
                    ACLMessage query = new ACLMessage(ACLMessage.QUERY_REF);
                    query.addReceiver(tableAID);
                    query.setContent("CHECK_AVAILABILITY");
                    query.setConversationId("CHECK_AVAILABILITY_" + System.currentTimeMillis());
                    send(query);
                }
                
                state = AssignmentState.WAITING_FOR_QUERY_RESPONSES;
                stateStartTime = System.currentTimeMillis();
            } catch (Exception e) {
                System.err.println("EnterAgent: Error starting table query: " + e.getMessage());
                state = AssignmentState.IDLE;
            }
        }
        
        private void collectQueryResponses(long currentTime) {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            // We don't filter by conversation ID strictly here to avoid missing messages, 
            // but we could if we stored the ID.
            
            ACLMessage reply = myAgent.receive(mt);
            while (reply != null) {
                if ("AVAILABLE".equals(reply.getContent()) || "OCCUPIED".equals(reply.getContent())) {
                    tableResponses.put(reply.getSender().getLocalName(), reply.getContent());
                }
                reply = myAgent.receive(mt);
            }
            
            if (tableResponses.size() >= expectedResponseCount || (currentTime - stateStartTime > TickDuration.TABLE_QUERY_TIMEOUT.getMilliseconds())) {
                // Done waiting, find an available table
                for (java.util.Map.Entry<String, String> entry : tableResponses.entrySet()) {
                    if ("AVAILABLE".equals(entry.getValue())) {
                        currentCandidateTable = entry.getKey();
                        currentClient = clientQueue.dequeue();
                        DebugLogger.success(getLocalName(), "enter", "EntryContainer", "Assigning " + currentClient + " to " + currentCandidateTable);
                        if (currentClient != null) {
                            sendOccupyRequest();
                            return;
                        }
                    }
                }
                // No table found or no client
                state = AssignmentState.IDLE;
                lastAssignmentTime = currentTime;
            }
        }
        
        private void sendOccupyRequest() {
            try {
                AID tableAID = new AID(currentCandidateTable, AID.ISLOCALNAME);
                ACLMessage occupyMsg = new ACLMessage(ACLMessage.REQUEST);
                occupyMsg.addReceiver(tableAID);
                occupyMsg.setContent("OCCUPY:" + currentClient);
                occupyMsg.setConversationId("OCCUPY_" + System.currentTimeMillis());
                send(occupyMsg);
                
                state = AssignmentState.WAITING_FOR_OCCUPY_CONFIRMATION;
                stateStartTime = System.currentTimeMillis();
            } catch (Exception e) {
                System.err.println("EnterAgent: Error sending occupy request: " + e.getMessage());
                clientQueue.enqueue(currentClient);
                state = AssignmentState.IDLE;
            }
        }
        
        private void checkOccupyConfirmation(long currentTime) {
            MessageTemplate mt = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
                MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchContent("OCCUPIED")
                )
            );
            
            ACLMessage reply = myAgent.receive(mt);
            if (reply != null) {
                if (reply.getSender().getLocalName().equals(currentCandidateTable)) {
                    System.out.println("EnterAgent: Table " + currentCandidateTable + " occupied by " + currentClient);
                    assignTableToClient(currentClient, currentCandidateTable);
                    lastAssignmentTime = currentTime;
                    state = AssignmentState.IDLE;
                }
            } else if (currentTime - stateStartTime > TickDuration.TABLE_QUERY_TIMEOUT.getMilliseconds()) {
                System.err.println("EnterAgent: Timeout waiting for occupy confirmation from " + currentCandidateTable);
                clientQueue.enqueue(currentClient);
                state = AssignmentState.IDLE;
                lastAssignmentTime = currentTime;
            }
        }
        
        private void assignTableToClient(String clientName, String tableName) {
            try {
                // Use ISGUID to enable cross-container messaging
                // EnterAgent is in EntryContainer, ClientAgent is also in EntryContainer
                // but ISGUID is more reliable for inter-agent communication
                AID clientAID = new AID(clientName, AID.ISLOCALNAME);
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(clientAID);
                msg.setContent("MOVE_TO_TABLE:" + tableName);
                send(msg);
                System.out.println("[DEBUG] EnterAgent: Assigned " + tableName + " to " + clientName + " (MOVE_TO_TABLE sent)");
            } catch (Exception e) {
                System.err.println("EnterAgent: Error assigning table: " + e.getMessage());
            }
        }
    }
}
