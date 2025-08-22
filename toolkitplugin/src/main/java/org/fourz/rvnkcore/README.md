# RVNKCore Implementation

This directory contains the RVNKCore implementation that is being extracted from RVNKTools to provide a centralized data and service layer for the RVNK plugin ecosystem.

## Plugin Architecture Standards

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

## Directory Structure

```text
org.fourz.rvnkcore/
├── api/                    # Public API interfaces
│   ├── service/            # Service interfaces
│   ├── model/              # Data transfer objects
│   ├── event/              # Event interfaces
│   └── exception/          # API exceptions
├── database/               # Database implementation
│   ├── connection/         # Connection management
│   ├── query/              # Query building
│   ├── repository/         # Data repositories
│   └── schema/             # Schema management
├── service/                # Service implementations
│   ├── registry/           # Service registry
│   ├── player/             # Player services
│   ├── config/             # Configuration services
│   └── ...                 # Other services
├── util/                   # Utility classes
│   ├── log/                # Logging framework (shared with RVNKTools)
│   ├── concurrent/         # Concurrency utilities
│   └── ...                 # Other utilities
└── RVNKCore.java           # Main plugin class
```

## Implementation Status

**Current Status**: RVNKCore Phase 1 foundation is **99% complete** with all core infrastructure operational. See the main project [ROADMAP.md](../../../../../../../ROADMAP.md) for detailed implementation status, timelines, and development priorities.

### Core Foundation Complete ✅

- Complete service framework with dependency injection and lifecycle management
- Database layer with MySQL/SQLite support and connection pooling  
- Player services with comprehensive tracking and per-world data
- REST API infrastructure with HTTPS, authentication, and 20+ endpoints
- Announcement system infrastructure ready for migration implementation

### Development Guidelines

1. **Follow the Plugin Architecture Standards** described above for all RVNKCore implementations
2. **Use the RVNKCore Copilot Instructions** in `.github/copilot-instructions.md`
3. **Use LogManager** for all logging operations
4. **Implement async patterns** with CompletableFuture for database operations
5. **Keep commands simple** - no complex CommandManager framework needed
6. **Document all public APIs** with comprehensive JavaDoc
7. **Follow SOLID principles** for all component design
8. **Use ServiceRegistry pattern** for dependency injection across services

## Integration with RVNKTools

During the development phase, RVNKCore classes will coexist with RVNKTools classes in the same JAR. The extraction will happen gradually:

1. **Phase 1**: Implement core infrastructure within RVNKTools
2. **Phase 2**: Migrate RVNKTools features to use RVNKCore services
3. **Phase 3**: Extract RVNKCore into separate plugin

## Next Steps

1. Implement SQLiteConnectionProvider
2. Implement basic QueryBuilder
3. Create ServiceRegistry implementation
4. Set up basic schema management
5. Create player service implementation
6. Integrate with existing RVNKTools logging system

## Testing

All RVNKCore components should be thoroughly tested:

- Unit tests for individual components
- Integration tests for database operations
- End-to-end tests for service interactions
- Performance tests for database operations

## Documentation

- API documentation is generated from JavaDoc comments
- Implementation guides are in `docs/plans/rvnkcore/`
- Examples and usage patterns will be documented as development progresses
