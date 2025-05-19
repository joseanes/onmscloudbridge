package org.opennms.bridge.api;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for metric collection.
 */
public class CollectionConfiguration {
    
    private CloudResource resource;
    private CloudProvider provider;
    private int intervalSeconds = 300; // Default 5 minutes
    private boolean enabled = true;
    private Set<String> metricNames;
    private int retentionDays = 7;
    private int timeout = 30000; // 30 seconds default timeout
    private int retries = 3;
    private int initialDelaySeconds = 0; // Default no initial delay
    
    public CollectionConfiguration() {
        this.metricNames = new HashSet<>();
    }
    
    public int getIntervalSeconds() {
        return intervalSeconds;
    }
    
    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Set<String> getMetricNames() {
        return metricNames;
    }
    
    public void setMetricNames(Set<String> metricNames) {
        this.metricNames = metricNames != null ? metricNames : new HashSet<>();
    }
    
    public void addMetricName(String metricName) {
        this.metricNames.add(metricName);
    }
    
    public int getRetentionDays() {
        return retentionDays;
    }
    
    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public int getRetries() {
        return retries;
    }
    
    public void setRetries(int retries) {
        this.retries = retries;
    }
    
    /**
     * Gets the cloud resource to collect metrics from.
     * 
     * @return the cloud resource
     */
    public CloudResource getResource() {
        return resource;
    }
    
    /**
     * Sets the cloud resource to collect metrics from.
     * 
     * @param resource the cloud resource
     */
    public void setResource(CloudResource resource) {
        this.resource = resource;
    }
    
    /**
     * Gets the cloud provider to use for collection.
     * 
     * @return the cloud provider
     */
    public CloudProvider getProvider() {
        return provider;
    }
    
    /**
     * Sets the cloud provider to use for collection.
     * 
     * @param provider the cloud provider
     */
    public void setProvider(CloudProvider provider) {
        this.provider = provider;
    }
    
    /**
     * Gets the initial delay in seconds before the first collection run.
     * 
     * @return initial delay in seconds
     */
    public int getInitialDelaySeconds() {
        return initialDelaySeconds;
    }
    
    /**
     * Sets the initial delay in seconds before the first collection run.
     * 
     * @param initialDelaySeconds initial delay in seconds
     */
    public void setInitialDelaySeconds(int initialDelaySeconds) {
        this.initialDelaySeconds = initialDelaySeconds;
    }
    
    /**
     * Sets the initial delay as a Duration.
     * 
     * @param initialDelay initial delay as Duration
     */
    public void setInitialDelay(java.time.Duration initialDelay) {
        this.initialDelaySeconds = (int) initialDelay.toSeconds();
    }
    
    /**
     * Gets the initial delay as a Duration.
     * 
     * @return initial delay as a Duration
     */
    public java.time.Duration getInitialDelay() {
        return java.time.Duration.ofSeconds(initialDelaySeconds);
    }
    
    /**
     * Sets the interval as a Duration.
     * 
     * @param interval interval as Duration
     */
    public void setInterval(java.time.Duration interval) {
        this.intervalSeconds = (int) interval.toSeconds();
    }
    
    /**
     * Gets the interval as a Duration.
     * 
     * @return interval as a Duration
     */
    public java.time.Duration getInterval() {
        return java.time.Duration.ofSeconds(intervalSeconds);
    }
    
    @Override
    public String toString() {
        return "CollectionConfiguration{" +
                "intervalSeconds=" + intervalSeconds +
                ", initialDelaySeconds=" + initialDelaySeconds +
                ", enabled=" + enabled +
                ", metricNames=" + metricNames +
                ", retentionDays=" + retentionDays +
                ", timeout=" + timeout +
                ", retries=" + retries +
                ", resource=" + (resource != null ? resource.getId() : "null") +
                ", provider=" + (provider != null ? provider.getProviderId() : "null") +
                '}';
    }
}