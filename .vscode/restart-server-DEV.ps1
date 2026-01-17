$ErrorActionPreference = "Stop"
function Show-ProgressBar { param([int]$length = 79, [int]$Seconds = 2, [string]$pattern = 'spectrum', [string]$character = '='); $colors = @('Magenta', 'Red', 'DarkMagenta', 'DarkRed', 'Yellow', 'Green', 'DarkGreen', 'Cyan', 'DarkCyan', 'Blue', 'DarkBlue'); if ($pattern -ne 'spectrum') { $colors = @($pattern) }; $sleepTime = ($Seconds * 1000) / $length; $colorIndex = 0; for ($i = 0; $i -lt $length; $i++) { $color = $colors[$colorIndex]; Write-Host $character -NoNewline -ForegroundColor $color; $colorIndex = ($colorIndex + 1) % $colors.Length; Start-Sleep -Milliseconds $sleepTime }; Write-Host "" }

# Set the current location to the script root
Set-Location -Path $PSScriptRoot
$config = Get-Content -Path .\project.json | ConvertFrom-Json

# Define the port and URIs for the server API using config
$protocol = $config.API.protocol
$port = $config.API.port
$hostname = $config.API.hostname

$executeuri = "${protocol}://${hostname}:$port/api/v2/servers/execute/action"
$queryuri = "${protocol}://${hostname}:$port/api/v2/servers"

# Define the server ID from config
$serverId = $config.API.serverid

# Define the headers for the API requests from config
$headers = @{
    apiKey = $config.API.key
} 

# Define the body for the POST request
$body = @{
    serverids = 
        @("$serverId")
    action = 4 
} | ConvertTo-Json -Depth 2

# Try to send a POST request to the server API to initiate a server restart
try { 
    Invoke-RestMethod -Uri $executeuri -Method Post -Headers $headers -Body $body -ContentType "application/json" 
    Write-Host "Server restart initiated at ${hostname}:$port." -ForegroundColor Yellow
} catch {
    Write-Error "Error during server restart: $($_.Exception.Message)"
}

Show-ProgressBar -Seconds 16

# Initialize a counter
$counter = 0

# Loop to check the server status three times
while ($counter -lt 3) {
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
        break
    } else {
        Write-Host "Server is down." -ForegroundColor Red
        Show-ProgressBar -character 'o'
    }

    # Increment the counter
    $counter++
}

# If the server is still down after three checks, exit the script
if ($counter -eq 6) {
    Write-Error "Server offline. Exiting script."
    exit
}