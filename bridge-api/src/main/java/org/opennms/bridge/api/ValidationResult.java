package org.opennms.bridge.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a validation operation.
 */
public class ValidationResult {
    
    private boolean valid;
    private List<String> messages;
    
    public ValidationResult() {
        this.messages = new ArrayList<>();
    }
    
    public ValidationResult(boolean valid) {
        this();
        this.valid = valid;
    }
    
    public static ValidationResult valid() {
        return new ValidationResult(true);
    }
    
    public static ValidationResult invalid(String message) {
        ValidationResult result = new ValidationResult(false);
        result.addMessage(message);
        return result;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public List<String> getMessages() {
        return messages;
    }
    
    public void setMessages(List<String> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }
    
    public void addMessage(String message) {
        this.messages.add(message);
    }
    
    /**
     * Gets the first message or a default message if there are no messages.
     * 
     * @return the primary validation message
     */
    public String getMessage() {
        if (messages.isEmpty()) {
            return valid ? "Validation successful" : "Validation failed";
        }
        return messages.get(0);
    }
    
    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", messages=" + messages +
                '}';
    }
}