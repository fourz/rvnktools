# RVNKCore Configuration Migration Guide

## Configuration Migration: application.properties → config.yml

**Date**: August 2, 2025  
**Status**: Complete - All settings moved to centralized config.yml

## Overview

All RVNKCore configuration has been migrated from `application.properties` to `config.yml` for centralized configuration management. This provides better organization, YAML benefits, and consistency with Bukkit/Spigot plugin conventions.

## What Changed

### Before (application.properties)
```properties
# Database settings
database.type=sqlite
database.sqlite.file=rvnkcore.db
database.mysql.host=localhost
database.mysql.port=3306
database.mysql.username=user
database.mysql.password=pass

# API settings  
api.enabled=false
api.port=8080
api.host=localhost
api.ssl.enabled=false
api.auth.enabled=true
api.auth.key=changeme
```

### After (config.yml)
```yaml
# Database Configuration
database:
  type: sqlite
  sqlite:
    file: rvnkcore.db
  mysql:
    host: localhost
    port: 3306
    database: rvnktools
    username: user
    password: pass
    useSSL: true
    connectionParameters: allowPublicKeyRetrieval=true
    pool:
      maxConnections: 20
      minIdleConnections: 5
      connectionTimeoutMs: 30000
      idleTimeoutMs: 600000
      maxLifetimeMs: 1800000
      leakDetectionMs: 60000

# API Configuration
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
    
  auth:
    enabled: true
    key: "changeme"
    
  cors:
    enabled: false
    allowed-origins: "*"
    allowed-methods: "GET,POST,DELETE,PUT,OPTIONS"
    
  server:
    min-threads: 2
    max-threads: 4
    queue-size: 100
    idle-timeout: 30000
    connection-timeout: 5000
    send-version: false
    use-forwarded-headers: true
    
  security:
    allowed-ips: ""
    
  logging:
    retain-days: 1
    filename: "api-access-%d{yyyy-MM-dd}.log"
```

## Migration Benefits

### 1. **Centralized Configuration**
- All settings in one file (`config.yml`)
- Better organization with hierarchical structure
- Easier to find and modify settings

### 2. **YAML Advantages**
- More readable format
- Support for comments and documentation
- Better structure for complex configurations
- Native Bukkit configuration support

### 3. **Enhanced Features**
- **Database**: Complete connection pool configuration
- **API**: Full server configuration options
- **Validation**: Built-in configuration validation
- **Reload**: Runtime configuration reload support

## Updated Code Components

### DatabaseConfigLoader.java
- **Before**: Read from `application.properties` using Java Properties
- **After**: Read from `config.yml` using Bukkit FileConfiguration
- **Benefit**: Native YAML support, better error handling, configuration validation

### ApiConfig.java  
- **Before**: Mixed property sources
- **After**: Unified config.yml reading with `api.auth.key` instead of `api.key`
- **Benefit**: Consistent configuration structure, better organization

### application.properties
- **Before**: Database and API configuration
- **After**: Empty placeholder for future external configuration needs
- **Status**: Reserved for future use

## Configuration Examples

### Development Setup (SQLite)
```yaml
# Minimal development configuration
database:
  type: sqlite
  sqlite:
    file: rvnkcore.db

api:
  enabled: false
```

### Production Setup (MySQL + API)
```yaml
# Production configuration
database:
  type: mysql
  mysql:
    host: mysql.production.com
    port: 3306
    database: rvnktools
    username: rvnk_prod_user
    password: ${MYSQL_PASSWORD}
    useSSL: true
    connectionParameters: allowPublicKeyRetrieval=true&serverTimezone=UTC
    pool:
      maxConnections: 50
      minIdleConnections: 10
      connectionTimeoutMs: 30000
      idleTimeoutMs: 300000
      maxLifetimeMs: 1800000
      leakDetectionMs: 30000

api:
  enabled: true
  host: 0.0.0.0
  
  http:
    port: 8080
    
  https:
    enabled: true
    port: 8443
    keystore-path: "/etc/ssl/rvnktools.jks"
    keystore-password: "${SSL_KEYSTORE_PASSWORD}"
    
  auth:
    enabled: true
    key: "${API_KEY}"
    
  cors:
    enabled: true
    allowed-origins: "https://admin.yourserver.com"
    
  security:
    allowed-ips: "10.0.0.0/8,192.168.0.0/16"
```

### High-Performance Setup
```yaml
# High-performance server configuration
database:
  type: mysql
  mysql:
    host: mysql-cluster.internal
    port: 3306
    database: rvnktools
    username: rvnk_performance
    password: ${MYSQL_PASSWORD}
    useSSL: true
    connectionParameters: cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048&useServerPrepStmts=true&useLocalSessionState=true&rewriteBatchedStatements=true&cacheResultSetMetadata=true&cacheServerConfiguration=true&elideSetAutoCommits=true&maintainTimeStats=false
    pool:
      maxConnections: 100
      minIdleConnections: 20
      connectionTimeoutMs: 20000
      idleTimeoutMs: 300000
      maxLifetimeMs: 1800000
      leakDetectionMs: 30000

api:
  enabled: true
  
  server:
    min-threads: 4
    max-threads: 20
    queue-size: 500
    idle-timeout: 60000
    connection-timeout: 10000
```

## Migration Checklist

- [x] **Database Configuration Moved**: All database settings moved to `config.yml`
- [x] **API Configuration Moved**: All API settings moved to `config.yml`  
- [x] **Code Updated**: `DatabaseConfigLoader` and `ApiConfig` updated
- [x] **Documentation Updated**: All docs reflect new configuration format
- [x] **Build Verified**: Project compiles successfully with new configuration
- [x] **Backward Compatibility**: Existing SQLite configurations work unchanged

## Troubleshooting

### Configuration Not Loading
1. **Check YAML Syntax**: Ensure proper indentation and syntax
2. **Verify File Location**: `config.yml` must be in plugin data folder
3. **Check Permissions**: Ensure plugin can read configuration file
4. **Validate Structure**: Use online YAML validator if needed

### Database Connection Issues
1. **Check Type Setting**: Ensure `database.type` is set to `sqlite` or `mysql`
2. **Verify Credentials**: For MySQL, check host, port, username, password
3. **Test Connectivity**: Verify MySQL server is accessible
4. **Check SSL Settings**: Ensure SSL configuration matches server requirements

### API Configuration Issues
1. **Port Conflicts**: Ensure API ports don't conflict with other services
2. **SSL Certificate**: Verify keystore path and password for HTTPS
3. **CORS Settings**: Check allowed origins for web access
4. **Authentication**: Verify API key is properly configured

## Future Enhancements

### Planned Features
- **Environment Variables**: Support for `${VAR}` substitution
- **Configuration Profiles**: Development, staging, production profiles
- **Hot Reload**: Runtime configuration changes without restart
- **Validation UI**: Web interface for configuration validation
- **Backup/Restore**: Configuration backup and restore tools

### Migration Path
This migration establishes the foundation for advanced configuration features while maintaining simplicity for basic deployments.

---

*Configuration migration completed as part of Day 1+ MySQL implementation enhancements.*
