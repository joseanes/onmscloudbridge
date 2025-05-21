package org.opennms.bridge.aws;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    // Mark as not required so tests can run without it
    @Autowired(required = false)
    private DiscoveryLogService discoveryLogService;
    
    // Default constructor for testing
    public AwsDiscoveryStrategy() {
        // Empty constructor
    }
    
    // Constructor for explicitly setting discovery log service
    public AwsDiscoveryStrategy(DiscoveryLogService discoveryLogService) {
        this.discoveryLogService = discoveryLogService;
    }
    
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
        
        // Create log entry for discovery start
        Map<String, Object> startLogEntry = new HashMap<>();
        startLogEntry.put("action", "discovery_start");
        startLogEntry.put("region", region);
        startLogEntry.put("provider", config.getProviderId());
        startLogEntry.put("message", "Starting EC2 instance discovery in region " + region);
        
        if (discoveryLogService != null) {
            discoveryLogService.addLogEntry(config.getProviderId(), startLogEntry);
        }
        
        try {
            // Build filters based on configuration
            List<Filter> filters = new ArrayList<>();
            
            // Filter by instance state
            if (!config.getEc2Discovery().getInstanceStates().isEmpty()) {
                filters.add(Filter.builder()
                        .name("instance-state-name")
                        .values(config.getEc2Discovery().getInstanceStates())
                        .build());
                
                // Log the instance state filter
                if (discoveryLogService != null) {
                    Map<String, Object> filterLogEntry = new HashMap<>();
                    filterLogEntry.put("action", "discovery_filter");
                    filterLogEntry.put("region", region);
                    filterLogEntry.put("filter_type", "instance_state");
                    filterLogEntry.put("filter_values", config.getEc2Discovery().getInstanceStates());
                    filterLogEntry.put("message", "Filtering by instance states: " + config.getEc2Discovery().getInstanceStates());
                    discoveryLogService.addLogEntry(config.getProviderId(), filterLogEntry);
                }
            }
            
            // Filter by tags
            List<String> tagFilters = new ArrayList<>();
            for (String tagFilter : config.getEc2Discovery().getFilterByTags()) {
                String[] parts = tagFilter.split("=", 2);
                if (parts.length == 2) {
                    filters.add(Filter.builder()
                            .name("tag:" + parts[0])
                            .values(parts[1])
                            .build());
                    tagFilters.add(tagFilter);
                }
            }
            
            // Log the tag filters
            if (!tagFilters.isEmpty() && discoveryLogService != null) {
                Map<String, Object> filterLogEntry = new HashMap<>();
                filterLogEntry.put("action", "discovery_filter");
                filterLogEntry.put("region", region);
                filterLogEntry.put("filter_type", "tags");
                filterLogEntry.put("filter_values", tagFilters);
                filterLogEntry.put("message", "Filtering by tags: " + tagFilters);
                discoveryLogService.addLogEntry(config.getProviderId(), filterLogEntry);
            }
            
            // Execute EC2 describe instances request
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .filters(filters)
                    .build();
            
            // Log the API request
            if (discoveryLogService != null) {
                Map<String, Object> apiLogEntry = new HashMap<>();
                apiLogEntry.put("action", "api_request");
                apiLogEntry.put("region", region);
                apiLogEntry.put("api", "describeInstances");
                apiLogEntry.put("filters", filters.stream()
                        .map(f -> f.name() + "=" + String.join(",", f.values()))
                        .collect(Collectors.toList()));
                apiLogEntry.put("message", "Calling EC2 describeInstances API with " + filters.size() + " filters");
                discoveryLogService.addLogEntry(config.getProviderId(), apiLogEntry);
            }
            
            // Make the API call
            DescribeInstancesResponse response = ec2Client.describeInstances(request);
            Set<CloudResource> resources = new HashSet<>();
            
            // Log the API response
            if (discoveryLogService != null) {
                int totalInstances = 0;
                for (Reservation reservation : response.reservations()) {
                    totalInstances += reservation.instances().size();
                }
                
                Map<String, Object> responseLogEntry = new HashMap<>();
                responseLogEntry.put("action", "api_response");
                responseLogEntry.put("region", region);
                responseLogEntry.put("api", "describeInstances");
                responseLogEntry.put("reservations", response.reservations().size());
                responseLogEntry.put("instances", totalInstances);
                responseLogEntry.put("message", "Received " + totalInstances + " instances in " + 
                        response.reservations().size() + " reservations");
                discoveryLogService.addLogEntry(config.getProviderId(), responseLogEntry);
            }
            
            // Process each reservation and instance
            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    CloudResource resource = convertInstanceToResource(instance, region, config);
                    resources.add(resource);
                    
                    // Log each discovered instance with details
                    if (discoveryLogService != null) {
                        Map<String, Object> instanceLogEntry = new HashMap<>();
                        instanceLogEntry.put("action", "instance_discovered");
                        instanceLogEntry.put("region", region);
                        instanceLogEntry.put("instance_id", instance.instanceId());
                        instanceLogEntry.put("state", instance.state().nameAsString());
                        instanceLogEntry.put("type", instance.instanceType().toString());
                        
                        // Add key tags for identification
                        Map<String, String> tags = extractTags(instance);
                        if (tags.containsKey("Name")) {
                            instanceLogEntry.put("name", tags.get("Name"));
                        }
                        
                        // Add key properties
                        instanceLogEntry.put("private_ip", instance.privateIpAddress());
                        if (instance.publicIpAddress() != null) {
                            instanceLogEntry.put("public_ip", instance.publicIpAddress());
                        }
                        
                        instanceLogEntry.put("message", "Discovered EC2 instance " + instance.instanceId() + 
                                (tags.containsKey("Name") ? " (" + tags.get("Name") + ")" : "") + 
                                " in state " + instance.state().nameAsString());
                        
                        discoveryLogService.addLogEntry(config.getProviderId(), instanceLogEntry);
                    }
                }
            }
            
            // Log discovery completion
            LOG.info("Discovered {} EC2 instances in region {}", resources.size(), region);
            
            if (discoveryLogService != null) {
                Map<String, Object> completeLogEntry = new HashMap<>();
                completeLogEntry.put("action", "discovery_complete");
                completeLogEntry.put("region", region);
                completeLogEntry.put("instance_count", resources.size());
                completeLogEntry.put("message", "Completed discovery of " + resources.size() + " EC2 instances in region " + region);
                discoveryLogService.addLogEntry(config.getProviderId(), completeLogEntry);
                
                // Store the discovered resources
                discoveryLogService.storeDiscoveredResources(config.getProviderId(), resources);
            }
            
            return resources;
        } catch (Exception e) {
            LOG.error("Error discovering EC2 instances in region {}: {}", region, e.getMessage(), e);
            
            // Log the error
            if (discoveryLogService != null) {
                Map<String, Object> errorLogEntry = new HashMap<>();
                errorLogEntry.put("action", "discovery_error");
                errorLogEntry.put("region", region);
                errorLogEntry.put("error", e.getMessage());
                errorLogEntry.put("error_type", e.getClass().getName());
                errorLogEntry.put("message", "Error discovering EC2 instances: " + e.getMessage());
                discoveryLogService.addLogEntry(config.getProviderId(), errorLogEntry);
            }
            
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
        
        // Set the resource status based on the EC2 instance state
        String instanceState = instance.state().nameAsString();
        resource.setStatus(instanceState);
        
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