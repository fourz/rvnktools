package org.fourz.rvnktools.dhlogfilter.repository;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.dhlogfilter.model.DHLogFilterConfig;
import org.fourz.rvnktools.dhlogfilter.model.DHLogFilterConfig.LogLevel;
import org.fourz.rvnktools.util.log.LogManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * YAML-based implementation of DHLogFilterConfigRepository.
 * Handles loading and saving configuration data from/to YAML files with proper error handling.
 * 
 * @since 1.1-alpha
 */
public class YamlDHLogFilterConfigRepository implements DHLogFilterConfigRepository {
    
    private static final String CONFIG_FILE_NAME = "dhlogfilter.yml";
    private static final String BACKUP_FILE_PREFIX = "dhlogfilter_backup_";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private final Plugin plugin;
    private final File configFile;
    private final File dataFolder;
    private final LogManager logger;
    
    /**
     * Constructor for YamlDHLogFilterConfigRepository.
     * 
     * @param plugin The plugin instance for accessing data folder and logging
     */
    public YamlDHLogFilterConfigRepository(Plugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.configFile = new File(dataFolder, CONFIG_FILE_NAME);
        this.logger = LogManager.getInstance(plugin, getClass());
        
        // Ensure data folder exists
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }
    
    @Override
    public CompletableFuture<DHLogFilterConfig> loadConfiguration() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!configFile.exists()) {
                    logger.info("DH log filter configuration not found, creating default configuration");
                    DHLogFilterConfig defaultConfig = createDefaultConfigObject();
                    saveConfigurationSync(defaultConfig);
                    return defaultConfig;
                }
                
                FileConfiguration yamlConfig = YamlConfiguration.loadConfiguration(configFile);
                return parseConfiguration(yamlConfig);
                
            } catch (Exception e) {
                logger.error("Failed to load DH log filter configuration, using default", e);
                return createDefaultConfigObject();
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> saveConfiguration(DHLogFilterConfig config) {
        return CompletableFuture.runAsync(() -> {
            saveConfigurationSync(config);
        });
    }
    
    @Override
    public CompletableFuture<Boolean> configurationExists() {
        return CompletableFuture.supplyAsync(() -> configFile.exists());
    }
    
    @Override
    public CompletableFuture<Void> createDefaultConfiguration() {
        return CompletableFuture.runAsync(() -> {
            try {
                DHLogFilterConfig defaultConfig = createDefaultConfigObject();
                saveConfigurationSync(defaultConfig);
                logger.info("Created default DH log filter configuration");
            } catch (Exception e) {
                logger.error("Failed to create default configuration", e);
                throw new RuntimeException("Failed to create default configuration", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> backupConfiguration() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!configFile.exists()) {
                    logger.warning("Cannot backup configuration - file does not exist");
                    return;
                }
                
                String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
                File backupFile = new File(dataFolder, BACKUP_FILE_PREFIX + timestamp + ".yml");
                
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Backed up DH log filter configuration to: " + backupFile.getName());
                
            } catch (IOException e) {
                logger.error("Failed to backup configuration", e);
                throw new RuntimeException("Failed to backup configuration", e);
            }
        });
    }
    
    /**
     * Parse a FileConfiguration into a DHLogFilterConfig object.
     * 
     * @param yamlConfig The YAML configuration to parse
     * @return The parsed configuration
     */
    private DHLogFilterConfig parseConfiguration(FileConfiguration yamlConfig) {
        DHLogFilterConfig config = new DHLogFilterConfig();
        
        try {
            // Parse log level
            String levelString = yamlConfig.getString("logging.level", "INFO");
            config.setLevel(LogLevel.fromString(levelString));
            
            // Parse keywords
            List<String> keywords = yamlConfig.getStringList("logging.keywords");
            if (keywords.isEmpty()) {
                // Fallback to default if not configured
                keywords = List.of("[DHS] Received");
            }
            config.setKeywords(keywords);
            
            // Parse other settings
            config.setEnabled(yamlConfig.getBoolean("logging.enabled", true));
            config.setRateLimitSeconds(yamlConfig.getLong("logging.rateLimitSeconds", 30));
            config.setMaxCacheSize(yamlConfig.getInt("logging.maxCacheSize", 1000));
            config.setUseRegexPatterns(yamlConfig.getBoolean("logging.useRegexPatterns", false));
            
            // Validate configuration
            config.validate();
            
            logger.debug("Loaded DH log filter configuration: " + config);
            return config;
            
        } catch (Exception e) {
            logger.error("Failed to parse configuration, using defaults", e);
            return createDefaultConfigObject();
        }
    }
    
    /**
     * Save configuration synchronously.
     * 
     * @param config The configuration to save
     */
    private void saveConfigurationSync(DHLogFilterConfig config) {
        try {
            config.validate();
            
            FileConfiguration yamlConfig = new YamlConfiguration();
            
            // Set configuration values
            yamlConfig.set("logging.level", config.getLevel().getName());
            yamlConfig.set("logging.keywords", config.getKeywords());
            yamlConfig.set("logging.enabled", config.isEnabled());
            yamlConfig.set("logging.rateLimitSeconds", config.getRateLimitSeconds());
            yamlConfig.set("logging.maxCacheSize", config.getMaxCacheSize());
            yamlConfig.set("logging.useRegexPatterns", config.isUseRegexPatterns());
            
            // Add comments through header
            yamlConfig.options().header(createConfigHeader());
            
            yamlConfig.save(configFile);
            logger.debug("Saved DH log filter configuration: " + config);
            
        } catch (IOException e) {
            logger.error("Failed to save DH log filter configuration", e);
            throw new RuntimeException("Failed to save configuration", e);
        }
    }
    
    /**
     * Create a default configuration object.
     * 
     * @return Default configuration
     */
    private DHLogFilterConfig createDefaultConfigObject() {
        DHLogFilterConfig config = new DHLogFilterConfig();
        config.setLevel(LogLevel.INFO);
        config.setKeywords(List.of("[DHS] Received"));
        config.setEnabled(true);
        config.setRateLimitSeconds(30);
        config.setMaxCacheSize(1000);
        config.setUseRegexPatterns(false);
        return config;
    }
    
    /**
     * Create the configuration file header with documentation.
     * 
     * @return Configuration header string
     */
    private String createConfigHeader() {
        return "\n" +
            "DH Log Filter Configuration\n" +
            "===========================\n" +
            "\n" +
            "This configuration controls the filtering of repetitive Distant Horizons server plugin messages.\n" +
            "\n" +
            "Settings:\n" +
            "  level: Log level threshold (DEBUG, INFO, WARN, ERROR)\n" +
            "    - DEBUG: Show all messages (most verbose)\n" +
            "    - INFO: Show info, warnings, and errors (default)\n" +
            "    - WARN: Show only warnings and errors\n" +
            "    - ERROR: Show only errors\n" +
            "\n" +
            "  keywords: List of message keywords to filter\n" +
            "    - Messages containing these keywords will be filtered based on rate limiting\n" +
            "    - Use regex patterns if useRegexPatterns is enabled\n" +
            "\n" +
            "  enabled: Enable/disable the log filter (true/false)\n" +
            "  rateLimitSeconds: Time window for rate limiting duplicate messages (seconds)\n" +
            "  maxCacheSize: Maximum number of messages to keep in rate limiting cache\n" +
            "  useRegexPatterns: Enable regex pattern matching for keywords (true/false)\n" +
            "\n";
    }
}