param(
    [Parameter(Position=0, Mandatory=$true)]
    [string]$Command
)

$ErrorActionPreference = "Stop"

# Set the current location to the script root
Set-Location -Path $PSScriptRoot
$config = Get-Content -Path .\project.json | ConvertFrom-Json

# Define the connection parameters from config
$protocol = $config.API.protocol
$port = $config.API.port
$hostname = $config.API.hostname
$serverId = $config.API.serverid

# Define the headers for the API requests from config
$headers = @{
    apiKey = $config.API.key
}

# Build the API endpoint
$commandUri = "${protocol}://${hostname}:${port}/api/v2/servers/${serverId}/execute/command"

Write-Host "Sending command to server: $Command" -ForegroundColor Cyan
Write-Host "===============================================================================" -ForegroundColor DarkGray

try {
    # POST request to send command
    $body = @{
        "command" = $Command
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri $commandUri -Method Post -Headers $headers -Body $body -ContentType "application/json"
    
    Write-Host "Command sent successfully!" -ForegroundColor Green
    
    # Wait a moment for the command to execute
    Start-Sleep -Seconds 2
    
    # Get latest console output to see command results
    $consoleUri = "${protocol}://${hostname}:${port}/api/v2/servers/${serverId}/console"
    $params = @{
        "AmountOfLines" = 10
        "Reversed" = "false"
    }
    $queryString = ($params.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "&"
    $fullUri = "${consoleUri}?${queryString}"
    
    $consoleOutput = Invoke-RestMethod -Uri $fullUri -Method Get -Headers $headers
    
    Write-Host "--- COMMAND OUTPUT ---" -ForegroundColor Yellow
    $consoleOutput | ForEach-Object { Write-Host $_ }
    Write-Host "--- END OUTPUT ---" -ForegroundColor Yellow
    
} catch {
    Write-Error "Error sending command: $($_.Exception.Message)"
}
