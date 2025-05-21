package org.opennms.bridge.webapp.service;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.webapp.config.BeanConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for filtering providers based on the current mock provider settings.
 * This ensures that when mock providers are disabled, they are filtered out from all results.
 */
@Service
public class ProviderFilterService {
    private static final Logger LOG = LoggerFactory.getLogger(ProviderFilterService.class);
    
    @Autowired
    private BeanConfig beanConfig;
    
    /**
     * Get a filtered list of cloud providers based on the current mock provider setting.
     * If mock providers are disabled, only real providers will be returned.
     * 
     * @return filtered list of cloud providers
     */
    public List<CloudProvider> getFilteredProviders() {
        return beanConfig.getActiveProviders();
    }
    
    /**
     * Check if a provider should be included based on the current mock provider setting.
     * 
     * @param provider the provider to check
     * @return true if the provider should be included, false otherwise
     */
    public boolean shouldIncludeProvider(CloudProvider provider) {
        // If mock providers are enabled, include all providers
        if (beanConfig.getUseMockProviders()) {
            return true;
        }
        
        // If mock providers are disabled, only include real providers
        return !provider.getClass().getSimpleName().contains("Mock");
    }
    
    /**
     * Check if a provider should be included based on its ID and the current mock provider setting.
     * 
     * @param providerId the provider ID to check
     * @return true if the provider should be included, false otherwise
     */
    public boolean shouldIncludeProvider(String providerId) {
        // If mock providers are enabled, include all providers
        if (beanConfig.getUseMockProviders()) {
            return true;
        }
        
        // If mock providers are disabled, check if the provider is a mock
        return !providerId.toLowerCase().contains("mock");
    }
    
    /**
     * Get the current state of the mock providers setting.
     * 
     * @return true if mock providers are enabled, false otherwise
     */
    public boolean isUseMockProviders() {
        return beanConfig.getUseMockProviders();
    }
}