package org.opennms.bridge.api;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

/**
 * Status of metric collection for a cloud resource.
 */
public class CollectionStatus {
    
    public enum State {
        PENDING,
        SCHEDULED,
        RUNNING,
        COMPLETED,
        FAILED,
        DISABLED
    }
    
    private String resourceId;
    private String resourceName;
    private String resourceType;
    private State state;
    private Instant lastCollection;
    private Instant nextCollection;
    private String message;
    private int consecutiveFailures;
    private long totalMetricsCollected;
    private String providerId;
    private String status;
    private Instant lastEndTime;
    private Instant lastSuccessTime;
    private Instant lastStartTime;
    private int lastMetricCount;
    private String lastError;
    private boolean scheduled;
    private Duration scheduleInterval;
    private Instant nextScheduledRun;
    
    public CollectionStatus() {
        this.scheduleInterval = Duration.ofMinutes(5); // Default interval
    }
    
    public CollectionStatus(String resourceId, String resourceName, State state) {
        this();
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.state = state;
    }
    
    public String getResourceId() {
        return resourceId;
    }
    
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    
    public State getState() {
        return state;
    }
    
    public void setState(State state) {
        this.state = state;
    }
    
    public Instant getLastCollection() {
        return lastCollection;
    }
    
    public void setLastCollection(Instant lastCollection) {
        this.lastCollection = lastCollection;
    }
    
    public Instant getNextCollection() {
        return nextCollection;
    }
    
    public void setNextCollection(Instant nextCollection) {
        this.nextCollection = nextCollection;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
    
    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }
    
    public long getTotalMetricsCollected() {
        return totalMetricsCollected;
    }
    
    public void setTotalMetricsCollected(long totalMetricsCollected) {
        this.totalMetricsCollected = totalMetricsCollected;
    }
    
    public String getProviderId() {
        return providerId;
    }
    
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Instant getLastEndTime() {
        return lastEndTime;
    }
    
    public void setLastEndTime(Instant lastEndTime) {
        this.lastEndTime = lastEndTime;
    }
    
    public Instant getLastSuccessTime() {
        return lastSuccessTime;
    }
    
    public void setLastSuccessTime(Instant lastSuccessTime) {
        this.lastSuccessTime = lastSuccessTime;
    }
    
    public Instant getLastStartTime() {
        return lastStartTime;
    }
    
    public void setLastStartTime(Instant lastStartTime) {
        this.lastStartTime = lastStartTime;
    }
    
    public int getLastMetricCount() {
        return lastMetricCount;
    }
    
    public void setLastMetricCount(int lastMetricCount) {
        this.lastMetricCount = lastMetricCount;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    /**
     * Checks if collection is scheduled for this resource.
     * 
     * @return true if scheduled, false otherwise
     */
    public boolean isScheduled() {
        return scheduled;
    }
    
    /**
     * Sets whether collection is scheduled for this resource.
     * 
     * @param scheduled true to enable scheduling, false to disable
     */
    public void setScheduled(boolean scheduled) {
        this.scheduled = scheduled;
    }
    
    /**
     * Gets the scheduled interval for collection.
     * 
     * @return the schedule interval
     */
    public Duration getScheduleInterval() {
        return scheduleInterval;
    }
    
    /**
     * Sets the scheduled interval for collection.
     * 
     * @param scheduleInterval the schedule interval
     */
    public void setScheduleInterval(Duration scheduleInterval) {
        this.scheduleInterval = scheduleInterval;
    }
    
    /**
     * Gets the next scheduled collection time.
     * 
     * @return the next scheduled run time
     */
    public Instant getNextScheduledRun() {
        return nextScheduledRun;
    }
    
    /**
     * Sets the next scheduled collection time.
     * 
     * @param nextScheduledRun the next scheduled run time
     */
    public void setNextScheduledRun(Instant nextScheduledRun) {
        this.nextScheduledRun = nextScheduledRun;
    }
    
    /**
     * Sets the next scheduled collection time based on the current time plus interval.
     * 
     * @param interval the time interval to add
     */
    public void setNextScheduledRun(TemporalAmount interval) {
        this.nextScheduledRun = Instant.now().plus(interval);
    }
    
    @Override
    public String toString() {
        return "CollectionStatus{" +
                "resourceId='" + resourceId + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", status='" + status + '\'' +
                ", lastStartTime=" + lastStartTime +
                ", lastEndTime=" + lastEndTime +
                ", lastMetricCount=" + lastMetricCount +
                ", scheduled=" + scheduled +
                ", nextScheduledRun=" + nextScheduledRun +
                '}';
    }
}