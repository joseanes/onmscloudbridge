package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.api.CollectionLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for accessing collection logs.
 */
@RestController
@RequestMapping("/api/collection/logs")
public class CollectionLogController {
    private static final Logger LOG = LoggerFactory.getLogger(CollectionLogController.class);

    @Autowired
    private CollectionLogService collectionLogService;

    /**
     * Get collection logs for a provider.
     */
    @GetMapping("/{providerId}")
    public ResponseEntity<Map<String, Object>> getCollectionLogs(@PathVariable String providerId) {
        LOG.debug("Getting collection logs for provider: {}", providerId);
        
        if (collectionLogService == null) {
            return ResponseEntity.ok(Map.of(
                "message", "Collection logs not available - log service not configured",
                "providerId", providerId,
                "logs", Collections.emptyList()
            ));
        }
        
        try {
            List<Map<String, Object>> logs = collectionLogService.getProviderLogs(providerId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("providerId", providerId);
            response.put("logs", logs != null ? logs : Collections.emptyList());
            response.put("count", logs != null ? logs.size() : 0);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error getting collection logs: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get collection logs: " + e.getMessage(),
                "providerId", providerId
            ));
        }
    }

    /**
     * Clear collection logs for a provider.
     */
    @DeleteMapping("/{providerId}")
    public ResponseEntity<Map<String, Object>> clearCollectionLogs(@PathVariable String providerId) {
        LOG.info("Clearing collection logs for provider: {}", providerId);
        
        if (collectionLogService == null) {
            return ResponseEntity.ok(Map.of(
                "message", "Collection logs not available - log service not configured",
                "providerId", providerId
            ));
        }
        
        try {
            collectionLogService.clearProviderLogs(providerId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Collection logs cleared for provider: " + providerId,
                "providerId", providerId
            ));
        } catch (Exception e) {
            LOG.error("Error clearing collection logs: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to clear collection logs: " + e.getMessage(),
                "providerId", providerId
            ));
        }
    }

    /**
     * Get collection results for a provider.
     */
    @GetMapping("/results/{providerId}")
    public ResponseEntity<Map<String, Object>> getCollectionResults(@PathVariable String providerId) {
        LOG.debug("Getting collection results for provider: {}", providerId);
        
        if (collectionLogService == null) {
            return ResponseEntity.ok(Map.of(
                "message", "Collection results not available - log service not configured",
                "providerId", providerId,
                "results", Collections.emptyList()
            ));
        }
        
        try {
            List<Map<String, Object>> results = collectionLogService.getCollectionResults(providerId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("providerId", providerId);
            response.put("results", results != null ? results : Collections.emptyList());
            response.put("count", results != null ? results.size() : 0);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error getting collection results: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get collection results: " + e.getMessage(),
                "providerId", providerId
            ));
        }
    }
}