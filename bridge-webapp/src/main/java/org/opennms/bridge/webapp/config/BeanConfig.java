package org.opennms.bridge.webapp.config;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.api.CloudProviderException;
import org.opennms.bridge.aws.AwsCloudProvider;
import org.opennms.bridge.aws.AwsConfigurationProperties;
import org.opennms.bridge.aws.AwsDiscoveryStrategy;
import org.opennms.bridge.aws.AwsMetricCollector;
import org.opennms.bridge.api.DiscoveryLogService;
import org.opennms.bridge.api.CollectionLogService;
import org.opennms.bridge.core.service.ProviderSettingsService;
import org.opennms.bridge.webapp.controller.OpenNMSController.IntegrationConfig;
import org.opennms.bridge.webapp.service.IntegrationConfigService;
import org.opennms.bridge.webapp.service.MockAwsCloudProvider;
import org.opennms.bridge.webapp.service.MockDiscoveryLogService;
import org.opennms.bridge.webapp.service.MockCollectionLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bean configuration class for the application.
 */
@Configuration
public class BeanConfig {
    private static final Logger LOG = LoggerFactory.getLogger(BeanConfig.class);
    
    @Value("${cloud.providers.useMock:false}")
    private boolean useMockProviders;
    
    @Autowired
    private IntegrationConfigService integrationConfigService;
    
    // Keep references to providers for dynamic switching
    private List<CloudProvider> mockProviders = new ArrayList<>();
    private List<CloudProvider> realProviders = new ArrayList<>();
    private List<CloudProvider> activeProviders = new ArrayList<>();
    
    /**
     * Initialize settings based on saved configuration
     */
    @PostConstruct
    public void init() {
        try {
            LOG.info("Initializing BeanConfig with saved settings");
            
            // Load integration config
            IntegrationConfig config = integrationConfigService.getConfig();
            
            // Use the disableMockProviders setting to set useMockProviders
            boolean newMockProvidersSetting = !config.isDisableMockProviders();
            
            // Only log if there's a change from the default
            if (newMockProvidersSetting != useMockProviders) {
                LOG.info("Setting useMockProviders to {} based on saved configuration", newMockProvidersSetting);
                this.useMockProviders = newMockProvidersSetting;
            }
            
        } catch (Exception e) {
            LOG.error("Error initializing BeanConfig from saved settings: {}", e.getMessage(), e);
        }
    }
    
    @Value("${discovery.logs.useMock:true}")
    private boolean useMockLogs;
    
    @Value("${collection.logs.useMock:true}")
    private boolean useMockCollectionLogs;
    
    /**
     * Get the current state of mock providers
     * @return true if mock providers are enabled, false otherwise
     */
    public boolean isUseMockProviders() {
        return useMockProviders;
    }
    
    /**
     * Updates the mock providers setting
     * 
     * @param useMock whether to use mock providers
     * @return the updated setting
     */
    public boolean setUseMockProviders(boolean useMock) {
        LOG.info("Setting useMockProviders to: {}", useMock);
        this.useMockProviders = useMock;
        
        // Also save this setting to application.yml for persistence
        try {
            // Update the main setting
            updateApplicationProperty("cloud.providers.useMock", String.valueOf(useMock));
            
            // Also update the emergency bypass mode when changing mock providers setting
            // When mock providers are disabled, emergency bypass should also be disabled
            // to ensure real AWS resources are used
            if (!useMock) {
                // When disabling mock providers (using real providers), disable emergency bypass
                LOG.info("Disabling AWS emergency bypass mode since real providers are enabled");
                updateApplicationProperty("bridge.debug.aws.emergency_bypass", "false");
            }
        } catch (Exception e) {
            LOG.error("Error saving mock providers setting to application.yml: {}", e.getMessage(), e);
        }
        
        // Update the active providers list
        updateActiveProviders();
        
        return this.useMockProviders;
    }
    
    /**
     * Gets the current mock providers setting
     * 
     * @return whether mock providers are currently enabled
     */
    public boolean getUseMockProviders() {
        return this.useMockProviders;
    }
    
    /**
     * Provides a discovery log service implementation.
     * Will use mock service for development and testing.
     * 
     * @param mockDiscoveryLogService Mock discovery log service
     * @return DiscoveryLogService implementation
     */
    @Bean
    public DiscoveryLogService discoveryLogService(MockDiscoveryLogService mockDiscoveryLogService) {
        if (useMockLogs) {
            LOG.info("Using mock discovery log service");
            return mockDiscoveryLogService;
        } else {
            LOG.info("No production discovery log service available, using mock");
            return mockDiscoveryLogService;
        }
    }
    
    /**
     * Provides a collection log service implementation.
     * Will use mock service for development and testing.
     * 
     * @param mockCollectionLogService Mock collection log service
     * @return CollectionLogService implementation
     */
    @Bean
    public CollectionLogService collectionLogService(MockCollectionLogService mockCollectionLogService) {
        if (useMockCollectionLogs) {
            LOG.info("Using mock collection log service");
            return mockCollectionLogService;
        } else {
            LOG.info("No production collection log service available, using mock");
            return mockCollectionLogService;
        }
    }
    
    /**
     * The provider settings service is already created via the @Service annotation
     * in the ProviderSettingsService class. No need to create it again here.
     */
    
    /**
     * Creates a list of cloud providers for the application.
     * 
     * @param mockAwsCloudProvider Mock AWS cloud provider
     * @param awsCloudProvider Real AWS cloud provider
     * @return List of cloud providers
     */
    @Bean
    public List<CloudProvider> cloudProviders(MockAwsCloudProvider mockAwsCloudProvider, 
                                             AwsCloudProvider awsCloudProvider) {
        // Store providers in separate lists for dynamic switching
        mockProviders.clear();
        realProviders.clear();
        activeProviders.clear();
        
        // Add mock providers to mock list
        mockProviders.add(mockAwsCloudProvider);
        LOG.info("Registered mock AWS provider: {}", mockAwsCloudProvider.getProviderId());
        
        // Try to initialize and add real providers to real list
        try {
            // Initialize and validate the provider
            awsCloudProvider.validate();
            LOG.info("AWS provider initialized and validated successfully");
            realProviders.add(awsCloudProvider);
            LOG.info("Registered real AWS provider: {}", awsCloudProvider.getProviderId());
        } catch (Exception e) {
            LOG.error("Error initializing AWS provider: {}", e.getMessage(), e);
            LOG.warn("Real AWS provider will not be available");
        }
        
        // Now create the active providers list based on useMockProviders flag
        if (useMockProviders) {
            LOG.info("Using mock AWS provider");
            activeProviders.addAll(mockProviders);
        } else {
            LOG.info("Using real AWS provider");
            if (!realProviders.isEmpty()) {
                activeProviders.addAll(realProviders);
            } else {
                LOG.warn("Falling back to mock AWS provider due to initialization error");
                activeProviders.addAll(mockProviders);
            }
        }
        
        return activeProviders;
    }
    
    /**
     * Method to create a new AWS cloud provider instance for testing or dynamic creation.
     * This is NOT registered as a bean to avoid conflicts with the main provider.
     * 
     * @param discoveryStrategy AWS discovery strategy
     * @param metricCollector AWS metric collector
     * @return AWS cloud provider instance
     */
    public AwsCloudProvider createAwsCloudProvider(
            AwsDiscoveryStrategy discoveryStrategy,
            AwsMetricCollector metricCollector) {
        try {
            LOG.info("Creating new AWS cloud provider instance");
            
            // Create configuration properties with default values
            AwsConfigurationProperties config = new AwsConfigurationProperties();
            
            // Generate a unique provider ID
            String providerId = "temp-" + UUID.randomUUID().toString();
            config.setProviderId(providerId);
            config.setDisplayName("Temporary AWS Provider");
            
            // Configure sensible defaults for connection timeouts
            config.setConnectionTimeout(java.time.Duration.ofSeconds(10));
            config.setReadTimeout(java.time.Duration.ofSeconds(30));
            config.setMaxRetries(3);
            
            // Add a default region if none is specified
            if (config.getRegions() == null || config.getRegions().isEmpty()) {
                config.setRegions(java.util.Collections.singletonList("us-east-1"));
            }
            
            LOG.info("Creating AWS provider with ID: {}", providerId);
            
            // Create a new AWS cloud provider with proper construction
            AwsCloudProvider provider = new AwsCloudProvider();
            
            // Set dependencies using reflection in a more robust way
            try {
                // Set configuration
                java.lang.reflect.Field configField = AwsCloudProvider.class.getDeclaredField("config");
                configField.setAccessible(true);
                configField.set(provider, config);
                
                // Set discovery strategy
                java.lang.reflect.Field discoveryStrategyField = AwsCloudProvider.class.getDeclaredField("discoveryStrategy");
                discoveryStrategyField.setAccessible(true);
                discoveryStrategyField.set(provider, discoveryStrategy);
                
                // Set metric collector
                java.lang.reflect.Field metricCollectorField = AwsCloudProvider.class.getDeclaredField("metricCollector");
                metricCollectorField.setAccessible(true);
                metricCollectorField.set(provider, metricCollector);
                
                LOG.info("Dependencies set successfully for AWS provider");
                
                // Initialize the provider manually to set up credentials
                provider.init();
                
                // Validate the provider to catch any immediate issues
                provider.validate();
                
                LOG.info("AWS cloud provider initialized and validated successfully");
                return provider;
                
            } catch (NoSuchFieldException | IllegalAccessException e) {
                LOG.error("Failed to set AWS provider dependencies using reflection: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to set AWS provider dependencies: " + e.getMessage(), e);
            } catch (CloudProviderException e) {
                LOG.error("Failed to validate AWS provider after initialization: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to validate AWS provider: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            LOG.error("Failed to create AWS cloud provider: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create AWS cloud provider: " + e.getMessage(), e);
        }
    }
    
    /**
     * Updates the active providers list based on the current setting.
     * This method can be called at runtime to switch between provider types.
     * 
     * @return the updated list of active providers
     */
    public List<CloudProvider> updateActiveProviders() {
        activeProviders.clear();
        
        if (useMockProviders) {
            LOG.info("Switching to mock providers");
            activeProviders.addAll(mockProviders);
        } else {
            LOG.info("Switching to real providers");
            if (!realProviders.isEmpty()) {
                activeProviders.addAll(realProviders);
            } else {
                LOG.warn("No real providers available, falling back to mock providers");
                activeProviders.addAll(mockProviders);
            }
        }
        
        LOG.info("Updated active providers list, now contains {} providers", activeProviders.size());
        for (CloudProvider provider : activeProviders) {
            LOG.info("  - Active provider: {} ({})", provider.getProviderId(), provider.getClass().getSimpleName());
        }
        
        return activeProviders;
    }
    
    /**
     * Get the list of currently active providers.
     * 
     * @return the list of active providers
     */
    public List<CloudProvider> getActiveProviders() {
        return activeProviders;
    }
    
    /**
     * Updates a property in the application.yml file.
     * This is a simple implementation that should be enhanced for production use.
     * 
     * @param key the property key in dot notation (e.g., "cloud.providers.useMock")
     * @param value the property value as a string
     */
    private void updateApplicationProperty(String key, String value) {
        try {
            // Define the path to the application.yml file in the config directory
            java.nio.file.Path configFile = java.nio.file.Paths.get("config", "application.yml");
            java.io.File configDir = new java.io.File("config");
            
            // Create the config directory if it doesn't exist
            if (!configDir.exists() && !configDir.mkdirs()) {
                LOG.error("Failed to create config directory");
                return;
            }
            
            // Check if the file already exists; if not, create a new one
            if (!configFile.toFile().exists()) {
                LOG.info("Creating new application.yml in config directory");
                java.nio.file.Files.createFile(configFile);
            }
            
            // Read the existing content of the file
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (configFile.toFile().length() > 0) {
                lines = java.nio.file.Files.readAllLines(configFile);
            }
            
            // Parse the key into nested parts (e.g., "cloud.providers.useMock" -> ["cloud", "providers", "useMock"])
            String[] keyParts = key.split("\\.");
            
            // If the file is empty, create the necessary structure
            if (lines.isEmpty()) {
                // Add each part of the key as a new indented level
                for (int i = 0; i < keyParts.length - 1; i++) {
                    String indentation = new String(new char[i * 2]).replace("\0", " ");
                    lines.add(indentation + keyParts[i] + ":");
                }
                
                // Add the final key-value pair
                String indentation = new String(new char[(keyParts.length - 1) * 2]).replace("\0", " ");
                lines.add(indentation + keyParts[keyParts.length - 1] + ": " + value);
            } else {
                // If the file has content, we need to find the right place to insert or update the property
                boolean found = false;
                
                // Simple implementation: look for exact key match at appropriate indentation
                // This should be enhanced for production to properly handle YAML structure
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith(keyParts[keyParts.length - 1] + ":")) {
                        // Found the key at appropriate level, replace the line
                        String indentation = line.substring(0, line.indexOf(keyParts[keyParts.length - 1]));
                        lines.set(i, indentation + keyParts[keyParts.length - 1] + ": " + value);
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    // If not found, add the property at the end (this is a simplified approach)
                    // For production, this should navigate the YAML structure properly
                    LOG.info("Adding new property to application.yml: {} = {}", key, value);
                    lines.add("cloud:");
                    lines.add("  providers:");
                    lines.add("    useMock: " + value);
                }
            }
            
            // Write the updated content back to the file
            java.nio.file.Files.write(configFile, lines);
            LOG.info("Updated application.yml with property: {} = {}", key, value);
            
        } catch (Exception e) {
            LOG.error("Error updating application property: {}", e.getMessage(), e);
        }
    }
}