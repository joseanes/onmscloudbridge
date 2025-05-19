package org.opennms.bridge.api;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

/**
 * Status of cloud resource discovery for a provider.
 */
public class DiscoveryStatus {
    
    public enum State {
        PENDING,
        SCHEDULED,
        RUNNING,
        COMPLETED,
        FAILED,
        DISABLED
    }
    
    private String providerId;
    private String providerType;
    private State state;
    private Instant lastDiscovery;
    private Instant nextDiscovery;
    private String message;
    private int consecutiveFailures;
    private int nodesDiscovered;
    private Instant lastStartTime;
    private String status;
    private Instant lastEndTime;
    private int lastDiscoveredCount;
    private Instant lastSuccessTime;
    private String lastError;
    private boolean scheduled;
    private Duration scheduleInterval;
    private Instant nextScheduledRun;
    private String jobId;
    
    public DiscoveryStatus() {
        this.scheduleInterval = Duration.ofMinutes(15); // Default interval
    }
    
    public DiscoveryStatus(String providerId, String providerType, State state) {
        this();
        this.providerId = providerId;
        this.providerType = providerType;
        this.state = state;
    }
    
    public String getProviderId() {
        return providerId;
    }
    
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
    
    public String getProviderType() {
        return providerType;
    }
    
    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }
    
    public State getState() {
        return state;
    }
    
    public void setState(State state) {
        this.state = state;
    }
    
    public Instant getLastDiscovery() {
        return lastDiscovery;
    }
    
    public void setLastDiscovery(Instant lastDiscovery) {
        this.lastDiscovery = lastDiscovery;
    }
    
    public Instant getNextDiscovery() {
        return nextDiscovery;
    }
    
    public void setNextDiscovery(Instant nextDiscovery) {
        this.nextDiscovery = nextDiscovery;
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
    
    public int getNodesDiscovered() {
        return nodesDiscovered;
    }
    
    public void setNodesDiscovered(int nodesDiscovered) {
        this.nodesDiscovered = nodesDiscovered;
    }
    
    public Instant getLastStartTime() {
        return lastStartTime;
    }
    
    public void setLastStartTime(Instant lastStartTime) {
        this.lastStartTime = lastStartTime;
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
    
    public int getLastDiscoveredCount() {
        return lastDiscoveredCount;
    }
    
    public void setLastDiscoveredCount(int lastDiscoveredCount) {
        this.lastDiscoveredCount = lastDiscoveredCount;
    }
    
    public Instant getLastSuccessTime() {
        return lastSuccessTime;
    }
    
    public void setLastSuccessTime(Instant lastSuccessTime) {
        this.lastSuccessTime = lastSuccessTime;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
    
    /**
     * Checks if discovery is scheduled for this provider.
     * 
     * @return true if scheduled, false otherwise
     */
    public boolean isScheduled() {
        return scheduled;
    }
    
    /**
     * Sets whether discovery is scheduled for this provider.
     * 
     * @param scheduled true to enable scheduling, false to disable
     */
    public void setScheduled(boolean scheduled) {
        this.scheduled = scheduled;
    }
    
    /**
     * Gets the scheduled interval for discovery.
     * 
     * @return the schedule interval
     */
    public Duration getScheduleInterval() {
        return scheduleInterval;
    }
    
    /**
     * Sets the scheduled interval for discovery.
     * 
     * @param scheduleInterval the schedule interval
     */
    public void setScheduleInterval(Duration scheduleInterval) {
        this.scheduleInterval = scheduleInterval;
    }
    
    /**
     * Sets the scheduled interval for discovery.
     * 
     * @param minutes interval in minutes
     */
    public void setScheduleInterval(int minutes) {
        this.scheduleInterval = Duration.ofMinutes(minutes);
    }
    
    /**
     * Gets the next scheduled discovery time.
     * 
     * @return the next scheduled run time
     */
    public Instant getNextScheduledRun() {
        return nextScheduledRun;
    }
    
    /**
     * Sets the next scheduled discovery time.
     * 
     * @param nextScheduledRun the next scheduled run time
     */
    public void setNextScheduledRun(Instant nextScheduledRun) {
        this.nextScheduledRun = nextScheduledRun;
    }
    
    /**
     * Sets the next scheduled discovery time based on the current time plus interval.
     * 
     * @param interval the time interval to add
     */
    public void setNextScheduledRun(TemporalAmount interval) {
        this.nextScheduledRun = Instant.now().plus(interval);
    }
    
    /**
     * Gets the job ID for the current or most recent discovery.
     * 
     * @return the job ID
     */
    public String getJobId() {
        return jobId;
    }
    
    /**
     * Sets the job ID for the current discovery.
     * 
     * @param jobId the job ID
     */
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    @Override
    public String toString() {
        return "DiscoveryStatus{" +
                "providerId='" + providerId + '\'' +
                ", providerType='" + providerType + '\'' +
                ", status='" + status + '\'' +
                ", lastStartTime=" + lastStartTime +
                ", lastEndTime=" + lastEndTime +
                ", lastDiscoveredCount=" + lastDiscoveredCount +
                ", scheduled=" + scheduled +
                ", nextScheduledRun=" + nextScheduledRun +
                '}';
    }
}