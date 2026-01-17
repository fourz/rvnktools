# RVNK Tools JAR Cleanup Script
# Removes old plugin JAR files from development server
# Usage: .\cleanup-jar-DEV.ps1 [-ListOnly] [-Force] [-WhatIf]

param(
    [Parameter()]
    [switch]$ListOnly,
    
    [Parameter()]
    [switch]$Force,
    
    [Parameter()]
    [switch]$WhatIf
)

# Load server configuration from project.json
$configPath = Join-Path $PSScriptRoot "project.json"
if (-not (Test-Path $configPath)) {
    Write-Host "ERROR: project.json not found at: $configPath" -ForegroundColor Red
    exit 1
}

$config = Get-Content -Path $configPath | ConvertFrom-Json
$serverPluginPath = $config.DestinationPath

Write-Host "RVNK Tools JAR Cleanup Script" -ForegroundColor Cyan
Write-Host "=============================" -ForegroundColor Cyan

# Validate server plugin directory exists
if (-not (Test-Path $serverPluginPath)) {
    Write-Host "ERROR: Server plugin directory not found: $serverPluginPath" -ForegroundColor Red
    Write-Host "Please verify the development server path is correct." -ForegroundColor Yellow
    exit 1
}

Write-Host "Server plugin directory: $serverPluginPath" -ForegroundColor Cyan

# Find RVNKTools JAR files
$jarFiles = Get-ChildItem -Path $serverPluginPath -Filter "rvnktools*.jar" -File

Write-Host ""
Write-Host "Searching for RVNKTools JAR files..." -ForegroundColor Cyan

if ($jarFiles.Count -eq 0) {
    Write-Host "No RVNKTools JAR files found in server plugins directory." -ForegroundColor Yellow
    exit 0
}

# Display found files
Write-Host ""
Write-Host "Found JAR files:" -ForegroundColor Yellow
foreach ($jar in $jarFiles) {
    $size = [math]::Round($jar.Length / 1MB, 2)
    Write-Host "  - $($jar.Name) ($size MB, Modified: $($jar.LastWriteTime))" -ForegroundColor Gray
}

# List only mode
if ($ListOnly) {
    Write-Host ""
    Write-Host "List-only mode: No files were removed." -ForegroundColor Cyan
    exit 0
}

# WhatIf mode
if ($WhatIf) {
    Write-Host ""
    Write-Host "WhatIf mode: Showing what would be removed." -ForegroundColor Cyan
    foreach ($jar in $jarFiles) {
        Write-Host "Would remove: $($jar.Name)" -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "Run without -WhatIf to actually remove files." -ForegroundColor Cyan
    exit 0
}

# Confirmation prompt (unless Force is specified)
if (-not $Force) {
    Write-Host ""
    $totalItems = $jarFiles.Count
    Write-Host "This will remove $totalItems JAR files from the server." -ForegroundColor Yellow
    $confirm = Read-Host "Continue with cleanup? (y/N)"
    
    if ($confirm -ne "y" -and $confirm -ne "Y") {
        Write-Host "Cleanup cancelled." -ForegroundColor Cyan
        exit 0
    }
}

# Remove JAR files
$removedFiles = 0
$errors = @()

Write-Host ""
Write-Host "Removing RVNKTools JAR files from server..." -ForegroundColor Cyan

foreach ($jar in $jarFiles) {
    try {
        Remove-Item -Path $jar.FullName -Force
        Write-Host "REMOVED: $($jar.Name)" -ForegroundColor Green
        $removedFiles++
    }
    catch {
        Write-Host "ERROR removing $($jar.Name): $($_.Exception.Message)" -ForegroundColor Red
        $errors += "JAR file: $($jar.Name) - $($_.Exception.Message)"
    }
}

# Summary
Write-Host ""
Write-Host "Cleanup Summary" -ForegroundColor Cyan
Write-Host "===============" -ForegroundColor Cyan
Write-Host "JAR files removed: $removedFiles" -ForegroundColor Cyan

if ($errors.Count -gt 0) {
    Write-Host "Errors encountered: $($errors.Count)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Error Details:" -ForegroundColor Red
    foreach ($errorMsg in $errors) {
        Write-Host "  - $errorMsg" -ForegroundColor Red
    }
} else {
    Write-Host ""
    Write-Host "JAR cleanup completed successfully!" -ForegroundColor Green
    Write-Host "Server is ready for new plugin deployment." -ForegroundColor Cyan
}
