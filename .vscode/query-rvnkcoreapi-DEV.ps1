# RVNKCore API Query Script
# Execute RVNKCore REST API calls for testing and validation
# Uses project.json configuration for API endpoints and authentication

param(
    [Parameter(Position=0)]
    [ValidateSet("test", "players", "announcements", "player", "announcement", "custom")]
    [string]$QueryType = "test",
    
    [Parameter(Position=1)]
    [string]$Endpoint = "",
    
    [Parameter(Position=2)]
    [ValidateSet("GET", "POST", "PUT", "DELETE")]
    [string]$Method = "GET",
    
    [Parameter(Position=3)]
    [string]$Body = "",
    
    [switch]$HttpOnly,
    [switch]$HttpsOnly,
    [switch]$IgnoreSSLErrors = $true,
    [switch]$Detail,
    [switch]$Raw
)

$ErrorActionPreference = "Stop"

# Load configuration from project.json
Set-Location -Path $PSScriptRoot
$config = Get-Content -Path .\project.json | ConvertFrom-Json

$httpUrl = $config.RVNKCoreAPI.httpUrl
$httpsUrl = $config.RVNKCoreAPI.httpsUrl
$apiKey = $config.RVNKCoreAPI.apiKey

# Set TLS 1.2 for secure connections
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# SSL certificate validation bypass for self-signed certificates
if ($IgnoreSSLErrors) {
    if (-not ([System.Management.Automation.PSTypeName]'ServerCertificateValidationCallback').Type) {
        $certCallback = @"
            using System;
            using System.Net;
            using System.Net.Security;
            using System.Security.Cryptography.X509Certificates;
            public class ServerCertificateValidationCallback {
                public static void Ignore() {
                    if(ServicePointManager.ServerCertificateValidationCallback == null) {
                        ServicePointManager.ServerCertificateValidationCallback += 
                            delegate(
                                Object obj, 
                                X509Certificate certificate, 
                                X509Chain chain, 
                                SslPolicyErrors errors
                            ) {
                                return true;
                            };
                    }
                }
            }
"@
        Add-Type $certCallback
    }
    [ServerCertificateValidationCallback]::Ignore()
}

$Headers = @{
    "X-API-Key" = $apiKey
    "Content-Type" = "application/json"
    "Accept" = "application/json"
}

# Predefined API endpoints
$Endpoints = @{
    # Player API
    Players = "/api/v1/players"
    OnlinePlayers = "/api/v1/players/online"
    PlayerByName = "/api/v1/player/name/wizardofire"
    PlayerCount = "/api/v1/players/count"
    
    # Announcement API
    Announcements = "/api/v1/announcements"
    AnnouncementCount = "/api/v1/announcements/count"
    AnnouncementMetrics = "/api/v1/announcements/metrics"
    AnnouncementsByType = "/api/v1/announcements/type/BROADCAST"
}

function Show-ProgressBar { 
    param([int]$length = 25, [int]$Seconds = 1)
    $colors = @('Cyan', 'DarkCyan', 'Blue', 'DarkBlue', 'Magenta', 'DarkMagenta')
    $sleepTime = ($Seconds * 1000) / $length
    $colorIndex = 0
    for ($i = 0; $i -lt $length; $i++) { 
        $color = $colors[$colorIndex]
        Write-Host "=" -NoNewline -ForegroundColor $color
        $colorIndex = ($colorIndex + 1) % $colors.Length
        Start-Sleep -Milliseconds $sleepTime 
    }
    Write-Host ""
}

function Invoke-ApiRequest {
    param(
        [string]$BaseUrl,
        [string]$Endpoint,
        [string]$Method = "GET",
        [string]$Body = ""
    )
    
    $Uri = "$BaseUrl$Endpoint"
    
    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            Headers = $Headers
            ContentType = "application/json"
            ErrorAction = "Stop"
        }
        
        if (![string]::IsNullOrEmpty($Body)) {
            $params.Body = $Body
        }
        
        Write-Host "Request: $Method $Uri" -ForegroundColor Yellow
        if (![string]::IsNullOrEmpty($Body)) {
            Write-Host "Body: $Body" -ForegroundColor Gray
        }
        
        $response = Invoke-RestMethod @params
        
        return @{
            Success = $true
            Data = $response
            StatusCode = 200
        }
    } catch {
        $statusCode = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.value__ } else { "Unknown" }
        return @{
            Success = $false
            Error = $_.Exception.Message
            StatusCode = $statusCode
            Exception = $_
        }
    }
}

function Test-BasicConnectivity {
    param([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n=== Testing $Protocol Connectivity ===" -ForegroundColor Cyan
    
    # Test basic connection
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint "/api/v1/players"
    
    if ($result.Success) {
        Write-Host "[PASS] Connection successful" -ForegroundColor Green
        if ($Detail) {
            Write-Host "Response: $($result.Data | ConvertTo-Json -Compress)" -ForegroundColor Gray
        }
    } else {
        Write-Host "[FAIL] Connection failed: $($result.Error)" -ForegroundColor Red
        if ($Detail) {
            Write-Host "Status Code: $($result.StatusCode)" -ForegroundColor Gray
        }
    }
    
    return $result.Success
}

function Test-PlayerEndpoints {
    param([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n--- Player API Endpoints ---" -ForegroundColor Yellow
    
    $endpoints = @{
        "All Players" = $Endpoints.Players
        "Online Players" = $Endpoints.OnlinePlayers
        "Player by Name" = $Endpoints.PlayerByName
        "Player Count" = $Endpoints.PlayerCount
    }
    
    foreach ($testName in $endpoints.Keys) {
        $endpoint = $endpoints[$testName]
        $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
        
        if ($result.Success) {
            Write-Host "[PASS] $testName" -ForegroundColor Green
            if ($Detail -and $result.Data) {
                Write-Host "  Data: $($result.Data | ConvertTo-Json -Compress)" -ForegroundColor Gray
            }
        } else {
            Write-Host "[FAIL] $testName - $($result.Error)" -ForegroundColor Red
        }
    }
}

function Test-AnnouncementEndpoints {
    param([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n--- Announcement API Endpoints ---" -ForegroundColor Yellow
    
    $endpoints = @{
        "All Announcements" = $Endpoints.Announcements
        "Announcement Count" = $Endpoints.AnnouncementCount
        "Announcement Metrics" = $Endpoints.AnnouncementMetrics
        "Announcements by Type" = $Endpoints.AnnouncementsByType
    }
    
    foreach ($testName in $endpoints.Keys) {
        $endpoint = $endpoints[$testName]
        $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
        
        if ($result.Success) {
            Write-Host "[PASS] $testName" -ForegroundColor Green
            if ($Detail -and $result.Data) {
                Write-Host "  Data: $($result.Data | ConvertTo-Json -Compress)" -ForegroundColor Gray
            }
        } else {
            Write-Host "[FAIL] $testName - $($result.Error)" -ForegroundColor Red
        }
    }
}

function Invoke-CustomRequest {
    param([string]$Protocol, [string]$BaseUrl, [string]$Endpoint, [string]$Method, [string]$Body)
    
    Write-Host "`n=== Custom $Protocol Request ===" -ForegroundColor Cyan
    Write-Host "Endpoint: $Endpoint" -ForegroundColor Yellow
    Write-Host "Method: $Method" -ForegroundColor Yellow
    
    if (![string]::IsNullOrEmpty($Body)) {
        Write-Host "Body: $Body" -ForegroundColor Yellow
    }
    
    Show-ProgressBar -Seconds 1
    
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoint -Method $Method -Body $Body
    
    if ($result.Success) {
        Write-Host "[SUCCESS] Request completed successfully" -ForegroundColor Green
        
        if ($Raw) {
            Write-Host "`nRaw Response:" -ForegroundColor Cyan
            $result.Data | ConvertTo-Json -Depth 10
        } else {
            Write-Host "`nFormatted Response:" -ForegroundColor Cyan
            $result.Data | ConvertTo-Json -Depth 10 | Write-Host
        }
    } else {
        Write-Host "[FAILED] Request failed: $($result.Error)" -ForegroundColor Red
        Write-Host "Status Code: $($result.StatusCode)" -ForegroundColor Red
        
        if ($Detail -and $result.Exception) {
            Write-Host "`nDetailed Error:" -ForegroundColor Red
            Write-Host $result.Exception.ToString() -ForegroundColor Gray
        }
    }
}

function Show-UsageExamples {
    Write-Host ""
    Write-Host "Usage Examples:" -ForegroundColor DarkGray
    Write-Host "  .\query-rvnkcoreapi-DEV.ps1 test                               # Test connectivity (HTTPS default)" -ForegroundColor DarkGray
    Write-Host "  .\query-rvnkcoreapi-DEV.ps1 players -HttpsOnly                # Test player endpoints (HTTPS)" -ForegroundColor DarkGray  
    Write-Host "  .\query-rvnkcoreapi-DEV.ps1 announcements -Detail             # Test announcement endpoints with details" -ForegroundColor DarkGray
    Write-Host "  .\query-rvnkcoreapi-DEV.ps1 custom '/api/v1/players' GET      # Custom GET request" -ForegroundColor DarkGray
    Write-Host "  .\query-rvnkcoreapi-DEV.ps1 custom '/api/v1/announcements' POST '{\"title\":\"Test\"}'" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "Note: HTTP (port 8080) is disabled for security when HTTPS (port 8081) is enabled" -ForegroundColor Yellow
    Write-Host "Available Query Types: test, players, announcements, player, announcement, custom" -ForegroundColor DarkGray
    Write-Host "Available Methods: GET, POST, PUT, DELETE" -ForegroundColor DarkGray
}

# Main execution logic
Write-Host "RVNKCore API Query Tool" -ForegroundColor Magenta
Write-Host "======================" -ForegroundColor Magenta

if ($IgnoreSSLErrors) {
    Write-Host "SSL Certificate Validation: DISABLED" -ForegroundColor Yellow
}

# Determine which URLs to test
# Security Note: HTTP is disabled when HTTPS is enabled as a security feature
$testUrls = @()
if ($HttpOnly -and -not $HttpsOnly) {
    $testUrls += @{ Protocol = "HTTP"; Url = $httpUrl }
} elseif ($HttpsOnly -and -not $HttpOnly) {
    $testUrls += @{ Protocol = "HTTPS"; Url = $httpsUrl }
} else {
    # Default to HTTPS only - HTTP is disabled for security when HTTPS is active
    Write-Host "Info: Testing HTTPS only - HTTP is disabled as a security feature when HTTPS is enabled" -ForegroundColor Yellow
    $testUrls += @{ Protocol = "HTTPS"; Url = $httpsUrl }
}

$totalPassed = 0
$totalFailed = 0

foreach ($testConfig in $testUrls) {
    $protocol = $testConfig.Protocol
    $baseUrl = $testConfig.Url
    
    switch ($QueryType.ToLower()) {
        "test" {
            $success = Test-BasicConnectivity -Protocol $protocol -BaseUrl $baseUrl
            if ($success) { $totalPassed++ } else { $totalFailed++ }
        }
        
        { $_ -in @("players", "player") } {
            $success = Test-BasicConnectivity -Protocol $protocol -BaseUrl $baseUrl
            if ($success) {
                Test-PlayerEndpoints -Protocol $protocol -BaseUrl $baseUrl
            }
        }
        
        { $_ -in @("announcements", "announcement") } {
            $success = Test-BasicConnectivity -Protocol $protocol -BaseUrl $baseUrl
            if ($success) {
                Test-AnnouncementEndpoints -Protocol $protocol -BaseUrl $baseUrl
            }
        }
        
        "custom" {
            if ([string]::IsNullOrEmpty($Endpoint)) {
                Write-Error "Custom queries require an endpoint. Use: .\query-rvnkcoreapi-DEV.ps1 custom '/api/v1/endpoint' GET"
                Show-UsageExamples
                exit 1
            }
            Invoke-CustomRequest -Protocol $protocol -BaseUrl $baseUrl -Endpoint $Endpoint -Method $Method -Body $Body
        }
        
        default {
            Write-Error "Unknown query type: $QueryType"
            Show-UsageExamples
            exit 1
        }
    }
}

# Summary (for non-custom requests)
if ($QueryType -ne "custom") {
    Write-Host "`n" + "="*50 -ForegroundColor Cyan
    Write-Host "QUERY SUMMARY" -ForegroundColor Cyan
    Write-Host "="*50 -ForegroundColor Cyan
    Write-Host "Query Type: $QueryType" -ForegroundColor White
    Write-Host "Protocols Tested: $($testUrls.Protocol -join ', ')" -ForegroundColor White
    
    if ($totalFailed -gt 0) {
        Write-Host "Some requests failed. Check the output above for details." -ForegroundColor Red
        exit 1
    } else {
        Write-Host "All requests completed successfully!" -ForegroundColor Green
        exit 0
    }
}
