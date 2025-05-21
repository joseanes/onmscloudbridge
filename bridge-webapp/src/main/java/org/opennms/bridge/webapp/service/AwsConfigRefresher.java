package org.opennms.bridge.webapp.service;

import org.opennms.bridge.aws.AwsCloudProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;

/**
 * Service to refresh AWS provider configuration when settings change.
 * This enables real-time changes to emergency bypass mode without requiring a restart.
 */
@Service
public class AwsConfigRefresher {
    private static final Logger LOG = LoggerFactory.getLogger(AwsConfigRefresher.class);
    
    @Autowired
    private AwsCloudProvider awsCloudProvider;
    
    /**
     * Refreshes the AWS emergency bypass setting.
     * This uses reflection to update the static field directly.
     * 
     * @param bypassEnabled whether emergency bypass should be enabled
     */
    public void refreshEmergencyBypassSetting(boolean bypassEnabled) {
        LOG.info("Refreshing AWS emergency bypass setting to: {}", bypassEnabled);
        
        try {
            // Get the EMERGENCY_BYPASS field using reflection
            Field bypassField = AwsCloudProvider.class.getDeclaredField("EMERGENCY_BYPASS");
            bypassField.setAccessible(true);
            
            // Set the new value
            bypassField.setBoolean(null, bypassEnabled); // null because it's a static field
            
            LOG.info("Successfully updated AWS emergency bypass setting to: {}", bypassEnabled);
        } catch (Exception e) {
            LOG.error("Failed to update AWS emergency bypass setting: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Refreshes AWS provider configuration.
     * This triggers a reload of AWS configuration settings.
     */
    public void refreshAwsConfiguration() {
        LOG.info("Refreshing AWS provider configuration");
        
        try {
            // Create a minimal configuration update to trigger a refresh
            java.util.Map<String, Object> config = new java.util.HashMap<>();
            
            // Update timestamp field to force refresh
            config.put("_refreshTimestamp", System.currentTimeMillis());
            
            // Apply the configuration update
            awsCloudProvider.updateConfiguration(config);
            
            LOG.info("Successfully refreshed AWS provider configuration");
        } catch (Exception e) {
            LOG.error("Failed to refresh AWS provider configuration: {}", e.getMessage(), e);
        }
    }
}