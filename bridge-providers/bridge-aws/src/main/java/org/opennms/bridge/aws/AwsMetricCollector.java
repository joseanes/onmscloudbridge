package org.opennms.bridge.aws;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AWS CloudWatch metric collector.
 * Collects metrics from CloudWatch for a specific cloud resource.
 */
@Component
public class AwsMetricCollector {
    private static final Logger LOG = LoggerFactory.getLogger(AwsMetricCollector.class);
    
    private static final String NAMESPACE_EC2 = "AWS/EC2";
    
    /**
     * Collect metrics for an EC2 instance.
     *
     * @param cloudWatchClient CloudWatch client
     * @param resource         Cloud resource (EC2 instance)
     * @param config           AWS configuration properties
     * @return metric collection
     */
    public MetricCollection collectEc2Metrics(CloudWatchClient cloudWatchClient, CloudResource resource, AwsConfigurationProperties config) {
        LOG.info("Collecting CloudWatch metrics for EC2 instance {}", resource.getResourceId());
        
        try {
            // Validate that this is an EC2 instance
            if (!"EC2".equals(resource.getResourceType())) {
                throw new IllegalArgumentException("Resource is not an EC2 instance: " + resource.getResourceType());
            }
            
            // Extract instance ID from resource
            String instanceId = resource.getResourceId();
            
            // Calculate time range for metrics
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(config.getCloudWatchCollection().getPeriod().toMillis(), ChronoUnit.MILLIS);
            
            // Build dimension for EC2 instance
            Dimension instanceDimension = Dimension.builder()
                    .name("InstanceId")
                    .value(instanceId)
                    .build();
            
            // Collect metrics
            List<MetricCollection.Metric> metrics = new ArrayList<>();
            for (String metricName : config.getCloudWatchCollection().getMetrics()) {
                try {
                    GetMetricDataRequest.Builder requestBuilder = GetMetricDataRequest.builder()
                            .startTime(startTime)
                            .endTime(endTime);
                    
                    // Add metric queries for each statistic
                    List<MetricDataQuery> queries = new ArrayList<>();
                    int queryId = 0;
                    
                    for (String statistic : config.getCloudWatchCollection().getStatistics()) {
                        String id = "q" + queryId++;
                        
                        MetricStat metricStat = MetricStat.builder()
                                .metric(Metric.builder()
                                        .namespace(NAMESPACE_EC2)
                                        .metricName(metricName)
                                        .dimensions(instanceDimension)
                                        .build())
                                .period(config.getCloudWatchCollection().getPeriod().getSeconds() > 0 
                                       ? (int) config.getCloudWatchCollection().getPeriod().getSeconds() 
                                       : 300)
                                .stat(mapStatistic(statistic))
                                .build();
                        
                        MetricDataQuery query = MetricDataQuery.builder()
                                .id(id)
                                .metricStat(metricStat)
                                .returnData(true)
                                .build();
                        
                        queries.add(query);
                    }
                    
                    GetMetricDataRequest request = requestBuilder
                            .metricDataQueries(queries)
                            .build();
                    
                    GetMetricDataResponse response = cloudWatchClient.getMetricData(request);
                    
                    // Process results for this metric
                    for (MetricDataResult result : response.metricDataResults()) {
                        if (!result.values().isEmpty()) {
                            String statisticName = result.id();
                            
                            // For each timestamp and value, create a metric
                            for (int i = 0; i < result.timestamps().size(); i++) {
                                Instant timestamp = result.timestamps().get(i);
                                Double value = result.values().get(i);
                                
                                if (value != null) {
                                    // Create metric key with name and statistic
                                    String metricKey = metricName + "." + statisticName;
                                    
                                    MetricCollection.Metric metric = new MetricCollection.Metric(metricKey, value);
                                    metric.addTag("timestamp", timestamp.toString());
                                    metric.addTag("resourceId", resource.getResourceId());
                                    metric.addTag("providerId", resource.getProviderId() != null ? resource.getProviderId() : "");
                                    metric.addTag("type", "GAUGE");
                                    
                                    metrics.add(metric);
                                }
                            }
                        }
                    }
                } catch (Exception metricException) {
                    LOG.warn("Error collecting CloudWatch metric {} for instance {}: {}", 
                            metricName, instanceId, metricException.getMessage());
                }
            }
            
            LOG.info("Collected {} CloudWatch metrics for EC2 instance {}", metrics.size(), instanceId);
            
            // Create metric collection
            MetricCollection collection = new MetricCollection(resource.getResourceId());
            collection.setTimestamp(Instant.now());
            collection.addTag("providerId", resource.getProviderId() != null ? resource.getProviderId() : "");
            collection.setMetrics(metrics);
            return collection;
            
        } catch (Exception e) {
            LOG.error("Error collecting CloudWatch metrics for EC2 instance {}: {}", 
                    resource.getResourceId(), e.getMessage(), e);
            
            // Return empty collection on error
            MetricCollection collection = new MetricCollection(resource.getResourceId());
            collection.setTimestamp(Instant.now());
            collection.addTag("providerId", resource.getProviderId() != null ? resource.getProviderId() : "");
            collection.setMetrics(Collections.emptyList());
            return collection;
        }
    }
    
    /**
     * Map a simple statistic name to CloudWatch statistic.
     *
     * @param statistic Simple statistic name (Average, Maximum, Minimum, Sum)
     * @return CloudWatch statistic
     */
    private String mapStatistic(String statistic) {
        switch (statistic.toLowerCase()) {
            case "average":
                return "Average";
            case "maximum":
                return "Maximum";
            case "minimum":
                return "Minimum";
            case "sum":
                return "Sum";
            case "samplecount":
                return "SampleCount";
            default:
                return "Average";
        }
    }
}