package org.fourz.rvnktools.dhlogfilter;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.util.log.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for managing DH log filter configuration with async operations.
 * 
 * Handles loading and saving configuration to YAML files using CompletableFuture
 * to prevent blocking the main thread during I/O operations.
 */
public class DHLogFilterConfigRepository {
    
    private final Plugin plugin;
    private final LogManager logger;
    private final File configFile;
    
    /**
     * Constructor for DHLogFilterConfigRepository.
     * 
     * @param plugin The plugin instance
     */
    public DHLogFilterConfigRepository(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.configFile = new File(plugin.getDataFolder(), "dhlogfilter.yml");
    }
    
    /**
     * Loads configuration from file asynchronously.
     * 
     * @return CompletableFuture containing the loaded configuration
     */
    public CompletableFuture<DHLogFilterConfig> loadConfiguration() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!configFile.exists()) {
                    logger.info("Configuration file not found, creating default configuration");
                    return createDefaultConfiguration();
                }
                
                FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                return parseConfiguration(config);
                
            } catch (Exception e) {
                logger.error("Failed to load DH log filter configuration", e);
                logger.warning("Using default configuration");
                return createDefaultConfiguration();
            }
        });
    }
    
    /**
     * Saves configuration to file asynchronously.
     * 
     * @param config The configuration to save
     * @return CompletableFuture that completes when save is finished
     */
    public CompletableFuture<Void> saveConfiguration(DHLogFilterConfig config) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Validate configuration before saving
                config.validate();
                
                // Ensure plugin data folder exists
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                
                FileConfiguration yamlConfig = new YamlConfiguration();
                writeConfiguration(yamlConfig, config);
                
                yamlConfig.save(configFile);
                logger.info("DH log filter configuration saved successfully");
                
            } catch (IOException e) {
                logger.error("Failed to save DH log filter configuration", e);
                throw new RuntimeException("Configuration save failed", e);
            } catch (Exception e) {
                logger.error("Invalid configuration data", e);
                throw new RuntimeException("Configuration validation failed", e);
            }
        });
    }
    
    /**
     * Creates a default configuration with standard settings.
     * 
     * @return Default configuration
     */
    private DHLogFilterConfig createDefaultConfiguration() {
        DHLogFilterConfig config = new DHLogFilterConfig();
        
        // Save default configuration to file
        saveConfiguration(config).exceptionally(ex -> {
            logger.warning("Could not save default configuration: " + ex.getMessage());
            return null;
        });
        
        return config;
    }
    
    /**
     * Parses YAML configuration into DHLogFilterConfig object.
     * 
     * @param config The YAML configuration
     * @return Parsed configuration object
     */
    private DHLogFilterConfig parseConfiguration(FileConfiguration config) {
        DHLogFilterConfig filterConfig = new DHLogFilterConfig();
        
        // Parse log level
        String levelString = config.getString("logging.level", "INFO");
        try {
            filterConfig.setLevel(DHLogLevel.fromString(levelString));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid log level '" + levelString + "', using INFO");
            filterConfig.setLevel(DHLogLevel.INFO);
        }
        
        // Parse keywords
        List<String> keywords = config.getStringList("logging.keywords");
        if (keywords.isEmpty()) {
            keywords = List.of("[DHS] Received");
        }
        filterConfig.setKeywords(keywords);
        
        // Parse other settings
        filterConfig.setEnabled(config.getBoolean("logging.enabled", true));
        filterConfig.setRateLimitSeconds(config.getInt("logging.rateLimitSeconds", 30));
        filterConfig.setUseRegexPatterns(config.getBoolean("logging.useRegexPatterns", false));
        
        return filterConfig;
    }
    
    /**
     * Writes DHLogFilterConfig object to YAML configuration.
     * 
     * @param yamlConfig The YAML configuration to write to
     * @param config The configuration object to write
     */
    private void writeConfiguration(FileConfiguration yamlConfig, DHLogFilterConfig config) {
        yamlConfig.set("logging.level", config.getLevel().getDisplayName());
        yamlConfig.set("logging.keywords", config.getKeywords());
        yamlConfig.set("logging.enabled", config.isEnabled());
        yamlConfig.set("logging.rateLimitSeconds", config.getRateLimitSeconds());
        yamlConfig.set("logging.useRegexPatterns", config.isUseRegexPatterns());
        
        // Add comments for documentation
        yamlConfig.setComments("logging", List.of(
            "DH Log Filter Configuration",
            "Reduces console spam from repetitive Distant Horizons messages"
        ));
        
        yamlConfig.setComments("logging.level", List.of(
            "Log level filter: DEBUG (most verbose), INFO (default), WARN, ERROR (most restrictive)"
        ));
        
        yamlConfig.setComments("logging.keywords", List.of(
            "Keywords to filter from log messages",
            "Messages containing these keywords will be filtered based on rate limiting"
        ));
        
        yamlConfig.setComments("logging.rateLimitSeconds", List.of(
            "Rate limiting cache duration in seconds for repetitive messages"
        ));
        
        yamlConfig.setComments("logging.useRegexPatterns", List.of(
            "Treat keywords as regular expression patterns instead of simple text matching"
        ));
    }
}