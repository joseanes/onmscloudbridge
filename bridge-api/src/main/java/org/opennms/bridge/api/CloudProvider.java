package org.opennms.bridge.api;

import java.util.Map;
import java.util.Set;

/**
 * Cloud provider interface following the Service Provider Interface (SPI) pattern.
 * This interface will be directly compatible with the OpenNMS plugin system
 * when converted to an OSGi service.
 */
public interface CloudProvider {
    
    /**
     * Unique identifier for this cloud provider instance.
     * 
     * @return provider ID (e.g., "aws-production", "azure-dev")
     */
    String getProviderId();
    
    /**
     * Cloud provider type.
     * 
     * @return provider type (e.g., "aws", "azure", "gcp")
     */
    String getProviderType();
    
    /**
     * Human-readable name for this provider instance.
     * 
     * @return display name
     */
    String getDisplayName();
    
    /**
     * Validate that this provider is properly configured and can connect.
     * 
     * @return validation result
     * @throws CloudProviderException if validation fails
     */
    ValidationResult validate() throws CloudProviderException;
    
    /**
     * Discover cloud resources (VMs, containers, etc.) from this provider.
     * 
     * @return set of discovered cloud resources
     * @throws CloudProviderException if discovery fails
     */
    Set<CloudResource> discover() throws CloudProviderException;
    
    /**
     * Collect metrics from a specific cloud resource.
     * 
     * @param resource the cloud resource to collect from
     * @return collected metrics
     * @throws CloudProviderException if collection fails
     */
    MetricCollection collect(CloudResource resource) throws CloudProviderException;
    
    /**
     * Get available regions/locations for this provider.
     * 
     * @return set of available regions
     */
    Set<String> getAvailableRegions();
    
    /**
     * Get provider-specific configuration properties.
     * 
     * @return configuration properties
     */
    Map<String, Object> getConfiguration();
    
    /**
     * Update provider configuration.
     * 
     * @param configuration new configuration
     * @throws CloudProviderException if configuration is invalid
     */
    void updateConfiguration(Map<String, Object> configuration) throws CloudProviderException;
    
    /**
     * Get supported metric types for this provider.
     * 
     * @return set of supported metric types
     */
    Set<String> getSupportedMetrics();
    
    /**
     * Close any resources and cleanup.
     */
    void close();
}