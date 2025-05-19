package org.opennms.bridge.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for the OpenNMS Cloud Bridge web application.
 * This application provides a REST API for managing cloud providers, discovery,
 * and collection services.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"org.opennms.bridge"})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}