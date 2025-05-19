package org.opennms.bridge.aws;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AWS cloud provider implementation.
 * Implements the CloudProvider interface for AWS EC2 and CloudWatch.
 */
@Component
public class AwsCloudProvider implements CloudProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AwsCloudProvider.class);
    
    @Autowired
    private AwsConfigurationProperties config;
    
    @Autowired
    private AwsDiscoveryStrategy discoveryStrategy;
    
    @Autowired
    private AwsMetricCollector metricCollector;
    
    // Cache of EC2 and CloudWatch clients by region
    private final Map<String, Ec2Client> ec2ClientCache = new ConcurrentHashMap<>();
    private final Map<String, CloudWatchClient> cloudWatchClientCache = new ConcurrentHashMap<>();
    
    // Credential provider
    private AwsCredentialsProvider credentialsProvider;
    
    @PostConstruct
    public void init() {
        LOG.info("Initializing AWS cloud provider: {}", config.getProviderId());
        initializeCredentialsProvider();
    }
    
    @PreDestroy
    public void cleanup() {
        LOG.info("Closing AWS cloud provider: {}", config.getProviderId());
        close();
    }
    
    /**
     * Initialize AWS credentials provider based on configuration.
     */
    private void initializeCredentialsProvider() {
        try {
            // If access key and secret key are provided, use static credentials
            if (config.getAccessKeyId() != null && config.getSecretAccessKey() != null) {
                LOG.info("Using static AWS credentials for provider {}", config.getProviderId());
                
                AwsCredentials awsCredentials;
                if (config.getSessionToken() != null) {
                    awsCredentials = AwsSessionCredentials.create(
                            config.getAccessKeyId(),
                            config.getSecretAccessKey(),
                            config.getSessionToken());
                } else {
                    awsCredentials = AwsBasicCredentials.create(
                            config.getAccessKeyId(),
                            config.getSecretAccessKey());
                }
                
                credentialsProvider = StaticCredentialsProvider.create(awsCredentials);
            } else {
                LOG.info("Using default AWS credential chain for provider {}", config.getProviderId());
                credentialsProvider = DefaultCredentialsProvider.create();
            }
            
            // If role ARN is provided, use assume role credentials
            if (config.getRoleArn() != null) {
                LOG.info("Assuming AWS role {} for provider {}", config.getRoleArn(), config.getProviderId());
                
                try (StsClient stsClient = StsClient.builder()
                        .credentialsProvider(credentialsProvider)
                        .build()) {
                    
                    AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                            .roleArn(config.getRoleArn())
                            .roleSessionName("OpenNMSCloudBridge-" + config.getProviderId())
                            .build();
                    
                    credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                            .stsClient(stsClient)
                            .refreshRequest(assumeRoleRequest)
                            .build();
                }
            }
        } catch (Exception e) {
            LOG.error("Error initializing AWS credentials provider: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize AWS credentials provider", e);
        }
    }
    
    /**
     * Get an EC2 client for a specific region.
     *
     * @param region AWS region
     * @return EC2 client
     */
    private Ec2Client getEc2Client(String region) {
        return ec2ClientCache.computeIfAbsent(region, r -> {
            LOG.debug("Creating new EC2 client for region {}", r);
            return Ec2Client.builder()
                    .region(Region.of(r))
                    .credentialsProvider(credentialsProvider)
                    .overrideConfiguration(c -> c
                            .apiCallTimeout(config.getConnectionTimeout())
                            .apiCallAttemptTimeout(config.getReadTimeout()))
                    .build();
        });
    }
    
    /**
     * Get a CloudWatch client for a specific region.
     *
     * @param region AWS region
     * @return CloudWatch client
     */
    private CloudWatchClient getCloudWatchClient(String region) {
        return cloudWatchClientCache.computeIfAbsent(region, r -> {
            LOG.debug("Creating new CloudWatch client for region {}", r);
            return CloudWatchClient.builder()
                    .region(Region.of(r))
                    .credentialsProvider(credentialsProvider)
                    .overrideConfiguration(c -> c
                            .apiCallTimeout(config.getConnectionTimeout())
                            .apiCallAttemptTimeout(config.getReadTimeout()))
                    .build();
        });
    }
    
    /**
     * Get regions to use for operations.
     * If regions are configured, use those. Otherwise, get the current region from the environment.
     *
     * @return list of AWS regions
     */
    private List<String> getRegionsToUse() {
        if (config.getRegions() != null && !config.getRegions().isEmpty()) {
            return config.getRegions();
        } else {
            try {
                String region = new DefaultAwsRegionProviderChain().getRegion().id();
                return Collections.singletonList(region);
            } catch (Exception e) {
                LOG.warn("Failed to determine AWS region from environment: {}", e.getMessage());
                return Collections.singletonList("us-east-1");
            }
        }
    }
    
    @Override
    public String getProviderId() {
        return config.getProviderId();
    }
    
    @Override
    public String getProviderType() {
        return "aws";
    }
    
    @Override
    public String getDisplayName() {
        return config.getDisplayName();
    }
    
    @Override
    public ValidationResult validate() throws CloudProviderException {
        LOG.info("Validating AWS cloud provider: {}", config.getProviderId());
        try {
            List<String> regions = getRegionsToUse();
            boolean allRegionsValid = true;
            List<String> validationErrors = new ArrayList<>();
            
            for (String region : regions) {
                try {
                    // Try to create and use EC2 client for validation
                    Ec2Client ec2Client = getEc2Client(region);
                    ec2Client.describeRegions();
                    
                    // Try to create and use CloudWatch client for validation
                    CloudWatchClient cloudWatchClient = getCloudWatchClient(region);
                    cloudWatchClient.listMetrics();
                } catch (Exception e) {
                    allRegionsValid = false;
                    validationErrors.add("Failed to validate region " + region + ": " + e.getMessage());
                    LOG.warn("Validation failed for AWS region {}: {}", region, e.getMessage(), e);
                }
            }
            
            if (allRegionsValid) {
                return ValidationResult.valid();
            } else {
                return ValidationResult.invalid(String.join("; ", validationErrors));
            }
        } catch (Exception e) {
            LOG.error("AWS provider validation error: {}", e.getMessage(), e);
            throw new CloudProviderException("Validation failed for AWS provider " + config.getProviderId(), e);
        }
    }
    
    @Override
    public Set<CloudResource> discover() throws CloudProviderException {
        LOG.info("Discovering resources from AWS provider: {}", config.getProviderId());
        try {
            List<String> regions = getRegionsToUse();
            Set<CloudResource> allResources = new HashSet<>();
            
            for (String region : regions) {
                LOG.debug("Discovering resources in region {}", region);
                
                // Discover EC2 instances if enabled
                if (config.getEc2Discovery().isEnabled()) {
                    Ec2Client ec2Client = getEc2Client(region);
                    Set<CloudResource> ec2Resources = discoveryStrategy.discoverEc2Instances(ec2Client, region, config);
                    allResources.addAll(ec2Resources);
                }
                
                // Add other AWS resource types here as needed
            }
            
            LOG.info("Discovered {} resources from AWS provider: {}", allResources.size(), config.getProviderId());
            return allResources;
        } catch (Exception e) {
            LOG.error("Error discovering AWS resources: {}", e.getMessage(), e);
            throw new CloudProviderException("Failed to discover AWS resources", e);
        }
    }
    
    @Override
    public MetricCollection collect(CloudResource resource) throws CloudProviderException {
        LOG.info("Collecting metrics for resource {} from AWS provider: {}", resource.getResourceId(), config.getProviderId());
        try {
            // Check that resource belongs to this provider
            if (!config.getProviderId().equals(resource.getProviderId())) {
                throw new CloudProviderException("Resource " + resource.getResourceId() + 
                        " does not belong to provider " + config.getProviderId());
            }
            
            // Get region from resource
            String region = resource.getRegion();
            if (region == null) {
                throw new CloudProviderException("Resource " + resource.getResourceId() + " has no region specified");
            }
            
            // Collect metrics based on resource type
            switch (resource.getResourceType()) {
                case "EC2":
                    if (config.getCloudWatchCollection().isEnabled()) {
                        CloudWatchClient cloudWatchClient = getCloudWatchClient(region);
                        return metricCollector.collectEc2Metrics(cloudWatchClient, resource, config);
                    } else {
                        LOG.info("CloudWatch metric collection is disabled");
                        MetricCollection collection = new MetricCollection(resource.getResourceId());
                        collection.setTimestamp(new Date().toInstant());
                        collection.addTag("providerId", config.getProviderId());
                        collection.setMetrics(Collections.emptyList());
                        return collection;
                    }
                default:
                    throw new CloudProviderException("Unsupported resource type: " + resource.getResourceType());
            }
        } catch (CloudProviderException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Error collecting metrics for resource {}: {}", resource.getResourceId(), e.getMessage(), e);
            throw new CloudProviderException("Failed to collect metrics for resource " + resource.getResourceId(), e);
        }
    }
    
    @Override
    public Set<String> getAvailableRegions() {
        try {
            // Try to get regions from EC2 client
            Ec2Client ec2Client = getEc2Client(getRegionsToUse().get(0));
            return ec2Client.describeRegions().regions().stream()
                    .map(r -> r.regionName())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            LOG.warn("Error getting available AWS regions: {}", e.getMessage(), e);
            return new HashSet<>(getRegionsToUse());
        }
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> configuration = new HashMap<>();
        
        configuration.put("providerId", config.getProviderId());
        configuration.put("displayName", config.getDisplayName());
        configuration.put("regions", config.getRegions());
        configuration.put("connectionTimeout", config.getConnectionTimeout().toMillis());
        configuration.put("readTimeout", config.getReadTimeout().toMillis());
        configuration.put("maxRetries", config.getMaxRetries());
        
        // Include EC2 discovery configuration
        Map<String, Object> ec2Config = new HashMap<>();
        ec2Config.put("enabled", config.getEc2Discovery().isEnabled());
        ec2Config.put("includeTags", config.getEc2Discovery().getIncludeTags());
        ec2Config.put("filterByTags", config.getEc2Discovery().getFilterByTags());
        ec2Config.put("instanceStates", config.getEc2Discovery().getInstanceStates());
        configuration.put("ec2Discovery", ec2Config);
        
        // Include CloudWatch collection configuration
        Map<String, Object> cloudWatchConfig = new HashMap<>();
        cloudWatchConfig.put("enabled", config.getCloudWatchCollection().isEnabled());
        cloudWatchConfig.put("metrics", config.getCloudWatchCollection().getMetrics());
        cloudWatchConfig.put("period", config.getCloudWatchCollection().getPeriod().toMinutes());
        cloudWatchConfig.put("statistics", config.getCloudWatchCollection().getStatistics());
        configuration.put("cloudWatchCollection", cloudWatchConfig);
        
        return configuration;
    }
    
    @Override
    public void updateConfiguration(Map<String, Object> configuration) throws CloudProviderException {
        LOG.info("Updating configuration for AWS provider: {}", config.getProviderId());
        
        // Close existing clients before updating configuration
        closeClients();
        
        try {
            // Update basic configuration
            if (configuration.containsKey("providerId")) {
                config.setProviderId((String) configuration.get("providerId"));
            }
            if (configuration.containsKey("displayName")) {
                config.setDisplayName((String) configuration.get("displayName"));
            }
            if (configuration.containsKey("accessKeyId")) {
                config.setAccessKeyId((String) configuration.get("accessKeyId"));
            }
            if (configuration.containsKey("secretAccessKey")) {
                config.setSecretAccessKey((String) configuration.get("secretAccessKey"));
            }
            if (configuration.containsKey("sessionToken")) {
                config.setSessionToken((String) configuration.get("sessionToken"));
            }
            if (configuration.containsKey("roleArn")) {
                config.setRoleArn((String) configuration.get("roleArn"));
            }
            if (configuration.containsKey("regions")) {
                @SuppressWarnings("unchecked")
                List<String> regions = (List<String>) configuration.get("regions");
                config.setRegions(regions);
            }
            if (configuration.containsKey("connectionTimeout")) {
                Object timeout = configuration.get("connectionTimeout");
                if (timeout instanceof Number) {
                    config.setConnectionTimeout(Duration.ofMillis(((Number) timeout).longValue()));
                } else if (timeout instanceof String) {
                    config.setConnectionTimeout(Duration.ofMillis(Long.parseLong((String) timeout)));
                }
            }
            if (configuration.containsKey("readTimeout")) {
                Object timeout = configuration.get("readTimeout");
                if (timeout instanceof Number) {
                    config.setReadTimeout(Duration.ofMillis(((Number) timeout).longValue()));
                } else if (timeout instanceof String) {
                    config.setReadTimeout(Duration.ofMillis(Long.parseLong((String) timeout)));
                }
            }
            if (configuration.containsKey("maxRetries")) {
                Object maxRetries = configuration.get("maxRetries");
                if (maxRetries instanceof Number) {
                    config.setMaxRetries(((Number) maxRetries).intValue());
                } else if (maxRetries instanceof String) {
                    config.setMaxRetries(Integer.parseInt((String) maxRetries));
                }
            }
            
            // Update EC2 discovery configuration
            if (configuration.containsKey("ec2Discovery")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ec2Config = (Map<String, Object>) configuration.get("ec2Discovery");
                
                if (ec2Config.containsKey("enabled")) {
                    config.getEc2Discovery().setEnabled((Boolean) ec2Config.get("enabled"));
                }
                if (ec2Config.containsKey("includeTags")) {
                    @SuppressWarnings("unchecked")
                    List<String> includeTags = (List<String>) ec2Config.get("includeTags");
                    config.getEc2Discovery().setIncludeTags(includeTags);
                }
                if (ec2Config.containsKey("filterByTags")) {
                    @SuppressWarnings("unchecked")
                    List<String> filterByTags = (List<String>) ec2Config.get("filterByTags");
                    config.getEc2Discovery().setFilterByTags(filterByTags);
                }
                if (ec2Config.containsKey("instanceStates")) {
                    @SuppressWarnings("unchecked")
                    List<String> instanceStates = (List<String>) ec2Config.get("instanceStates");
                    config.getEc2Discovery().setInstanceStates(instanceStates);
                }
            }
            
            // Update CloudWatch collection configuration
            if (configuration.containsKey("cloudWatchCollection")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cloudWatchConfig = (Map<String, Object>) configuration.get("cloudWatchCollection");
                
                if (cloudWatchConfig.containsKey("enabled")) {
                    config.getCloudWatchCollection().setEnabled((Boolean) cloudWatchConfig.get("enabled"));
                }
                if (cloudWatchConfig.containsKey("metrics")) {
                    @SuppressWarnings("unchecked")
                    List<String> metrics = (List<String>) cloudWatchConfig.get("metrics");
                    config.getCloudWatchCollection().setMetrics(metrics);
                }
                if (cloudWatchConfig.containsKey("period")) {
                    Object period = cloudWatchConfig.get("period");
                    if (period instanceof Number) {
                        config.getCloudWatchCollection().setPeriod(Duration.ofMinutes(((Number) period).longValue()));
                    } else if (period instanceof String) {
                        config.getCloudWatchCollection().setPeriod(Duration.ofMinutes(Long.parseLong((String) period)));
                    }
                }
                if (cloudWatchConfig.containsKey("statistics")) {
                    @SuppressWarnings("unchecked")
                    List<String> statistics = (List<String>) cloudWatchConfig.get("statistics");
                    config.getCloudWatchCollection().setStatistics(statistics);
                }
            }
            
            // Re-initialize credentials provider after configuration update
            initializeCredentialsProvider();
            
        } catch (Exception e) {
            LOG.error("Error updating AWS provider configuration: {}", e.getMessage(), e);
            throw new CloudProviderException("Failed to update configuration for AWS provider " + config.getProviderId(), e);
        }
    }
    
    @Override
    public Set<String> getSupportedMetrics() {
        Set<String> metrics = new HashSet<>();
        
        // Add EC2 metrics
        if (config.getEc2Discovery().isEnabled()) {
            for (String metric : config.getCloudWatchCollection().getMetrics()) {
                for (String statistic : config.getCloudWatchCollection().getStatistics()) {
                    metrics.add(metric + "." + statistic);
                }
            }
        }
        
        return metrics;
    }
    
    /**
     * Close all clients and release resources.
     */
    private void closeClients() {
        // Close EC2 clients
        for (Map.Entry<String, Ec2Client> entry : ec2ClientCache.entrySet()) {
            try {
                LOG.debug("Closing EC2 client for region {}", entry.getKey());
                entry.getValue().close();
            } catch (Exception e) {
                LOG.warn("Error closing EC2 client for region {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
        ec2ClientCache.clear();
        
        // Close CloudWatch clients
        for (Map.Entry<String, CloudWatchClient> entry : cloudWatchClientCache.entrySet()) {
            try {
                LOG.debug("Closing CloudWatch client for region {}", entry.getKey());
                entry.getValue().close();
            } catch (Exception e) {
                LOG.warn("Error closing CloudWatch client for region {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
        cloudWatchClientCache.clear();
    }
    
    @Override
    public void close() {
        LOG.info("Closing AWS cloud provider: {}", config.getProviderId());
        closeClients();
    }
}