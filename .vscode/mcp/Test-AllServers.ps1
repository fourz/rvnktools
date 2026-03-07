<#
.SYNOPSIS
    MCP Server Test Suite

.DESCRIPTION
    This script provides a test matrix for validating MCP tools against all configured servers.
    Actual API calls are handled by the RVNKDev MCP server via Claude Code.

.PARAMETER Verbose
    Show detailed test instructions

.EXAMPLE
    .\Test-AllServers.ps1
    .\Test-AllServers.ps1 -Verbose

.NOTES
    This script generates a comprehensive test plan with MCP tool invocation instructions
    for each configured server.
#>

param(
    [switch]$ShowDetail
)

$ErrorActionPreference = "Stop"

# Import MCP client module
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Import-Module (Join-Path $scriptDir "lib\MCP-Client.psm1") -Force

function Write-Header {
    param([string]$Title)
    Write-Host ""
    Write-Host ("=" * 60) -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host ("=" * 60) -ForegroundColor Cyan
}

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "--- $Title ---" -ForegroundColor Yellow
}

function Get-TestPlan {
    param(
        [string]$ServerId,
        [string]$ServerName,
        [string]$ServerType,
        [bool]$ShowVerbose
    )

    Write-Section "Server: $ServerName ($ServerId)"
    Write-Host "Type: $ServerType"
    $access = if ($ServerType -eq "test") { "Full" } else { "Read-only" }
    Write-Host "Access: $access"

    $tests = @(
        @{
            Name = "Server Status"
            Tool = "get_server_state"
            ReadOnly = $true
            Call = "mcp__rvnkdev-minecraft-server__get_server_state(server_id=`"$ServerId`")"
            Expected = "Returns state, cpu_percent, memory_percent, players_online"
        },
        @{
            Name = "Console Output"
            Tool = "get_console_output"
            ReadOnly = $true
            Call = "mcp__rvnkdev-minecraft-server__get_console_output(server_id=`"$ServerId`", lines=10)"
            Expected = "Returns recent console log lines"
        },
        @{
            Name = "File List (root)"
            Tool = "file_read"
            ReadOnly = $true
            Call = "mcp__rvnkdev-minecraft-server__file_read(action=`"list`", server_id=`"$ServerId`", remote_path=`"/`")"
            Expected = "Returns directory listing"
        },
        @{
            Name = "File List (plugins)"
            Tool = "file_read"
            ReadOnly = $true
            Call = "mcp__rvnkdev-minecraft-server__file_read(action=`"list`", server_id=`"$ServerId`", remote_path=`"/plugins`")"
            Expected = "Returns plugin directory listing"
        }
    )

    # Add write tests for test servers only
    if ($ServerType -eq "test") {
        $tests += @(
            @{
                Name = "Send Command (list)"
                Tool = "send_console_command"
                ReadOnly = $false
                Call = "mcp__rvnkdev-minecraft-server__send_console_command(server_id=`"$ServerId`", command=`"list`")"
                Expected = "Returns command execution result"
            },
            @{
                Name = "Server Restart (CAUTION)"
                Tool = "set_server_state"
                ReadOnly = $false
                Call = "mcp__rvnkdev-minecraft-server__set_server_state(server_id=`"$ServerId`", action=`"restart`")"
                Expected = "Initiates server restart (wait 60s for completion)"
            }
        )
    }

    Write-Host ""
    Write-Host "Test Cases:"
    $i = 1
    foreach ($test in $tests) {
        $accessTag = if ($test.ReadOnly) { "[R]" } else { "[W]" }
        Write-Host "  $i. $accessTag $($test.Name)"
        if ($ShowVerbose) {
            Write-Host "      Tool: $($test.Tool)" -ForegroundColor DarkGray
            Write-Host "      Call: $($test.Call)" -ForegroundColor DarkGray
            Write-Host "      Expected: $($test.Expected)" -ForegroundColor DarkGray
            Write-Host ""
        }
        $i++
    }
}

# Main execution
Write-Header "MCP Server Test Suite"
Write-Host "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"

try {
    $servers = Get-MCPServerList
} catch {
    Write-Error "Failed to load server configuration: $_"
    exit 1
}

$testServers = $servers | Where-Object { $_.Type -eq "test" }
$prodServers = $servers | Where-Object { $_.Type -eq "production" }

Write-Host ""
Write-Host "Configured Servers: $($servers.Count) total"
Write-Host "  - Test servers: $($testServers.Count) (full access)"
Write-Host "  - Production servers: $($prodServers.Count) (read-only)"

# Test servers first
if ($testServers) {
    Write-Header "TEST SERVERS (Full Access)"
    foreach ($server in $testServers) {
        Get-TestPlan -ServerId $server.Id -ServerName $server.Name -ServerType $server.Type -ShowVerbose $ShowDetail
    }
}

# Production servers (read-only)
if ($prodServers) {
    Write-Header "PRODUCTION SERVERS (Read-Only)"
    foreach ($server in $prodServers) {
        Get-TestPlan -ServerId $server.Id -ServerName $server.Name -ServerType $server.Type -ShowVerbose $ShowDetail
    }
}

Write-Header "Test Execution Instructions"
Write-Host @"

To run these tests, use Claude Code with the MCP tools listed above.

Test Order:
1. Start with read-only tests on all servers
2. Verify all servers respond correctly
3. For test servers only, run write operations
4. Verify restart completes successfully (wait 60s)

Database Tests (separate from server tests):
  mcp__rvnkdev-minecraft-server__database_tools(action="test")
  mcp__rvnkdev-minecraft-server__database_tools(action="list_tables")
  mcp__rvnkdev-minecraft-server__database_tools(action="list_databases")

Report any failures to the development team.
"@
