package org.opennms.bridge.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a collection of metrics from a cloud resource.
 */
public class MetricCollection {
    
    private String resourceId;
    private Instant timestamp;
    private List<Metric> metrics;
    private Map<String, String> tags;
    
    public MetricCollection() {
        this.metrics = new ArrayList<>();
        this.tags = new HashMap<>();
        this.timestamp = Instant.now();
    }
    
    public MetricCollection(String resourceId) {
        this();
        this.resourceId = resourceId;
    }
    
    public String getResourceId() {
        return resourceId;
    }
    
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public List<Metric> getMetrics() {
        return metrics;
    }
    
    public void setMetrics(List<Metric> metrics) {
        this.metrics = metrics != null ? metrics : new ArrayList<>();
    }
    
    public Map<String, String> getTags() {
        return tags;
    }
    
    public void setTags(Map<String, String> tags) {
        this.tags = tags != null ? tags : new HashMap<>();
    }
    
    public void addMetric(Metric metric) {
        this.metrics.add(metric);
    }
    
    public void addMetric(String name, double value) {
        this.metrics.add(new Metric(name, value));
    }
    
    public void addTag(String key, String value) {
        this.tags.put(key, value);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricCollection that = (MetricCollection) o;
        return Objects.equals(resourceId, that.resourceId) && 
               Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(resourceId, timestamp);
    }
    
    @Override
    public String toString() {
        return "MetricCollection{" +
                "resourceId='" + resourceId + '\'' +
                ", timestamp=" + timestamp +
                ", metrics=" + metrics.size() +
                '}';
    }
    
    /**
     * Inner class representing a single metric.
     */
    public static class Metric {
        private String name;
        private double value;
        private Map<String, String> tags;
        
        public Metric() {
            this.tags = new HashMap<>();
        }
        
        public Metric(String name, double value) {
            this();
            this.name = name;
            this.value = value;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public double getValue() {
            return value;
        }
        
        public void setValue(double value) {
            this.value = value;
        }
        
        public Map<String, String> getTags() {
            return tags;
        }
        
        public void setTags(Map<String, String> tags) {
            this.tags = tags != null ? tags : new HashMap<>();
        }
        
        public void addTag(String key, String value) {
            this.tags.put(key, value);
        }
        
        @Override
        public String toString() {
            return "Metric{" +
                    "name='" + name + '\'' +
                    ", value=" + value +
                    '}';
        }
    }
}