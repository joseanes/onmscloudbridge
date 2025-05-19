package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.core.service.OpenNMSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for managing OpenNMS connection settings and status.
 */
@RestController
@RequestMapping("/api/opennms")
public class OpenNMSController {
    private static final Logger LOG = LoggerFactory.getLogger(OpenNMSController.class);

    @Autowired
    private OpenNMSClient openNMSClient;
    
    @Value("${opennms.base-url}")
    private String baseUrl;
    
    @Value("${opennms.username}")
    private String username;
    
    @Value("${opennms.default-location}")
    private String defaultLocation;

    /**
     * Get OpenNMS connection information
     */
    @GetMapping("/connection")
    public ResponseEntity<Map<String, Object>> getConnectionInfo() {
        LOG.debug("Getting OpenNMS connection info");
        
        boolean connectionStatus = openNMSClient.testConnection();
        
        Map<String, Object> connectionInfo = new HashMap<>();
        connectionInfo.put("baseUrl", baseUrl);
        connectionInfo.put("username", username);
        connectionInfo.put("defaultLocation", defaultLocation);
        connectionInfo.put("connectionStatus", connectionStatus);
        
        return ResponseEntity.ok(connectionInfo);
    }
    
    /**
     * Test the OpenNMS connection
     */
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        LOG.info("Testing OpenNMS connection to URL: {}", baseUrl);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean success = openNMSClient.testConnection();
            
            result.put("success", success);
            if (success) {
                result.put("status", "connected");
                result.put("message", "Successfully connected to OpenNMS at " + baseUrl);
            } else {
                result.put("status", "error");
                // Check if the URL is accessible at all by trying to ping the base URL
                try {
                    boolean baseUrlExists = openNMSClient.isUrlAccessible();
                    if (baseUrlExists) {
                        result.put("message", "OpenNMS server was found, but API access failed. Check URL path and credentials.");
                        result.put("detailMessage", "The server at " + baseUrl + " is reachable, but the API endpoint could not be accessed. Make sure the URL is correct and includes the proper context path for OpenNMS (usually '/opennms').");
                    } else {
                        result.put("message", "Cannot connect to OpenNMS server. Server may not be running.");
                        result.put("detailMessage", "The server at " + baseUrl + " is not responding. Make sure the OpenNMS server is running and the URL is correct.");
                    }
                } catch (Exception ex) {
                    result.put("message", "Failed to connect to OpenNMS. Check URL and credentials.");
                    result.put("detailMessage", "Error details: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Error testing OpenNMS connection", e);
            
            result.put("success", false);
            result.put("status", "error");
            result.put("message", "Error testing connection: " + e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Update OpenNMS connection settings
     */
    @PostMapping("/connection")
    public ResponseEntity<Map<String, Object>> updateConnectionSettings(@RequestBody Map<String, String> settings) {
        LOG.info("Updating OpenNMS connection settings");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // This is just a mock implementation
            // In a real app, this would update application properties or a database
            result.put("success", true);
            result.put("message", "Changes saved but not applied. Restart required to apply changes.");
            result.put("settings", settings);
            result.put("note", "Note: In this demo version, settings are not permanently saved.");
        } catch (Exception e) {
            LOG.error("Error updating OpenNMS connection settings", e);
            result.put("success", false);
            result.put("message", "Error updating settings: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get OpenNMS status information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        LOG.debug("Getting OpenNMS system status");
        
        Map<String, Object> status = new HashMap<>();
        
        try {
            boolean connected = openNMSClient.testConnection();
            status.put("connected", connected);
            
            if (connected) {
                // In a real implementation, these would be actual metrics
                // from the OpenNMS REST API
                status.put("nodesUp", 42);
                status.put("nodesDown", 3);
                status.put("alarms", 5);
                status.put("outages", 2);
                status.put("discoveredServices", 128);
                status.put("uptimeHours", 72);
            }
            
            status.put("message", connected ? 
                "Connected to OpenNMS" : 
                "Not connected to OpenNMS");
                
        } catch (Exception e) {
            LOG.error("Error getting OpenNMS status", e);
            status.put("connected", false);
            status.put("message", "Error getting status: " + e.getMessage());
            status.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(status);
    }
}