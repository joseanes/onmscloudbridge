package org.opennms.bridge.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing discovery logs and discovered resources.
 */
public interface DiscoveryLogService {
    
    /**
     * Add a discovery log entry for a provider
     * 
     * @param providerId the provider ID
     * @param logEntry the log entry
     */
    void addLogEntry(String providerId, Map<String, Object> logEntry);
    
    /**
     * Store discovered resources for a provider
     * 
     * @param providerId the provider ID
     * @param resources the discovered resources
     */
    void storeDiscoveredResources(String providerId, Set<CloudResource> resources);
    
    /**
     * Get discovery logs for a provider
     * 
     * @param providerId the provider ID
     * @return list of log entries
     */
    List<Map<String, Object>> getProviderLogs(String providerId);
    
    /**
     * Get discovered resources for a provider
     * 
     * @param providerId the provider ID
     * @return list of resource data
     */
    List<Map<String, Object>> getDiscoveredResources(String providerId);
    
    /**
     * Clear logs for a provider
     * 
     * @param providerId the provider ID
     */
    void clearProviderLogs(String providerId);
}