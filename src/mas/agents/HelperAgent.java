package mas.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;

/**
 * HelperAgent is a temporary agent used to send messages to AgentFactoryAgent.
 * Created on-demand and removed after sending the message.
 */
public class HelperAgent extends Agent {
    private static final long serialVersionUID = 1L;
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] instanceof String) {
            String content = (String) args[0];
            
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("AgentFactory", AID.ISLOCALNAME));
            msg.setContent(content);
            send(msg);
            
            // Wait a bit for response
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        // Agent will be killed by Main class after use
    }
}

