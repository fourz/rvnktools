# RVNK Tools Version Increment Script
# Automatically increments plugin version across all project files
# Usage: .\increment-version.ps1 [major|minor|patch] [-NewVersion "x.y.z"] [-WhatIf]

param(
    [Parameter(Position=0)]
    [ValidateSet("major", "minor", "patch", "")]
    [string]$IncrementType = "patch",
    
    [Parameter()]
    [string]$NewVersion = "",
    
    [Parameter()]
    [switch]$WhatIf
)

# Project file paths
$projectRoot = Split-Path $PSScriptRoot -Parent
$pomFile = Join-Path $projectRoot "toolkitplugin\pom.xml"
$pluginYmlFile = Join-Path $projectRoot "toolkitplugin\src\main\resources\plugin.yml"
$projectJsonFile = Join-Path $projectRoot ".vscode\project.json"

# Files that may contain version references in documentation
$docFiles = @(
    (Join-Path $projectRoot "docs\api\rvnkcore-httprest.md"),
    (Join-Path $projectRoot "docs\api\rvnkcore-player-world-tracking.md"),
    (Join-Path $projectRoot "docs\api-reference\mcss-dev-server.md")
)

Write-Host "ℹ RVNK Tools Version Increment Script" -ForegroundColor Cyan
Write-Host "ℹ ====================================" -ForegroundColor Cyan

# Validate files exist
$requiredFiles = @($pomFile, $pluginYmlFile, $projectJsonFile)
foreach ($file in $requiredFiles) {
    if (-not (Test-Path $file)) {
        Write-Host "✖ Required file not found: $file" -ForegroundColor Red
        exit 1
    }
}

# Extract current version from pom.xml
$pomContent = Get-Content $pomFile -Raw
if ($pomContent -match '<version>([^<]+)</version>') {
    $currentVersion = $matches[1].Trim()
} else {
    Write-Host "✖ Could not find version in pom.xml" -ForegroundColor Red
    exit 1
}

Write-Host "ℹ Current version: $currentVersion" -ForegroundColor Cyan

# Calculate new version
if ($NewVersion) {
    $newVersion = $NewVersion
    Write-Host "ℹ Setting explicit version: $newVersion" -ForegroundColor Cyan
} else {
    # Handle alpha/beta versions
    $cleanVersion = $currentVersion -replace '-alpha|-beta|-SNAPSHOT', ''
    $suffix = ""
    if ($currentVersion -match '-(alpha|beta|SNAPSHOT)') {
        $suffix = "-$($matches[1])"
    }
    
    $parts = $cleanVersion.Split('.')
    if ($parts.Length -lt 2 -or $parts.Length -gt 3) {
        Write-Host "✖ Invalid version format: $currentVersion. Expected format: x.y or x.y.z" -ForegroundColor Red
        exit 1
    }
    
    # Default patch to 0 if not provided
    if ($parts.Length -eq 2) {
        $parts += "0"
    }
    
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    $patch = [int]$parts[2]
    
    switch ($IncrementType.ToLower()) {
        "major" {
            $major++
            $minor = 0
            $patch = 0
        }
        "minor" {
            $minor++
            $patch = 0
        }
        "patch" {
            $patch++
        }
    }
    
    $newVersion = "$major.$minor.$patch$suffix"
    Write-Host "Incrementing $IncrementType version: $currentVersion -> $newVersion" -ForegroundColor Cyan
}

if ($currentVersion -eq $newVersion) {
    Write-Host "⚠ New version is the same as current version: $newVersion" -ForegroundColor Yellow
    if (-not $WhatIf) {
        $continue = Read-Host "Continue anyway? (y/N)"
        if ($continue -ne "y" -and $continue -ne "Y") {
            Write-Host "ℹ Operation cancelled." -ForegroundColor Cyan
            exit 0
        }
    }
}

Write-Host ""
if ($WhatIf) {
    Write-Host "ℹ DRY RUN - No files will be modified" -ForegroundColor Cyan
    Write-Host "ℹ ======================================" -ForegroundColor Cyan
} else {
    Write-Host "ℹ Updating version from $currentVersion to $newVersion" -ForegroundColor Cyan
    Write-Host "ℹ ==================================================" -ForegroundColor Cyan
}

$updatedCount = 0

# Update core project files
$coreFiles = @(
    @{ Path = $pomFile; Name = "pom.xml" },
    @{ Path = $pluginYmlFile; Name = "plugin.yml" },
    @{ Path = $projectJsonFile; Name = "project.json" }
)

foreach ($file in $coreFiles) {
    if (-not (Test-Path $file.Path)) {
        Write-Host "⚠ File not found: $($file.Name)" -ForegroundColor Yellow
        continue
    }
    
    $content = Get-Content $file.Path -Raw
    $originalContent = $content
    
    # Replace version references
    $content = $content -replace [regex]::Escape($currentVersion), $newVersion
    
    # Special handling for project.json OutputFile path
    if ($file.Path -like "*project.json") {
        $oldJarName = "rvnktools-$currentVersion.jar"
        $newJarName = "rvnktools-$newVersion.jar"
        $content = $content -replace [regex]::Escape($oldJarName), $newJarName
    }
    
    if ($content -ne $originalContent) {
        if ($WhatIf) {
            Write-Host "ℹ Would update: $($file.Name)" -ForegroundColor Cyan
            # Show summary of changes
            $oldLines = $originalContent -split "`r?`n"
            $newLines = $content -split "`r?`n"
            for ($i = 0; $i -lt $oldLines.Length; $i++) {
                if ($i -lt $newLines.Length -and $oldLines[$i] -ne $newLines[$i] -and $oldLines[$i] -match [regex]::Escape($currentVersion)) {
                    Write-Host "    Line $($i + 1): $($oldLines[$i])" -ForegroundColor Gray
                    Write-Host "             -> $($newLines[$i])" -ForegroundColor Yellow
                }
            }
        } else {
            Set-Content $file.Path -Value $content -NoNewline
            Write-Host "✓ Updated: $($file.Name)" -ForegroundColor Green
        }
        $updatedCount++
    }
}

# Update documentation files (optional)
Write-Host ""
Write-Host "ℹ Checking documentation files for version references..." -ForegroundColor Cyan
$docUpdatedCount = 0

foreach ($docFile in $docFiles) {
    if (-not (Test-Path $docFile)) {
        continue
    }
    
    $content = Get-Content $docFile -Raw
    $originalContent = $content
    
    # Replace version references
    $content = $content -replace [regex]::Escape($currentVersion), $newVersion
    
    if ($content -ne $originalContent) {
        $fileName = Split-Path $docFile -Leaf
        if ($WhatIf) {
            Write-Host "ℹ Would update: $fileName" -ForegroundColor Cyan
        } else {
            Set-Content $docFile -Value $content -NoNewline
            Write-Host "✓ Updated: $fileName" -ForegroundColor Green
        }
        $docUpdatedCount++
    }
}

# Summary
Write-Host ""
Write-Host "ℹ Summary" -ForegroundColor Cyan
Write-Host "ℹ =======" -ForegroundColor Cyan
Write-Host "ℹ Core files processed: $($coreFiles.Count)" -ForegroundColor Cyan
Write-Host "ℹ Core files updated: $updatedCount" -ForegroundColor Cyan
Write-Host "ℹ Documentation files updated: $docUpdatedCount" -ForegroundColor Cyan

if ($WhatIf) {
    Write-Host ""
    Write-Host "⚠ This was a dry run. No files were modified." -ForegroundColor Yellow
    Write-Host "ℹ Run without -WhatIf to apply changes." -ForegroundColor Cyan
} else {
    Write-Host ""
    if ($updatedCount -gt 0) {
        Write-Host "✓ Version increment completed successfully!" -ForegroundColor Green
        Write-Host "ℹ New version: $newVersion" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "ℹ Next steps:" -ForegroundColor Cyan
        Write-Host "ℹ 1. Review changes with git diff" -ForegroundColor Cyan
        Write-Host "ℹ 2. Run Build Plugin task to test compilation" -ForegroundColor Cyan
        Write-Host "ℹ 3. Commit changes when ready" -ForegroundColor Cyan
    } else {
        Write-Host "⚠ No files were updated." -ForegroundColor Yellow
    }
}
