# RVNK Command Framework Guidelines

## Command Framework Guidelines

Follow the CommandManager framework for all commands:

1. **Extend BaseCommand** (part of CommandManager framework) for new commands
2. **Register through CommandManager**: `commandManager.registerCommand(new MyCommand(plugin));`
3. **Use subcommands** where appropriate: `registerSubCommand("subcommand", new MySubCommand(plugin));`
4. **Implement tab completion** with `getMatchingSubCommands(sender, args[0])`

*See examples: [Command Framework Examples](copilot-instructions.examples.md#command-framework-examples)*

### Command Framework Integration

- Validate synchronously (permissions, args, format)
- Use async for database/API operations
- Provide immediate feedback to users
- Handle async results with proper error messages

**Important**: Command responses must be immediate to provide user feedback, but long-running database/API operations within commands should be wrapped in `CompletableFuture` to avoid blocking the main thread.

*See examples: [Command Framework Integration](copilot-instructions.examples.md#command-framework-integration)*
