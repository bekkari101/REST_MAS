package mas.main;

import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

/**
 * Example class showing how to request agent and container creation from AgentFactoryAgent.
 * This demonstrates how to send requests to the father agent.
 */
public class ExampleUsage {
    
    /**
     * Example: Request creation of a Boss agent
     * @param container The container controller
     * @param agentName Name for the new agent
     * @param x X coordinate
     * @param y Y coordinate
     */
    public static void requestBossAgent(ContainerController container, String agentName, double x, double y) {
        try {
            // Create a temporary agent to send the request
            AgentController tempAgent = container.createNewAgent(
                "TempAgent", 
                "jade.core.Agent", 
                null
            );
            tempAgent.start();
            
            // Create request message
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("AgentFactory", AID.ISLOCALNAME));
            msg.setContent("CREATE_AGENT:boss:" + agentName + ":" + x + ":" + y + ":main");
            
            // Note: In a real scenario, you'd use an agent's send() method
            // This is just an example of the message format
            
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Example: Request creation of a container
     * @param containerName Name of the container
     * @param host Host address
     * @param port Port number
     */
    public static void requestContainer(String containerName, String host, String port) {
        // Message format: CREATE_CONTAINER:containerName:host:port
        String messageContent = "CREATE_CONTAINER:" + containerName + ":" + host + ":" + port;
        System.out.println("To create container, send message: " + messageContent);
    }
}

