package mas.core;

/**
 * Menu enum defines available food items and their prices.
 */
public enum Menu {
    PIZZA("Pizza", 12.50, 5.00),
    SALAD("Salad", 8.00, 3.00),
    BURGER("Burger", 10.50, 4.50),
    PASTA("Pasta", 11.00, 4.00),
    STEAK("Steak", 25.00, 12.00),
    DESSERT("Dessert", 6.00, 2.00);

    private final String name;
    private final double price;
    private final double cost;

    Menu(String name, double price, double cost) {
        this.name = name;
        this.price = price;
        this.cost = cost;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public double getCost() {
        return cost;
    }

    /**
     * Get menu item by name (case insensitive)
     */
    public static Menu getByName(String name) {
        for (Menu item : values()) {
            if (item.name.equalsIgnoreCase(name)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return name + " ($" + String.format("%.2f", price) + ")";
    }
}
