package org.opennms.bridge.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opennms.bridge.api.DiscoveredNode;
import org.opennms.bridge.api.MetricCollection;
import org.opennms.bridge.api.MetricCollection.Metric;
import org.opennms.bridge.api.CloudResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.HttpURLConnection;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Client for interacting with the OpenNMS REST API.
 * This client handles all REST calls to OpenNMS for node provisioning, metric submission, and more.
 */
@Component
public class OpenNMSClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenNMSClient.class);
    
    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${opennms.base-url}")
    private String baseUrl;
    
    @Value("${opennms.username}")
    private String username;
    
    @Value("${opennms.password}")
    private String password;
    
    @Value("${opennms.api.v1.base-path}")
    private String v1BasePath;
    
    @Value("${opennms.api.v2.base-path}")
    private String v2BasePath;
    
    @Value("${opennms.default-location}")
    private String defaultLocation;

    private boolean isConnected = false;
    
    private final HttpHeaders authHeaders;
    
    @Autowired
    public OpenNMSClient(
            RestTemplate restTemplate,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        
        // Normalize the baseUrl
        if (this.baseUrl != null) {
            if (!this.baseUrl.endsWith("/")) {
                this.baseUrl = this.baseUrl + "/";
            }
            if (!this.baseUrl.contains("/opennms/") && !this.baseUrl.endsWith("/opennms/")) {
                this.baseUrl = this.baseUrl + "opennms/";
            }
            LOG.info("Normalized OpenNMS base URL: {}", this.baseUrl);
        }
        
        // Setup authentication headers
        this.authHeaders = new HttpHeaders();
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        this.authHeaders.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
        this.authHeaders.setContentType(MediaType.APPLICATION_JSON);
        this.authHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        
        // Setup WebClient with authentication
        this.webClient = webClientBuilder
                .baseUrl(this.baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
    
    /**
     * Test connectivity to the OpenNMS server
     *
     * @return true if connection is successful
     */
    public boolean testConnection() {
        return testConnection(this.baseUrl, this.username, this.password);
    }

    /**
     * Test connectivity to the OpenNMS server with custom credentials
     *
     * @param baseUrl the OpenNMS server URL
     * @param username the username to use
     * @param password the password to use
     * @return true if connection is successful
     */
    public boolean testConnection(String baseUrl, String username, String password) {
        try {
            // Make sure baseUrl has the right format - it should end with / and we must ensure it has /opennms
            if (!baseUrl.endsWith("/")) {
                baseUrl = baseUrl + "/";
            }
            if (!baseUrl.contains("/opennms/") && !baseUrl.endsWith("/opennms/")) {
                baseUrl = baseUrl + "opennms/";
            }
            LOG.info("Normalized base URL for OpenNMS: {}", baseUrl);
            
            // Try the REST API endpoint format
            // Fixed URL construction to avoid duplicate /opennms in the path
            String url = baseUrl + "rest/info";
            LOG.info("Testing connection to OpenNMS at URL: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            
            // Set Basic Auth explicitly with explicit credentials
            LOG.debug("Using username: {} for OpenNMS connection test", username);
            if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                LOG.warn("OpenNMS username or password is empty, authentication will likely fail");
            }
            
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
            
            LOG.debug("Headers for test: {}", headers);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            boolean success = response.getStatusCode().is2xxSuccessful();
            
            if (success) {
                LOG.info("Successfully connected to OpenNMS at {} using REST API", baseUrl);
                return true;
            }
            
            return false;
        } catch (RestClientException restError) {
            LOG.info("Couldn't connect to REST API, trying v2 API...: {}", restError.getMessage());
            
            try {
                // Try the v2 API endpoint format as fallback
                String url = baseUrl + "api/v2/info";
                LOG.info("Testing connection to OpenNMS at URL: {}", url);
                
                // Create fresh headers for the second attempt
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                
                // Set Basic Auth explicitly with explicit credentials
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                boolean success = response.getStatusCode().is2xxSuccessful();
                
                if (success) {
                    LOG.info("Successfully connected to OpenNMS at {} using v2 API", baseUrl);
                } else {
                    LOG.warn("Connection to OpenNMS v2 API returned non-success status: {}", response.getStatusCode());
                }
                
                return success;
            } catch (Exception e) {
                if (e instanceof org.springframework.web.client.HttpClientErrorException.NotFound) {
                    LOG.warn("OpenNMS API endpoint not found at {}. Please verify the API path is correct. Expected paths: {} or {}", 
                            baseUrl, v1BasePath + "/info", v2BasePath + "/info");
                } else if (e instanceof org.springframework.web.client.HttpClientErrorException.Unauthorized) {
                    LOG.warn("Authentication failed for OpenNMS at {}. Check your username and password.", baseUrl);
                } else if (e instanceof org.springframework.web.client.ResourceAccessException) {
                    LOG.warn("Cannot access OpenNMS at {}. Server may not be running or network issues.", baseUrl);
                } else {
                    LOG.error("Failed to connect to OpenNMS at {}: {}", baseUrl, e.getMessage());
                    LOG.debug("Connection error details", e);
                }
                return false;
            }
        }
    }
    
    /**
     * Create or update a requisition in OpenNMS
     *
     * @param foreignSource the foreign source name
     * @param nodes the nodes to include in the requisition
     */
    public void createOrUpdateRequisition(String foreignSource, Set<DiscoveredNode> nodes) {
        LOG.info("Creating/updating requisition '{}' with {} nodes", foreignSource, nodes.size());
        
        try {
            String url = baseUrl + "/api/v2/requisitions";
            
            // Convert to requisition format
            Map<String, Object> requisition = createRequisitionObject(foreignSource, nodes);
            
            // Send request
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requisition, authHeaders);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.error("Failed to create/update requisition: {}", response.getStatusCode());
                throw new RestClientException("Failed to create/update requisition: " + response.getStatusCode());
            }
            
            LOG.info("Successfully created/updated requisition '{}'", foreignSource);
        } catch (Exception e) {
            LOG.error("Error creating/updating requisition", e);
            throw new RuntimeException("Failed to create/update requisition", e);
        }
    }
    
    /**
     * Synchronize a requisition to apply changes
     *
     * @param foreignSource the foreign source name
     */
    public void synchronizeRequisition(String foreignSource) {
        LOG.info("Synchronizing requisition '{}'", foreignSource);
        
        try {
            String url = baseUrl + "/api/v2/requisitions/" + foreignSource + "/import";
            HttpEntity<String> entity = new HttpEntity<>(authHeaders);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.error("Failed to synchronize requisition: {}", response.getStatusCode());
                throw new RestClientException("Failed to synchronize requisition: " + response.getStatusCode());
            }
            
            LOG.info("Successfully synchronized requisition '{}'", foreignSource);
        } catch (Exception e) {
            LOG.error("Error synchronizing requisition", e);
            throw new RuntimeException("Failed to synchronize requisition", e);
        }
    }
    
    /**
     * Create a new monitoring location
     *
     * @param locationName the location name
     * @param monitoringArea the monitoring area (e.g., cloud region)
     */
    public void createLocation(String locationName, String monitoringArea) {
        LOG.info("Creating location '{}' with monitoring area '{}'", locationName, monitoringArea);
        
        try {
            String url = baseUrl + "/api/v2/monitoringLocations";
            
            // Create location request
            Map<String, Object> location = Map.of(
                "location", locationName,
                "monitoringArea", monitoringArea,
                "geolocation", Map.of()
            );
            
            // Send request
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(location, authHeaders);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.error("Failed to create location: {}", response.getStatusCode());
                throw new RestClientException("Failed to create location: " + response.getStatusCode());
            }
            
            LOG.info("Successfully created location '{}'", locationName);
        } catch (Exception e) {
            LOG.error("Error creating location", e);
            throw new RuntimeException("Failed to create location", e);
        }
    }
    
    /**
     * Submit metrics to OpenNMS
     *
     * @param nodeId the OpenNMS node ID
     * @param metrics the metrics to submit
     */
    public void submitMetrics(String nodeId, MetricCollection metrics) {
        LOG.info("Submitting {} metrics for node '{}'", metrics.getMetrics().size(), nodeId);
        
        try {
            String url = baseUrl + "/api/v2/measurements";
            
            // Convert to measurement format
            Map<String, Object> measurements = createMeasurementsObject(nodeId, metrics);
            
            // Send request using WebClient for better async handling with large metric sets
            webClient
                .post()
                .uri("/api/v2/measurements")
                .bodyValue(measurements)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(),
                    clientResponse -> Mono.error(new RestClientException(
                        "Failed to submit metrics: " + clientResponse.statusCode()))
                )
                .bodyToMono(Map.class)
                .block(); // Block for simplicity, would be async in production
            
            LOG.info("Successfully submitted metrics for node '{}'", nodeId);
        } catch (Exception e) {
            LOG.error("Error submitting metrics", e);
            throw new RuntimeException("Failed to submit metrics", e);
        }
    }
    
    /**
     * Find a node by foreign ID
     *
     * @param foreignSource the foreign source
     * @param foreignId the foreign ID
     * @return the OpenNMS node ID
     */
    public String findNodeByForeignId(String foreignSource, String foreignId) {
        LOG.info("Looking up node with foreignSource '{}' and foreignId '{}'", foreignSource, foreignId);
        
        try {
            String url = baseUrl + "/api/v2/nodes?foreignSource=" + foreignSource 
                    + "&foreignId=" + foreignId + "&limit=1";
            
            HttpEntity<String> entity = new HttpEntity<>(authHeaders);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.error("Failed to find node: {}", response.getStatusCode());
                return null;
            }
            
            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("nodes") || ((List)body.get("nodes")).isEmpty()) {
                LOG.warn("No node found for foreignSource '{}' and foreignId '{}'", foreignSource, foreignId);
                return null;
            }
            
            Map<String, Object> node = (Map<String, Object>) ((List)body.get("nodes")).get(0);
            String nodeId = node.get("id").toString();
            LOG.info("Found node ID '{}' for foreignSource '{}' and foreignId '{}'", 
                    nodeId, foreignSource, foreignId);
            
            return nodeId;
        } catch (Exception e) {
            LOG.error("Error finding node by foreign ID", e);
            return null;
        }
    }
    
    /**
     * Create a requisition object from nodes
     *
     * @param foreignSource the foreign source name
     * @param nodes the nodes to include
     * @return the requisition object
     */
    private Map<String, Object> createRequisitionObject(String foreignSource, Set<DiscoveredNode> nodes) {
        // Convert nodes to requisition format
        List<Map<String, Object>> requisitionNodes = nodes.stream()
            .map(this::convertToRequisitionNode)
            .collect(Collectors.toList());
        
        return Map.of(
            "foreign-source", foreignSource,
            "nodes", requisitionNodes
        );
    }
    
    /**
     * Convert a discovered node to a requisition node
     *
     * @param node the discovered node
     * @return the requisition node
     */
    private Map<String, Object> convertToRequisitionNode(DiscoveredNode node) {
        // This is a simplified implementation
        // In a real implementation, this would map all node attributes
        return Map.of(
            "foreign-id", node.getForeignId(),
            "node-label", node.getNodeLabel(),
            "location", node.getLocation(),
            "interfaces", Collections.emptyList()
        );
    }
    
    /**
     * Create a measurements object from metrics
     *
     * @param nodeId the OpenNMS node ID
     * @param metrics the metrics to submit
     * @return the measurements object
     */
    private Map<String, Object> createMeasurementsObject(String nodeId, MetricCollection metrics) {
        // Create a map for the metrics data
        Map<String, Object> data = new HashMap<>();
        
        // Convert each metric to the required format
        metrics.getMetrics().forEach(metric -> {
            data.put(metric.getName(), metric.getValue());
        });
        
        // Create the final measurements object
        Map<String, Object> result = new HashMap<>();
        result.put("node", nodeId);
        result.put("metrics", data);
        
        return result;
    }
    
    /**
     * Check if the base URL is accessible at all, without testing specific API endpoints
     * 
     * @return true if the URL is accessible
     */
    public boolean isUrlAccessible() {
        try {
            // Disable redirects to better diagnose issues
            RestTemplate simpleTemplate = new RestTemplate();
            simpleTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    super.prepareConnection(connection, httpMethod);
                    connection.setInstanceFollowRedirects(false);
                }
            });
            
            // Create headers with authentication
            HttpHeaders headers = new HttpHeaders();
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
            
            // Try to access the base URL
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = simpleTemplate.exchange(
                baseUrl, 
                HttpMethod.HEAD, 
                entity, 
                String.class
            );
            
            return response.getStatusCode().is2xxSuccessful() || 
                   response.getStatusCode().is3xxRedirection();
        } catch (ResourceAccessException e) {
            // Cannot connect at all
            LOG.warn("Cannot access URL {}: {}", baseUrl, e.getMessage());
            return false;
        } catch (Exception e) {
            // Any response (even error) means the server is there
            LOG.info("URL {} returned an error but is accessible: {}", baseUrl, e.getMessage());
            return true;
        }
    }

    public void createOrUpdateNode(CloudResource resource) {
        try {
            // First try to get the node by foreign ID
            String foreignId = resource.getProviderId() + ":" + resource.getResourceId();
            String url = baseUrl + v1BasePath + "/nodes?foreignId=" + URLEncoder.encode(foreignId, StandardCharsets.UTF_8);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Node exists, update it
                Map<String, Object> node = response.getBody();
                String nodeId = (String) node.get("id");
                url = baseUrl + v1BasePath + "/nodes/" + nodeId;
                
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("label", resource.getName());
                updateData.put("location", defaultLocation);
                updateData.put("foreignId", foreignId);
                
                restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateData, authHeaders),
                    Map.class
                );
            } else {
                // Node doesn't exist, create it
                url = baseUrl + v1BasePath + "/nodes";
                
                Map<String, Object> nodeData = new HashMap<>();
                nodeData.put("label", resource.getName());
                nodeData.put("location", defaultLocation);
                nodeData.put("foreignId", foreignId);
                
                restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(nodeData, authHeaders),
                    Map.class
                );
            }
        } catch (Exception e) {
            LOG.error("Error creating/updating node for resource {}: {}", resource.getResourceId(), e.getMessage());
            throw new RuntimeException("Failed to create/update node in OpenNMS", e);
        }
    }
    
    public void deleteNode(String providerId, String resourceId) {
        try {
            String foreignId = providerId + ":" + resourceId;
            String url = baseUrl + v1BasePath + "/nodes?foreignId=" + URLEncoder.encode(foreignId, StandardCharsets.UTF_8);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> node = response.getBody();
                String nodeId = (String) node.get("id");
                url = baseUrl + v1BasePath + "/nodes/" + nodeId;
                
                restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders),
                    Void.class
                );
            }
        } catch (Exception e) {
            LOG.error("Error deleting node for resource {}: {}", resourceId, e.getMessage());
            throw new RuntimeException("Failed to delete node from OpenNMS", e);
        }
    }
    
    public void updateNodeMetrics(String providerId, String resourceId, Map<String, Double> metrics) {
        try {
            String foreignId = providerId + ":" + resourceId;
            String url = baseUrl + v1BasePath + "/nodes?foreignId=" + URLEncoder.encode(foreignId, StandardCharsets.UTF_8);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> node = response.getBody();
                String nodeId = (String) node.get("id");
                
                // Update metrics using the v2 API
                url = baseUrl + v2BasePath + "/nodes/" + nodeId + "/metrics";
                
                Map<String, Object> metricsData = new HashMap<>();
                metricsData.put("metrics", metrics);
                
                restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(metricsData, authHeaders),
                    Map.class
                );
            }
        } catch (Exception e) {
            LOG.error("Error updating metrics for resource {}: {}", resourceId, e.getMessage());
            throw new RuntimeException("Failed to update metrics in OpenNMS", e);
        }
    }
}