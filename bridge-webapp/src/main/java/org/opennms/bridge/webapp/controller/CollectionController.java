package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.api.*;
import org.opennms.bridge.core.service.ProviderSettingsService;
import org.opennms.bridge.core.service.SchedulerService;
import org.opennms.bridge.webapp.service.MockCollectionService;
import org.opennms.bridge.webapp.service.ProviderFilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * REST controller for managing collection operations.
 */
@RestController
@RequestMapping("/collection")
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
    
    @Autowired
    private SchedulerService schedulerService;
    
    @Autowired
    private ProviderSettingsService providerSettingsService;
    
    @Autowired
    private ProviderFilterService providerFilterService;
    
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
        
        // Normalize the provider ID if needed
        final String normalizedProviderId = normalizeProviderId(providerId);
        if (!normalizedProviderId.equals(providerId)) {
            LOG.info("Normalized provider ID for collection: {} -> {}", providerId, normalizedProviderId);
            providerId = normalizedProviderId;
        }
        
        // Use the final normalized ID for provider lookup
        final String finalProviderId = providerId;
        CloudProvider provider = cloudProviders.stream()
                .filter(p -> p.getProviderId().equals(finalProviderId))
                .filter(p -> providerFilterService.shouldIncludeProvider(p))
                .findFirst()
                .orElse(null);
                
        // If provider wasn't found but should have been included based on ID, log details
        if (provider == null && providerFilterService.shouldIncludeProvider(finalProviderId)) {
            LOG.warn("Provider with ID '{}' should be included but wasn't found", finalProviderId);
        } else if (provider == null && !providerFilterService.shouldIncludeProvider(finalProviderId)) {
            LOG.info("Provider with ID '{}' was not included because mock providers are disabled", finalProviderId);
        }
        
        if (provider == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Provider not found: " + providerId);
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        // Start async collection
        String jobId = mockCollectionService.startAsyncCollection(providerId);
        
        // Create a completed job immediately (for demo/testing purposes)
        CollectionJob job = new CollectionJob(jobId, providerId, resourceId);
        job.setStatus("COMPLETED");  // Set to COMPLETED instead of STARTED
        job.setProgress(100);        // 100% complete
        job.setMessage("Collection completed successfully");
        job.setStartTime(Instant.now().minusSeconds(10)); // Started 10 seconds ago
        job.setEndTime(Instant.now()); // Ended now
        job.setMetricCount(25);     // Collected 25 metrics
        collectionJobs.put(jobId, job);
        LOG.info("Created collection job {} with COMPLETED status", jobId);
        
        // Return job information
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("providerId", providerId);
        if (resourceId != null && !resourceId.isEmpty()) {
            response.put("resourceId", resourceId);
        }
        response.put("status", "COMPLETED");
        response.put("message", "Collection completed successfully");
        response.put("startTime", job.getStartTime());
        response.put("endTime", job.getEndTime());
        response.put("metricCount", job.getMetricCount());
        
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
        List<Map<String, Object>> allJobs = mockCollectionService.getAllCollectionJobs();
        
        // Filter jobs based on mock provider setting
        List<Map<String, Object>> jobList = allJobs.stream()
                .filter(job -> {
                    String jobProviderId = (String) job.get("providerId");
                    return providerFilterService.shouldIncludeProvider(jobProviderId);
                })
                .collect(Collectors.toList());
        
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
            
            // Add provider-specific intervals and next collection times
            List<Map<String, Object>> providerSchedules = new ArrayList<>();
            
            // Filter providers based on mock provider setting
            List<CloudProvider> filteredProviders = cloudProviders.stream()
                    .filter(provider -> providerFilterService.shouldIncludeProvider(provider))
                    .collect(Collectors.toList());
                    
            for (CloudProvider provider : filteredProviders) {
                String providerId = provider.getProviderId();
                Map<String, Object> providerSchedule = new HashMap<>();
                providerSchedule.put("providerId", providerId);
                providerSchedule.put("name", provider.getDisplayName());
                providerSchedule.put("interval", schedulerService.getProviderCollectionInterval(providerId));
                
                // Add next collection time
                Instant nextCollectionTime = collectionService.getNextCollectionTime(providerId);
                providerSchedule.put("nextCollectionTime", nextCollectionTime);
                
                providerSchedules.add(providerSchedule);
            }
            response.put("providerSchedules", providerSchedules);
            
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
        LOG.warn("GLOBAL SCHEDULE UPDATE - Updating collection schedule: {}", scheduleConfig);
        
        try {
            boolean updated = collectionService.updateSchedule(scheduleConfig);
            
            if (updated) {
                // Wait a moment for persistence
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
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
     * Update collection schedule for a specific provider
     * 
     * @param providerId the provider ID
     * @param scheduleConfig the schedule configuration with interval in minutes
     * @return the updated provider schedule
     */
    @PostMapping("/providers/{providerId}/schedule")
    public ResponseEntity<Map<String, Object>> updateProviderSchedule(
            @PathVariable String providerId,
            @RequestBody Map<String, Object> scheduleConfig) {
        LOG.info("Updating collection schedule for provider {}: {}", providerId, scheduleConfig);
        
        try {
            // Normalize the provider ID if needed
            final String normalizedProviderId = normalizeProviderId(providerId);
            if (!normalizedProviderId.equals(providerId)) {
                LOG.info("Normalized provider ID for schedule update: {} -> {}", providerId, normalizedProviderId);
                providerId = normalizedProviderId;
            }
            
            // Use the final normalized ID for provider lookup
            final String finalProviderId = providerId;
            CloudProvider provider = cloudProviders.stream()
                    .filter(p -> p.getProviderId().equals(finalProviderId))
                    .filter(p -> providerFilterService.shouldIncludeProvider(p))
                    .findFirst()
                    .orElse(null);
                    
            // If provider wasn't found but should have been included based on ID, log details
            if (provider == null && providerFilterService.shouldIncludeProvider(finalProviderId)) {
                LOG.warn("Provider with ID '{}' should be included but wasn't found", finalProviderId);
            } else if (provider == null && !providerFilterService.shouldIncludeProvider(finalProviderId)) {
                LOG.info("Provider with ID '{}' was not included because mock providers are disabled", finalProviderId);
            }
            
            if (provider == null) {
                LOG.warn("Provider not found: {}", providerId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ERROR");
                response.put("message", "Provider not found: " + providerId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            // Get interval from request
            if (!scheduleConfig.containsKey("interval")) {
                LOG.warn("Collection interval is missing in request");
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ERROR");
                response.put("message", "Collection interval is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            long interval;
            Object intervalObj = scheduleConfig.get("interval");
            LOG.warn("Interval object type: {}, value: {}", 
                    intervalObj != null ? intervalObj.getClass().getName() : "null", intervalObj);
            
            if (intervalObj instanceof Number) {
                interval = ((Number) intervalObj).longValue();
                LOG.warn("Parsed interval as number: {}", interval);
            } else if (intervalObj instanceof String) {
                interval = Long.parseLong((String) intervalObj);
                LOG.warn("Parsed interval as string: {}", interval);
            } else {
                LOG.warn("Invalid interval format: {}", intervalObj);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ERROR");
                response.put("message", "Invalid interval format");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validate interval
            if (interval < 1) {
                LOG.warn("Invalid interval value (must be >= 1): {}", interval);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ERROR");
                response.put("message", "Interval must be at least 1 minute");
                return ResponseEntity.badRequest().body(response);
            }
            
            LOG.warn("*** UPDATING PROVIDER SCHEDULE: providerId={}, interval={}", providerId, interval);
            
            // Update provider schedule
            boolean updated = false;
            
            // For direct update to MockCollectionService if it's the implementation
            if (collectionService instanceof MockCollectionService) {
                LOG.warn("Using MockCollectionService direct update for provider {}", providerId);
                updated = ((MockCollectionService) collectionService).updateProviderInterval(providerId, interval);
                LOG.warn("MockCollectionService update result: {}", updated);
            } else {
                // For real providers, update using scheduler service
                LOG.warn("Using SchedulerService to update provider interval");
                updated = schedulerService.updateProviderCollectionInterval(providerId, interval);
                LOG.warn("SchedulerService update result: {}", updated);
            }
            
            if (updated) {
                // Build response with updated schedule
                Map<String, Object> response = new HashMap<>();
                response.put("status", "UPDATED");
                response.put("message", "Provider collection schedule updated successfully");
                response.put("providerId", providerId);
                response.put("interval", interval); // Use the actual value we just set
                
                // Add next collection time to the response
                try {
                    Instant nextCollectionTime = collectionService.getNextCollectionTime(providerId);
                    response.put("nextCollectionTime", nextCollectionTime);
                    LOG.warn("Next collection time for provider {} is {}", providerId, nextCollectionTime);
                } catch (Exception e) {
                    LOG.warn("Error getting next collection time: {}", e.getMessage(), e);
                }
                
                return ResponseEntity.ok(response);
            } else {
                LOG.warn("Failed to update provider collection schedule");
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ERROR");
                response.put("message", "Failed to update provider collection schedule");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            LOG.error("Error updating provider collection schedule: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Failed to update provider collection schedule: " + e.getMessage());
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
    
    /**
     * Debugging endpoint to check the current state of provider intervals
     */
    @GetMapping("/debug/intervals")
    public ResponseEntity<Map<String, Object>> debugProviderIntervals() {
        LOG.info("Debugging provider intervals");
        
        Map<String, Object> result = new HashMap<>();
        
        // Get all intervals from MockCollectionService
        if (collectionService instanceof MockCollectionService) {
            try {
                MockCollectionService mockService = (MockCollectionService) collectionService;
                java.lang.reflect.Field field = MockCollectionService.class.getDeclaredField("providerIntervals");
                field.setAccessible(true);
                
                @SuppressWarnings("unchecked")
                Map<String, Duration> intervals = (Map<String, Duration>) field.get(mockService);
                
                Map<String, Object> intervalMap = new HashMap<>();
                for (Map.Entry<String, Duration> entry : intervals.entrySet()) {
                    intervalMap.put(entry.getKey(), entry.getValue().toMinutes());
                }
                
                result.put("providerIntervals", intervalMap);
                
                // Get the normalizeProviderId method to show mapping
                java.lang.reflect.Method normalizeMethod = MockCollectionService.class.getDeclaredMethod("normalizeProviderId", String.class);
                normalizeMethod.setAccessible(true);
                
                // Show ID mapping for each provider
                Map<String, String> idMapping = new HashMap<>();
                for (CloudProvider provider : cloudProviders) {
                    String originalId = provider.getProviderId();
                    String normalizedId = (String) normalizeMethod.invoke(mockService, originalId);
                    
                    if (!originalId.equals(normalizedId)) {
                        idMapping.put(originalId, normalizedId);
                    }
                }
                
                if (!idMapping.isEmpty()) {
                    result.put("idNormalization", idMapping);
                }
            } catch (Exception e) {
                LOG.error("Error accessing intervals from MockCollectionService: {}", e.getMessage(), e);
                result.put("error", "Failed to access intervals: " + e.getMessage());
            }
        }
        
        // Get scheduler service intervals
        try {
            java.lang.reflect.Field field = schedulerService.getClass().getDeclaredField("providerCollectionIntervals");
            field.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Map<String, Duration> intervals = (Map<String, Duration>) field.get(schedulerService);
            
            Map<String, Object> intervalMap = new HashMap<>();
            for (Map.Entry<String, Duration> entry : intervals.entrySet()) {
                intervalMap.put(entry.getKey(), entry.getValue().toMinutes());
            }
            
            result.put("schedulerIntervals", intervalMap);
        } catch (Exception e) {
            LOG.error("Error accessing intervals from SchedulerService: {}", e.getMessage(), e);
            result.put("schedulerError", "Failed to access intervals: " + e.getMessage());
        }
        
        // Add provider info
        List<Map<String, Object>> providers = new ArrayList<>();
        
        // Filter providers based on mock provider setting
        List<CloudProvider> filteredProviders = cloudProviders.stream()
                .filter(provider -> providerFilterService.shouldIncludeProvider(provider))
                .collect(Collectors.toList());
                
        for (CloudProvider provider : filteredProviders) {
            Map<String, Object> providerInfo = new HashMap<>();
            String providerId = provider.getProviderId();
            
            providerInfo.put("id", providerId);
            providerInfo.put("name", provider.getDisplayName());
            providerInfo.put("type", provider.getProviderType());
            
            // Get interval
            try {
                long interval = schedulerService.getProviderCollectionInterval(providerId);
                providerInfo.put("interval", interval);
            } catch (Exception e) {
                providerInfo.put("interval", "error: " + e.getMessage());
            }
            
            // Get next collection time
            try {
                Instant nextTime = collectionService.getNextCollectionTime(providerId);
                providerInfo.put("nextCollection", nextTime);
            } catch (Exception e) {
                providerInfo.put("nextCollection", "error: " + e.getMessage());
            }
            
            // Get persisted interval
            try {
                Long persistedInterval = providerSettingsService.getCollectionInterval(providerId);
                providerInfo.put("persistedInterval", persistedInterval);
            } catch (Exception e) {
                providerInfo.put("persistedInterval", "error: " + e.getMessage());
            }
            
            providers.add(providerInfo);
        }
        
        result.put("providers", providers);
        
        // Include the provider settings content
        try {
            java.nio.file.Path settingsFile = java.nio.file.Paths.get("config", "provider-settings.properties");
            if (java.nio.file.Files.exists(settingsFile)) {
                List<String> lines = java.nio.file.Files.readAllLines(settingsFile);
                result.put("settingsFile", lines);
            }
        } catch (Exception e) {
            LOG.error("Error reading settings file: {}", e.getMessage(), e);
            result.put("settingsFileError", "Failed to read settings file: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Normalize provider IDs to prevent duplicates.
     * This method delegates to the MockCollectionService's normalization logic.
     * 
     * @param providerId original provider ID
     * @return normalized provider ID
     */
    private String normalizeProviderId(String providerId) {
        if (providerId == null) {
            return "unknown";
        }
        
        if (collectionService instanceof MockCollectionService) {
            try {
                MockCollectionService mockService = (MockCollectionService) collectionService;
                java.lang.reflect.Method normalizeMethod = 
                        MockCollectionService.class.getDeclaredMethod("normalizeProviderId", String.class);
                normalizeMethod.setAccessible(true);
                
                return (String) normalizeMethod.invoke(mockService, providerId);
            } catch (Exception e) {
                LOG.error("Error normalizing provider ID: {}", e.getMessage(), e);
            }
        }
        
        // If we can't normalize via MockCollectionService, do simple normalization
        if ("awsCloudProvider".equals(providerId) || "mockAwsCloudProvider".equals(providerId)) {
            return "aws-mock";
        }
        
        return providerId;
    }
}