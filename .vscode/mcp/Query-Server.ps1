<#
.SYNOPSIS
    MCP-based Server Query Tool

.DESCRIPTION
    This script queries Minecraft server status, console output, and sends commands
    via the RVNKDev MCP server.

.PARAMETER QueryType
    Type of query: console, status, stats, command

.PARAMETER Lines
    Number of console lines to retrieve (default: 50)

.PARAMETER Server
    Server alias or ID (default: rvnk-test)

.PARAMETER Command
    Command to send (for 'command' query type)

.PARAMETER Debug
    Enable debug output

.EXAMPLE
    .\Query-Server.ps1 console -Lines 100
    .\Query-Server.ps1 status -Server rvnk-test
    .\Query-Server.ps1 command -Command "say Hello"
#>

param(
    [Parameter(Position=0)]
    [ValidateSet("console", "status", "stats", "command", "help")]
    [string]$QueryType = "help",

    [Parameter()]
    [int]$Lines = 50,

    [Parameter()]
    [string]$Server = "rvnk-test",

    [Parameter()]
    [string]$Command = "",

    [Parameter()]
    [switch]$Debug
)

$ErrorActionPreference = "Stop"

# Import MCP client module
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Import-Module (Join-Path $scriptDir "lib\MCP-Client.psm1") -Force

function Invoke-ConsoleQuery {
    param([string]$ServerAlias, [int]$LineCount, [switch]$DebugMode)

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    Write-Host "=== CONSOLE OUTPUT ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host "Lines: $LineCount"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "get_console_output" -Arguments @{
            server_id = $serverId
            lines = $LineCount
        }

        if ($result.success) {
            $data = $result.data
            if ($data -is [array]) {
                foreach ($line in $data) {
                    Write-Host $line
                }
            } else {
                Write-Host $data
            }
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-StatusQuery {
    param([string]$ServerAlias, [switch]$DebugMode)

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    Write-Host "=== SERVER STATUS ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "get_server_state" -Arguments @{
            server_id = $serverId
        }

        Write-Host "State: $($result.state)"
        Write-Host "CPU: $($result.cpu_percent)%"
        Write-Host "Memory: $($result.memory_percent)%"
        Write-Host "Players: $($result.players_online)"
        Write-Host "Uptime: $($result.uptime)"
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-StatsQuery {
    param([string]$ServerAlias, [switch]$DebugMode)

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    Write-Host "=== SERVER STATISTICS ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "get_server_state" -Arguments @{
            server_id = $serverId
        }

        $result | ConvertTo-Json -Depth 5 | Write-Host
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-CommandExecution {
    param([string]$ServerAlias, [string]$Cmd, [switch]$DebugMode)

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    Write-Host "=== SEND COMMAND ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host "Command: $Cmd"
    Write-Host ""

    if (-not (Test-MCPWriteAllowed -Identifier $ServerAlias)) {
        Write-Host "ERROR: Server '$ServerAlias' is production - command execution blocked" -ForegroundColor Red
        Write-Host "Only test servers allow command execution."
        exit 1
    }

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "send_console_command" -Arguments @{
            server_id = $serverId
            command = $Cmd
        }

        if ($result.success) {
            Write-Host "Command sent successfully" -ForegroundColor Green
            if ($result.data) {
                Write-Host "Response: $($result.data)"
            }
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Show-Help {
    Write-Host ""
    Write-Host "Query-Server.ps1 - MCP-based Server Query Tool" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\Query-Server.ps1 console [-Lines 50] [-Server rvnk-test]"
    Write-Host "  .\Query-Server.ps1 status [-Server rvnk-test]"
    Write-Host "  .\Query-Server.ps1 stats [-Server rvnk-test]"
    Write-Host "  .\Query-Server.ps1 command -Command 'say Hello' [-Server rvnk-test]"
    Write-Host ""
    Write-Host "Available Servers:" -ForegroundColor Yellow
    $servers = Get-MCPServerList
    foreach ($srv in $servers) {
        $access = if ($srv.Type -eq "test") { "Full access" } else { "Read-only" }
        Write-Host "  $($srv.Alias): $($srv.Name) ($($srv.Id)) [$access]"
    }
}

# Main execution
switch ($QueryType.ToLower()) {
    "console" {
        Invoke-ConsoleQuery -ServerAlias $Server -LineCount $Lines -DebugMode:$Debug
    }
    "status" {
        Invoke-StatusQuery -ServerAlias $Server -DebugMode:$Debug
    }
    "stats" {
        Invoke-StatsQuery -ServerAlias $Server -DebugMode:$Debug
    }
    "command" {
        if ([string]::IsNullOrWhiteSpace($Command)) {
            Write-Error "Command parameter is required for command execution"
            exit 1
        }
        Invoke-CommandExecution -ServerAlias $Server -Cmd $Command -DebugMode:$Debug
    }
    default {
        Show-Help
    }
}
