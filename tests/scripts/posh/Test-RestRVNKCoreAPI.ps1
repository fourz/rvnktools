# RVNKCoreServer HTTP & HTTPS API Test Script
# Tests both HTTP and HTTPS endpoints comprehensively
# PowerShell 7+ required

# MANUAL TESTING EXAMPLES:
# HTTP:  Invoke-WebRequest http://localhost:8080/api/v1/players/name/wizardofire -Headers @{"X-API-Key"="your-api-key"}
# HTTPS: [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
#        Invoke-WebRequest https://localhost:8081/api/v1/players/name/wizardofire -Headers @{"X-API-Key"="your-api-key"}

param (
    [Parameter(Mandatory = $false)]
    [string]$HttpUrl = "http://localhost:8080",
    [Parameter(Mandatory = $false)]
    [string]$HttpsUrl = "https://localhost:8081",
    [Parameter(Mandatory = $false)]
    [string]$ApiKey = "test-api-key",
    [Parameter(Mandatory = $false)]
    [switch]$IgnoreSSLErrors,
    [Parameter(Mandatory = $false)]
    [switch]$HttpOnly,
    [Parameter(Mandatory = $false)]
    [switch]$HttpsOnly,
    [Parameter(Mandatory = $false)]
    [switch]$Detail
)

# Set TLS 1.2 for secure connections (required for modern HTTPS)
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

$Endpoints = @{
    Players = "/api/v1/players"
    OnlinePlayers = "/api/v1/players/online"
    PlayerByUuid = "/api/v1/players/{uuid}"
    PlayerByName = "/api/v1/players/name/{name}"
    PlayersByGroup = "/api/v1/players/group/{group}"
    SearchPlayers = "/api/v1/players/search"
    PlayerCount = "/api/v1/players/count"
}

$TestData = @{
    PlayerUuid = "94c37976-5134-40b0-9e03-722ae6664fea"
    PlayerName = "wizardofire"
    GroupName = "default"
    SearchQuery = "wizard"
}

$Headers = @{
    "X-API-Key" = $ApiKey
    "Content-Type" = "application/json"
    "Accept" = "application/json"
}

$TestResults = @{
    HTTP = @{ Passed = 0; Failed = 0; Errors = @() }
    HTTPS = @{ Passed = 0; Failed = 0; Errors = @() }
}

function Write-TestResult {
    param (
        [string]$Protocol,
        [string]$TestName,
        [bool]$Success,
        [string]$Message,
        [object]$Err = $null,
        [hashtable]$RequestInfo = $null,
        [object]$ResponseInfo = $null
    )
    if ($Success) {
        $prefix = "[PASS]"
        $color = "Green"
        Write-Host "$prefix [$Protocol] $TestName : $Message" -ForegroundColor $color
        $TestResults[$Protocol].Passed++
    } else {
        $prefix = "[FAIL]"
        $color = "Red"
        Write-Host "$prefix [$Protocol] $TestName : $Message" -ForegroundColor $color
        $TestResults[$Protocol].Failed++
        if ($Err) {
            $TestResults[$Protocol].Errors += @{
                Test = $TestName
                Error = $Err
                Message = $Message
            }
        }
    }
    if ($Detail -and $RequestInfo) {
        Write-Host "    --- INPUT ---" -ForegroundColor DarkGray
        Write-Host "    URI: $($RequestInfo.Uri)" -ForegroundColor Gray
        Write-Host "    Method: $($RequestInfo.Method)" -ForegroundColor Gray
        Write-Host "    Headers: $($RequestInfo.Headers | ConvertTo-Json -Compress)" -ForegroundColor Gray
        if ($RequestInfo.Body) {
            Write-Host "    Body: $($RequestInfo.Body | ConvertTo-Json -Compress)" -ForegroundColor Gray
        }
        if ($RequestInfo.QueryParams) {
            Write-Host "    QueryParams: $($RequestInfo.QueryParams | ConvertTo-Json -Compress)" -ForegroundColor Gray
        }
    }
    if ($Detail -and $ResponseInfo) {
        Write-Host "    --- OUTPUT ---" -ForegroundColor DarkGray
        Write-Host "    Response: $($ResponseInfo | ConvertTo-Json -Compress)" -ForegroundColor Gray
    } elseif ($Detail -and $Err) {
        Write-Host "    --- OUTPUT (Error) ---" -ForegroundColor DarkGray
        Write-Host "    Error: $($Err | ConvertTo-Json -Compress)" -ForegroundColor Gray
    }
}

function Invoke-ApiRequest {
    param (
        [string]$BaseUrl,
        [string]$Endpoint,
        [string]$Method = "GET",
        [object]$Body = $null,
        [hashtable]$QueryParams = @{}
    )
    $Uri = "$BaseUrl$Endpoint"
    if ($QueryParams.Count -gt 0) {
        $queryPairs = @()
        foreach ($param in $QueryParams.GetEnumerator()) {
            $encodedKey = [System.Uri]::EscapeDataString($param.Key)
            $encodedValue = [System.Uri]::EscapeDataString($param.Value)
            $queryPairs += "$encodedKey=$encodedValue"
        }
        $Uri += "?" + ($queryPairs -join "&")
    }
    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            Headers = $Headers
            ContentType = "application/json"
            ErrorAction = "Stop"
        }
        if ($IgnoreSSLErrors) {
            $params.SkipCertificateCheck = $true
        }
        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json)
        }
        $requestInfo = @{
            Uri = $Uri
            Method = $Method
            Headers = $Headers
            Body = $Body
            QueryParams = $QueryParams
        }
        $response = Invoke-RestMethod @params
        return @{ Success = $true; Data = $response; RequestInfo = $requestInfo; ResponseInfo = $response }
    } catch {
        $requestInfo = @{
            Uri = $Uri
            Method = $Method
            Headers = $Headers
            Body = $Body
            QueryParams = $QueryParams
        }
        return @{ Success = $false; Error = $_; RequestInfo = $requestInfo }
    }
}

function Test-Connection {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Players
    $message = if ($result.Success) { "Connected successfully" } else { "Failed to connect" }
    Write-TestResult -Protocol $Protocol -TestName "Connection" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-Authentication {
    param ([string]$Protocol, [string]$BaseUrl)
    $originalApiKey = $Headers["X-API-Key"]
    $Headers["X-API-Key"] = "invalid-key"
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Players
    $Headers["X-API-Key"] = $originalApiKey
    $isAuthFail = (-not $result.Success -and $result.Error.Exception.Response.StatusCode -eq 401)
    $message = if ($isAuthFail) { "Rejected invalid API key" } else { "Failed to reject invalid API key" }
    Write-TestResult -Protocol $Protocol -TestName "Authentication" -Success $isAuthFail -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAllPlayers {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Players
    if ($result.Success) {
        $playerCount = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Players list retrieved ($playerCount players found)"
        
        # Show detailed player data if Detail flag is enabled
        if ($Detail -and $result.Data) {
            $message += "`n    Raw Response Data: " + ($result.Data | ConvertTo-Json -Compress)
        }
    } else {
        $message = "Failed to retrieve players"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get All Players" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetOnlinePlayers {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.OnlinePlayers
    if ($result.Success) {
        $playerCount = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Online players retrieved ($playerCount online players found)"
        
        # Show detailed player data if Detail flag is enabled
        if ($Detail -and $result.Data) {
            $message += "`n    Raw Response Data: " + ($result.Data | ConvertTo-Json -Compress)
        }
    } else {
        $message = "Failed to retrieve online players"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get Online Players" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetPlayerByUuid {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.PlayerByUuid -replace "{uuid}", $TestData.PlayerUuid
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $message = if ($result.Success) { "Player by UUID retrieved" } else { "Failed to retrieve player by UUID" }
    Write-TestResult -Protocol $Protocol -TestName "Get Player by UUID" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetPlayerByName {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.PlayerByName -replace "{name}", $TestData.PlayerName
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $message = if ($result.Success) { "Player by name retrieved" } else { "Failed to retrieve player by name" }
    Write-TestResult -Protocol $Protocol -TestName "Get Player by Name" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetPlayersByGroup {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.PlayersByGroup -replace "{group}", $TestData.GroupName
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $message = if ($result.Success) { "Players by group retrieved" } else { "Failed to retrieve players by group" }
    Write-TestResult -Protocol $Protocol -TestName "Get Players by Group" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-SearchPlayers {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.SearchPlayers -QueryParams @{ name = $TestData.SearchQuery }
    $message = if ($result.Success) { "Players search succeeded" } else { "Failed to search players" }
    Write-TestResult -Protocol $Protocol -TestName "Search Players" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetPlayerCount {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.PlayerCount
    if ($result.Success) {
        $countValue = if ($result.Data -and $result.Data.count -ne $null) { $result.Data.count } else { "unknown" }
        $message = "Player count retrieved (count: $countValue)"
        
        # Show detailed count data if Detail flag is enabled
        if ($Detail -and $result.Data) {
            $message += "`n    Raw Response Data: " + ($result.Data | ConvertTo-Json -Compress)
        }
    } else {
        $message = "Failed to retrieve player count"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get Player Count" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-PutPlayerLocation {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = "$($Endpoints.PlayerByUuid -replace '{uuid}', $TestData.PlayerUuid)/location"
    $locationData = @{
        world = "world"
        x = 100.5
        y = 64.0
        z = -200.3
    }
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint -Method "PUT" -Body $locationData
    $message = if ($result.Success) { "Player location updated" } else { "Failed to update player location" }
    Write-TestResult -Protocol $Protocol -TestName "Update Player Location" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-PutPlayerGroups {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = "$($Endpoints.PlayerByUuid -replace '{uuid}', $TestData.PlayerUuid)/groups"
    $groupData = @{
        action = "set"
        groups = @("default", "vip")
    }
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint -Method "PUT" -Body $groupData
    $message = if ($result.Success) { "Player groups updated" } else { "Failed to update player groups" }
    Write-TestResult -Protocol $Protocol -TestName "Update Player Groups" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Run-AllTests {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n=== Testing $Protocol API ($BaseUrl) ===" -ForegroundColor Cyan
    
    # Basic tests
    Test-Connection -Protocol $Protocol -BaseUrl $BaseUrl
    Test-Authentication -Protocol $Protocol -BaseUrl $BaseUrl
    
    # GET endpoint tests
    Test-GetAllPlayers -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetOnlinePlayers -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerByUuid -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerByName -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayersByGroup -Protocol $Protocol -BaseUrl $BaseUrl
    Test-SearchPlayers -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerCount -Protocol $Protocol -BaseUrl $BaseUrl
    
    # PUT endpoint tests
    Test-PutPlayerLocation -Protocol $Protocol -BaseUrl $BaseUrl
    Test-PutPlayerGroups -Protocol $Protocol -BaseUrl $BaseUrl
}

# Main execution
Write-Host "RVNKCoreServer REST API Comprehensive Test Suite" -ForegroundColor Magenta
Write-Host "================================================" -ForegroundColor Magenta

if ($IgnoreSSLErrors) {
    Write-Host "SSL Certificate Validation: DISABLED" -ForegroundColor Yellow
}


# Run tests for HTTP, HTTPS, or both depending on parameters:
# - If -HttpOnly is supplied, only test HTTP
# - If -HttpsOnly is supplied, only test HTTPS
# - If neither is supplied, test both
if ($HttpOnly -and -not $HttpsOnly) {
    Run-AllTests -Protocol "HTTP" -BaseUrl $HttpUrl
} elseif ($HttpsOnly -and -not $HttpOnly) {
    Run-AllTests -Protocol "HTTPS" -BaseUrl $HttpsUrl
} else {
    Run-AllTests -Protocol "HTTP" -BaseUrl $HttpUrl
    Run-AllTests -Protocol "HTTPS" -BaseUrl $HttpsUrl
}

# Print comprehensive summary
Write-Host "`n" + "="*50 -ForegroundColor Cyan
Write-Host "COMPREHENSIVE TEST SUMMARY" -ForegroundColor Cyan
Write-Host "="*50 -ForegroundColor Cyan

$totalPassed = 0
$totalFailed = 0

foreach ($protocol in $TestResults.Keys) {
    if (($protocol -eq "HTTP" -and -not $HttpsOnly) -or ($protocol -eq "HTTPS" -and -not $HttpOnly)) {
        $results = $TestResults[$protocol]
        $color = if ($results.Failed -eq 0) { "Green" } else { "Red" }
        Write-Host "$protocol Tests - Passed: $($results.Passed), Failed: $($results.Failed)" -ForegroundColor $color
        $totalPassed += $results.Passed
        $totalFailed += $results.Failed
    }
}

Write-Host "`nOverall Results:" -ForegroundColor Cyan
Write-Host "Total Passed: $totalPassed" -ForegroundColor Green
Write-Host "Total Failed: $totalFailed" -ForegroundColor Red

# Show errors if any
$hasErrors = $false
foreach ($protocol in $TestResults.Keys) {
    if (($protocol -eq "HTTP" -and -not $HttpsOnly) -or ($protocol -eq "HTTPS" -and -not $HttpOnly)) {
        $results = $TestResults[$protocol]
        if ($results.Errors.Count -gt 0) {
            if (-not $hasErrors) {
                Write-Host "`nDetailed Errors:" -ForegroundColor Red
                $hasErrors = $true
            }
            Write-Host "`n$protocol Errors:" -ForegroundColor Red
            foreach ($err in $results.Errors) {
                Write-Host "  Test: $($err.Test)" -ForegroundColor Yellow
                Write-Host "  Message: $($err.Message)"
                if ($err.Error -and $err.Error.Exception) {
                    Write-Host ("  Error: " + $err.Error.Exception.Message) -ForegroundColor Red
                }
                Write-Host ""
            }
        }
    }
}

if ($totalFailed -gt 0) { exit 1 }
