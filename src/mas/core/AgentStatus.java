package mas.core;

/**
 * AgentStatus enum for different agent states
 */
public enum AgentStatus {
    // Client statuses
    WAITING_IN_QUEUE("Waiting in queue"),
    MOVING_TO_TABLE("Moving to table"),
    AT_TABLE("At table"),
    ORDERING("Ordering"),
    EATING("Eating"),
    MOVING_TO_CASHIER("Moving to cashier"),
    PAYING("Paying"),
    MOVING_TO_EXIT("Moving to exit"),
    EXITING("Exiting"),
    
    // Waiter statuses
    WAITER_IDLE("Waiter: Idle"),
    WAITER_SERVING_CLIENT("Waiter: Serving client"),
    WAITER_GETTING_ORDER("Waiter: Getting order"),
    WAITER_SERVING_ORDER("Waiter: Serving order"),
    WAITER_MOVING_TO_TABLE("Waiter: Moving to table"),
    WAITER_MOVING_TO_CHEF("Waiter: Moving to chef"),
    
    // Chef statuses
    CHEF_IDLE("Chef: Idle"),
    CHEF_PREPARING_ORDER("Chef: Preparing order"),
    
    // Boss statuses
    BOSS_MONITORING("Boss: Monitoring"),
    BOSS_MANAGING("Boss: Managing"),
    
    // Cashier statuses
    CASHIER_IDLE("Cashier: Idle"),
    CASHIER_PROCESSING_PAYMENT("Cashier: Processing payment");
    
    private final String description;
    
    AgentStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}

