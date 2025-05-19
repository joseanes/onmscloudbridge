package org.opennms.bridge.core.service;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Default implementation of the Collection Service.
 * Handles metric collection from cloud resources and scheduling of collection tasks.
 */
@Service
public class DefaultCollectionService implements CollectionService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCollectionService.class);

    private final OpenNMSClient openNMSClient;
    private final TaskScheduler taskScheduler;
    private final ExecutorService executorService;
    
    @Autowired
    private List<CloudProvider> cloudProviders;
    
    @Autowired
    private DiscoveryService discoveryService;
    
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, CollectionConfiguration> resourceConfigurations = new ConcurrentHashMap<>();
    private final Map<String, CollectionStatus> collectionStatuses = new ConcurrentHashMap<>();
    private final Map<String, CloudProvider> resourceProviders = new ConcurrentHashMap<>();
    
    // Global collection schedule
    private boolean collectionEnabled = true;
    private Duration collectionInitialDelay = Duration.ofMinutes(1);
    private Duration collectionInterval = Duration.ofMinutes(5);
    private Instant lastCollectionRun;
    private Instant nextCollectionRun;
    private ScheduledFuture<?> globalCollectionTask;

    @Autowired
    public DefaultCollectionService(OpenNMSClient openNMSClient, TaskScheduler taskScheduler) {
        this.openNMSClient = openNMSClient;
        this.taskScheduler = taskScheduler;
        this.executorService = Executors.newCachedThreadPool();
    }
    
    @PostConstruct
    public void init() {
        // Schedule global collection if enabled
        if (collectionEnabled) {
            scheduleGlobalCollection();
        }
    }

    @Override
    public CompletableFuture<CollectionResult> collectMetrics(CloudResource resource) {
        LOG.info("Starting metric collection for resource: {}", resource.getResourceId());
        
        CollectionStatus status = collectionStatuses.computeIfAbsent(
            resource.getResourceId(), 
            id -> new CollectionStatus()
        );
        
        status.setResourceId(resource.getResourceId());
        status.setResourceType(resource.getResourceType());
        status.setLastStartTime(Instant.now());
        status.setStatus("RUNNING");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.debug("Executing collection for resource: {}", resource.getResourceId());
                
                // Get associated provider for this resource
                CloudProvider provider = getProviderForResource(resource);
                if (provider == null) {
                    throw new IllegalStateException("No provider found for resource: " + resource.getResourceId());
                }
                
                // Collect metrics from the provider
                MetricCollection metrics = provider.collect(resource);
                LOG.info("Collected {} metrics from resource: {}", 
                    metrics.getMetrics().size(), resource.getResourceId());
                
                // Create collection result
                CollectionResult result = new CollectionResult();
                result.setResourceId(resource.getResourceId());
                result.setTimestamp(Instant.now());
                result.setMetrics(metrics);
                
                // Update collection status
                status.setStatus("COMPLETED");
                status.setLastEndTime(Instant.now());
                status.setLastSuccessTime(Instant.now());
                status.setLastMetricCount(metrics.getMetrics().size());
                
                return result;
            } catch (Exception e) {
                LOG.error("Error during collection for resource: " + resource.getResourceId(), e);
                
                // Update collection status
                status.setStatus("FAILED");
                status.setLastEndTime(Instant.now());
                status.setLastError(e.getMessage());
                
                throw new CompletionException(e);
            }
        }, executorService);
    }
    
    @Override
    public MetricCollection collectMetrics(String providerId, CloudResource resource) throws CloudProviderException {
        LOG.info("Collecting metrics for resource {} from provider {}", resource.getResourceId(), providerId);
        
        // Find the provider
        CloudProvider provider = cloudProviders.stream()
                .filter(p -> p.getProviderId().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new CloudProviderException("Provider not found: " + providerId));
        
        try {
            // Collect metrics
            MetricCollection metrics = provider.collect(resource);
            
            // Update collection status
            CollectionStatus status = collectionStatuses.computeIfAbsent(
                resource.getResourceId(), 
                id -> new CollectionStatus()
            );
            
            status.setResourceId(resource.getResourceId());
            status.setResourceType(resource.getResourceType());
            status.setLastStartTime(Instant.now());
            status.setLastEndTime(Instant.now());
            status.setLastSuccessTime(Instant.now());
            status.setStatus("COMPLETED");
            status.setLastMetricCount(metrics.getMetrics().size());
            
            return metrics;
        } catch (Exception e) {
            LOG.error("Error collecting metrics for resource {}: {}", resource.getResourceId(), e.getMessage(), e);
            
            // Update collection status
            CollectionStatus status = collectionStatuses.computeIfAbsent(
                resource.getResourceId(), 
                id -> new CollectionStatus()
            );
            
            status.setResourceId(resource.getResourceId());
            status.setResourceType(resource.getResourceType());
            status.setLastStartTime(Instant.now());
            status.setLastEndTime(Instant.now());
            status.setStatus("FAILED");
            status.setLastError(e.getMessage());
            
            throw new CloudProviderException("Failed to collect metrics for resource: " + resource.getResourceId(), e);
        }
    }
    
    @Override
    public List<MetricCollection> collectAllMetrics(String providerId) throws CloudProviderException {
        LOG.info("Collecting all metrics for provider {}", providerId);
        
        // Find the provider
        CloudProvider provider = cloudProviders.stream()
                .filter(p -> p.getProviderId().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new CloudProviderException("Provider not found: " + providerId));
        
        try {
            // Get resources for this provider
            Set<CloudResource> resources = discoveryService.discoverResources(provider);
            LOG.info("Found {} resources for provider {}", resources.size(), providerId);
            
            // Collect metrics for each resource
            List<MetricCollection> collections = new ArrayList<>();
            for (CloudResource resource : resources) {
                try {
                    MetricCollection metrics = provider.collect(resource);
                    collections.add(metrics);
                    
                    // Update collection status
                    CollectionStatus status = collectionStatuses.computeIfAbsent(
                        resource.getResourceId(), 
                        id -> new CollectionStatus()
                    );
                    
                    status.setResourceId(resource.getResourceId());
                    status.setResourceType(resource.getResourceType());
                    status.setLastStartTime(Instant.now());
                    status.setLastEndTime(Instant.now());
                    status.setLastSuccessTime(Instant.now());
                    status.setStatus("COMPLETED");
                    status.setLastMetricCount(metrics.getMetrics().size());
                } catch (Exception e) {
                    LOG.error("Error collecting metrics for resource {}: {}", 
                            resource.getResourceId(), e.getMessage(), e);
                    
                    // Update collection status
                    CollectionStatus status = collectionStatuses.computeIfAbsent(
                        resource.getResourceId(), 
                        id -> new CollectionStatus()
                    );
                    
                    status.setResourceId(resource.getResourceId());
                    status.setResourceType(resource.getResourceType());
                    status.setLastStartTime(Instant.now());
                    status.setLastEndTime(Instant.now());
                    status.setStatus("FAILED");
                    status.setLastError(e.getMessage());
                }
            }
            
            return collections;
        } catch (Exception e) {
            LOG.error("Error collecting metrics for provider {}: {}", providerId, e.getMessage(), e);
            throw new CloudProviderException("Failed to collect metrics for provider: " + providerId, e);
        }
    }
    
    @Override
    public Map<String, Object> getScheduleInfo() {
        Map<String, Object> scheduleInfo = new HashMap<>();
        scheduleInfo.put("enabled", collectionEnabled);
        scheduleInfo.put("initialDelay", collectionInitialDelay.toMinutes());
        scheduleInfo.put("interval", collectionInterval.toMinutes());
        scheduleInfo.put("nextRun", nextCollectionRun);
        scheduleInfo.put("lastRun", lastCollectionRun);
        return scheduleInfo;
    }
    
    @Override
    public boolean updateSchedule(Map<String, Object> scheduleConfig) {
        try {
            // Update schedule configuration
            if (scheduleConfig.containsKey("enabled")) {
                collectionEnabled = (Boolean) scheduleConfig.get("enabled");
            }
            
            if (scheduleConfig.containsKey("initialDelay")) {
                Object delay = scheduleConfig.get("initialDelay");
                if (delay instanceof Number) {
                    collectionInitialDelay = Duration.ofMinutes(((Number) delay).longValue());
                } else if (delay instanceof String) {
                    collectionInitialDelay = Duration.ofMinutes(Long.parseLong((String) delay));
                }
            }
            
            if (scheduleConfig.containsKey("interval")) {
                Object interval = scheduleConfig.get("interval");
                if (interval instanceof Number) {
                    collectionInterval = Duration.ofMinutes(((Number) interval).longValue());
                } else if (interval instanceof String) {
                    collectionInterval = Duration.ofMinutes(Long.parseLong((String) interval));
                }
            }
            
            // Cancel existing schedule
            if (globalCollectionTask != null) {
                globalCollectionTask.cancel(false);
                globalCollectionTask = null;
            }
            
            // Reschedule if enabled
            if (collectionEnabled) {
                scheduleGlobalCollection();
            }
            
            return true;
        } catch (Exception e) {
            LOG.error("Error updating collection schedule: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Schedule global collection for all providers
     */
    private void scheduleGlobalCollection() {
        nextCollectionRun = Instant.now().plus(collectionInitialDelay);
        
        globalCollectionTask = taskScheduler.scheduleAtFixedRate(
            () -> {
                try {
                    lastCollectionRun = Instant.now();
                    nextCollectionRun = lastCollectionRun.plus(collectionInterval);
                    
                    LOG.info("Running global collection for all providers");
                    
                    // Collect metrics for all providers
                    for (CloudProvider provider : cloudProviders) {
                        try {
                            collectAllMetrics(provider.getProviderId());
                        } catch (Exception e) {
                            LOG.error("Error collecting metrics for provider {}: {}", 
                                    provider.getProviderId(), e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Error in global collection task: {}", e.getMessage(), e);
                }
            },
            new Date(System.currentTimeMillis() + collectionInitialDelay.toMillis()),
            collectionInterval.toMillis()
        );
        
        LOG.info("Global collection scheduled with interval: {} minutes", collectionInterval.toMinutes());
    }

    @Override
    public void scheduleCollection(CloudResource resource, CollectionConfiguration configuration) {
        LOG.info("Scheduling collection for resource: {} with interval: {}", 
                resource.getResourceId(), configuration.getInterval());
        
        // Cancel existing scheduled task if present
        stopCollection(resource.getResourceId());
        
        // Store configuration
        resourceConfigurations.put(resource.getResourceId(), configuration);
        
        // Store resource provider mapping
        // Note: In a real implementation, we'd get or lookup the provider more robustly
        if (configuration.getProvider() != null) {
            resourceProviders.put(resource.getResourceId(), configuration.getProvider());
        }
        
        // Schedule new task
        ScheduledFuture<?> scheduledTask = taskScheduler.scheduleAtFixedRate(
            () -> {
                try {
                    collectMetrics(resource)
                        .thenAccept(result -> processCollectionResult(result, resource))
                        .exceptionally(ex -> {
                            LOG.error("Scheduled collection failed for resource: " + resource.getResourceId(), ex);
                            return null;
                        });
                } catch (Exception e) {
                    LOG.error("Error in scheduled collection task for resource: " + resource.getResourceId(), e);
                }
            },
            new Date(System.currentTimeMillis() + configuration.getInitialDelay().toMillis()),
            configuration.getInterval().toMillis()
        );
        
        scheduledTasks.put(resource.getResourceId(), scheduledTask);
        
        LOG.info("Collection scheduled for resource: {}", resource.getResourceId());
    }

    @Override
    public void stopCollection(String resourceId) {
        LOG.info("Stopping collection for resource: {}", resourceId);
        
        ScheduledFuture<?> task = scheduledTasks.remove(resourceId);
        if (task != null) {
            task.cancel(false);
            LOG.info("Collection stopped for resource: {}", resourceId);
        }
        
        resourceConfigurations.remove(resourceId);
    }

    @Override
    public Set<CollectionStatus> getCollectionStatus() {
        return new HashSet<>(collectionStatuses.values());
    }

    @Override
    public CompletableFuture<Void> submitMetrics(String nodeId, MetricCollection metrics) {
        LOG.info("Submitting {} metrics for node: {}", metrics.getMetrics().size(), nodeId);
        
        return CompletableFuture.runAsync(() -> {
            try {
                openNMSClient.submitMetrics(nodeId, metrics);
                LOG.info("Successfully submitted metrics for node: {}", nodeId);
            } catch (Exception e) {
                LOG.error("Error submitting metrics for node: " + nodeId, e);
                throw new CompletionException(e);
            }
        }, executorService);
    }
    
    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down DefaultCollectionService");
        
        // Cancel all scheduled tasks
        scheduledTasks.forEach((id, task) -> task.cancel(false));
        scheduledTasks.clear();
        
        // Shutdown executor
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Process collection result by submitting metrics to OpenNMS
     * @param result the collection result
     * @param resource the cloud resource
     */
    private void processCollectionResult(CollectionResult result, CloudResource resource) {
        LOG.info("Processing collection result with {} metrics for resource: {}", 
                result.getMetrics().getMetrics().size(), resource.getResourceId());
        
        try {
            // Get OpenNMS node ID for this resource
            String nodeId = lookupNodeId(resource);
            if (nodeId == null) {
                LOG.warn("No OpenNMS node found for resource: {}", resource.getResourceId());
                return;
            }
            
            // Submit metrics to OpenNMS
            openNMSClient.submitMetrics(nodeId, result.getMetrics());
            
            LOG.info("Successfully processed metrics for resource: {}", resource.getResourceId());
        } catch (Exception e) {
            LOG.error("Error processing collection result for resource: " + resource.getResourceId(), e);
            throw new RuntimeException("Failed to process collection result", e);
        }
    }
    
    /**
     * Get the provider for a given resource
     * @param resource the cloud resource
     * @return the cloud provider
     */
    private CloudProvider getProviderForResource(CloudResource resource) {
        // In a real implementation, we'd have a more robust way to lookup or track
        // which provider a resource belongs to
        return resourceProviders.get(resource.getResourceId());
    }
    
    /**
     * Lookup OpenNMS node ID for a given resource
     * @param resource the cloud resource
     * @return the OpenNMS node ID
     */
    private String lookupNodeId(CloudResource resource) {
        // In a real implementation, we would query OpenNMS by foreign ID
        // or other identifying information to get the OpenNMS node ID
        // For this example, we'll assume the resource ID maps to a node ID
        try {
            return openNMSClient.findNodeByForeignId(
                "cloud-" + resource.getProviderType(),
                resource.getResourceId()
            );
        } catch (Exception e) {
            LOG.error("Error looking up node ID for resource: " + resource.getResourceId(), e);
            return null;
        }
    }
}