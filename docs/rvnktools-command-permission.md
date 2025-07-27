# RVNKTools Command & Permission System

## Overview

The Command & Permission System provides a structured approach to managing commands, subcommands, and permissions within the RVNKTools plugin. This system ensures consistent command behavior, simplified permission management, and improved user experience.

## Permission Structure

### Core Principles

- **Hierarchical Design**: Permissions follow a hierarchical structure matching command structure
- **Granular Control**: Fine-grained permissions allow for precise access control
- **Wildcard Support**: Support for wildcard permissions (using `*`)
- **Default Configuration**: Sensible default permissions for typical server roles
- **Documentation**: Each permission is fully documented

### Permission Naming Convention

All permissions follow a consistent naming pattern:

```yaml
rvnktools.<module>.<action>[.<sub-action>]
```

Examples:

- `rvnktools.announce.add` - Permission to add announcements
- `rvnktools.announce.remove` - Permission to remove announcements
- `rvnktools.admin.reload` - Permission to reload the plugin
- `rvnktools.hat.use` - Permission to use the hat command

### Wildcard Permissions

Wildcard permissions grant access to all subpermissions:

- `rvnktools.announce.*` - All announcement permissions
- `rvnktools.admin.*` - All administrative permissions
- `rvnktools.*` - All plugin permissions (super admin)

## Standard Permission Groups

### Default Permission Sets

```yaml
# Player permissions
rvnktools.player:
  - rvnktools.hat.use
  - rvnktools.link.view

# Moderator permissions
rvnktools.moderator:
  - rvnktools.player
  - rvnktools.announce.view
  - rvnktools.cyclecommands.use

# Admin permissions
rvnktools.admin:
  - rvnktools.moderator
  - rvnktools.announce.*
  - rvnktools.cyclecommands.*
  - rvnktools.admin.reload
```

## Command Registration

### Command Registration in plugin.yml

```yaml
commands:
  rvnktools:
    description: Main command for RVNKTools
    usage: /rvnktools [help|reload]
    aliases: [tool]
    permission: rvnktools.command
  announce:
    description: Manage server announcements
    usage: /announce [add|remove|list]
    permission: rvnktools.announce
  hat:
    description: Wear any item as a hat
    usage: /hat
    permission: rvnktools.hat.use
```

### Permission Registration in plugin.yml

```yaml
permissions:
  # Main plugin permission nodes
  rvnktools.*:
    description: Grants all permissions for RVNKTools
    default: op
    children:
      rvnktools.admin.*: true
      
  rvnktools.admin.*:
    description: Grants all administrative permissions
    default: op
    children:
      rvnktools.admin.reload: true
      
  rvnktools.announce.*:
    description: Grants all announcement permissions
    default: op
    children:
      rvnktools.announce.add: true
      rvnktools.announce.remove: true
      rvnktools.announce.list: true
      rvnktools.announce.view: true
      
  # Individual permissions
  rvnktools.admin.reload:
    description: Allows reloading the plugin configuration
    default: op
    
  rvnktools.announce.add:
    description: Allows adding new announcements
    default: op
    
  rvnktools.announce.remove:
    description: Allows removing announcements
    default: op
    
  rvnktools.announce.list:
    description: Allows listing all announcements
    default: op
    
  rvnktools.announce.view:
    description: Allows viewing announcements (doesn't disable seeing them)
    default: true
    
  rvnktools.hat.use:
    description: Allows using the hat command
    default: true
```

## Implementation

### Permission Checking

```java
/**
 * Checks if the sender has the required permission
 * 
 * @param sender The command sender
 * @param permission The permission to check
 * @return true if the sender has permission
 */
public boolean hasPermission(CommandSender sender, String permission) {
    if (permission == null || permission.isEmpty()) {
        return true; // No permission required
    }
    
    // Check for direct permission
    if (sender.hasPermission(permission)) {
        return true;
    }
    
    // Check for wildcard permissions
    String[] parts = permission.split("\\.");
    StringBuilder wildcard = new StringBuilder();
    
    for (int i = 0; i < parts.length - 1; i++) {
        wildcard.append(parts[i]).append(".");
        
        if (sender.hasPermission(wildcard.toString() + "*")) {
            return true;
        }
    }
    
    return false;
}
```

### Permission Response

```java
/**
 * Sends a no permission message to the sender
 * 
 * @param sender The command sender
 * @param permission The permission that was missing
 */
public void sendNoPermissionMessage(CommandSender sender, String permission) {
    sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
    
    // Log attempt for audit purposes
    if (sender instanceof Player) {
        Player player = (Player) sender;
        logger.warning("Player " + player.getName() + " attempted to use command requiring " + 
                      permission + " but was denied");
    }
}
```

## Command Structure Integration

### Base Command Class

```java
public abstract class BaseCommand implements CommandExecutor, TabCompleter {
    protected final RVNKTools plugin;
    protected final String name;
    protected final String permission;
    protected final RVNKLogger logger;
    
    public BaseCommand(RVNKTools plugin, String name, String permission) {
        this.plugin = plugin;
        this.name = name;
        this.permission = permission;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Log command usage
        logger.debug(sender.getName() + " executed /" + label + " " + String.join(" ", args));
        
        // Check permission
        if (!plugin.getPermissionManager().hasPermission(sender, permission)) {
            plugin.getPermissionManager().sendNoPermissionMessage(sender, permission);
            return true;
        }
        
        // Execute command
        return execute(sender, args);
    }
    
    /**
     * Execute the command logic
     * 
     * @param sender The command sender
     * @param args Command arguments
     * @return true if the command was handled
     */
    protected abstract boolean execute(CommandSender sender, String[] args);
    
    /**
     * Get command usage help
     * 
     * @return The command usage string
     */
    public abstract String getUsage();
    
    /**
     * Send help information to the sender
     * 
     * @param sender The command sender
     */
    public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + name.toUpperCase() + " Command ===");
        sender.sendMessage(ChatColor.YELLOW + "Usage: " + getUsage());
    }
}
```

### Subcommand Manager

```java
public class SubcommandManager {
    private final Map<String, BaseCommand> subcommands = new HashMap<>();
    private final BaseCommand parentCommand;
    
    public SubcommandManager(BaseCommand parentCommand) {
        this.parentCommand = parentCommand;
    }
    
    /**
     * Register a subcommand
     * 
     * @param subcommand The subcommand to register
     */
    public void registerSubcommand(BaseCommand subcommand) {
        subcommands.put(subcommand.getName().toLowerCase(), subcommand);
    }
    
    /**
     * Get a subcommand by name
     * 
     * @param name The name of the subcommand
     * @return The subcommand, or null if not found
     */
    public BaseCommand getSubcommand(String name) {
        return subcommands.get(name.toLowerCase());
    }
    
    /**
     * Get all registered subcommands
     * 
     * @return A collection of subcommands
     */
    public Collection<BaseCommand> getSubcommands() {
        return subcommands.values();
    }
    
    /**
     * Process a command with potential subcommands
     * 
     * @param sender The command sender
     * @param args Command arguments
     * @return true if the command was handled
     */
    public boolean processCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            parentCommand.sendHelp(sender);
            return true;
        }
        
        BaseCommand subcommand = getSubcommand(args[0]);
        if (subcommand == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + args[0]);
            parentCommand.sendHelp(sender);
            return true;
        }
        
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subcommand.onCommand(sender, null, args[0], subArgs);
    }
    
    /**
     * Provide tab completion for subcommands
     * 
     * @param sender The command sender
     * @param args Command arguments
     * @return List of tab completions
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            
            for (BaseCommand subcommand : subcommands.values()) {
                if (subcommand.getName().toLowerCase().startsWith(partial)) {
                    if (subcommand.hasPermission(sender)) {
                        completions.add(subcommand.getName());
                    }
                }
            }
            
            return completions;
        } else if (args.length > 1) {
            BaseCommand subcommand = getSubcommand(args[0]);
            if (subcommand != null) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subcommand.onTabComplete(sender, null, args[0], subArgs);
            }
        }
        
        return Collections.emptyList();
    }
}
```

## Permission Manager

```java
public class PermissionManager {
    private final RVNKTools plugin;
    private final RVNKLogger logger;
    private final Map<String, Set<String>> permissionGroups = new HashMap<>();
    
    public PermissionManager(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        loadPermissionGroups();
    }
    
    /**
     * Load permission groups from configuration
     */
    private void loadPermissionGroups() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("permission-groups");
        if (section == null) return;
        
        for (String groupName : section.getKeys(false)) {
            Set<String> permissions = new HashSet<>();
            List<String> permList = section.getStringList(groupName);
            
            for (String perm : permList) {
                if (perm.startsWith("group:")) {
                    String referencedGroup = perm.substring(6);
                    Set<String> groupPerms = permissionGroups.get(referencedGroup);
                    if (groupPerms != null) {
                        permissions.addAll(groupPerms);
                    }
                } else {
                    permissions.add(perm);
                }
            }
            
            permissionGroups.put(groupName, permissions);
            logger.debug("Loaded permission group: " + groupName + " with " + permissions.size() + " permissions");
        }
    }
    
    /**
     * Check if a sender has a specific permission
     */
    public boolean hasPermission(CommandSender sender, String permission) {
        // Implementation as shown earlier
    }
    
    /**
     * Send no permission message
     */
    public void sendNoPermissionMessage(CommandSender sender, String permission) {
        // Implementation as shown earlier
    }
    
    /**
     * Register a permission with the server
     * 
     * @param permission The permission to register
     * @param description Description of the permission
     * @param defaultValue Default permission value
     */
    public void registerPermission(String permission, String description, PermissionDefault defaultValue) {
        try {
            PermissionDefault existing = plugin.getServer().getPluginManager()
                .getPermission(permission).getDefault();
            
            if (existing != defaultValue) {
                logger.warning("Permission " + permission + " already exists with different default value");
            }
        } catch (Exception e) {
            Permission perm = new Permission(permission, description, defaultValue);
            plugin.getServer().getPluginManager().addPermission(perm);
            logger.debug("Registered permission: " + permission);
        }
    }
}
```

## Best Practices

1. **Always Check Permissions**: Every command should check permissions before execution
2. **Descriptive Permission Names**: Use clear, descriptive permission names
3. **Granular Control**: Create permissions for specific actions, not broad categories
4. **Default Values**: Set reasonable default permission values
5. **Document Permissions**: Document all permissions in plugin.yml and user guides
6. **Consistent Structure**: Follow the established permission naming convention
7. **Test Coverage**: Test all permission combinations during development

## Migration Path

When migrating from the old permission system:

1. Create a mapping of old to new permissions
2. Implement temporary compatibility layer
3. Alert server admins about the permission changes
4. Provide migration scripts for popular permission plugins
5. Deprecate old permissions with warnings before removing

## Example Configuration

```yaml
# config.yml
permission-groups:
  player:
    - rvnktools.hat.use
    - rvnktools.link.view
  
  moderator:
    - group:player
    - rvnktools.announce.view
    - rvnktools.cyclecommands.use
  
  admin:
    - group:moderator
    - rvnktools.announce.*
    - rvnktools.cyclecommands.*
    - rvnktools.admin.reload

# Default permission assignments
permissions:
  default:
    - rvnktools.hat.use
    - rvnktools.link.view
  
  # Special overrides for specific groups
  member:
    - group:player
  
  staff:
    - group:moderator
  
  administrator:
    - group:admin
```
