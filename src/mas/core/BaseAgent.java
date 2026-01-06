package mas.core;

import jade.core.Agent;
import jade.core.ContainerID;
import jade.core.Location;
import java.util.List;
import java.util.ArrayList;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Base agent class for all agents in the restaurant simulation.
 * Provides position (x, y) coordinates for each agent in the environment.
 * Supports tick-based movement with A* pathfinding.
 */
public abstract class BaseAgent extends Agent implements TickSystem.TickListener {
    private static final long serialVersionUID = 1L;
    
    protected double x;  // X coordinate position
    protected double y;  // Y coordinate position
    protected AgentStatus status = null;  // Current agent status
    protected List<int[]> currentPath = new ArrayList<>();  // Current path for movement
    protected int currentPathIndex = 0;  // Current index in path
    protected double targetX = 0, targetY = 0;  // Target position
    protected boolean isMoving = false;  // Whether agent is currently moving
    
    private static final String API_URL = "http://localhost:5001/update";
    private long lastUpdateTick = -1;
    private static final int UPDATE_INTERVAL_TICKS = 2; // Update every 2 ticks to save bandwidth
    
    /**
     * Constructor for BaseAgent with position coordinates
     * @param x X coordinate position
     * @param y Y coordinate position
     */
    public BaseAgent(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    /**
     * Default constructor - position set to (0, 0)
     */
    public BaseAgent() {
        this(0.0, 0.0);
    }
    
    /**
     * Initialize position from arguments passed during agent creation.
     * This method should be called in setup() if arguments are provided.
     * @param args Arguments array [x, y] or null
     */
    protected void initializePosition(Object[] args) {
        if (args != null && args.length >= 2) {
            try {
                if (args[0] instanceof Double) {
                    this.x = (Double) args[0];
                } else if (args[0] instanceof Number) {
                    this.x = ((Number) args[0]).doubleValue();
                } else if (args[0] instanceof String) {
                    this.x = Double.parseDouble((String) args[0]);
                }
                
                if (args[1] instanceof Double) {
                    this.y = (Double) args[1];
                } else if (args[1] instanceof Number) {
                    this.y = ((Number) args[1]).doubleValue();
                } else if (args[1] instanceof String) {
                    this.y = Double.parseDouble((String) args[1]);
                }
                // Send initial state to API immediately so it appears in GUI
                sendStateToAPI();
            } catch (Exception e) {
                System.err.println("Error parsing position arguments: " + e.getMessage());
                this.x = 0.0;
                this.y = 0.0;
                // Even if parsing fails, send the default (0,0) state
                sendStateToAPI();
            }
        } else {
            // If no arguments, position remains (0,0) from default constructor
            // Send initial (0,0) state to API
            sendStateToAPI();
        }
        
        // Register with TickSystem to ensure periodic API updates (prevents stale agent cleanup)
        TickSystem.getInstance().addListener(this);
    }
    
    /**
     * Get the X coordinate
     * @return X coordinate
     */
    public double getX() {
        return x;
    }
    
    /**
     * Get the Y coordinate
     * @return Y coordinate
     */
    public double getY() {
        return y;
    }
    
    /**
     * Set the X coordinate
     * @param x X coordinate
     */
    public void setX(double x) {
        this.x = x;
    }
    
    /**
     * Set the Y coordinate
     * @param y Y coordinate
     */
    public void setY(double y) {
        this.y = y;
    }
    
    /**
     * Set both coordinates
     * @param x X coordinate
     * @param y Y coordinate
     */
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    /**
     * Get the position as a string representation
     * @return String representation of position
     */
    public String getPositionString() {
        return "(" + x + ", " + y + ")";
    }
    
    /**
     * Get current status
     */
    public AgentStatus getStatus() {
        return status;
    }
    
    /**
     * Get current container name
     */
    protected String getCurrentContainerName() {
        try {
            Location loc = here();
            if (loc != null) {
                String name = loc.getName(); // Container short name (e.g. "EntryContainer")
                // Extract just the container name without host/port/platform info
                if (name.contains("@")) {
                    name = name.substring(0, name.indexOf("@"));
                } else if (name.contains("/")) {
                    name = name.substring(0, name.indexOf("/"));
                }
                return name;
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return "Unknown";
    }
    
    /**
     * Get debug info string with name, container, and coordinates
     */
    protected String getDebugInfo() {
        return getLocalName() + " [Container: " + getCurrentContainerName() + ", Position: " + getPositionString() + "]";
    }
    
    /**
     * Set agent status
     */
    public void setStatus(AgentStatus newStatus) {
        if (status != newStatus) {
            AgentStatus oldStatus = status;
            status = newStatus;
            String oldStatusStr = oldStatus != null ? oldStatus.toString() : "NULL";
            System.out.println("[DEBUG] " + getDebugInfo() + " | Status: " + oldStatusStr + " → " + newStatus);
            sendStateToAPI(); // Update API immediately on status change
        }
    }
    
    /**
     * Move agent to target position using A* pathfinding
     * Movement happens step by step using ticks
     */
    public void moveTo(double targetX, double targetY) {
        this.targetX = targetX;
        this.targetY = targetY;
        
        // Clamp coordinates to grid bounds
        if (targetX < 0) targetX = 0;
        if (targetX >= GridEnvironment.GRID_WIDTH) targetX = GridEnvironment.GRID_WIDTH - 1;
        if (targetY < 0) targetY = 0;
        if (targetY >= GridEnvironment.GRID_HEIGHT) targetY = GridEnvironment.GRID_HEIGHT - 1;
        
        int startX = GridEnvironment.toGridX(x);
        int startY = GridEnvironment.toGridY(y);
        int goalX = GridEnvironment.toGridX(targetX);
        int goalY = GridEnvironment.toGridY(targetY);
        
        // Ensure coordinates are within grid
        if (startX < 0) startX = 0;
        if (startX >= GridEnvironment.GRID_WIDTH) startX = GridEnvironment.GRID_WIDTH - 1;
        if (startY < 0) startY = 0;
        if (startY >= GridEnvironment.GRID_HEIGHT) startY = GridEnvironment.GRID_HEIGHT - 1;
        if (goalX < 0) goalX = 0;
        if (goalX >= GridEnvironment.GRID_WIDTH) goalX = GridEnvironment.GRID_WIDTH - 1;
        if (goalY < 0) goalY = 0;
        if (goalY >= GridEnvironment.GRID_HEIGHT) goalY = GridEnvironment.GRID_HEIGHT - 1;
        
        currentPath = AStarPathfinding.findPath(startX, startY, goalX, goalY);
        currentPathIndex = 0;
        isMoving = !currentPath.isEmpty();
        
        if (isMoving) {
            System.out.println("[DEBUG] " + getDebugInfo() + " | Starting movement from (" + startX + "," + startY + ") to (" + goalX + "," + goalY + ") - Path length: " + currentPath.size());
        } else {
            System.out.println("[DEBUG] " + getDebugInfo() + " | No path found from (" + startX + "," + startY + ") to (" + goalX + "," + goalY + ")");
        }
    }
    
    /**
     * Update agent position based on current path (called on each tick)
     */
    public void updateMovement() {
        if (!isMoving || currentPath == null || currentPathIndex >= currentPath.size()) {
            isMoving = false;
            return;
        }
        
        // Move to next position in path
        int[] nextPos = currentPath.get(currentPathIndex);
        x = nextPos[0];
        y = nextPos[1];
        currentPathIndex++;
        
        // Check if reached destination
        if (currentPathIndex >= currentPath.size()) {
            isMoving = false;
            // Clamp to grid
            if (targetX < 0) targetX = 0;
            if (targetX >= GridEnvironment.GRID_WIDTH) targetX = GridEnvironment.GRID_WIDTH - 1;
            if (targetY < 0) targetY = 0;
            if (targetY >= GridEnvironment.GRID_HEIGHT) targetY = GridEnvironment.GRID_HEIGHT - 1;
            x = targetX;
            y = targetY;
            System.out.println("[DEBUG] " + getDebugInfo() + " | Reached destination (" + targetX + "," + targetY + ")");
            onMovementFinished();
        }
    }
    
    /**
     * Hook called when movement is finished. Override in subclasses to handle
     * state transitions or next actions.
     */
    protected void onMovementFinished() {
        // Default no-op
    }
    
    /**
     * Check if agent is currently moving
     */
    public boolean isMoving() {
        return isMoving;
    }
    
    /**
     * Move agent to a different container using JADE mobility
     * This makes the agent visible moving in the JADE GUI
     * @param containerName Name of the target container
     * @return true if successful, false otherwise
     */
    protected boolean moveToContainer(String containerName) {
        try {
            // Get ContainerID of the target container
            ContainerID targetContainerID = getContainerIDByName(containerName);
            
            if (targetContainerID != null) {
                String currentContainer = getCurrentContainerName();
                System.out.println("[DEBUG] " + getDebugInfo() + " | Moving to container: " + containerName);
                doMove(targetContainerID);
                // Non-blocking: we return true immediately. 
                // Any post-move logic should be in afterMove() override.
                return true;
            } else {
                System.err.println("[ERROR] " + getDebugInfo() + " | Could not find container " + containerName);
                return false;
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + getDebugInfo() + " | Error moving to container " + containerName + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Override JADE beforeMove to handle cleanup before container transition.
     */
    @Override
    protected void beforeMove() {
        TickSystem.getInstance().removeListener(this);
        System.out.println("[DEBUG] " + getLocalName() + " | Removed self from tick listeners before moving from " + getCurrentContainerName());
        super.beforeMove();
    }

    /**
     * Override JADE afterMove to handle post-container-transition logic.
     */
    @Override
    protected void afterMove() {
        super.afterMove();
        TickSystem.getInstance().addListener(this);
        System.out.println("[DEBUG] " + getLocalName() + " | Re-added self to tick listeners after moving to " + getCurrentContainerName());
        System.out.println("[DEBUG] " + getLocalName() + " | Successfully moved to " + getCurrentContainerName());
    }

    /**
     * Implementation of TickListener interface
     */
    @Override
    public void onTick(long tick) {
        if (isMoving()) {
            updateMovement();
        }
        
        // Throttled API updates
        if (tick % UPDATE_INTERVAL_TICKS == 0) {
            sendStateToAPI();
        }
    }
    
    /**
     * Send current agent state to the Flask REST API
     */
    protected void sendStateToAPI() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);

                String type = getClass().getSimpleName().replace("Agent", "").toLowerCase();
                String json = String.format(
                    "{\"id\":\"%s\", \"type\":\"%s\", \"x\":%.2f, \"y\":%.2f, \"status\":\"%s\", \"container\":\"%s\"}",
                    getLocalName(), type, x, y, (status != null ? status : "IDLE"), getCurrentContainerName()
                );

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                // We don't necessarily need to check the code here to keep it non-blocking and silent
            } catch (Exception e) {
                // Fail silently to avoid console spam if API is down
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    /**
     * Override JADE takeDown to handle cleanup when agent is destroyed.
     * Deregisters from TickSystem and notifies API to remove from dashboard.
     */
    @Override
    protected void takeDown() {
        // Deregister from tick system
        TickSystem.getInstance().removeListener(this);
        
        // Notify API to remove this agent from dashboard
        new Thread(() -> {
            try {
                String removeUrl = "http://localhost:5001/remove/" + getLocalName();
                java.net.URL url = new java.net.URL(removeUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                // Fail silently
            }
        }).start();
        
        System.out.println("[DEBUG] " + getLocalName() + " | Agent destroyed and removed from dashboard");
        super.takeDown();
    }
    
    /**
     * Get ContainerID by container name
     * Constructs ContainerID using the container name. 
     * For intra-platform migration, the address can usually be omitted or set to null.
     */
    private ContainerID getContainerIDByName(String containerName) {
        try {
            ContainerID targetContainer = new ContainerID();
            targetContainer.setName(containerName);
            // In JADE, if address is null, it assumes the same platform
            targetContainer.setAddress(null);
            return targetContainer;
        } catch (Exception e) {
            System.err.println("[ERROR] " + getLocalName() + " | Error constructing ContainerID for " + containerName + ": " + e.getMessage());
            return null;
        }
    }
}

