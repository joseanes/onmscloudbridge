package org.opennms.bridge.webapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web application configuration for OpenNMS Cloud Bridge.
 * Configures security, CORS, and other web-related settings.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
    
    /**
     * Configure static resource mapping to ensure HTML files are accessible
     * at the root context path.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Ensure static resources are served properly from the classpath
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}