package mas.agents;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import mas.core.BaseAgent;
import mas.core.AgentStatus;
import mas.core.Menu;
import mas.core.DebugLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * BossAgent represents the restaurant manager/owner.
 * Oversees restaurant operations, staff management, and profit planning.
 */
public class BossAgent extends BaseAgent {
    private static final long serialVersionUID = 1L;
    private double totalRevenue = 0;
    private double totalCost = 0;
    private double targetProfit = 50.0; // Goal for the day
    private List<Menu> dailyMenu = new ArrayList<>();
    
    /**
     * Constructor for BossAgent with position coordinates
     */
    public BossAgent(double x, double y) {
        super(x, y);
    }
    
    /**
     * Default constructor - position set to (0, 0)
     */
    public BossAgent() {
        super();
    }
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null) {
            initializePosition(args);
        }
        System.out.println("BossAgent " + getLocalName() + " initialized. Target Profit: $" + targetProfit);
        DebugLogger.info(getLocalName(), "boss", "BossContainer", "Restaurant manager ready! Target: $" + targetProfit);
        
        // Initialize status
        status = AgentStatus.BOSS_MONITORING;
        
        // Add behavior to wait for simulation to start before planning
        addBehaviour(new jade.core.behaviours.TickerBehaviour(this, 1000) {
            private static final long serialVersionUID = 1L;
            private boolean hasStarted = false;
            
            @Override
            protected void onTick() {
                if (!hasStarted && mas.core.TickSystem.getInstance().isRunning()) {
                    hasStarted = true;
                    // Add planning behavior once simulation starts
                    addBehaviour(new MenuPlanningBehaviour());
                    stop(); // Stop this behavior
                }
            }
        });
        
        // Add profit tracking and menu request behavior
        addBehaviour(new BossMessageHandler());
        
        // Periodic Boss Status (Mood) log
        addBehaviour(new jade.core.behaviours.TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                double netProfit = totalRevenue - totalCost;
                System.out.println("\n--- Boss Status Report ---");
                System.out.println("Revenue: $" + String.format("%.2f", totalRevenue));
                System.out.println("Cost:    $" + String.format("%.2f", totalCost));
                System.out.println("Net:     $" + String.format("%.2f", netProfit));
                
                if (netProfit >= targetProfit) {
                    System.out.println("BOSS STATUS: HAPPY! :D (Target reached)");
                } else if (netProfit > 0) {
                    System.out.println("BOSS STATUS: CAUTIOUSLY OPTIMISTIC (Profit is positive)");
                } else {
                    System.out.println("BOSS STATUS: NOT HAPPY. :( (Need more clients)");
                }
                System.out.println("--------------------------\n");
            }
        });
    }
    
    /**
     * Planning behavior: Decisions on daily menu to maximize profit.
     */
    private class MenuPlanningBehaviour extends OneShotBehaviour {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void action() {
            System.out.println("BossAgent: Planning phase started...");
            DebugLogger.info(getLocalName(), "boss", "BossContainer", "Planning today's menu...");
            
            // Randomly select 3-4 items for the daily menu
            Menu[] allItems = Menu.values();
            Random rand = new Random();
            int menuSize = 3 + rand.nextInt(2); // 3 or 4 items
            
            while (dailyMenu.size() < menuSize) {
                Menu item = allItems[rand.nextInt(allItems.length)];
                if (!dailyMenu.contains(item)) {
                    dailyMenu.add(item);
                }
            }
            
            System.out.println("BossAgent: Daily menu decided: " + dailyMenu);
            DebugLogger.success(getLocalName(), "boss", "BossContainer", "Menu ready: " + dailyMenu.toString());
            
            // Communicate menu to all TableAgents (Table1 to Table4)
            broadcastMenu();
        }
    }
    
    /**
     * Broadcast the menu to all tables
     */
    private void broadcastMenu() {
        if (dailyMenu.isEmpty()) return;
        
        String menuString = getMenuString();
        for (int i = 1; i <= 4; i++) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID("Table" + i, AID.ISLOCALNAME));
            msg.setContent(menuString);
            msg.setConversationId("Menu-Broadcasting");
            msg.setOntology("Restaurant-Init");
            send(msg);
        }
        System.out.println("BossAgent: Menu broadcasted to TableAgents.");
        DebugLogger.success(getLocalName(), "boss", "BossContainer", "Menu sent to all tables");
    }
    
    /**
     * Get menu as a formatted string
     */
    private String getMenuString() {
        StringBuilder sb = new StringBuilder("DAILY_MENU:");
        for (int i = 0; i < dailyMenu.size(); i++) {
            sb.append(dailyMenu.get(i).name());
            if (i < dailyMenu.size() - 1) sb.append(",");
        }
        return sb.toString();
    }
    
    /**
     * Listen for messages (profit reports, menu requests)
     */
    private class BossMessageHandler extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String content = msg.getContent();
                if (content.startsWith("PROFIT_REPORT:")) {
                    try {
                        // Format: PROFIT_REPORT:price:cost
                        String[] parts = content.split(":");
                        if (parts.length >= 3) {
                            double price = Double.parseDouble(parts[1]);
                            double cost = Double.parseDouble(parts[2]);
                            totalRevenue += price;
                            totalCost += cost;
                            System.out.println("BossAgent: Received payment of $" + price + " (Cost: $" + cost + ")");
                            double netProfit = totalRevenue - totalCost;
                            String mood = netProfit >= targetProfit ? "😊 HAPPY" : (netProfit > 0 ? "😐 OK" : "😟 WORRIED");
                            DebugLogger.info(getLocalName(), "boss", "BossContainer", "Revenue: $" + String.format("%.2f", totalRevenue) + " | Profit: $" + String.format("%.2f", netProfit) + " " + mood);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("BossAgent: Error parsing profit report: " + content);
                    }
                } else if (content.equals("GET_MENU")) {
                    if (!dailyMenu.isEmpty()) {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent(getMenuString());
                        reply.setConversationId("Menu-Request");
                        reply.setOntology("Restaurant-Init");
                        send(reply);
                        System.out.println("BossAgent: Sent menu to " + msg.getSender().getLocalName() + " on request.");
                    }
                }
            } else {
                block();
            }
        }
    }
}
