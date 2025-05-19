package org.opennms.bridge.webapp.controller;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.api.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for managing cloud providers.
 */
@RestController
@RequestMapping("/api/cloud-providers")
public class CloudProviderController {

    @Autowired
    private List<CloudProvider> cloudProviders;
    
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllCloudProviders() {
        List<Map<String, Object>> providerData = cloudProviders.stream()
                .map(provider -> {
                    try {
                        // Convert provider to a map of properties for the API
                        Map<String, Object> data = provider.getConfiguration();
                        
                        // Add basic provider information
                        data.put("id", provider.getProviderId());
                        data.put("type", provider.getProviderType());
                        data.put("name", provider.getDisplayName());
                        
                        // Add validation status
                        try {
                            ValidationResult validationResult = provider.validate();
                            data.put("valid", validationResult.isValid());
                            data.put("validationMessage", validationResult.getMessage());
                        } catch (Exception e) {
                            data.put("valid", false);
                            data.put("validationMessage", "Validation error: " + e.getMessage());
                        }
                        
                        // Add provider capabilities
                        data.put("supportedMetrics", provider.getSupportedMetrics());
                        data.put("availableRegions", provider.getAvailableRegions());
                        
                        return data;
                    } catch (Exception e) {
                        // Log error and return basic information
                        Map<String, Object> data = Map.of(
                                "id", provider.getProviderId(),
                                "type", provider.getProviderType(),
                                "name", provider.getDisplayName(),
                                "valid", false,
                                "validationMessage", "Error loading provider details: " + e.getMessage()
                        );
                        return data;
                    }
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(providerData);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCloudProvider(@PathVariable String id) {
        return cloudProviders.stream()
                .filter(provider -> provider.getProviderId().equals(id))
                .findFirst()
                .map(provider -> {
                    try {
                        // Convert provider to a map of properties for the API
                        Map<String, Object> data = provider.getConfiguration();
                        
                        // Add basic provider information
                        data.put("id", provider.getProviderId());
                        data.put("type", provider.getProviderType());
                        data.put("name", provider.getDisplayName());
                        
                        // Add validation status
                        try {
                            ValidationResult validationResult = provider.validate();
                            data.put("valid", validationResult.isValid());
                            data.put("validationMessage", validationResult.getMessage());
                        } catch (Exception e) {
                            data.put("valid", false);
                            data.put("validationMessage", "Validation error: " + e.getMessage());
                        }
                        
                        // Add provider capabilities
                        data.put("supportedMetrics", provider.getSupportedMetrics());
                        data.put("availableRegions", provider.getAvailableRegions());
                        
                        return ResponseEntity.ok(data);
                    } catch (Exception e) {
                        // Log error and return basic information
                        Map<String, Object> data = Map.of(
                                "id", provider.getProviderId(),
                                "type", provider.getProviderType(),
                                "name", provider.getDisplayName(),
                                "valid", false,
                                "validationMessage", "Error loading provider details: " + e.getMessage()
                        );
                        return ResponseEntity.ok(data);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/{id}/validate")
    public ResponseEntity<Map<String, Object>> validateCloudProvider(@PathVariable String id) {
        return cloudProviders.stream()
                .filter(provider -> provider.getProviderId().equals(id))
                .findFirst()
                .map(provider -> {
                    try {
                        ValidationResult result = provider.validate();
                        Map<String, Object> response = Map.of(
                                "id", provider.getProviderId(),
                                "valid", result.isValid(),
                                "message", result.getMessage()
                        );
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        Map<String, Object> response = Map.of(
                                "id", provider.getProviderId(),
                                "valid", false,
                                "message", "Validation error: " + e.getMessage()
                        );
                        return ResponseEntity.ok(response);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> createCloudProvider(@Valid @RequestBody Map<String, Object> providerConfig) {
        // In a real implementation, you would create a new cloud provider instance
        // Currently, providers are loaded from configuration
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("message", "Provider creation not implemented - providers are configured via application.yml"));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCloudProvider(
            @PathVariable String id, 
            @Valid @RequestBody Map<String, Object> providerConfig) {
        
        return cloudProviders.stream()
                .filter(provider -> provider.getProviderId().equals(id))
                .findFirst()
                .map(provider -> {
                    try {
                        // Update provider configuration
                        provider.updateConfiguration(providerConfig);
                        
                        // Get updated configuration
                        Map<String, Object> updatedConfig = provider.getConfiguration();
                        updatedConfig.put("id", provider.getProviderId());
                        updatedConfig.put("type", provider.getProviderType());
                        updatedConfig.put("name", provider.getDisplayName());
                        
                        return ResponseEntity.ok(updatedConfig);
                    } catch (Exception e) {
                        Map<String, Object> response = Map.of(
                                "id", provider.getProviderId(),
                                "error", "Failed to update provider configuration: " + e.getMessage()
                        );
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCloudProvider(@PathVariable String id) {
        // In a real implementation, you would delete the cloud provider
        // Currently, providers are loaded from configuration
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}