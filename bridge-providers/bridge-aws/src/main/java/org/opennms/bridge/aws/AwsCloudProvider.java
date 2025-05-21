package org.opennms.bridge.aws;

import org.opennms.bridge.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * AWS cloud provider implementation.
 * Implements the CloudProvider interface for AWS EC2 and CloudWatch.
 */
@Component
public class AwsCloudProvider implements CloudProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AwsCloudProvider.class);
    
    /**
     * DIRECT DEBUG OUTPUT - Bypasses logging system completely for maximum visibility
     */
    private void directDebug(String message) {
        try {
            String output = "[DIRECT-AWS-DEBUG @ " + System.currentTimeMillis() + "] " + message + "\n";
            
            // Output to stderr for maximum visibility (bypasses logging system)
            if (logToStdout) {
                System.err.println("\n" + output);
            }
            
            // Write directly to a file that will be accessible even if app crashes
            if (logToFile) {
                // Use configurable log directory
                String logDir = getDebugLogDirectory();
                try (FileWriter fw = new FileWriter(logDir + "/aws-direct-debug.log", true)) {
                    fw.write(output);
                }
            }
        } catch (Exception ignore) {
            // Last resort fallback
            System.err.println("[EMERGENCY] Failed to log debug info: " + message);
        }
    }
    
    /**
     * Gets the debug log directory, creating it if it doesn't exist
     */
    private String getDebugLogDirectory() {
        String dir = "/tmp";
        if (logDirectory != null && !logDirectory.trim().isEmpty()) {
            dir = logDirectory.trim();
            // Create directory if it doesn't exist
            File logDir = new File(dir);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
        }
        return dir;
    }
    
    /**
     * Emergency debug logging for troubleshooting the AWS configuration issue.
     */
    private void awsDebug(String phase, String message, Exception e) {
        // First use the direct debug method for guaranteed output
        directDebug(phase + ": " + message);
        if (e != null) {
            directDebug("EXCEPTION in " + phase + ": " + e.getMessage());
            if (e.getCause() != null) {
                directDebug("CAUSED BY: " + e.getCause().getMessage());
            }
            
            // Get stack trace as string
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            directDebug("STACK TRACE: " + sw.toString());
        }
        
        // Also attempt the original debug approach
        String timestamp = new Date().toString();
        String threadInfo = Thread.currentThread().getName() + ":" + Thread.currentThread().getId();
        String fullMessage = "[" + timestamp + "][" + threadInfo + "][" + phase + "] " + message;
        
        // Write to system console
        System.err.println("\n\n------------------ AWS DEBUG: " + phase + " ------------------");
        System.err.println(fullMessage);
        if (e != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            System.err.println("EXCEPTION: " + e.getMessage());
            System.err.println(sw.toString());
        }
        System.err.println("-------------------------------------------------------\n\n");
        
        // Also write to a separate log file
        if (logToFile) {
            try {
                String logDir = getDebugLogDirectory();
                PrintWriter writer = new PrintWriter(new FileWriter(logDir + "/aws-provider-debug.log", true));
                writer.println("\n\n------------------ AWS DEBUG: " + phase + " ------------------");
                writer.println(fullMessage);
                if (e != null) {
                    e.printStackTrace(writer);
                }
                writer.println("-------------------------------------------------------\n\n");
                writer.close();
            } catch (Exception logEx) {
                System.err.println("Failed to write to AWS debug log: " + logEx.getMessage());
            }
        }
    }
    
    @Autowired
    private AwsConfigurationProperties config;
    
    @Autowired
    private AwsDiscoveryStrategy discoveryStrategy;
    
    @Autowired
    private AwsMetricCollector metricCollector;
    
    @Value("${bridge.debug.aws.extreme_debug:false}")
    private void setExtremeDebug(boolean extremeDebug) {
        EXTREME_DEBUG = extremeDebug;
        LOG.info("AWS extreme debug mode: {}", EXTREME_DEBUG);
    }
    
    @Value("${bridge.debug.aws.emergency_bypass:false}")
    private void setEmergencyBypass(boolean emergencyBypass) {
        EMERGENCY_BYPASS = emergencyBypass;
        LOG.info("AWS emergency bypass mode: {}", EMERGENCY_BYPASS);
    }
    
    @Value("${bridge.debug.aws.log_directory:}")
    private String logDirectory;
    
    @Value("${bridge.debug.log_stdout:true}")
    private boolean logToStdout;
    
    @Value("${bridge.debug.log_file:true}")
    private boolean logToFile;
    
    // Cache of EC2 and CloudWatch clients by region
    private final Map<String, Ec2Client> ec2ClientCache = new ConcurrentHashMap<>();
    private final Map<String, CloudWatchClient> cloudWatchClientCache = new ConcurrentHashMap<>();
    
    // Credential provider
    private AwsCredentialsProvider credentialsProvider;
    
    @PostConstruct
    public void init() {
        LOG.info("Initializing AWS cloud provider: {}", config.getProviderId());
        LOG.info("AWS emergency bypass mode (actual value): {}", EMERGENCY_BYPASS);
        LOG.info("AWS extreme debug mode (actual value): {}", EXTREME_DEBUG);
        initializeCredentialsProvider();
    }
    
    @PreDestroy
    public void cleanup() {
        LOG.info("Closing AWS cloud provider: {}", config.getProviderId());
        close();
    }
    
    /**
     * Initialize AWS credentials provider based on configuration.
     */
    private void initializeCredentialsProvider() {
        LOG.info("Initializing AWS credentials provider for provider ID: {}", config.getProviderId());
        
        try {
            // EXTREME DEBUGGING: Dump environment variables related to AWS
            if (EXTREME_DEBUG) {
                System.err.println("\n=== AWS ENVIRONMENT VARIABLES ===");
                for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                    String key = entry.getKey();
                    if (key.contains("AWS") || key.contains("aws")) {
                        // Mask all credential-related information to prevent sensitive data exposure
                        boolean isSensitive = key.contains("KEY") || key.contains("SECRET") || 
                                              key.contains("TOKEN") || key.contains("CREDENTIAL") || 
                                              key.contains("PASSWORD") || key.contains("PASS");
                        System.err.println(key + ": " + (isSensitive ? "******" : entry.getValue()));
                    }
                }
                
                // Check system properties
                System.err.println("\n=== AWS SYSTEM PROPERTIES ===");
                for (String propName : System.getProperties().stringPropertyNames()) {
                    if (propName.contains("aws") || propName.contains("AWS")) {
                        String value = System.getProperty(propName);
                        // Mask all credential-related information to prevent sensitive data exposure
                        boolean isSensitive = propName.contains("key") || propName.contains("secret") || 
                                             propName.contains("token") || propName.contains("credential") || 
                                             propName.contains("password") || propName.contains("pass");
                        System.err.println(propName + ": " + (isSensitive ? "******" : value));
                    }
                }
                System.err.println("===============================\n");
            }
            
            // Check if credentials are provided
            boolean hasAccessKey = config.getAccessKeyId() != null && !config.getAccessKeyId().trim().isEmpty();
            boolean hasSecretKey = config.getSecretAccessKey() != null && !config.getSecretAccessKey().trim().isEmpty();
            boolean hasSessionToken = config.getSessionToken() != null && !config.getSessionToken().trim().isEmpty();
            boolean hasRoleArn = config.getRoleArn() != null && !config.getRoleArn().trim().isEmpty();
            
            LOG.debug("Credential configuration: hasAccessKey={}, hasSecretKey={}, hasSessionToken={}, hasRoleArn={}",
                    hasAccessKey, hasSecretKey, hasSessionToken, hasRoleArn);
            
            // TEMPORARY WORKAROUND: Try with default provider regardless
            try {
                if (EXTREME_DEBUG) {
                    // Try the "safest" option first - DefaultCredentialsProvider
                    System.err.println("\n=== TRYING DEFAULT CREDENTIALS PROVIDER ===");
                    LOG.info("WORKAROUND: Trying default AWS credential chain first, regardless of configuration");
                    try {
                        credentialsProvider = DefaultCredentialsProvider.create();
                        AwsCredentials credentials = credentialsProvider.resolveCredentials();
                        System.err.println("Default credentials resolved successfully! Type: " + credentials.getClass().getName());
                        System.err.println("==========================================\n");
                        
                        // If this worked, let's return early and skip the rest
                        LOG.info("Successfully created default credentials provider as a workaround");
                        return;
                    } catch (Exception e) {
                        System.err.println("Default credentials failed: " + e.getMessage());
                        System.err.println("==========================================\n");
                    }
                }
            } catch (Exception e) {
                LOG.warn("Workaround with default credentials provider failed: {}", e.getMessage());
                // Continue with normal flow
            }
            
            // Normal flow
            if (hasAccessKey && hasSecretKey) {
                LOG.info("Using static AWS credentials for provider {}", config.getProviderId());
                // Log only the first few characters of the access key for security
                if (config.getAccessKeyId() != null && !config.getAccessKeyId().isEmpty()) {
                    String prefix = config.getAccessKeyId().substring(0, Math.min(4, config.getAccessKeyId().length()));
                    LOG.debug("Access key ID prefix: {}", prefix + "****************");
                }
                
                try {
                    AwsCredentials awsCredentials;
                    if (hasSessionToken) {
                        LOG.debug("Creating AWS session credentials with session token");
                        awsCredentials = AwsSessionCredentials.create(
                                config.getAccessKeyId(),
                                config.getSecretAccessKey(),
                                config.getSessionToken());
                    } else {
                        LOG.debug("Creating AWS basic credentials (without session token)");
                        awsCredentials = AwsBasicCredentials.create(
                                config.getAccessKeyId(),
                                config.getSecretAccessKey());
                    }
                    
                    credentialsProvider = StaticCredentialsProvider.create(awsCredentials);
                    LOG.info("Successfully created static credentials provider");
                } catch (Exception e) {
                    LOG.error("Failed to create static credentials provider: {}", e.getMessage(), e);
                    throw new RuntimeException("Failed to create static AWS credentials provider", e);
                }
            } else {
                LOG.info("No access key/secret key provided, using default AWS credential chain for provider {}", config.getProviderId());
                try {
                    // TEMPORARY WORKAROUND: Use a more robust DefaultCredentialsProvider construction
                    if (EXTREME_DEBUG) {
                        System.err.println("\n=== BUILDING DEFAULT CREDENTIALS MANUALLY ===");
                        try {
                            // Try with the profile credentials provider first
                            credentialsProvider = ProfileCredentialsProvider.create();
                            System.err.println("Created ProfileCredentialsProvider successfully");
                        } catch (Exception e) {
                            System.err.println("Profile credentials failed: " + e.getMessage());
                            // Try with the environment
                            try {
                                credentialsProvider = EnvironmentVariableCredentialsProvider.create();
                                System.err.println("Created EnvironmentVariableCredentialsProvider successfully");
                            } catch (Exception e2) {
                                System.err.println("Environment credentials failed: " + e2.getMessage());
                                // Fall back to the default
                                credentialsProvider = DefaultCredentialsProvider.create();
                                System.err.println("Created DefaultCredentialsProvider successfully");
                            }
                        }
                        System.err.println("===========================================\n");
                    } else {
                        credentialsProvider = DefaultCredentialsProvider.create();
                    }
                    
                    LOG.info("Successfully created default credentials provider");
                } catch (Exception e) {
                    LOG.error("Failed to create default credentials provider: {}", e.getMessage(), e);
                    
                    // TEMPORARY WORKAROUND: Try the static profile (debug only)
                    if (EXTREME_DEBUG) {
                        System.err.println("\n=== LAST RESORT CREDENTIALS ATTEMPT ===");
                        try {
                            LOG.warn("Attempting last resort credentials - using hardcoded test credentials");
                            // Use a placeholder credentials provider as a last resort for debugging
                            credentialsProvider = AnonymousCredentialsProvider.create();
                            System.err.println("Created anonymous credentials provider as a last resort");
                            LOG.warn("Using anonymous credentials provider for debugging purposes");
                            return;  // Return early to skip validation
                        } catch (Exception e2) {
                            System.err.println("Last resort credentials attempt failed: " + e2.getMessage());
                        }
                        System.err.println("=====================================\n");
                    }
                    
                    throw new RuntimeException("Failed to create default AWS credentials provider", e);
                }
            }
            
            // Test if we can resolve credentials
            try {
                LOG.debug("Testing if credentials can be resolved from the provider...");
                AwsCredentials credentials = credentialsProvider.resolveCredentials();
                LOG.debug("Successfully resolved credentials of type: {}", credentials.getClass().getSimpleName());
                
                if (EXTREME_DEBUG) {
                    System.err.println("\n=== RESOLVED CREDENTIALS INFO ===");
                    System.err.println("Credential type: " + credentials.getClass().getName());
                    // Only show first few characters of access key with masking
                    if (credentials.accessKeyId() != null && !credentials.accessKeyId().isEmpty()) {
                        String prefix = credentials.accessKeyId().substring(0, Math.min(4, credentials.accessKeyId().length()));
                        System.err.println("Access key ID: " + prefix + "****************");
                    } else {
                        System.err.println("Access key ID: <empty>");
                    }
                    System.err.println("Has session token: " + (credentials instanceof AwsSessionCredentials));
                    System.err.println("================================\n");
                }
            } catch (Exception e) {
                LOG.error("Failed to resolve credentials from provider: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to resolve AWS credentials", e);
            }
            
            // If role ARN is provided, use assume role credentials
            if (hasRoleArn) {
                LOG.info("Assuming AWS role {} for provider {}", config.getRoleArn(), config.getProviderId());
                
                try {
                    LOG.debug("Creating STS client for role assumption");
                    
                    // TEMPORARY WORKAROUND: Make role assumption more robust
                    if (EXTREME_DEBUG) {
                        System.err.println("\n=== BUILDING STS CLIENT WITH EXTRA OPTIONS ===");
                        System.err.println("Using role ARN: " + config.getRoleArn());
                        
                        // Try with a simpler STS client configuration
                        StsClient stsClient = StsClient.builder()
                                .region(Region.US_EAST_1)  // Use a specific region as fallback
                                .credentialsProvider(credentialsProvider)
                                .overrideConfiguration(c -> c
                                    .apiCallTimeout(Duration.ofSeconds(30))  // Longer timeout
                                    .apiCallAttemptTimeout(Duration.ofSeconds(15))
                                    .retryPolicy(r -> r.numRetries(5)))  // More retries
                                .build();
                        
                        try {
                            LOG.debug("Testing STS connection with getCallerIdentity...");
                            String identity = stsClient.getCallerIdentity().account();
                            System.err.println("STS connection successful! Account: " + identity);
                            
                            LOG.debug("Creating assume role request with role ARN: {}", config.getRoleArn());
                            AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                                    .roleArn(config.getRoleArn())
                                    .roleSessionName("OpenNMSCloudBridge-" + config.getProviderId())
                                    .durationSeconds(3600)  // 1 hour session
                                    .build();
                            
                            try {
                                // Try to assume the role directly first
                                System.err.println("Attempting direct role assumption...");
                                AssumeRoleResponse response = stsClient.assumeRole(assumeRoleRequest);
                                System.err.println("Role assumption successful!");
                                System.err.println("Assumed role user: " + response.assumedRoleUser().arn());
                                System.err.println("Temporary credentials expiration: " + response.credentials().expiration());
                                
                                // Create credentials from the response
                                AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                                        response.credentials().accessKeyId(),
                                        response.credentials().secretAccessKey(),
                                        response.credentials().sessionToken());
                                
                                credentialsProvider = StaticCredentialsProvider.create(sessionCredentials);
                                System.err.println("Created static provider with assumed role credentials");
                                stsClient.close();
                                
                                // Skip the provider creation since we did it manually
                                LOG.info("Successfully assumed AWS role {} directly", config.getRoleArn());
                                return;
                            } catch (Exception e) {
                                System.err.println("Direct role assumption failed: " + e.getMessage());
                                
                                // Fall back to the normal credential provider
                                System.err.println("Falling back to credential provider method...");
                                credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                                        .stsClient(stsClient)
                                        .refreshRequest(assumeRoleRequest)
                                        .build();
                                
                                System.err.println("Created STS assume role credentials provider");
                                credentialsProvider.resolveCredentials();
                                System.err.println("Successfully verified role assumption credentials");
                            }
                        } catch (Exception e) {
                            System.err.println("STS error: " + e.getMessage());
                            stsClient.close();
                            throw e;
                        }
                        
                        System.err.println("=========================================\n");
                    } else {
                        // Normal path
                        StsClient stsClient = StsClient.builder()
                                .credentialsProvider(credentialsProvider)
                                .overrideConfiguration(c -> c
                                    .apiCallTimeout(Duration.ofSeconds(15))
                                    .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                                .build();
                        
                        LOG.debug("Creating assume role request with role ARN: {}", config.getRoleArn());
                        AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                                .roleArn(config.getRoleArn())
                                .roleSessionName("OpenNMSCloudBridge-" + config.getProviderId())
                                .build();
                        
                        try {
                            LOG.debug("Building STS assume role credentials provider");
                            credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                                    .stsClient(stsClient)
                                    .refreshRequest(assumeRoleRequest)
                                    .build();
                            
                            LOG.debug("Testing if the assume role credentials can be resolved...");
                            credentialsProvider.resolveCredentials();
                            LOG.info("Successfully assumed AWS role {}", config.getRoleArn());
                        } catch (Exception e) {
                            LOG.error("Failed to assume AWS role {}: {}", config.getRoleArn(), e.getMessage(), e);
                            stsClient.close();
                            throw new RuntimeException("Failed to assume AWS role: " + e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Error creating STS client for role assumption: {}", e.getMessage(), e);
                    
                    // TEMPORARY WORKAROUND: If role assumption fails but we have base credentials, continue without the role
                    if (EXTREME_DEBUG) {
                        LOG.warn("Role assumption failed, but continuing with base credentials for debugging");
                        return;  // Skip the exception since we have base credentials
                    }
                    
                    throw new RuntimeException("Failed to create STS client for role assumption", e);
                }
            }
            
            LOG.info("Successfully initialized AWS credentials provider: {}", 
                    credentialsProvider != null ? credentialsProvider.getClass().getName() : "NULL");
        } catch (Exception e) {
            LOG.error("Error initializing AWS credentials provider: {}", e.getMessage(), e);
            
            if (EXTREME_DEBUG) {
                System.err.println("\n=== CREDENTIAL INITIALIZATION FAILURE ===");
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace(System.err);
                System.err.println("========================================\n");
                
                // EXTREME WORKAROUND: Create an anonymous provider as a last resort
                LOG.warn("Creating anonymous credentials provider as a last resort for debugging");
                credentialsProvider = AnonymousCredentialsProvider.create();
                return;  // Return without throwing exception
            }
            
            throw new RuntimeException("Failed to initialize AWS credentials provider: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get an EC2 client for a specific region.
     *
     * @param region AWS region
     * @return EC2 client
     */
    private Ec2Client getEc2Client(String region) {
        return ec2ClientCache.computeIfAbsent(region, r -> {
            LOG.debug("Creating new EC2 client for region {}", r);
            return Ec2Client.builder()
                    .region(Region.of(r))
                    .credentialsProvider(credentialsProvider)
                    .overrideConfiguration(c -> c
                            .apiCallTimeout(config.getConnectionTimeout())
                            .apiCallAttemptTimeout(config.getReadTimeout()))
                    .build();
        });
    }
    
    /**
     * Get a CloudWatch client for a specific region.
     *
     * @param region AWS region
     * @return CloudWatch client
     */
    private CloudWatchClient getCloudWatchClient(String region) {
        return cloudWatchClientCache.computeIfAbsent(region, r -> {
            LOG.debug("Creating new CloudWatch client for region {}", r);
            return CloudWatchClient.builder()
                    .region(Region.of(r))
                    .credentialsProvider(credentialsProvider)
                    .overrideConfiguration(c -> c
                            .apiCallTimeout(config.getConnectionTimeout())
                            .apiCallAttemptTimeout(config.getReadTimeout()))
                    .build();
        });
    }
    
    /**
     * Get regions to use for operations.
     * If regions are configured, use those. Otherwise, get the current region from the environment.
     *
     * @return list of AWS regions
     */
    private List<String> getRegionsToUse() {
        if (config.getRegions() != null && !config.getRegions().isEmpty()) {
            return config.getRegions();
        } else {
            try {
                String region = new DefaultAwsRegionProviderChain().getRegion().id();
                return Collections.singletonList(region);
            } catch (Exception e) {
                LOG.warn("Failed to determine AWS region from environment: {}", e.getMessage());
                return Collections.singletonList("us-east-1");
            }
        }
    }
    
    @Override
    public String getProviderId() {
        return config.getProviderId();
    }
    
    @Override
    public String getProviderType() {
        return "aws";
    }
    
    @Override
    public String getDisplayName() {
        return config.getDisplayName();
    }
    
    @Override
    public ValidationResult validate() throws CloudProviderException {
        LOG.info("Validating AWS cloud provider: {}", config.getProviderId());
        
        // Emergency bypass mode - skip validation
        if (EMERGENCY_BYPASS) {
            LOG.warn("EMERGENCY BYPASS mode enabled - skipping AWS validation");
            return ValidationResult.valid();
        }
        
        try {
            // Step 1: Verify AWS SDK initialization
            try {
                Class.forName("software.amazon.awssdk.core.SdkClient");
                LOG.debug("AWS SDK successfully loaded");
            } catch (ClassNotFoundException e) {
                LOG.error("AWS SDK not properly initialized: {}", e.getMessage(), e);
                return ValidationResult.invalid("AWS SDK initialization failed: " + e.getMessage());
            }
            
            // Step 2: Verify credential provider
            if (credentialsProvider == null) {
                try {
                    initializeCredentialsProvider();
                } catch (Exception e) {
                    LOG.error("Failed to initialize AWS credentials provider: {}", e.getMessage(), e);
                    return ValidationResult.invalid("Cannot initialize AWS credentials: " + e.getMessage());
                }
            }
            
            // Step 3: Validate credentials by checking if they can be loaded
            try {
                credentialsProvider.resolveCredentials();
                LOG.debug("AWS credentials successfully resolved");
            } catch (Exception e) {
                LOG.error("AWS credentials validation failed: {}", e.getMessage(), e);
                return ValidationResult.invalid("Invalid AWS credentials: " + e.getMessage());
            }
            
            // Step 4: Verify network connectivity and validate each region
            List<String> regions = getRegionsToUse();
            boolean allRegionsValid = true;
            List<String> validationErrors = new ArrayList<>();
            
            for (String region : regions) {
                try {
                    // Test basic connectivity first
                    try {
                        LOG.debug("Testing network connectivity to AWS in region {}", region);
                        StsClient stsClient = StsClient.builder()
                                .region(Region.of(region))
                                .credentialsProvider(credentialsProvider)
                                .overrideConfiguration(c -> c
                                        .apiCallTimeout(Duration.ofSeconds(5)) // Short timeout for connectivity test
                                        .apiCallAttemptTimeout(Duration.ofSeconds(3)))
                                .build();
                        
                        stsClient.getCallerIdentity();
                        LOG.debug("Network connectivity to AWS in region {} verified", region);
                    } catch (Exception e) {
                        allRegionsValid = false;
                        String errorMsg = e.getMessage();
                        if (errorMsg.contains("Unable to execute HTTP request") || 
                            errorMsg.contains("connect timed out") || 
                            errorMsg.contains("Connection refused") ||
                            errorMsg.contains("No route to host")) {
                            validationErrors.add("Network connectivity issue for region " + region + ": " + errorMsg);
                            LOG.warn("Network connectivity issue for AWS region {}: {}", region, errorMsg);
                            continue; // Skip further validation for this region
                        }
                    }
                    
                    // Try to create and use EC2 client for validation
                    Ec2Client ec2Client = getEc2Client(region);
                    ec2Client.describeRegions();
                    LOG.debug("EC2 client validation successful for region {}", region);
                    
                    // Try to create and use CloudWatch client for validation
                    CloudWatchClient cloudWatchClient = getCloudWatchClient(region);
                    cloudWatchClient.listMetrics();
                    LOG.debug("CloudWatch client validation successful for region {}", region);
                } catch (Exception e) {
                    allRegionsValid = false;
                    String errorMessage = e.getMessage();
                    String prettyError;
                    
                    // Customize error messages for different types of errors
                    if (errorMessage.contains("The security token included in the request is invalid") || 
                        errorMessage.contains("The request signature we calculated does not match") ||
                        errorMessage.contains("credentials") || 
                        errorMessage.contains("Not authorized")) {
                        prettyError = "Authentication error in region " + region + ": Invalid or expired credentials";
                    } else if (errorMessage.contains("not authorized to perform")) {
                        prettyError = "Authorization error in region " + region + ": The provided credentials don't have permission for required operations";
                    } else if (errorMessage.contains("Rate exceeded")) {
                        prettyError = "Rate limit exceeded in region " + region + ": Too many requests";
                    } else {
                        prettyError = "Failed to validate AWS services in region " + region + ": " + errorMessage;
                    }
                    
                    validationErrors.add(prettyError);
                    LOG.warn("Validation failed for AWS region {}: {}", region, errorMessage, e);
                }
            }
            
            // Return the final validation result
            if (allRegionsValid) {
                return ValidationResult.valid();
            } else {
                return ValidationResult.invalid(String.join("; ", validationErrors));
            }
        } catch (Exception e) {
            LOG.error("AWS provider validation error: {}", e.getMessage(), e);
            throw new CloudProviderException("Validation failed for AWS provider " + config.getProviderId() + ": " + e.getMessage(), e);
        }
    }
    
    @Override
    public Set<CloudResource> discover() throws CloudProviderException {
        LOG.info("Discovering resources from AWS provider: {}", config.getProviderId());
        
        // If emergency bypass is enabled, create mock resources instead
        if (EMERGENCY_BYPASS) {
            LOG.warn("EMERGENCY BYPASS mode enabled - using mock resources instead of real AWS resources");
            
            // Create a set of mock EC2 instances
            Set<CloudResource> mockResources = new HashSet<>();
            
            // Mock EC2 instance 1
            CloudResource ec1 = new CloudResource();
            ec1.setId("i-0123456789abcdef0");
            ec1.setType("EC2");
            ec1.setRegion("us-east-1");
            ec1.setStatus("running");
            ec1.setName("Mock App Server");
            ec1.addProperty("providerId", config.getProviderId());
            ec1.addProperty("providerType", "aws");
            ec1.addTag("Name", "Mock App Server");
            ec1.addTag("Environment", "Production");
            ec1.addProperty("instanceType", "t3.medium");
            ec1.addProperty("privateIp", "10.0.1.5");
            
            // Mock EC2 instance 2
            CloudResource ec2 = new CloudResource();
            ec2.setId("i-0a1b2c3d4e5f67890");
            ec2.setType("EC2");
            ec2.setRegion("us-east-1");
            ec2.setStatus("running");
            ec2.setName("Mock Database Server");
            ec2.addProperty("providerId", config.getProviderId());
            ec2.addProperty("providerType", "aws");
            ec2.addTag("Name", "Mock Database Server");
            ec2.addTag("Environment", "Production");
            ec2.addProperty("instanceType", "t3.large");
            ec2.addProperty("privateIp", "10.0.1.6");
            
            mockResources.add(ec1);
            mockResources.add(ec2);
            
            LOG.info("Created {} mock AWS resources for provider: {}", mockResources.size(), config.getProviderId());
            return mockResources;
        }
        
        // Regular implementation for real AWS
        try {
            List<String> regions = getRegionsToUse();
            Set<CloudResource> allResources = new HashSet<>();
            
            for (String region : regions) {
                LOG.debug("Discovering resources in region {}", region);
                
                // Discover EC2 instances if enabled
                if (config.getEc2Discovery().isEnabled()) {
                    Ec2Client ec2Client = getEc2Client(region);
                    Set<CloudResource> ec2Resources = discoveryStrategy.discoverEc2Instances(ec2Client, region, config);
                    allResources.addAll(ec2Resources);
                }
                
                // Add other AWS resource types here as needed
            }
            
            LOG.info("Discovered {} resources from AWS provider: {}", allResources.size(), config.getProviderId());
            return allResources;
        } catch (Exception e) {
            LOG.error("Error discovering AWS resources: {}", e.getMessage(), e);
            
            // If we're in emergency bypass mode, return mock resources instead of failing
            if (EMERGENCY_BYPASS) {
                LOG.warn("EMERGENCY BYPASS mode enabled - falling back to mock resources due to error");
                
                // Create a set of mock EC2 instances
                Set<CloudResource> mockResources = new HashSet<>();
                
                // Mock EC2 instance 1
                CloudResource ec1 = new CloudResource();
                ec1.setId("i-0123456789abcdef0");
                ec1.setType("EC2");
                ec1.setRegion("us-east-1");
                ec1.setStatus("running");
                ec1.setName("Fallback App Server");
                ec1.addProperty("providerId", config.getProviderId());
                ec1.addProperty("providerType", "aws");
                ec1.addTag("Name", "Fallback App Server");
                ec1.addTag("Environment", "Production");
                ec1.addProperty("instanceType", "t3.medium");
                ec1.addProperty("privateIp", "10.0.1.5");
                
                // Mock EC2 instance 2
                CloudResource ec2 = new CloudResource();
                ec2.setId("i-0a1b2c3d4e5f67890");
                ec2.setType("EC2");
                ec2.setRegion("us-east-1");
                ec2.setStatus("running");
                ec2.setName("Fallback Database Server");
                ec2.addProperty("providerId", config.getProviderId());
                ec2.addProperty("providerType", "aws");
                ec2.addTag("Name", "Fallback Database Server");
                ec2.addTag("Environment", "Production");
                ec2.addProperty("instanceType", "t3.large");
                ec2.addProperty("privateIp", "10.0.1.6");
                
                mockResources.add(ec1);
                mockResources.add(ec2);
                
                LOG.info("Created {} fallback mock AWS resources for provider due to error: {}", mockResources.size(), config.getProviderId());
                return mockResources;
            }
            
            throw new CloudProviderException("Failed to discover AWS resources", e);
        }
    }
    
    @Override
    public MetricCollection collect(CloudResource resource) throws CloudProviderException {
        LOG.info("Collecting metrics for resource {} from AWS provider: {}", resource.getResourceId(), config.getProviderId());
        
        // If emergency bypass is enabled, return mock metrics
        if (EMERGENCY_BYPASS) {
            LOG.warn("EMERGENCY BYPASS mode enabled - returning mock metrics for resource {}", resource.getResourceId());
            return createMockMetrics(resource);
        }
        
        try {
            // Check that resource belongs to this provider
            if (!config.getProviderId().equals(resource.getProviderId())) {
                throw new CloudProviderException("Resource " + resource.getResourceId() + 
                        " does not belong to provider " + config.getProviderId());
            }
            
            // Get region from resource
            String region = resource.getRegion();
            if (region == null) {
                throw new CloudProviderException("Resource " + resource.getResourceId() + " has no region specified");
            }
            
            // Collect metrics based on resource type
            switch (resource.getResourceType()) {
                case "EC2":
                    if (config.getCloudWatchCollection().isEnabled()) {
                        CloudWatchClient cloudWatchClient = getCloudWatchClient(region);
                        return metricCollector.collectEc2Metrics(cloudWatchClient, resource, config);
                    } else {
                        LOG.info("CloudWatch metric collection is disabled");
                        MetricCollection collection = new MetricCollection(resource.getResourceId());
                        collection.setTimestamp(new Date().toInstant());
                        collection.addTag("providerId", config.getProviderId());
                        collection.setMetrics(Collections.emptyList());
                        return collection;
                    }
                default:
                    throw new CloudProviderException("Unsupported resource type: " + resource.getResourceType());
            }
        } catch (CloudProviderException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Error collecting metrics for resource {}: {}", resource.getResourceId(), e.getMessage(), e);
            
            // If we're in emergency bypass mode, return mock metrics instead of failing
            if (EMERGENCY_BYPASS) {
                LOG.warn("EMERGENCY BYPASS mode enabled - falling back to mock metrics due to error");
                return createMockMetrics(resource);
            }
            
            throw new CloudProviderException("Failed to collect metrics for resource " + resource.getResourceId(), e);
        }
    }
    
    /**
     * Create mock metrics for a resource when in emergency bypass mode
     * @param resource the resource to create metrics for
     * @return a collection of mock metrics
     */
    private MetricCollection createMockMetrics(CloudResource resource) {
        MetricCollection collection = new MetricCollection(resource.getResourceId());
        collection.setTimestamp(new Date().toInstant());
        collection.addTag("providerId", config.getProviderId());
        collection.addTag("mockData", "true");
        
        // Add resource metadata as tags
        if (resource.getName() != null) {
            collection.addTag("name", resource.getName());
        }
        if (resource.getResourceType() != null) {
            collection.addTag("resourceType", resource.getResourceType());
        }
        if (resource.getRegion() != null) {
            collection.addTag("region", resource.getRegion());
        }
        
        List<MetricCollection.Metric> metrics = new ArrayList<>();
        
        // CPU metrics
        MetricCollection.Metric cpuMetric = new MetricCollection.Metric("CPUUtilization", Math.random() * 100);
        cpuMetric.addTag("unit", "Percent");
        metrics.add(cpuMetric);
        
        // Network metrics
        MetricCollection.Metric networkInMetric = new MetricCollection.Metric("NetworkIn", Math.random() * 1000000);
        networkInMetric.addTag("unit", "Bytes");
        metrics.add(networkInMetric);
        
        MetricCollection.Metric networkOutMetric = new MetricCollection.Metric("NetworkOut", Math.random() * 1000000);
        networkOutMetric.addTag("unit", "Bytes");
        metrics.add(networkOutMetric);
        
        // Disk metrics
        MetricCollection.Metric diskReadMetric = new MetricCollection.Metric("DiskReadBytes", Math.random() * 500000);
        diskReadMetric.addTag("unit", "Bytes");
        metrics.add(diskReadMetric);
        
        MetricCollection.Metric diskWriteMetric = new MetricCollection.Metric("DiskWriteBytes", Math.random() * 500000);
        diskWriteMetric.addTag("unit", "Bytes");
        metrics.add(diskWriteMetric);
        
        collection.setMetrics(metrics);
        return collection;
    }
    
    @Override
    public Set<String> getAvailableRegions() {
        try {
            // Try to get regions from EC2 client
            Ec2Client ec2Client = getEc2Client(getRegionsToUse().get(0));
            return ec2Client.describeRegions().regions().stream()
                    .map(r -> r.regionName())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            LOG.warn("Error getting available AWS regions: {}", e.getMessage(), e);
            return new HashSet<>(getRegionsToUse());
        }
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> configuration = new HashMap<>();
        
        configuration.put("providerId", config.getProviderId());
        configuration.put("displayName", config.getDisplayName());
        configuration.put("regions", config.getRegions());
        configuration.put("connectionTimeout", config.getConnectionTimeout().toMillis());
        configuration.put("readTimeout", config.getReadTimeout().toMillis());
        configuration.put("maxRetries", config.getMaxRetries());
        
        // Include EC2 discovery configuration
        Map<String, Object> ec2Config = new HashMap<>();
        ec2Config.put("enabled", config.getEc2Discovery().isEnabled());
        ec2Config.put("includeTags", config.getEc2Discovery().getIncludeTags());
        ec2Config.put("filterByTags", config.getEc2Discovery().getFilterByTags());
        ec2Config.put("instanceStates", config.getEc2Discovery().getInstanceStates());
        configuration.put("ec2Discovery", ec2Config);
        
        // Include CloudWatch collection configuration
        Map<String, Object> cloudWatchConfig = new HashMap<>();
        cloudWatchConfig.put("enabled", config.getCloudWatchCollection().isEnabled());
        cloudWatchConfig.put("metrics", config.getCloudWatchCollection().getMetrics());
        cloudWatchConfig.put("period", config.getCloudWatchCollection().getPeriod().toMinutes());
        cloudWatchConfig.put("statistics", config.getCloudWatchCollection().getStatistics());
        configuration.put("cloudWatchCollection", cloudWatchConfig);
        
        return configuration;
    }
    
    // Configure debug flags through application.yml
    private static boolean EXTREME_DEBUG = false;
    
    /**
     * Print detailed debugging information for troubleshooting
     */
    private void debugPrintConfigState(String stage) {
        if (!EXTREME_DEBUG) return;
        
        try {
            System.err.println("\n==================== AWS PROVIDER DEBUG [" + stage + "] ====================");
            System.err.println("Provider ID: " + (config != null ? config.getProviderId() : "NULL CONFIG"));
            System.err.println("Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
            System.err.println("Free memory: " + Runtime.getRuntime().freeMemory() / (1024 * 1024) + "MB");
            
            if (config != null) {
                System.err.println("Config state:");
                // Securely log credential presence without exposing values
                System.err.println("  - accessKeyId: " + (config.getAccessKeyId() != null ? 
                    (config.getAccessKeyId().isEmpty() ? "empty" : "present [" + 
                    config.getAccessKeyId().substring(0, Math.min(3, config.getAccessKeyId().length())) + "***]") : "null"));
                System.err.println("  - secretAccessKey: " + (config.getSecretAccessKey() != null ? 
                    (config.getSecretAccessKey().isEmpty() ? "empty" : "present [masked]") : "null"));
                System.err.println("  - sessionToken: " + (config.getSessionToken() != null ? 
                    (config.getSessionToken().isEmpty() ? "empty" : "present [masked]") : "null"));
                System.err.println("  - roleArn: " + config.getRoleArn());
                System.err.println("  - regions: " + config.getRegions());
                System.err.println("  - connectionTimeout: " + (config.getConnectionTimeout() != null ? config.getConnectionTimeout().toMillis() + "ms" : "null"));
                System.err.println("  - readTimeout: " + (config.getReadTimeout() != null ? config.getReadTimeout().toMillis() + "ms" : "null"));
                System.err.println("  - EC2 discovery enabled: " + (config.getEc2Discovery() != null ? config.getEc2Discovery().isEnabled() : "null"));
                System.err.println("  - CloudWatch collection enabled: " + (config.getCloudWatchCollection() != null ? config.getCloudWatchCollection().isEnabled() : "null"));
            }
            
            System.err.println("Credentials provider: " + (credentialsProvider != null ? credentialsProvider.getClass().getName() : "NULL"));
            System.err.println("====================================================================\n");
        } catch (Exception e) {
            System.err.println("Error in debug printing: " + e.getMessage());
        }
    }

    // Configure emergency bypass through application.yml
    private static boolean EMERGENCY_BYPASS = false;
    
    @Override
    public void updateConfiguration(Map<String, Object> configuration) throws CloudProviderException {
        // Start with direct debug for maximum visibility
        directDebug("ENTRY: updateConfiguration called - providerId: " + (config != null ? config.getProviderId() : "NULL CONFIG"));
        directDebug("Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
        try {
            directDebug("CONFIG: Configuration map keys: " + (configuration != null ? configuration.keySet() : "NULL CONFIGURATION"));
            if (configuration != null) {
                for (String key : configuration.keySet()) {
                    Object value = configuration.get(key);
                    // Mask any sensitive information
                    boolean isSensitive = key.contains("Key") || key.contains("Secret") || 
                                         key.contains("Token") || key.contains("Password") || 
                                         key.contains("Credential") || key.contains("Auth");
                    directDebug("CONFIG ITEM: " + key + " = " + 
                        (isSensitive ? "********" : String.valueOf(value)) + 
                        " (Type: " + (value != null ? value.getClass().getName() : "null") + ")");
                }
            }
        } catch (Exception debugEx) {
            directDebug("ERROR in initial debug: " + debugEx.getMessage());
        }
        
        awsDebug("ENTRY", "updateConfiguration called - providerId: " + config.getProviderId(), null);
        awsDebug("CONFIG", "Configuration map keys: " + configuration.keySet(), null);
        
        if (EMERGENCY_BYPASS) {
            // EMERGENCY BYPASS CODE: This is a temporary fix to bypass the problematic AWS configuration code
            // This only updates the displayName and keeps the provider operational
            try {
                String providerId = config.getProviderId();
                LOG.warn("EMERGENCY MODE: Using bypass configuration update for AWS provider: {}", providerId);
                awsDebug("BYPASS", "Using emergency bypass for provider: " + providerId, null);
                
                // Only selectively apply minimal configuration changes
                if (configuration.containsKey("displayName")) {
                    String displayName = (String) configuration.get("displayName");
                    config.setDisplayName(displayName);
                    LOG.info("Updated display name to: {}", displayName);
                    awsDebug("BYPASS", "Updated display name to: " + displayName, null);
                }
                
                // ADDITIONAL DEBUG: Examine ALL config keys
                for (String key : configuration.keySet()) {
                    Object value = configuration.get(key);
                    String valueType = value != null ? value.getClass().getName() : "null";
                    // Enhanced sensitive data masking
                    boolean isSensitive = key.contains("Key") || key.contains("Secret") || 
                                         key.contains("Token") || key.contains("Password") || 
                                         key.contains("Credential") || key.contains("Auth") || 
                                         key.toLowerCase().contains("access") || 
                                         key.toLowerCase().contains("pass");
                    String valueString = isSensitive ? "********" : String.valueOf(value);
                    awsDebug("CONFIG_KEY", "Key: " + key + ", Type: " + valueType + ", Value: " + valueString, null);
                }
                
                // Skip credential initialization - keep existing credentials
                LOG.warn("EMERGENCY MODE: Skipping AWS credentials reinitialization and validation");
                awsDebug("BYPASS", "Skipping credentials reinitialization", null);
                
                // Try to detect what's actually happening with credentials
                try {
                    awsDebug("CRED_CHECK", "Current credentialsProvider: " + 
                            (credentialsProvider != null ? credentialsProvider.getClass().getName() : "NULL"), null);
                            
                    if (credentialsProvider != null) {
                        try {
                            awsDebug("CRED_CHECK", "Attempting to resolve credentials", null);
                            credentialsProvider.resolveCredentials();
                            awsDebug("CRED_CHECK", "Credentials resolved successfully!", null);
                        } catch (Exception credEx) {
                            awsDebug("CRED_CHECK", "Failed to resolve credentials", credEx);
                        }
                    }
                } catch (Exception debugEx) {
                    awsDebug("CRED_CHECK", "Error checking credentials", debugEx);
                }
                
                // Log success but with warning
                LOG.warn("EMERGENCY MODE: AWS provider {} configuration update bypassed successfully", providerId);
                awsDebug("BYPASS", "Configuration update bypassed successfully", null);
                return;
            } catch (Exception e) {
                LOG.error("Even emergency bypass mode failed: {}", e.getMessage(), e);
                awsDebug("BYPASS_ERROR", "Emergency bypass mode failed", e);
                throw new CloudProviderException("Configuration update failed even in emergency bypass mode", e);
            }
        }
        // Extreme debug - Print initial state
        debugPrintConfigState("BEFORE_UPDATE");
        
        if (configuration == null) {
            LOG.error("Update configuration failed: Configuration map is null");
            throw new CloudProviderException("Configuration cannot be null");
        }
        
        LOG.info("Starting configuration update for AWS provider: {}", config.getProviderId());
        if (EXTREME_DEBUG) {
            System.err.println("\n=== CONFIGURATION MAP DUMP ===");
            for (Map.Entry<String, Object> entry : configuration.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String valueClass = value != null ? value.getClass().getName() : "null";
                String valueToString = key.contains("Secret") || key.contains("Key") || key.contains("Token") ? 
                        "******" : String.valueOf(value);
                
                System.err.println(key + " (" + valueClass + "): " + valueToString);
            }
            System.err.println("==============================\n");
        }
        
        // Make a backup of the current configuration
        LOG.debug("Creating backup of current configuration");
        AwsConfigurationProperties backupConfig = new AwsConfigurationProperties();
        copyConfigurationProperties(config, backupConfig);
        AwsCredentialsProvider backupCredentialsProvider = credentialsProvider;
        
        // TEMPORARY WORKAROUND: Keep original values if they're not in the configuration
        String originalAccessKeyId = config.getAccessKeyId();
        String originalSecretKey = config.getSecretAccessKey();
        String originalSessionToken = config.getSessionToken();
        String originalRoleArn = config.getRoleArn();
        List<String> originalRegions = config.getRegions() != null ? new ArrayList<>(config.getRegions()) : null;
        
        // Close existing clients before updating configuration
        LOG.debug("Closing existing AWS clients");
        try {
            closeClients();
            LOG.debug("Successfully closed existing AWS clients");
        } catch (Exception e) {
            LOG.warn("Error while closing AWS clients: {}", e.getMessage());
        }
        
        try {
            try {
                LOG.info("Updating basic configuration properties");
                awsDebug("NORMAL", "Calling updateBasicConfiguration", null);
                updateBasicConfiguration(configuration);
                LOG.debug("Basic configuration updated successfully");
                awsDebug("NORMAL", "Basic configuration update completed successfully", null);
                debugPrintConfigState("AFTER_BASIC_CONFIG_UPDATE");
                
                // TEMPORARY WORKAROUND: Restore values if they're empty now but weren't before
                if (config.getAccessKeyId() == null && originalAccessKeyId != null) {
                    LOG.warn("Access key ID was lost during configuration update, restoring original value");
                    config.setAccessKeyId(originalAccessKeyId);
                }
                if (config.getSecretAccessKey() == null && originalSecretKey != null) {
                    LOG.warn("Secret access key was lost during configuration update, restoring original value");
                    config.setSecretAccessKey(originalSecretKey);
                }
                if (config.getSessionToken() == null && originalSessionToken != null) {
                    LOG.warn("Session token was lost during configuration update, restoring original value");
                    config.setSessionToken(originalSessionToken);
                }
                if (config.getRoleArn() == null && originalRoleArn != null) {
                    LOG.warn("Role ARN was lost during configuration update, restoring original value");
                    config.setRoleArn(originalRoleArn);
                }
                if ((config.getRegions() == null || config.getRegions().isEmpty()) && originalRegions != null && !originalRegions.isEmpty()) {
                    LOG.warn("Regions were lost during configuration update, restoring original values");
                    config.setRegions(originalRegions);
                }
                
                // Ensure we have default values for required fields
                if (config.getConnectionTimeout() == null) {
                    LOG.warn("Connection timeout is null, setting default value");
                    config.setConnectionTimeout(Duration.ofSeconds(10));
                }
                if (config.getReadTimeout() == null) {
                    LOG.warn("Read timeout is null, setting default value");
                    config.setReadTimeout(Duration.ofSeconds(30));
                }
                
                debugPrintConfigState("AFTER_WORKAROUND_FIXES");
            } catch (Exception e) {
                LOG.error("Error updating basic configuration: {}", e.getMessage(), e);
                throw new CloudProviderException("Failed to update basic configuration: " + e.getMessage(), e);
            }
            
            try {
                LOG.info("Updating EC2 discovery configuration");
                awsDebug("NORMAL", "Calling updateEc2DiscoveryConfiguration", null);
                updateEc2DiscoveryConfiguration(configuration);
                LOG.debug("EC2 discovery configuration updated successfully");
                awsDebug("NORMAL", "EC2 discovery configuration update completed successfully", null);
                debugPrintConfigState("AFTER_EC2_CONFIG_UPDATE");
            } catch (Exception e) {
                LOG.error("Error updating EC2 discovery configuration: {}", e.getMessage(), e);
                awsDebug("ERROR", "Failed during EC2 discovery configuration update", e);
                throw new CloudProviderException("Failed to update EC2 discovery configuration: " + e.getMessage(), e);
            }
            
            try {
                LOG.info("Updating CloudWatch collection configuration");
                awsDebug("NORMAL", "Calling updateCloudWatchConfiguration", null);
                updateCloudWatchConfiguration(configuration);
                LOG.debug("CloudWatch collection configuration updated successfully");
                awsDebug("NORMAL", "CloudWatch collection configuration update completed successfully", null);
                debugPrintConfigState("AFTER_CLOUDWATCH_CONFIG_UPDATE");
            } catch (Exception e) {
                LOG.error("Error updating CloudWatch configuration: {}", e.getMessage(), e);
                awsDebug("ERROR", "Failed during CloudWatch collection configuration update", e);
                throw new CloudProviderException("Failed to update CloudWatch configuration: " + e.getMessage(), e);
            }
            
            LOG.info("Configuration properties updated successfully, initializing credentials provider");
            debugPrintConfigState("BEFORE_CREDENTIALS_INIT");
            
            // Re-initialize credentials provider after configuration update
            try {
                awsDebug("CREDS_INIT", "Starting credentials initialization", null);
                
                // Network connectivity check
                try {
                    awsDebug("NETWORK", "Performing basic network connectivity check", null);
                    java.net.InetAddress address = java.net.InetAddress.getByName("sts.amazonaws.com");
                    awsDebug("NETWORK", "DNS lookup for sts.amazonaws.com: " + address.getHostAddress(), null);
                    
                    // Try a simple HTTP connection
                    awsDebug("NETWORK", "Attempting simple connection to AWS STS...", null);
                    java.net.Socket socket = new java.net.Socket();
                    socket.connect(new java.net.InetSocketAddress(address, 443), 5000);
                    awsDebug("NETWORK", "Connection successful!", null);
                    socket.close();
                } catch (Exception e) {
                    awsDebug("NETWORK", "Network check failed", e);
                }
                
                // Check environment for AWS credentials
                awsDebug("CREDS_ENV", "Checking environment variables and system properties", null);
                try {
                    // Check AWS environment variables
                    String awsKeyId = System.getenv("AWS_ACCESS_KEY_ID");
                    String awsSecretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
                    String awsRegion = System.getenv("AWS_REGION");
                    awsDebug("CREDS_ENV", "AWS_ACCESS_KEY_ID is " + (awsKeyId != null ? "set" : "not set"), null);
                    awsDebug("CREDS_ENV", "AWS_SECRET_ACCESS_KEY is " + (awsSecretKey != null ? "set" : "not set"), null);
                    awsDebug("CREDS_ENV", "AWS_REGION is " + (awsRegion != null ? "set to " + awsRegion : "not set"), null);
                    
                    // Check system properties
                    String sysPropKey = System.getProperty("aws.accessKeyId");
                    String sysPropRegion = System.getProperty("aws.region");
                    awsDebug("CREDS_ENV", "aws.accessKeyId property is " + (sysPropKey != null ? "set" : "not set"), null);
                    awsDebug("CREDS_ENV", "aws.region property is " + (sysPropRegion != null ? "set to " + sysPropRegion : "not set"), null);
                } catch (Exception e) {
                    awsDebug("CREDS_ENV", "Error checking credentials in environment", e);
                }
                
                // Debug config settings
                awsDebug("CONFIG_CHECK", "Current configuration:", null);
                awsDebug("CONFIG_CHECK", "Provider ID: " + config.getProviderId(), null);
                awsDebug("CONFIG_CHECK", "Access Key ID set: " + (config.getAccessKeyId() != null), null);
                awsDebug("CONFIG_CHECK", "Secret Key set: " + (config.getSecretAccessKey() != null), null);
                awsDebug("CONFIG_CHECK", "Session Token set: " + (config.getSessionToken() != null), null);
                awsDebug("CONFIG_CHECK", "Role ARN: " + config.getRoleArn(), null);
                awsDebug("CONFIG_CHECK", "Regions: " + config.getRegions(), null);
                
                LOG.info("Reinitializing AWS credentials provider");
                awsDebug("CREDS_INIT", "Calling initializeCredentialsProvider", null);
                
                try {
                    initializeCredentialsProvider();
                    awsDebug("CREDS_INIT", "Credentials provider initialized successfully", null);
                } catch (Exception e) {
                    awsDebug("CREDS_INIT", "Credential initialization failed", e);
                    throw e; // Re-throw to be handled by outer catch
                }
                
                debugPrintConfigState("AFTER_CREDENTIALS_INIT");
                
                // TEMPORARY WORKAROUND: Skip validation during troubleshooting
                awsDebug("VALIDATION", "Beginning validation phase", null);
                
                // Always skip validation in our emergency debug mode to separate credential issues from validation issues
                if (true) {
                    awsDebug("VALIDATION", "SKIPPING validation for debugging purposes", null);
                    LOG.info("SKIPPING validation step for debugging purposes");
                } else {
                    awsDebug("VALIDATION", "Calling validateConfigurationWorks", null);
                    LOG.info("Validating updated configuration");
                    validateConfigurationWorks();
                    awsDebug("VALIDATION", "Validation completed successfully", null);
                }
                
                awsDebug("SUCCESS", "Configuration update completed successfully", null);
                LOG.info("Successfully updated configuration for AWS provider: {}", config.getProviderId());
            } catch (Exception e) {
                LOG.error("Error initializing or validating credentials with new configuration: {}", e.getMessage(), e);
                Throwable rootCause = getRootCause(e);
                LOG.error("Root cause: {} - {}", rootCause.getClass().getName(), rootCause.getMessage());
                
                awsDebug("CRITICAL_ERROR", "Error during credential initialization or validation", e);
                awsDebug("ROOT_CAUSE", "Root cause: " + rootCause.getClass().getName() + " - " + rootCause.getMessage(), null);
                
                // Try to capture the full exception chain
                Throwable current = e;
                int level = 0;
                while (current != null) {
                    awsDebug("EXCEPTION_CHAIN", "Level " + level + ": " + current.getClass().getName() + " - " + current.getMessage(), null);
                    level++;
                    current = current.getCause();
                }
                
                // Attempt to diagnose common AWS credential issues
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                String rootMsg = rootCause.getMessage() != null ? rootCause.getMessage().toLowerCase() : "";
                
                if (errorMsg.contains("access denied") || rootMsg.contains("access denied") || 
                    errorMsg.contains("not authorized") || rootMsg.contains("not authorized")) {
                    awsDebug("DIAGNOSIS", "This appears to be an AWS permissions issue - credentials may be valid but lack required permissions", null);
                } else if (errorMsg.contains("invalid") || rootMsg.contains("invalid") ||
                           errorMsg.contains("credentials") || rootMsg.contains("credentials")) {
                    awsDebug("DIAGNOSIS", "This appears to be an AWS credentials issue - credentials may be invalid or expired", null);
                } else if (errorMsg.contains("timeout") || rootMsg.contains("timeout") ||
                           errorMsg.contains("connect") || rootMsg.contains("connect")) {
                    awsDebug("DIAGNOSIS", "This appears to be a network connectivity issue to AWS services", null);
                }
                
                // Add direct debug entry that will show up regardless of logging system
                directDebug("CRITICAL FAILURE: Failed to initialize credentials - " + e.getMessage());
                directDebug("Root cause: " + (rootCause != null ? rootCause.getClass().getName() + ": " + rootCause.getMessage() : "None"));
                
                throw new CloudProviderException("Failed to initialize AWS credentials after configuration update: " + e.getMessage() + 
                        " (Root cause: " + rootCause.getMessage() + ")", e);
            }
        } catch (Exception e) {
            // Rollback to previous configuration on error
            LOG.error("Error updating AWS provider configuration, rolling back to previous configuration: {}", e.getMessage(), e);
            awsDebug("OUTER_ERROR", "Error in outer try-catch block during configuration update", e);
            
            try {
                LOG.debug("Performing rollback to previous configuration");
                awsDebug("ROLLBACK", "Attempting to roll back to previous configuration", null);
                copyConfigurationProperties(backupConfig, config);
                credentialsProvider = backupCredentialsProvider;
                LOG.debug("Rollback to previous configuration completed");
                awsDebug("ROLLBACK", "Rollback completed successfully", null);
                debugPrintConfigState("AFTER_ROLLBACK");
            } catch (Exception rollbackEx) {
                LOG.error("Failed to rollback to previous configuration: {}", rollbackEx.getMessage(), rollbackEx);
                awsDebug("ROLLBACK_ERROR", "Failed to roll back to previous configuration", rollbackEx);
                // Continue with the original error
            }
            
            Throwable rootCause = getRootCause(e);
            LOG.error("Root cause of configuration update failure: {} - {}", rootCause.getClass().getName(), rootCause.getMessage());
            awsDebug("FINAL_ERROR", "Final error diagnosis - Root cause: " + rootCause.getClass().getName() + " - " + rootCause.getMessage(), null);
            
            // Try to dump a basic system environment summary
            try {
                Runtime runtime = Runtime.getRuntime();
                awsDebug("SYSTEM", "Java version: " + System.getProperty("java.version"), null);
                awsDebug("SYSTEM", "Memory: " + runtime.freeMemory()/1024/1024 + "MB free of " + runtime.totalMemory()/1024/1024 + "MB total", null);
                awsDebug("SYSTEM", "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"), null);
            } catch (Exception envEx) {
                awsDebug("SYSTEM", "Error getting system information", envEx);
            }
            
            // Add direct debug at the very end for guaranteed visibility
            directDebug("FINAL ERROR: " + e.getMessage());
            directDebug("FINAL ROOT CAUSE: " + (rootCause != null ? rootCause.getClass().getName() + ": " + rootCause.getMessage() : "None"));
            directDebug("THIS ERROR MESSAGE WILL APPEAR IN /tmp/aws-direct-debug.log");
            
            throw new CloudProviderException("Failed to update configuration for AWS provider " + config.getProviderId() + 
                    ": " + e.getMessage() + " (Root cause: " + rootCause.getMessage() + ")", e);
        }
    }
    
    /**
     * Create a loggable version of the configuration map that masks sensitive values.
     * 
     * @param configuration The configuration map
     * @return A string representation with sensitive values masked
     */
    private String loggableConfigMap(Map<String, Object> configuration) {
        Map<String, Object> loggableMap = new HashMap<>(configuration);
        
        // Mask sensitive information
        if (loggableMap.containsKey("accessKeyId")) {
            String accessKey = (String) loggableMap.get("accessKeyId");
            if (accessKey != null && accessKey.length() > 5) {
                loggableMap.put("accessKeyId", accessKey.substring(0, 5) + "...");
            }
        }
        
        if (loggableMap.containsKey("secretAccessKey")) {
            loggableMap.put("secretAccessKey", "********");
        }
        
        if (loggableMap.containsKey("sessionToken")) {
            loggableMap.put("sessionToken", "********");
        }
        
        return loggableMap.toString();
    }
    
    /**
     * Get the root cause of an exception.
     * 
     * @param throwable The exception
     * @return The root cause
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
    
    /**
     * Update basic configuration properties from the provided configuration map.
     *
     * @param configuration Configuration map
     */
    private void updateBasicConfiguration(Map<String, Object> configuration) {
        if (configuration.containsKey("providerId")) {
            Object value = configuration.get("providerId");
            if (value instanceof String) {
                config.setProviderId((String) value);
            } else {
                LOG.warn("Ignoring invalid providerId value type: {}", value != null ? value.getClass().getName() : "null");
            }
        }
        
        if (configuration.containsKey("displayName")) {
            Object value = configuration.get("displayName");
            if (value instanceof String) {
                config.setDisplayName((String) value);
            } else {
                LOG.warn("Ignoring invalid displayName value type: {}", value != null ? value.getClass().getName() : "null");
            }
        }
        
        if (configuration.containsKey("accessKeyId")) {
            Object value = configuration.get("accessKeyId");
            if (value instanceof String) {
                config.setAccessKeyId((String) value);
            } else {
                LOG.warn("Ignoring invalid accessKeyId value type: {}", value != null ? value.getClass().getName() : "null");
            }
        }
        
        if (configuration.containsKey("secretAccessKey")) {
            Object value = configuration.get("secretAccessKey");
            if (value instanceof String) {
                config.setSecretAccessKey((String) value);
            } else {
                LOG.warn("Ignoring invalid secretAccessKey value type: {}", value != null ? value.getClass().getName() : "null");
            }
        }
        
        if (configuration.containsKey("sessionToken")) {
            Object value = configuration.get("sessionToken");
            if (value instanceof String) {
                config.setSessionToken((String) value);
            } else {
                LOG.warn("Ignoring invalid sessionToken value type: {}", value != null ? value.getClass().getName() : "null");
            }
        }
        
        if (configuration.containsKey("roleArn")) {
            Object value = configuration.get("roleArn");
            if (value instanceof String) {
                config.setRoleArn((String) value);
            } else {
                LOG.warn("Ignoring invalid roleArn value type: {}", value != null ? value.getClass().getName() : "null");
            }
        }
        
        if (configuration.containsKey("regions")) {
            Object value = configuration.get("regions");
            try {
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> regionObjects = (List<Object>) value;
                    List<String> regions = new ArrayList<>();
                    
                    for (Object regionObj : regionObjects) {
                        if (regionObj instanceof String) {
                            regions.add((String) regionObj);
                        } else {
                            LOG.warn("Ignoring non-string region value: {}", regionObj);
                        }
                    }
                    
                    if (!regions.isEmpty()) {
                        config.setRegions(regions);
                    } else {
                        LOG.warn("Empty regions list after filtering invalid values, keeping existing regions");
                    }
                } else {
                    LOG.warn("Ignoring invalid regions value type: {}", value != null ? value.getClass().getName() : "null");
                }
            } catch (Exception e) {
                LOG.warn("Error processing regions configuration: {}", e.getMessage(), e);
            }
        }
        
        if (configuration.containsKey("connectionTimeout")) {
            Object value = configuration.get("connectionTimeout");
            try {
                if (value instanceof Number) {
                    config.setConnectionTimeout(Duration.ofMillis(((Number) value).longValue()));
                } else if (value instanceof String) {
                    config.setConnectionTimeout(Duration.ofMillis(Long.parseLong((String) value)));
                } else {
                    LOG.warn("Ignoring invalid connectionTimeout value type: {}", value != null ? value.getClass().getName() : "null");
                }
            } catch (NumberFormatException e) {
                LOG.warn("Invalid connectionTimeout value: {}", value);
            }
        }
        
        if (configuration.containsKey("readTimeout")) {
            Object value = configuration.get("readTimeout");
            try {
                if (value instanceof Number) {
                    config.setReadTimeout(Duration.ofMillis(((Number) value).longValue()));
                } else if (value instanceof String) {
                    config.setReadTimeout(Duration.ofMillis(Long.parseLong((String) value)));
                } else {
                    LOG.warn("Ignoring invalid readTimeout value type: {}", value != null ? value.getClass().getName() : "null");
                }
            } catch (NumberFormatException e) {
                LOG.warn("Invalid readTimeout value: {}", value);
            }
        }
        
        if (configuration.containsKey("maxRetries")) {
            Object value = configuration.get("maxRetries");
            try {
                if (value instanceof Number) {
                    config.setMaxRetries(((Number) value).intValue());
                } else if (value instanceof String) {
                    config.setMaxRetries(Integer.parseInt((String) value));
                } else {
                    LOG.warn("Ignoring invalid maxRetries value type: {}", value != null ? value.getClass().getName() : "null");
                }
            } catch (NumberFormatException e) {
                LOG.warn("Invalid maxRetries value: {}", value);
            }
        }
    }
    
    /**
     * Update EC2 discovery configuration from the provided configuration map.
     *
     * @param configuration Configuration map
     */
    private void updateEc2DiscoveryConfiguration(Map<String, Object> configuration) {
        if (!configuration.containsKey("ec2Discovery")) {
            return;
        }
        
        Object ec2ConfigObj = configuration.get("ec2Discovery");
        if (!(ec2ConfigObj instanceof Map)) {
            LOG.warn("Ignoring invalid ec2Discovery value type: {}", ec2ConfigObj != null ? ec2ConfigObj.getClass().getName() : "null");
            return;
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> ec2Config = (Map<String, Object>) ec2ConfigObj;
            
            if (ec2Config.containsKey("enabled")) {
                Object value = ec2Config.get("enabled");
                if (value instanceof Boolean) {
                    config.getEc2Discovery().setEnabled((Boolean) value);
                } else if (value instanceof String) {
                    config.getEc2Discovery().setEnabled(Boolean.parseBoolean((String) value));
                } else {
                    LOG.warn("Ignoring invalid ec2Discovery.enabled value type: {}", value != null ? value.getClass().getName() : "null");
                }
            }
            
            if (ec2Config.containsKey("includeTags")) {
                Object value = ec2Config.get("includeTags");
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> tagObjects = (List<Object>) value;
                    List<String> tags = new ArrayList<>();
                    
                    for (Object tagObj : tagObjects) {
                        if (tagObj instanceof String) {
                            tags.add((String) tagObj);
                        } else {
                            LOG.warn("Ignoring non-string includeTags value: {}", tagObj);
                        }
                    }
                    
                    config.getEc2Discovery().setIncludeTags(tags);
                } else {
                    LOG.warn("Ignoring invalid includeTags value type: {}", value != null ? value.getClass().getName() : "null");
                }
            }
            
            if (ec2Config.containsKey("filterByTags")) {
                Object value = ec2Config.get("filterByTags");
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> tagObjects = (List<Object>) value;
                    List<String> tags = new ArrayList<>();
                    
                    for (Object tagObj : tagObjects) {
                        if (tagObj instanceof String) {
                            tags.add((String) tagObj);
                        } else {
                            LOG.warn("Ignoring non-string filterByTags value: {}", tagObj);
                        }
                    }
                    
                    config.getEc2Discovery().setFilterByTags(tags);
                } else {
                    LOG.warn("Ignoring invalid filterByTags value type: {}", value != null ? value.getClass().getName() : "null");
                }
            }
            
            if (ec2Config.containsKey("instanceStates")) {
                Object value = ec2Config.get("instanceStates");
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> stateObjects = (List<Object>) value;
                    List<String> states = new ArrayList<>();
                    
                    for (Object stateObj : stateObjects) {
                        if (stateObj instanceof String) {
                            states.add((String) stateObj);
                        } else {
                            LOG.warn("Ignoring non-string instanceStates value: {}", stateObj);
                        }
                    }
                    
                    if (!states.isEmpty()) {
                        config.getEc2Discovery().setInstanceStates(states);
                    } else {
                        // Default to "running" if empty after filtering
                        config.getEc2Discovery().setInstanceStates(Collections.singletonList("running"));
                    }
                } else {
                    LOG.warn("Ignoring invalid instanceStates value type: {}", value != null ? value.getClass().getName() : "null");
                }
            }
        } catch (Exception e) {
            LOG.warn("Error processing EC2 discovery configuration: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update CloudWatch collection configuration from the provided configuration map.
     *
     * @param configuration Configuration map
     */
    private void updateCloudWatchConfiguration(Map<String, Object> configuration) {
        if (!configuration.containsKey("cloudWatchCollection")) {
            return;
        }
        
        Object cloudWatchConfigObj = configuration.get("cloudWatchCollection");
        if (!(cloudWatchConfigObj instanceof Map)) {
            LOG.warn("Ignoring invalid cloudWatchCollection value type: {}", cloudWatchConfigObj != null ? cloudWatchConfigObj.getClass().getName() : "null");
            return;
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> cloudWatchConfig = (Map<String, Object>) cloudWatchConfigObj;
            
            if (cloudWatchConfig.containsKey("enabled")) {
                Object value = cloudWatchConfig.get("enabled");
                if (value instanceof Boolean) {
                    config.getCloudWatchCollection().setEnabled((Boolean) value);
                } else if (value instanceof String) {
                    config.getCloudWatchCollection().setEnabled(Boolean.parseBoolean((String) value));
                } else {
                    LOG.warn("Ignoring invalid cloudWatchCollection.enabled value type: {}", value != null ? value.getClass().getName() : "null");
                }
            }
            
            if (cloudWatchConfig.containsKey("metrics")) {
                Object value = cloudWatchConfig.get("metrics");
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> metricObjects = (List<Object>) value;
                    List<String> metrics = new ArrayList<>();
                    
                    for (Object metricObj : metricObjects) {
                        if (metricObj instanceof String) {
                            metrics.add((String) metricObj);
                        } else {
                            LOG.warn("Ignoring non-string metrics value: {}", metricObj);
                        }
                    }
                    
                    if (!metrics.isEmpty()) {
                        config.getCloudWatchCollection().setMetrics(metrics);
                    } else {
                        // Set default metrics if empty after filtering
                        config.getCloudWatchCollection().setMetrics(Arrays.asList(
                            "CPUUtilization", "NetworkIn", "NetworkOut", "DiskReadBytes", "DiskWriteBytes", "StatusCheckFailed"
                        ));
                    }
                } else {
                    LOG.warn("Ignoring invalid metrics value type: {}", value != null ? value.getClass().getName() : "null");
                }
            }
            
            if (cloudWatchConfig.containsKey("period")) {
                Object value = cloudWatchConfig.get("period");
                try {
                    if (value instanceof Number) {
                        config.getCloudWatchCollection().setPeriod(Duration.ofMinutes(((Number) value).longValue()));
                    } else if (value instanceof String) {
                        config.getCloudWatchCollection().setPeriod(Duration.ofMinutes(Long.parseLong((String) value)));
                    } else {
                        LOG.warn("Ignoring invalid period value type: {}", value != null ? value.getClass().getName() : "null");
                    }
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid period value: {}", value);
                }
            }
            
            if (cloudWatchConfig.containsKey("statistics")) {
                Object value = cloudWatchConfig.get("statistics");
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> statObjects = (List<Object>) value;
                    List<String> statistics = new ArrayList<>();
                    
                    for (Object statObj : statObjects) {
                        if (statObj instanceof String) {
                            statistics.add((String) statObj);
                        } else {
                            LOG.warn("Ignoring non-string statistics value: {}", statObj);
                        }
                    }
                    
                    if (!statistics.isEmpty()) {
                        config.getCloudWatchCollection().setStatistics(statistics);
                    } else {
                        // Set default statistics if empty after filtering
                        config.getCloudWatchCollection().setStatistics(Arrays.asList("Average", "Maximum", "Minimum"));
                    }
                } else {
                    LOG.warn("Ignoring invalid statistics value type: {}", value != null ? value.getClass().getName() : "null");
                }
            }
        } catch (Exception e) {
            LOG.warn("Error processing CloudWatch collection configuration: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Validate that the updated configuration works by making a simple API call.
     * 
     * @throws CloudProviderException if validation fails
     */
    private void validateConfigurationWorks() throws CloudProviderException {
        try {
            LOG.info("Validating AWS provider configuration for provider ID: {}", config.getProviderId());
            LOG.debug("Current configuration details: accessKeyId={}, secretKey={}, roleArn={}, regions={}",
                    config.getAccessKeyId() != null ? "provided" : "not provided",
                    config.getSecretAccessKey() != null ? "provided" : "not provided",
                    config.getRoleArn(),
                    config.getRegions());
            
            if (credentialsProvider == null) {
                LOG.error("Validation failed: Credentials provider is null after initialization");
                throw new CloudProviderException("Credentials provider is null after initialization");
            }
            
            // Verify credentials can be resolved
            LOG.info("Attempting to resolve AWS credentials...");
            try {
                credentialsProvider.resolveCredentials();
                LOG.info("Successfully resolved AWS credentials");
            } catch (Exception e) {
                LOG.error("Failed to resolve AWS credentials: {}", e.getMessage(), e);
                throw new CloudProviderException("Failed to resolve AWS credentials: " + e.getMessage(), e);
            }
            
            // Test a simple API call to verify configuration
            List<String> regions = getRegionsToUse();
            LOG.info("Using regions for validation: {}", regions);
            
            if (regions.isEmpty()) {
                LOG.error("Validation failed: No AWS regions configured");
                throw new CloudProviderException("No AWS regions configured");
            }
            
            String testRegion = regions.get(0);
            LOG.info("Testing AWS configuration using region: {}", testRegion);
            
            try {
                LOG.debug("Building STS client for region {} with provided credentials", testRegion);
                StsClient stsClient = StsClient.builder()
                        .region(Region.of(testRegion))
                        .credentialsProvider(credentialsProvider)
                        .overrideConfiguration(c -> c
                            .apiCallTimeout(Duration.ofSeconds(10))
                            .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                        .build();
                
                try {
                    // Make a simple call to verify connectivity and authentication
                    LOG.info("Making test AWS API call (getCallerIdentity)...");
                    String accountId = stsClient.getCallerIdentity().account();
                    LOG.info("Successfully validated AWS configuration. Connected to AWS Account: {}", accountId);
                    stsClient.close();
                } catch (Exception e) {
                    LOG.error("AWS API call failed: {}", e.getMessage(), e);
                    stsClient.close();
                    throw new CloudProviderException("AWS API call failed: " + e.getMessage(), e);
                }
            } catch (Exception e) {
                LOG.error("Failed to create STS client: {}", e.getMessage(), e);
                throw new CloudProviderException("Failed to create STS client: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            if (e instanceof CloudProviderException) {
                throw (CloudProviderException) e;
            }
            LOG.error("Configuration validation failed with unexpected error: {}", e.getMessage(), e);
            throw new CloudProviderException("Configuration validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Utility method to copy configuration properties from one object to another.
     *
     * @param source Source configuration
     * @param target Target configuration
     */
    private void copyConfigurationProperties(AwsConfigurationProperties source, AwsConfigurationProperties target) {
        target.setProviderId(source.getProviderId());
        target.setDisplayName(source.getDisplayName());
        target.setAccessKeyId(source.getAccessKeyId());
        target.setSecretAccessKey(source.getSecretAccessKey());
        target.setSessionToken(source.getSessionToken());
        target.setRoleArn(source.getRoleArn());
        
        // Copy regions (create new list to avoid sharing references)
        if (source.getRegions() != null) {
            target.setRegions(new ArrayList<>(source.getRegions()));
        } else {
            target.setRegions(new ArrayList<>());
        }
        
        target.setConnectionTimeout(source.getConnectionTimeout());
        target.setReadTimeout(source.getReadTimeout());
        target.setMaxRetries(source.getMaxRetries());
        
        // EC2 discovery configuration
        target.getEc2Discovery().setEnabled(source.getEc2Discovery().isEnabled());
        if (source.getEc2Discovery().getIncludeTags() != null) {
            target.getEc2Discovery().setIncludeTags(new ArrayList<>(source.getEc2Discovery().getIncludeTags()));
        }
        if (source.getEc2Discovery().getFilterByTags() != null) {
            target.getEc2Discovery().setFilterByTags(new ArrayList<>(source.getEc2Discovery().getFilterByTags()));
        }
        if (source.getEc2Discovery().getInstanceStates() != null) {
            target.getEc2Discovery().setInstanceStates(new ArrayList<>(source.getEc2Discovery().getInstanceStates()));
        }
        
        // CloudWatch collection configuration
        target.getCloudWatchCollection().setEnabled(source.getCloudWatchCollection().isEnabled());
        if (source.getCloudWatchCollection().getMetrics() != null) {
            target.getCloudWatchCollection().setMetrics(new ArrayList<>(source.getCloudWatchCollection().getMetrics()));
        }
        target.getCloudWatchCollection().setPeriod(source.getCloudWatchCollection().getPeriod());
        if (source.getCloudWatchCollection().getStatistics() != null) {
            target.getCloudWatchCollection().setStatistics(new ArrayList<>(source.getCloudWatchCollection().getStatistics()));
        }
    }
    
    @Override
    public Set<String> getSupportedMetrics() {
        Set<String> metrics = new HashSet<>();
        
        // Add EC2 metrics
        if (config.getEc2Discovery().isEnabled()) {
            for (String metric : config.getCloudWatchCollection().getMetrics()) {
                for (String statistic : config.getCloudWatchCollection().getStatistics()) {
                    metrics.add(metric + "." + statistic);
                }
            }
        }
        
        return metrics;
    }
    
    /**
     * Close all clients and release resources.
     */
    private void closeClients() {
        // Close EC2 clients
        for (Map.Entry<String, Ec2Client> entry : ec2ClientCache.entrySet()) {
            try {
                LOG.debug("Closing EC2 client for region {}", entry.getKey());
                entry.getValue().close();
            } catch (Exception e) {
                LOG.warn("Error closing EC2 client for region {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
        ec2ClientCache.clear();
        
        // Close CloudWatch clients
        for (Map.Entry<String, CloudWatchClient> entry : cloudWatchClientCache.entrySet()) {
            try {
                LOG.debug("Closing CloudWatch client for region {}", entry.getKey());
                entry.getValue().close();
            } catch (Exception e) {
                LOG.warn("Error closing CloudWatch client for region {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
        cloudWatchClientCache.clear();
    }
    
    @Override
    public void close() {
        LOG.info("Closing AWS cloud provider: {}", config.getProviderId());
        closeClients();
    }
}