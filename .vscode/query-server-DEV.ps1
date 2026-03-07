param(
    [Parameter(Position=0)]
    [ValidateSet("console", "status", "stats", "info", "command")]
    [string]$QueryType = "console",
    
    [Parameter(Position=1)]
    [ValidateScript({
        if ($QueryType -eq "command") { return $true }  # For command type, this is the command to send
        if ($_ -eq "all") { return $true }
        $num = $null
        if ([int]::TryParse($_, [ref]$num) -and $num -ge 1 -and $num -le 500) { return $true }
        throw "Lines must be 'all' or a number between 1 and 500"
    })]
    [string]$Lines = "50",
    
    [switch]$Reversed,
    [switch]$FromBeginning,
    [switch]$ErrorsOnly,
    [switch]$PluginOnly,
    [string]$FilterText = "",
    [switch]$NoTimestamp,
    [switch]$Raw
)

$ErrorActionPreference = "Stop"

function Show-ProgressBar { 
    param([int]$length = 39, [int]$Seconds = 1, [string]$pattern = 'spectrum', [string]$character = '='); 
    $colors = @('Cyan', 'DarkCyan', 'Blue', 'DarkBlue', 'Magenta', 'DarkMagenta'); 
    if ($pattern -ne 'spectrum') { $colors = @($pattern) }; 
    $sleepTime = ($Seconds * 1000) / $length; 
    $colorIndex = 0; 
    for ($i = 0; $i -lt $length; $i++) { 
        $color = $colors[$colorIndex]; 
        Write-Host $character -NoNewline -ForegroundColor $color; 
        $colorIndex = ($colorIndex + 1) % $colors.Length; 
        Start-Sleep -Milliseconds $sleepTime 
    }; 
    Write-Host "" 
}

function Format-LogEntry {
    param([string]$logLine, [switch]$NoTimestamp, [switch]$ErrorsOnly, [switch]$PluginOnly, [string]$FilterText)
    
    # Skip empty lines
    if ([string]::IsNullOrWhiteSpace($logLine)) { return $false }
    
    # Parse log entry using MCSS API log format
    # Pattern: [HH:mm:ss] [Thread/LEVEL]: [Plugin] Message
    $pattern = '^\[(\d{2}:\d{2}:\d{2})\] \[([^/]+)/([A-Z]+)\](?:: \[([^\]]+)\])?: (.+)$'
    $match = [regex]::Match($logLine, $pattern)
    
    if ($match.Success) {
        $timestamp = $match.Groups[1].Value
        $level = $match.Groups[3].Value
        $plugin = $match.Groups[4].Value
        $message = $match.Groups[5].Value
        
        # Apply filters
        if ($ErrorsOnly -and $level -notmatch "ERROR|WARN") { return $false }
        if ($PluginOnly -and [string]::IsNullOrEmpty($plugin)) { return $false }
        if (![string]::IsNullOrEmpty($FilterText) -and $logLine -notlike "*$FilterText*") { return $false }
        
        # Format output based on options
        if ($NoTimestamp) {
            $formatted = "[$level]"
            if (![string]::IsNullOrEmpty($plugin)) { $formatted += " [$plugin]" }
            $formatted += " $message"
        } else {
            $formatted = "[$timestamp] [$level]"
            if (![string]::IsNullOrEmpty($plugin)) { $formatted += " [$plugin]" }
            $formatted += " $message"
        }
        
        # Color coding for different log levels
        switch ($level) {
            "ERROR" { Write-Host $formatted -ForegroundColor Red }
            "WARN"  { Write-Host $formatted -ForegroundColor Yellow }
            "INFO"  { Write-Host $formatted -ForegroundColor Green }
            "DEBUG" { Write-Host $formatted -ForegroundColor Gray }
            default { Write-Host $formatted }
        }
    } else {
        # Raw log line that doesn't match pattern
        if (![string]::IsNullOrEmpty($FilterText) -and $logLine -notlike "*$FilterText*") { return $false }
        Write-Host $logLine
        return $true
    }
}

# Define the connection parameters
Set-Location -Path $PSScriptRoot
$config = Get-Content -Path .\project.json | ConvertFrom-Json

$protocol = $config.API.protocol
$port = $config.API.port
$hostname = $config.API.hostname
$serverId = $config.API.serverid

# Define the headers for the API requests from config
$headers = @{
    apiKey = $config.API.key
}

# Main query logic using switch/case for future extensibility
switch ($QueryType.ToLower()) {
    "console" {
        Write-Host "=== CONSOLE OUTPUT QUERY ===" -ForegroundColor Cyan
        
        # Build console API endpoint with parameters
        $consoleUri = "${protocol}://${hostname}:${port}/api/v2/servers/${serverId}/console"
        
        # Handle line count parameter
        $lineCount = if ($Lines -eq "all") { -1 } else { [int]$Lines }
        
        # Build query parameters
        $params = @{
            "AmountOfLines" = $lineCount
            "Reversed" = $Reversed.IsPresent.ToString().ToLower()
        }
        
        if ($FromBeginning) {
            $params["takeFromBeginning"] = "true"
        }
        
        # Build query string
        $queryString = ($params.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "&"
        $fullUri = "${consoleUri}?${queryString}"
        
        Write-Host "Querying: $Lines lines" -ForegroundColor Yellow
        if ($Reversed) { Write-Host "Order: Reversed (newest first)" -ForegroundColor Yellow }
        if ($FromBeginning) { Write-Host "Source: From beginning" -ForegroundColor Yellow }
        if ($ErrorsOnly) { Write-Host "Filter: Errors and warnings only" -ForegroundColor Yellow }
        if ($PluginOnly) { Write-Host "Filter: Plugin messages only" -ForegroundColor Yellow }
        if (![string]::IsNullOrEmpty($FilterText)) { Write-Host "Filter: '$FilterText'" -ForegroundColor Yellow }
        
        Show-ProgressBar -Seconds 1
        
        try {
            $response = Invoke-RestMethod -Uri $fullUri -Method Get -Headers $headers
            
            if ($Raw) {
                # Raw output without formatting
                $response | ForEach-Object { Write-Host $_ }
            } else {
                # Formatted output with color coding and filtering
                Write-Host "--- CONSOLE OUTPUT ---" -ForegroundColor Cyan
                $filteredCount = 0
                $response | ForEach-Object {
                    # Format-LogEntry handles the Write-Host internally, just track if it outputs anything
                    if (Format-LogEntry -logLine $_ -NoTimestamp:$NoTimestamp -ErrorsOnly:$ErrorsOnly -PluginOnly:$PluginOnly -FilterText:$FilterText) {
                        $filteredCount++
                    }
                }
                Write-Host "--- END OUTPUT ($filteredCount lines) ---" -ForegroundColor Cyan
            }
            
        } catch {
            Write-Error "Error querying console: $($_.Exception.Message)"
            Show-UsageExamples
        }
    }
    
    "status" {
        Write-Host "=== SERVER STATUS QUERY ===" -ForegroundColor Cyan
        
        $statusUri = "${protocol}://${hostname}:$port/api/v2/servers"
        
        try {
            $response = Invoke-RestMethod -Uri $statusUri -Method Get -Headers $headers
            $server = $response | Where-Object { $_.serverId -eq $serverId }
            
            if ($server) {
                Write-Host "Server Status Information:" -ForegroundColor Green
                Write-Host "  Server ID: $($server.serverId)"
                Write-Host "  Name: $($server.name)"
                Write-Host "  Status: $(if($server.status -eq 1) { 'Running' } else { 'Stopped' })"
                Write-Host "  Type: $($server.type)"
                Write-Host "  Memory: $($server.javaAllocatedMemory)MB"
                Write-Host "  Path: $($server.pathToFolder)"
            } else {
                Write-Warning "Server with ID $serverId not found"
            }
            
        } catch {
            Write-Error "Error querying server status: $($_.Exception.Message)"
            Show-UsageExamples
        }
    }
    
    "stats" {
        Write-Host "=== SERVER STATISTICS QUERY ===" -ForegroundColor Cyan
        
        $statsUri = "${protocol}://${hostname}:$port/api/v2/servers/$serverId/stats"
        
        try {
            $response = Invoke-RestMethod -Uri $statsUri -Method Get -Headers $headers
            
            if ($response.latest) {
                $stats = $response.latest
                Write-Host "Server Performance Statistics:" -ForegroundColor Green
                Write-Host "  CPU Usage: $($stats.cpu)%"
                Write-Host "  Memory Used: $($stats.memoryUsed)MB / $($stats.memoryLimit)MB"
                Write-Host "  Memory Usage: $(($stats.memoryUsed / $stats.memoryLimit * 100).ToString('F1'))%"
                Write-Host "  Players Online: $($stats.playersOnline) / $($stats.playerLimit)"
                
                if ($stats.startDate) {
                    $startTime = [DateTime]::FromFileTimeUtc($stats.startDate * 10000 + 116444736000000000)
                    $uptime = (Get-Date) - $startTime
                    Write-Host "  Uptime: $($uptime.Days)d $($uptime.Hours)h $($uptime.Minutes)m"
                }
            } else {
                Write-Warning "No statistics available for server"
            }
            
        } catch {
            Write-Error "Error querying server statistics: $($_.Exception.Message)"
            Show-UsageExamples
        }
    }
    
    "info" {
        Write-Host "=== SERVER INFORMATION QUERY ===" -ForegroundColor Cyan
        
        $infoUri = "${protocol}://${hostname}:$port/api/v2/servers/$serverId"
        
        try {
            $response = Invoke-RestMethod -Uri $infoUri -Method Get -Headers $headers
            
            Write-Host "Detailed Server Information:" -ForegroundColor Green
            Write-Host "  Server ID: $($response.serverId)"
            Write-Host "  Name: $($response.name)"
            Write-Host "  Description: $($response.description)"
            Write-Host "  Folder: $($response.folderName)"
            Write-Host "  Full Path: $($response.pathToFolder)"
            Write-Host "  Server Type: $($response.type)"
            Write-Host "  Java Memory: $($response.javaAllocatedMemory)MB"
            Write-Host "  Startup Command: $($response.javaStartupLine)"
            Write-Host "  Status Code: $($response.status) ($(if($response.status -eq 1) { 'Running' } else { 'Stopped' }))"
            
        } catch {
            Write-Error "Error querying server information: $($_.Exception.Message)"
            Show-UsageExamples
        }
    }
    
    "command" {
        Write-Host "=== SERVER COMMAND EXECUTION ===" -ForegroundColor Cyan
        
        if ([string]::IsNullOrWhiteSpace($Lines)) {
            Write-Error "Command parameter is required for command execution"
            Show-UsageExamples
            exit 1
        }
        
        $commandUri = "${protocol}://${hostname}:${port}/api/v2/servers/${serverId}/execute/command"
        
        Write-Host "Sending command to server: $Lines" -ForegroundColor Yellow
        Show-ProgressBar -Seconds 1
        
        try {
            # POST request to send command
            $body = @{
                "command" = $Lines
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
            Show-UsageExamples
        }
    }
    
    default {
        Write-Error "Unknown query type: $QueryType. Valid options: console, status, stats, info, command"
        Show-UsageExamples
        exit 1
    }
}

# Usage examples - only shown on error
function Show-UsageExamples {
    Write-Host ""
    Write-Host "Usage Examples:" -ForegroundColor DarkGray
    Write-Host "  .\query-server-DEV.ps1 console 25                    # Get last 25 console lines" -ForegroundColor DarkGray
    Write-Host "  .\query-server-DEV.ps1 console all -ErrorsOnly       # Get all error/warning messages" -ForegroundColor DarkGray
    Write-Host "  .\query-server-DEV.ps1 console 50 -FilterText 'RVNKTools'  # Filter for RVNKTools messages" -ForegroundColor DarkGray
    Write-Host "  .\query-server-DEV.ps1 command 'rvnktools debug'     # Send command to server" -ForegroundColor DarkGray
    Write-Host "  .\query-server-DEV.ps1 command 'stop'                # Send stop command to server" -ForegroundColor DarkGray
    Write-Host "  .\query-server-DEV.ps1 status                        # Get server status" -ForegroundColor DarkGray
    Write-Host "  .\query-server-DEV.ps1 stats                         # Get performance statistics" -ForegroundColor DarkGray
    Write-Host "  .\query-server-DEV.ps1 info                          # Get detailed server info" -ForegroundColor DarkGray
}
