package mas.core;

import java.util.ArrayList;
import java.util.List;

/**
 * TickSystem manages simulation timing.
 * Each tick = 0.05 seconds (50ms)
 */
public class TickSystem {
    private static TickSystem instance;
    private long baseTickInterval = 50; // 0.05 seconds = 50 milliseconds (base)
    private long tickInterval = 50; // Actual tick interval (base / speedFactor)
    private double speedFactor = 1.0; // Speed multiplier (1.0 = normal, higher = faster)
    private long currentTick = 0;
    private List<TickListener> listeners;
    private boolean running = false;
    private Thread tickThread;
    
    private TickSystem() {
        listeners = new ArrayList<>();
        updateTickInterval();
    }
    
    /**
     * Get the current speed factor
     * @return Speed factor (1.0 = normal speed)
     */
    public double getSpeedFactor() {
        return speedFactor;
    }
    
    /**
     * Set the speed factor for simulation
     * @param factor Speed multiplier (1.0 = normal, 2.0 = 2x faster, 0.5 = 2x slower)
     */
    public void setSpeedFactor(double factor) {
        if (factor > 0) {
            this.speedFactor = factor;
            updateTickInterval();
            System.out.println("TickSystem: Speed factor set to " + factor + "x (Tick interval: " + tickInterval + "ms)");
        }
    }
    
    /**
     * Update tick interval based on speed factor
     */
    private void updateTickInterval() {
        tickInterval = (long)(baseTickInterval / speedFactor);
    }
    
    public static TickSystem getInstance() {
        if (instance == null) {
            instance = new TickSystem();
        }
        return instance;
    }
    
    /**
     * Check if tick system is running
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Start the tick system
     */
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        tickThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(tickInterval);
                    currentTick++;
                    notifyListeners();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
        System.out.println("TickSystem: Started (Tick interval: " + tickInterval + "ms)");
    }
    
    /**
     * Stop the tick system
     */
    public void stop() {
        running = false;
        if (tickThread != null) {
            tickThread.interrupt();
        }
        System.out.println("TickSystem: Stopped at tick " + currentTick);
    }
    
    /**
     * Get current tick number
     */
    public long getCurrentTick() {
        return currentTick;
    }
    
    /**
     * Get tick interval in milliseconds
     */
    public long getTickInterval() {
        return tickInterval;
    }
    
    /**
     * Register a tick listener
     */
    public void addListener(TickListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a tick listener
     */
    public void removeListener(TickListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    /**
     * Notify all listeners of new tick
     */
    private void notifyListeners() {
        // Create a copy to avoid ConcurrentModificationException
        List<TickListener> listenersCopy;
        synchronized (listeners) {
            listenersCopy = new ArrayList<>(listeners);
        }
        
        for (TickListener listener : listenersCopy) {
            try {
                listener.onTick(currentTick);
            } catch (Exception e) {
                System.err.println("TickSystem: Error notifying listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Interface for objects that need to be notified on each tick
     */
    public interface TickListener extends java.io.Serializable {
        void onTick(long tick);
    }
}

