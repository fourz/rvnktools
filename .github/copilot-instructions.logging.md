# RVNK Logging and Debug Standards

## Logging

### LogManager Standard

- Use `LogManager` for all info, warning, and error logging in plugin code
- Declare as `private final LogManager logger;` and initialize with `LogManager.getInstance(plugin);`
- Use `logger.info()`, `logger.warning()`, `logger.error(message, exception)` for all logging
- Do not use `System.out.println()` or direct logger calls
- Reserve `Debug` class for debug-level or trace logging only

### Console and Debug Messages

- Use the designated logging system for all console output
- Create clear, concise messages that explain the context
- For errors, include actionable information to help troubleshoot
- Use appropriate log levels (INFO, WARNING, ERROR, DEBUG)

*See examples: [LogManager Usage](copilot-instructions.examples.md#logmanager-usage)*
