package org.opennms.bridge.webapp.config;

import org.opennms.bridge.api.CloudProvider;
import org.opennms.bridge.webapp.service.MockAwsCloudProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean configuration class for the application.
 */
@Configuration
public class BeanConfig {
    
    /**
     * Creates a list of cloud providers for the application.
     * 
     * @param mockAwsCloudProvider Mock AWS cloud provider
     * @return List of cloud providers
     */
    @Bean
    public List<CloudProvider> cloudProviders(MockAwsCloudProvider mockAwsCloudProvider) {
        List<CloudProvider> providers = new ArrayList<>();
        providers.add(mockAwsCloudProvider);
        return providers;
    }
}