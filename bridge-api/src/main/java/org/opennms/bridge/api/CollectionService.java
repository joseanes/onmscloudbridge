package org.opennms.bridge.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for collecting metrics from cloud resources.
 * This interface mirrors the pattern used by OpenNMS Collectd
 * and will be easily adaptable to the OpenNMS plugin architecture.
 */
public interface CollectionService {
    
    /**
     * Collect metrics from a specific cloud resource asynchronously.
     * 
     * @param resource the cloud resource to collect from
     * @return a future containing the collection result
     */
    CompletableFuture<CollectionResult> collectMetrics(CloudResource resource);
    
    /**
     * Collect metrics from a specific cloud resource synchronously.
     * 
     * @param resource the cloud resource to collect from
     * @return the metric collection
     * @throws CloudProviderException if collection fails
     */
    MetricCollection collectMetrics(String providerId, CloudResource resource) throws CloudProviderException;
    
    /**
     * Collect metrics for all resources from a specific provider.
     * 
     * @param providerId the provider ID
     * @return list of metric collections
     * @throws CloudProviderException if collection fails
     */
    List<MetricCollection> collectAllMetrics(String providerId) throws CloudProviderException;
    
    /**
     * Schedule periodic collection for a cloud resource.
     * 
     * @param resource the cloud resource
     * @param configuration collection configuration
     */
    void scheduleCollection(CloudResource resource, CollectionConfiguration configuration);
    
    /**
     * Stop collection for a specific resource.
     * 
     * @param resourceId the resource identifier
     */
    void stopCollection(String resourceId);
    
    /**
     * Get collection status for all resources.
     * 
     * @return collection status information
     */
    java.util.Set<CollectionStatus> getCollectionStatus();
    
    /**
     * Get schedule information for collection.
     * 
     * @return map of schedule parameters
     */
    Map<String, Object> getScheduleInfo();
    
    /**
     * Update collection schedule.
     * 
     * @param scheduleConfig schedule configuration
     * @return true if update was successful
     */
    boolean updateSchedule(Map<String, Object> scheduleConfig);
    
    /**
     * Submit collected metrics to OpenNMS.
     * 
     * @param nodeId OpenNMS node ID
     * @param metrics collected metrics
     * @return future indicating submission success
     */
    CompletableFuture<Void> submitMetrics(String nodeId, MetricCollection metrics);
}