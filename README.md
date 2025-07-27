# RVNKTools Minecraft Plugin

RVNKTools is a comprehensive Minecraft server plugin that provides a wide array of features to enhance server administration. It includes tools for managing announcements, permissions, events, and much more, tailored for ease of use and scalability.

**Note**: RVNKTools is currently undergoing a major architectural refactor to extract core functionality into RVNKCore, a centralized data and service layer for the RVNK plugin ecosystem. This refactor is happening on the `derek/dev-core` branch and will provide better modularity, shared services, and cross-plugin integration capabilities.

## RVNKCore Data Layer Abstraction

The core innovation of this refactor is the extraction of database operations, service management, and API interfaces into a reusable framework that can be shared across all RVNK plugins. This provides:

### Centralized Database Management
- **Unified Connection Providers**: Abstracted database connections supporting SQLite and MySQL
- **Query Builder Framework**: Database-agnostic query construction with dialect-specific implementations
- **Repository Pattern**: Clean separation between business logic and data access
- **Asynchronous Operations**: All database operations use CompletableFuture to prevent blocking the main thread

### Service Framework Architecture
- **Service Registry**: Centralized discovery and dependency injection for all services
- **Event-Driven Communication**: Cross-plugin messaging and state synchronization
- **API Versioning**: Backward-compatible interfaces for third-party integration
- **Performance Monitoring**: Built-in logging and metrics collection

### Plugin Ecosystem Benefits
- **Shared Data Layer**: All RVNK plugins can access common player data, permissions, and configuration
- **Consistent APIs**: Standardized interfaces across all plugins in the ecosystem
- **Reduced Complexity**: Plugins focus on features rather than infrastructure
- **Better Testing**: Mockable services and repositories for comprehensive testing
- **Web Integration Strategy**: REST API framework for web-enabled plugins (Shops, Tools, Lore) while maintaining internal-only access for game mechanics (Quests, Worlds)

## Features

- **Announcements Management**:
  - Schedule, manage, and remove announcements with YAML or database integration
  - Support for recurring announcements and categorized types
  - Player preference management for announcement visibility

- **Permissions Handling**:
  - Tools to efficiently manage player permissions and access control
  - Integration with LuckPerms for extended permission functionality

- **Admin Utilities**:
  - Hat Manager for cosmetic item management
  - Link Maker for creating and sharing clickable links in-game
  - Cycle Commands for routine server maintenance tasks
  - Join Message customization for player welcome messages
  - **DH Log Filter**: Advanced console log filtering to reduce spam from Distant Horizons server plugin

- **DH Log Filter System**:
  - Configurable log level filtering (DEBUG, INFO, WARN, ERROR)
  - Keyword-based message filtering with regex support
  - Rate limiting for repetitive messages (configurable time windows)
  - Real-time statistics and performance monitoring
  - Administrative commands for filter management (`/dhfilter`)
  - Asynchronous configuration loading and management

- **Integration Support**:
  - Seamless integration with PlaceholderAPI for dynamic messages and content
  - Multiverse support for multi-world environments
  - Vault integration for economy and permissions

- **Database Compatibility**:
  - Supports SQLite for lightweight installations
  - MySQL support (planned with RVNKCore integration)
  - Connection pooling with HikariCP for optimal performance
  - Configurable through application.properties

## RVNKCore Integration (In Development)

RVNKTools is being refactored to utilize RVNKCore, a centralized data and service layer that will provide:

- **Centralized Database Management**: Shared database layer across all RVNK plugins
- **Service Framework**: Common services for player data, permissions, and configuration
- **Cross-Plugin Communication**: Event system for plugin interactions
- **API Framework**: Clean, versioned APIs for third-party integration
- **Performance Optimization**: Connection pooling, caching, and async operations

### Implementation Architecture

The RVNKCore framework follows a simplified, performance-focused architecture:

```text
┌─────────────────────────┐
│      RVNK Plugins       │
│  (Tools, Lore, Quests)  │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│    Service Layer        │
│  (Async Operations)     │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│   Repository Layer      │────▶│    Database Layer       │
│  (CompletableFuture)    │     │  (Connection Pooling)   │
└─────────────────────────┘     └─────────────────────────┘
```

### Key Design Principles

- **Async-First**: All database operations use CompletableFuture to prevent main thread blocking
- **Simple & Clean**: No unnecessary abstractions or complex frameworks
- **Performance-Focused**: Connection pooling, caching, and performance monitoring built-in
- **Plugin Ecosystem**: Designed for multiple RVNK plugins to share data and services
- **Easy Migration**: Gradual transition from existing systems with backward compatibility

### Web Integration Architecture

RVNKCore includes a comprehensive web integration strategy that categorizes plugins based on their external access requirements:

#### Web-Enabled Plugins (Full REST API)

- **RVNKShops**: Product catalog management, pricing, transaction history
- **RVNKTools**: Announcement management, server statistics, admin dashboards
- **RVNKLore**: Item galleries, player collections, community showcases

#### Internal-Only Plugins (Java API Only)

- **RVNKQuests**: Quest progress tracking, internal game mechanics
- **RVNKWorlds**: World management, server operations

This architecture ensures that plugins requiring external web access receive full REST API implementations while maintaining secure, performance-optimized internal access for game mechanics.

### Current Implementation Status

See the [ROADMAP.md](ROADMAP.md) for detailed implementation status and timelines.

## Installation

1. Download the latest version of `rvnktools.jar` from the [Releases](https://github.com/fourz/rvnktools/releases) page
2. Place the JAR file in your server's `plugins` directory
3. Restart your server
4. Configuration files will be generated in the `plugins/RVNKTools` directory

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/announce` | Manage server announcements | `rvnktools.announce` |
| `/announce add` | Create a new announcement | `rvnktools.announce.add` |
| `/announce remove` | Remove an announcement | `rvnktools.announce.remove` |
| `/announce list` | List all announcements | `rvnktools.announce.list` |
| `/hat` | Set the item in your hand as a hat | `rvnktools.hat` |
| `/link` | Manage clickable links | `rvnktools.link` |

## Configuration

RVNKTools uses several configuration files:

- `config.yml` - Main plugin configuration
- `announcements.yml` - Announcement definitions and schedules
- `links.yml` - Stored links for the link maker
- `joinmessages.yml` - Custom join message configuration
- `application.properties` - Database and advanced settings

## Development

### Building from Source

1. Clone the repository: `git clone https://github.com/fourz/rvnktools.git`
2. Navigate to the project directory: `cd rvnktools/toolkitplugin`
3. Build with Maven: `mvn clean package`
4. Find the compiled JAR in the `target` directory

### VS Code Tasks

Several VS Code tasks are available for development:

- **Build Plugin**: Compiles and packages the plugin
- **Copy to Server**: Copies the built plugin to your development server
- **Restart Server**: Restarts the development server with the new build
- **Reload Server**: Reloads plugins without a full server restart
- **Clean&Restart Server**: Cleans up and restarts the server

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a new branch: `git checkout -b feature-branch`
3. Make your changes and test thoroughly
4. Submit a pull request with a clear description of the changes

## License

RVNKTools is licensed under the MIT License. See the [LICENSE](LICENSE) file for more information.

## Contact

For any questions or issues, please contact [fourz](https://github.com/fourz) or open an issue on GitHub.
