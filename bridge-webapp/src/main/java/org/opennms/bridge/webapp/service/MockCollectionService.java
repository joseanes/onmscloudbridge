package org.opennms.bridge.webapp.service;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;

/**
 * Mock implementation of the Collection Service for demo purposes.
 */
@Service
@Primary
public class MockCollectionService implements CollectionService {
    private static final Logger LOG = LoggerFactory.getLogger(MockCollectionService.class);

    @Autowired
    private MockDiscoveryService discoveryService;
    
    @Autowired
    private List<CloudProvider> cloudProviders;
    
    private final Map<String, CollectionStatus> resourceStatuses = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> providerJobs = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> activeJobs = new ConcurrentHashMap<>();
    
    private boolean enabled = true;
    private Duration initialDelay = Duration.ofMinutes(1);
    private Duration interval = Duration.ofMinutes(5);
    private Instant lastRun = Instant.now().minus(Duration.ofMinutes(3));
    private Instant nextRun = Instant.now().plus(Duration.ofMinutes(2));
    
    @Override
    public CompletableFuture<CollectionResult> collectMetrics(CloudResource resource) {
        LOG.info("Mock collecting metrics for resource: {}", resource.getResourceId());
        
        String providerId = resource.getProviderId();
        
        // Find the corresponding provider
        CloudProvider provider = cloudProviders.stream()
                .filter(p -> p.getProviderId().equals(providerId))
                .findFirst()
                .orElse(null);
        
        if (provider == null) {
            LOG.warn("Provider not found for resource {}: {}", resource.getResourceId(), providerId);
            return CompletableFuture.failedFuture(new CloudProviderException("Provider not found: " + providerId));
        }
        
        // Simulate async collection
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulate delay
                Thread.sleep(500);
                
                // Collect metrics from provider
                MetricCollection metrics = provider.collect(resource);
                
                // Create result
                CollectionResult result = new CollectionResult();
                result.setResourceId(resource.getResourceId());
                result.setTimestamp(Instant.now());
                result.setMetrics(metrics);
                
                // Update status
                updateResourceStatus(resource, metrics.getMetrics().size());
                
                return result;
            } catch (Exception e) {
                LOG.error("Error collecting metrics for resource {}: {}", resource.getResourceId(), e.getMessage(), e);
                throw new RuntimeException("Failed to collect metrics for resource " + resource.getResourceId(), e);
            }
        });
    }
    
    @Override
    public MetricCollection collectMetrics(String providerId, CloudResource resource) throws CloudProviderException {
        LOG.info("Mock collecting metrics for resource {} from provider {}", 
                resource.getResourceId(), providerId);
        
        // Find the corresponding provider
        CloudProvider provider = cloudProviders.stream()
                .filter(p -> p.getProviderId().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new CloudProviderException("Provider not found: " + providerId));
        
        try {
            // Collect metrics from provider
            MetricCollection metrics = provider.collect(resource);
            
            // Update status
            updateResourceStatus(resource, metrics.getMetrics().size());
            
            return metrics;
        } catch (Exception e) {
            LOG.error("Error collecting metrics for resource {}: {}", resource.getResourceId(), e.getMessage(), e);
            throw new CloudProviderException("Failed to collect metrics for resource " + resource.getResourceId(), e);
        }
    }
    
    @Override
    public List<MetricCollection> collectAllMetrics(String providerId) throws CloudProviderException {
        LOG.info("Mock collecting all metrics for provider {}", providerId);
        
        try {
            // Get resources
            Set<CloudResource> resources = discoveryService.discoverResources(providerId);
            LOG.info("Found {} resources for provider {}", resources.size(), providerId);
            
            // Collect metrics for each resource
            List<MetricCollection> collections = new ArrayList<>();
            for (CloudResource resource : resources) {
                MetricCollection metrics = collectMetrics(providerId, resource);
                collections.add(metrics);
            }
            
            // Update provider job status
            updateProviderJobStatus(providerId, resources.size(), collections.stream()
                    .mapToInt(c -> c.getMetrics().size())
                    .sum());
            
            return collections;
        } catch (Exception e) {
            LOG.error("Error collecting metrics for provider {}: {}", providerId, e.getMessage(), e);
            throw new CloudProviderException("Failed to collect metrics for provider: " + providerId, e);
        }
    }
    
    @Override
    public void scheduleCollection(CloudResource resource, CollectionConfiguration configuration) {
        LOG.info("Mock scheduling collection for resource: {}", resource.getResourceId());
        
        // Create status if it doesn't exist
        CollectionStatus status = resourceStatuses.computeIfAbsent(resource.getResourceId(), id -> {
            CollectionStatus s = new CollectionStatus();
            s.setResourceId(id);
            s.setResourceType(resource.getResourceType());
            return s;
        });
        
        // Update status
        status.setScheduled(true);
        status.setScheduleInterval(configuration.getInterval());
        status.setNextScheduledRun(Instant.now().plus(configuration.getInterval()));
    }
    
    @Override
    public void stopCollection(String resourceId) {
        LOG.info("Mock stopping collection for resource: {}", resourceId);
        
        // Update status
        CollectionStatus status = resourceStatuses.get(resourceId);
        if (status != null) {
            status.setScheduled(false);
            status.setStatus("STOPPED");
        }
    }
    
    @Override
    public Set<CollectionStatus> getCollectionStatus() {
        return new HashSet<>(resourceStatuses.values());
    }
    
    @Override
    public Map<String, Object> getScheduleInfo() {
        Map<String, Object> scheduleInfo = new HashMap<>();
        scheduleInfo.put("enabled", enabled);
        scheduleInfo.put("initialDelay", initialDelay.toMinutes());
        scheduleInfo.put("interval", interval.toMinutes());
        scheduleInfo.put("nextRun", nextRun);
        scheduleInfo.put("lastRun", lastRun);
        return scheduleInfo;
    }
    
    @Override
    public boolean updateSchedule(Map<String, Object> scheduleConfig) {
        try {
            if (scheduleConfig.containsKey("enabled")) {
                enabled = (Boolean) scheduleConfig.get("enabled");
            }
            
            if (scheduleConfig.containsKey("initialDelay")) {
                Object delay = scheduleConfig.get("initialDelay");
                if (delay instanceof Number) {
                    initialDelay = Duration.ofMinutes(((Number) delay).longValue());
                } else if (delay instanceof String) {
                    initialDelay = Duration.ofMinutes(Long.parseLong((String) delay));
                }
            }
            
            if (scheduleConfig.containsKey("interval")) {
                Object intervalObj = scheduleConfig.get("interval");
                if (intervalObj instanceof Number) {
                    interval = Duration.ofMinutes(((Number) intervalObj).longValue());
                } else if (intervalObj instanceof String) {
                    interval = Duration.ofMinutes(Long.parseLong((String) intervalObj));
                }
            }
            
            // Update next run
            nextRun = Instant.now().plus(interval);
            
            return true;
        } catch (Exception e) {
            LOG.error("Error updating collection schedule: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public CompletableFuture<Void> submitMetrics(String nodeId, MetricCollection metrics) {
        LOG.info("Mock submitting {} metrics for node: {}", 
                metrics.getMetrics().size(), nodeId);
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Update resource collection status.
     * 
     * @param resource cloud resource
     * @param metricCount number of metrics collected
     */
    private void updateResourceStatus(CloudResource resource, int metricCount) {
        CollectionStatus status = resourceStatuses.computeIfAbsent(resource.getResourceId(), id -> {
            CollectionStatus s = new CollectionStatus();
            s.setResourceId(id);
            s.setResourceType(resource.getResourceType());
            return s;
        });
        
        status.setLastStartTime(status.getLastStartTime() != null ? status.getLastStartTime() : Instant.now().minusSeconds(10));
        status.setLastEndTime(Instant.now());
        status.setLastSuccessTime(Instant.now());
        status.setStatus("COMPLETED");
        status.setLastMetricCount(metricCount);
        
        if (status.isScheduled()) {
            status.setNextScheduledRun(Instant.now().plus(status.getScheduleInterval()));
        }
    }
    
    /**
     * Update provider job status.
     * 
     * @param providerId provider ID
     * @param resourceCount number of resources
     * @param metricCount number of metrics collected
     */
    private void updateProviderJobStatus(String providerId, int resourceCount, int metricCount) {
        Map<String, Object> jobInfo = providerJobs.computeIfAbsent(providerId, id -> new HashMap<>());
        
        jobInfo.put("providerId", providerId);
        jobInfo.put("startTime", jobInfo.containsKey("startTime") ? jobInfo.get("startTime") : Instant.now().minusSeconds(30));
        jobInfo.put("endTime", Instant.now());
        jobInfo.put("status", "COMPLETED");
        jobInfo.put("resourceCount", resourceCount);
        jobInfo.put("metricCount", metricCount);
        jobInfo.put("jobId", jobInfo.containsKey("jobId") ? jobInfo.get("jobId") : UUID.randomUUID().toString());
    }
    
    /**
     * Start asynchronous collection for a provider.
     * 
     * @param providerId the provider ID
     * @return job ID
     */
    public String startAsyncCollection(String providerId) {
        LOG.info("Starting async collection for provider: {}", providerId);
        
        // Generate job ID
        String jobId = UUID.randomUUID().toString();
        
        // Create job info
        Map<String, Object> jobInfo = providerJobs.computeIfAbsent(providerId, id -> new HashMap<>());
        jobInfo.put("providerId", providerId);
        jobInfo.put("startTime", Instant.now());
        jobInfo.put("status", "IN_PROGRESS");
        jobInfo.put("jobId", jobId);
        
        // Start async job
        CompletableFuture<Void> job = CompletableFuture.runAsync(() -> {
            try {
                // Simulate delay
                Thread.sleep(2000);
                
                // Collect metrics
                List<MetricCollection> collections = collectAllMetrics(providerId);
                
                // Update job info
                jobInfo.put("endTime", Instant.now());
                jobInfo.put("status", "COMPLETED");
                jobInfo.put("metricCount", collections.stream()
                        .mapToInt(c -> c.getMetrics().size())
                        .sum());
            } catch (Exception e) {
                LOG.error("Error in async collection for provider {}: {}", providerId, e.getMessage(), e);
                
                // Update job info on error
                jobInfo.put("endTime", Instant.now());
                jobInfo.put("status", "ERROR");
                jobInfo.put("error", e.getMessage());
            } finally {
                // Remove job from active jobs
                activeJobs.remove(providerId);
            }
        });
        
        // Store job
        activeJobs.put(providerId, job);
        
        return jobId;
    }
    
    /**
     * Get all collection jobs.
     * 
     * @return list of job information
     */
    public List<Map<String, Object>> getAllCollectionJobs() {
        // Return a copy of the provider jobs
        return new ArrayList<>(providerJobs.values());
    }
}