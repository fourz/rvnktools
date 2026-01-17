# Clean SQLite Database - Development Environment
# Removes SQLite .db files from the main plugin directory only
# Excludes backup files and subdirectories to preserve data safety
# Use when schema changes are expected to break existing version

param(
    [switch]$Force = $false,
    [switch]$ListOnly = $false
)

# Set the current location to the script root and load configuration
Set-Location -Path $PSScriptRoot
$config = Get-Content -Path .\project.json | ConvertFrom-Json

# Server Configuration from project.json - derive server path from destination path
$serverPath = Split-Path $config.DestinationPath -Parent
$SQLiteConfig = @{
    ServerPath = $serverPath
    PluginFolderName = $config.PluginFolder
}

function Write-Status {
    param([string]$Message, [string]$Type = "INFO")
    $timestamp = Get-Date -Format "HH:mm:ss"
    switch ($Type) {
        "INFO" { Write-Host "[$timestamp] $Message" -ForegroundColor Green }
        "WARN" { Write-Host "[$timestamp] WARNING: $Message" -ForegroundColor Yellow }
        "ERROR" { Write-Host "[$timestamp] ERROR: $Message" -ForegroundColor Red }
        "DEBUG" { Write-Host "[$timestamp] DEBUG: $Message" -ForegroundColor Gray }
    }
}

function Get-SQLiteFiles {
    $pluginsPath = Join-Path -Path $SQLiteConfig.ServerPath -ChildPath "plugins"
    
    if (-not (Test-Path $pluginsPath)) {
        Write-Status "Plugins folder not found: $pluginsPath" "ERROR"
        return @()
    }
    
    # Look for the plugin data folder - try common variations
    $possibleFolders = @(
        $SQLiteConfig.PluginFolderName,
        "RVNKTools",
        "rvnktools",
        "RVNK"
    )
    
    $pluginDataPath = $null
    foreach ($folderName in $possibleFolders) {
        $testPath = Join-Path -Path $pluginsPath -ChildPath $folderName
        if (Test-Path $testPath) {
            $pluginDataPath = $testPath
            Write-Status "Found plugin data folder: $folderName" "DEBUG"
            break
        }
    }
    
    if (-not $pluginDataPath) {
        Write-Status "Plugin data folder not found. Checked:" "WARN"
        foreach ($folderName in $possibleFolders) {
            Write-Status "  - $folderName" "DEBUG"
        }
        return @()
    }
    
    # Look for .db files ONLY in the main plugin directory (not subdirectories)
    # This targets the actual plugin databases and excludes backup/copy folders
    Write-Status "Checking main plugin directory for .db files" "DEBUG"
    $sqliteFiles = Get-ChildItem -Path $pluginDataPath -Filter "*.db" -File -ErrorAction SilentlyContinue
    
    if ($sqliteFiles.Count -gt 0) {
        Write-Status "Found $($sqliteFiles.Count) .db file(s) in main plugin directory" "DEBUG"
        foreach ($file in $sqliteFiles) {
            Write-Status "  - $($file.Name)" "DEBUG"
        }
    } else {
        Write-Status "No .db files found in main plugin directory" "DEBUG"
    }
    
    return $sqliteFiles
}

function Remove-SQLiteFiles {
    param([System.IO.FileInfo[]]$Files)
    
    if ($Files.Count -eq 0) {
        Write-Status "No SQLite database files found to remove"
        return $true
    }
    
    Write-Status "Found $($Files.Count) SQLite database file(s) to remove"
    
    $removedCount = 0
    foreach ($file in $Files) {
        try {
            Write-Status "Removing SQLite file: $($file.Name)" "DEBUG"
            Remove-Item $file.FullName -Force
            $removedCount++
            Write-Status "Removed SQLite file: $($file.Name)"
        }
        catch {
            Write-Status "Failed to remove SQLite file '$($file.Name)': $($_.Exception.Message)" "ERROR"
        }
    }
    
    Write-Status "Successfully removed $removedCount of $($Files.Count) SQLite files"
    return $removedCount -eq $Files.Count
}

# Main execution
Write-Status "=== SQLite Database Cleanup - Development Environment ==="
Write-Status "Target: $($SQLiteConfig.ServerPath)"

# Check if server path exists
if (-not (Test-Path $SQLiteConfig.ServerPath)) {
    Write-Status "Server path not found: $($SQLiteConfig.ServerPath)" "ERROR"
    exit 1
}

# Get list of SQLite files
$sqliteFiles = Get-SQLiteFiles

if ($sqliteFiles.Count -eq 0) {
    Write-Status "No .db files found in main plugin directory"
    exit 0
}

if ($ListOnly) {
    Write-Status ".db files found in main plugin directory:"
    foreach ($file in $sqliteFiles) {
        $relativePath = $file.FullName.Replace($SQLiteConfig.ServerPath, "").TrimStart('\')
        Write-Host "  - $relativePath ($([math]::Round($file.Length / 1KB, 2)) KB)"
    }
    exit 0
}

# Confirm action unless Force is specified
if (-not $Force) {
    Write-Status "This will remove ALL $($sqliteFiles.Count) .db file(s) from the main plugin directory" "WARN"
    Write-Status "Files to be removed:" "WARN"
    foreach ($file in $sqliteFiles) {
        $relativePath = $file.FullName.Replace($SQLiteConfig.ServerPath, "").TrimStart('\')
        Write-Host "  - $relativePath ($([math]::Round($file.Length / 1KB, 2)) KB)" -ForegroundColor Yellow
    }
    
    $confirmation = Read-Host "Are you sure you want to continue? (type 'YES' to confirm)"
    if ($confirmation -ne "YES") {
        Write-Status "Operation cancelled by user"
        exit 0
    }
}

# Remove SQLite files
$success = Remove-SQLiteFiles -Files $sqliteFiles

if ($success) {
    Write-Status "SQLite database cleanup completed successfully"
    exit 0
} else {
    Write-Status "SQLite database cleanup completed with errors" "WARN"
    exit 1
}
