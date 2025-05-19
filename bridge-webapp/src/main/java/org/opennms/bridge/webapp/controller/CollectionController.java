package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.api.*;
import org.opennms.bridge.webapp.service.MockCollectionService;
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
 * REST controller for managing collection operations.
 */
@RestController
@RequestMapping("/api/collection")
public class CollectionController {
    private static final Logger LOG = LoggerFactory.getLogger(CollectionController.class);

    @Autowired
    private CollectionService collectionService;
    
    @Autowired
    private MockCollectionService mockCollectionService;
    
    @Autowired
    private DiscoveryService discoveryService;
    
    @Autowired
    private List<CloudProvider> cloudProviders;
    
    // In-memory job tracking (would be persisted in a real implementation)
    private final Map<String, CollectionJob> collectionJobs = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // Add a dummy job for testing
        String jobId = UUID.randomUUID().toString();
        CollectionJob job = new CollectionJob(jobId, "aws-mock", null);
        job.setStatus("COMPLETED");
        job.setProgress(100);
        job.setMessage("Collection completed successfully");
        job.setStartTime(Instant.now().minusSeconds(30));
        job.setEndTime(Instant.now().minusSeconds(5));
        job.setMetricCount(150);
        collectionJobs.put(jobId, job);
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startCollection(
            @RequestParam String providerId,
            @RequestParam(required = false) String resourceId) {
        LOG.info("Starting collection for provider: {}, resource: {}", providerId, resourceId);
        
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
        
        // Start async collection
        String jobId = mockCollectionService.startAsyncCollection(providerId);
        
        // Create a new job in memory
        CollectionJob job = new CollectionJob(jobId, providerId, resourceId);
        job.setStatus("STARTED");
        job.setProgress(0);
        job.setMessage("Collection job started");
        job.setStartTime(Instant.now());
        collectionJobs.put(jobId, job);
        
        // Return job information
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("providerId", providerId);
        if (resourceId != null && !resourceId.isEmpty()) {
            response.put("resourceId", resourceId);
        }
        response.put("status", "STARTED");
        response.put("message", "Collection job started");
        response.put("startTime", Instant.now());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getCollectionJobStatus(
            @PathVariable String jobId) {
        LOG.debug("Getting status for collection job: {}", jobId);
        
        CollectionJob job = collectionJobs.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getJobId());
        response.put("providerId", job.getProviderId());
        if (job.getResourceId() != null) {
            response.put("resourceId", job.getResourceId());
        }
        response.put("status", job.getStatus());
        response.put("progress", job.getProgress());
        response.put("message", job.getMessage());
        response.put("startTime", job.getStartTime());
        
        if (job.getEndTime() != null) {
            response.put("endTime", job.getEndTime());
        }
        
        if (job.getMetricCount() > 0) {
            response.put("metricCount", job.getMetricCount());
        }
        
        // Include metric summary if collections are available
        if (job.getCollections() != null && !job.getCollections().isEmpty()) {
            List<Map<String, Object>> collectionSummaries = job.getCollections().stream()
                    .map(collection -> {
                        Map<String, Object> summary = new HashMap<>();
                        summary.put("resourceId", collection.getResourceId());
                        summary.put("timestamp", collection.getTimestamp());
                        summary.put("metricCount", collection.getMetrics().size());
                        
                        // Include a sample of metrics (first 10)
                        List<Map<String, Object>> metricSamples = collection.getMetrics().stream()
                                .limit(10)
                                .map(metric -> {
                                    Map<String, Object> metricData = new HashMap<>();
                                    metricData.put("name", metric.getName());
                                    metricData.put("value", metric.getValue());
                                    metricData.put("tags", metric.getTags());
                                    return metricData;
                                })
                                .collect(Collectors.toList());
                        
                        summary.put("metrics", metricSamples);
                        return summary;
                    })
                    .collect(Collectors.toList());
            
            response.put("collections", collectionSummaries);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/jobs")
    public ResponseEntity<Map<String, Object>> getAllCollectionJobs() {
        LOG.debug("Getting all collection jobs");
        
        // Get jobs from mock service
        List<Map<String, Object>> jobList = mockCollectionService.getAllCollectionJobs();
        
        // Add in-memory jobs
        jobList.addAll(collectionJobs.values().stream()
                .map(job -> {
                    Map<String, Object> jobData = new HashMap<>();
                    jobData.put("jobId", job.getJobId());
                    jobData.put("providerId", job.getProviderId());
                    if (job.getResourceId() != null) {
                        jobData.put("resourceId", job.getResourceId());
                    }
                    jobData.put("status", job.getStatus());
                    jobData.put("progress", job.getProgress());
                    jobData.put("message", job.getMessage());
                    jobData.put("startTime", job.getStartTime());
                    
                    if (job.getEndTime() != null) {
                        jobData.put("endTime", job.getEndTime());
                    }
                    
                    if (job.getMetricCount() > 0) {
                        jobData.put("metricCount", job.getMetricCount());
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
    
    @GetMapping("/schedule")
    public ResponseEntity<Map<String, Object>> getCollectionSchedule() {
        LOG.debug("Getting collection schedule");
        
        try {
            Map<String, Object> schedule = collectionService.getScheduleInfo();
            
            Map<String, Object> response = new HashMap<>();
            response.put("enabled", schedule.get("enabled"));
            response.put("initialDelay", schedule.get("initialDelay"));
            response.put("interval", schedule.get("interval"));
            response.put("nextRun", schedule.get("nextRun"));
            response.put("lastRun", schedule.get("lastRun"));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Error getting collection schedule: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to get collection schedule: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    @PostMapping("/schedule")
    public ResponseEntity<Map<String, Object>> updateCollectionSchedule(
            @RequestBody Map<String, Object> scheduleConfig) {
        LOG.info("Updating collection schedule: {}", scheduleConfig);
        
        try {
            boolean updated = collectionService.updateSchedule(scheduleConfig);
            
            if (updated) {
                Map<String, Object> updatedSchedule = collectionService.getScheduleInfo();
                
                Map<String, Object> response = new HashMap<>();
                response.put("status", "UPDATED");
                response.put("message", "Collection schedule updated successfully");
                response.put("schedule", updatedSchedule);
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ERROR");
                response.put("message", "Failed to update collection schedule");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            LOG.error("Error updating collection schedule: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Failed to update collection schedule: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * Collection job tracking class.
     */
    private static class CollectionJob {
        private final String jobId;
        private final String providerId;
        private final String resourceId;
        private String status;
        private int progress;
        private String message;
        private Instant startTime;
        private Instant endTime;
        private int metricCount;
        private List<MetricCollection> collections;
        
        public CollectionJob(String jobId, String providerId, String resourceId) {
            this.jobId = jobId;
            this.providerId = providerId;
            this.resourceId = resourceId;
            this.collections = new ArrayList<>();
        }
        
        public String getJobId() {
            return jobId;
        }
        
        public String getProviderId() {
            return providerId;
        }
        
        public String getResourceId() {
            return resourceId;
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
        
        public int getMetricCount() {
            return metricCount;
        }
        
        public void setMetricCount(int metricCount) {
            this.metricCount = metricCount;
        }
        
        public List<MetricCollection> getCollections() {
            return collections;
        }
        
        public void setCollections(List<MetricCollection> collections) {
            this.collections = collections;
        }
    }
}