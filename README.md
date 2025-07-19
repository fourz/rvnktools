# RVNKTools Minecraft Plugin

RVNKTools is a comprehensive Minecraft server plugin that provides a wide array of features to enhance server administration. It includes tools for managing announcements, permissions, events, and much more, tailored for ease of use and scalability.

**Note**: RVNKTools is currently undergoing a major architectural refactor to extract core functionality into RVNKCore, a centralized data and service layer for the RVNK plugin ecosystem. This refactor is happening on the `derek/dev-core` branch and will provide better modularity, shared services, and cross-plugin integration capabilities.

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
