package org.opennms.bridge.webapp.config;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.api.CloudProviderException;
import org.opennms.bridge.aws.AwsCloudProvider;
import org.opennms.bridge.aws.AwsConfigurationProperties;
import org.opennms.bridge.aws.AwsDiscoveryStrategy;
import org.opennms.bridge.aws.AwsMetricCollector;
import org.opennms.bridge.api.DiscoveryLogService;
import org.opennms.bridge.webapp.service.MockAwsCloudProvider;
import org.opennms.bridge.webapp.service.MockDiscoveryLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bean configuration class for the application.
 */
@Configuration
public class BeanConfig {
    private static final Logger LOG = LoggerFactory.getLogger(BeanConfig.class);
    
    @Value("${cloud.providers.useMock:true}")
    private boolean useMockProviders;
    
    @Value("${discovery.logs.useMock:true}")
    private boolean useMockLogs;
    
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
     * Creates a list of cloud providers for the application.
     * 
     * @param mockAwsCloudProvider Mock AWS cloud provider
     * @param awsCloudProvider Real AWS cloud provider
     * @return List of cloud providers
     */
    @Bean
    public List<CloudProvider> cloudProviders(MockAwsCloudProvider mockAwsCloudProvider, 
                                             AwsCloudProvider awsCloudProvider) {
        List<CloudProvider> providers = new ArrayList<>();
        
        if (useMockProviders) {
            providers.add(mockAwsCloudProvider);
        } else {
            providers.add(awsCloudProvider);
        }
        
        return providers;
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
}