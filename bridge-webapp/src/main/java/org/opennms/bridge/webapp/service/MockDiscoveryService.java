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
 * Mock implementation of the Discovery Service for demo purposes.
 */
@Service
@Primary
public class MockDiscoveryService implements DiscoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(MockDiscoveryService.class);

    @Autowired
    private List<CloudProvider> cloudProviders;
    
    private final Map<String, Set<CloudResource>> resourcesCache = new ConcurrentHashMap<>();
    private final Map<String, DiscoveryStatus> statuses = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> activeJobs = new ConcurrentHashMap<>();
    
    @Override
    public CompletableFuture<Set<DiscoveredNode>> discoverNodes(CloudProvider provider) {
        LOG.info("Mock discovering nodes for provider: {}", provider.getProviderId());
        // Create an empty set of discovered nodes
        Set<DiscoveredNode> nodes = new HashSet<>();
        
        // Simulate async discovery
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulate network delay
                Thread.sleep(500);
                // In a real implementation, this would convert cloud resources to OpenNMS nodes
                return nodes;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Collections.emptySet();
            }
        });
    }
    
    @Override
    public Set<CloudResource> discoverResources(String providerId) throws CloudProviderException {
        LOG.info("Mock discovering resources for provider: {}", providerId);
        
        // Find the corresponding provider
        CloudProvider provider = cloudProviders.stream()
                .filter(p -> p.getProviderId().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new CloudProviderException("Provider not found: " + providerId));
        
        // Check if we have a cached result
        if (resourcesCache.containsKey(providerId)) {
            return resourcesCache.get(providerId);
        }
        
        try {
            // Get resources from the provider
            Set<CloudResource> resources = provider.discover();
            
            // Cache the resources
            resourcesCache.put(providerId, resources);
            
            // Update status
            updateDiscoveryStatus(providerId, provider.getProviderType(), resources.size());
            
            return resources;
        } catch (Exception e) {
            LOG.error("Error discovering resources for provider {}: {}", providerId, e.getMessage(), e);
            throw new CloudProviderException("Failed to discover resources for provider " + providerId, e);
        }
    }
    
    @Override
    public Set<CloudResource> discoverResources(CloudProvider provider) throws CloudProviderException {
        return discoverResources(provider.getProviderId());
    }
    
    @Override
    public void scheduleDiscovery(CloudProvider provider, DiscoveryConfiguration configuration) {
        LOG.info("Mock scheduling discovery for provider: {}", provider.getProviderId());
        
        // In a real implementation, this would schedule discovery using a task scheduler
        // For now, we'll just update the status to show it's scheduled
        
        DiscoveryStatus status = statuses.computeIfAbsent(provider.getProviderId(), id -> {
            DiscoveryStatus s = new DiscoveryStatus();
            s.setProviderId(id);
            s.setProviderType(provider.getProviderType());
            return s;
        });
        
        status.setScheduled(true);
        status.setScheduleInterval(Duration.ofMinutes(configuration.getInterval()));
        status.setNextScheduledRun(Instant.now().plus(Duration.ofMinutes(configuration.getInterval())));
    }
    
    @Override
    public void stopDiscovery(String providerId) {
        LOG.info("Mock stopping discovery for provider: {}", providerId);
        
        // Cancel active jobs
        CompletableFuture<Void> job = activeJobs.remove(providerId);
        if (job != null && !job.isDone()) {
            job.cancel(true);
        }
        
        // Update status
        DiscoveryStatus status = statuses.get(providerId);
        if (status != null) {
            status.setScheduled(false);
            status.setStatus("STOPPED");
        }
    }
    
    @Override
    public Set<DiscoveryStatus> getDiscoveryStatus() {
        return new HashSet<>(statuses.values());
    }
    
    /**
     * Start asynchronous discovery for a provider.
     * 
     * @param providerId the provider ID
     * @return job ID
     */
    public String startAsyncDiscovery(String providerId) {
        LOG.info("Starting async discovery for provider: {}", providerId);
        
        // Generate job ID
        String jobId = UUID.randomUUID().toString();
        
        // Find provider
        CloudProvider provider = cloudProviders.stream()
                .filter(p -> p.getProviderId().equals(providerId))
                .findFirst()
                .orElse(null);
        
        if (provider == null) {
            LOG.warn("Provider not found: {}", providerId);
            return jobId;
        }
        
        // Update status to in progress
        DiscoveryStatus status = statuses.computeIfAbsent(providerId, id -> {
            DiscoveryStatus s = new DiscoveryStatus();
            s.setProviderId(id);
            s.setProviderType(provider.getProviderType());
            return s;
        });
        
        status.setStatus("IN_PROGRESS");
        status.setLastStartTime(Instant.now());
        status.setJobId(jobId);
        
        // Start async job
        CompletableFuture<Void> job = CompletableFuture.runAsync(() -> {
            try {
                // Simulate delay
                Thread.sleep(3000);
                
                // Get resources from the provider
                Set<CloudResource> resources = provider.discover();
                
                // Cache the resources
                resourcesCache.put(providerId, resources);
                
                // Update status
                updateDiscoveryStatus(providerId, provider.getProviderType(), resources.size());
            } catch (Exception e) {
                LOG.error("Error in async discovery for provider {}: {}", providerId, e.getMessage(), e);
                
                // Update status on error
                DiscoveryStatus errorStatus = statuses.get(providerId);
                if (errorStatus != null) {
                    errorStatus.setStatus("ERROR");
                    errorStatus.setLastEndTime(Instant.now());
                    errorStatus.setLastError(e.getMessage());
                }
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
     * Update discovery status after a successful discovery.
     * 
     * @param providerId provider ID
     * @param providerType provider type
     * @param resourceCount number of discovered resources
     */
    private void updateDiscoveryStatus(String providerId, String providerType, int resourceCount) {
        DiscoveryStatus status = statuses.computeIfAbsent(providerId, id -> {
            DiscoveryStatus s = new DiscoveryStatus();
            s.setProviderId(id);
            s.setProviderType(providerType);
            return s;
        });
        
        status.setLastStartTime(status.getLastStartTime() != null ? status.getLastStartTime() : Instant.now().minusSeconds(30));
        status.setLastEndTime(Instant.now());
        status.setLastSuccessTime(Instant.now());
        status.setStatus("COMPLETED");
        status.setLastDiscoveredCount(resourceCount);
        
        if (status.isScheduled()) {
            status.setNextScheduledRun(Instant.now().plus(status.getScheduleInterval()));
        }
    }
    
    /**
     * Get all discovery jobs.
     * 
     * @return list of job information
     */
    public List<Map<String, Object>> getAllDiscoveryJobs() {
        // Convert statuses to job information
        return statuses.values().stream()
                .sorted(Comparator.comparing(DiscoveryStatus::getLastStartTime, 
                        Comparator.nullsFirst(Comparator.reverseOrder())))
                .map(this::convertToJobInfo)
                .collect(Collectors.toList());
    }
    
    /**
     * Convert discovery status to job information map.
     * 
     * @param status discovery status
     * @return job information
     */
    private Map<String, Object> convertToJobInfo(DiscoveryStatus status) {
        Map<String, Object> jobInfo = new HashMap<>();
        
        jobInfo.put("jobId", status.getJobId() != null ? status.getJobId() : UUID.randomUUID().toString());
        jobInfo.put("providerId", status.getProviderId());
        jobInfo.put("providerType", status.getProviderType());
        jobInfo.put("status", status.getStatus());
        jobInfo.put("startTime", status.getLastStartTime());
        jobInfo.put("endTime", status.getLastEndTime());
        jobInfo.put("resourceCount", status.getLastDiscoveredCount());
        jobInfo.put("scheduled", status.isScheduled());
        jobInfo.put("nextRun", status.getNextScheduledRun());
        
        if (status.getLastError() != null) {
            jobInfo.put("error", status.getLastError());
        }
        
        return jobInfo;
    }
}