package org.opennms.bridge.aws;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AWS discovery strategy for EC2 instances.
 * This class handles the discovery of EC2 instances and converts them into OpenNMS compatible resources.
 */
@Component
public class AwsDiscoveryStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(AwsDiscoveryStrategy.class);
    
    /**
     * Discover EC2 instances in a specific region.
     *
     * @param ec2Client       EC2 client for the region
     * @param region          AWS region
     * @param config          AWS configuration properties
     * @return set of discovered cloud resources
     */
    public Set<CloudResource> discoverEc2Instances(Ec2Client ec2Client, String region, AwsConfigurationProperties config) {
        LOG.info("Discovering EC2 instances in region {}", region);
        
        try {
            // Build filters based on configuration
            List<Filter> filters = new ArrayList<>();
            
            // Filter by instance state
            if (!config.getEc2Discovery().getInstanceStates().isEmpty()) {
                filters.add(Filter.builder()
                        .name("instance-state-name")
                        .values(config.getEc2Discovery().getInstanceStates())
                        .build());
            }
            
            // Filter by tags
            for (String tagFilter : config.getEc2Discovery().getFilterByTags()) {
                String[] parts = tagFilter.split("=", 2);
                if (parts.length == 2) {
                    filters.add(Filter.builder()
                            .name("tag:" + parts[0])
                            .values(parts[1])
                            .build());
                }
            }
            
            // Execute EC2 describe instances request
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .filters(filters)
                    .build();
            
            DescribeInstancesResponse response = ec2Client.describeInstances(request);
            Set<CloudResource> resources = new HashSet<>();
            
            // Process each reservation and instance
            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    CloudResource resource = convertInstanceToResource(instance, region, config);
                    resources.add(resource);
                }
            }
            
            LOG.info("Discovered {} EC2 instances in region {}", resources.size(), region);
            return resources;
        } catch (Exception e) {
            LOG.error("Error discovering EC2 instances in region {}: {}", region, e.getMessage(), e);
            return Collections.emptySet();
        }
    }
    
    /**
     * Convert an EC2 instance to a cloud resource.
     *
     * @param instance        EC2 instance
     * @param region          AWS region
     * @param config          AWS configuration properties
     * @return cloud resource
     */
    private CloudResource convertInstanceToResource(Instance instance, String region, AwsConfigurationProperties config) {
        Map<String, String> metadata = new HashMap<>();
        Map<String, String> tags = extractTags(instance);
        
        metadata.put("resourceType", "ec2-instance");
        metadata.put("instanceId", instance.instanceId());
        metadata.put("instanceType", instance.instanceType().toString());
        metadata.put("region", region);
        metadata.put("availabilityZone", instance.placement().availabilityZone());
        metadata.put("privateIpAddress", instance.privateIpAddress());
        
        // Add public IP if available
        if (instance.publicIpAddress() != null) {
            metadata.put("publicIpAddress", instance.publicIpAddress());
        }
        
        // Add instance state
        metadata.put("state", instance.state().nameAsString());
        
        // Add VPC and subnet info if available
        if (instance.vpcId() != null) {
            metadata.put("vpcId", instance.vpcId());
        }
        if (instance.subnetId() != null) {
            metadata.put("subnetId", instance.subnetId());
        }
        
        // Add platform details
        metadata.put("platform", instance.platformAsString() != null ? instance.platformAsString() : "linux");
        if (instance.platformDetails() != null) {
            metadata.put("platformDetails", instance.platformDetails());
        }
        
        // Add selected tags as metadata
        for (String tagName : config.getEc2Discovery().getIncludeTags()) {
            if (tags.containsKey(tagName)) {
                metadata.put("tag_" + tagName, tags.get(tagName));
            }
        }
        
        // Extract name from tags for display
        String displayName = tags.getOrDefault("Name", instance.instanceId());
        
        // Create the resource
        CloudResource resource = new CloudResource(instance.instanceId(), displayName, "EC2", region);
        resource.setTags(tags);
        
        // Add properties
        resource.addProperty("providerId", config.getProviderId());
        resource.addProperty("address", instance.privateIpAddress());
        resource.addProperty("publicAddress", instance.publicIpAddress());
        
        // Add metadata as properties
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            resource.addProperty(entry.getKey(), entry.getValue());
        }
        
        return resource;
    }
    
    /**
     * Extract tags from an EC2 instance.
     *
     * @param instance EC2 instance
     * @return map of tag keys to values
     */
    private Map<String, String> extractTags(Instance instance) {
        return instance.tags().stream()
                .collect(Collectors.toMap(
                        Tag::key,
                        Tag::value,
                        (existing, replacement) -> existing
                ));
    }
}