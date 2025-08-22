# MCSS API Examples and Integration Guide

This comprehensive guide provides practical examples for integrating with the MC Server Soft (MCSS) REST API for Minecraft server console access, command execution, and monitoring during plugin development.

## Table of Contents

1. [API Overview and Setup](#api-overview-and-setup)
2. [Authentication and Configuration](#authentication-and-configuration)
3. [Console Access Examples](#console-access-examples)
4. [Command Execution Examples](#command-execution-examples)
5. [Server Management Examples](#server-management-examples)
6. [Plugin Development Integration](#plugin-development-integration)
7. [VS Code Extension Integration](#vs-code-extension-integration)
8. [Error Handling and Best Practices](#error-handling-and-best-practices)

## API Overview and Setup

### Base Configuration

```typescript
interface MCSSConfig {
    baseUrl: string;          // Default: "http://localhost:25564"
    apiVersion: string;       // Default: "v2"
    apiKey: string;          // Required: Your MCSS API key
    serverId: string;        // Required: Target server UUID
    timeout: number;         // Default: 30000ms
    useHttps: boolean;       // Default: false (use true for production)
}

const defaultConfig: MCSSConfig = {
    baseUrl: "http://localhost:25564",
    apiVersion: "v2", 
    apiKey: process.env.MCSS_API_KEY || "",
    serverId: process.env.MCSS_SERVER_ID || "",
    timeout: 30000,
    useHttps: false
};
```

### API Client Base Class

```typescript
class MCSSApiClient {
    private config: MCSSConfig;
    private baseUrl: string;

    constructor(config: Partial<MCSSConfig> = {}) {
        this.config = { ...defaultConfig, ...config };
        this.baseUrl = `${this.config.baseUrl}/api/${this.config.apiVersion}`;
    }

    private async makeRequest<T>(
        endpoint: string, 
        method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
        body?: any
    ): Promise<T> {
        const url = `${this.baseUrl}${endpoint}`;
        const headers = {
            'apiKey': this.config.apiKey,
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        };

        const options: RequestInit = {
            method,
            headers,
            signal: AbortSignal.timeout(this.config.timeout)
        };

        if (body && method !== 'GET') {
            options.body = JSON.stringify(body);
        }

        try {
            const response = await fetch(url, options);
            
            if (!response.ok) {
                throw new Error(`MCSS API Error: ${response.status} ${response.statusText}`);
            }

            return await response.json();
        } catch (error) {
            throw new Error(`MCSS API Request Failed: ${error.message}`);
        }
    }
}
```

## Authentication and Configuration

### API Key Management

```typescript
class MCSSAuth {
    private static readonly API_KEY_STORAGE = 'mcss.apiKey';
    private static readonly SERVER_ID_STORAGE = 'mcss.serverId';

    // For VS Code extensions
    static async storeCredentials(
        secrets: vscode.SecretStorage, 
        apiKey: string, 
        serverId: string
    ): Promise<void> {
        await secrets.store(this.API_KEY_STORAGE, apiKey);
        await secrets.store(this.SERVER_ID_STORAGE, serverId);
    }

    static async getCredentials(secrets: vscode.SecretStorage): Promise<{
        apiKey: string | undefined,
        serverId: string | undefined
    }> {
        const apiKey = await secrets.get(this.API_KEY_STORAGE);
        const serverId = await secrets.get(this.SERVER_ID_STORAGE);
        return { apiKey, serverId };
    }

    // Validate API key
    static async validateApiKey(client: MCSSApiClient): Promise<boolean> {
        try {
            await client.getServers();
            return true;
        } catch (error) {
            return false;
        }
    }
}
```

### Environment Configuration

```bash
# .env file for development
MCSS_API_KEY=your-api-key-here
MCSS_SERVER_ID=69361e31-2ac8-43b5-9377-0cb5e40e75ac
MCSS_BASE_URL=http://localhost:25564
MCSS_USE_HTTPS=false

# Production environment
MCSS_API_KEY=production-api-key
MCSS_SERVER_ID=production-server-id
MCSS_BASE_URL=https://your-server.com:25565
MCSS_USE_HTTPS=true
```

## Console Access Examples

### Real-time Console Monitoring

```typescript
class ConsoleMonitor extends MCSSApiClient {
    private lastLogCount = 0;
    private monitoringInterval: NodeJS.Timeout | null = null;

    async getRecentLogs(count: number = 50, reversed: boolean = false): Promise<string[]> {
        const endpoint = `/servers/${this.config.serverId}/console`;
        const params = new URLSearchParams({
            AmountOfLines: count.toString(),
            Reversed: reversed.toString()
        });

        return this.makeRequest<string[]>(`${endpoint}?${params}`);
    }

    async getNewLogs(): Promise<string[]> {
        const allLogs = await this.getRecentLogs(100);
        const newLogs = allLogs.slice(this.lastLogCount);
        this.lastLogCount = allLogs.length;
        return newLogs;
    }

    startMonitoring(callback: (logs: string[]) => void, interval: number = 2000): void {
        this.monitoringInterval = setInterval(async () => {
            try {
                const newLogs = await this.getNewLogs();
                if (newLogs.length > 0) {
                    callback(newLogs);
                }
            } catch (error) {
                console.error('Console monitoring error:', error);
            }
        }, interval);
    }

    stopMonitoring(): void {
        if (this.monitoringInterval) {
            clearInterval(this.monitoringInterval);
            this.monitoringInterval = null;
        }
    }

    // Filter logs by plugin or pattern
    async getPluginLogs(pluginName: string, count: number = 100): Promise<string[]> {
        const logs = await this.getRecentLogs(count);
        return logs.filter(log => 
            log.includes(`[${pluginName}]`) || 
            log.toLowerCase().includes(pluginName.toLowerCase())
        );
    }

    // Search for errors in recent logs
    async getErrorLogs(count: number = 100): Promise<string[]> {
        const logs = await this.getRecentLogs(count);
        return logs.filter(log => 
            log.toLowerCase().includes('error') || 
            log.toLowerCase().includes('exception') ||
            log.toLowerCase().includes('warn')
        );
    }
}
```

### Log Parsing and Analysis

```typescript
interface LogEntry {
    timestamp: Date;
    level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG';
    thread: string;
    plugin?: string;
    message: string;
    raw: string;
}

class LogParser {
    private static readonly LOG_PATTERN = /^\[(\d{2}:\d{2}:\d{2})\] \[([^/]+)\/([A-Z]+)\](?:: \[([^\]]+)\])?: (.+)$/;

    static parseLogEntry(logLine: string): LogEntry | null {
        const match = logLine.match(this.LOG_PATTERN);
        if (!match) return null;

        const [, timeStr, thread, level, plugin, message] = match;
        
        // Create timestamp for today with the parsed time
        const today = new Date();
        const [hours, minutes, seconds] = timeStr.split(':').map(Number);
        const timestamp = new Date(today.getFullYear(), today.getMonth(), today.getDate(), hours, minutes, seconds);

        return {
            timestamp,
            level: level as LogEntry['level'],
            thread,
            plugin,
            message,
            raw: logLine
        };
    }

    static parseLogs(logs: string[]): LogEntry[] {
        return logs
            .map(log => this.parseLogEntry(log))
            .filter((entry): entry is LogEntry => entry !== null);
    }

    static filterByPlugin(entries: LogEntry[], pluginName: string): LogEntry[] {
        return entries.filter(entry => 
            entry.plugin?.toLowerCase() === pluginName.toLowerCase()
        );
    }

    static filterByLevel(entries: LogEntry[], level: LogEntry['level']): LogEntry[] {
        return entries.filter(entry => entry.level === level);
    }

    static filterByTimeRange(entries: LogEntry[], startTime: Date, endTime: Date): LogEntry[] {
        return entries.filter(entry => 
            entry.timestamp >= startTime && entry.timestamp <= endTime
        );
    }
}
```

## Command Execution Examples

### Single Command Execution

```typescript
class CommandExecutor extends MCSSApiClient {
    async executeCommand(command: string): Promise<boolean> {
        const endpoint = `/servers/${this.config.serverId}/execute/command`;
        try {
            await this.makeRequest(endpoint, 'POST', { command });
            return true;
        } catch (error) {
            console.error(`Failed to execute command "${command}":`, error);
            return false;
        }
    }

    async executeCommandWithResponse(command: string, waitTime: number = 2000): Promise<{
        success: boolean;
        output: string[];
    }> {
        // Get baseline log count
        const baselineLogs = await this.getRecentLogs(10);
        const baselineCount = baselineLogs.length;

        // Execute command
        const success = await this.executeCommand(command);
        if (!success) {
            return { success: false, output: [] };
        }

        // Wait for response
        await new Promise(resolve => setTimeout(resolve, waitTime));

        // Get new logs
        const newLogs = await this.getRecentLogs(50);
        const output = newLogs.slice(baselineCount);

        return { success: true, output };
    }
}
```

### Batch Command Execution

```typescript
class BatchCommandExecutor extends CommandExecutor {
    async executeCommands(commands: string[]): Promise<boolean> {
        const endpoint = `/servers/${this.config.serverId}/execute/commands`;
        try {
            await this.makeRequest(endpoint, 'POST', { commands });
            return true;
        } catch (error) {
            console.error('Failed to execute batch commands:', error);
            return false;
        }
    }

    async executeSequential(commands: string[], delay: number = 1000): Promise<{
        results: Array<{ command: string; success: boolean; output: string[] }>;
        overallSuccess: boolean;
    }> {
        const results: Array<{ command: string; success: boolean; output: string[] }> = [];
        let overallSuccess = true;

        for (const command of commands) {
            const result = await this.executeCommandWithResponse(command);
            results.push({
                command,
                success: result.success,
                output: result.output
            });

            if (!result.success) {
                overallSuccess = false;
            }

            if (delay > 0 && commands.indexOf(command) < commands.length - 1) {
                await new Promise(resolve => setTimeout(resolve, delay));
            }
        }

        return { results, overallSuccess };
    }
}
```

### Plugin-Specific Commands

```typescript
class RVNKToolsCommands extends CommandExecutor {
    async reloadPlugin(): Promise<{ success: boolean; output: string[] }> {
        return this.executeCommandWithResponse('rvnktools reload');
    }

    async getPluginStatus(): Promise<{ success: boolean; output: string[] }> {
        return this.executeCommandWithResponse('rvnktools status');
    }

    async getPluginVersion(): Promise<{ success: boolean; output: string[] }> {
        return this.executeCommandWithResponse('rvnktools version');
    }

    async testAnnouncements(): Promise<{ success: boolean; output: string[] }> {
        return this.executeCommandWithResponse('announce list');
    }

    async testLinks(): Promise<{ success: boolean; output: string[] }> {
        return this.executeCommandWithResponse('link list');
    }

    // Comprehensive plugin test sequence
    async runPluginTests(): Promise<{
        testResults: Array<{ test: string; success: boolean; output: string[] }>;
        overallSuccess: boolean;
    }> {
        const tests = [
            { name: 'Plugin Status', command: 'rvnktools status' },
            { name: 'Plugin Version', command: 'rvnktools version' },
            { name: 'Reload Plugin', command: 'rvnktools reload' },
            { name: 'Announcement List', command: 'announce list' },
            { name: 'Link List', command: 'link list' }
        ];

        const testResults: Array<{ test: string; success: boolean; output: string[] }> = [];
        let overallSuccess = true;

        for (const test of tests) {
            console.log(`Running test: ${test.name}`);
            const result = await this.executeCommandWithResponse(test.command);
            
            testResults.push({
                test: test.name,
                success: result.success,
                output: result.output
            });

            if (!result.success) {
                overallSuccess = false;
            }

            // Small delay between tests
            await new Promise(resolve => setTimeout(resolve, 500));
        }

        return { testResults, overallSuccess };
    }
}
```

## Server Management Examples

### Server Status and Control

```typescript
class ServerManager extends MCSSApiClient {
    async getServerInfo(): Promise<any> {
        const endpoint = `/servers/${this.config.serverId}`;
        return this.makeRequest(endpoint);
    }

    async getServerStats(): Promise<any> {
        const endpoint = `/servers/${this.config.serverId}/stats`;
        return this.makeRequest(endpoint);
    }

    async restartServer(): Promise<boolean> {
        const endpoint = `/servers/${this.config.serverId}/execute/action`;
        try {
            await this.makeRequest(endpoint, 'POST', { action: 4 }); // 4 = restart
            return true;
        } catch (error) {
            console.error('Failed to restart server:', error);
            return false;
        }
    }

    async stopServer(): Promise<boolean> {
        const endpoint = `/servers/${this.config.serverId}/execute/action`;
        try {
            await this.makeRequest(endpoint, 'POST', { action: 1 }); // 1 = stop
            return true;
        } catch (error) {
            console.error('Failed to stop server:', error);
            return false;
        }
    }

    async startServer(): Promise<boolean> {
        const endpoint = `/servers/${this.config.serverId}/execute/action`;
        try {
            await this.makeRequest(endpoint, 'POST', { action: 2 }); // 2 = start
            return true;
        } catch (error) {
            console.error('Failed to start server:', error);
            return false;
        }
    }

    async getServerStatus(): Promise<{
        isRunning: boolean;
        playersOnline: number;
        maxPlayers: number;
        memoryUsed: number;
        memoryLimit: number;
        cpuUsage: number;
    }> {
        try {
            const stats = await this.getServerStats();
            const info = await this.getServerInfo();
            
            return {
                isRunning: info.status === 0, // 0 = running
                playersOnline: stats.latest?.playersOnline || 0,
                maxPlayers: stats.latest?.playerLimit || 20,
                memoryUsed: stats.latest?.memoryUsed || 0,
                memoryLimit: stats.latest?.memoryLimit || 0,
                cpuUsage: stats.latest?.cpu || 0
            };
        } catch (error) {
            throw new Error(`Failed to get server status: ${error.message}`);
        }
    }
}
```

## Plugin Development Integration

### Development Workflow Integration

```typescript
class PluginDevelopmentWorkflow {
    private console: ConsoleMonitor;
    private commands: RVNKToolsCommands;
    private server: ServerManager;

    constructor(config: MCSSConfig) {
        this.console = new ConsoleMonitor(config);
        this.commands = new RVNKToolsCommands(config);
        this.server = new ServerManager(config);
    }

    async deployAndTest(): Promise<{
        deployment: { success: boolean; output: string[] };
        tests: { testResults: Array<any>; overallSuccess: boolean };
        errors: string[];
    }> {
        const errors: string[] = [];

        // Step 1: Reload plugin
        console.log('🔄 Reloading plugin...');
        const deployment = await this.commands.reloadPlugin();
        
        if (!deployment.success) {
            errors.push('Plugin reload failed');
        }

        // Step 2: Wait for plugin to initialize
        await new Promise(resolve => setTimeout(resolve, 2000));

        // Step 3: Check for errors in console
        const errorLogs = await this.console.getErrorLogs(20);
        if (errorLogs.length > 0) {
            errors.push(...errorLogs);
        }

        // Step 4: Run plugin tests
        console.log('🧪 Running plugin tests...');
        const tests = await this.commands.runPluginTests();

        return {
            deployment,
            tests,
            errors
        };
    }

    async monitorPluginActivity(pluginName: string, duration: number = 30000): Promise<{
        logs: LogEntry[];
        errors: LogEntry[];
        summary: string;
    }> {
        const startTime = new Date();
        const endTime = new Date(startTime.getTime() + duration);
        
        console.log(`📊 Monitoring ${pluginName} for ${duration/1000} seconds...`);

        return new Promise((resolve) => {
            const logs: string[] = [];
            
            this.console.startMonitoring((newLogs) => {
                logs.push(...newLogs);
            }, 1000);

            setTimeout(() => {
                this.console.stopMonitoring();
                
                const parsedLogs = LogParser.parseLogs(logs);
                const pluginLogs = LogParser.filterByPlugin(parsedLogs, pluginName);
                const errors = LogParser.filterByLevel(pluginLogs, 'ERROR');
                
                const summary = `
Monitoring Summary for ${pluginName}:
- Total logs: ${pluginLogs.length}
- Errors: ${errors.length}
- Duration: ${duration/1000}s
                `.trim();

                resolve({
                    logs: pluginLogs,
                    errors,
                    summary
                });
            }, duration);
        });
    }
}
```

## VS Code Extension Integration

### Extension Command Integration

```typescript
// VS Code extension commands
export class MCSSCommands {
    private workflow: PluginDevelopmentWorkflow;

    constructor(context: vscode.ExtensionContext) {
        this.initializeWorkflow(context);
        this.registerCommands(context);
    }

    private async initializeWorkflow(context: vscode.ExtensionContext): Promise<void> {
        const credentials = await MCSSAuth.getCredentials(context.secrets);
        
        if (!credentials.apiKey || !credentials.serverId) {
            const config = await this.promptForConfiguration();
            await MCSSAuth.storeCredentials(context.secrets, config.apiKey, config.serverId);
            this.workflow = new PluginDevelopmentWorkflow(config);
        } else {
            this.workflow = new PluginDevelopmentWorkflow({
                apiKey: credentials.apiKey,
                serverId: credentials.serverId
            } as MCSSConfig);
        }
    }

    private registerCommands(context: vscode.ExtensionContext): void {
        // Console access commands
        context.subscriptions.push(
            vscode.commands.registerCommand('mcss.showConsole', () => this.showConsole()),
            vscode.commands.registerCommand('mcss.executeCommand', () => this.executeCommand()),
            vscode.commands.registerCommand('mcss.reloadPlugin', () => this.reloadPlugin()),
            vscode.commands.registerCommand('mcss.deployAndTest', () => this.deployAndTest()),
            vscode.commands.registerCommand('mcss.monitorPlugin', () => this.monitorPlugin())
        );
    }

    private async showConsole(): Promise<void> {
        const panel = vscode.window.createWebviewPanel(
            'mcssConsole',
            'Minecraft Console',
            vscode.ViewColumn.Two,
            {
                enableScripts: true,
                retainContextWhenHidden: true
            }
        );

        // Start console monitoring
        const console = new ConsoleMonitor(this.workflow['console'].config);
        console.startMonitoring((logs) => {
            panel.webview.postMessage({
                type: 'newLogs',
                data: logs
            });
        });

        // Clean up on panel disposal
        panel.onDidDispose(() => {
            console.stopMonitoring();
        });
    }

    private async executeCommand(): Promise<void> {
        const command = await vscode.window.showInputBox({
            prompt: 'Enter Minecraft server command',
            placeHolder: 'e.g., rvnktools status'
        });

        if (command) {
            const result = await this.workflow.commands.executeCommandWithResponse(command);
            
            if (result.success) {
                vscode.window.showInformationMessage(`Command executed: ${command}`);
                this.showOutput(result.output);
            } else {
                vscode.window.showErrorMessage(`Command failed: ${command}`);
            }
        }
    }

    private async reloadPlugin(): Promise<void> {
        const result = await this.workflow.commands.reloadPlugin();
        
        if (result.success) {
            vscode.window.showInformationMessage('Plugin reloaded successfully');
        } else {
            vscode.window.showErrorMessage('Plugin reload failed');
        }
        
        this.showOutput(result.output);
    }

    private async deployAndTest(): Promise<void> {
        vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: "Deploying and testing plugin...",
            cancellable: false
        }, async (progress) => {
            progress.report({ increment: 0 });

            const result = await this.workflow.deployAndTest();
            
            progress.report({ increment: 100 });

            if (result.tests.overallSuccess && result.errors.length === 0) {
                vscode.window.showInformationMessage('Plugin deployment and testing completed successfully');
            } else {
                vscode.window.showWarningMessage(`Plugin testing completed with ${result.errors.length} errors`);
            }

            // Show detailed results
            this.showTestResults(result);
        });
    }

    private showOutput(output: string[]): void {
        const outputChannel = vscode.window.createOutputChannel('MCSS Console');
        output.forEach(line => outputChannel.appendLine(line));
        outputChannel.show();
    }
}
```

### WebView Console Interface

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body {
            font-family: 'Consolas', 'Courier New', monospace;
            background: var(--vscode-editor-background);
            color: var(--vscode-editor-foreground);
            margin: 0;
            padding: 10px;
        }
        
        .console-container {
            display: flex;
            flex-direction: column;
            height: calc(100vh - 20px);
        }
        
        .console-output {
            flex: 1;
            overflow-y: auto;
            background: var(--vscode-terminal-background);
            border: 1px solid var(--vscode-panel-border);
            padding: 10px;
            font-size: 12px;
            line-height: 1.4;
        }
        
        .console-input {
            margin-top: 10px;
            display: flex;
        }
        
        .console-input input {
            flex: 1;
            background: var(--vscode-input-background);
            color: var(--vscode-input-foreground);
            border: 1px solid var(--vscode-input-border);
            padding: 8px;
            font-family: inherit;
            font-size: 12px;
        }
        
        .console-input button {
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: none;
            padding: 8px 16px;
            margin-left: 5px;
            cursor: pointer;
        }
        
        .log-entry {
            margin: 2px 0;
        }
        
        .log-error { color: #ff6b6b; }
        .log-warn { color: #ffa726; }
        .log-info { color: #66bb6a; }
        .log-debug { color: #9e9e9e; }
        
        .timestamp {
            color: var(--vscode-descriptionForeground);
            margin-right: 8px;
        }
        
        .plugin-name {
            color: #42a5f5;
            font-weight: bold;
        }
    </style>
</head>
<body>
    <div class="console-container">
        <div class="console-output" id="consoleOutput"></div>
        <div class="console-input">
            <input type="text" id="commandInput" placeholder="Enter command..." />
            <button onclick="executeCommand()">Execute</button>
            <button onclick="clearConsole()">Clear</button>
        </div>
    </div>

    <script>
        const vscode = acquireVsCodeApi();
        const consoleOutput = document.getElementById('consoleOutput');
        const commandInput = document.getElementById('commandInput');
        
        // Listen for messages from extension
        window.addEventListener('message', event => {
            const message = event.data;
            
            switch (message.type) {
                case 'newLogs':
                    appendLogs(message.data);
                    break;
            }
        });
        
        function appendLogs(logs) {
            logs.forEach(log => {
                const logEntry = document.createElement('div');
                logEntry.className = 'log-entry';
                
                const parsed = parseLogEntry(log);
                if (parsed) {
                    logEntry.innerHTML = `
                        <span class="timestamp">${parsed.timestamp}</span>
                        <span class="log-${parsed.level.toLowerCase()}">[${parsed.level}]</span>
                        ${parsed.plugin ? `<span class="plugin-name">[${parsed.plugin}]</span>` : ''}
                        <span>${parsed.message}</span>
                    `;
                } else {
                    logEntry.textContent = log;
                }
                
                consoleOutput.appendChild(logEntry);
            });
            
            // Auto-scroll to bottom
            consoleOutput.scrollTop = consoleOutput.scrollHeight;
        }
        
        function parseLogEntry(logLine) {
            const pattern = /^\[(\d{2}:\d{2}:\d{2})\] \[([^/]+)\/([A-Z]+)\](?:: \[([^\]]+)\])?: (.+)$/;
            const match = logLine.match(pattern);
            
            if (match) {
                return {
                    timestamp: match[1],
                    thread: match[2],
                    level: match[3],
                    plugin: match[4],
                    message: match[5]
                };
            }
            
            return null;
        }
        
        function executeCommand() {
            const command = commandInput.value.trim();
            if (command) {
                vscode.postMessage({
                    type: 'executeCommand',
                    command: command
                });
                commandInput.value = '';
            }
        }
        
        function clearConsole() {
            consoleOutput.innerHTML = '';
        }
        
        // Enter key execution
        commandInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                executeCommand();
            }
        });
        
        // Focus input on load
        commandInput.focus();
    </script>
</body>
</html>
```

## Error Handling and Best Practices

### Robust Error Handling

```typescript
class MCSSErrorHandler {
    static handleApiError(error: any): string {
        if (error.message.includes('401')) {
            return 'Invalid API key. Please check your MCSS configuration.';
        } else if (error.message.includes('403')) {
            return 'Insufficient permissions. Check your MCSS user permissions.';
        } else if (error.message.includes('404')) {
            return 'Server not found. Please check your server ID.';
        } else if (error.message.includes('timeout')) {
            return 'Request timed out. Check your MCSS server connection.';
        } else {
            return `MCSS API Error: ${error.message}`;
        }
    }

    static async withRetry<T>(
        operation: () => Promise<T>, 
        maxRetries: number = 3, 
        delay: number = 1000
    ): Promise<T> {
        let lastError: Error;
        
        for (let i = 0; i < maxRetries; i++) {
            try {
                return await operation();
            } catch (error) {
                lastError = error;
                
                if (i < maxRetries - 1) {
                    await new Promise(resolve => setTimeout(resolve, delay * (i + 1)));
                }
            }
        }
        
        throw lastError!;
    }
}
```

### Connection Health Monitoring

```typescript
class ConnectionHealthMonitor {
    private client: MCSSApiClient;
    private isHealthy: boolean = true;
    private healthCheckInterval: NodeJS.Timeout | null = null;
    private onHealthChange?: (healthy: boolean) => void;

    constructor(client: MCSSApiClient, onHealthChange?: (healthy: boolean) => void) {
        this.client = client;
        this.onHealthChange = onHealthChange;
    }

    startMonitoring(interval: number = 30000): void {
        this.healthCheckInterval = setInterval(async () => {
            await this.performHealthCheck();
        }, interval);
    }

    stopMonitoring(): void {
        if (this.healthCheckInterval) {
            clearInterval(this.healthCheckInterval);
            this.healthCheckInterval = null;
        }
    }

    private async performHealthCheck(): Promise<void> {
        try {
            await this.client.getServers();
            
            if (!this.isHealthy) {
                this.isHealthy = true;
                this.onHealthChange?.(true);
                console.log('MCSS connection restored');
            }
        } catch (error) {
            if (this.isHealthy) {
                this.isHealthy = false;
                this.onHealthChange?.(false);
                console.error('MCSS connection lost:', error.message);
            }
        }
    }

    getHealthStatus(): boolean {
        return this.isHealthy;
    }
}
```

### Performance Optimization

```typescript
class MCSSPerformanceOptimizer {
    private static readonly REQUEST_CACHE = new Map<string, { data: any; timestamp: number }>();
    private static readonly CACHE_TTL = 5000; // 5 seconds

    static async cachedRequest<T>(
        key: string, 
        operation: () => Promise<T>, 
        ttl: number = this.CACHE_TTL
    ): Promise<T> {
        const cached = this.REQUEST_CACHE.get(key);
        const now = Date.now();
        
        if (cached && (now - cached.timestamp) < ttl) {
            return cached.data;
        }
        
        const data = await operation();
        this.REQUEST_CACHE.set(key, { data, timestamp: now });
        
        return data;
    }

    static clearCache(): void {
        this.REQUEST_CACHE.clear();
    }

    static async batchRequests<T>(
        operations: Array<() => Promise<T>>, 
        concurrency: number = 3
    ): Promise<T[]> {
        const results: T[] = [];
        
        for (let i = 0; i < operations.length; i += concurrency) {
            const batch = operations.slice(i, i + concurrency);
            const batchResults = await Promise.all(batch.map(op => op()));
            results.push(...batchResults);
        }
        
        return results;
    }
}
```

## PowerShell Integration Examples

### PowerShell Development Helper

```powershell
# MCSS-DevHelper.ps1 - PowerShell integration for MCSS API

param(
    [string]$ApiKey = $env:MCSS_API_KEY,
    [string]$ServerId = $env:MCSS_SERVER_ID,
    [string]$BaseUrl = "http://localhost:25564"
)

function Invoke-MCSSApi {
    param(
        [string]$Endpoint,
        [string]$Method = "GET",
        [object]$Body = $null
    )
    
    $headers = @{
        'apiKey' = $ApiKey
        'Content-Type' = 'application/json'
    }
    
    $uri = "$BaseUrl/api/v2$Endpoint"
    
    try {
        if ($Body) {
            $jsonBody = $Body | ConvertTo-Json -Depth 10
            Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -Body $jsonBody
        } else {
            Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers
        }
    } catch {
        Write-Error "MCSS API call failed: $($_.Exception.Message)"
        throw
    }
}

function Get-MCSSConsole {
    param(
        [int]$Lines = 50,
        [switch]$Reversed
    )
    
    $endpoint = "/servers/$ServerId/console?AmountOfLines=$Lines&Reversed=$($Reversed.IsPresent)"
    return Invoke-MCSSApi -Endpoint $endpoint
}

function Send-MCSSCommand {
    param([string]$Command)
    
    $endpoint = "/servers/$ServerId/execute/command"
    $body = @{ command = $Command }
    return Invoke-MCSSApi -Endpoint $endpoint -Method "POST" -Body $body
}

function Test-RVNKToolsPlugin {
    $commands = @(
        "rvnktools status",
        "rvnktools version", 
        "announce list",
        "link list"
    )
    
    $results = @()
    
    foreach ($cmd in $commands) {
        Write-Host "Testing: $cmd" -ForegroundColor Yellow
        
        try {
            Send-MCSSCommand -Command $cmd
            Start-Sleep -Seconds 1
            
            $output = Get-MCSSConsole -Lines 5
            $results += @{
                Command = $cmd
                Success = $true
                Output = $output
            }
            
            Write-Host "✓ Success" -ForegroundColor Green
        } catch {
            $results += @{
                Command = $cmd
                Success = $false
                Error = $_.Exception.Message
            }
            
            Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    
    return $results
}

function Watch-MCSSConsole {
    param([int]$IntervalSeconds = 2)
    
    Write-Host "Watching console (Press Ctrl+C to stop)..." -ForegroundColor Green
    
    try {
        while ($true) {
            $logs = Get-MCSSConsole -Lines 10
            
            foreach ($log in $logs) {
                $timestamp = Get-Date -Format "HH:mm:ss"
                Write-Host "[$timestamp] $log"
            }
            
            Start-Sleep -Seconds $IntervalSeconds
        }
    } catch [System.Management.Automation.PipelineStoppedException] {
        Write-Host "`nConsole monitoring stopped." -ForegroundColor Yellow
    }
}

# Export functions for use in other scripts
Export-ModuleMember -Function Get-MCSSConsole, Send-MCSSCommand, Test-RVNKToolsPlugin, Watch-MCSSConsole
```

This comprehensive guide provides all the tools and examples needed to integrate MCSS API access into VS Code extensions and development workflows, enabling seamless console access and server management during Minecraft plugin development.
