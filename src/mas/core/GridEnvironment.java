package mas.core;

/**
 * GridEnvironment represents a 30x30 grid for agent movement.
 * Coordinates range from (0,0) to (29,29)
 */
public class GridEnvironment {
    public static final int GRID_WIDTH = 120;
    public static final int GRID_HEIGHT = 120;
    
    // Centralized Coordinates
    public static final double TABLE_BASE_X = 20.0;
    public static final double TABLE_SPACING_X = 15.0;
    public static final double TABLE_Y = 20.0;
    
    public static final double CASHIER_X = 80.0;
    public static final double CASHIER_Y = 80.0;
    
    public static final double EXIT_X = 100.0;
    public static final double EXIT_Y = 100.0;
    
    public static final double BOSS_X = 110.0;
    public static final double BOSS_Y = 10.0;
    
    public static final double CHEF1_X = 30.0;
    public static final double CHEF1_Y = 30.0;
    public static final double CHEF2_X = 35.0;
    public static final double CHEF2_Y = 30.0;
    
    public static final double ENTRY_X = 0.0;
    public static final double ENTRY_Y = 0.0;
    
    /**
     * Check if coordinates are valid within the grid
     */
    public static boolean isValidPosition(int x, int y) {
        return x >= 0 && x < GRID_WIDTH && y >= 0 && y < GRID_HEIGHT;
    }
    
    /**
     * Check if coordinates are valid within the grid (double version)
     */
    public static boolean isValidPosition(double x, double y) {
        return x >= 0 && x < GRID_WIDTH && y >= 0 && y < GRID_HEIGHT;
    }
    
    /**
     * Convert double coordinates to grid cell coordinates
     */
    public static int toGridX(double x) {
        return (int) Math.floor(x);
    }
    
    /**
     * Convert double coordinates to grid cell coordinates
     */
    public static int toGridY(double y) {
        return (int) Math.floor(y);
    }
}

