# Configuration Loader Updates Summary

## Overview

Updated all configuration loaders to ensure that API and database configurations are properly loaded from `config-core.yml` instead of the standard `config.yml`, maintaining proper separation between RVNKTools and RVNKCore configurations.

## Changes Made

### 1. ApiConfigLoader.java Updates

**File**: `org.fourz.rvnkcore.api.config.ApiConfigLoader`

**Key Changes**:
- Added `@Deprecated` annotation - recommends using unified `org.fourz.rvnkcore.config.ConfigLoader`
- Changed configuration file from `config.yml` to `config-core.yml`
- Updated resource copying to use `config-core.yml` from plugin resources
- Modified validation methods to load from `config-core.yml` using `YamlConfiguration.loadConfiguration()`
- Updated `loadApiConfig()` method to create `ApiConfig` with `FileConfiguration` from core config
- Updated fallback configuration to create RVNKCore-specific structure
- Modified error messages to reference correct configuration file

**Before**:
```java
File configFile = new File(plugin.getDataFolder(), "config.yml");
plugin.reloadConfig();
config = plugin.getConfig();
return new ApiConfig(plugin);
```

**After**:
```java
File configFile = new File(plugin.getDataFolder(), configFileName); // "config-core.yml"
org.bukkit.configuration.file.YamlConfiguration coreConfig = 
    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
return new ApiConfig(plugin, coreConfig);
```

### 2. DatabaseConfigLoader.java Updates

**File**: `org.fourz.rvnkcore.database.config.DatabaseConfigLoader`

**Key Changes**:
- Added `@Deprecated` annotation - recommends using unified `org.fourz.rvnkcore.config.ConfigLoader`
- Changed configuration file from `config.yml` to `config-core.yml`
- Updated `loadConfiguration()` method to load from `config-core.yml` using `YamlConfiguration.loadConfiguration()`
- Modified error messages to reference correct configuration file
- Removed dependency on `plugin.reloadConfig()` and `plugin.getConfig()`

**Before**:
```java
plugin.reloadConfig();
config = plugin.getConfig();
throw new IllegalStateException("MySQL host is required but not configured in config.yml");
```

**After**:
```java
config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
throw new IllegalStateException("MySQL host is required but not configured in " + configFileName);
```

### 3. ConnectionProviderFactory.java Updates

**File**: `org.fourz.rvnkcore.database.connection.ConnectionProviderFactory`

**Key Changes**:
- Changed import from `DatabaseConfigLoader` to unified `ConfigLoader`
- Updated to use `ConfigLoader.getDatabaseConfig()` instead of `DatabaseConfigLoader.loadDatabaseConfig()`

**Before**:
```java
import org.fourz.rvnkcore.database.config.DatabaseConfigLoader;
DatabaseConfigLoader configLoader = new DatabaseConfigLoader(plugin);
DatabaseConfig config = configLoader.loadDatabaseConfig();
```

**After**:
```java
import org.fourz.rvnkcore.config.ConfigLoader;
ConfigLoader configLoader = new ConfigLoader(plugin);
DatabaseConfig config = configLoader.getDatabaseConfig();
```

### 4. Enhanced ApiConfig Constructor Support

**File**: `org.fourz.rvnkcore.api.config.ApiConfig`

**Key Changes**:
- Added new constructor: `ApiConfig(Plugin plugin, FileConfiguration config)`
- Original constructor now delegates to new constructor: `this(plugin, plugin.getConfig())`
- Enables ApiConfig to read from any FileConfiguration, including config-core.yml

## Configuration File Structure Validation

### config.yml (RVNKTools scope)
```yaml
# RVNKTools Configuration
logging:
  level: INFO

logfilter:
  enabled: true
  show:
    spigot: false
    bukkit: false
    craftbukkit: false
    paper: false
    plugins: true

features:
  announcements: true
  hat-command: true
  link-command: true
  world-swap: true
```

### config-core.yml (RVNKCore scope)
```yaml
# RVNKCore Configuration
logging:
  level: INFO

database:
  type: sqlite
  sqlite:
    file: rvnkcore.db
  mysql:
    host: localhost
    port: 3306
    database: rvnktools
    username: rvnkuser
    password: secure_password
    useSSL: true
    connectionParameters: allowPublicKeyRetrieval=true
    pool:
      maxConnections: 20
      minIdleConnections: 5
      connectionTimeoutMs: 30000
      idleTimeoutMs: 600000
      maxLifetimeMs: 1800000
      leakDetectionMs: 60000

api:
  enabled: false
  host: localhost
  context-path: /api
  http:
    port: 8080
  https:
    enabled: false
    port: 8081
    keystore-path: "keystore.jks"
    keystore-password: "changeme"
  cors:
    enabled: true
    allowed-origins: "*"
    allowed-methods: "GET,POST,PUT,DELETE,OPTIONS"
  auth:
    key: "changeme"
  server:
    max-threads: 50
    idle-timeout: 30000
    connection-timeout: 60000
    send-version: false
    use-forwarded-headers: true
  security:
    allowed-ips: ""
```

## Fallback Logic Updates

### ApiConfigLoader Fallback
- Creates `config-core.yml` fallback with RVNKCore-specific structure
- Includes database, API, and logging configuration sections
- No longer attempts to use `plugin.saveDefaultConfig()` for non-standard config files

### DatabaseConfigLoader Fallback
- Updated error messages to reference `config-core.yml`
- Maintains proper validation for MySQL connection requirements
- Consistent error reporting with correct file references

## Migration Path

### Deprecated Classes
- `ApiConfigLoader` - Use `org.fourz.rvnkcore.config.ConfigLoader.getApiConfig()`
- `DatabaseConfigLoader` - Use `org.fourz.rvnkcore.config.ConfigLoader.getDatabaseConfig()`

### Recommended Usage
```java
// New unified approach
org.fourz.rvnkcore.config.ConfigLoader coreLoader = new org.fourz.rvnkcore.config.ConfigLoader(plugin);
ApiConfig apiConfig = coreLoader.getApiConfig();
DatabaseConfig dbConfig = coreLoader.getDatabaseConfig();

// RVNKTools configuration
org.fourz.rvnktools.config.ConfigLoader toolsLoader = new org.fourz.rvnktools.config.ConfigLoader(plugin);
Config toolsConfig = toolsLoader.getConfig();
```

## Validation Results

### Build Status
✅ **BUILD SUCCESS** - All configuration changes compile successfully
- No compilation errors
- Proper dependency resolution
- Deprecated methods appropriately marked

### Configuration Loading
✅ **API Configuration** - Loads from config-core.yml with proper validation
✅ **Database Configuration** - Loads from config-core.yml with MySQL/SQLite support
✅ **RVNKTools Configuration** - Loads from config.yml with feature toggles
✅ **Fallback Handling** - Creates appropriate fallback configurations when files missing

### Error Handling
✅ **Proper Error Messages** - All error messages reference correct configuration files
✅ **Validation Logic** - Maintains comprehensive validation for all configuration sections
✅ **Resource Management** - Proper file handling and stream management

## Future Considerations

### Complete Migration Strategy
1. **Phase Out Deprecated Classes** - Remove ApiConfigLoader and DatabaseConfigLoader in future versions
2. **Unified Configuration Management** - All RVNKCore components should use unified ConfigLoader
3. **Enhanced Validation** - Consider adding schema validation for configuration files
4. **Hot Reload Support** - Implement configuration reload without server restart

### Compatibility
- **Backward Compatibility** - Deprecated classes still functional for existing code
- **Migration Warnings** - Deprecated annotations guide developers to new approach
- **Documentation** - Clear migration path documented for future development
