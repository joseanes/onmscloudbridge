package org.opennms.bridge.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for cloud resource discovery.
 */
public class DiscoveryConfiguration {
    
    private int intervalMinutes = 60; // Default 1 hour
    private boolean enabled = true;
    private Set<String> regions;
    private Set<String> resourceTypes;
    private List<String> filterTags;
    private int timeout = 60000; // 60 seconds default
    private boolean persistResults = true;
    private int retries = 3;
    private boolean autoImport = false;
    private int initialDelayMinutes = 0; // Default no initial delay
    
    public DiscoveryConfiguration() {
        this.regions = new HashSet<>();
        this.resourceTypes = new HashSet<>();
        this.filterTags = new ArrayList<>();
    }
    
    public int getIntervalMinutes() {
        return intervalMinutes;
    }
    
    public void setIntervalMinutes(int intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Set<String> getRegions() {
        return regions;
    }
    
    public void setRegions(Set<String> regions) {
        this.regions = regions != null ? regions : new HashSet<>();
    }
    
    public void addRegion(String region) {
        this.regions.add(region);
    }
    
    public Set<String> getResourceTypes() {
        return resourceTypes;
    }
    
    public void setResourceTypes(Set<String> resourceTypes) {
        this.resourceTypes = resourceTypes != null ? resourceTypes : new HashSet<>();
    }
    
    public void addResourceType(String resourceType) {
        this.resourceTypes.add(resourceType);
    }
    
    public List<String> getFilterTags() {
        return filterTags;
    }
    
    public void setFilterTags(List<String> filterTags) {
        this.filterTags = filterTags != null ? filterTags : new ArrayList<>();
    }
    
    public void addFilterTag(String filterTag) {
        this.filterTags.add(filterTag);
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public boolean isPersistResults() {
        return persistResults;
    }
    
    public void setPersistResults(boolean persistResults) {
        this.persistResults = persistResults;
    }
    
    public int getRetries() {
        return retries;
    }
    
    public void setRetries(int retries) {
        this.retries = retries;
    }
    
    public boolean isAutoImport() {
        return autoImport;
    }
    
    public void setAutoImport(boolean autoImport) {
        this.autoImport = autoImport;
    }
    
    /**
     * Gets the interval in minutes between discovery runs.
     * 
     * @return the interval in minutes
     */
    public int getInterval() {
        return intervalMinutes;
    }
    
    /**
     * Gets the initial delay in minutes before the first discovery run.
     * 
     * @return initial delay in minutes
     */
    public int getInitialDelay() {
        return initialDelayMinutes;
    }
    
    /**
     * Sets the initial delay in minutes before the first discovery run.
     * 
     * @param initialDelayMinutes initial delay in minutes
     */
    public void setInitialDelayMinutes(int initialDelayMinutes) {
        this.initialDelayMinutes = initialDelayMinutes;
    }
    
    /**
     * Sets the initial delay as a Duration.
     * 
     * @param initialDelay initial delay as Duration
     */
    public void setInitialDelay(java.time.Duration initialDelay) {
        this.initialDelayMinutes = (int) initialDelay.toMinutes();
    }
    
    /**
     * Sets the interval as a Duration.
     * 
     * @param interval interval as Duration
     */
    public void setInterval(java.time.Duration interval) {
        this.intervalMinutes = (int) interval.toMinutes();
    }
    
    @Override
    public String toString() {
        return "DiscoveryConfiguration{" +
                "intervalMinutes=" + intervalMinutes +
                ", enabled=" + enabled +
                ", regions=" + regions +
                ", resourceTypes=" + resourceTypes +
                ", filterTags=" + filterTags +
                ", timeout=" + timeout +
                ", persistResults=" + persistResults +
                ", retries=" + retries +
                ", autoImport=" + autoImport +
                '}';
    }
}