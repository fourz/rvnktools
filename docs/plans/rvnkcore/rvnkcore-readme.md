# RVNKCore - Centralized Data and Service Layer

RVNKCore is a foundational Minecraft server plugin that provides a centralized data access layer, service framework, and API for the RVNK plugin ecosystem. It serves as the backbone for plugins like RVNKTools, RVNKLore, RVNKQuests, and others, offering consistent data management, shared services, and cross-plugin communication.

## Features

- **Database Abstraction Layer**:
  - Support for both SQLite and MySQL databases
  - Connection pooling with HikariCP for optimal performance
  - Query building framework for database dialect independence
  - Repository pattern for clean data access
  - Automatic schema management and migration

- **Service Framework**:
  - Service registry for dependency management
  - Event system for cross-plugin communication
  - Type-safe API for service consumption
  - Lifecycle management for all services

- **Player Data Management**:
  - Centralized player tracking across plugins
  - Player metadata and preferences system
  - UUID-based identification with username history
  - Cross-server synchronization capability

- **Configuration Management**:
  - Centralized configuration storage
  - Versioned configuration files
  - Automatic migration and validation
  - Type-safe configuration access

- **Plugin API**:
  - Clean interface-based API design
  - Comprehensive documentation
  - Versioned API for compatibility
  - Event-driven integration points

- **Development Tools**:
  - REST API for external access
  - Metrics and monitoring
  - Diagnostic tools
  - Performance tracking

## Integration

### For Server Administrators

1. Install RVNKCore plugin before any dependent plugins
2. Configure database settings in `config.yml`
3. Adjust performance settings as needed
4. Restart your server
5. Install dependent plugins (RVNKTools, RVNKLore, etc.)

### For Plugin Developers

```java
// Get RVNKCore API instance
Plugin corePlugin = getServer().getPluginManager().getPlugin("RVNKCore");
if (corePlugin != null && corePlugin instanceof RVNKCore) {
    RVNKCoreAPI api = ((RVNKCore) corePlugin).getAPI();
    
    // Register your plugin
    api.registerPlugin(this, "my-plugin", "1.0.0");
    
    // Access services
    IPlayerService playerService = api.getService(IPlayerService.class);
    playerService.getPlayer(uuid).thenAccept(player -> {
        // Use player data
    });
}
```

## Configuration

RVNKCore uses several configuration files:

- `config.yml` - Main configuration file
- `database.properties` - Database connection settings
- `permissions.yml` - Permission configurations
- `api-access.yml` - API access control settings

### Database Configuration Example

```yaml
database:
  type: mysql  # sqlite or mysql
  mysql:
    host: localhost
    port: 3306
    database: rvnkcore
    username: minecraft
    password: secure_password
    pool-size: 10
  sqlite:
    file: rvnkcore.db
    wal: true
  prefix: rvnk_  # Table prefix
  backup:
    enabled: true
    interval: 86400  # Daily backups in seconds
```

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/rvnkcore info` | Shows plugin information | `rvnkcore.command.info` |
| `/rvnkcore reload` | Reloads configuration | `rvnkcore.command.reload` |
| `/rvnkcore services` | Lists registered services | `rvnkcore.command.services` |
| `/rvnkcore plugins` | Lists registered plugins | `rvnkcore.command.plugins` |
| `/rvnkcore metrics` | Shows performance metrics | `rvnkcore.command.metrics` |

## Permissions

- `rvnkcore.admin` - All administrative permissions
- `rvnkcore.user` - Basic user permissions
- `rvnkcore.api.access` - API access permission
- `rvnkcore.database.manage` - Database management permissions

## Developer Documentation

For detailed development guides, see:
- [API Documentation](api-documentation.md)
- [Service Development Guide](service-development.md)
- [Database Architecture](database-architecture.md)
- [Migration Guide](migration-guide.md)

## Status

RVNKCore is currently in active development as part of the RVNKTools ecosystem transition to a modular architecture. See the [Roadmap](rvnkcore-roadmap.md) for development status and plans.

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

Please ensure your code follows our [coding standards](rvnkcore-copilot-instructions.md) and includes appropriate tests.

## License

RVNKCore is licensed under the MIT License. See the [LICENSE](LICENSE) file for more information.

## Contact

For any questions or issues, please contact [fourz](https://github.com/fourz) or open an issue on GitHub.
