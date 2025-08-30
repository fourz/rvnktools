# RVNKCore - Centralized Data and Service Layer

RVNKCore is a foundational Minecraft server plugin that provides a centralized data access layer, service framework, and API for the RVNK plugin ecosystem. It serves as the backbone for plugins like RVNKTools, RVNKLore, RVNKQuests, and others, offering consistent data management, shared services, and cross-plugin communication.

## Architectural Standards

All RVNK plugins using RVNKCore follow a consistent architecture:

```text
Plugin Root/
├── api/                    # Public plugin APIs
├── service/               # Business logic services  
├── repository/            # Data access layer
├── command/              # Command implementations
├── listener/             # Event listeners
├── config/               # Configuration management
└── integration/          # Third-party integrations
```

### Architectural Principles

- **Service Registry Pattern**: All services obtained through centralized ServiceRegistry
- **Repository Pattern**: Clean separation between business logic and data access
- **Async-First Operations**: Database operations use CompletableFuture to prevent blocking
- **Dependency Injection**: Services automatically resolved through RVNKCore infrastructure
- **Cross-Plugin Compatibility**: Shared interfaces enable plugin ecosystem integration

## Features

- **Advanced Database Abstraction Layer**:
  - Production-ready SQLite provider with connection pooling and WAL mode
  - MySQL ConnectionProvider framework (HikariCP integration in progress)
  - Sophisticated query building framework with DDL/DML support
  - Repository pattern with BaseRepository and specialized implementations
  - Comprehensive schema management with automatic versioning and migration
  - Async operations with CompletableFuture integration

- **Enterprise Service Framework**:
  - Service registry with dependency injection and lifecycle management
  - Circular dependency detection and resolution
  - ServiceException hierarchy for standardized error handling
  - Real-time event system for cross-plugin communication
  - Service health monitoring and restart capabilities

- **Comprehensive Player Data Management**:
  - Advanced player tracking with seen status, name history, location tracking
  - Permission system integration with group tracking and inheritance
  - PlayerDTO with comprehensive activity and world-specific data
  - Event-driven updates via PlayerTrackingListener
  - Advanced caching with TTL and memory optimization

- **Production-Ready REST API Infrastructure**:
  - Jetty server with SSL/HTTPS support and certificate management
  - API key authentication with rate limiting and request monitoring
  - 12+ operational REST endpoints including full player CRUD operations
  - Async request processing with comprehensive error handling
  - CORS policy management and security layers

- **Sophisticated Configuration Management**:
  - YAML configuration with validation and type safety (ApiConfig - 242 lines)
  - Hot-reload capabilities without server restart
  - Environment-specific configuration profiles
  - Configuration migration and version compatibility

- **Advanced Development Tools**:
  - Complete REST API with 12+ endpoints for external integration
  - Real-time performance monitoring and health checks
  - Comprehensive diagnostic capabilities
  - Request/response validation and monitoring

## Integration

### For Server Administrators

1. Install RVNKCore plugin before any dependent plugins
2. Configure database settings in `config.yml`
3. Adjust performance settings as needed
4. Restart your server
5. Install dependent plugins (RVNKTools, RVNKLore, etc.)

### For Plugin Developers

```java
// Get RVNKCore services through ServiceRegistry
ServiceRegistry serviceRegistry = RVNKCoreBootstrap.getServiceRegistry();

// Access player services
PlayerService playerService = serviceRegistry.getService(PlayerService.class);
playerService.getPlayerAsync(uuid).thenAccept(playerDto -> {
    // Use comprehensive player data with tracking info
    if (playerDto != null) {
        String displayName = playerDto.getDisplayName();
        Timestamp lastSeen = playerDto.getLastSeen();
        // Access location history, permission data, etc.
    }
});

// Access database services
DatabaseService databaseService = serviceRegistry.getService(DatabaseService.class);
PlayerRepository playerRepo = serviceRegistry.getService(PlayerRepository.class);

// Use async operations with CompletableFuture
playerRepo.findByUuidAsync(uuid).thenAccept(player -> {
    // Database operations are non-blocking
});
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

**MAJOR IMPLEMENTATION UPDATE**: RVNKCore Phase 1 implementation has **significantly exceeded all expectations**. What was originally planned as a basic foundation has evolved into a comprehensive, production-ready ecosystem with advanced features including a complete REST API infrastructure.

**Current Status**: ✅ **Phase 1 COMPLETED (95%)** - August 22, 2025

### Implementation Highlights

- **Core Foundation Complete**: Service framework, database layer, and player services are fully operational
- **REST API Infrastructure**: Production-ready with 12+ endpoints, SSL/HTTPS, and authentication 
- **Database Layer**: SQLite provider operational with connection pooling and schema management
- **Player Services**: Comprehensive tracking with event-driven updates and caching
- **Configuration System**: Advanced YAML configuration with hot-reload capabilities

### Next Priority Items

1. **MySQL ConnectionProvider Implementation** - Complete HikariCP integration
2. **Documentation Gap Resolution** - Create missing example code files
3. **Testing Framework Enhancement** - Comprehensive integration testing

RVNKCore is currently embedded within RVNKTools during the development phase, with extraction to separate plugin planned for Phase 3. See the [Roadmap](rvnkcore-roadmap.md) for detailed development status, timelines, and implementation priorities.

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
