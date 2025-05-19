package org.opennms.bridge.webapp.service;

import org.opennms.bridge.api.CloudResource;
import org.opennms.bridge.api.DiscoveryLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of the DiscoveryLogService interface for testing.
 * This service provides sample discovery logs and resources for demo purposes.
 */
@Service
public class MockDiscoveryLogService implements DiscoveryLogService {
    private static final Logger LOG = LoggerFactory.getLogger(MockDiscoveryLogService.class);
    
    // Store logs by provider ID
    private final Map<String, List<Map<String, Object>>> discoveryLogs = new ConcurrentHashMap<>();
    
    // Store the latest discovered resources for each provider
    private final Map<String, Set<CloudResource>> discoveredResources = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // Create some sample log entries for the mock AWS provider
        List<Map<String, Object>> mockLogs = createSampleLogs("aws-mock");
        discoveryLogs.put("aws-mock", mockLogs);
        
        LOG.info("Initialized mock discovery logs for 'aws-mock' provider");
    }
    
    @Override
    public void addLogEntry(String providerId, Map<String, Object> logEntry) {
        // Create list if it doesn't exist
        discoveryLogs.computeIfAbsent(providerId, k -> new ArrayList<>());
        
        // Add log entry with timestamp if not already present
        if (!logEntry.containsKey("timestamp")) {
            logEntry.put("timestamp", new Date());
        }
        
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
    
    @Override
    public List<Map<String, Object>> getProviderLogs(String providerId) {
        // If provider doesn't have logs yet, generate sample logs
        if (!discoveryLogs.containsKey(providerId)) {
            discoveryLogs.put(providerId, createSampleLogs(providerId));
        }
        
        return discoveryLogs.getOrDefault(providerId, new ArrayList<>());
    }
    
    @Override
    public List<Map<String, Object>> getDiscoveredResources(String providerId) {
        // For mock service, create some fake resources if needed
        if (!discoveredResources.containsKey(providerId)) {
            createSampleResources(providerId);
        }
        
        Set<CloudResource> resources = discoveredResources.getOrDefault(providerId, Collections.emptySet());
        List<Map<String, Object>> resourceData = new ArrayList<>();
        
        for (CloudResource resource : resources) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", resource.getResourceId());
            data.put("name", resource.getDisplayName());
            data.put("type", resource.getResourceType());
            data.put("status", resource.getStatus());
            data.put("region", resource.getRegion());
            
            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            resource.getProperties().forEach(metadata::put);
            resource.getTags().forEach(metadata::put);
            data.put("metadata", metadata);
            
            resourceData.add(data);
        }
        
        return resourceData;
    }
    
    @Override
    public void clearProviderLogs(String providerId) {
        discoveryLogs.remove(providerId);
    }
    
    /**
     * Create sample logs for a provider
     */
    private List<Map<String, Object>> createSampleLogs(String providerId) {
        List<Map<String, Object>> logs = new ArrayList<>();
        
        // Base time for our logs (30 minutes ago)
        Instant baseTime = Instant.now().minus(30, ChronoUnit.MINUTES);
        
        // Add some startup logs
        addSampleLog(logs, baseTime, "INFO", "Discovery", "Starting discovery for provider: " + providerId);
        addSampleLog(logs, baseTime.plus(1, ChronoUnit.SECONDS), "INFO", "CredentialProvider", "Initializing credential provider for " + providerId);
        addSampleLog(logs, baseTime.plus(2, ChronoUnit.SECONDS), "DEBUG", "AWSCredentials", "Using default credential provider chain");
        
        // Add some connection logs
        addSampleLog(logs, baseTime.plus(3, ChronoUnit.SECONDS), "INFO", "Discovery", "Connecting to AWS API");
        addSampleLog(logs, baseTime.plus(4, ChronoUnit.SECONDS), "INFO", "AWSClient", "Successfully connected to AWS API for regions: us-east-1");
        
        // Add some discovery logs
        addSampleLog(logs, baseTime.plus(5, ChronoUnit.SECONDS), "INFO", "EC2Discovery", "Starting EC2 instance discovery");
        addSampleLog(logs, baseTime.plus(6, ChronoUnit.SECONDS), "DEBUG", "EC2Discovery", "Fetching all EC2 instances with state: running");
        addSampleLog(logs, baseTime.plus(8, ChronoUnit.SECONDS), "INFO", "EC2Discovery", "Found 5 EC2 instances in us-east-1");
        
        // Add a warning
        addSampleLog(logs, baseTime.plus(10, ChronoUnit.SECONDS), "WARN", "EC2Discovery", "Rate limiting detected, slowing down requests");
        
        // Continue discovery
        addSampleLog(logs, baseTime.plus(12, ChronoUnit.SECONDS), "INFO", "EC2Discovery", "Processing instance i-1234567890abcdef0");
        addSampleLog(logs, baseTime.plus(13, ChronoUnit.SECONDS), "DEBUG", "EC2Discovery", "Instance i-1234567890abcdef0 metadata: {InstanceType=t2.micro, State=running}");
        addSampleLog(logs, baseTime.plus(14, ChronoUnit.SECONDS), "INFO", "EC2Discovery", "Processing instance i-2345678901abcdef1");
        addSampleLog(logs, baseTime.plus(15, ChronoUnit.SECONDS), "DEBUG", "EC2Discovery", "Instance i-2345678901abcdef1 metadata: {InstanceType=t2.small, State=running}");
        
        // Add an error
        addSampleLog(logs, baseTime.plus(18, ChronoUnit.SECONDS), "ERROR", "EC2Discovery", "Failed to retrieve tags for instance i-3456789012abcdef2: AccessDeniedException");
        
        // Continue with more instances
        addSampleLog(logs, baseTime.plus(20, ChronoUnit.SECONDS), "INFO", "EC2Discovery", "Processing instance i-4567890123abcdef3");
        addSampleLog(logs, baseTime.plus(21, ChronoUnit.SECONDS), "DEBUG", "EC2Discovery", "Instance i-4567890123abcdef3 metadata: {InstanceType=t2.medium, State=running}");
        addSampleLog(logs, baseTime.plus(22, ChronoUnit.SECONDS), "INFO", "EC2Discovery", "Processing instance i-5678901234abcdef4");
        addSampleLog(logs, baseTime.plus(23, ChronoUnit.SECONDS), "DEBUG", "EC2Discovery", "Instance i-5678901234abcdef4 metadata: {InstanceType=t2.large, State=running}");
        
        // Add service discovery
        addSampleLog(logs, baseTime.plus(25, ChronoUnit.SECONDS), "INFO", "ServiceDiscovery", "Scanning for open ports on discovered instances");
        addSampleLog(logs, baseTime.plus(26, ChronoUnit.SECONDS), "INFO", "ServiceDiscovery", "Detected HTTP (80) on i-1234567890abcdef0");
        addSampleLog(logs, baseTime.plus(27, ChronoUnit.SECONDS), "INFO", "ServiceDiscovery", "Detected HTTPS (443) on i-1234567890abcdef0");
        addSampleLog(logs, baseTime.plus(28, ChronoUnit.SECONDS), "INFO", "ServiceDiscovery", "Detected SSH (22) on i-2345678901abcdef1");
        
        // Complete the discovery
        addSampleLog(logs, baseTime.plus(30, ChronoUnit.SECONDS), "INFO", "Discovery", "EC2 discovery completed for region us-east-1");
        addSampleLog(logs, baseTime.plus(31, ChronoUnit.SECONDS), "INFO", "Discovery", "All regions processed");
        addSampleLog(logs, baseTime.plus(32, ChronoUnit.SECONDS), "INFO", "Discovery", "Discovery completed for provider " + providerId);
        addSampleLog(logs, baseTime.plus(33, ChronoUnit.SECONDS), "INFO", "Discovery", "Summary: 5 EC2 instances discovered");
        
        return logs;
    }
    
    /**
     * Helper method to add a sample log entry
     */
    private void addSampleLog(List<Map<String, Object>> logs, Instant timestamp, String level, String component, String message) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", Date.from(timestamp));
        logEntry.put("level", level);
        logEntry.put("component", component);
        logEntry.put("message", message);
        logs.add(logEntry);
    }
    
    /**
     * Create sample resources for a provider
     */
    private void createSampleResources(String providerId) {
        Set<CloudResource> resources = new HashSet<>();
        
        // Create some sample EC2 instances
        for (int i = 0; i < 5; i++) {
            String instanceId = "i-" + (1234567890 + i) + "abcdef" + i;
            CloudResource resource = new CloudResource();
            resource.setId(instanceId);
            resource.setName("EC2 Instance " + (i + 1));
            resource.setType("EC2");
            resource.setRegion("us-east-1");
            
            // Set different statuses
            if (i < 3) {
                resource.setStatus("running");
            } else if (i == 3) {
                resource.setStatus("stopped");
            } else {
                resource.setStatus("pending");
            }
            
            // Add some properties
            Map<String, Object> properties = new HashMap<>();
            properties.put("InstanceType", "t2." + (i == 0 ? "micro" : i == 1 ? "small" : i == 2 ? "medium" : i == 3 ? "large" : "xlarge"));
            properties.put("AmiId", "ami-" + (12345678 + i));
            properties.put("LaunchTime", Date.from(Instant.now().minus(i + 1, ChronoUnit.DAYS)));
            resource.setProperties(properties);
            
            // Add some tags
            Map<String, String> tags = new HashMap<>();
            tags.put("Name", "Instance " + (i + 1));
            tags.put("Environment", i % 2 == 0 ? "Production" : "Development");
            tags.put("Service", "Demo");
            resource.setTags(tags);
            
            resources.add(resource);
        }
        
        // Store the created resources
        discoveredResources.put(providerId, resources);
        LOG.debug("Created {} sample resources for provider {}", resources.size(), providerId);
    }
}