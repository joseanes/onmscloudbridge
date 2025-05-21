package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.api.CloudResource;
import org.opennms.bridge.api.DiscoveredNode;
import org.opennms.bridge.api.MetricCollection;
import org.opennms.bridge.core.service.OpenNMSClient;
import org.opennms.bridge.webapp.config.BeanConfig;
import org.opennms.bridge.webapp.service.AwsConfigRefresher;
import org.opennms.bridge.webapp.service.IntegrationConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for managing OpenNMS connection settings and status.
 * Also handles integration configuration for scheduling and automation.
 */
@RestController
@RequestMapping("/opennms")
public class OpenNMSController {    
    // In-memory tracking for transfer jobs
    private final Map<String, TransferJob> transferJobs = new ConcurrentHashMap<>();
    
    @Autowired
    private IntegrationConfigService integrationConfigService;
    
    @Autowired
    private List<CloudProvider> cloudProviders;
    
    private static final Logger LOG = LoggerFactory.getLogger(OpenNMSController.class);

    @Autowired
    private OpenNMSClient openNMSClient;
    
    @Autowired
    private BeanConfig beanConfig;
    
    @Autowired
    private AwsConfigRefresher awsConfigRefresher;
    
    @Value("${opennms.base-url}")
    private String baseUrl;
    
    @Value("${opennms.username}")
    private String username;
    
    @Value("${opennms.default-location}")
    private String defaultLocation;

    private boolean isConnected = false;

    /**
     * Get OpenNMS connection information
     */
    @GetMapping("/connection")
    public ResponseEntity<Map<String, Object>> getConnectionInfo() {
        LOG.debug("Getting OpenNMS connection info");
        
        Map<String, Object> connectionInfo = new HashMap<>();
        connectionInfo.put("baseUrl", baseUrl);
        connectionInfo.put("username", username);
        connectionInfo.put("defaultLocation", defaultLocation);
        connectionInfo.put("connectionStatus", isConnected);
        
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
            isConnected = success;
            
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
            isConnected = false;
            result.put("success", false);
            result.put("status", "error");
            result.put("message", "Error testing connection: " + e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Disconnect from OpenNMS
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect() {
        LOG.info("Disconnecting from OpenNMS");
        
        Map<String, Object> result = new HashMap<>();
        isConnected = false;
        
        result.put("success", true);
        result.put("message", "Successfully disconnected from OpenNMS");
        
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
            // Validate settings first
            boolean validationSuccess = true;
            StringBuilder validationErrors = new StringBuilder();
            
            // Required fields check
            if (!settings.containsKey("baseUrl") || settings.get("baseUrl").trim().isEmpty()) {
                validationSuccess = false;
                validationErrors.append("Base URL is required. ");
            }
            if (!settings.containsKey("username") || settings.get("username").trim().isEmpty()) {
                validationSuccess = false;
                validationErrors.append("Username is required. ");
            }
            if (!settings.containsKey("password") || settings.get("password").trim().isEmpty()) {
                validationSuccess = false;
                validationErrors.append("Password is required. ");
            }
            
            if (!validationSuccess) {
                result.put("success", false);
                result.put("message", "Validation failed: " + validationErrors.toString());
                return ResponseEntity.badRequest().body(result);
            }
            
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
     * Validate OpenNMS connection settings
     */
    @PostMapping("/validate-connection")
    public ResponseEntity<Map<String, Object>> validateConnection(@RequestBody Map<String, String> settings) {
        LOG.info("Validating OpenNMS connection settings");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Validate required fields
            if (!settings.containsKey("baseUrl") || settings.get("baseUrl").trim().isEmpty() ||
                !settings.containsKey("username") || settings.get("username").trim().isEmpty() ||
                !settings.containsKey("password") || settings.get("password").trim().isEmpty()) {
                
                result.put("success", false);
                result.put("status", "error");
                result.put("message", "All fields (baseUrl, username, password) are required");
                return ResponseEntity.badRequest().body(result);
            }
            
            // Create a temporary client with the provided settings to test connection
            String baseUrl = settings.get("baseUrl");
            String username = settings.get("username");
            String password = settings.get("password");
            
            // Normalize baseUrl - make sure it ends with a slash and has /opennms added if needed
            if (baseUrl != null) {
                if (!baseUrl.endsWith("/")) {
                    baseUrl = baseUrl + "/";
                }
                if (!baseUrl.contains("/opennms/") && !baseUrl.endsWith("/opennms/")) {
                    baseUrl = baseUrl + "opennms/";
                }
                LOG.info("Normalized OpenNMS base URL for validation: {}", baseUrl);
            }
            
            // Log the test with sanitized info
            LOG.info("Testing connection to OpenNMS at {} with username {}", 
                    baseUrl, username);
            
            // Use the same test logic as the main test endpoint
            boolean success = openNMSClient.testConnection(baseUrl, username, password);
            isConnected = success;
            
            if (success) {
                result.put("success", true);
                result.put("status", "connected");
                result.put("message", "Successfully connected to OpenNMS at " + baseUrl);
            } else {
                result.put("success", false);
                result.put("status", "error");
                result.put("message", "Failed to connect to OpenNMS. Check URL and credentials.");
            }
        } catch (Exception e) {
            LOG.error("Error testing OpenNMS connection", e);
            isConnected = false;
            result.put("success", false);
            result.put("status", "error");
            result.put("message", "Error testing connection: " + e.getMessage());
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
    
    /**
     * Create or update a node in OpenNMS
     * 
     * @param request the node creation request containing resource details
     * @return the response with job ID
     */
    @PostMapping("/nodes")
    public ResponseEntity<Map<String, Object>> createOrUpdateNode(@RequestBody Map<String, Object> request) {
        LOG.info("Creating/updating node in OpenNMS: {}", request);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Verify connection to OpenNMS
            if (!openNMSClient.testConnection()) {
                response.put("success", false);
                response.put("message", "Not connected to OpenNMS. Please check your connection settings.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            // Extract parameters from request
            String providerId = (String) request.get("providerId");
            String resourceId = (String) request.get("resourceId");
            
            if (providerId == null || resourceId == null) {
                response.put("success", false);
                response.put("message", "Provider ID and resource ID are required.");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Find the provider
            CloudProvider provider = findProviderById(providerId);
            if (provider == null) {
                response.put("success", false);
                response.put("message", "Provider not found: " + providerId);
                return ResponseEntity.badRequest().body(response);
            }
            
            // Create a transfer job
            String jobId = UUID.randomUUID().toString();
            TransferJob job = new TransferJob(jobId, providerId, resourceId, TransferType.NODE);
            job.setStatus("RUNNING");
            job.setStartTime(Instant.now());
            job.setMessage("Creating/updating node in OpenNMS...");
            transferJobs.put(jobId, job);
            
            // Async execution to avoid blocking the request
            new Thread(() -> {
                try {
                    // Fetch the resource details
                    Set<CloudResource> resources = provider.discover();
                    CloudResource resource = resources.stream()
                            .filter(r -> resourceId.equals(r.getResourceId()))
                            .findFirst()
                            .orElse(null);
                    
                    if (resource == null) {
                        job.setStatus("FAILED");
                        job.setEndTime(Instant.now());
                        job.setMessage("Resource not found: " + resourceId);
                        LOG.error("Resource not found: {}", resourceId);
                        return;
                    }
                    
                    // Create/update the node in OpenNMS
                    openNMSClient.createOrUpdateNode(resource);
                    
                    // Update job status
                    job.setStatus("COMPLETED");
                    job.setEndTime(Instant.now());
                    job.setMessage("Node created/updated successfully in OpenNMS");
                    
                } catch (Exception e) {
                    LOG.error("Error creating/updating node in OpenNMS", e);
                    job.setStatus("FAILED");
                    job.setEndTime(Instant.now());
                    job.setMessage("Failed to create/update node: " + e.getMessage());
                }
            }).start();
            
            // Return job information
            response.put("success", true);
            response.put("jobId", jobId);
            response.put("providerId", providerId);
            response.put("resourceId", resourceId);
            response.put("status", "RUNNING");
            response.put("message", "Node creation/update job started");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error processing node creation/update request", e);
            response.put("success", false);
            response.put("message", "Error processing request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Send metrics to OpenNMS
     * 
     * @param request the metrics request
     * @return the response with job ID
     */
    @PostMapping("/metrics")
    public ResponseEntity<Map<String, Object>> sendMetrics(@RequestBody Map<String, Object> request) {
        LOG.info("Sending metrics to OpenNMS: {}", request);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Verify connection to OpenNMS
            if (!openNMSClient.testConnection()) {
                response.put("success", false);
                response.put("message", "Not connected to OpenNMS. Please check your connection settings.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            // Extract parameters from request
            String providerId = (String) request.get("providerId");
            String resourceId = (String) request.get("resourceId");
            
            if (providerId == null || resourceId == null) {
                response.put("success", false);
                response.put("message", "Provider ID and resource ID are required.");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Find the provider
            CloudProvider provider = findProviderById(providerId);
            if (provider == null) {
                response.put("success", false);
                response.put("message", "Provider not found: " + providerId);
                return ResponseEntity.badRequest().body(response);
            }
            
            // Create a transfer job
            String jobId = UUID.randomUUID().toString();
            TransferJob job = new TransferJob(jobId, providerId, resourceId, TransferType.METRICS);
            job.setStatus("RUNNING");
            job.setStartTime(Instant.now());
            job.setMessage("Collecting and sending metrics to OpenNMS...");
            transferJobs.put(jobId, job);
            
            // Async execution to avoid blocking the request
            new Thread(() -> {
                try {
                    // Discover the resource
                    Set<CloudResource> resources = provider.discover();
                    CloudResource resource = resources.stream()
                            .filter(r -> resourceId.equals(r.getResourceId()))
                            .findFirst()
                            .orElse(null);
                    
                    if (resource == null) {
                        job.setStatus("FAILED");
                        job.setEndTime(Instant.now());
                        job.setMessage("Resource not found: " + resourceId);
                        LOG.error("Resource not found: {}", resourceId);
                        return;
                    }
                    
                    // Collect metrics
                    MetricCollection metrics = provider.collect(resource);
                    if (metrics == null || metrics.getMetrics().isEmpty()) {
                        job.setStatus("FAILED");
                        job.setEndTime(Instant.now());
                        job.setMessage("No metrics collected for resource: " + resourceId);
                        LOG.error("No metrics collected for resource: {}", resourceId);
                        return;
                    }
                    
                    // Find the node ID in OpenNMS
                    String foreignId = providerId + ":" + resourceId;
                    String nodeId = openNMSClient.findNodeByForeignId(providerId, foreignId);
                    
                    if (nodeId == null) {
                        // Node doesn't exist, create it first
                        openNMSClient.createOrUpdateNode(resource);
                        // Try to get the node ID again
                        nodeId = openNMSClient.findNodeByForeignId(providerId, foreignId);
                        
                        if (nodeId == null) {
                            job.setStatus("FAILED");
                            job.setEndTime(Instant.now());
                            job.setMessage("Failed to get node ID from OpenNMS. Node creation might have failed.");
                            LOG.error("Failed to get node ID from OpenNMS for resource: {}", resourceId);
                            return;
                        }
                    }
                    
                    // Send metrics to OpenNMS
                    openNMSClient.submitMetrics(nodeId, metrics);
                    
                    // Update job status
                    job.setStatus("COMPLETED");
                    job.setEndTime(Instant.now());
                    job.setMetricCount(metrics.getMetrics().size());
                    job.setMessage("Metrics sent successfully to OpenNMS");
                    
                } catch (Exception e) {
                    LOG.error("Error sending metrics to OpenNMS", e);
                    job.setStatus("FAILED");
                    job.setEndTime(Instant.now());
                    job.setMessage("Failed to send metrics: " + e.getMessage());
                }
            }).start();
            
            // Return job information
            response.put("success", true);
            response.put("jobId", jobId);
            response.put("providerId", providerId);
            response.put("resourceId", resourceId);
            response.put("status", "RUNNING");
            response.put("message", "Metrics collection and transfer job started");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error processing metrics request", e);
            response.put("success", false);
            response.put("message", "Error processing request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get the status of a transfer job
     * 
     * @param jobId the job ID
     * @return the job status
     */
    @GetMapping("/transfers/{jobId}")
    public ResponseEntity<Map<String, Object>> getTransferStatus(@PathVariable String jobId) {
        LOG.debug("Getting transfer job status: {}", jobId);
        
        TransferJob job = transferJobs.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getJobId());
        response.put("providerId", job.getProviderId());
        response.put("resourceId", job.getResourceId());
        response.put("type", job.getType().toString());
        response.put("status", job.getStatus());
        response.put("message", job.getMessage());
        response.put("startTime", job.getStartTime());
        
        if (job.getEndTime() != null) {
            response.put("endTime", job.getEndTime());
        }
        
        if (job.getMetricCount() > 0) {
            response.put("metricCount", job.getMetricCount());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all transfer jobs
     * 
     * @return list of all transfer jobs
     */
    @GetMapping("/transfers")
    public ResponseEntity<Map<String, Object>> getAllTransfers() {
        LOG.debug("Getting all transfer jobs");
        
        List<Map<String, Object>> jobsList = new ArrayList<>();
        
        for (TransferJob job : transferJobs.values()) {
            Map<String, Object> jobData = new HashMap<>();
            jobData.put("jobId", job.getJobId());
            jobData.put("providerId", job.getProviderId());
            jobData.put("resourceId", job.getResourceId());
            jobData.put("type", job.getType().toString());
            jobData.put("status", job.getStatus());
            jobData.put("message", job.getMessage());
            jobData.put("startTime", job.getStartTime());
            
            if (job.getEndTime() != null) {
                jobData.put("endTime", job.getEndTime());
            }
            
            if (job.getMetricCount() > 0) {
                jobData.put("metricCount", job.getMetricCount());
            }
            
            jobsList.add(jobData);
        }
        
        // Sort by start time (newest first)
        jobsList.sort((j1, j2) -> {
            Instant t1 = (Instant) j1.get("startTime");
            Instant t2 = (Instant) j2.get("startTime");
            return t2.compareTo(t1);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("jobs", jobsList);
        response.put("count", jobsList.size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get the OpenNMS integration configuration
     * 
     * @return the integration configuration
     */
    @GetMapping("/integration-config")
    public ResponseEntity<IntegrationConfig> getIntegrationConfig() {
        LOG.debug("Getting OpenNMS integration configuration");
        return ResponseEntity.ok(integrationConfigService.getConfig());
    }
    
    /**
     * Update the OpenNMS integration configuration
     * 
     * @param config the new configuration
     * @return the updated configuration
     */
    @PostMapping("/integration-config")
    public ResponseEntity<Map<String, Object>> updateIntegrationConfig(@RequestBody IntegrationConfig config) {
        LOG.info("Updating OpenNMS integration configuration: {}", config);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate config values
            if (config.getDiscoveryFrequency() < 1) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Discovery frequency must be at least 1 minute"
                ));
            }
            if (config.getCollectionFrequency() < 1) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Collection frequency must be at least 1 minute"
                ));
            }
            if (config.getNodeSyncFrequency() < 1) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Node sync frequency must be at least 1 minute"
                ));
            }
            if (config.getMetricsSendFrequency() < 1) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Metrics send frequency must be at least 1 minute"
                ));
            }
            
            // Update configuration in the service (which saves it to disk)
            integrationConfigService.updateConfig(config);
            
            // Update mock providers setting
            LOG.info("Setting mock providers to: {}", !config.isDisableMockProviders());
            beanConfig.setUseMockProviders(!config.isDisableMockProviders());
            
            // Update emergency bypass to match mock providers setting
            // When mock providers are disabled, emergency bypass should also be disabled
            // When mock providers are enabled, emergency bypass should be enabled
            awsConfigRefresher.refreshEmergencyBypassSetting(config.isDisableMockProviders() == false);
            awsConfigRefresher.refreshAwsConfiguration();
            
            // BeanConfig will automatically update the active providers
            // This updates the same list that's injected everywhere
            
            response.put("success", true);
            response.put("message", "Integration configuration updated successfully");
            response.put("config", config);
            response.put("mockProviderStatus", !config.isDisableMockProviders() ? "enabled" : "disabled");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error updating integration configuration", e);
            
            response.put("success", false);
            response.put("message", "Error updating configuration: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Helper method to find provider by ID
     * 
     * @param providerId the provider ID
     * @return the provider or null if not found
     */
    private CloudProvider findProviderById(String providerId) {
        if (providerId == null) {
            return null;
        }
        
        return cloudProviders.stream()
                .filter(p -> providerId.equals(p.getProviderId()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Transfer job type
     */
    private enum TransferType {
        NODE,
        METRICS
    }
    
    /**
     * Integration Configuration class
     */
    public static class IntegrationConfig {
        private int discoveryFrequency;  // minutes
        private int collectionFrequency; // minutes
        private int nodeSyncFrequency;   // minutes
        private int metricsSendFrequency; // minutes
        private boolean enableAutoNodeSync;
        private boolean enableAutoMetricsSend;
        private boolean disableMockProviders; // Use real providers only when enabled
        
        public IntegrationConfig() {
            // Default constructor
        }
        
        public IntegrationConfig(
                int discoveryFrequency, 
                int collectionFrequency, 
                int nodeSyncFrequency, 
                int metricsSendFrequency, 
                boolean enableAutoNodeSync, 
                boolean enableAutoMetricsSend,
                boolean disableMockProviders) {
            this.discoveryFrequency = discoveryFrequency;
            this.collectionFrequency = collectionFrequency;
            this.nodeSyncFrequency = nodeSyncFrequency;
            this.metricsSendFrequency = metricsSendFrequency;
            this.enableAutoNodeSync = enableAutoNodeSync;
            this.enableAutoMetricsSend = enableAutoMetricsSend;
            this.disableMockProviders = disableMockProviders;
        }

        public int getDiscoveryFrequency() {
            return discoveryFrequency;
        }

        public void setDiscoveryFrequency(int discoveryFrequency) {
            this.discoveryFrequency = discoveryFrequency;
        }

        public int getCollectionFrequency() {
            return collectionFrequency;
        }

        public void setCollectionFrequency(int collectionFrequency) {
            this.collectionFrequency = collectionFrequency;
        }

        public int getNodeSyncFrequency() {
            return nodeSyncFrequency;
        }

        public void setNodeSyncFrequency(int nodeSyncFrequency) {
            this.nodeSyncFrequency = nodeSyncFrequency;
        }

        public int getMetricsSendFrequency() {
            return metricsSendFrequency;
        }

        public void setMetricsSendFrequency(int metricsSendFrequency) {
            this.metricsSendFrequency = metricsSendFrequency;
        }

        public boolean isEnableAutoNodeSync() {
            return enableAutoNodeSync;
        }

        public void setEnableAutoNodeSync(boolean enableAutoNodeSync) {
            this.enableAutoNodeSync = enableAutoNodeSync;
        }

        public boolean isEnableAutoMetricsSend() {
            return enableAutoMetricsSend;
        }

        public void setEnableAutoMetricsSend(boolean enableAutoMetricsSend) {
            this.enableAutoMetricsSend = enableAutoMetricsSend;
        }
        
        public boolean isDisableMockProviders() {
            return disableMockProviders;
        }
        
        public void setDisableMockProviders(boolean disableMockProviders) {
            this.disableMockProviders = disableMockProviders;
        }
    }
    
    /**
     * Get the current mock providers setting
     * 
     * @return the mock providers setting
     */
    @GetMapping("/mock-providers")
    public ResponseEntity<Map<String, Object>> getMockProvidersSetting() {
        LOG.debug("Getting mock providers setting");
        
        Map<String, Object> result = new HashMap<>();
        result.put("useMockProviders", beanConfig.getUseMockProviders());
        return ResponseEntity.ok(result);
    }
    
    /**
     * Update the mock providers setting
     * 
     * @param settings the settings with useMockProviders boolean
     * @return the updated settings
     */
    @PostMapping("/mock-providers")
    public ResponseEntity<Map<String, Object>> updateMockProvidersSetting(@RequestBody Map<String, Object> settings) {
        LOG.info("Updating mock providers setting: {}", settings);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Check if the setting is provided
            if (!settings.containsKey("useMockProviders")) {
                result.put("success", false);
                result.put("message", "useMockProviders setting is required");
                return ResponseEntity.badRequest().body(result);
            }
            
            // Get the setting value
            boolean useMockProviders = (boolean) settings.get("useMockProviders");
            
            // Update the setting
            boolean updated = beanConfig.setUseMockProviders(useMockProviders);
            
            // Prepare response
            result.put("success", true);
            result.put("useMockProviders", updated);
            result.put("message", "Mock providers setting updated successfully to: " + updated);
            result.put("note", "Application restart required for changes to take effect");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.error("Error updating mock providers setting", e);
            result.put("success", false);
            result.put("message", "Error updating setting: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * Transfer job tracking class
     */
    private static class TransferJob {
        private final String jobId;
        private final String providerId;
        private final String resourceId;
        private final TransferType type;
        private String status;
        private String message;
        private Instant startTime;
        private Instant endTime;
        private int metricCount;
        
        public TransferJob(String jobId, String providerId, String resourceId, TransferType type) {
            this.jobId = jobId;
            this.providerId = providerId;
            this.resourceId = resourceId;
            this.type = type;
        }
        
        public String getJobId() {
            return jobId;
        }
        
        public String getProviderId() {
            return providerId;
        }
        
        public String getResourceId() {
            return resourceId;
        }
        
        public TransferType getType() {
            return type;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public Instant getStartTime() {
            return startTime;
        }
        
        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }
        
        public Instant getEndTime() {
            return endTime;
        }
        
        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }
        
        public int getMetricCount() {
            return metricCount;
        }
        
        public void setMetricCount(int metricCount) {
            this.metricCount = metricCount;
        }
    }
    
}