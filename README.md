# RVNKTools Minecraft Plugin

RVNKTools is a comprehensive Minecraft server plugin that provides a wide array of features to enhance server administration. It includes tools for managing announcements, permissions, events, and much more, tailored for ease of use and scalability.

## Features

- **Announcements Management**: 
  - Schedule, edit, and remove announcements using YAML or database integration.
  - Flexible recurrence and type categorization for announcements.
- **Permissions Handling**: 
  - Advanced tools to manage player permissions.
- **Integration Support**: 
  - PlaceholderAPI integration for dynamic message handling.
- **Extensible Commands**: 
  - Rich subcommand system for plugin interaction.
- **Custom Features**: 
  - Includes features such as a hat manager, link maker, and more.

## Installation

1. Download the latest version of `rvnktools.jar` from the [Releases](#).
2. Place the JAR file in your server's `plugins` directory.
3. Restart your server.
4. Configure the plugin using the `config.yml` and `announcements.yml` files generated in the `plugins/RVNKTools` directory.

## Configuration

### announcements.yml
The `announcements.yml` file allows you to define and customize announcements in YAML format:

```yaml
announcements:
  - id: "event_reminder"
    text: "Don't forget to join the upcoming event!"
    type: "news"
    recurrence: "3h"
    imported: false
```
Commands

    /announce
        /announce list - List all announcements.
        /announce add <id> - Add a new announcement.
        /announce delete <id> - Delete an announcement by ID.
        /announce reload - Reload the announcements configuration.

Permissions

    rvnktools.announce.manage - Allows managing announcements.
    rvnktools.announce.view - Allows viewing announcements.

Development
Building from Source

    Clone the repository:

    git clone https://github.com/fourz/rvnktools.git

Navigate to the project directory:

cd rvnktools

Build the plugin using Maven:

mvn clean package

    The JAR file will be available in the target directory.

Contributions

Contributions are welcome! Please follow these steps:

    Fork the repository.
    Create a new branch for your feature/bugfix.
    Commit your changes.
    Submit a pull request with a clear description of the changes.

Known Issues

    YAML and database sync might result in discrepancies if not properly configured.
    Ensure unique id values for announcements to avoid conflicts.
