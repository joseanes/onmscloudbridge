package org.opennms.bridge.core.service;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Default implementation of the Discovery Service.
 * Handles the discovery of cloud resources and scheduling of discovery tasks.
 */
@Service
public class DefaultDiscoveryService implements DiscoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDiscoveryService.class);

    private final OpenNMSClient openNMSClient;
    private final TaskScheduler taskScheduler;
    private final ExecutorService executorService;
    
    @Autowired
    private List<CloudProvider> cloudProviders;
    
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, DiscoveryConfiguration> providerConfigurations = new ConcurrentHashMap<>();
    private final Map<String, DiscoveryStatus> discoveryStatuses = new ConcurrentHashMap<>();
    
    // Cache of discovered resources for quicker access
    private final Map<String, Set<CloudResource>> discoveredResourcesCache = new ConcurrentHashMap<>();

    @Autowired
    public DefaultDiscoveryService(OpenNMSClient openNMSClient, TaskScheduler taskScheduler) {
        this.openNMSClient = openNMSClient;
        this.taskScheduler = taskScheduler;
        this.executorService = Executors.newCachedThreadPool();
    }

    @Override
    public CompletableFuture<Set<DiscoveredNode>> discoverNodes(CloudProvider provider) {
        LOG.info("Starting discovery for provider: {}", provider.getProviderId());
        
        DiscoveryStatus status = new DiscoveryStatus();
        status.setProviderId(provider.getProviderId());
        status.setProviderType(provider.getProviderType());
        status.setLastStartTime(Instant.now());
        status.setStatus("RUNNING");
        discoveryStatuses.put(provider.getProviderId(), status);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.debug("Executing discovery for provider: {}", provider.getProviderId());
                
                // Get cloud resources from provider
                Set<CloudResource> resources = provider.discover();
                LOG.info("Discovered {} resources from provider: {}", resources.size(), provider.getProviderId());
                
                // Cache the discovered resources
                discoveredResourcesCache.put(provider.getProviderId(), resources);
                
                // Convert to discovered nodes
                Set<DiscoveredNode> nodes = resources.stream()
                    .map(resource -> convertToDiscoveredNode(resource, provider))
                    .collect(Collectors.toSet());
                
                // Update discovery status
                status.setStatus("COMPLETED");
                status.setLastEndTime(Instant.now());
                status.setLastDiscoveredCount(nodes.size());
                status.setLastSuccessTime(Instant.now());
                
                return nodes;
            } catch (Exception e) {
                LOG.error("Error during discovery for provider: " + provider.getProviderId(), e);
                
                // Update discovery status
                status.setStatus("FAILED");
                status.setLastEndTime(Instant.now());
                status.setLastError(e.getMessage());
                
                throw new CompletionException(e);
            }
        }, executorService);
    }
    
    @Override
    public Set<CloudResource> discoverResources(String providerId) throws CloudProviderException {
        LOG.info("Discovering resources for provider: {}", providerId);
        
        // Find the provider with the given ID
        CloudProvider provider = cloudProviders.stream()
                .filter(p -> p.getProviderId().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new CloudProviderException("Provider not found: " + providerId));
        
        return discoverResources(provider);
    }
    
    @Override
    public Set<CloudResource> discoverResources(CloudProvider provider) throws CloudProviderException {
        LOG.info("Discovering resources for provider: {}", provider.getProviderId());
        
        // Check if we have cached resources
        if (discoveredResourcesCache.containsKey(provider.getProviderId())) {
            Set<CloudResource> cachedResources = discoveredResourcesCache.get(provider.getProviderId());
            if (cachedResources != null && !cachedResources.isEmpty()) {
                LOG.debug("Using cached resources for provider: {}", provider.getProviderId());
                return cachedResources;
            }
        }
        
        // If no cached resources, discover new ones
        try {
            Set<CloudResource> resources = provider.discover();
            LOG.info("Discovered {} resources from provider: {}", resources.size(), provider.getProviderId());
            
            // Update discovery status
            DiscoveryStatus status = new DiscoveryStatus();
            status.setProviderId(provider.getProviderId());
            status.setProviderType(provider.getProviderType());
            status.setLastStartTime(Instant.now());
            status.setLastEndTime(Instant.now());
            status.setLastSuccessTime(Instant.now());
            status.setStatus("COMPLETED");
            status.setLastDiscoveredCount(resources.size());
            discoveryStatuses.put(provider.getProviderId(), status);
            
            // Cache the discovered resources
            discoveredResourcesCache.put(provider.getProviderId(), resources);
            
            return resources;
        } catch (Exception e) {
            LOG.error("Error discovering resources for provider: {}", provider.getProviderId(), e);
            
            // Update discovery status
            DiscoveryStatus status = new DiscoveryStatus();
            status.setProviderId(provider.getProviderId());
            status.setProviderType(provider.getProviderType());
            status.setLastStartTime(Instant.now());
            status.setLastEndTime(Instant.now());
            status.setStatus("FAILED");
            status.setLastError(e.getMessage());
            discoveryStatuses.put(provider.getProviderId(), status);
            
            throw new CloudProviderException("Failed to discover resources for provider: " + provider.getProviderId(), e);
        }
    }

    @Override
    public void scheduleDiscovery(CloudProvider provider, DiscoveryConfiguration configuration) {
        LOG.info("Scheduling discovery for provider: {} with interval: {}", 
                provider.getProviderId(), configuration.getInterval());
        
        // Cancel existing scheduled task if present
        stopDiscovery(provider.getProviderId());
        
        // Store configuration
        providerConfigurations.put(provider.getProviderId(), configuration);
        
        // Schedule new task
        ScheduledFuture<?> scheduledTask = taskScheduler.scheduleAtFixedRate(
            () -> {
                try {
                    discoverNodes(provider)
                        .thenAccept(nodes -> processDiscoveredNodes(nodes, provider))
                        .exceptionally(ex -> {
                            LOG.error("Scheduled discovery failed for provider: " + provider.getProviderId(), ex);
                            return null;
                        });
                } catch (Exception e) {
                    LOG.error("Error in scheduled discovery task for provider: " + provider.getProviderId(), e);
                }
            },
            new Date(System.currentTimeMillis() + java.time.Duration.ofMinutes(configuration.getInitialDelay()).toMillis()),
            java.time.Duration.ofMinutes(configuration.getInterval()).toMillis()
        );
        
        scheduledTasks.put(provider.getProviderId(), scheduledTask);
        
        LOG.info("Discovery scheduled for provider: {}", provider.getProviderId());
    }

    @Override
    public void stopDiscovery(String providerId) {
        LOG.info("Stopping discovery for provider: {}", providerId);
        
        ScheduledFuture<?> task = scheduledTasks.remove(providerId);
        if (task != null) {
            task.cancel(false);
            LOG.info("Discovery stopped for provider: {}", providerId);
        }
        
        providerConfigurations.remove(providerId);
    }

    @Override
    public Set<DiscoveryStatus> getDiscoveryStatus() {
        return new HashSet<>(discoveryStatuses.values());
    }
    
    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down DefaultDiscoveryService");
        
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
     * Process discovered nodes by submitting them to OpenNMS
     * @param nodes the discovered nodes
     * @param provider the cloud provider
     */
    private void processDiscoveredNodes(Set<DiscoveredNode> nodes, CloudProvider provider) {
        LOG.info("Processing {} discovered nodes from provider: {}", nodes.size(), provider.getProviderId());
        
        try {
            // Create requisition for this provider
            String foreignSource = "cloud-" + provider.getProviderType() + "-" + provider.getProviderId();
            openNMSClient.createOrUpdateRequisition(foreignSource, nodes);
            
            // Synchronize requisition to apply changes
            openNMSClient.synchronizeRequisition(foreignSource);
            
            LOG.info("Successfully processed discovered nodes for provider: {}", provider.getProviderId());
        } catch (Exception e) {
            LOG.error("Error processing discovered nodes for provider: " + provider.getProviderId(), e);
            throw new RuntimeException("Failed to process discovered nodes", e);
        }
    }
    
    /**
     * Convert a cloud resource to a discovered node
     * @param resource the cloud resource
     * @param provider the cloud provider
     * @return the discovered node
     */
    private DiscoveredNode convertToDiscoveredNode(CloudResource resource, CloudProvider provider) {
        // Implementation would convert cloud resource information to OpenNMS node format
        DiscoveredNode node = new DiscoveredNode();
        // Set node properties based on resource
        // This is simplified and would need proper implementation
        return node;
    }
}