package org.opennms.bridge.webapp.controller;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.api.CloudResource;
import org.opennms.bridge.api.ValidationResult;
import org.opennms.bridge.aws.AwsCloudProvider;
import org.opennms.bridge.aws.AwsConfigurationProperties;
import org.opennms.bridge.aws.AwsDiscoveryStrategy;
import org.opennms.bridge.aws.AwsMetricCollector;
import org.opennms.bridge.webapp.config.BeanConfig;
import org.opennms.bridge.webapp.service.CredentialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for managing cloud providers.
 */
@RestController
@RequestMapping("/api/cloud-providers")
public class CloudProviderController {
    private static final Logger LOG = LoggerFactory.getLogger(CloudProviderController.class);

    @Autowired
    private List<CloudProvider> cloudProviders;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private BeanConfig beanConfig;
    
    @Autowired
    private AwsDiscoveryStrategy awsDiscoveryStrategy;
    
    @Autowired
    private AwsMetricCollector awsMetricCollector;
    
    @Autowired
    private CredentialService credentialService;
    
    @Value("${bridge.debug.aws.log_directory:logs/aws}")
    private String debugLogDirectory;
    
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllCloudProviders() {
        List<Map<String, Object>> providerData = cloudProviders.stream()
                .map(provider -> {
                    try {
                        // Convert provider to a map of properties for the API
                        Map<String, Object> data = provider.getConfiguration();
                        
                        // Add basic provider information
                        data.put("id", provider.getProviderId());
                        data.put("type", provider.getProviderType());
                        data.put("name", provider.getDisplayName());
                        
                        // Add validation status
                        try {
                            ValidationResult validationResult = provider.validate();
                            data.put("valid", validationResult.isValid());
                            data.put("validationMessage", validationResult.getMessage());
                        } catch (Exception e) {
                            data.put("valid", false);
                            data.put("validationMessage", "Validation error: " + e.getMessage());
                        }
                        
                        // Add provider capabilities
                        data.put("supportedMetrics", provider.getSupportedMetrics());
                        data.put("availableRegions", provider.getAvailableRegions());
                        
                        return data;
                    } catch (Exception e) {
                        // Log error and return basic information
                        Map<String, Object> data = Map.of(
                                "id", provider.getProviderId(),
                                "type", provider.getProviderType(),
                                "name", provider.getDisplayName(),
                                "valid", false,
                                "validationMessage", "Error loading provider details: " + e.getMessage()
                        );
                        return data;
                    }
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(providerData);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCloudProvider(@PathVariable String id) {
        return cloudProviders.stream()
                .filter(provider -> provider.getProviderId().equals(id))
                .findFirst()
                .map(provider -> {
                    try {
                        // Convert provider to a map of properties for the API
                        Map<String, Object> data = provider.getConfiguration();
                        
                        // Add basic provider information
                        data.put("id", provider.getProviderId());
                        data.put("type", provider.getProviderType());
                        data.put("name", provider.getDisplayName());
                        
                        // Add validation status
                        try {
                            ValidationResult validationResult = provider.validate();
                            data.put("valid", validationResult.isValid());
                            data.put("validationMessage", validationResult.getMessage());
                        } catch (Exception e) {
                            data.put("valid", false);
                            data.put("validationMessage", "Validation error: " + e.getMessage());
                        }
                        
                        // Add provider capabilities
                        data.put("supportedMetrics", provider.getSupportedMetrics());
                        data.put("availableRegions", provider.getAvailableRegions());
                        
                        return ResponseEntity.ok(data);
                    } catch (Exception e) {
                        // Log error and return basic information
                        Map<String, Object> data = Map.of(
                                "id", provider.getProviderId(),
                                "type", provider.getProviderType(),
                                "name", provider.getDisplayName(),
                                "valid", false,
                                "validationMessage", "Error loading provider details: " + e.getMessage()
                        );
                        return ResponseEntity.ok(data);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/{id}/validate")
    public ResponseEntity<Map<String, Object>> validateCloudProvider(@PathVariable String id) {
        return cloudProviders.stream()
                .filter(provider -> provider.getProviderId().equals(id))
                .findFirst()
                .map(provider -> {
                    try {
                        ValidationResult result = provider.validate();
                        Map<String, Object> response = Map.of(
                                "id", provider.getProviderId(),
                                "valid", result.isValid(),
                                "message", result.getMessage()
                        );
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        Map<String, Object> response = Map.of(
                                "id", provider.getProviderId(),
                                "valid", false,
                                "message", "Validation error: " + e.getMessage()
                        );
                        return ResponseEntity.ok(response);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> createCloudProvider(@Valid @RequestBody Map<String, Object> providerConfig) {
        try {
            // Get provider type from config
            String providerType = (String) providerConfig.getOrDefault("type", "");
            
            // Generate a unique provider ID if not provided
            if (!providerConfig.containsKey("providerId")) {
                String providerId = UUID.randomUUID().toString();
                providerConfig.put("providerId", providerId);
            }
            
            CloudProvider newProvider = null;
            
            // Create provider based on type
            switch (providerType) {
                case "aws":
                    newProvider = createAwsCloudProvider(providerConfig);
                    break;
                // Add other provider types as needed
                default:
                    return ResponseEntity.badRequest()
                            .body(Map.of(
                                "error", "Unsupported provider type: " + providerType,
                                "supportedTypes", List.of("aws")
                            ));
            }
            
            // Add to providers list
            cloudProviders.add(newProvider);
            
            // Get provider configuration for response
            Map<String, Object> createdConfig = newProvider.getConfiguration();
            createdConfig.put("id", newProvider.getProviderId());
            createdConfig.put("type", newProvider.getProviderType());
            createdConfig.put("name", newProvider.getDisplayName());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(createdConfig);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create provider: " + e.getMessage()));
        }
    }
    
    /**
     * Tests an AWS connection with the given configuration.
     * 
     * @param config Configuration to test
     * @return Validation result with detailed status information
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@Valid @RequestBody Map<String, Object> config) {
        try {
            // Get provider type from config
            String providerType = (String) config.getOrDefault("type", "");
            
            // Validate provider type
            if (providerType == null || providerType.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                            "valid", false,
                            "message", "Missing provider type",
                            "step", "initialization",
                            "supportedTypes", List.of("aws")
                        ));
            }
            
            // Create a temporary provider ID if not provided
            if (!config.containsKey("providerId")) {
                config.put("providerId", "temp-" + UUID.randomUUID().toString());
            }
            
            // Create a temporary provider to test
            CloudProvider tempProvider = null;
            
            try {
                switch (providerType) {
                    case "aws":
                        tempProvider = createAwsCloudProvider(config);
                        break;
                    default:
                        return ResponseEntity.badRequest()
                                .body(Map.of(
                                    "valid", false,
                                    "message", "Unsupported provider type: " + providerType,
                                    "step", "initialization",
                                    "supportedTypes", List.of("aws")
                                ));
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                            "valid", false,
                            "message", "Failed to initialize provider: " + e.getMessage(),
                            "step", "initialization"
                        ));
            }
            
            // Validate the provider
            ValidationResult result;
            try {
                result = tempProvider.validate();
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                            "valid", false,
                            "message", "Validation process failed: " + e.getMessage(),
                            "step", "validation"
                        ));
            } finally {
                // Clean up temporary provider
                if (tempProvider != null) {
                    try {
                        tempProvider.close();
                    } catch (Exception e) {
                        // Log but continue
                        e.printStackTrace();
                    }
                }
                
                // Clean up any temporary credentials
                try {
                    String providerId = (String) config.get("providerId");
                    if (providerId != null && providerId.startsWith("temp-")) {
                        credentialService.removeAllCredentials(providerId);
                    }
                } catch (Exception e) {
                    // Log but continue
                    e.printStackTrace();
                }
            }
            
            // Determine which step failed based on error message
            String step = "success";
            if (!result.isValid()) {
                String errorMsg = result.getMessage();
                if (errorMsg.contains("SDK initialization")) {
                    step = "sdk_initialization";
                } else if (errorMsg.contains("credentials") || errorMsg.contains("Authentication")) {
                    step = "credential_validation";
                } else if (errorMsg.contains("Network connectivity")) {
                    step = "network_connectivity";
                } else {
                    step = "service_validation";
                }
            }
            
            // Return validation result with step information
            Map<String, Object> response = new HashMap<>();
            response.put("valid", result.isValid());
            response.put("message", result.getMessage());
            response.put("step", step);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "valid", false,
                        "message", "Connection test failed: " + e.getMessage(),
                        "step", "unexpected_error"
                    ));
        }
    }
    
    private CloudProvider createAwsCloudProvider(Map<String, Object> config) {
        try {
            LOG.info("Creating new AWS cloud provider with config: {}", config.keySet());
            
            // Create new configuration properties
            AwsConfigurationProperties awsConfig = new AwsConfigurationProperties();
            
            // Apply basic configuration
            String providerId = (String) config.getOrDefault("providerId", UUID.randomUUID().toString());
            awsConfig.setProviderId(providerId);
            LOG.debug("Setting provider ID: {}", providerId);
            
            if (config.containsKey("displayName")) {
                String displayName = (String) config.get("displayName");
                awsConfig.setDisplayName(displayName);
                LOG.debug("Setting display name: {}", displayName);
            }
            
            // Securely handle credentials
            if (config.containsKey("accessKeyId")) {
                String accessKeyId = (String) config.get("accessKeyId");
                // Store in secure credential service
                credentialService.storeCredential(providerId, "aws-access-key", accessKeyId);
                // Set in provider config for immediate use
                awsConfig.setAccessKeyId(accessKeyId);
                LOG.debug("Access key ID stored securely for provider ID: {}", providerId);
                // Remove from configuration map for security
                config.remove("accessKeyId");
            }
            
            if (config.containsKey("secretAccessKey")) {
                String secretAccessKey = (String) config.get("secretAccessKey");
                // Store in secure credential service
                credentialService.storeCredential(providerId, "aws-secret-key", secretAccessKey);
                // Set in provider config for immediate use
                awsConfig.setSecretAccessKey(secretAccessKey);
                LOG.debug("Secret access key stored securely for provider ID: {}", providerId);
                // Remove from configuration map for security
                config.remove("secretAccessKey");
            }
            
            if (config.containsKey("sessionToken")) {
                String sessionToken = (String) config.get("sessionToken");
                // Store in secure credential service
                credentialService.storeCredential(providerId, "aws-session-token", sessionToken);
                // Set in provider config for immediate use
                awsConfig.setSessionToken(sessionToken);
                LOG.debug("Session token stored securely for provider ID: {}", providerId);
                // Remove from configuration map for security
                config.remove("sessionToken");
            }
            
            if (config.containsKey("roleArn")) {
                String roleArn = (String) config.get("roleArn");
                awsConfig.setRoleArn(roleArn);
                LOG.debug("Role ARN set: {}", roleArn);
            }
            
            // Set regions
            if (config.containsKey("regions")) {
                try {
                    @SuppressWarnings("unchecked")
                    List<String> regions = (List<String>) config.get("regions");
                    if (regions != null && !regions.isEmpty()) {
                        awsConfig.setRegions(regions);
                        LOG.debug("Setting regions: {}", regions);
                    }
                } catch (Exception e) {
                    LOG.warn("Error setting regions: {}", e.getMessage());
                }
            }
            
            // Apply EC2 discovery configuration
            if (config.containsKey("ec2Discovery")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ec2Config = (Map<String, Object>) config.get("ec2Discovery");
                
                if (ec2Config.containsKey("enabled")) {
                    awsConfig.getEc2Discovery().setEnabled((Boolean) ec2Config.get("enabled"));
                }
                if (ec2Config.containsKey("includeTags")) {
                    @SuppressWarnings("unchecked")
                    List<String> includeTags = (List<String>) ec2Config.get("includeTags");
                    awsConfig.getEc2Discovery().setIncludeTags(includeTags);
                }
                if (ec2Config.containsKey("filterByTags")) {
                    @SuppressWarnings("unchecked")
                    List<String> filterByTags = (List<String>) ec2Config.get("filterByTags");
                    awsConfig.getEc2Discovery().setFilterByTags(filterByTags);
                }
                if (ec2Config.containsKey("instanceStates")) {
                    @SuppressWarnings("unchecked")
                    List<String> instanceStates = (List<String>) ec2Config.get("instanceStates");
                    awsConfig.getEc2Discovery().setInstanceStates(instanceStates);
                }
            }
            
            // Apply CloudWatch collection configuration
            if (config.containsKey("cloudWatchCollection")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cloudWatchConfig = (Map<String, Object>) config.get("cloudWatchCollection");
                
                if (cloudWatchConfig.containsKey("enabled")) {
                    awsConfig.getCloudWatchCollection().setEnabled((Boolean) cloudWatchConfig.get("enabled"));
                }
                if (cloudWatchConfig.containsKey("metrics")) {
                    @SuppressWarnings("unchecked")
                    List<String> metrics = (List<String>) cloudWatchConfig.get("metrics");
                    awsConfig.getCloudWatchCollection().setMetrics(metrics);
                }
                if (cloudWatchConfig.containsKey("statistics")) {
                    @SuppressWarnings("unchecked")
                    List<String> statistics = (List<String>) cloudWatchConfig.get("statistics");
                    awsConfig.getCloudWatchCollection().setStatistics(statistics);
                }
            }
            
            // Create a new provider instance using the factory method
            AwsCloudProvider provider = beanConfig.createAwsCloudProvider(
                    awsDiscoveryStrategy, 
                    awsMetricCollector);
            LOG.info("Provider created successfully, ID: {}", provider.getProviderId());
            
            // Create a copied map of the config with proper types
            Map<String, Object> safeConfig = new HashMap<>();
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                safeConfig.put(entry.getKey(), entry.getValue());
            }
            
            // Apply configuration to the provider
            try {
                LOG.info("Updating provider configuration with keys: {}", safeConfig.keySet());
                provider.updateConfiguration(safeConfig);
                LOG.info("Provider configuration updated successfully");
            } catch (Exception e) {
                // Log detailed error information
                LOG.error("Failed to configure AWS provider: {}", e.getMessage(), e);
                // Close the provider to release resources
                try {
                    provider.close();
                } catch (Exception ex) {
                    // Log and continue
                    LOG.error("Error closing provider during error handling", ex);
                }
                throw new RuntimeException("Failed to configure AWS provider: " + e.getMessage(), e);
            }
            
            return provider;
        } catch (Exception e) {
            LOG.error("Error creating AWS provider: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create AWS provider: " + e.getMessage(), e);
        }
    }
    
    private void emergencyDebugLog(String message, Exception e) {
        // Ensure log directory exists
        File logDir = new File(debugLogDirectory);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        String timestamp = new Date().toString();
        String threadInfo = Thread.currentThread().getName() + ":" + Thread.currentThread().getId();
        String fullMessage = "[" + timestamp + "][" + threadInfo + "] " + message;
        
        // Write to system console
        System.out.println("\n\n******************** EMERGENCY DEBUG LOG ********************");
        System.out.println(fullMessage);
        if (e != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            System.out.println("EXCEPTION: " + e.getMessage());
            System.out.println(sw.toString());
        }
        System.out.println("***********************************************************\n\n");
        
        // Also write to a separate log file
        try {
            String logFilePath = new File(debugLogDirectory, "aws-debug.log").getPath();
            PrintWriter writer = new PrintWriter(new FileWriter(logFilePath, true));
            writer.println("\n\n******************** EMERGENCY DEBUG LOG ********************");
            writer.println(fullMessage);
            if (e != null) {
                e.printStackTrace(writer);
            }
            writer.println("***********************************************************\n\n");
            writer.close();
        } catch (Exception logEx) {
            System.err.println("Failed to write to emergency debug log: " + logEx.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCloudProvider(
            @PathVariable String id, 
            @Valid @RequestBody Map<String, Object> providerConfig) {
        
        emergencyDebugLog("UPDATE CLOUD PROVIDER CALLED - ID: " + id, null);
        emergencyDebugLog("PROVIDER CONFIG: " + providerConfig, null);
        
        try {
            // Get provider
            CloudProvider provider = null;
            for (CloudProvider p : cloudProviders) {
                if (p.getProviderId().equals(id)) {
                    provider = p;
                    break;
                }
            }
            
            if (provider == null) {
                emergencyDebugLog("PROVIDER NOT FOUND: " + id, null);
                return ResponseEntity.notFound().build();
            }
            
            emergencyDebugLog("FOUND PROVIDER: " + provider.getDisplayName() + " (" + provider.getClass().getName() + ")", null);
            
            // Make a safety copy of the config
            Map<String, Object> safeConfig = new HashMap<>(providerConfig);
            emergencyDebugLog("SAFE CONFIG CREATED WITH KEYS: " + safeConfig.keySet(), null);
            
            try {
                // Update provider configuration
                emergencyDebugLog("CALLING updateConfiguration ON PROVIDER", null);
                provider.updateConfiguration(safeConfig);
                emergencyDebugLog("PROVIDER CONFIGURATION UPDATED SUCCESSFULLY", null);
                
                // Get updated configuration
                Map<String, Object> updatedConfig = provider.getConfiguration();
                updatedConfig.put("id", provider.getProviderId());
                updatedConfig.put("type", provider.getProviderType());
                updatedConfig.put("name", provider.getDisplayName());
                
                emergencyDebugLog("RETURNING SUCCESSFUL RESPONSE", null);
                return ResponseEntity.ok(updatedConfig);
            } catch (Exception e) {
                emergencyDebugLog("ERROR UPDATING PROVIDER CONFIGURATION", e);
                
                // Try to get as much debug info as possible about the provider
                emergencyDebugLog("PROVIDER DETAILS:", null);
                emergencyDebugLog("  Provider ID: " + provider.getProviderId(), null);
                emergencyDebugLog("  Provider Type: " + provider.getProviderType(), null);
                emergencyDebugLog("  Display Name: " + provider.getDisplayName(), null);
                
                try {
                    emergencyDebugLog("  Provider Class: " + provider.getClass().getName(), null);
                    emergencyDebugLog("  Provider String: " + provider.toString(), null);
                } catch (Exception debugEx) {
                    emergencyDebugLog("  Error getting provider details: " + debugEx.getMessage(), debugEx);
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("id", provider.getProviderId());
                response.put("error", "Failed to update provider configuration: " + e.getMessage());
                
                // Add exception details
                response.put("exceptionClass", e.getClass().getName());
                if (e.getCause() != null) {
                    response.put("rootCauseClass", e.getCause().getClass().getName());
                    response.put("rootCauseMessage", e.getCause().getMessage());
                }
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (Exception outerEx) {
            emergencyDebugLog("CATASTROPHIC ERROR IN updateCloudProvider", outerEx);
            Map<String, Object> response = Map.of(
                    "id", id,
                    "error", "Unexpected error: " + outerEx.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCloudProvider(@PathVariable String id) {
        boolean removed = cloudProviders.removeIf(provider -> provider.getProviderId().equals(id));
        
        if (removed) {
            // Clean up any stored credentials
            credentialService.removeAllCredentials(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Emergency diagnostic endpoint for AWS debugging.
     * This bypasses normal logging and writes debug information directly to console and files.
     */
    @GetMapping("/debug-aws")
    public ResponseEntity<Map<String, Object>> debugAws() {
        Map<String, Object> debugInfo = new HashMap<>();
        
        try {
            // Ensure log directory exists
            File logDir = new File(debugLogDirectory);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // Create a direct debug log file
            String debugPath = new File(debugLogDirectory, "aws-controller-debug.log").getPath();
            PrintWriter writer = new PrintWriter(new FileWriter(debugPath, true));
            
            // Log header
            String timestamp = new Date().toString();
            String header = "\n\n====== AWS DEBUG DUMP - " + timestamp + " ======\n";
            writer.println(header);
            System.err.println(header);
            
            // Log providers
            writer.println("[CloudProviders Available: " + cloudProviders.size() + "]");
            System.err.println("[CloudProviders Available: " + cloudProviders.size() + "]");
            
            // Log each provider
            int index = 0;
            for (CloudProvider provider : cloudProviders) {
                String providerInfo = "Provider " + index + ": " + 
                                     " ID=" + provider.getProviderId() +
                                     " Type=" + provider.getProviderType() +
                                     " Name=" + provider.getDisplayName() +
                                     " Class=" + provider.getClass().getName();
                writer.println(providerInfo);
                System.err.println(providerInfo);
                
                try {
                    // Try to get a dump of provider config
                    Map<String, Object> config = provider.getConfiguration();
                    writer.println("  - Configuration keys: " + config.keySet());
                    for (String key : config.keySet()) {
                        Object value = config.get(key);
                        if (!(key.contains("Key") || key.contains("Secret") || key.contains("Token"))) {
                            writer.println("  - " + key + ": " + value);
                        }
                    }
                } catch (Exception e) {
                    writer.println("  - Error getting configuration: " + e.getMessage());
                }
                
                index++;
            }
            
            // Log environment info
            writer.println("\n[Java]");
            writer.println("Version: " + System.getProperty("java.version"));
            writer.println("VM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
            writer.println("Classpath: " + System.getProperty("java.class.path"));
            
            writer.println("\n[AWS Environment]");
            writer.println("AWS_ACCESS_KEY_ID: " + (System.getenv("AWS_ACCESS_KEY_ID") != null ? "Set" : "Not set"));
            writer.println("AWS_SECRET_ACCESS_KEY: " + (System.getenv("AWS_SECRET_ACCESS_KEY") != null ? "Set" : "Not set"));
            writer.println("AWS_SESSION_TOKEN: " + (System.getenv("AWS_SESSION_TOKEN") != null ? "Set" : "Not set"));
            writer.println("AWS_REGION: " + System.getenv("AWS_REGION"));
            writer.println("AWS_DEFAULT_REGION: " + System.getenv("AWS_DEFAULT_REGION"));
            
            writer.println("\n[System]");
            writer.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            writer.println("User: " + System.getProperty("user.name"));
            writer.println("Working dir: " + System.getProperty("user.dir"));
            
            // Close the debug log
            writer.println("\n====== END AWS DEBUG DUMP ======\n");
            writer.close();
            
            // Add debug info for response
            debugInfo.put("timestamp", timestamp);
            debugInfo.put("providersCount", cloudProviders.size());
            debugInfo.put("javaVersion", System.getProperty("java.version"));
            debugInfo.put("osVersion", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            debugInfo.put("debugLogPath", debugPath);
            debugInfo.put("message", "Debug information has been written to " + debugPath + " and stderr");
            
            return ResponseEntity.ok(debugInfo);
        } catch (Exception e) {
            debugInfo.put("error", "Error generating debug information: " + e.getMessage());
            System.err.println("ERROR IN DEBUG ENDPOINT: " + e.getMessage());
            e.printStackTrace(System.err);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(debugInfo);
        }
    }
    
    @PutMapping("/{id}/display-name")
    public ResponseEntity<Map<String, Object>> updateDisplayName(
            @PathVariable String id,
            @Valid @RequestBody Map<String, Object> request) {
        
        // Check if the request contains the display name
        if (!request.containsKey("displayName")) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Display name is required"
            ));
        }
        
        String displayName = (String) request.get("displayName");
        if (displayName == null || displayName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Display name cannot be empty"
            ));
        }
        
        // Find the provider
        CloudProvider provider = null;
        for (CloudProvider p : cloudProviders) {
            if (p.getProviderId().equals(id)) {
                provider = p;
                break;
            }
        }
        
        if (provider == null) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            // Create a simple update map with just the display name
            Map<String, Object> updateConfig = new HashMap<>();
            updateConfig.put("displayName", displayName);
            
            // Update the provider configuration
            provider.updateConfiguration(updateConfig);
            
            // Return success with the updated display name
            return ResponseEntity.ok(Map.of(
                "id", provider.getProviderId(),
                "displayName", provider.getDisplayName(),
                "message", "Display name updated successfully"
            ));
        } catch (Exception e) {
            LOG.error("Error updating display name: {}", e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "id", provider.getProviderId(),
                "error", "Failed to update display name: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Gets all resources from a specific cloud provider.
     * This endpoint allows the frontend to display detailed resource information.
     * 
     * @param id The provider ID
     * @return List of cloud resources
     */
    @GetMapping("/{id}/resources")
    public ResponseEntity<List<Map<String, Object>>> getProviderResources(@PathVariable String id) {
        LOG.info("Getting resources for provider: {}", id);
        
        // Find the provider
        CloudProvider provider = null;
        for (CloudProvider p : cloudProviders) {
            if (p.getProviderId().equals(id)) {
                provider = p;
                break;
            }
        }
        
        if (provider == null) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            // Discover resources for this provider
            Set<CloudResource> resources = provider.discover();
            
            // Convert resources to maps
            List<Map<String, Object>> resourceData = resources.stream()
                    .map(resource -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", resource.getResourceId());
                        data.put("name", resource.getDisplayName());
                        data.put("type", resource.getResourceType());
                        data.put("status", resource.getStatus());
                        data.put("region", resource.getRegion());
                        // Combine tags and properties into metadata
                        Map<String, Object> metadata = new HashMap<>(resource.getProperties());
                        resource.getTags().forEach((k, v) -> metadata.put(k, v));
                        data.put("metadata", metadata);
                        data.put("providerId", resource.getProviderId());
                        return data;
                    })
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(resourceData);
        } catch (Exception e) {
            LOG.error("Error discovering resources for provider {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}