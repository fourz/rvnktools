<#
.SYNOPSIS
    MCP-based File Operations Tool

.DESCRIPTION
    This script performs file operations on Minecraft servers
    via the RVNKDev MCP server.

.PARAMETER Action
    File action: list, read, upload, delete, download

.PARAMETER Path
    Remote path for list/read/delete operations

.PARAMETER LocalPath
    Local file path for upload/download

.PARAMETER RemotePath
    Remote file path for upload/download

.PARAMETER Server
    Server alias or ID (default: rvnk-test)

.PARAMETER Force
    Skip confirmation for delete operations

.PARAMETER Debug
    Enable debug output

.EXAMPLE
    .\File-Ops.ps1 list /plugins
    .\File-Ops.ps1 read /plugins/RVNKTools/config.yml
    .\File-Ops.ps1 upload -LocalPath .\target\plugin.jar -RemotePath /plugins
    .\File-Ops.ps1 delete /plugins/old-plugin.jar -Force
#>

param(
    [Parameter(Position=0)]
    [ValidateSet("list", "read", "upload", "delete", "download", "help")]
    [string]$Action = "help",

    [Parameter(Position=1)]
    [string]$Path = "/",

    [Parameter()]
    [string]$LocalPath = "",

    [Parameter()]
    [string]$RemotePath = "",

    [Parameter()]
    [string]$Server = "rvnk-test",

    [Parameter()]
    [switch]$Force,

    [Parameter()]
    [switch]$Debug
)

$ErrorActionPreference = "Stop"

# Import MCP client module
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Import-Module (Join-Path $scriptDir "lib\MCP-Client.psm1") -Force

function Invoke-ListDirectory {
    param([string]$ServerAlias, [string]$RemPath, [switch]$DebugMode)

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    Write-Host "=== LIST DIRECTORY ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host "Path: $RemPath"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "file_read" -Arguments @{
            action = "list"
            server_id = $serverId
            remote_path = $RemPath
        }

        if ($result.success) {
            $data = $result.data
            if ($data -is [array]) {
                foreach ($item in $data) {
                    if ($item -is [PSCustomObject] -or $item -is [hashtable]) {
                        $name = if ($item.name) { $item.name } else { $item.filename }
                        $ftype = if ($item.type) { $item.type } else { $item.is_dir }
                        if ($ftype -eq $true -or $ftype -eq "directory") {
                            Write-Host "  [DIR]  $name"
                        } else {
                            $size = $item.size
                            if ($size) {
                                Write-Host "  [FILE] $name ($size bytes)"
                            } else {
                                Write-Host "  [FILE] $name"
                            }
                        }
                    } else {
                        Write-Host "  $item"
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

function Invoke-ReadFile {
    param([string]$ServerAlias, [string]$RemPath, [switch]$DebugMode)

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    Write-Host "=== READ FILE ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host "Path: $RemPath"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        $result = Invoke-MCPTool -Client $client -ToolName "file_read" -Arguments @{
            action = "read"
            server_id = $serverId
            remote_path = $RemPath
        }

        if ($result.success) {
            $content = if ($result.data) { $result.data } else { $result.content }
            Write-Host $content
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-UploadFile {
    param([string]$ServerAlias, [string]$LocPath, [string]$RemPath, [switch]$DebugMode)

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    Write-Host "=== UPLOAD FILE ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host "Local: $LocPath"
    Write-Host "Remote: $RemPath"
    Write-Host ""

    if (-not (Test-MCPWriteAllowed -Identifier $ServerAlias)) {
        Write-Host "ERROR: Server '$ServerAlias' is production - upload blocked" -ForegroundColor Red
        Write-Host "Only test servers allow file uploads."
        exit 1
    }

    # Verify local file exists
    if (-not (Test-Path $LocPath)) {
        Write-Error "Local file not found: $LocPath"
        exit 1
    }

    $fullPath = (Resolve-Path $LocPath).Path

    $client = New-MCPClient -Debug:$DebugMode
    try {
        Write-Host "Uploading $(Split-Path $LocPath -Leaf)..."
        $result = Invoke-MCPTool -Client $client -ToolName "file_write" -Arguments @{
            action = "upload"
            server_id = $serverId
            local_path = $fullPath
            remote_path = $RemPath
        }

        if ($result.success) {
            Write-Host "Upload completed successfully" -ForegroundColor Green
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-DeleteFile {
    param([string]$ServerAlias, [string]$RemPath, [switch]$ForceDelete, [switch]$DebugMode)

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    Write-Host "=== DELETE FILE ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host "Path: $RemPath"
    Write-Host ""

    if (-not (Test-MCPWriteAllowed -Identifier $ServerAlias)) {
        Write-Host "ERROR: Server '$ServerAlias' is production - delete blocked" -ForegroundColor Red
        Write-Host "Only test servers allow file deletion."
        exit 1
    }

    if (-not $ForceDelete) {
        $confirm = Read-Host "Delete $RemPath? [y/N]"
        if ($confirm -ne 'y') {
            Write-Host "Cancelled"
            return
        }
    }

    $client = New-MCPClient -Debug:$DebugMode
    try {
        Write-Host "Deleting file..."
        $result = Invoke-MCPTool -Client $client -ToolName "file_write" -Arguments @{
            action = "delete"
            server_id = $serverId
            remote_path = $RemPath
        }

        if ($result.success) {
            Write-Host "File deleted successfully" -ForegroundColor Green
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Invoke-DownloadFile {
    param([string]$ServerAlias, [string]$RemPath, [string]$LocPath, [switch]$DebugMode)

    $serverId = Resolve-MCPServer -Identifier $ServerAlias
    $serverInfo = Get-MCPServerInfo -Identifier $ServerAlias

    Write-Host "=== DOWNLOAD FILE ===" -ForegroundColor Cyan
    Write-Host "Server: $($serverInfo.Name) ($serverId)"
    Write-Host "Remote: $RemPath"
    Write-Host "Local: $LocPath"
    Write-Host ""

    $client = New-MCPClient -Debug:$DebugMode
    try {
        Write-Host "Downloading file..."
        $result = Invoke-MCPTool -Client $client -ToolName "file_read" -Arguments @{
            action = "download"
            server_id = $serverId
            remote_path = $RemPath
            local_path = $LocPath
        }

        if ($result.success) {
            Write-Host "Downloaded to $LocPath" -ForegroundColor Green
        } else {
            Write-Error "Error: $($result.error)"
        }
    } finally {
        Close-MCPClient -Client $client
    }
}

function Show-Help {
    Write-Host ""
    Write-Host "File-Ops.ps1 - MCP-based File Operations Tool" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\File-Ops.ps1 list [/path] [-Server rvnk-test]"
    Write-Host "  .\File-Ops.ps1 read /path/to/file [-Server rvnk-test]"
    Write-Host "  .\File-Ops.ps1 upload -LocalPath .\file.jar -RemotePath /plugins [-Server rvnk-test]"
    Write-Host "  .\File-Ops.ps1 delete /path/to/file [-Server rvnk-test] [-Force]"
    Write-Host "  .\File-Ops.ps1 download -RemotePath /path/to/file -LocalPath .\local.file [-Server rvnk-test]"
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
    "list" {
        Invoke-ListDirectory -ServerAlias $Server -RemPath $Path -DebugMode:$Debug
    }
    "read" {
        Invoke-ReadFile -ServerAlias $Server -RemPath $Path -DebugMode:$Debug
    }
    "upload" {
        if ([string]::IsNullOrWhiteSpace($LocalPath)) {
            Write-Error "LocalPath parameter is required for upload"
            exit 1
        }
        $remoteDest = if ($RemotePath) { $RemotePath } else { $Path }
        Invoke-UploadFile -ServerAlias $Server -LocPath $LocalPath -RemPath $remoteDest -DebugMode:$Debug
    }
    "delete" {
        Invoke-DeleteFile -ServerAlias $Server -RemPath $Path -ForceDelete:$Force -DebugMode:$Debug
    }
    "download" {
        if ([string]::IsNullOrWhiteSpace($LocalPath)) {
            Write-Error "LocalPath parameter is required for download"
            exit 1
        }
        $remoteSrc = if ($RemotePath) { $RemotePath } else { $Path }
        Invoke-DownloadFile -ServerAlias $Server -RemPath $remoteSrc -LocPath $LocalPath -DebugMode:$Debug
    }
    default {
        Show-Help
    }
}
