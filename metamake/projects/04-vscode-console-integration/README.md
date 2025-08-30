# VS Code Console Integration for Minecraft Development

## Overview

This project focuses on creating seamless VS Code integration with Minecraft server console access, enabling real-time debugging, command execution, and log monitoring directly from the development environment.

## Problem Statement

Currently, developers must switch between VS Code and external console windows to:
- Monitor server logs and debug output
- Execute commands for testing plugin functionality
- Restart/reload plugins during development
- Monitor performance and error messages

This context switching breaks development flow and reduces productivity.

## Objectives

### Primary Goals
1. **Direct Console Access**: Integrated terminal panel showing real-time server console output
2. **Command Execution**: Send commands directly to server from VS Code
3. **Log Filtering**: Filter console output by plugin, log level, or custom patterns
4. **Development Workflow**: Seamless build-deploy-test cycles with immediate feedback

### Secondary Goals
1. **Performance Monitoring**: Real-time server performance metrics in VS Code
2. **Player Event Monitoring**: Track player actions during plugin testing
3. **Error Highlighting**: Automatic error detection with stack trace navigation
4. **Plugin Hot-Reload**: Instant plugin reloading with status feedback

## Current Workflow Pain Points

### Manual Steps Required
1. Build plugin in VS Code
2. Switch to PowerShell/Command Prompt
3. Copy JAR to server directory
4. Restart or reload server
5. Monitor console in separate window
6. Execute test commands manually
7. Switch back to VS Code for code changes
8. Repeat entire cycle

### Time Waste
- **Context Switching**: 10-15 seconds per cycle
- **Manual Monitoring**: Constantly watching separate console window
- **Error Correlation**: Matching errors in console to code locations
- **Testing Feedback**: Delayed feedback on functionality changes

## Solution Architecture

### Integration Options

#### Option 1: MCSS API Integration (Recommended)
**Leverage existing MCSS (MC Server Soft) REST API for console access**

**Advantages:**
- Already implemented and working in RVNK environment
- RESTful API for programmatic access
- Real-time console log streaming
- Command execution capabilities
- Performance monitoring endpoints

**Implementation:**
- VS Code extension connecting to MCSS API
- WebSocket or polling for real-time updates
- Authentication using existing API keys
- Filter and search capabilities

#### Option 2: Direct Server Integration
**Custom VS Code extension with direct server communication**

**Advantages:**
- No dependency on external services
- Potentially lower latency
- Custom protocol optimization

**Disadvantages:**
- Requires custom server-side implementation
- More complex authentication
- Network configuration complexity

### Proposed Implementation: MCSS API Integration

#### VS Code Extension Components

```typescript
// Extension structure
vscode-minecraft-console/
├── src/
│   ├── extension.ts          # Main extension entry point
│   ├── mcssClient.ts         # MCSS API client
│   ├── consoleProvider.ts    # Console output provider
│   ├── commandProvider.ts    # Command execution provider
│   └── logFilter.ts          # Log filtering and search
├── views/
│   ├── console.html          # Console view panel
│   └── performance.html      # Performance monitoring panel
└── package.json             # Extension manifest
```

#### Core Features

1. **Console Panel**
   ```typescript
   class MinecraftConsoleProvider implements vscode.WebviewViewProvider {
     // Real-time console output
     // Command input field
     // Log filtering controls
     // Auto-scroll and search
   }
   ```

2. **Command Execution**
   ```typescript
   class CommandProvider {
     async executeCommand(command: string): Promise<CommandResult> {
       // Send command via MCSS API
       // Return execution result and output
     }
   }
   ```

3. **Log Filtering**
   ```typescript
   class LogFilter {
     filterByLevel(level: LogLevel): LogEntry[]
     filterByPlugin(pluginName: string): LogEntry[]
     searchLogs(pattern: string): LogEntry[]
   }
   ```

## Development Phases

### Phase 1: Basic Console Access (Week 1-2)
- [ ] Create VS Code extension project structure
- [ ] Implement MCSS API client with authentication
- [ ] Create basic console output panel
- [ ] Add command execution capability
- [ ] Test with existing MCSS setup

### Phase 2: Enhanced Features (Week 3-4)
- [ ] Add log filtering and search functionality
- [ ] Implement real-time log streaming (WebSocket/polling)
- [ ] Create performance monitoring panel
- [ ] Add error detection and highlighting
- [ ] Integrate with existing VS Code tasks

### Phase 3: Workflow Integration (Week 5-6)
- [ ] Seamless build-deploy-test workflow
- [ ] Plugin hot-reload capabilities
- [ ] Custom debugging commands
- [ ] Stack trace navigation from console errors
- [ ] Integration with VS Code debugging tools

### Phase 4: Advanced Features (Week 7-8)
- [ ] Player event monitoring
- [ ] Custom log parsers for different plugins
- [ ] Historical log search and analysis
- [ ] Performance trend visualization
- [ ] Multi-server support

## Technical Specifications

### MCSS API Integration

**Enhanced API Capabilities (Based on Documentation Analysis):**

**Authentication & Configuration:**
```typescript
interface MCSSConfig {
  baseUrl: string;          // Default: "http://localhost:25564"
  apiVersion: string;       // API v2 (current)
  apiKey: string;          // Required: MCSS-generated API key
  serverId: string;        // Required: Server UUID from MCSS
  timeout: number;         // Request timeout (default: 30000ms)
  useHttps: boolean;       // HTTPS support for production
}
```

**Core Console API Endpoints:**
```typescript
interface MCSSConsoleAPI {
  // Console access with advanced filtering
  getConsoleLogs(params: {
    AmountOfLines: number;    // -1 for all logs
    Reversed: boolean;        // Chronological order
    takeFromBeginning?: boolean;
  }): Promise<string[]>;
  
  // Command execution - single and batch
  executeCommand(command: string): Promise<void>;
  executeBatchCommands(commands: string[]): Promise<void>;
  
  // Server management actions
  executeServerAction(action: ServerAction): Promise<void>;
  // 0: Invalid, 1: Stop, 2: Start, 3: Kill, 4: Restart
  
  // Performance monitoring
  getServerStats(): Promise<ServerStats>;
  getServerInfo(): Promise<ServerInfo>;
}
```

**Advanced Features Discovered:**
- **Real-time Monitoring**: Polling-based console updates (optimized intervals)
- **Log Parsing**: Built-in timestamp, level, plugin, and thread parsing
- **Error Detection**: Automatic ERROR/WARN/EXCEPTION filtering
- **Plugin Integration**: RVNKTools-specific command testing framework
- **Performance Tracking**: Memory, CPU, player count monitoring
- **Connection Health**: Automatic retry logic with exponential backoff
- **Security**: API key-based authentication with SecretStorage integration

### VS Code Integration

**Extension Activation:**
```json
{
  "activationEvents": [
    "onLanguage:java",
    "workspaceContains:**/plugin.yml",
    "workspaceContains:**/pom.xml"
  ]
}
```

**Commands and Views:**
```json
{
  "commands": [
    {
      "command": "minecraft.console.show",
      "title": "Show Minecraft Console"
    },
    {
      "command": "minecraft.command.execute",
      "title": "Execute Minecraft Command"
    }
  ],
  "views": {
    "minecraft": [
      {
        "id": "minecraft.console",
        "name": "Server Console",
        "type": "webview"
      }
    ]
  }
}
```

## Success Metrics

### Developer Experience
- **Context Switch Reduction**: 80% fewer manual window switches
- **Feedback Speed**: Immediate console feedback within VS Code
- **Error Correlation**: Click-to-navigate from console errors to code
- **Testing Efficiency**: Automated build-deploy-test cycles

### Technical Metrics
- **Connection Reliability**: 99%+ uptime for console connection
- **Response Time**: <100ms for command execution feedback
- **Log Processing**: Handle >1000 log entries per minute
- **Memory Usage**: <50MB additional VS Code memory usage

## Risk Assessment

### Technical Risks
- **MCSS API Availability**: Dependency on external service
- **Network Connectivity**: Requires stable connection to server
- **Authentication**: API key management and security
- **Performance**: Large log volumes affecting VS Code performance

### Mitigation Strategies
- **Offline Mode**: Cache recent logs for offline viewing
- **Failover**: Direct server connection as backup
- **Rate Limiting**: Intelligent log polling to prevent overload
- **Local Storage**: Persist configuration and recent logs

## Resource Requirements

### Development Time
- **Phase 1**: 16-20 hours (basic functionality)
- **Phase 2**: 20-24 hours (enhanced features)
- **Phase 3**: 16-20 hours (workflow integration)
- **Phase 4**: 24-30 hours (advanced features)

**Total Estimate**: 76-94 hours over 8 weeks

### Technical Requirements
- TypeScript/Node.js development environment
- VS Code Extension API knowledge
- MCSS API documentation and access
- Minecraft server for testing

## Next Steps

1. **Validate MCSS API Capabilities**
   - Document available endpoints
   - Test authentication and permissions
   - Verify real-time log access

2. **Create Extension Prototype**
   - Basic project structure
   - Simple console output panel
   - Command execution proof-of-concept

3. **Integration Testing**
   - Test with existing RVNK development setup
   - Validate workflow improvements
   - Gather developer feedback

This project would significantly enhance the Minecraft plugin development experience by bringing server console access directly into the development environment, eliminating context switches and enabling real-time debugging and testing.
