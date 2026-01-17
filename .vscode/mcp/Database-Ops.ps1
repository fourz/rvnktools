<#
.SYNOPSIS
    MCP-based Database Operations Tool

.DESCRIPTION
    This script performs database operations on Minecraft servers
    via the RVNKDev MCP server.

.PARAMETER Action
    Database action: list-tables, list-databases, describe, query, test

.PARAMETER Table
    Table name for describe action

.PARAMETER Query
    SQL query for query action

.PARAMETER Connection
    Connection name (default: default)

.PARAMETER Debug
    Enable debug output

.EXAMPLE
    .\Database-Ops.ps1 list-tables
    .\Database-Ops.ps1 describe -Table players
    .\Database-Ops.ps1 query -Query "SELECT * FROM players LIMIT 10"
    .\Database-Ops.ps1 test
#>

param(
    [Parameter(Position=0)]
    [ValidateSet("list-tables", "list-databases", "describe", "query", "test", "help")]
    [string]$Action = "help",

    [Parameter()]
    [string]$Table = "",

    [Parameter()]
    [string]$Query = "",

    [Parameter()]
    [string]$Connection = "default",

    [Parameter()]
    [switch]$Debug
)

$ErrorActionPreference = "Stop"

# Import MCP client module
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Import-Module (Join-Path $scriptDir "lib\MCP-Client.psm1") -Force

function Invoke-ListTables {
    param([string]$ConnName, [switch]$DebugMode)

    Write-Host "=== LIST TABLES ===" -ForegroundColor Cyan
    Write-Host "Connection: $ConnName"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "database_tools" -Arguments @{
            action = "list_tables"
            connection_name = $ConnName
        }

        if ($result.success) {
            $data = $result.data
            if ($data -is [array]) {
                Write-Host "Tables:" -ForegroundColor Yellow
                foreach ($table in $data) {
                    Write-Host "  - $table"
                }
            } else {
                $data | ConvertTo-Json -Depth 5 | Write-Host
            }
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-ListDatabases {
    param([string]$ConnName, [switch]$DebugMode)

    Write-Host "=== LIST DATABASES ===" -ForegroundColor Cyan
    Write-Host "Connection: $ConnName"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "database_tools" -Arguments @{
            action = "list_databases"
            connection_name = $ConnName
        }

        if ($result.success) {
            $data = $result.data
            if ($data -is [array]) {
                Write-Host "Databases:" -ForegroundColor Yellow
                foreach ($db in $data) {
                    Write-Host "  - $db"
                }
            } else {
                $data | ConvertTo-Json -Depth 5 | Write-Host
            }
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-DescribeTable {
    param([string]$TableName, [string]$ConnName, [switch]$DebugMode)

    Write-Host "=== DESCRIBE TABLE ===" -ForegroundColor Cyan
    Write-Host "Connection: $ConnName"
    Write-Host "Table: $TableName"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "database_tools" -Arguments @{
            action = "describe"
            table_name = $TableName
            connection_name = $ConnName
        }

        if ($result.success) {
            $data = $result.data
            if ($data -is [array]) {
                Write-Host "Columns:" -ForegroundColor Yellow
                foreach ($col in $data) {
                    if ($col -is [PSCustomObject] -or $col -is [hashtable]) {
                        $name = $col.Field
                        $type = $col.Type
                        $null = if ($col.Null -eq "YES") { "NULL" } else { "NOT NULL" }
                        $key = if ($col.Key) { " [$($col.Key)]" } else { "" }
                        Write-Host "  $name : $type $null$key"
                    } else {
                        Write-Host "  $col"
                    }
                }
            } else {
                $data | ConvertTo-Json -Depth 5 | Write-Host
            }
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-Query {
    param([string]$SqlQuery, [string]$ConnName, [switch]$DebugMode)

    Write-Host "=== EXECUTE QUERY ===" -ForegroundColor Cyan
    Write-Host "Connection: $ConnName"
    Write-Host "Query: $SqlQuery"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "database_tools" -Arguments @{
            action = "query"
            query = $SqlQuery
            connection_name = $ConnName
        }

        if ($result.success) {
            $data = $result.data
            if ($data -is [array] -and $data.Count -gt 0) {
                # Try to format as table
                Write-Host "Results ($($data.Count) rows):" -ForegroundColor Yellow
                $data | Format-Table -AutoSize | Out-String | Write-Host
            } elseif ($data) {
                $data | ConvertTo-Json -Depth 5 | Write-Host
            } else {
                Write-Host "Query executed successfully (no results)"
            }
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-TestConnection {
    param([string]$ConnName, [switch]$DebugMode)

    Write-Host "=== TEST CONNECTION ===" -ForegroundColor Cyan
    Write-Host "Connection: $ConnName"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "database_tools" -Arguments @{
            action = "test"
            connection_name = $ConnName
        }

        if ($result.success) {
            Write-Host "Connection successful!" -ForegroundColor Green
            if ($result.data) {
                $result.data | ConvertTo-Json -Depth 5 | Write-Host
            }
        } else {
            Write-Host "Connection failed!" -ForegroundColor Red
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Show-Help {
    Write-Host ""
    Write-Host "Database-Ops.ps1 - MCP-based Database Operations Tool" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\Database-Ops.ps1 list-tables [-Connection default]"
    Write-Host "  .\Database-Ops.ps1 list-databases [-Connection default]"
    Write-Host "  .\Database-Ops.ps1 describe -Table tablename [-Connection default]"
    Write-Host "  .\Database-Ops.ps1 query -Query 'SELECT * FROM table' [-Connection default]"
    Write-Host "  .\Database-Ops.ps1 test [-Connection default]"
    Write-Host ""
    Write-Host "Options:" -ForegroundColor Yellow
    Write-Host "  -Debug    Enable debug output"
}

# Main execution
switch ($Action.ToLower()) {
    "list-tables" {
        Invoke-ListTables -ConnName $Connection -DebugMode:$Debug
    }
    "list-databases" {
        Invoke-ListDatabases -ConnName $Connection -DebugMode:$Debug
    }
    "describe" {
        if ([string]::IsNullOrWhiteSpace($Table)) {
            Write-Error "Table parameter is required for describe action"
            exit 1
        }
        Invoke-DescribeTable -TableName $Table -ConnName $Connection -DebugMode:$Debug
    }
    "query" {
        if ([string]::IsNullOrWhiteSpace($Query)) {
            Write-Error "Query parameter is required for query action"
            exit 1
        }
        Invoke-Query -SqlQuery $Query -ConnName $Connection -DebugMode:$Debug
    }
    "test" {
        Invoke-TestConnection -ConnName $Connection -DebugMode:$Debug
    }
    default {
        Show-Help
    }
}
