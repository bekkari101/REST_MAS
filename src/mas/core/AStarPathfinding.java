package mas.core;

import java.util.*;

/**
 * A* pathfinding algorithm for agent movement on grid
 */
public class AStarPathfinding {
    
    /**
     * Node class for A* algorithm
     */
    private static class Node {
        int x, y;
        double gCost, hCost, fCost;
        Node parent;
        
        Node(int x, int y) {
            this.x = x;
            this.y = y;
            this.gCost = 0;
            this.hCost = 0;
            this.fCost = 0;
            this.parent = null;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return x == node.x && y == node.y;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }
    
    /**
     * Calculate path from start to goal using A* algorithm
     * @param startX Start X coordinate
     * @param startY Start Y coordinate
     * @param goalX Goal X coordinate
     * @param goalY Goal Y coordinate
     * @return List of path points, or empty list if no path found
     */
    public static List<int[]> findPath(int startX, int startY, int goalX, int goalY) {
        if (!GridEnvironment.isValidPosition(startX, startY) || 
            !GridEnvironment.isValidPosition(goalX, goalY)) {
            return new ArrayList<>();
        }
        
        Node start = new Node(startX, startY);
        Node goal = new Node(goalX, goalY);
        
        Set<Node> openSet = new HashSet<>();
        Set<Node> closedSet = new HashSet<>();
        Map<Node, Node> cameFrom = new HashMap<>();
        
        start.gCost = 0;
        start.hCost = heuristic(start, goal);
        start.fCost = start.gCost + start.hCost;
        
        openSet.add(start);
        
        while (!openSet.isEmpty()) {
            Node current = getLowestF(openSet);
            
            if (current.equals(goal)) {
                // Reconstruct path
                return reconstructPath(cameFrom, current);
            }
            
            openSet.remove(current);
            closedSet.add(current);
            
            // Check neighbors (4-directional movement)
            int[] dx = {0, 1, 0, -1};
            int[] dy = {-1, 0, 1, 0};
            
            for (int i = 0; i < 4; i++) {
                int newX = current.x + dx[i];
                int newY = current.y + dy[i];
                
                if (!GridEnvironment.isValidPosition(newX, newY)) {
                    continue;
                }
                
                Node neighbor = new Node(newX, newY);
                
                if (closedSet.contains(neighbor)) {
                    continue;
                }
                
                double tentativeGCost = current.gCost + 1; // Each step costs 1
                
                if (!openSet.contains(neighbor)) {
                    openSet.add(neighbor);
                } else if (tentativeGCost >= neighbor.gCost) {
                    continue;
                }
                
                cameFrom.put(neighbor, current);
                neighbor.gCost = tentativeGCost;
                neighbor.hCost = heuristic(neighbor, goal);
                neighbor.fCost = neighbor.gCost + neighbor.hCost;
            }
        }
        
        // No path found
        return new ArrayList<>();
    }
    
    /**
     * Get node with lowest F cost from open set
     */
    private static Node getLowestF(Set<Node> openSet) {
        Node lowest = null;
        for (Node node : openSet) {
            if (lowest == null || node.fCost < lowest.fCost) {
                lowest = node;
            }
        }
        return lowest;
    }
    
    /**
     * Heuristic function (Manhattan distance)
     */
    private static double heuristic(Node a, Node b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }
    
    /**
     * Reconstruct path from goal to start
     */
    private static List<int[]> reconstructPath(Map<Node, Node> cameFrom, Node current) {
        List<int[]> path = new ArrayList<>();
        path.add(new int[]{current.x, current.y});
        
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(0, new int[]{current.x, current.y}); // Add to beginning
        }
        
        return path;
    }
}

