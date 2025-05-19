package org.opennms.bridge.api;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for discovering cloud resources.
 * This interface mirrors the pattern used by OpenNMS Provisiond
 * and will be easily adaptable to the OpenNMS plugin architecture.
 */
public interface DiscoveryService {
    
    /**
     * Discover nodes from a specific cloud provider.
     * 
     * @param provider the cloud provider to query
     * @return a future containing discovered nodes
     */
    CompletableFuture<Set<DiscoveredNode>> discoverNodes(CloudProvider provider);
    
    /**
     * Discover resources from a specific cloud provider by ID.
     * 
     * @param providerId the cloud provider ID
     * @return a set of discovered cloud resources
     * @throws CloudProviderException if discovery fails
     */
    Set<CloudResource> discoverResources(String providerId) throws CloudProviderException;
    
    /**
     * Discover resources from a specific cloud provider.
     * 
     * @param provider the cloud provider
     * @return a set of discovered cloud resources
     * @throws CloudProviderException if discovery fails
     */
    Set<CloudResource> discoverResources(CloudProvider provider) throws CloudProviderException;
    
    /**
     * Schedule periodic discovery for a cloud provider.
     * 
     * @param provider the cloud provider
     * @param configuration discovery configuration
     */
    void scheduleDiscovery(CloudProvider provider, DiscoveryConfiguration configuration);
    
    /**
     * Stop discovery for a cloud provider.
     * 
     * @param providerId the provider identifier
     */
    void stopDiscovery(String providerId);
    
    /**
     * Get discovery status for all providers.
     * 
     * @return discovery status information
     */
    Set<DiscoveryStatus> getDiscoveryStatus();
}