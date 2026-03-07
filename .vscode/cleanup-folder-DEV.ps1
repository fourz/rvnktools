# Description: This script is used to clean up the destination plugin jar file and folder before restarting the server.

# Set the current location to the script root
Set-Location -Path $PSScriptRoot
$config = Get-Content -Path .\project.json | ConvertFrom-Json

# Define the path to the destination plugin jar file and folder from config
$pluginJarName = Split-Path $config.OutputFile -Leaf
$pluginJarPath = Join-Path $config.DestinationPath $pluginJarName
$pluginFolderPath = Join-Path $config.DestinationPath $config.PluginFolder

# Check if the destination plugin jar file exists
if (Test-Path $pluginJarPath) {
    # Remove the destination plugin jar file
    Remove-Item $pluginJarPath -Force
    Write-Host "Destination plugin jar file removed." -ForegroundColor Green
} else {
    Write-Host "Destination plugin jar file does not exist." -ForegroundColor Yellow
}

# Check if the destination plugin folder exists
if (Test-Path $pluginFolderPath) {
    # Remove the destination plugin folder and its contents
    Remove-Item $pluginFolderPath -Recurse -Force
    Write-Host "Destination plugin folder removed." -ForegroundColor Green
} else {
    Write-Host "Destination plugin folder does not exist." -ForegroundColor Yellow
}
