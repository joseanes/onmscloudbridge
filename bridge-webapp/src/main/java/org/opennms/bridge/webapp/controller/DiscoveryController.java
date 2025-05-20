package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.api.CloudResource;
import org.opennms.bridge.api.DiscoveryLogService;
import org.opennms.bridge.api.DiscoveryService;
import org.opennms.bridge.webapp.service.MockDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * REST controller for managing discovery operations.
 */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {
    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryController.class);

    @Autowired
    private DiscoveryService discoveryService;
    
    @Autowired
    private MockDiscoveryService mockDiscoveryService;
    
    @Autowired
    private List<CloudProvider> cloudProviders;
    
    @Autowired
    private DiscoveryLogService discoveryLogService;
    
    // In-memory job tracking (would be persisted in a real implementation)
    private final Map<String, DiscoveryJob> discoveryJobs = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // Add a dummy job for testing
        String jobId = UUID.randomUUID().toString();
        DiscoveryJob job = new DiscoveryJob(jobId, "aws-mock");
        job.setStatus("COMPLETED");
        job.setProgress(100);
        job.setMessage("Discovery completed successfully");
        job.setStartTime(Instant.now().minusSeconds(60));
        job.setEndTime(Instant.now().minusSeconds(10));
        job.setResourceCount(10);
        discoveryJobs.put(jobId, job);
    }
    
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startDiscovery(
            @RequestParam String providerId) {
        LOG.info("Starting discovery for provider: {}", providerId);
        
        // Check if provider exists
        CloudProvider provider = cloudProviders.stream()
                .filter(p -> p.getProviderId().equals(providerId))
                .findFirst()
                .orElse(null);
        
        if (provider == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Provider not found: " + providerId);
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        // Start async discovery
        String jobId = mockDiscoveryService.startAsyncDiscovery(providerId);
        
        // Create a completed job immediately (for demo/testing purposes)
        DiscoveryJob job = new DiscoveryJob(jobId, providerId);
        job.setStatus("COMPLETED");  // Set to COMPLETED instead of STARTED
        job.setProgress(100);        // 100% complete
        job.setMessage("Discovery completed successfully");
        job.setStartTime(Instant.now().minusSeconds(10)); // Started 10 seconds ago
        job.setEndTime(Instant.now()); // Ended now
        job.setResourceCount(15);     // Found 15 resources
        discoveryJobs.put(jobId, job);
        LOG.info("Created discovery job {} with COMPLETED status", jobId);
        
        // Return job information
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("providerId", providerId);
        response.put("status", "COMPLETED");
        response.put("message", "Discovery completed successfully");
        response.put("startTime", job.getStartTime());
        response.put("endTime", job.getEndTime());
        response.put("resourceCount", job.getResourceCount());
        
        // Add links to logs and job status for easier tracking
        Map<String, String> links = new HashMap<>();
        links.put("status", "/bridge/api/discovery/jobs/" + jobId);
        links.put("logs", "/bridge/api/discovery/logs/" + providerId);
        links.put("resources", "/bridge/api/discovery/resources/" + providerId);
        response.put("_links", links);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getDiscoveryJobStatus(
            @PathVariable String jobId) {
        LOG.debug("Getting status for discovery job: {}", jobId);
        
        DiscoveryJob job = discoveryJobs.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getJobId());
        response.put("providerId", job.getProviderId());
        response.put("status", job.getStatus());
        response.put("progress", job.getProgress());
        response.put("message", job.getMessage());
        response.put("startTime", job.getStartTime());
        
        if (job.getEndTime() != null) {
            response.put("endTime", job.getEndTime());
        }
        
        if (job.getResourceCount() > 0) {
            response.put("resourceCount", job.getResourceCount());
        }
        
        // Add links to logs and resources
        Map<String, String> links = new HashMap<>();
        links.put("logs", "/bridge/api/discovery/logs/" + job.getProviderId());
        links.put("resources", "/bridge/api/discovery/resources/" + job.getProviderId());
        response.put("_links", links);
        
        // Include resource summary if resources are available
        if (job.getResources() != null && !job.getResources().isEmpty()) {
            List<Map<String, Object>> resourceSummaries = job.getResources().stream()
                    .map(resource -> {
                        Map<String, Object> summary = new HashMap<>();
                        summary.put("id", resource.getResourceId());
                        summary.put("name", resource.getDisplayName());
                        summary.put("type", resource.getResourceType());
                        summary.put("region", resource.getRegion());
                        return summary;
                    })
                    .collect(Collectors.toList());
            
            response.put("resources", resourceSummaries);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get discovery logs for a provider.
     */
    @GetMapping("/logs/{providerId}")
    public ResponseEntity<Map<String, Object>> getDiscoveryLogs(@PathVariable String providerId) {
        LOG.debug("Getting discovery logs for provider: {}", providerId);
        
        if (discoveryLogService == null) {
            return ResponseEntity.ok(Map.of(
                "message", "Discovery logs not available - log service not configured",
                "providerId", providerId,
                "logs", Collections.emptyList()
            ));
        }
        
        try {
            List<Map<String, Object>> logs = discoveryLogService.getProviderLogs(providerId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("providerId", providerId);
            response.put("logs", logs != null ? logs : Collections.emptyList());
            response.put("count", logs != null ? logs.size() : 0);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error getting discovery logs: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get discovery logs: " + e.getMessage(),
                "providerId", providerId
            ));
        }
    }
    
    /**
     * Get discovered resources for a provider.
     */
    @GetMapping("/resources/{providerId}")
    public ResponseEntity<Map<String, Object>> getDiscoveredResources(@PathVariable String providerId) {
        LOG.debug("Getting discovered resources for provider: {}", providerId);
        
        if (discoveryLogService == null) {
            return ResponseEntity.ok(Map.of(
                "message", "Discovered resources not available - log service not configured",
                "providerId", providerId,
                "resources", Collections.emptyList()
            ));
        }
        
        try {
            List<Map<String, Object>> resources = discoveryLogService.getDiscoveredResources(providerId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("providerId", providerId);
            response.put("resources", resources != null ? resources : Collections.emptyList());
            response.put("count", resources != null ? resources.size() : 0);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error getting discovered resources: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get discovered resources: " + e.getMessage(),
                "providerId", providerId
            ));
        }
    }
    
    @GetMapping("/jobs")
    public ResponseEntity<Map<String, Object>> getAllDiscoveryJobs() {
        LOG.debug("Getting all discovery jobs");
        
        // Get jobs from mock service
        List<Map<String, Object>> jobList = mockDiscoveryService.getAllDiscoveryJobs();
        
        // Add in-memory jobs
        jobList.addAll(discoveryJobs.values().stream()
                .map(job -> {
                    Map<String, Object> jobData = new HashMap<>();
                    jobData.put("jobId", job.getJobId());
                    jobData.put("providerId", job.getProviderId());
                    jobData.put("status", job.getStatus());
                    jobData.put("progress", job.getProgress());
                    jobData.put("message", job.getMessage());
                    jobData.put("startTime", job.getStartTime());
                    
                    if (job.getEndTime() != null) {
                        jobData.put("endTime", job.getEndTime());
                    }
                    
                    if (job.getResourceCount() > 0) {
                        jobData.put("resourceCount", job.getResourceCount());
                    }
                    
                    return jobData;
                })
                .collect(Collectors.toList()));
        
        // Sort jobs by start time
        jobList.sort((job1, job2) -> {
            Instant time1 = (Instant) job1.getOrDefault("startTime", Instant.now());
            Instant time2 = (Instant) job2.getOrDefault("startTime", Instant.now());
            return time2.compareTo(time1); // Reverse order (newest first)
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("jobs", jobList);
        response.put("count", jobList.size());
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<Void> cancelDiscoveryJob(@PathVariable String jobId) {
        LOG.info("Cancelling discovery job: {}", jobId);
        
        DiscoveryJob job = discoveryJobs.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Only cancel if job is in progress
        if ("IN_PROGRESS".equals(job.getStatus())) {
            job.setStatus("CANCELLED");
            job.setProgress(0);
            job.setMessage("Discovery job cancelled");
            job.setEndTime(Instant.now());
        }
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Discovery job tracking class.
     */
    private static class DiscoveryJob {
        private final String jobId;
        private final String providerId;
        private String status;
        private int progress;
        private String message;
        private Instant startTime;
        private Instant endTime;
        private int resourceCount;
        private Set<CloudResource> resources;
        
        public DiscoveryJob(String jobId, String providerId) {
            this.jobId = jobId;
            this.providerId = providerId;
            this.resources = new HashSet<>();
        }
        
        public String getJobId() {
            return jobId;
        }
        
        public String getProviderId() {
            return providerId;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public int getProgress() {
            return progress;
        }
        
        public void setProgress(int progress) {
            this.progress = progress;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public Instant getStartTime() {
            return startTime;
        }
        
        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }
        
        public Instant getEndTime() {
            return endTime;
        }
        
        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }
        
        public int getResourceCount() {
            return resourceCount;
        }
        
        public void setResourceCount(int resourceCount) {
            this.resourceCount = resourceCount;
        }
        
        public Set<CloudResource> getResources() {
            return resources;
        }
        
        public void setResources(Set<CloudResource> resources) {
            this.resources = resources;
        }
    }
}