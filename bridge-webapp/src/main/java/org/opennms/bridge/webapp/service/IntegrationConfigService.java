package org.opennms.bridge.webapp.service;

import org.opennms.bridge.webapp.controller.OpenNMSController.IntegrationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Service for persisting integration configuration settings between application restarts.
 * This service stores settings in a properties file.
 */
@Service
public class IntegrationConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(IntegrationConfigService.class);
    
    private static final String CONFIG_DIR = "config";
    private static final String INTEGRATION_CONFIG_FILE = "integration-config.properties";
    
    // Load default values from application.yml
    @Value("${integration.discoveryFrequency:15}")
    private int defaultDiscoveryFrequency;
    
    @Value("${integration.collectionFrequency:5}")
    private int defaultCollectionFrequency;
    
    @Value("${integration.nodeSyncFrequency:30}")
    private int defaultNodeSyncFrequency;
    
    @Value("${integration.metricsSendFrequency:10}")
    private int defaultMetricsSendFrequency;
    
    @Value("${integration.enableAutoNodeSync:false}")
    private boolean defaultEnableAutoNodeSync;
    
    @Value("${integration.enableAutoMetricsSend:false}")
    private boolean defaultEnableAutoMetricsSend;
    
    @Value("${integration.disableMockProviders:false}")
    private boolean defaultDisableMockProviders;
    
    // Will be initialized in init()
    private IntegrationConfig integrationConfig;
    
    @PostConstruct
    public void init() {
        try {
            // Create config directory if it doesn't exist
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                LOG.info("Created config directory: {}", configDir);
            }
            
            // Initialize with defaults from application.yml
            integrationConfig = new IntegrationConfig(
                defaultDiscoveryFrequency,
                defaultCollectionFrequency,
                defaultNodeSyncFrequency,
                defaultMetricsSendFrequency,
                defaultEnableAutoNodeSync,
                defaultEnableAutoMetricsSend,
                defaultDisableMockProviders
            );
            
            LOG.info("Initialized integration config with defaults from application.yml: " +
                    "discoveryFreq={}, collectionFreq={}, nodeSyncFreq={}, metricsSendFreq={}, " +
                    "autoNodeSync={}, autoMetricsSend={}, disableMockProviders={}",
                    integrationConfig.getDiscoveryFrequency(),
                    integrationConfig.getCollectionFrequency(),
                    integrationConfig.getNodeSyncFrequency(),
                    integrationConfig.getMetricsSendFrequency(),
                    integrationConfig.isEnableAutoNodeSync(),
                    integrationConfig.isEnableAutoMetricsSend(),
                    integrationConfig.isDisableMockProviders());
            
            // Load settings from file (overrides defaults if file exists)
            loadSettings();
            
            // If the file doesn't exist, save the default settings
            Path settingsFile = Paths.get(CONFIG_DIR, INTEGRATION_CONFIG_FILE);
            if (!Files.exists(settingsFile)) {
                LOG.info("Integration configuration file does not exist yet, saving defaults");
                saveSettings();
            }
            
        } catch (Exception e) {
            LOG.error("Error initializing IntegrationConfigService: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Load integration configuration settings from the properties file.
     */
    private void loadSettings() {
        Path settingsFile = Paths.get(CONFIG_DIR, INTEGRATION_CONFIG_FILE);
        
        if (!Files.exists(settingsFile)) {
            LOG.info("Integration configuration file does not exist yet: {}", settingsFile);
            return;
        }
        
        Properties properties = new Properties();
        try (InputStream in = new FileInputStream(settingsFile.toFile())) {
            properties.load(in);
            LOG.info("Loaded integration configuration from: {}", settingsFile);
            
            // Load discovery frequency
            String discoveryFreq = properties.getProperty("discoveryFrequency");
            if (discoveryFreq != null) {
                try {
                    integrationConfig.setDiscoveryFrequency(Integer.parseInt(discoveryFreq));
                    LOG.info("Loaded discovery frequency: {} minutes", integrationConfig.getDiscoveryFrequency());
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid discovery frequency value: {}", discoveryFreq);
                }
            }
            
            // Load collection frequency
            String collectionFreq = properties.getProperty("collectionFrequency");
            if (collectionFreq != null) {
                try {
                    integrationConfig.setCollectionFrequency(Integer.parseInt(collectionFreq));
                    LOG.info("Loaded collection frequency: {} minutes", integrationConfig.getCollectionFrequency());
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid collection frequency value: {}", collectionFreq);
                }
            }
            
            // Load node sync frequency
            String nodeSyncFreq = properties.getProperty("nodeSyncFrequency");
            if (nodeSyncFreq != null) {
                try {
                    integrationConfig.setNodeSyncFrequency(Integer.parseInt(nodeSyncFreq));
                    LOG.info("Loaded node sync frequency: {} minutes", integrationConfig.getNodeSyncFrequency());
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid node sync frequency value: {}", nodeSyncFreq);
                }
            }
            
            // Load metrics send frequency
            String metricsSendFreq = properties.getProperty("metricsSendFrequency");
            if (metricsSendFreq != null) {
                try {
                    integrationConfig.setMetricsSendFrequency(Integer.parseInt(metricsSendFreq));
                    LOG.info("Loaded metrics send frequency: {} minutes", integrationConfig.getMetricsSendFrequency());
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid metrics send frequency value: {}", metricsSendFreq);
                }
            }
            
            // Load auto node sync
            String autoNodeSync = properties.getProperty("enableAutoNodeSync");
            if (autoNodeSync != null) {
                integrationConfig.setEnableAutoNodeSync(Boolean.parseBoolean(autoNodeSync));
                LOG.info("Loaded auto node sync: {}", integrationConfig.isEnableAutoNodeSync());
            }
            
            // Load auto metrics send
            String autoMetricsSend = properties.getProperty("enableAutoMetricsSend");
            if (autoMetricsSend != null) {
                integrationConfig.setEnableAutoMetricsSend(Boolean.parseBoolean(autoMetricsSend));
                LOG.info("Loaded auto metrics send: {}", integrationConfig.isEnableAutoMetricsSend());
            }
            
            // Load disable mock providers
            String disableMockProviders = properties.getProperty("disableMockProviders");
            if (disableMockProviders != null) {
                integrationConfig.setDisableMockProviders(Boolean.parseBoolean(disableMockProviders));
                LOG.info("Loaded disable mock providers: {}", integrationConfig.isDisableMockProviders());
            }
            
        } catch (IOException e) {
            LOG.error("Failed to load integration configuration: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Save integration configuration settings to the properties file.
     */
    private synchronized void saveSettings() {
        Path settingsFile = Paths.get(CONFIG_DIR, INTEGRATION_CONFIG_FILE);
        File configDir = new File(CONFIG_DIR);
        
        // Ensure config directory exists
        if (!configDir.exists() && !configDir.mkdirs()) {
            LOG.error("Failed to create config directory: {}", configDir);
            return;
        }
        
        Properties properties = new Properties();
        
        // Convert settings to properties
        properties.setProperty("discoveryFrequency", String.valueOf(integrationConfig.getDiscoveryFrequency()));
        properties.setProperty("collectionFrequency", String.valueOf(integrationConfig.getCollectionFrequency()));
        properties.setProperty("nodeSyncFrequency", String.valueOf(integrationConfig.getNodeSyncFrequency()));
        properties.setProperty("metricsSendFrequency", String.valueOf(integrationConfig.getMetricsSendFrequency()));
        properties.setProperty("enableAutoNodeSync", String.valueOf(integrationConfig.isEnableAutoNodeSync()));
        properties.setProperty("enableAutoMetricsSend", String.valueOf(integrationConfig.isEnableAutoMetricsSend()));
        properties.setProperty("disableMockProviders", String.valueOf(integrationConfig.isDisableMockProviders()));
        
        // Save to file
        try (OutputStream out = new FileOutputStream(settingsFile.toFile())) {
            properties.store(out, "OpenNMS Cloud Bridge Integration Configuration");
            LOG.info("Saved integration configuration to: {}", settingsFile);
        } catch (IOException e) {
            LOG.error("Failed to save integration configuration: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get the current integration configuration.
     * 
     * @return the integration configuration
     */
    public IntegrationConfig getConfig() {
        return integrationConfig;
    }
    
    /**
     * Update the integration configuration.
     * 
     * @param config the new configuration
     */
    public void updateConfig(IntegrationConfig config) {
        if (config == null) {
            LOG.warn("Attempted to update integration configuration with null config");
            return;
        }
        
        // Update configuration
        this.integrationConfig = config;
        
        // Save to file
        saveSettings();
        
        LOG.info("Updated integration configuration: discoveryFreq={}, collectionFreq={}, nodeSyncFreq={}, " +
                "metricsSendFreq={}, autoNodeSync={}, autoMetricsSend={}, disableMockProviders={}",
                config.getDiscoveryFrequency(),
                config.getCollectionFrequency(),
                config.getNodeSyncFrequency(),
                config.getMetricsSendFrequency(),
                config.isEnableAutoNodeSync(),
                config.isEnableAutoMetricsSend(),
                config.isDisableMockProviders());
    }
}