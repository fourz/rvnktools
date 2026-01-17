$ErrorActionPreference = "Stop"

function Show-ProgressBar { param([int]$length = 79, [int]$Seconds = 2, [string]$pattern = 'spectrum', [string]$character = '='); $colors = @('Red', 'Magenta', 'DarkMagenta', 'DarkRed', 'Yellow', 'Green', 'DarkGreen', 'Cyan', 'DarkCyan', 'Blue', 'DarkBlue'); if ($pattern -ne 'spectrum') { $colors = @($pattern) }; $sleepTime = ($Seconds * 1000) / $length; $colorIndex = 0; for ($i = 0; $i -lt $length; $i++) { $color = $colors[$colorIndex]; Write-Host $character -NoNewline -ForegroundColor $color; $colorIndex = ($colorIndex + 1) % $colors.Length; Start-Sleep -Milliseconds $sleepTime }; Write-Host "" }

# Set the current location to the script root
Set-Location -Path $PSScriptRoot
$config = Get-Content -Path .\project.json | ConvertFrom-Json

# Define the port and URIs for the server API using config
$protocol = $config.API.protocol
$port = $config.API.port
$hostname = $config.API.hostname

$executeuri = "${protocol}://${hostname}:$port/api/v2/servers/execute/command"
$queryuri = "${protocol}://${hostname}:$port/api/v2/servers"

# Define the server ID from config
$serverId = $config.API.serverid

# Define the headers for the API request from config
$headers = @{
    apiKey = $config.API.key
} 

# Define the body for the API request
$body = @{
    serverids = 
        @("$serverId")
    command = 'reload confirm'
} | ConvertTo-Json -Depth 2

# Try to send a POST request to the server to initiate a reload
try {
    Invoke-RestMethod -Uri $executeuri -Method Post -Headers $headers -Body $body -ContentType "application/json"

    # If successful, print a message
    Write-Host "Plugin reload initiated." -ForegroundColor Yellow

} catch {
    # If there's an error, print it
    Write-Error "Error during reload: $($_.Exception.Message)"
}

# Output a makeshift progress bar while waiting for the server to reload
Show-ProgressBar -Seconds 5

# Try to send a GET request to check the server status
try {
    $status = Invoke-RestMethod -Uri $queryuri -Method Get -Headers $headers 

} catch {
    # If there's an error, print it
    Write-Error "Error during status check: $($_.Exception.Message)"
}

# Check if the server is up
$serverup = ($status | Where-Object {$_.serverId -eq $serverId }).status -eq 1

# Output a message based on the server status
If ($serverup) {
    Write-Host "Server is up." -ForegroundColor Green
} else {
    Write-Host "Server is down." -ForegroundColor Red
}