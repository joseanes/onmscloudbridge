package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.api.CloudResource;
import org.opennms.bridge.api.DiscoveryLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for discovery logs.
 * This controller provides endpoints to view detailed logs from discovery operations.
 */
@RestController
@RequestMapping("/api/discovery-logs")
public class DiscoveryLogController implements DiscoveryLogService {
    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryLogController.class);
    
    @Autowired
    private List<CloudProvider> cloudProviders;
    
    // Store logs by provider ID
    private final Map<String, List<Map<String, Object>>> discoveryLogs = new ConcurrentHashMap<>();
    
    // Store the latest discovered resources for each provider
    private final Map<String, Set<CloudResource>> discoveredResources = new ConcurrentHashMap<>();
    
    @Override
    public void addLogEntry(String providerId, Map<String, Object> logEntry) {
        // Create list if it doesn't exist
        discoveryLogs.computeIfAbsent(providerId, k -> new ArrayList<>());
        
        // Add log entry with timestamp
        logEntry.put("timestamp", new Date());
        List<Map<String, Object>> logs = discoveryLogs.get(providerId);
        
        // Limit to 100 entries per provider
        if (logs.size() >= 100) {
            logs.remove(0);
        }
        
        logs.add(logEntry);
        LOG.debug("Added discovery log entry for provider {}: {}", providerId, logEntry);
    }
    
    @Override
    public void storeDiscoveredResources(String providerId, Set<CloudResource> resources) {
        discoveredResources.put(providerId, resources);
        LOG.debug("Stored {} discovered resources for provider {}", resources.size(), providerId);
    }
    
    /**
     * Get discovery logs for a provider via REST
     */
    @GetMapping("/{providerId}")
    public ResponseEntity<List<Map<String, Object>>> getProviderLogsRest(@PathVariable String providerId) {
        LOG.debug("Getting discovery logs for provider: {}", providerId);
        List<Map<String, Object>> logs = getProviderLogs(providerId);
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Implementation of the interface method
     */
    @Override
    public List<Map<String, Object>> getProviderLogs(String providerId) {
        return discoveryLogs.getOrDefault(providerId, new ArrayList<>());
    }
    
    /**
     * Get discovered resources for a provider via REST
     */
    @GetMapping("/{providerId}/resources")
    public ResponseEntity<List<Map<String, Object>>> getDiscoveredResourcesRest(@PathVariable String providerId) {
        LOG.debug("Getting discovered resources for provider: {}", providerId);
        List<Map<String, Object>> resources = getDiscoveredResources(providerId);
        return ResponseEntity.ok(resources);
    }
    
    /**
     * Implementation of the interface method
     */
    @Override
    public List<Map<String, Object>> getDiscoveredResources(String providerId) {
        Set<CloudResource> resources = discoveredResources.getOrDefault(providerId, Collections.emptySet());
        List<Map<String, Object>> resourceData = new ArrayList<>();
        
        for (CloudResource resource : resources) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", resource.getResourceId());
            data.put("name", resource.getDisplayName());
            data.put("type", resource.getResourceType());
            data.put("status", resource.getStatus());
            data.put("region", resource.getRegion());
            
            // Combine tags and properties for detailed view
            Map<String, Object> details = new HashMap<>();
            details.putAll(resource.getProperties());
            resource.getTags().forEach(details::put);
            data.put("details", details);
            
            resourceData.add(data);
        }
        
        return resourceData;
    }
    
    /**
     * Clear logs for a provider via REST
     */
    @DeleteMapping("/{providerId}")
    public ResponseEntity<Map<String, Object>> clearProviderLogsRest(@PathVariable String providerId) {
        LOG.info("Clearing discovery logs for provider: {}", providerId);
        clearProviderLogs(providerId);
        return ResponseEntity.ok(Map.of(
            "message", "Logs cleared for provider " + providerId,
            "providerId", providerId
        ));
    }
    
    /**
     * Implementation of the interface method
     */
    @Override
    public void clearProviderLogs(String providerId) {
        discoveryLogs.remove(providerId);
    }
}