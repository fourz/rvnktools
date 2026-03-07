# VS Code Console Integration - Copilot Development Instructions

This document provides comprehensive guidance for developing the VS Code Console Integration extension, enabling seamless Minecraft server console access directly within the VS Code development environment.

## Project Context

**Objective**: Eliminate context switching between VS Code and external console windows during Minecraft plugin development by integrating real-time server console access, command execution, and monitoring capabilities directly into the VS Code interface.

**Technical Foundation**: Leverage existing MCSS (MC Server Soft) REST API for server communication, TypeScript for extension development, and VS Code Extension API for UI integration.

## Development Guidelines

### Extension Architecture Standards

Follow VS Code extension best practices with modular architecture based on comprehensive MCSS API analysis:

```typescript
// Core extension structure (Updated with confirmed API capabilities)
export class MinecraftConsoleExtension {
    private mcssClient: MCSSClient;
    private consolePanel: ConsoleWebviewProvider;
    private commandProvider: CommandProvider;
    private healthMonitor: ConnectionHealthMonitor;
    
    public async activate(context: vscode.ExtensionContext) {
        // Initialize core services with validated API endpoints
        // Register commands and views with confirmed capabilities
        // Set up event listeners with error resilience patterns
        // Start connection health monitoring
    }
}
```

**Key Principles:**
- **Async-First Operations**: All API calls and UI updates must be non-blocking
- **Error Resilience**: Graceful handling of connection failures and API errors
- **Performance Optimization**: Efficient memory usage and log processing
- **User Experience Focus**: Intuitive interface with immediate feedback

### MCSS API Integration

Implement robust API client based on validated MCSS API v2 specification from `docs/examples/mcss-api.md`:

```typescript
interface MCSSConfig {
    baseUrl: string;          // Default: "http://localhost:25564"
    apiVersion: string;       // Confirmed: "v2"
    apiKey: string;          // MCSS-generated API key
    serverId: string;        // Server UUID from MCSS
    timeout: number;         // Default: 30000ms
    useHttps: boolean;       // Production HTTPS support
    reconnectInterval: number; // Health monitoring interval
}

class MCSSClient {
    // Confirmed API endpoints with actual parameters
    async getConsoleLogs(params: {
        AmountOfLines: number;    // -1 for all logs
        Reversed: boolean;        // Chronological order
        takeFromBeginning?: boolean;
    }): Promise<string[]>;
    
    async executeCommand(command: string): Promise<void>;
    async executeBatchCommands(commands: string[]): Promise<void>;
    async getServerStats(): Promise<ServerStats>;
    async executeServerAction(action: ServerAction): Promise<void>;
    // Server actions: 1=Stop, 2=Start, 3=Kill, 4=Restart
}
```

**Implementation Requirements:**
- Use VS Code's built-in HTTP client for consistency
- Implement automatic retry logic with exponential backoff
- Cache authentication tokens using VS Code SecretStorage
- Handle rate limiting and connection throttling

### WebView Panel Development

Create responsive console interface using modern web technologies:

```typescript
class ConsoleWebviewProvider implements vscode.WebviewViewProvider {
    private logs: LogEntry[] = [];
    private filters: LogFilter = new LogFilter();
    
    public resolveWebviewView(webviewView: vscode.WebviewView) {
        // Set up HTML content with CSS and JavaScript
        // Implement bidirectional communication
        // Handle user interactions and updates
    }
    
    private updateLogDisplay(newLogs: LogEntry[]) {
        // Efficient DOM updates
        // Auto-scroll management
        // Search highlighting
    }
}
```

**UI Guidelines:**
- Use VS Code's native color theme for consistency
- Implement virtual scrolling for performance with large log volumes
- Provide keyboard shortcuts for common actions
- Support both light and dark themes

### Command Execution System

Implement secure and efficient command execution:

```typescript
class CommandProvider {
    private history: CommandHistory;
    private templates: CommandTemplate[];
    
    async executeCommand(command: string): Promise<void> {
        // Validate command safety
        // Send to server via MCSS API
        // Display result in console
        // Update command history
    }
    
    async getCommandSuggestions(partial: string): Promise<string[]> {
        // Provide intelligent auto-completion
        // Include server-specific commands
        // Filter based on permissions
    }
}
```

**Security Considerations:**
- Validate all commands before execution
- Implement command whitelisting for safety
- Log all executed commands for audit purposes
- Provide confirmation for destructive operations

## Code Quality Standards

### TypeScript Best Practices

Use strict TypeScript configuration with comprehensive type safety:

```typescript
// Strict typing for all API interfaces
interface LogEntry {
    timestamp: Date;
    level: LogLevel;
    plugin: string;
    message: string;
    thread?: string;
    stackTrace?: string[];
}

// Use enums for constants
enum LogLevel {
    DEBUG = 'DEBUG',
    INFO = 'INFO',
    WARN = 'WARN',
    ERROR = 'ERROR'
}
```

### Error Handling Patterns

Implement comprehensive error handling with user feedback:

```typescript
class ExtensionError extends Error {
    constructor(
        message: string,
        public readonly code: ErrorCode,
        public readonly context?: any
    ) {
        super(message);
    }
}

async function safeApiCall<T>(
    operation: () => Promise<T>,
    fallback?: T
): Promise<T> {
    try {
        return await operation();
    } catch (error) {
        console.error('API call failed:', error);
        showErrorNotification(error);
        return fallback ?? throwError(error);
    }
}
```

### Performance Optimization

Implement efficient data handling for high-volume scenarios:

```typescript
class LogBuffer {
    private maxSize = 10000;
    private buffer: LogEntry[] = [];
    
    public add(entries: LogEntry[]): void {
        // Efficient buffer management
        // Memory cleanup for old entries
        // Event-driven UI updates
    }
    
    public search(pattern: string): LogEntry[] {
        // Optimized search with indexing
        // Regex compilation caching
        // Lazy loading for large results
    }
}
```

## Integration Patterns

### VS Code Task Integration

Connect with existing build and deployment tasks:

```typescript
class TaskIntegration {
    public async createBuildAndTestTask(): Promise<vscode.Task> {
        return new vscode.Task(
            { type: 'shell', task: 'buildAndTest' },
            vscode.TaskScope.Workspace,
            'Build and Test Plugin',
            'minecraft',
            new vscode.ShellExecution(this.getBuildCommand())
        );
    }
    
    private async onTaskComplete(task: vscode.Task): Promise<void> {
        // Automatically switch to console view
        // Show relevant log entries
        // Provide success/failure feedback
    }
}
```

### Settings and Configuration

Provide comprehensive configuration options:

```typescript
interface ExtensionSettings {
    mcss: {
        apiUrl: string;
        serverName: string;
        reconnectInterval: number;
    };
    console: {
        maxLogEntries: number;
        autoScroll: boolean;
        timestampFormat: string;
        colorTheme: 'auto' | 'light' | 'dark';
    };
    commands: {
        confirmDestructive: boolean;
        historySize: number;
        enableAutoComplete: boolean;
    };
}
```

## Testing Strategy

### Unit Testing

Test core functionality with comprehensive coverage:

```typescript
describe('MCSSClient', () => {
    let client: MCSSClient;
    let mockApi: MockMCSSApi;
    
    beforeEach(() => {
        mockApi = new MockMCSSApi();
        client = new MCSSClient(mockApi);
    });
    
    it('should handle authentication errors gracefully', async () => {
        mockApi.setAuthError();
        const result = await client.getConsoleLogs();
        expect(result).toEqual([]);
        expect(mockApi.retryCount).toBeGreaterThan(0);
    });
});
```

### Integration Testing

Validate end-to-end functionality with real server:

```typescript
describe('Extension Integration', () => {
    it('should connect to development server and retrieve logs', async () => {
        const extension = new MinecraftConsoleExtension();
        await extension.activate(mockContext);
        
        const logs = await extension.getRecentLogs();
        expect(logs.length).toBeGreaterThan(0);
        expect(logs[0]).toHaveProperty('timestamp');
    });
});
```

## Deployment and Distribution

### Extension Packaging

Configure proper extension metadata and dependencies:

```json
{
    "name": "minecraft-console-integration",
    "displayName": "Minecraft Console Integration",
    "description": "Seamless Minecraft server console access for plugin development",
    "version": "1.0.0",
    "engines": {
        "vscode": "^1.80.0"
    },
    "categories": ["Other", "Debuggers"],
    "keywords": ["minecraft", "console", "development", "rvnk"],
    "activationEvents": [
        "onLanguage:java",
        "workspaceContains:**/plugin.yml"
    ]
}
```

### Security and Privacy

Implement secure credential management:

```typescript
class CredentialManager {
    private secrets: vscode.SecretStorage;
    
    public async storeApiKey(key: string): Promise<void> {
        await this.secrets.store('mcss.apiKey', key);
    }
    
    public async getApiKey(): Promise<string | undefined> {
        return await this.secrets.get('mcss.apiKey');
    }
}
```

## Development Workflow

### Local Development Setup

1. **Environment Preparation**
   - Install VS Code Extension Development environment
   - Set up TypeScript compilation and testing
   - Configure MCSS API access for testing

2. **Development Process**
   - Use TDD approach with comprehensive test coverage
   - Implement features incrementally with user feedback
   - Regular integration testing with real Minecraft server

3. **Quality Assurance**
   - Code reviews focusing on performance and security
   - User acceptance testing with target developers
   - Performance profiling under high-load scenarios

### Debugging and Troubleshooting

Implement comprehensive logging and diagnostics:

```typescript
class ExtensionLogger {
    private outputChannel = vscode.window.createOutputChannel('Minecraft Console');
    
    public logApiCall(method: string, url: string, result: any): void {
        this.outputChannel.appendLine(
            `[${new Date().toISOString()}] API ${method} ${url}: ${JSON.stringify(result)}`
        );
    }
    
    public logError(context: string, error: Error): void {
        this.outputChannel.appendLine(
            `[${new Date().toISOString()}] ERROR in ${context}: ${error.message}`
        );
        console.error(context, error);
    }
}
```

## Success Criteria

### Functional Requirements
- ✅ Real-time console log display with filtering capabilities
- ✅ Command execution with immediate feedback
- ✅ Integration with existing VS Code tasks
- ✅ Error navigation from console to source code
- ✅ Performance monitoring and server status display

### Performance Requirements
- Response time: <100ms for command execution feedback
- Memory usage: <50MB additional VS Code memory consumption
- Log processing: Handle >1000 log entries per minute
- Connection reliability: 99%+ uptime with automatic reconnection

### User Experience Requirements
- Zero-configuration setup for developers using MCSS
- Intuitive interface matching VS Code design patterns
- Keyboard shortcuts for all major functions
- Context-aware help and documentation

This extension will significantly enhance the Minecraft plugin development experience by eliminating context switching and providing real-time feedback directly within the development environment.
