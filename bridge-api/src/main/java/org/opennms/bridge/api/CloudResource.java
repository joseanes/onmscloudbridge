package org.opennms.bridge.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a resource in a cloud environment (VM, container, service, etc.)
 */
public class CloudResource {
    
    private String id;
    private String name;
    private String type;
    private String region;
    private String status;
    private Map<String, String> tags;
    private Map<String, Object> properties;
    
    public CloudResource() {
        this.tags = new HashMap<>();
        this.properties = new HashMap<>();
    }
    
    public CloudResource(String id, String name, String type, String region) {
        this();
        this.id = id;
        this.name = name;
        this.type = type;
        this.region = region;
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
    
    public String getRegion() {
        return region;
    }
    
    public void setRegion(String region) {
        this.region = region;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Map<String, String> getTags() {
        return tags;
    }
    
    public void setTags(Map<String, String> tags) {
        this.tags = tags != null ? tags : new HashMap<>();
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties != null ? properties : new HashMap<>();
    }
    
    public void addTag(String key, String value) {
        this.tags.put(key, value);
    }
    
    public void addProperty(String key, Object value) {
        this.properties.put(key, value);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloudResource that = (CloudResource) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    /**
     * Gets the resource unique identifier.
     * 
     * @return the resource ID
     */
    public String getResourceId() {
        return id;
    }
    
    /**
     * Gets the provider ID associated with this resource.
     * 
     * @return the provider ID
     */
    public String getProviderId() {
        return (String) properties.getOrDefault("providerId", null);
    }
    
    /**
     * Gets the provider type associated with this resource.
     * 
     * @return the provider type
     */
    public String getProviderType() {
        return (String) properties.getOrDefault("providerType", null);
    }
    
    /**
     * Gets the resource type (EC2 instance, Lambda function, etc.)
     * 
     * @return the resource type
     */
    public String getResourceType() {
        return type;
    }
    
    /**
     * Gets the display name for this resource.
     * 
     * @return the display name
     */
    public String getDisplayName() {
        return name;
    }
    
    @Override
    public String toString() {
        return "CloudResource{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", region='" + region + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}