package org.fourz.rvnkcore.api.config;

import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Utility class for testing and validating API configuration.
 * Provides methods to validate configuration loading and identify issues.
 */
public class ApiConfigValidator {
    
    /**
     * Validates API configuration and provides detailed feedback.
     * 
     * @param config The ApiConfig to validate
     * @return ValidationResult with details
     */
    public static ValidationResult validateConfig(ApiConfig config) {
        ValidationResult result = new ValidationResult();
        
        if (config == null) {
            result.addError("ApiConfig is null");
            return result;
        }
        
        // Validate core API configuration
        if (!config.validate()) {
            result.addError("ApiConfig validation failed - check logs for details");
        }
        
        // Check for security issues
        if ("changeme".equals(config.getApiKey())) {
            result.addWarning("API key is set to default value - change for production use");
        }
        
        // Check port configuration
        if (config.getHttpPort() == config.getHttpsPort() && config.isHttpsEnabled()) {
            result.addError("HTTP and HTTPS ports cannot be the same");
        }
        
        // Check HTTPS configuration
        if (config.isHttpsEnabled() && (config.getKeystorePath() == null || config.getKeystorePath().trim().isEmpty())) {
            result.addError("HTTPS enabled but no keystore path specified");
        }
        
        // Check thread configuration
        if (config.getMaxThreads() <= 0) {
            result.addError("Max threads must be greater than 0");
        }
        
        if (config.getMaxThreads() > 200) {
            result.addWarning("Max threads is very high (" + config.getMaxThreads() + ") - consider reducing");
        }
        
        // Check timeout configuration
        if (config.getConnectionTimeout() <= 0) {
            result.addError("Connection timeout must be greater than 0");
        }
        
        if (config.getIdleTimeout() <= 0) {
            result.addError("Idle timeout must be greater than 0");
        }
        
        return result;
    }
    
    /**
     * Result of configuration validation.
     */
    public static class ValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }
        
        public java.util.List<String> getWarnings() {
            return new java.util.ArrayList<>(warnings);
        }
        
        public boolean isValid() {
            return !hasErrors();
        }
        
        /**
         * Logs validation results using the provided logger.
         * 
         * @param logger The logger to use
         */
        public void logResults(LogManager logger) {
            if (hasErrors()) {
                logger.error("Configuration validation failed:");
                for (String error : errors) {
                    logger.error("  - " + error);
                }
            }
            
            if (hasWarnings()) {
                logger.warning("Configuration validation warnings:");
                for (String warning : warnings) {
                    logger.warning("  - " + warning);
                }
            }
            
            if (!hasErrors() && !hasWarnings()) {
                logger.info("Configuration validation passed");
            }
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult{");
            sb.append("valid=").append(isValid());
            sb.append(", errors=").append(errors.size());
            sb.append(", warnings=").append(warnings.size());
            sb.append("}");
            return sb.toString();
        }
    }
}
