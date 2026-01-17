$ErrorActionPreference = "Stop"

Set-Location -Path $PSScriptRoot
$config = Get-Content -Path .\project.json | ConvertFrom-Json

$source = $config.OutputFile
$destination = $config.DestinationPath

# Copy the plugin to the server
Write-Host "Copying " -ForegroundColor Yellow -NoNewline
Write-Host $source -ForegroundColor DarkMagenta 
Write-Host "to " -ForegroundColor Yellow -NoNewline 
Write-Host $destination -ForegroundColor DarkMagenta

$o = Copy-Item -Path $source -Destination $destination -PassThru -Force

if ($null -ne $o) {
    Write-Host "Copy Success." -ForegroundColor Green -NoNewline
} else {
    Write-Error "Error copying plugin to server:`n$($Error[0].Exception.Message)"
}

