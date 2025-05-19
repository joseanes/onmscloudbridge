package org.opennms.bridge.api;

/**
 * Exception thrown by cloud provider implementations.
 */
public class CloudProviderException extends Exception {
    
    private String providerId;
    private String providerType;
    private String errorCode;
    
    public CloudProviderException(String message) {
        super(message);
    }
    
    public CloudProviderException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public CloudProviderException(String providerId, String message) {
        super(message);
        this.providerId = providerId;
    }
    
    public CloudProviderException(String providerId, String message, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
    }
    
    public CloudProviderException(String providerId, String errorCode, String message) {
        super(message);
        this.providerId = providerId;
        this.errorCode = errorCode;
    }
    
    public CloudProviderException(String providerId, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
        this.errorCode = errorCode;
    }
    
    public String getProviderId() {
        return providerId;
    }
    
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
    
    public String getProviderType() {
        return providerType;
    }
    
    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}