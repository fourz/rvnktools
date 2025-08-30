# RVNKCore MySQL Implementation Guide

## Overview

RVNKCore now supports MySQL as a production database backend alongside SQLite. This implementation uses HikariCP for high-performance connection pooling and provides enterprise-grade features for production deployments.

## Features

### ✅ Implemented (Day 1)
- **HikariCP Connection Pooling**: Production-ready connection management
- **SSL/TLS Support**: Secure database connections with certificate validation
- **Configuration Management**: Properties-based configuration with validation
- **Connection Provider Factory**: Automatic database type selection
- **Dual Database Support**: SQLite and MySQL with seamless switching
- **Health Monitoring**: Connection pool statistics and health checks
- **Error Handling**: Comprehensive error handling and logging

### 🚧 Planned (Days 2-5)
- **Schema Migration Framework**: Automated schema upgrades
- **Performance Optimization**: Query optimization and indexing
- **Backup/Restore Tools**: Database backup and restoration utilities
- **Monitoring Dashboard**: Real-time connection pool monitoring
- **Load Testing**: Performance benchmarking tools

## Quick Start

### 1. Configuration

Edit `config.yml` to switch from SQLite to MySQL:

```yaml
# Database Configuration
database:
  type: mysql
  
  # MySQL connection details
  mysql:
    host: localhost
    port: 3306
    database: rvnktools
    username: rvnkuser
    password: secure_password
    useSSL: true
    connectionParameters: allowPublicKeyRetrieval=true
    
    # Connection Pool Configuration
    pool:
      maxConnections: 20
      minIdleConnections: 5
      connectionTimeoutMs: 30000
      idleTimeoutMs: 600000
      maxLifetimeMs: 1800000
      leakDetectionMs: 60000
```

### 2. Database Setup

Create the database and user in MySQL:

```sql
CREATE DATABASE rvnktools CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'rvnkuser'@'%' IDENTIFIED BY 'secure_password';
GRANT ALL PRIVILEGES ON rvnktools.* TO 'rvnkuser'@'%';
FLUSH PRIVILEGES;
```

### 3. Start the Server

RVNKCore automatically detects the MySQL configuration and initializes the connection pool. No code changes required!

## Architecture

### Database Configuration Flow

```
application.properties → DatabaseConfigLoader → DatabaseConfig → ConnectionProviderFactory → MySQLConnectionProvider → HikariCP
```

### Key Components

1. **DatabaseConfig**: Configuration model with builder pattern and validation
2. **MySQLConnectionProvider**: HikariCP-based connection provider
3. **ConnectionProviderFactory**: Factory for creating appropriate providers
4. **DatabaseConfigLoader**: Properties file loader with validation

## Connection Pool Configuration

### Default Settings
- **Maximum Connections**: 20
- **Minimum Idle**: 5
- **Connection Timeout**: 30 seconds
- **Idle Timeout**: 10 minutes
- **Max Lifetime**: 30 minutes
- **Leak Detection**: 60 seconds

### Production Tuning

For high-traffic servers, adjust pool settings:

```yaml
database:
  mysql:
    pool:
      maxConnections: 50
      minIdleConnections: 10
      connectionTimeoutMs: 30000
      idleTimeoutMs: 300000
      maxLifetimeMs: 1800000
      leakDetectionMs: 30000
```

## SSL/TLS Configuration

### Basic SSL (Recommended)

```yaml
database:
  mysql:
    useSSL: true
    connectionParameters: allowPublicKeyRetrieval=true&serverTimezone=UTC
```

### Advanced SSL with Certificates

```yaml
database:
  mysql:
    useSSL: true
    connectionParameters: sslMode=REQUIRED&trustCertificateKeyStoreUrl=file:///path/to/truststore.jks&trustCertificateKeyStorePassword=password
```

## Monitoring and Health Checks

### Connection Pool Metrics

The MySQLConnectionProvider exposes connection pool statistics:

```java
// Get pool statistics
HikariPoolMXBean poolBean = connectionProvider.getPoolMXBean();
int activeConnections = poolBean.getActiveConnections();
int idleConnections = poolBean.getIdleConnections();
int totalConnections = poolBean.getTotalConnections();
```

### Health Check Endpoint

Connection health is automatically validated on checkout and periodically tested.

## Migration from SQLite

### Automatic Detection

RVNKCore automatically uses the configured database type. To migrate:

1. Export SQLite data (future tool)
2. Update configuration to MySQL
3. Import data to MySQL (future tool)
4. Restart server

### Backward Compatibility

SQLite remains the default for development and small deployments:

```yaml
database:
  type: sqlite
  sqlite:
    file: rvnkcore.db
```

## Troubleshooting

### Common Issues

1. **Connection Refused**
   - Check MySQL server is running
   - Verify host/port configuration
   - Check firewall settings

2. **Authentication Failed**
   - Verify username/password
   - Check user permissions
   - Ensure user can connect from server IP

3. **SSL Errors**
   - Verify SSL is enabled on MySQL server
   - Check certificate configuration
   - Try with `useSSL=false` for testing

### Debug Logging

Enable debug logging in `config.yml`:

```yaml
logging:
  level: DEBUG
  components:
    - database
    - connection-pool
```

## Performance Considerations

### Connection Pool Sizing

- **Small Server (1-50 players)**: maxConnections=10-20
- **Medium Server (50-200 players)**: maxConnections=20-40
- **Large Server (200+ players)**: maxConnections=40-80

### Query Optimization

- Use prepared statements (automatically handled)
- Enable query logging for slow query analysis
- Consider read replicas for high-read workloads

## Security Best Practices

1. **Use Strong Passwords**: Minimum 16 characters with mixed case, numbers, symbols
2. **Enable SSL**: Always use SSL in production
3. **Network Security**: Use firewalls to restrict database access
4. **User Permissions**: Grant only necessary privileges
5. **Regular Updates**: Keep MySQL and connector versions updated

## Production Deployment Checklist

- [ ] MySQL server configured with appropriate resources
- [ ] Database and user created with proper permissions
- [ ] SSL/TLS enabled and configured
- [ ] Connection pool sized for expected load
- [ ] Firewall rules configured
- [ ] Backup strategy implemented
- [ ] Monitoring configured
- [ ] Error handling tested
- [ ] Performance testing completed

## Next Steps

1. **Day 2**: Performance optimization and SSL refinement
2. **Day 3**: Schema migration framework
3. **Day 4**: Backup/restore tools and monitoring
4. **Day 5**: Load testing and production validation

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review the configuration examples
3. Enable debug logging for detailed information
4. Consult the RVNKCore documentation

---

*This implementation follows the RVNKCore MySQL Implementation Plan (rvnkcore-implement-mysql.md) Day 1 specifications.*
