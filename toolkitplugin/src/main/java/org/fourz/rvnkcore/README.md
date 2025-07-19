# RVNKCore Implementation

This directory contains the RVNKCore implementation that is being extracted from RVNKTools to provide a centralized data and service layer for the RVNK plugin ecosystem.

## Directory Structure

```
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

### Phase 1: Foundation (Q3 2025) - **IN PROGRESS**

#### Completed
- [x] Directory structure creation
- [x] Basic RVNKCore class
- [x] Exception hierarchy foundation
- [x] ConnectionProvider interface
- [x] QueryBuilder interface
- [x] ServiceRegistry interface
- [x] Enhanced PlayerDTO with comprehensive tracking (seen, name history, location, rank/groups)
- [x] IPlayerService interface with async operations
- [x] BaseRepository abstract class with CRUD operations
- [x] PlayerRepository implementation
- [x] PlayerService implementation with full business logic
- [x] SQLiteConnectionProvider with schema auto-creation
- [x] BasicSQLQueryBuilder implementation

#### In Progress
- [ ] ServiceRegistry implementation
- [ ] MySQL ConnectionProvider implementation with HikariCP
- [ ] Schema management and migrations
- [ ] Performance monitoring integration

#### Planned
- [ ] Configuration management
- [ ] Event system
- [ ] Basic command support
- [ ] Integration testing framework

## Development Guidelines

1. **Follow the RVNKCore Copilot Instructions** in `docs/plans/rvnkcore/rvnkcore-copilot-instructions.md`
2. **Use LogManager** for all logging operations
3. **Implement async patterns** with CompletableFuture for database operations
4. **Keep commands simple** - no complex CommandManager framework needed
5. **Document all public APIs** with comprehensive JavaDoc
6. **Follow SOLID principles** for all component design

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
