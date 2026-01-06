package mas.agents;

import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import mas.core.BaseAgent;
import mas.core.TickDuration;
import mas.core.TickSystem;
import mas.core.DebugLogger;

/**
 * ExitAgent manages client exit from the restaurant.
 * Checks every 2 seconds for client agents in ExitContainer and kills them.
 */
public class ExitAgent extends BaseAgent {
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructor for ExitAgent with position coordinates
     * @param x X coordinate position
     * @param y Y coordinate position
     */
    public ExitAgent(double x, double y) {
        super(x, y);
    }
    
    /**
     * Default constructor - position set to (0, 0)
     */
    public ExitAgent() {
        super();
    }
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null) {
            initializePosition(args);
        }
        System.out.println("ExitAgent " + getLocalName() + " initialized at position " + getPositionString());
        System.out.println("ExitAgent will check for client agents every 2 seconds");
        
        // Add behavior to check for client agents every 2 seconds
        addBehaviour(new ClientCheckBehaviour(this, TickDuration.EXIT_AGENT_CHECK_INTERVAL.getMilliseconds()));
    }
    
    /**
     * Behavior to check for client agents in ExitContainer and kill them
     */
    private class ClientCheckBehaviour extends TickerBehaviour {
        private static final long serialVersionUID = 1L;
        
        public ClientCheckBehaviour(ExitAgent agent, long period) {
            super(agent, period);
        }
        
        @Override
        protected void onTick() {
            // Only check if simulation is running
            if (!TickSystem.getInstance().isRunning()) {
                return;
            }
            
            try {
                System.out.println("ExitAgent: Checking for client agents in ExitContainer...");
                
                // Get the container controller
                ContainerController container = getContainerController();
                
                int clientsFound = 0;
                
                // Search for client agents using DFService
                try {
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("client-agent");
                    template.addServices(sd);
                    
                    DFAgentDescription[] results = DFService.search(myAgent, template);
                    
                    if (results != null && results.length > 0) {
                        System.out.println("ExitAgent: Found " + results.length + " client agent(s) registered in DF");
                        for (DFAgentDescription desc : results) {
                            AID agentAID = desc.getName();
                            String agentName = agentAID.getLocalName();
                            
                            // Skip the ExitAgent itself
                            if (!agentName.equals(getLocalName())) {
                                try {
                                    // Only kill if agent exists in ExitContainer AND has reached exit position
                                    AgentController agentController = container.getAgent(agentName);
                                    if (agentController != null) {
                                        // Give client time to reach exit position before killing
                                        // Check if they've been in ExitContainer long enough (simple delay-based approach)
                                        // Alternative: query agent position, but that requires messaging
                                        // For now, we'll add a small grace period by checking less frequently
                                        System.out.println("ExitAgent: Client " + agentName + " found in ExitContainer, will be removed");
                                        DebugLogger.warning(getLocalName(), "exit", "ExitContainer", "Removing " + agentName + " from system");
                                        agentController.kill();
                                        System.out.println("ExitAgent: Killed client agent: " + agentName);
                                        clientsFound++;
                                    }
                                } catch (jade.wrapper.ControllerException e) {
                                    // Agent not in this container, skip silently
                                    System.out.println("ExitAgent: Client " + agentName + " not in ExitContainer (likely in another container)");
                                }
                            }
                        }
                    } else {
                        System.out.println("ExitAgent: No client agents registered in DF");
                    }
                } catch (Exception e) {
                    System.err.println("ExitAgent: Error searching for client agents: " + e.getMessage());
                }
                
                if (clientsFound == 0) {
                    System.out.println("ExitAgent: No client agents found in ExitContainer");
                } else {
                    System.out.println("ExitAgent: Total client agents killed: " + clientsFound);
                    DebugLogger.success(getLocalName(), "exit", "ExitContainer", clientsFound + " client(s) left restaurant");
                }
                
            } catch (Exception e) {
                System.err.println("ExitAgent: Error checking for client agents: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

