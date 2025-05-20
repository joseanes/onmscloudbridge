package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.webapp.config.OpenNMSAutoConnectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for managing OpenNMS synchronization settings.
 */
@RestController
@RequestMapping("/opennms/sync")
public class OpenNMSSyncController {
    private static final Logger LOG = LoggerFactory.getLogger(OpenNMSSyncController.class);

    @Autowired
    private OpenNMSAutoConnectConfig autoConnectConfig;

    /**
     * Get synchronization settings
     */
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSyncSettings() {
        LOG.debug("Getting OpenNMS sync settings");
        
        return ResponseEntity.ok(autoConnectConfig.getStatus());
    }
    
    /**
     * Update node synchronization settings
     */
    @PostMapping("/nodes")
    public ResponseEntity<Map<String, Object>> updateNodeSyncSettings(@RequestBody Map<String, Object> settings) {
        LOG.info("Updating OpenNMS node sync settings: {}", settings);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (settings.containsKey("enabled")) {
                boolean enabled = Boolean.parseBoolean(settings.get("enabled").toString());
                autoConnectConfig.setAutoSyncNodes(enabled);
                response.put("autoSyncNodes", enabled);
                response.put("message", "Node auto-sync " + (enabled ? "enabled" : "disabled"));
            } else {
                response.put("error", "Missing 'enabled' parameter");
                return ResponseEntity.badRequest().body(response);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error updating node sync settings", e);
            response.put("error", "Failed to update node sync settings: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Update metrics synchronization settings
     */
    @PostMapping("/metrics")
    public ResponseEntity<Map<String, Object>> updateMetricsSyncSettings(@RequestBody Map<String, Object> settings) {
        LOG.info("Updating OpenNMS metrics sync settings: {}", settings);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean updated = false;
            
            if (settings.containsKey("enabled")) {
                boolean enabled = Boolean.parseBoolean(settings.get("enabled").toString());
                autoConnectConfig.setAutoSyncMetrics(enabled);
                response.put("autoSyncMetrics", enabled);
                updated = true;
            }
            
            if (settings.containsKey("interval")) {
                long interval;
                try {
                    interval = Long.parseLong(settings.get("interval").toString());
                    if (interval < 1) {
                        response.put("warning", "Invalid interval, using minimum value (1 minute)");
                        interval = 1;
                    }
                    autoConnectConfig.setSyncInterval(interval);
                    response.put("syncIntervalMinutes", interval);
                    updated = true;
                } catch (NumberFormatException e) {
                    response.put("warning", "Invalid interval format, using default");
                }
            }
            
            if (!updated) {
                response.put("error", "No valid settings provided");
                return ResponseEntity.badRequest().body(response);
            }
            
            response.put("message", "Metrics sync settings updated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error updating metrics sync settings", e);
            response.put("error", "Failed to update metrics sync settings: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Trigger immediate synchronization of all metrics
     */
    @PostMapping("/metrics/sync-now")
    public ResponseEntity<Map<String, Object>> syncMetricsNow() {
        LOG.info("Manually triggering OpenNMS metrics synchronization");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            autoConnectConfig.syncAllMetricsToOpenNMS();
            response.put("success", true);
            response.put("message", "Metrics synchronization started");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error triggering metrics synchronization", e);
            response.put("error", "Failed to trigger metrics synchronization: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}