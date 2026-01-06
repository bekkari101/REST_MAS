package mas.core;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for sending debug messages to the Flask API
 * for display in the web dashboard debug console.
 */
public class DebugLogger {
    
    private static final String API_URL = "http://localhost:5001/debug";
    private static boolean enabled = true;
    
    public enum Level {
        DEBUG, INFO, SUCCESS, WARNING, ERROR
    }
    
    /**
     * Send a debug message to the web dashboard
     */
    public static void log(String agentId, String agentType, String container, 
                          Level level, String message, String status) {
        if (!enabled) return;
        
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            String jsonPayload = String.format(
                "{\"agent_id\":\"%s\",\"agent_type\":\"%s\",\"container\":\"%s\"," +
                "\"level\":\"%s\",\"message\":\"%s\",\"status\":\"%s\"}",
                agentId, agentType, container, level.name(), 
                escapeJson(message), status
            );
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.err.println("[DebugLogger] Failed to send debug message: HTTP " + responseCode);
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
            // Silently fail - don't disrupt agent operation
            // System.err.println("[DebugLogger] Error: " + e.getMessage());
        }
    }
    
    /**
     * Convenience method with default status
     */
    public static void log(String agentId, String agentType, String container, 
                          Level level, String message) {
        log(agentId, agentType, container, level, message, "");
    }
    
    /**
     * Debug level log
     */
    public static void debug(String agentId, String agentType, String container, String message) {
        log(agentId, agentType, container, Level.DEBUG, message);
    }
    
    /**
     * Info level log
     */
    public static void info(String agentId, String agentType, String container, String message) {
        log(agentId, agentType, container, Level.INFO, message);
    }
    
    /**
     * Success level log
     */
    public static void success(String agentId, String agentType, String container, String message) {
        log(agentId, agentType, container, Level.SUCCESS, message);
    }
    
    /**
     * Warning level log
     */
    public static void warning(String agentId, String agentType, String container, String message) {
        log(agentId, agentType, container, Level.WARNING, message);
    }
    
    /**
     * Error level log
     */
    public static void error(String agentId, String agentType, String container, String message) {
        log(agentId, agentType, container, Level.ERROR, message);
    }
    
    /**
     * Enable or disable debug logging
     */
    public static void setEnabled(boolean enable) {
        enabled = enable;
    }
    
    /**
     * Escape special characters for JSON
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}
