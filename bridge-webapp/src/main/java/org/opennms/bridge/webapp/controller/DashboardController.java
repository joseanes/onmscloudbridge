package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.api.CloudProviderException;
import org.opennms.bridge.api.CloudResource;
import org.opennms.bridge.api.DiscoveryService;
import org.opennms.bridge.api.CollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for dashboard data and metrics.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    private List<CloudProvider> cloudProviders;
    
    @Autowired
    private DiscoveryService discoveryService;
    
    @Autowired
    private CollectionService collectionService;
    
    @Autowired
    private DiscoveryController discoveryController;
    
    @Autowired
    private CollectionController collectionController;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        LOG.debug("Getting dashboard summary");
        
        try {
            // Count active cloud providers
            int providerCount = cloudProviders.size();
            
            // Count active discovery jobs
            Map<String, Object> discoveryJobs = discoveryController.getAllDiscoveryJobs().getBody();
            int activeDiscoveryJobs = 0;
            Instant lastDiscoveryTimestamp = null;
            
            if (discoveryJobs != null && discoveryJobs.containsKey("jobs")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jobs = (List<Map<String, Object>>) discoveryJobs.get("jobs");
                
                // Count active jobs
                activeDiscoveryJobs = (int) jobs.stream()
                        .filter(job -> "IN_PROGRESS".equals(job.get("status")))
                        .count();
                
                // Find latest discovery timestamp
                Optional<Instant> latestTimestamp = jobs.stream()
                        .filter(job -> job.containsKey("endTime") && job.get("endTime") != null)
                        .map(job -> (Instant) job.get("endTime"))
                        .max(Instant::compareTo);
                
                if (latestTimestamp.isPresent()) {
                    lastDiscoveryTimestamp = latestTimestamp.get();
                }
            }
            
            // Count collection jobs and get latest timestamp
            Map<String, Object> collectionJobs = collectionController.getAllCollectionJobs().getBody();
            Instant lastCollectionTimestamp = null;
            
            if (collectionJobs != null && collectionJobs.containsKey("jobs")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jobs = (List<Map<String, Object>>) collectionJobs.get("jobs");
                
                // Find latest collection timestamp
                Optional<Instant> latestTimestamp = jobs.stream()
                        .filter(job -> job.containsKey("endTime") && job.get("endTime") != null)
                        .map(job -> (Instant) job.get("endTime"))
                        .max(Instant::compareTo);
                
                if (latestTimestamp.isPresent()) {
                    lastCollectionTimestamp = latestTimestamp.get();
                }
            }
            
            // Count discovered resources from all providers
            int discoveredResources = 0;
            List<Map<String, Object>> providerSummaries = new ArrayList<>();
            
            for (CloudProvider provider : cloudProviders) {
                try {
                    // Get resources for this provider
                    Set<CloudResource> resources = discoveryService.discoverResources(provider.getProviderId());
                    
                    // Count resources
                    discoveredResources += resources.size();
                    
                    // Create provider summary
                    Map<String, Object> providerSummary = new HashMap<>();
                    providerSummary.put("id", provider.getProviderId());
                    providerSummary.put("name", provider.getDisplayName());
                    providerSummary.put("type", provider.getProviderType());
                    providerSummary.put("resourceCount", resources.size());
                    
                    try {
                        providerSummary.put("status", provider.validate().isValid() ? "CONNECTED" : "ERROR");
                    } catch (Exception e) {
                        providerSummary.put("status", "ERROR");
                    }
                    
                    // Get regions with resources
                    Set<String> regionsWithResources = resources.stream()
                            .map(CloudResource::getRegion)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    
                    providerSummary.put("regions", regionsWithResources);
                    
                    // Add to list
                    providerSummaries.add(providerSummary);
                } catch (Exception e) {
                    LOG.warn("Error getting resources for provider {}: {}", 
                            provider.getProviderId(), e.getMessage());
                    
                    // Create provider summary with error
                    Map<String, Object> providerSummary = new HashMap<>();
                    providerSummary.put("id", provider.getProviderId());
                    providerSummary.put("name", provider.getDisplayName());
                    providerSummary.put("type", provider.getProviderType());
                    providerSummary.put("resourceCount", 0);
                    providerSummary.put("status", "ERROR");
                    providerSummary.put("error", e.getMessage());
                    
                    // Add to list
                    providerSummaries.add(providerSummary);
                }
            }
            
            // Get last requisition update info
            Map<String, Object> lastRequisitionUpdate = new HashMap<>();
            lastRequisitionUpdate.put("timestamp", lastDiscoveryTimestamp);
            // Default to success if we have a timestamp, otherwise false
            boolean requisitionSuccess = lastDiscoveryTimestamp != null;
            lastRequisitionUpdate.put("success", requisitionSuccess);
            // Add some sample details - in a real app this would come from OpenNMS
            lastRequisitionUpdate.put("nodeCount", requisitionSuccess ? discoveredResources : 0);
            lastRequisitionUpdate.put("foreignSource", "cloud-aws-default");
            
            // Get last collection data transfer info
            Map<String, Object> lastCollectionTransfer = new HashMap<>();
            lastCollectionTransfer.put("timestamp", lastCollectionTimestamp);
            // Default to success if we have a timestamp, otherwise false
            boolean collectionSuccess = lastCollectionTimestamp != null;
            lastCollectionTransfer.put("success", collectionSuccess);
            // Add some sample details - in a real app this would come from OpenNMS
            lastCollectionTransfer.put("metricCount", collectionSuccess ? 54 : 0);
            lastCollectionTransfer.put("resourceCount", collectionSuccess ? discoveredResources : 0);
            
            // Create summary data
            Map<String, Object> summaryData = new HashMap<>();
            summaryData.put("totalCloudProviders", providerCount);
            summaryData.put("activeProviders", providerSummaries.stream()
                    .filter(p -> "CONNECTED".equals(p.get("status")))
                    .count());
            summaryData.put("activeDiscoveryJobs", activeDiscoveryJobs);
            summaryData.put("discoveredResources", discoveredResources);
            summaryData.put("lastCollectionTimestamp", lastCollectionTimestamp);
            summaryData.put("lastDiscoveryTimestamp", lastDiscoveryTimestamp);
            summaryData.put("lastRequisitionUpdate", lastRequisitionUpdate);
            summaryData.put("lastCollectionTransfer", lastCollectionTransfer);
            summaryData.put("providers", providerSummaries);
            
            return ResponseEntity.ok(summaryData);
        } catch (Exception e) {
            LOG.error("Error getting dashboard summary: {}", e.getMessage(), e);
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", "Failed to get dashboard summary: " + e.getMessage());
            return ResponseEntity.ok(errorData);
        }
    }
    
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String providerId) {
        LOG.debug("Getting metrics with timeRange: {}, providerId: {}", timeRange, providerId);
        
        try {
            // Parse time range (default to 24h)
            String effectiveTimeRange = timeRange != null ? timeRange : "24h";
            Duration duration;
            
            switch (effectiveTimeRange) {
                case "1h":
                    duration = Duration.ofHours(1);
                    break;
                case "6h":
                    duration = Duration.ofHours(6);
                    break;
                case "7d":
                    duration = Duration.ofDays(7);
                    break;
                case "30d":
                    duration = Duration.ofDays(30);
                    break;
                case "24h":
                default:
                    duration = Duration.ofHours(24);
                    effectiveTimeRange = "24h";
                    break;
            }
            
            // Calculate start time
            Instant startTime = Instant.now().minus(duration);
            
            // Get metrics for the specified provider or all providers
            List<Map<String, Object>> metricsList = new ArrayList<>();
            List<CloudProvider> targetProviders;
            
            if (providerId != null && !providerId.isEmpty()) {
                // Filter by provider ID
                targetProviders = cloudProviders.stream()
                        .filter(p -> p.getProviderId().equals(providerId))
                        .collect(Collectors.toList());
            } else {
                // Use all providers
                targetProviders = cloudProviders;
            }
            
            // Collect metrics from each provider
            for (CloudProvider provider : targetProviders) {
                try {
                    // Get resources
                    Set<CloudResource> resources = discoveryService.discoverResources(provider.getProviderId());
                    
                    // Collect sample metrics for each resource
                    for (CloudResource resource : resources) {
                        try {
                            // Get metrics
                            Map<String, Object> resourceMetrics = new HashMap<>();
                            resourceMetrics.put("providerId", provider.getProviderId());
                            resourceMetrics.put("providerName", provider.getDisplayName());
                            resourceMetrics.put("resourceId", resource.getResourceId());
                            resourceMetrics.put("resourceName", resource.getDisplayName());
                            resourceMetrics.put("resourceType", resource.getResourceType());
                            resourceMetrics.put("region", resource.getRegion());
                            
                            // Include a subset of supported metrics
                            Set<String> supportedMetrics = provider.getSupportedMetrics();
                            resourceMetrics.put("metrics", supportedMetrics);
                            
                            metricsList.add(resourceMetrics);
                        } catch (Exception e) {
                            LOG.warn("Error getting metrics for resource {}: {}", 
                                    resource.getResourceId(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Error getting resources for provider {}: {}", 
                            provider.getProviderId(), e.getMessage());
                }
            }
            
            // Create metrics data
            Map<String, Object> metricsData = new HashMap<>();
            metricsData.put("timeRange", effectiveTimeRange);
            metricsData.put("startTime", startTime);
            metricsData.put("endTime", Instant.now());
            metricsData.put("metrics", metricsList);
            metricsData.put("count", metricsList.size());
            
            return ResponseEntity.ok(metricsData);
        } catch (Exception e) {
            LOG.error("Error getting metrics: {}", e.getMessage(), e);
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", "Failed to get metrics: " + e.getMessage());
            return ResponseEntity.ok(errorData);
        }
    }
    
    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts() {
        LOG.debug("Getting alerts");
        
        // For now, we'll just return sample alerts
        // In a real implementation, you would integrate with OpenNMS alarms
        List<Map<String, Object>> alerts = new ArrayList<>();
        
        // Sample alert 1
        Map<String, Object> alert1 = new HashMap<>();
        alert1.put("id", UUID.randomUUID().toString());
        alert1.put("severity", "CRITICAL");
        alert1.put("type", "CPU_UTILIZATION");
        alert1.put("message", "High CPU utilization detected");
        alert1.put("resourceId", "i-1234567890abcdef0");
        alert1.put("timestamp", Instant.now().minusSeconds(300));
        alerts.add(alert1);
        
        // Sample alert 2
        Map<String, Object> alert2 = new HashMap<>();
        alert2.put("id", UUID.randomUUID().toString());
        alert2.put("severity", "WARNING");
        alert2.put("type", "DISK_SPACE");
        alert2.put("message", "Disk space running low");
        alert2.put("resourceId", "i-0987654321fedcba0");
        alert2.put("timestamp", Instant.now().minusSeconds(600));
        alerts.add(alert2);
        
        // Create alerts data
        Map<String, Object> alertsData = new HashMap<>();
        alertsData.put("alerts", alerts);
        alertsData.put("count", alerts.size());
        
        return ResponseEntity.ok(alertsData);
    }
}