<#
.SYNOPSIS
    MCP Client Module for RVNKDev Server Management

.DESCRIPTION
    This module provides a JSON-RPC client for communicating with the RVNKDev MCP server.
    It launches the MCP server as a subprocess and communicates via stdin/stdout.

.EXAMPLE
    Import-Module .\MCP-Client.psm1
    $client = New-MCPClient
    $result = Invoke-MCPTool -Client $client -ToolName "get_server_state" -Arguments @{server_id = "b2bc4d7e"}
    Close-MCPClient -Client $client
#>

# MCP Server configuration
$script:MCPExecutable = "c:\tools\_PROJECTS\Ravenkaft Dev\repos\rvnkdev-mcp-server\rvnkdev-fastmcp-server\.venv\Scripts\rvnkdev-mcp.exe"
$script:MCPConfigPath = "c:/tools/_PROJECTS/Ravenkaft Dev/repos/rvnkdev-mcp-server/rvnkdev-fastmcp-server/config.yaml"
$script:MCPJsonPath = "c:\tools\_PROJECTS\Ravenkaft Dev\.mcp.json"

# Module-level variables
$script:Config = $null
$script:ConfigPath = $null
$script:RequestId = 0

function Get-MCPEnvFromJson {
    <#
    .SYNOPSIS
        Load environment variables from .mcp.json (includes BW_SESSION).
    #>
    try {
        if (Test-Path $script:MCPJsonPath) {
            $mcpConfig = Get-Content $script:MCPJsonPath -Raw | ConvertFrom-Json
            $serverConfig = $mcpConfig.mcpServers.'rvnkdev-minecraft-server'
            if ($serverConfig -and $serverConfig.env) {
                return $serverConfig.env
            }
        }
    } catch {
        # Ignore errors
    }
    return $null
}

function Initialize-MCPConfig {
    <#
    .SYNOPSIS
        Initialize the MCP client configuration.
    #>
    param(
        [string]$ConfigPath
    )

    if (-not $ConfigPath) {
        # Find config relative to module
        $moduleDir = Split-Path -Parent $PSScriptRoot
        $ConfigPath = Join-Path $moduleDir "config\servers.json"

        if (-not (Test-Path $ConfigPath)) {
            $ConfigPath = Join-Path (Get-Location) ".vscode\mcp\config\servers.json"
        }
    }

    if (-not (Test-Path $ConfigPath)) {
        throw "Config file not found: $ConfigPath"
    }

    $script:ConfigPath = $ConfigPath
    $script:Config = Get-Content $ConfigPath -Raw | ConvertFrom-Json
}

function New-MCPClient {
    <#
    .SYNOPSIS
        Create a new MCP client instance.

    .PARAMETER Debug
        Enable debug output

    .EXAMPLE
        $client = New-MCPClient
        $client = New-MCPClient -Debug
    #>
    param(
        [switch]$Debug
    )

    if ($Debug) {
        Write-Host "[DEBUG] Starting MCP server: $script:MCPExecutable" -ForegroundColor Gray
    }

    # Start the MCP server process
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $script:MCPExecutable
    $psi.Arguments = "--transport stdio"
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    # Load env from .mcp.json (includes BW_SESSION)
    $mcpEnv = Get-MCPEnvFromJson
    if ($mcpEnv) {
        $mcpEnv.PSObject.Properties | ForEach-Object {
            $psi.EnvironmentVariables[$_.Name] = $_.Value
        }
    }

    # Override some settings
    $psi.EnvironmentVariables["CONFIG_PATH"] = $script:MCPConfigPath
    $psi.EnvironmentVariables["LOG_LEVEL"] = "ERROR"
    $psi.EnvironmentVariables["FASTMCP_ENV"] = "production"

    $process = [System.Diagnostics.Process]::Start($psi)

    $client = [PSCustomObject]@{
        Process = $process
        Debug = $Debug
        RequestId = 0
    }

    # Initialize MCP connection
    Initialize-MCPConnection -Client $client

    return $client
}

function Close-MCPClient {
    <#
    .SYNOPSIS
        Close MCP client and stop the server process.
    #>
    param(
        [Parameter(Mandatory)]
        [PSCustomObject]$Client
    )

    try {
        $Client.Process.StandardInput.Close()
        $Client.Process.WaitForExit(5000)
    } catch {
        # Ignore errors during cleanup
    } finally {
        if (-not $Client.Process.HasExited) {
            $Client.Process.Kill()
        }
        $Client.Process.Dispose()
    }
}

function Send-MCPRequest {
    <#
    .SYNOPSIS
        Send JSON-RPC request to MCP server.
    #>
    param(
        [Parameter(Mandatory)]
        [PSCustomObject]$Client,

        [Parameter(Mandatory)]
        [string]$Method,

        [hashtable]$Params
    )

    $Client.RequestId++
    $request = @{
        jsonrpc = "2.0"
        id = $Client.RequestId
        method = $Method
    }

    if ($Params) {
        $request.params = $Params
    }

    $requestJson = $request | ConvertTo-Json -Compress -Depth 10

    if ($Client.Debug) {
        Write-Host "[DEBUG] -> $requestJson" -ForegroundColor Gray
    }

    try {
        $Client.Process.StandardInput.WriteLine($requestJson)
        $Client.Process.StandardInput.Flush()

        $responseLine = $Client.Process.StandardOutput.ReadLine()

        if (-not $responseLine) {
            throw "No response from MCP server"
        }

        if ($Client.Debug) {
            Write-Host "[DEBUG] <- $responseLine" -ForegroundColor Gray
        }

        $response = $responseLine | ConvertFrom-Json

        if ($response.error) {
            throw "MCP error $($response.error.code): $($response.error.message)"
        }

        return $response.result
    } catch {
        throw "MCP communication error: $_"
    }
}

function Send-MCPNotification {
    <#
    .SYNOPSIS
        Send JSON-RPC notification to MCP server (no response expected).
    #>
    param(
        [Parameter(Mandatory)]
        [PSCustomObject]$Client,

        [Parameter(Mandatory)]
        [string]$Method,

        [hashtable]$Params
    )

    $notification = @{
        jsonrpc = "2.0"
        method = $Method
    }

    if ($Params) {
        $notification.params = $Params
    }

    $notificationJson = $notification | ConvertTo-Json -Compress -Depth 10

    if ($Client.Debug) {
        Write-Host "[DEBUG] -> $notificationJson" -ForegroundColor Gray
    }

    $Client.Process.StandardInput.WriteLine($notificationJson)
    $Client.Process.StandardInput.Flush()
}

function Initialize-MCPConnection {
    <#
    .SYNOPSIS
        Initialize MCP connection with handshake.
    #>
    param(
        [Parameter(Mandatory)]
        [PSCustomObject]$Client
    )

    # Send initialize request
    $result = Send-MCPRequest -Client $Client -Method "initialize" -Params @{
        protocolVersion = "2024-11-05"
        capabilities = @{}
        clientInfo = @{
            name = "rvnkdev-cli-ps"
            version = "1.0.0"
        }
    }

    if ($Client.Debug) {
        Write-Host "[DEBUG] Server capabilities: $($result | ConvertTo-Json -Compress)" -ForegroundColor Gray
    }

    # Send initialized notification
    Send-MCPNotification -Client $Client -Method "notifications/initialized"
}

function Invoke-MCPTool {
    <#
    .SYNOPSIS
        Call an MCP tool.

    .PARAMETER Client
        MCP client instance

    .PARAMETER ToolName
        Name of the tool (e.g., "get_server_state")

    .PARAMETER Arguments
        Tool arguments as hashtable

    .EXAMPLE
        Invoke-MCPTool -Client $client -ToolName "get_server_state" -Arguments @{server_id = "b2bc4d7e"}
    #>
    param(
        [Parameter(Mandatory)]
        [PSCustomObject]$Client,

        [Parameter(Mandatory)]
        [string]$ToolName,

        [Parameter(Mandatory)]
        [hashtable]$Arguments
    )

    $result = Send-MCPRequest -Client $Client -Method "tools/call" -Params @{
        name = $ToolName
        arguments = $Arguments
    }

    # Extract content from MCP tool response
    if ($result.content -and $result.content.Count -gt 0) {
        foreach ($item in $result.content) {
            if ($item.type -eq "text") {
                try {
                    return $item.text | ConvertFrom-Json
                } catch {
                    return @{ text = $item.text }
                }
            }
        }
    }

    return $result
}

# Configuration helper functions

function Get-MCPServerList {
    <#
    .SYNOPSIS
        Get list of configured servers.
    #>
    param(
        [ValidateSet("test", "production")]
        [string]$Type
    )

    if (-not $script:Config) {
        Initialize-MCPConfig
    }

    $results = @()
    $servers = $script:Config.servers.PSObject.Properties

    foreach ($prop in $servers) {
        $server = $prop.Value

        if ($Type -and $server.type -ne $Type) {
            continue
        }

        $results += [PSCustomObject]@{
            Alias = $prop.Name
            Id = $server.id
            Name = $server.name
            Provider = $server.provider
            Type = $server.type
            Description = $server.description
        }
    }

    return $results
}

function Resolve-MCPServer {
    <#
    .SYNOPSIS
        Resolve server alias or ID to server ID.
    #>
    param(
        [Parameter(Mandatory)]
        [string]$Identifier
    )

    if (-not $script:Config) {
        Initialize-MCPConfig
    }

    $servers = $script:Config.servers.PSObject.Properties
    foreach ($prop in $servers) {
        if ($prop.Name -eq $Identifier) {
            return $prop.Value.id
        }
        if ($prop.Value.id -eq $Identifier) {
            return $Identifier
        }
    }

    throw "Unknown server: $Identifier"
}

function Get-MCPServerInfo {
    <#
    .SYNOPSIS
        Get full server information by alias or ID.
    #>
    param(
        [Parameter(Mandatory)]
        [string]$Identifier
    )

    if (-not $script:Config) {
        Initialize-MCPConfig
    }

    $servers = $script:Config.servers.PSObject.Properties
    foreach ($prop in $servers) {
        if ($prop.Name -eq $Identifier -or $prop.Value.id -eq $Identifier) {
            return [PSCustomObject]@{
                Alias = $prop.Name
                Id = $prop.Value.id
                Name = $prop.Value.name
                Provider = $prop.Value.provider
                Type = $prop.Value.type
                Description = $prop.Value.description
            }
        }
    }

    throw "Unknown server: $Identifier"
}

function Test-MCPWriteAllowed {
    <#
    .SYNOPSIS
        Check if write operations are allowed on a server.
    #>
    param(
        [Parameter(Mandatory)]
        [string]$Identifier
    )

    $server = Get-MCPServerInfo -Identifier $Identifier
    return $server.Type -eq "test"
}

# Export module functions
Export-ModuleMember -Function @(
    'New-MCPClient',
    'Close-MCPClient',
    'Invoke-MCPTool',
    'Get-MCPServerList',
    'Resolve-MCPServer',
    'Get-MCPServerInfo',
    'Test-MCPWriteAllowed'
)
