package mas.core;

/**
 * TickDuration enum defines the duration (in ticks) for various activities.
 * Each tick = 0.05 seconds (50ms) at base speed (speed factor 1.0)
 * Makes it easy to control timing throughout the system.
 * All durations respect the speed factor from TickSystem.
 */
public enum TickDuration {
    // Client activities
    CLIENT_EATING(60),              // 3 seconds (60 ticks * 0.05s = 3s)
    CLIENT_ORDERING(20),            // 1 second
    CLIENT_WAITING_IN_QUEUE(10),    // 0.5 seconds
    CLIENT_MOVEMENT_BASE(10),       // Base movement time (0.5 seconds)
    
    // Waiter activities
    WAITER_TAKING_ORDER(20),        // 1 second
    WAITER_DELIVERING_FOOD(10),     // 0.5 seconds
    WAITER_MOVEMENT_BASE(10),       // Base movement time
    
    // Chef activities
    CHEF_PREPARING_ORDER(60),       // 3 seconds
    CHEF_BETWEEN_ORDERS(5),         // 0.25 seconds between orders
    
    // Cashier activities
    CASHIER_PROCESSING_PAYMENT(20), // 1 second
    
    // System activities
    ENTER_AGENT_CHECK_INTERVAL(40), // 2 seconds (checking for available tables)
    EXIT_AGENT_CHECK_INTERVAL(40), //  2    seconds (checking for clients to remove)
    
    // Message waiting times
    ENTER_AGENT_TABLE_QUERY_WAIT(10),  // 0.5 seconds (waiting for table responses)
    TABLE_AGENT_RESPONSE_DELAY(1),     // 0.05 seconds (delay before sending response)
    
    // Container transit and initialization
    TRANSIT_TIME(10),                // 0.5 seconds for moving between containers
    JADE_TRANSIT_DELAY(2),          // 0.1 seconds (100ms) for JADE doMove
    AGENT_INIT_DELAY(6),            // 0.3 seconds post-transit init
    
    // Movement and timeouts
    MOVEMENT_CHECK_INTERVAL(1),     // 0.05 seconds (1 tick)
    MOVEMENT_TIMEOUT(100),          // 5 seconds
    TABLE_QUERY_TIMEOUT(40),        // 2 seconds
    
    // System logic
    ASSIGNMENT_COOLDOWN(10),        // 0.5 seconds between assignments
    BEHAVIOUR_TICK(1),              // 0.05 seconds (1 tick)
    
    // Default wait times
    SHORT_WAIT(5),                  // 0.25 seconds
    MEDIUM_WAIT(10),                // 0.5 seconds
    LONG_WAIT(20);                  // 1 second
    
    private final int ticks;
    
    /**
     * Constructor
     * @param ticks Number of ticks for this duration
     */
    TickDuration(int ticks) {
        this.ticks = ticks;
    }
    
    /**
     * Get the duration in ticks
     * @return Number of ticks
     */
    public int getTicks() {
        return ticks;
    }
    
    /**
     * Get the duration in milliseconds (base, without speed factor)
     * @return Duration in milliseconds (ticks * 50ms)
     */
    public long getMilliseconds() {
        return ticks * 50L;
    }
    
    /**
     * Get the duration in milliseconds (with speed factor applied)
     * @return Duration in milliseconds (ticks * 50ms / speedFactor)
     */
    public long getMillisecondsWithSpeed() {
        double speedFactor = TickSystem.getInstance().getSpeedFactor();
        return (long)(ticks * 50L / speedFactor);
    }
    
    /**
     * Get the duration in seconds (base, without speed factor)
     * @return Duration in seconds
     */
    public double getSeconds() {
        return ticks * 0.05;
    }
    
    /**
     * Get the duration in seconds (with speed factor applied)
     * @return Duration in seconds (ticks * 0.05 / speedFactor)
     */
    public double getSecondsWithSpeed() {
        double speedFactor = TickSystem.getInstance().getSpeedFactor();
        return ticks * 0.05 / speedFactor;
    }
    
    /**
     * Sleep for this duration (respects speed factor)
     * @throws InterruptedException if interrupted
     */
    public void sleep() throws InterruptedException {
        Thread.sleep(getMillisecondsWithSpeed());
    }
    
    /**
     * Sleep for this duration in ticks (using tick system)
     * @param currentTick Current tick number
     * @return Target tick number when duration completes
     */
    public long getTargetTick(long currentTick) {
        return currentTick + ticks;
    }
}

