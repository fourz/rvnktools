<#
.SYNOPSIS
    MCP-based Server Control Tool

.DESCRIPTION
    This script controls Minecraft server state (start/stop/restart/reload)
    via the RVNKDev MCP server.

.PARAMETER Action
    Control action: start, stop, restart, reload

.PARAMETER Server
    Server alias or ID (default: rvnk-test)

.PARAMETER Wait
    Wait and verify state change

.PARAMETER Debug
    Enable debug output

.EXAMPLE
    .\Control-Server.ps1 start
    .\Control-Server.ps1 restart -Server rvnk-test -Wait
    .\Control-Server.ps1 reload
#>

param(
    [Parameter(Position=0)]
    [ValidateSet("start", "stop", "restart", "reload", "help")]
    [string]$Action = "help",

    [Parameter()]
    [string]$Server = "rvnk-test",

    [Parameter()]
    [switch]$Wait,

    [Parameter()]
    [switch]$Debug
)

$ErrorActionPreference = "Stop"

# Wait times before verifying state change
$WaitTimes = @{
    "stop" = 30
    "start" = 45
    "restart" = 60
}

# Import MCP client module
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Import-Module (Join-Path $scriptDir "lib\MCP-Client.psm1") -Force

function Invoke-ServerControl {
    param(
        [string]$ServerAlias,
        [string]$ControlAction,
        [switch]$WaitForCompletion,
        [switch]$DebugMode
    )

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    $actionNames = @{
        "start" = "START SERVER"
        "stop" = "STOP SERVER"
        "restart" = "RESTART SERVER"
        "reload" = "RELOAD PLUGINS"
    }

    Write-Host "=== $($actionNames[$ControlAction]) ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host "Type: $($serverInfo.Type)"
    Write-Host ""

    if (-not (Test-MCPWriteAllowed -Identifier $ServerAlias)) {
        Write-Host "ERROR: Server '$ServerAlias' is production - control operations blocked" -ForegroundColor Red
        Write-Host "Only test servers allow control operations."
        exit 1
    }

    $client = New-MCPClient -Debug:$DebugMode
    try {
        if ($ControlAction -eq "reload") {
            # Reload uses console command
            Write-Host "Sending reload command..."
            $result = Invoke-MCPTool -Client $client -ToolName "send_console_command" -Arguments @{
                server_id = $serverId
                command = "reload"
            }

            if ($result.success) {
                Write-Host "Reload command sent successfully" -ForegroundColor Green
            } else {
                Write-Error "Error: $($result.error)"
            }
        } else {
            # Start/stop/restart use set_server_state
            Write-Host "Sending $ControlAction command..."
            $result = Invoke-MCPTool -Client $client -ToolName "set_server_state" -Arguments @{
                server_id = $serverId
                action = $ControlAction
            }

            if ($result.success) {
                Write-Host "Command accepted. Current state: $($result.state)" -ForegroundColor Green

                if ($WaitForCompletion) {
                    $waitTime = $WaitTimes[$ControlAction]
                    Write-Host ""
                    Write-Host "Waiting ${waitTime}s for operation to complete..." -ForegroundColor Yellow
                    Start-Sleep -Seconds $waitTime

                    # Verify final state
                    Write-Host "Verifying final state..."
                    $status = Invoke-MCPTool -Client $client -ToolName "get_server_state" -Arguments @{
                        server_id = $serverId
                    }
                    Write-Host "Final state: $($status.state)"

                    $expected = if ($ControlAction -eq "stop") { "offline" } else { "running" }
                    if ($status.state -eq $expected) {
                        Write-Host "SUCCESS: Server is now $expected" -ForegroundColor Green
                    } else {
                        Write-Host "WARNING: Expected $expected, got $($status.state)" -ForegroundColor Yellow
                    }
                }
            } else {
                Write-Error "Error: $($result.error)"
            }
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Show-Help {
    Write-Host ""
    Write-Host "Control-Server.ps1 - MCP-based Server Control Tool" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\Control-Server.ps1 start [-Server rvnk-test] [-Wait]"
    Write-Host "  .\Control-Server.ps1 stop [-Server rvnk-test] [-Wait]"
    Write-Host "  .\Control-Server.ps1 restart [-Server rvnk-test] [-Wait]"
    Write-Host "  .\Control-Server.ps1 reload [-Server rvnk-test]"
    Write-Host ""
    Write-Host "Available Servers:" -ForegroundColor Yellow
    $servers = Get-MCPServerList
    foreach ($srv in $servers) {
        $access = if ($srv.Type -eq "test") { "Full access" } else { "Read-only" }
        Write-Host "  $($srv.Alias): $($srv.Name) ($($srv.Id)) [$access]"
    }
}

# Main execution
switch ($Action.ToLower()) {
    "start" {
        Invoke-ServerControl -ServerAlias $Server -ControlAction "start" -WaitForCompletion:$Wait -DebugMode:$Debug
    }
    "stop" {
        Invoke-ServerControl -ServerAlias $Server -ControlAction "stop" -WaitForCompletion:$Wait -DebugMode:$Debug
    }
    "restart" {
        Invoke-ServerControl -ServerAlias $Server -ControlAction "restart" -WaitForCompletion:$Wait -DebugMode:$Debug
    }
    "reload" {
        Invoke-ServerControl -ServerAlias $Server -ControlAction "reload" -DebugMode:$Debug
    }
    default {
        Show-Help
    }
}
