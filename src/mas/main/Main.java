package mas.main;

import jade.core.AID;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import jade.lang.acl.ACLMessage;
import mas.agents.AgentFactoryAgent;
import mas.agents.HelperAgent;
import mas.core.TickSystem;
import java.util.Scanner;
import java.io.File;
import java.io.IOException;

/**
 * Main class to initialize the JADE platform and start the AgentFactoryAgent (Father Agent).
 * Includes interactive console menu for managing agents and containers.
 */
public class Main {
    private static ContainerController mainContainer;
    private static Scanner scanner;
    private static boolean simulationRunning = false;
    private static TickSystem tickSystem;
    private static Process flaskProcess;
    private static final String API_SCRIPT = "api/app.py";
    private static boolean systemInitialized = false;
    private static final String CONTROL_URL = "http://localhost:5001/control";
    
    public static void main(String[] args) {
        scanner = new Scanner(System.in);
        
        // Get the JADE runtime instance
        Runtime runtime = Runtime.instance();
        
        // Create a profile for the main container
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.MAIN_PORT, "1099");
        profile.setParameter(Profile.GUI, "true"); // Enable JADE GUI
        profile.setParameter("gui-expanded", "true"); // Expand all containers in GUI
        
        // Create the main container
        mainContainer = runtime.createMainContainer(profile);
        
        // Start Flask API
        startFlaskAPI();
        
        try {
            // Create and start the AgentFactoryAgent (Father Agent)
            AgentController factoryAgent = mainContainer.createNewAgent(
                "AgentFactory", 
                AgentFactoryAgent.class.getName(), 
                null
            );
            
            factoryAgent.start();
            
            System.out.println("==========================================");
            System.out.println("JADE Platform Started Successfully!");
            System.out.println("AgentFactoryAgent (Father Agent) is running");
            System.out.println("==========================================");
            System.out.println();
            
            // Wait a bit for AgentFactoryAgent to be ready
            Thread.sleep(2000);
            
            // Start Web Control Polling Thread
            new Thread(new WebControlThread()).start();
            
            System.out.println("WAITING FOR WEB GUI CONTROL...");
            System.out.println("Open http://localhost:5001 in your browser to start the simulation.");
            System.out.println("==========================================\n");
            
            // Initialize tick system but don't start it yet
            tickSystem = TickSystem.getInstance();
            
            // Start interactive menu
            showMenu();
            
        } catch (Exception e) {
            System.err.println("Error starting system: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
            if (tickSystem != null && simulationRunning) {
                tickSystem.stop();
            }
            stopFlaskAPI();
        }
    }
    
    /**
     * Start the Flask API Python process
     */
    private static void startFlaskAPI() {
        System.out.println("Starting Flask API...");
        String[] pythonCommands = {"python", "py", "python3"};
        boolean started = false;
        
        for (String cmd : pythonCommands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, API_SCRIPT);
                pb.directory(new File(System.getProperty("user.dir")));
                pb.inheritIO();
                flaskProcess = pb.start();
                System.out.println("Flask API Process started successfully using: " + cmd);
                started = true;
                break;
            } catch (IOException e) {
                // Try next command
            }
        }
        
        if (started) {
            // Register shutdown hook
            java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown detected. Cleaning up...");
                
                // Clear API data
                try {
                    java.net.URL url = new java.net.URL("http://localhost:5001/clear");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(1000);
                    conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception e) {}

                if (flaskProcess != null && flaskProcess.isAlive()) {
                    flaskProcess.destroy();
                }
            }));
            
            // Sync grid dimensions to API (wait a bit for Flask to be ready)
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    java.net.URL url = new java.net.URL(CONTROL_URL);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    
                    String json = String.format("{\"grid_width\": %d, \"grid_height\": %d}", 
                        mas.core.GridEnvironment.GRID_WIDTH, mas.core.GridEnvironment.GRID_HEIGHT);
                    
                    try (java.io.OutputStream os = conn.getOutputStream()) {
                        os.write(json.getBytes());
                    }
                    int code = conn.getResponseCode();
                    System.out.println("Synced grid dimensions to API (" + mas.core.GridEnvironment.GRID_WIDTH + "x" + mas.core.GridEnvironment.GRID_HEIGHT + "). Code: " + code);
                } catch (Exception e) {
                    // Fail silently
                }
            }).start();
        } else {
            System.err.println("CRITICAL ERROR: Could not find Python (tried python, py, python3).");
            System.err.println("Please ensure Python is installed and in your PATH.");
        }
    }
    
    /**
     * Stop the Flask API Python process
     */
    private static void stopFlaskAPI() {
        if (flaskProcess != null && flaskProcess.isAlive()) {
            System.out.println("Stopping Flask API...");
            flaskProcess.destroy();
            try {
                flaskProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Flask API stopped.");
        }
    }
    
    /**
     * Send message to AgentFactoryAgent using a simple helper agent
     */
    private static void sendMessageToFactory(String content) {
        try {
            // Create a temporary helper agent to send messages
            String helperName = "Helper" + System.currentTimeMillis();
            AgentController helperAgent = mainContainer.createNewAgent(
                helperName, 
                HelperAgent.class.getName(), 
                new Object[]{content}
            );
            helperAgent.start();
            
            // Wait a bit for message to be sent
            Thread.sleep(1500);
            
            // Clean up helper agent
            helperAgent.kill();
            
        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Display interactive menu
     */
    private static void showMenu() {
        boolean running = true;
        
        while (running) {
            System.out.println("\n==========================================");
            System.out.println("RESTAURANT SIMULATION CONTROL MENU");
            System.out.println("==========================================");
            if (!simulationRunning) {
                System.out.println("START - Start the simulation");
            } else {
                System.out.println("STOP - Stop the simulation");
            }
            System.out.println("1. Initialize System (Create containers & default agents)");
            System.out.println("2. Create Container");
            System.out.println("3. Create Agent");
            System.out.println("4. System Status");
            System.out.println("5. Exit");
            System.out.println("==========================================");
            System.out.print("Enter command: ");
            
            try {
                String input = scanner.nextLine().trim().toLowerCase();
                if (input.isEmpty()) {
                    continue;
                }
                
                // Handle text commands
                if (input.equals("start")) {
                    handleStartSimulation();
                } else if (input.equals("stop")) {
                    handleStopSimulation();
                } else {
                    // Handle numeric options
                    try {
                        int choice = Integer.parseInt(input);
                        
                        switch (choice) {
                            case 1:
                                handleInitializeSystem();
                                break;
                            case 2:
                                handleCreateContainer();
                                break;
                            case 3:
                                handleCreateAgent();
                                break;
                            case 4:
                                showSystemStatus();
                                break;
                            case 5:
                                running = false;
                                if (simulationRunning) {
                                    handleStopSimulation();
                                }
                                System.out.println("Exiting...");
                                break;
                            default:
                                System.out.println("Invalid option. Please select 1-5 or type 'start'/'stop'.");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input. Please enter a number (1-5) or type 'start'/'stop'.");
                    }
                }
            } catch (Exception e) {
                // System.out.println("Error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Polling thread to synchronize with Web Dashboard controls
     */
    private static class WebControlThread implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(500); // Poll every 500ms
                    
                    java.net.URL url = new java.net.URL(CONTROL_URL);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(1000);
                    
                    if (conn.getResponseCode() == 200) {
                        java.util.Scanner s = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
                        String result = s.hasNext() ? s.next() : "";
                        
                        // Debug: print only if state changes or every 10th poll to avoid spam
                        if (result.contains("\"initialized\": true") || result.contains("\"initialized\":true")) {
                            // System.out.println("[DEBUG] Received control state: " + result);
                        }
                        
                        // Simple manual JSON parsing (handling potential spaces from Python/Flask)
                        boolean webInitialized = result.contains("\"initialized\":true") || result.contains("\"initialized\": true");
                        boolean webRunning = result.contains("\"running\":true") || result.contains("\"running\": true");
                        double webSpeed = 1.0;
                        
                        // Extract speed value
                        try {
                            String speedSearch = "\"speed\":";
                            int speedIdx = result.indexOf(speedSearch);
                            if (speedIdx != -1) {
                                int endIdx = result.indexOf(",", speedIdx);
                                if (endIdx == -1) endIdx = result.indexOf("}", speedIdx);
                                String speedStr = result.substring(speedIdx + speedSearch.length(), endIdx).trim();
                                webSpeed = Double.parseDouble(speedStr);
                            }
                        } catch (Exception e) {}

                        // Logic: Initialize system if requested via web and not yet done
                        if (webInitialized && !systemInitialized) {
                            System.out.println("[WEB CONTROL] Initializing system...");
                            handleInitializeSystem();
                            systemInitialized = true;
                        }

                        // Logic: Start/Stop simulation
                        if (systemInitialized) {
                            if (webRunning && !simulationRunning) {
                                System.out.println("[WEB CONTROL] Starting simulation...");
                                handleStartSimulationFromWeb(webSpeed);
                            } else if (!webRunning && simulationRunning) {
                                System.out.println("[WEB CONTROL] Pausing simulation...");
                                handleStopSimulation();
                            }
                            
                            // Update speed if changed
                            if (simulationRunning && tickSystem != null && Math.abs(tickSystem.getSpeedFactor() - webSpeed) > 0.01) {
                                tickSystem.setSpeedFactor(webSpeed);
                            }
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    // Fail silently, API might be starting up
                }
            }
        }
    }
    
    private static void handleStartSimulationFromWeb(double speed) {
        if (tickSystem != null) {
            tickSystem.setSpeedFactor(speed);
            tickSystem.start();
        }
        simulationRunning = true;
    }
    
    /**
     * Handle start simulation
     */
    private static void handleStartSimulation() {
        if (simulationRunning) {
            System.out.println("Simulation is already running!");
            return;
        }
        
        System.out.println("\n==========================================");
        System.out.println("STARTING SIMULATION...");
        System.out.println("==========================================");
        
        // Ask for speed factor
        System.out.print("Enter speed factor (1.0 = normal, 2.0 = 2x faster, 0.5 = 2x slower) [default: 1.0]: ");
        String speedInput = scanner.nextLine().trim();
        double speedFactor = 1.0;
        if (!speedInput.isEmpty()) {
            try {
                speedFactor = Double.parseDouble(speedInput);
                if (speedFactor <= 0) {
                    System.out.println("Invalid speed factor, using default 1.0");
                    speedFactor = 1.0;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input, using default speed factor 1.0");
                speedFactor = 1.0;
            }
        }
        
        // Start tick system
        if (tickSystem != null) {
            tickSystem.setSpeedFactor(speedFactor);
            tickSystem.start();
            System.out.println("TickSystem started with speed factor: " + speedFactor + "x");
        }
        
        simulationRunning = true;
        System.out.println("Simulation is now RUNNING!");
        System.out.println("Agents will start moving and interacting.");
        System.out.println("Type 'stop' to pause the simulation.");
        System.out.println("==========================================\n");
    }
    
    /**
     * Handle stop simulation
     */
    private static void handleStopSimulation() {
        if (!simulationRunning) {
            System.out.println("Simulation is not running!");
            return;
        }
        
        System.out.println("\n==========================================");
        System.out.println("STOPPING SIMULATION...");
        System.out.println("==========================================");
        
        // Stop tick system
        if (tickSystem != null) {
            tickSystem.stop();
            System.out.println("TickSystem stopped");
        }
        
        simulationRunning = false;
        System.out.println("Simulation is now PAUSED.");
        System.out.println("Type 'start' to resume the simulation.");
        System.out.println("==========================================\n");
    }
    
    /**
     * Handle initialize system option
     */
    private static void handleInitializeSystem() {
        System.out.println("\nSending INITIALIZE_SYSTEM request...");
        sendMessageToFactory("INITIALIZE_SYSTEM");
        System.out.println("Request sent. Check console output for results.");
    }
    
    /**
     * Handle create container option
     */
    private static void handleCreateContainer() {
        System.out.println("\n--- Create Container ---");
        System.out.print("Container name: ");
        String name = scanner.nextLine().trim();
        
        if (name.isEmpty()) {
            System.out.println("Container name cannot be empty!");
            return;
        }
        
        System.out.print("Host (default: localhost, press Enter to use default): ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) {
            host = "localhost";
        }
        
        System.out.print("Port (default: 1099, press Enter to use default): ");
        String port = scanner.nextLine().trim();
        if (port.isEmpty()) {
            port = "1099";
        }
        
        String message = "CREATE_CONTAINER:" + name + ":" + host + ":" + port;
        sendMessageToFactory(message);
        System.out.println("Container creation request sent: " + name);
    }
    
    /**
     * Handle create agent option
     */
    private static void handleCreateAgent() {
        System.out.println("\n--- Create Agent ---");
        System.out.println("Agent types: boss, waiter, chef, cashier, client, table, enter, exit");
        System.out.print("Agent type: ");
        String type = scanner.nextLine().trim().toLowerCase();
        
        if (!isValidAgentType(type)) {
            System.out.println("Invalid agent type!");
            return;
        }
        
        System.out.print("Agent name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("Agent name cannot be empty!");
            return;
        }
        
        System.out.print("X position (default: 0.0, press Enter to use default): ");
        String xStr = scanner.nextLine().trim();
        double x = 0.0;
        if (!xStr.isEmpty()) {
            try {
                x = Double.parseDouble(xStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid X position, using 0.0");
            }
        }
        
        System.out.print("Y position (default: 0.0, press Enter to use default): ");
        String yStr = scanner.nextLine().trim();
        double y = 0.0;
        if (!yStr.isEmpty()) {
            try {
                y = Double.parseDouble(yStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid Y position, using 0.0");
            }
        }
        
        System.out.print("Container name (default: main, press Enter to use default): ");
        String container = scanner.nextLine().trim();
        if (container.isEmpty()) {
            container = "main";
        }
        
        String message = "CREATE_AGENT:" + type + ":" + name + ":" + x + ":" + y + ":" + container;
        sendMessageToFactory(message);
        System.out.println("Agent creation request sent: " + name + " (" + type + ")");
    }
    
    /**
     * Check if agent type is valid
     */
    private static boolean isValidAgentType(String type) {
        return type.equals("boss") || type.equals("waiter") || type.equals("chef") || 
               type.equals("cashier") || type.equals("client") || type.equals("table") ||
               type.equals("enter") || type.equals("exit");
    }
    
    /**
     * Show system status
     */
    private static void showSystemStatus() {
        System.out.println("\n--- System Status ---");
        System.out.println("JADE Platform: Running");
        System.out.println("Main Container: Active");
        System.out.println("AgentFactoryAgent: Running");
        System.out.println("Simulation Status: " + (simulationRunning ? "RUNNING" : "PAUSED"));
        System.out.println("TickSystem: " + (simulationRunning ? "Active" : "Stopped"));
        if (tickSystem != null) {
            System.out.println("Speed Factor: " + tickSystem.getSpeedFactor() + "x");
            System.out.println("Tick Interval: " + tickSystem.getTickInterval() + "ms");
        }
        System.out.println("\nNote: Check JADE GUI for detailed agent and container information.");
        System.out.println("Use the menu options to create additional agents and containers.");
        if (!simulationRunning) {
            System.out.println("Type 'start' to begin the simulation.");
        }
    }
    
}
