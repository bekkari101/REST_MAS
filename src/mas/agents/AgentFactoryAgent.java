package mas.agents;

import jade.core.Agent;
import jade.core.ContainerID;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import mas.core.GridEnvironment;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;
import mas.core.BaseAgent;
import mas.core.DebugLogger;
import java.util.HashMap;
import java.util.Map;

/**
 * AgentFactoryAgent (Father Agent) - Creates agents and containers on request.
 * Handles requests to create different types of agents and containers.
 */
public class AgentFactoryAgent extends Agent {
    private static final long serialVersionUID = 1L;
    private Runtime runtime;
    private ContainerController mainContainer;
    private Map<String, ContainerController> containers; // Track created containers
    
    // Configuration: Number of agents to initialize
    private static final int NUM_CLIENTS =5;     // Number of client agents (default: 1)
    private static final int NUM_WAITERS = 1;     // Number of waiter agents (default: 1)
    private static final int NUM_CHEFS = 2;       // Number of chef agents (default: 2)
    private static final int NUM_TABLES = 4;      // Number of table agents (default: 4)
    
    // Message performatives
    private static final String CREATE_AGENT = "CREATE_AGENT";
    private static final String CREATE_CONTAINER = "CREATE_CONTAINER";
    private static final String INITIALIZE_SYSTEM = "INITIALIZE_SYSTEM";
    
    @Override
    protected void setup() {
        runtime = Runtime.instance();
        mainContainer = getContainerController();
        containers = new HashMap<>();
        containers.put("main", mainContainer); // Add main container to tracking
        
        System.out.println("AgentFactoryAgent " + getLocalName() + " initialized");
        System.out.println("Ready to create agents and containers on request");
        DebugLogger.success(getLocalName(), "factory", "Main", "Agent factory ready to create system");
        
        // Add behavior to handle requests
        addBehaviour(new RequestHandlerBehaviour());
        
        // Add behavior to poll for dynamic client creation requests from web GUI
        addBehaviour(new jade.core.behaviours.TickerBehaviour(this, 2000) { // Check every 2 seconds
            private static final long serialVersionUID = 1L;
            
            @Override
            protected void onTick() {
                checkForClientRequests();
            }
        });
        
        // System will be initialized manually from Main, not automatically
    }
    
    /**
     * Behavior to handle creation requests
     */
    private class RequestHandlerBehaviour extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void action() {
            // Wait for any message
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);
            
            if (msg != null) {
                String content = msg.getContent();
                ACLMessage reply = msg.createReply();
                
                try {
                    if (content.startsWith(CREATE_AGENT)) {
                        // Format: CREATE_AGENT:agentType:agentName:x:y:containerName
                        String[] parts = content.split(":");
                        if (parts.length >= 6) {
                            String agentType = parts[1];
                            String agentName = parts[2];
                            double x = Double.parseDouble(parts[3]);
                            double y = Double.parseDouble(parts[4]);
                            String containerName = parts[5];
                            
                            boolean success = createAgent(agentType, agentName, x, y, containerName);
                            
                            if (success) {
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent("Agent " + agentName + " of type " + agentType + " created successfully at (" + x + ", " + y + ")");
                            } else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("Failed to create agent: " + agentType);
                            }
                        } else {
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent("Invalid CREATE_AGENT format. Expected: CREATE_AGENT:agentType:agentName:x:y:containerName");
                        }
                    } else if (content.startsWith(CREATE_CONTAINER)) {
                        // Format: CREATE_CONTAINER:containerName:host:port
                        String[] parts = content.split(":");
                        if (parts.length >= 2) {
                            String containerName = parts[1];
                            String host = parts.length >= 3 ? parts[2] : "localhost";
                            String port = parts.length >= 4 ? parts[3] : "1099";
                            
                            boolean success = createContainer(containerName, host, port);
                            
                            if (success) {
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent("Container " + containerName + " created successfully");
                            } else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("Failed to create container: " + containerName);
                            }
                        } else {
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent("Invalid CREATE_CONTAINER format. Expected: CREATE_CONTAINER:containerName:host:port");
                        }
                    } else if (content.equals(INITIALIZE_SYSTEM)) {
                        // Initialize the restaurant system with containers and default agents
                        boolean success = initializeSystem();
                        
                        if (success) {
                            reply.setPerformative(ACLMessage.INFORM);
                            reply.setContent("System initialized successfully with containers and default agents");
                        } else {
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent("Failed to initialize system");
                        }
                    } else {
                        reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                        reply.setContent("Unknown request type: " + content);
                    }
                } catch (Exception e) {
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("Error processing request: " + e.getMessage());
                    e.printStackTrace();
                }
                
                send(reply);
            } else {
                block();
            }
        }
    }
    
    /**
     * Create an agent of the specified type
     * @param agentType Type of agent (Boss, Waiter, Chef, Table, Client, Exit, Enter)
     * @param agentName Name for the agent
     * @param x X coordinate
     * @param y Y coordinate
     * @param containerName Name of the container (null for main container)
     * @return true if successful, false otherwise
     */
    private boolean createAgent(String agentType, String agentName, double x, double y, String containerName) {
        try {
            ContainerController container = mainContainer;
            
            // If container name is specified, try to find it in tracked containers
            if (containerName != null && !containerName.isEmpty() && !containerName.equals("main")) {
                ContainerController trackedContainer = containers.get(containerName);
                if (trackedContainer != null) {
                    container = trackedContainer;
                } else {
                    System.out.println("Warning: Container " + containerName + " not found, using main container");
                }
            }
            
            AgentController agentController = null;
            Object[] args = new Object[]{x, y};
            
            switch (agentType.toLowerCase()) {
                case "boss":
                    agentController = container.createNewAgent(agentName, BossAgent.class.getName(), args);
                    break;
                case "waiter":
                    agentController = container.createNewAgent(agentName, WaiterAgent.class.getName(), args);
                    break;
                case "chef":
                    agentController = container.createNewAgent(agentName, ChefAgent.class.getName(), args);
                    break;
                case "cashier":
                    agentController = container.createNewAgent(agentName, CashierAgent.class.getName(), args);
                    break;
                case "table":
                    agentController = container.createNewAgent(agentName, TableAgent.class.getName(), args);
                    break;
                case "client":
                    agentController = container.createNewAgent(agentName, ClientAgent.class.getName(), args);
                    break;
                case "exit":
                    agentController = container.createNewAgent(agentName, ExitAgent.class.getName(), args);
                    break;
                case "enter":
                    agentController = container.createNewAgent(agentName, EnterAgent.class.getName(), args);
                    break;
                default:
                    System.err.println("Unknown agent type: " + agentType);
                    return false;
            }
            
            if (agentController != null) {
                agentController.start();
                System.out.println("Created " + agentType + " agent: " + agentName + " at (" + x + ", " + y + ") in container: " + containerName);
                DebugLogger.success(getLocalName(), "factory", "Main", "Created " + agentName + " (" + agentType + ")");
                return true;
            }
            
        } catch (StaleProxyException e) {
            System.err.println("Error creating agent: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        
        return false;
    }
    
    /**
     * Create a new container
     * @param containerName Name of the container
     * @param host Host address
     * @param port Port number
     * @return true if successful, false otherwise
     */
    private boolean createContainer(String containerName, String host, String port) {
        try {
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.CONTAINER_NAME, containerName);
            profile.setParameter(Profile.MAIN_HOST, host);
            profile.setParameter(Profile.MAIN_PORT, port);
            
            ContainerController newContainer = runtime.createAgentContainer(profile);
            
            if (newContainer != null) {
                containers.put(containerName, newContainer); // Track the container
                System.out.println("Created container: " + containerName + " at " + host + ":" + port);
                DebugLogger.info(getLocalName(), "factory", "Main", "Container created: " + containerName);
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error creating container: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        
        return false;
    }
    
    /**
     * Initialize the restaurant system with containers and default agents
     * Creates containers for each agent type and initializes default agents
     * @return true if successful, false otherwise
     */
    private boolean initializeSystem() {
        System.out.println("\n==========================================");
        System.out.println("Initializing Restaurant System...");
        System.out.println("==========================================\n");
        
        try {
            // Step 1: Create containers for each agent type
            System.out.println("[Step 1] Creating containers...");
            
            boolean containersCreated = 
                createContainer("BossContainer", "localhost", "1099") &&
                createContainer("WaiterContainer", "localhost", "1099") &&
                createContainer("ChefContainer", "localhost", "1099") &&
                createContainer("CashierContainer", "localhost", "1099") &&
                createContainer("ClientContainer", "localhost", "1099") &&
                createContainer("TableContainer", "localhost", "1099") &&
                createContainer("EntryContainer", "localhost", "1099") &&
                createContainer("ExitContainer", "localhost", "1099") &&
                createContainer("EnvContainer", "localhost", "1099");
            
            if (!containersCreated) {
                System.err.println("Failed to create some containers");
                return false;
            }
            
            System.out.println("\n[Step 2] Creating default agents...\n");
            
            // Step 2: Create default agents in their containers
            // 1 Boss in BossContainer
            createAgent("boss", "Boss1", GridEnvironment.BOSS_X, GridEnvironment.BOSS_Y, "BossContainer");
            
            // Waiters in WaiterContainer (configurable)
            initializeWaiters(NUM_WAITERS);
            
            // Chefs in ChefContainer (configurable)
            initializeChefs(NUM_CHEFS);
            
            // 1 Cashier in CashierContainer
            createAgent("cashier", "Cashier1", GridEnvironment.CASHIER_X, GridEnvironment.CASHIER_Y, "CashierContainer");
            
            // Tables in TableContainer (configurable)
            initializeTables(NUM_TABLES);
            
            // Clients in ClientContainer (configurable)
            initializeClients(NUM_CLIENTS);
            
            // Enter and Exit agents in their containers
            createAgent("enter", "Enter1", GridEnvironment.ENTRY_X, GridEnvironment.ENTRY_Y, "EntryContainer");
            createAgent("exit", "Exit1", GridEnvironment.EXIT_X, GridEnvironment.EXIT_Y, "ExitContainer");
            
            System.out.println("\n==========================================");
            System.out.println("System Initialization Complete!");
            System.out.println("==========================================\n");
            System.out.println("Containers created:");
            System.out.println("  - BossContainer (1 boss)");
            System.out.println("  - WaiterContainer (" + NUM_WAITERS + " waiter" + (NUM_WAITERS > 1 ? "s" : "") + ")");
            System.out.println("  - ChefContainer (" + NUM_CHEFS + " chef" + (NUM_CHEFS > 1 ? "s" : "") + ")");
            System.out.println("  - CashierContainer (1 cashier)");
            System.out.println("  - TableContainer (" + NUM_TABLES + " table" + (NUM_TABLES > 1 ? "s" : "") + ")");
            System.out.println("  - ClientContainer (" + NUM_CLIENTS + " client" + (NUM_CLIENTS > 1 ? "s" : "") + ")");
            System.out.println("  - EntryContainer (1 enter agent)");
            System.out.println("  - ExitContainer (1 exit agent)");
            System.out.println("==========================================\n");
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Error during system initialization: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Initialize multiple client agents
     * @param numClients Number of clients to create
     */
    private void initializeClients(int numClients) {
        System.out.println("Creating " + numClients + " client agent" + (numClients > 1 ? "s" : "") + "...");
        for (int i = 1; i <= numClients; i++) {
            // Spread clients in a grid pattern to avoid overlap
            double baseX = 5.0;
            double baseY = 5.0;
            double spacing = 2.0;
            int col = (i - 1) % 5; // 5 clients per row
            int row = (i - 1) / 5;
            double x = baseX + (col * spacing);
            double y = baseY + (row * spacing);
            
            createAgent("client", "Client" + i, x, y, "ClientContainer");
        }
    }
    
    /**
     * Initialize multiple waiter agents
     * @param numWaiters Number of waiters to create
     */
    private void initializeWaiters(int numWaiters) {
        System.out.println("Creating " + numWaiters + " waiter agent" + (numWaiters > 1 ? "s" : "") + "...");
        for (int i = 1; i <= numWaiters; i++) {
            double x = 20.0 + ((i - 1) * 3.0); // Space waiters 3 units apart
            double y = 20.0;
            createAgent("waiter", "Waiter" + i, x, y, "WaiterContainer");
        }
    }
    
    /**
     * Initialize multiple chef agents
     * @param numChefs Number of chefs to create
     */
    private void initializeChefs(int numChefs) {
        System.out.println("Creating " + numChefs + " chef agent" + (numChefs > 1 ? "s" : "") + "...");
        for (int i = 1; i <= numChefs; i++) {
            double x = GridEnvironment.CHEF1_X + ((i - 1) * 5.0); // Space chefs 5 units apart
            double y = GridEnvironment.CHEF1_Y;
            createAgent("chef", "Chef" + i, x, y, "ChefContainer");
        }
    }
    
    /**
     * Initialize multiple table agents
     * @param numTables Number of tables to create
     */
    private void initializeTables(int numTables) {
        System.out.println("Creating " + numTables + " table agent" + (numTables > 1 ? "s" : "") + "...");
        for (int i = 1; i <= numTables; i++) {
            double x = GridEnvironment.TABLE_BASE_X + ((i - 1) * GridEnvironment.TABLE_SPACING_X);
            double y = GridEnvironment.TABLE_Y;
            createAgent("table", "Table" + i, x, y, "TableContainer");
        }
    }
    
    /**
     * Check for client creation requests from the web GUI
     * Reads client_requests.txt file and creates new ClientAgents dynamically
     */
    private void checkForClientRequests() {
        String filePath = "client_requests.txt";
        java.io.File requestFile = new java.io.File(filePath);
        
        if (!requestFile.exists()) {
            return; // No requests yet
        }
        
        try {
            // Read all client names from the file
            java.util.List<String> clientNames = new java.util.ArrayList<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(requestFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        clientNames.add(line);
                    }
                }
            }
            
            // Delete the file to clear processed requests
            requestFile.delete();
            
            // Create each requested client
            for (String clientName : clientNames) {
                System.out.println("AgentFactoryAgent: Creating dynamic client: " + clientName);
                
                // Create client at entry position (ClientContainer)
                boolean success = createAgent("client", clientName, 
                    GridEnvironment.ENTRY_X, GridEnvironment.ENTRY_Y, "ClientContainer");
                
                if (success) {
                    System.out.println("AgentFactoryAgent: Successfully created " + clientName);
                } else {
                    System.err.println("AgentFactoryAgent: Failed to create " + clientName);
                }
            }
            
        } catch (java.io.IOException e) {
            System.err.println("AgentFactoryAgent: Error reading client requests: " + e.getMessage());
        }
    }
}

