package org.opennms.bridge.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for AWS provider.
 */
@Configuration
@ConfigurationProperties(prefix = "cloud.aws")
public class AwsConfigurationProperties {

    /**
     * Provider ID for this AWS provider instance.
     */
    private String providerId = "aws-default";
    
    /**
     * Display name for this AWS provider instance.
     */
    private String displayName = "AWS Cloud Provider";
    
    /**
     * AWS access key ID.
     * If not specified, the default AWS credential chain will be used.
     */
    private String accessKeyId;
    
    /**
     * AWS secret access key.
     * If not specified, the default AWS credential chain will be used.
     */
    private String secretAccessKey;
    
    /**
     * AWS session token for temporary credentials.
     */
    private String sessionToken;
    
    /**
     * AWS role ARN to assume.
     * If specified, the provider will attempt to assume this role.
     */
    private String roleArn;
    
    /**
     * AWS regions to monitor.
     * If empty, the provider will use the region from the environment.
     */
    private List<String> regions = new ArrayList<>();
    
    /**
     * Connection timeout for AWS API calls.
     */
    private Duration connectionTimeout = Duration.ofSeconds(10);
    
    /**
     * Read timeout for AWS API calls.
     */
    private Duration readTimeout = Duration.ofSeconds(30);
    
    /**
     * Maximum number of retries for AWS API calls.
     */
    private int maxRetries = 3;
    
    /**
     * EC2 instance discovery configuration.
     */
    private Ec2Discovery ec2Discovery = new Ec2Discovery();
    
    /**
     * CloudWatch metric collection configuration.
     */
    private CloudWatchCollection cloudWatchCollection = new CloudWatchCollection();
    
    /**
     * EC2 instance discovery configuration.
     */
    public static class Ec2Discovery {
        /**
         * Whether EC2 instance discovery is enabled.
         */
        private boolean enabled = true;
        
        /**
         * EC2 instance tags to include as metadata.
         */
        private List<String> includeTags = List.of("Name", "Environment", "Service");
        
        /**
         * Filter EC2 instances by tag.
         * Format: key=value
         */
        private List<String> filterByTags = new ArrayList<>();
        
        /**
         * Filter EC2 instances by instance state.
         * Valid values: pending, running, stopping, stopped, shutting-down, terminated
         */
        private List<String> instanceStates = List.of("running");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getIncludeTags() {
            return includeTags;
        }

        public void setIncludeTags(List<String> includeTags) {
            this.includeTags = includeTags;
        }

        public List<String> getFilterByTags() {
            return filterByTags;
        }

        public void setFilterByTags(List<String> filterByTags) {
            this.filterByTags = filterByTags;
        }

        public List<String> getInstanceStates() {
            return instanceStates;
        }

        public void setInstanceStates(List<String> instanceStates) {
            this.instanceStates = instanceStates;
        }
    }
    
    /**
     * CloudWatch metric collection configuration.
     */
    public static class CloudWatchCollection {
        /**
         * Whether CloudWatch metric collection is enabled.
         */
        private boolean enabled = true;
        
        /**
         * CloudWatch metrics to collect.
         * Default set includes common EC2 instance metrics.
         */
        private List<String> metrics = List.of(
            "CPUUtilization",
            "NetworkIn",
            "NetworkOut",
            "DiskReadBytes",
            "DiskWriteBytes",
            "StatusCheckFailed"
        );
        
        /**
         * CloudWatch metric collection period.
         */
        private Duration period = Duration.ofMinutes(5);
        
        /**
         * CloudWatch metric statistics to collect.
         */
        private List<String> statistics = List.of("Average", "Maximum", "Minimum");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getMetrics() {
            return metrics;
        }

        public void setMetrics(List<String> metrics) {
            this.metrics = metrics;
        }

        public Duration getPeriod() {
            return period;
        }

        public void setPeriod(Duration period) {
            this.period = period;
        }

        public List<String> getStatistics() {
            return statistics;
        }

        public void setStatistics(List<String> statistics) {
            this.statistics = statistics;
        }
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public void setRoleArn(String roleArn) {
        this.roleArn = roleArn;
    }

    public List<String> getRegions() {
        return regions;
    }

    public void setRegions(List<String> regions) {
        this.regions = regions;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Ec2Discovery getEc2Discovery() {
        return ec2Discovery;
    }

    public void setEc2Discovery(Ec2Discovery ec2Discovery) {
        this.ec2Discovery = ec2Discovery;
    }

    public CloudWatchCollection getCloudWatchCollection() {
        return cloudWatchCollection;
    }

    public void setCloudWatchCollection(CloudWatchCollection cloudWatchCollection) {
        this.cloudWatchCollection = cloudWatchCollection;
    }
}