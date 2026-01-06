package mas.core;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Queue;

/**
 * QueueManager provides FIFO queue management for agents.
 * Thread-safe queue implementation.
 * Implements Serializable to support JADE agent mobility.
 */
public class QueueManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private Queue<String> queue;
    
    public QueueManager() {
        this.queue = new LinkedList<>();
    }
    
    /**
     * Add an agent to the queue (FIFO)
     * @param agentName Name of the agent to add
     */
    public synchronized void enqueue(String agentName) {
        queue.offer(agentName);
        System.out.println("QueueManager: Added " + agentName + " to queue. Queue size: " + queue.size());
    }
    
    /**
     * Remove and return the first agent from the queue (FIFO)
     * @return Agent name or null if queue is empty
     */
    public synchronized String dequeue() {
        String agent = queue.poll();
        if (agent != null) {
            System.out.println("QueueManager: Removed " + agent + " from queue. Queue size: " + queue.size());
        }
        return agent;
    }
    
    /**
     * Check if queue is empty
     * @return true if empty, false otherwise
     */
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * Get current queue size
     * @return Queue size
     */
    public synchronized int size() {
        return queue.size();
    }
    
    /**
     * Peek at the first element without removing it
     * @return First agent name or null if empty
     */
    public synchronized String peek() {
        return queue.peek();
    }
}

