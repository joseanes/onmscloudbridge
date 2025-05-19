package org.opennms.bridge.core.service;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Service for scheduling and managing discovery and collection tasks.
 * This service provides centralized scheduling configuration and management.
 */
@Service
public class SchedulerService {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerService.class);

    private final TaskScheduler taskScheduler;
    private final DiscoveryService discoveryService;
    private final CollectionService collectionService;
    private final Map<String, CloudProvider> cloudProviders;
    
    private final boolean discoveryEnabled;
    private final Duration discoveryInitialDelay;
    private final Duration discoveryInterval;
    
    private final boolean collectionEnabled;
    private final Duration collectionInitialDelay;
    private final Duration collectionInterval;
    
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Autowired
    public SchedulerService(
            TaskScheduler taskScheduler,
            DiscoveryService discoveryService,
            CollectionService collectionService,
            Map<String, CloudProvider> cloudProviders,
            @Value("${scheduler.discovery.enabled:true}") boolean discoveryEnabled,
            @Value("${scheduler.discovery.initial-delay:30s}") String discoveryInitialDelayStr,
            @Value("${scheduler.discovery.interval:5m}") String discoveryIntervalStr,
            @Value("${scheduler.collection.enabled:true}") boolean collectionEnabled,
            @Value("${scheduler.collection.initial-delay:1m}") String collectionInitialDelayStr,
            @Value("${scheduler.collection.interval:60s}") String collectionIntervalStr) {
        
        this.taskScheduler = taskScheduler;
        this.discoveryService = discoveryService;
        this.collectionService = collectionService;
        this.cloudProviders = cloudProviders;
        
        this.discoveryEnabled = discoveryEnabled;
        this.discoveryInitialDelay = parseDuration(discoveryInitialDelayStr, Duration.ofSeconds(30));
        this.discoveryInterval = parseDuration(discoveryIntervalStr, Duration.ofMinutes(5));
        
        this.collectionEnabled = collectionEnabled;
        this.collectionInitialDelay = parseDuration(collectionInitialDelayStr, Duration.ofMinutes(1));
        this.collectionInterval = parseDuration(collectionIntervalStr, Duration.ofSeconds(60));
    }
    
    @PostConstruct
    public void initialize() {
        LOG.info("Initializing SchedulerService");
        
        if (discoveryEnabled) {
            LOG.info("Discovery is enabled with initial delay: {} and interval: {}", 
                    discoveryInitialDelay, discoveryInterval);
            scheduleGlobalDiscovery();
        } else {
            LOG.info("Discovery is disabled");
        }
        
        if (collectionEnabled) {
            LOG.info("Collection is enabled with initial delay: {} and interval: {}", 
                    collectionInitialDelay, collectionInterval);
            scheduleGlobalCollection();
        } else {
            LOG.info("Collection is disabled");
        }
    }
    
    /**
     * Schedule the global discovery task that runs for all providers
     */
    private void scheduleGlobalDiscovery() {
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(
            this::runDiscoveryForAllProviders,
            new Date(System.currentTimeMillis() + discoveryInitialDelay.toMillis()),
            discoveryInterval.toMillis()
        );
        
        scheduledTasks.put("global-discovery", task);
        LOG.info("Scheduled global discovery task");
    }
    
    /**
     * Schedule the global collection task
     */
    private void scheduleGlobalCollection() {
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(
            this::runCollectionForDiscoveredResources,
            new Date(System.currentTimeMillis() + collectionInitialDelay.toMillis()),
            collectionInterval.toMillis()
        );
        
        scheduledTasks.put("global-collection", task);
        LOG.info("Scheduled global collection task");
    }
    
    /**
     * Run discovery for all configured providers
     */
    void runDiscoveryForAllProviders() {
        LOG.info("Running discovery for all providers");
        
        cloudProviders.forEach((id, provider) -> {
            try {
                LOG.debug("Starting discovery for provider: {}", id);
                DiscoveryConfiguration config = new DiscoveryConfiguration();
                config.setInitialDelay(Duration.ZERO); // Run immediately
                config.setInterval(discoveryInterval);
                
                // This will trigger one-time discovery and schedule periodic discovery
                discoveryService.discoverNodes(provider)
                    .exceptionally(ex -> {
                        LOG.error("Discovery failed for provider: " + id, ex);
                        return null;
                    });
            } catch (Exception e) {
                LOG.error("Error scheduling discovery for provider: " + id, e);
            }
        });
    }
    
    /**
     * Run collection for all discovered resources
     */
    void runCollectionForDiscoveredResources() {
        LOG.info("Running collection for discovered resources");
        
        // In a real implementation, this would look up all discovered resources
        // from a registry or database, then trigger collection for each
        
        // For this example, we'll simulate by collecting from a fixed set of resources
        getKnownResources().forEach(resource -> {
            try {
                LOG.debug("Starting collection for resource: {}", resource.getResourceId());
                CloudProvider provider = getProviderForResource(resource);
                
                if (provider != null) {
                    CollectionConfiguration config = new CollectionConfiguration();
                    config.setInitialDelay(Duration.ZERO); // Run immediately 
                    config.setInterval(collectionInterval);
                    config.setProvider(provider);
                    
                    // Trigger one-time collection for this resource
                    collectionService.collectMetrics(resource)
                        .exceptionally(ex -> {
                            LOG.error("Collection failed for resource: " + resource.getResourceId(), ex);
                            return null;
                        });
                }
            } catch (Exception e) {
                LOG.error("Error running collection for resource: " + resource.getResourceId(), e);
            }
        });
    }
    
    /**
     * Schedule a specific discovery task for a provider
     *
     * @param provider the cloud provider
     * @param interval the discovery interval
     */
    public void scheduleProviderDiscovery(CloudProvider provider, Duration interval) {
        if (!discoveryEnabled) {
            LOG.warn("Discovery is disabled globally, not scheduling provider discovery");
            return;
        }
        
        LOG.info("Scheduling discovery for provider: {} with interval: {}", 
                provider.getProviderId(), interval);
        
        DiscoveryConfiguration config = new DiscoveryConfiguration();
        config.setInitialDelay(discoveryInitialDelay);
        config.setInterval(interval);
        
        discoveryService.scheduleDiscovery(provider, config);
        LOG.info("Scheduled discovery for provider: {}", provider.getProviderId());
    }
    
    /**
     * Schedule a specific collection task for a resource
     *
     * @param resource the cloud resource
     * @param provider the cloud provider for this resource
     * @param interval the collection interval
     */
    public void scheduleResourceCollection(CloudResource resource, CloudProvider provider, Duration interval) {
        if (!collectionEnabled) {
            LOG.warn("Collection is disabled globally, not scheduling resource collection");
            return;
        }
        
        LOG.info("Scheduling collection for resource: {} with interval: {}", 
                resource.getResourceId(), interval);
        
        CollectionConfiguration config = new CollectionConfiguration();
        config.setInitialDelay(collectionInitialDelay);
        config.setInterval(interval);
        config.setProvider(provider);
        
        collectionService.scheduleCollection(resource, config);
        LOG.info("Scheduled collection for resource: {}", resource.getResourceId());
    }
    
    /**
     * Cancel all scheduled tasks
     */
    public void cancelAllTasks() {
        LOG.info("Cancelling all scheduled tasks");
        
        scheduledTasks.forEach((id, task) -> {
            task.cancel(false);
            LOG.debug("Cancelled task: {}", id);
        });
        
        scheduledTasks.clear();
        LOG.info("All scheduled tasks cancelled");
    }
    
    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down SchedulerService");
        cancelAllTasks();
    }
    
    /**
     * Parse a duration string into a Duration
     *
     * @param durationStr the duration string (e.g. "30s", "5m")
     * @param defaultDuration the default duration if parsing fails
     * @return the parsed duration
     */
    private Duration parseDuration(String durationStr, Duration defaultDuration) {
        try {
            if (durationStr.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(durationStr.substring(0, durationStr.length() - 2)));
            } else if (durationStr.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(durationStr.substring(0, durationStr.length() - 1)));
            } else if (durationStr.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(durationStr.substring(0, durationStr.length() - 1)));
            } else if (durationStr.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(durationStr.substring(0, durationStr.length() - 1)));
            } else {
                return Duration.ofMillis(Long.parseLong(durationStr));
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse duration: {}, using default: {}", durationStr, defaultDuration);
            return defaultDuration;
        }
    }
    
    /**
     * Get known resources (example implementation)
     *
     * @return set of cloud resources
     */
    private Set<CloudResource> getKnownResources() {
        // In a real implementation, this would look up resources from a registry or database
        // For this example, we're returning an empty set
        return Collections.emptySet();
    }
    
    /**
     * Get provider for a specific resource (example implementation)
     *
     * @param resource the cloud resource
     * @return the cloud provider
     */
    private CloudProvider getProviderForResource(CloudResource resource) {
        // In a real implementation, this would look up the provider from a registry 
        // based on the resource details
        // For this example, we're using a simple approach
        String providerId = resource.getProviderId();
        return cloudProviders.get(providerId);
    }
}