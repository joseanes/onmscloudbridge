package org.opennms.bridge.api;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a cloud node discovered during discovery process.
 */
public class DiscoveredNode {
    
    private String id;
    private String name;
    private String type;
    private String providerId;
    private String region;
    private Instant discoveryTime;
    private Set<String> ipAddresses;
    private Map<String, String> tags;
    private Map<String, Object> attributes;
    private CloudResource cloudResource;
    
    public DiscoveredNode() {
        this.ipAddresses = new HashSet<>();
        this.tags = new HashMap<>();
        this.attributes = new HashMap<>();
        this.discoveryTime = Instant.now();
    }
    
    public DiscoveredNode(String id, String name, String type, String providerId) {
        this();
        this.id = id;
        this.name = name;
        this.type = type;
        this.providerId = providerId;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getProviderId() {
        return providerId;
    }
    
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
    
    public String getRegion() {
        return region;
    }
    
    public void setRegion(String region) {
        this.region = region;
    }
    
    public Instant getDiscoveryTime() {
        return discoveryTime;
    }
    
    public void setDiscoveryTime(Instant discoveryTime) {
        this.discoveryTime = discoveryTime;
    }
    
    public Set<String> getIpAddresses() {
        return ipAddresses;
    }
    
    public void setIpAddresses(Set<String> ipAddresses) {
        this.ipAddresses = ipAddresses != null ? ipAddresses : new HashSet<>();
    }
    
    public void addIpAddress(String ipAddress) {
        this.ipAddresses.add(ipAddress);
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
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }
    
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }
    
    public CloudResource getCloudResource() {
        return cloudResource;
    }
    
    public void setCloudResource(CloudResource cloudResource) {
        this.cloudResource = cloudResource;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscoveredNode that = (DiscoveredNode) o;
        return Objects.equals(id, that.id) && Objects.equals(providerId, that.providerId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, providerId);
    }
    
    /**
     * Gets the foreign ID for OpenNMS requisition.
     * 
     * @return the foreign ID
     */
    public String getForeignId() {
        return id;
    }
    
    /**
     * Gets the node label for OpenNMS requisition.
     * 
     * @return the node label
     */
    public String getNodeLabel() {
        return name;
    }
    
    /**
     * Gets the location for OpenNMS requisition.
     * 
     * @return the location
     */
    public String getLocation() {
        return region;
    }
    
    @Override
    public String toString() {
        return "DiscoveredNode{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", providerId='" + providerId + '\'' +
                ", region='" + region + '\'' +
                ", ipAddresses=" + ipAddresses +
                '}';
    }
}