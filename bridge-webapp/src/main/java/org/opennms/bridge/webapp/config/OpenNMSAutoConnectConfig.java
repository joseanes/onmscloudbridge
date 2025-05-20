package org.opennms.bridge.webapp.config;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.api.CloudResource;
import org.opennms.bridge.api.MetricCollection;
import org.opennms.bridge.core.service.OpenNMSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Auto-connect to OpenNMS on application startup and handle automatic synchronization.
 * This component attempts to connect to OpenNMS when the application starts and provides
 * automatic synchronization of discovered resources and metrics with OpenNMS.
 */
@Component
@EnableScheduling
public class OpenNMSAutoConnectConfig {
    
    private static final Logger LOG = LoggerFactory.getLogger(OpenNMSAutoConnectConfig.class);
    
    @Autowired
    private OpenNMSClient openNMSClient;
    
    @Autowired
    private Map<String, CloudProvider> cloudProviders;

    @Autowired
    private TaskScheduler taskScheduler;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean autoSyncNodes = new AtomicBoolean(false);
    private final AtomicBoolean autoSyncMetrics = new AtomicBoolean(false);
    private final Map<String, Long> lastSyncTimes = new ConcurrentHashMap<>();
    private Duration syncInterval = Duration.ofMinutes(15);
    
    /**
     * Attempt to connect to OpenNMS when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void connectToOpenNMS() {
        LOG.info("Attempting to auto-connect to OpenNMS...");
        
        // Retry multiple times with a delay
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                LOG.info("OpenNMS connection attempt {} of 3", attempt);
                boolean connected = openNMSClient.testConnection();
                
                if (connected) {
                    LOG.info("Successfully auto-connected to OpenNMS");
                    return;
                } else {
                    LOG.warn("Auto-connection to OpenNMS failed on attempt {}. Retrying...", attempt);
                    
                    // Add a delay between attempts if not the last attempt
                    if (attempt < 3) {
                        Thread.sleep(5000); // 5 second delay
                    }
                }
            } catch (Exception e) {
                LOG.error("Error during OpenNMS auto-connection attempt {}: {}", attempt, e.getMessage());
                
                // Add a delay between attempts if not the last attempt
                if (attempt < 3) {
                    try {
                        Thread.sleep(5000); // 5 second delay
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        LOG.warn("All OpenNMS auto-connection attempts failed. You may need to configure OpenNMS connection settings.");
    }
    
    @PostConstruct
    public void initialize() {
        LOG.info("Initializing OpenNMS Auto Connect configuration");
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down OpenNMS Auto Connect configuration");
        cancelAllTasks();
    }

    /**
     * Enable or disable automatic node synchronization
     *
     * @param enabled whether to enable auto-sync of nodes
     */
    public void setAutoSyncNodes(boolean enabled) {
        LOG.info("Setting auto-sync nodes: {}", enabled);
        autoSyncNodes.set(enabled);
    }

    /**
     * Check if auto-sync of nodes is enabled
     *
     * @return whether auto-sync of nodes is enabled
     */
    public boolean isAutoSyncNodesEnabled() {
        return autoSyncNodes.get();
    }

    /**
     * Enable or disable automatic metrics synchronization
     *
     * @param enabled whether to enable auto-sync of metrics
     */
    public void setAutoSyncMetrics(boolean enabled) {
        LOG.info("Setting auto-sync metrics: {}", enabled);
        autoSyncMetrics.set(enabled);

        if (enabled) {
            scheduleMetricsSynchronization();
        } else {
            cancelTask("metrics-sync");
        }
    }

    /**
     * Check if auto-sync of metrics is enabled
     *
     * @return whether auto-sync of metrics is enabled
     */
    public boolean isAutoSyncMetricsEnabled() {
        return autoSyncMetrics.get();
    }

    /**
     * Set the synchronization interval for metrics
     *
     * @param minutes the interval in minutes
     */
    public void setSyncInterval(long minutes) {
        if (minutes < 1) {
            LOG.warn("Invalid sync interval ({}), using default 15 minutes", minutes);
            this.syncInterval = Duration.ofMinutes(15);
            return;
        }

        LOG.info("Setting sync interval: {} minutes", minutes);
        this.syncInterval = Duration.ofMinutes(minutes);

        // Reschedule if auto-sync is enabled
        if (autoSyncMetrics.get()) {
            cancelTask("metrics-sync");
            scheduleMetricsSynchronization();
        }
    }

    /**
     * Get the current synchronization interval in minutes
     *
     * @return the interval in minutes
     */
    public long getSyncIntervalMinutes() {
        return syncInterval.toMinutes();
    }

    /**
     * Schedule metrics synchronization
     */
    private void scheduleMetricsSynchronization() {
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(
                this::syncAllMetricsToOpenNMS,
                syncInterval.toMillis()
        );

        scheduledTasks.put("metrics-sync", task);
        LOG.info("Scheduled metrics synchronization task with interval: {} minutes", syncInterval.toMinutes());
    }

    /**
     * Cancel a specific scheduled task
     *
     * @param taskId the task ID
     */
    private void cancelTask(String taskId) {
        ScheduledFuture<?> task = scheduledTasks.remove(taskId);
        if (task != null) {
            task.cancel(false);
            LOG.info("Cancelled task: {}", taskId);
        }
    }

    /**
     * Cancel all scheduled tasks
     */
    private void cancelAllTasks() {
        LOG.info("Cancelling all scheduled tasks");

        scheduledTasks.forEach((id, task) -> {
            task.cancel(false);
            LOG.debug("Cancelled task: {}", id);
        });

        scheduledTasks.clear();
        LOG.info("All scheduled tasks cancelled");
    }

    /**
     * Handle discovery completion event
     * If auto-sync is enabled, this will sync the discovered nodes to OpenNMS
     *
     * @param providerId the provider ID
     * @param resources the discovered resources
     */
    public void handleDiscoveryCompletion(String providerId, Set<CloudResource> resources) {
        if (!autoSyncNodes.get() || !openNMSClient.testConnection()) {
            return;
        }

        LOG.info("Auto-syncing {} resources from provider {} to OpenNMS", resources.size(), providerId);

        for (CloudResource resource : resources) {
            try {
                openNMSClient.createOrUpdateNode(resource);
                LOG.debug("Auto-synced resource to OpenNMS: {}", resource.getResourceId());
            } catch (Exception e) {
                LOG.error("Error auto-syncing resource to OpenNMS: {}", resource.getResourceId(), e);
            }
        }
    }

    /**
     * Handle collection completion event
     * If auto-sync is enabled, this will sync the collected metrics to OpenNMS
     *
     * @param providerId the provider ID
     * @param resourceId the resource ID
     * @param metrics the collected metrics
     */
    public void handleCollectionCompletion(String providerId, String resourceId, MetricCollection metrics) {
        if (!autoSyncMetrics.get() || !openNMSClient.testConnection()) {
            return;
        }

        LOG.info("Auto-syncing {} metrics for resource {} from provider {} to OpenNMS", 
                metrics.getMetrics().size(), resourceId, providerId);

        try {
            // Find the node ID in OpenNMS
            String foreignId = providerId + ":" + resourceId;
            String nodeId = openNMSClient.findNodeByForeignId(providerId, foreignId);

            if (nodeId == null) {
                // Node doesn't exist yet, need to get the resource and create it
                CloudProvider provider = cloudProviders.get(providerId);
                if (provider == null) {
                    LOG.error("Provider not found for auto-sync: {}", providerId);
                    return;
                }

                Set<CloudResource> resources = provider.discover();
                CloudResource resource = resources.stream()
                        .filter(r -> resourceId.equals(r.getResourceId()))
                        .findFirst()
                        .orElse(null);

                if (resource == null) {
                    LOG.error("Resource not found for auto-sync: {}", resourceId);
                    return;
                }

                // Create the node
                openNMSClient.createOrUpdateNode(resource);

                // Try to get the node ID again
                nodeId = openNMSClient.findNodeByForeignId(providerId, foreignId);
                if (nodeId == null) {
                    LOG.error("Failed to get node ID after creation for auto-sync");
                    return;
                }
            }

            // Submit the metrics
            openNMSClient.submitMetrics(nodeId, metrics);
            LOG.debug("Auto-synced metrics to OpenNMS for resource: {}", resourceId);

            // Update last sync time
            lastSyncTimes.put(resourceId, System.currentTimeMillis());
        } catch (Exception e) {
            LOG.error("Error auto-syncing metrics to OpenNMS for resource: {}", resourceId, e);
        }
    }

    /**
     * Sync all metrics to OpenNMS from all providers
     * This is run on a schedule when auto-sync metrics is enabled
     */
    public void syncAllMetricsToOpenNMS() {
        if (!autoSyncMetrics.get() || !openNMSClient.testConnection()) {
            return;
        }

        LOG.info("Running scheduled metrics sync to OpenNMS");

        for (Map.Entry<String, CloudProvider> entry : cloudProviders.entrySet()) {
            String providerId = entry.getKey();
            CloudProvider provider = entry.getValue();

            try {
                // Discover all resources
                Set<CloudResource> resources = provider.discover();
                LOG.info("Discovered {} resources from provider {}", resources.size(), providerId);

                // Collect and sync metrics for each resource
                for (CloudResource resource : resources) {
                    try {
                        MetricCollection metrics = provider.collect(resource);
                        if (metrics != null && !metrics.getMetrics().isEmpty()) {
                            handleCollectionCompletion(providerId, resource.getResourceId(), metrics);
                        }
                    } catch (Exception e) {
                        LOG.error("Error collecting metrics for resource {}: {}", resource.getResourceId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOG.error("Error discovering resources from provider {}: {}", providerId, e.getMessage());
            }
        }
    }

    /**
     * Get the status of auto-sync and the last sync times
     *
     * @return a map with the status information
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("autoSyncNodes", autoSyncNodes.get());
        status.put("autoSyncMetrics", autoSyncMetrics.get());
        status.put("syncIntervalMinutes", syncInterval.toMinutes());
        status.put("lastSyncTimes", lastSyncTimes);
        return status;
    }
}