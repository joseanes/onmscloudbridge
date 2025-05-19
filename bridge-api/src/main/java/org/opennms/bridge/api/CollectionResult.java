package org.opennms.bridge.api;

import java.time.Instant;

/**
 * Result of a metric collection operation.
 */
public class CollectionResult {
    
    public enum Status {
        SUCCESS,
        PARTIAL,
        FAILURE
    }
    
    private Status status;
    private MetricCollection metrics;
    private String message;
    private Throwable error;
    private Instant timestamp;
    private long executionTime; // in milliseconds
    private String resourceId;
    
    public CollectionResult() {
        this.timestamp = Instant.now();
    }
    
    public static CollectionResult success(MetricCollection metrics) {
        CollectionResult result = new CollectionResult();
        result.status = Status.SUCCESS;
        result.metrics = metrics;
        return result;
    }
    
    public static CollectionResult partial(MetricCollection metrics, String message) {
        CollectionResult result = new CollectionResult();
        result.status = Status.PARTIAL;
        result.metrics = metrics;
        result.message = message;
        return result;
    }
    
    public static CollectionResult failure(String message) {
        CollectionResult result = new CollectionResult();
        result.status = Status.FAILURE;
        result.message = message;
        return result;
    }
    
    public static CollectionResult failure(String message, Throwable error) {
        CollectionResult result = new CollectionResult();
        result.status = Status.FAILURE;
        result.message = message;
        result.error = error;
        return result;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public MetricCollection getMetrics() {
        return metrics;
    }
    
    public void setMetrics(MetricCollection metrics) {
        this.metrics = metrics;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Throwable getError() {
        return error;
    }
    
    public void setError(Throwable error) {
        this.error = error;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getExecutionTime() {
        return executionTime;
    }
    
    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }
    
    public String getResourceId() {
        return resourceId;
    }
    
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean isPartial() {
        return status == Status.PARTIAL;
    }
    
    public boolean isFailure() {
        return status == Status.FAILURE;
    }
    
    @Override
    public String toString() {
        return "CollectionResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                ", executionTime=" + executionTime +
                "ms}";
    }
}