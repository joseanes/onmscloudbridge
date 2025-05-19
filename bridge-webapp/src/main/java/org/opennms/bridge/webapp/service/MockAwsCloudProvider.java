package org.opennms.bridge.webapp.service;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;

/**
 * Mock AWS cloud provider implementation for demo purposes.
 * Simulates AWS EC2 and CloudWatch functionality without requiring real AWS credentials.
 */
@Component
public class MockAwsCloudProvider implements CloudProvider {
    private static final Logger LOG = LoggerFactory.getLogger(MockAwsCloudProvider.class);
    
    private String providerId = "aws-mock";
    private String displayName = "AWS Mock Provider";
    private List<String> regions = Arrays.asList("us-east-1", "us-west-2", "eu-west-1");
    private Map<String, Object> configuration = new HashMap<>();
    private Set<String> supportedMetrics = new HashSet<>();
    
    @PostConstruct
    public void init() {
        LOG.info("Initializing mock AWS cloud provider");
        
        // Initialize supported metrics
        supportedMetrics.add("CPUUtilization.Average");
        supportedMetrics.add("CPUUtilization.Maximum");
        supportedMetrics.add("CPUUtilization.Minimum");
        supportedMetrics.add("NetworkIn.Average");
        supportedMetrics.add("NetworkOut.Average");
        supportedMetrics.add("DiskReadBytes.Average");
        supportedMetrics.add("DiskWriteBytes.Average");
        supportedMetrics.add("StatusCheckFailed.Sum");
        
        // Initialize configuration
        configuration.put("providerId", providerId);
        configuration.put("displayName", displayName);
        configuration.put("regions", regions);
        configuration.put("connectionTimeout", 30000);
        configuration.put("readTimeout", 30000);
        configuration.put("maxRetries", 3);
        
        // EC2 discovery configuration
        Map<String, Object> ec2Config = new HashMap<>();
        ec2Config.put("enabled", true);
        ec2Config.put("includeTags", Arrays.asList("Name", "Environment", "Service"));
        ec2Config.put("filterByTags", new ArrayList<>());
        ec2Config.put("instanceStates", Arrays.asList("running", "stopped"));
        configuration.put("ec2Discovery", ec2Config);
        
        // CloudWatch collection configuration
        Map<String, Object> cloudWatchConfig = new HashMap<>();
        cloudWatchConfig.put("enabled", true);
        cloudWatchConfig.put("metrics", Arrays.asList("CPUUtilization", "NetworkIn", "NetworkOut", "DiskReadBytes", "DiskWriteBytes", "StatusCheckFailed"));
        cloudWatchConfig.put("period", 5);
        cloudWatchConfig.put("statistics", Arrays.asList("Average", "Maximum", "Minimum", "Sum"));
        configuration.put("cloudWatchCollection", cloudWatchConfig);
    }
    
    @Override
    public String getProviderId() {
        return providerId;
    }
    
    @Override
    public String getProviderType() {
        return "aws";
    }
    
    @Override
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public ValidationResult validate() throws CloudProviderException {
        // Always return valid for mock provider
        return ValidationResult.valid();
    }
    
    @Override
    public Set<CloudResource> discover() throws CloudProviderException {
        LOG.info("Mock discovering resources from AWS provider");
        
        Set<CloudResource> resources = new HashSet<>();
        
        // Create mock EC2 instances for each region
        for (String region : regions) {
            for (int i = 1; i <= 5; i++) {
                String instanceId = "i-" + UUID.randomUUID().toString().substring(0, 8);
                CloudResource resource = new CloudResource(instanceId, "EC2 Instance " + i + " (" + region + ")", "EC2", region);
                resource.setStatus("running");
                
                // Add properties
                resource.addProperty("providerId", providerId);
                resource.addProperty("providerType", "aws");
                resource.addProperty("instanceType", "t2.micro");
                resource.addProperty("privateIpAddress", "10.0." + region.hashCode() % 256 + "." + i);
                resource.addProperty("publicIpAddress", "54.123." + region.hashCode() % 256 + "." + i);
                
                // Add tags
                Map<String, String> tags = new HashMap<>();
                tags.put("Name", "Instance " + i + " (" + region + ")");
                tags.put("Environment", i % 3 == 0 ? "Production" : "Development");
                tags.put("Service", "Demo");
                resource.setTags(tags);
                
                resources.add(resource);
            }
        }
        
        return resources;
    }
    
    @Override
    public MetricCollection collect(CloudResource resource) throws CloudProviderException {
        LOG.info("Mock collecting metrics for resource {}", resource.getResourceId());
        
        MetricCollection collection = new MetricCollection(resource.getResourceId());
        collection.setTimestamp(new Date().toInstant());
        collection.addTag("providerId", providerId);
        collection.addTag("region", resource.getRegion());
        
        List<MetricCollection.Metric> metrics = new ArrayList<>();
        
        // CPU metrics
        metrics.add(new MetricCollection.Metric("CPUUtilization.Average", 
                25 + (Math.random() * 20)));
        metrics.add(new MetricCollection.Metric("CPUUtilization.Maximum", 
                45 + (Math.random() * 40)));
        metrics.add(new MetricCollection.Metric("CPUUtilization.Minimum", 
                5 + (Math.random() * 15)));
        
        // Network metrics
        metrics.add(new MetricCollection.Metric("NetworkIn.Average", 
                50000 + (Math.random() * 75000)));
        metrics.add(new MetricCollection.Metric("NetworkOut.Average", 
                40000 + (Math.random() * 60000)));
        
        // Disk metrics
        metrics.add(new MetricCollection.Metric("DiskReadBytes.Average", 
                20000 + (Math.random() * 30000)));
        metrics.add(new MetricCollection.Metric("DiskWriteBytes.Average", 
                15000 + (Math.random() * 25000)));
        
        // Status check
        metrics.add(new MetricCollection.Metric("StatusCheckFailed.Sum", 0.0));
        
        // Add tags to all metrics
        for (MetricCollection.Metric metric : metrics) {
            metric.addTag("timestamp", collection.getTimestamp().toString());
            metric.addTag("resourceId", resource.getResourceId());
            metric.addTag("resourceType", resource.getResourceType());
            metric.addTag("region", resource.getRegion());
        }
        
        collection.setMetrics(metrics);
        return collection;
    }
    
    @Override
    public Set<String> getAvailableRegions() {
        return new HashSet<>(regions);
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        return configuration;
    }
    
    @Override
    public void updateConfiguration(Map<String, Object> configuration) throws CloudProviderException {
        LOG.info("Updating mock AWS provider configuration");
        
        try {
            // Update basic configuration
            if (configuration.containsKey("providerId")) {
                this.providerId = (String) configuration.get("providerId");
                this.configuration.put("providerId", this.providerId);
            }
            
            if (configuration.containsKey("displayName")) {
                this.displayName = (String) configuration.get("displayName");
                this.configuration.put("displayName", this.displayName);
            }
            
            if (configuration.containsKey("regions")) {
                @SuppressWarnings("unchecked")
                List<String> regions = (List<String>) configuration.get("regions");
                this.regions = regions;
                this.configuration.put("regions", this.regions);
            }
            
            // Update other configuration values
            for (Map.Entry<String, Object> entry : configuration.entrySet()) {
                if (!"providerId".equals(entry.getKey()) && 
                    !"displayName".equals(entry.getKey()) && 
                    !"regions".equals(entry.getKey())) {
                    this.configuration.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            LOG.error("Error updating mock AWS provider configuration: {}", e.getMessage(), e);
            throw new CloudProviderException("Failed to update configuration for mock AWS provider", e);
        }
    }
    
    @Override
    public Set<String> getSupportedMetrics() {
        return supportedMetrics;
    }
    
    @Override
    public void close() {
        LOG.info("Closing mock AWS cloud provider");
    }
}