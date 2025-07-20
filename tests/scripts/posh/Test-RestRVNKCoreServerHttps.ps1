# Test-RestRVNKCoreServerHttps.ps1
# Tests the HTTPS functionality of RVNKCoreServer REST API endpoints
# Requires: PowerShell 7.0 or later

param (
    [Parameter(Mandatory = $false)]
    [string]$BaseUrl = "https://localhost:8081",  # Default HTTPS port from api.https.port
    [Parameter(Mandatory = $false)]
    [string]$ApiKey = "test-api-key",
    [Parameter(Mandatory = $false)]
    [switch]$SkipCertValidation = $true
)

# Test configuration
$Config = @{
    BaseUrl            = $BaseUrl
    ApiKey            = $ApiKey
    Endpoints         = @{
        Players     = "/v1/players"
        OnlinePlayers = "/v1/players/online"
        PlayerByUuid  = "/v1/players/{uuid}"
        PlayerByName  = "/v1/players/name/{name}"
        PlayersByGroup = "/v1/players/group/{group}"
        SearchPlayers = "/v1/players/search"
    }
    Headers           = @{
        "X-API-Key"     = $ApiKey
        "Content-Type"  = "application/json"
        "Accept"        = "application/json"
    }
    TestData         = @{
        PlayerUuid   = "94c37976-5134-40b0-9e03-722ae6664fea"  # Example UUID
        PlayerName   = "wizardofire"
        GroupName    = "default"
        SearchQuery  = "Test"
    }
}

# Initialize test results
$TestResults = @{
    Passed = 0
    Failed = 0
    Errors = @()
}

function Write-TestResult {
    param (
        [string]$TestName,
        [bool]$Success,
        [string]$Message,
        [object]$Err = $null
    )

    if ($Success) {
        Write-Host "✓ $TestName : $Message" -ForegroundColor Green
        $TestResults.Passed++
    }
    else {
        Write-Host "✗ $TestName : $Message" -ForegroundColor Red
        $TestResults.Failed++
        if ($Err) {
            $TestResults.Errors += @{
                Test = $TestName
                Error = $Err
                Message = $Message
            }
        }
    }
}

function Invoke-ApiRequest {
    param (
        [string]$Endpoint,
        [string]$Method = "GET",
        [object]$Body = $null,
        [hashtable]$QueryParams = @{}
    )

    $Uri = "$($Config.BaseUrl)$Endpoint"
    
    # Add query parameters if any
    if ($QueryParams.Count -gt 0) {
        $QueryString = [System.Web.HttpUtility]::ParseQueryString("")
        foreach ($param in $QueryParams.GetEnumerator()) {
            $QueryString[$param.Key] = $param.Value
        }
        $Uri += "?$QueryString"
    }

    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            Headers = $Config.Headers
            ContentType = "application/json"
            SkipCertificateCheck = $SkipCertValidation
            ErrorAction = "Stop"
        }

        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json)
        }

        $response = Invoke-RestMethod @params
        return @{
            Success = $true
            Data = $response
        }
    }
    catch {
        return @{
            Success = $false
            Error = $_
        }
    }
}

# Test Cases

function Test-HttpsConnection {
    $result = Invoke-ApiRequest -Endpoint $Config.Endpoints.Players
    
    if ($result.Success) {
        Write-TestResult -TestName "HTTPS Connection" -Success $true -Message "Successfully connected to HTTPS endpoint"
    }
    else {
        Write-TestResult -TestName "HTTPS Connection" -Success $false -Message "Failed to connect to HTTPS endpoint" -Error $result.Error
    }
}

function Test-Authentication {
    # Test with invalid API key
    $originalApiKey = $Config.Headers["X-API-Key"]
    $Config.Headers["X-API-Key"] = "invalid-key"
    
    $result = Invoke-ApiRequest -Endpoint $Config.Endpoints.Players
    
    $Config.Headers["X-API-Key"] = $originalApiKey
    
    if (-not $result.Success -and $result.Error.Response.StatusCode -eq 401) {
        Write-TestResult -TestName "Authentication" -Success $true -Message "Successfully rejected invalid API key"
    }
    else {
        Write-TestResult -TestName "Authentication" -Success $false -Message "Failed to reject invalid API key" -Error $result.Error
    }
}

function Test-GetAllPlayers {
    $result = Invoke-ApiRequest -Endpoint $Config.Endpoints.Players
    
    if ($result.Success) {
        Write-TestResult -TestName "Get All Players" -Success $true -Message "Successfully retrieved players list"
    }
    else {
        Write-TestResult -TestName "Get All Players" -Success $false -Message "Failed to retrieve players list" -Error $result.Error
    }
}

function Test-GetOnlinePlayers {
    $result = Invoke-ApiRequest -Endpoint $Config.Endpoints.OnlinePlayers
    
    if ($result.Success) {
        Write-TestResult -TestName "Get Online Players" -Success $true -Message "Successfully retrieved online players"
    }
    else {
        Write-TestResult -TestName "Get Online Players" -Success $false -Message "Failed to retrieve online players" -Error $result.Error
    }
}

function Test-GetPlayerByUuid {
    $endpoint = $Config.Endpoints.PlayerByUuid -replace "{uuid}", $Config.TestData.PlayerUuid
    $result = Invoke-ApiRequest -Endpoint $endpoint
    
    if ($result.Success) {
        Write-TestResult -TestName "Get Player by UUID" -Success $true -Message "Successfully retrieved player by UUID"
    }
    else {
        Write-TestResult -TestName "Get Player by UUID" -Success $false -Message "Failed to retrieve player by UUID" -Error $result.Error
    }
}

function Test-GetPlayerByName {
    $endpoint = $Config.Endpoints.PlayerByName -replace "{name}", $Config.TestData.PlayerName
    $result = Invoke-ApiRequest -Endpoint $endpoint
    
    if ($result.Success) {
        Write-TestResult -TestName "Get Player by Name" -Success $true -Message "Successfully retrieved player by name"
    }
    else {
        Write-TestResult -TestName "Get Player by Name" -Success $false -Message "Failed to retrieve player by name" -Error $result.Error
    }
}

function Test-GetPlayersByGroup {
    $endpoint = $Config.Endpoints.PlayersByGroup -replace "{group}", $Config.TestData.GroupName
    $result = Invoke-ApiRequest -Endpoint $endpoint
    
    if ($result.Success) {
        Write-TestResult -TestName "Get Players by Group" -Success $true -Message "Successfully retrieved players by group"
    }
    else {
        Write-TestResult -TestName "Get Players by Group" -Success $false -Message "Failed to retrieve players by group" -Error $result.Error
    }
}

function Test-SearchPlayers {
    $result = Invoke-ApiRequest -Endpoint $Config.Endpoints.SearchPlayers -QueryParams @{ name = $Config.TestData.SearchQuery }
    
    if ($result.Success) {
        Write-TestResult -TestName "Search Players" -Success $true -Message "Successfully searched for players"
    }
    else {
        Write-TestResult -TestName "Search Players" -Success $false -Message "Failed to search for players" -Error $result.Error
    }
}

# Run Tests
Write-Host "`nStarting RVNKCoreServer HTTPS API Tests`n" -ForegroundColor Cyan

# Basic connectivity tests
Test-HttpsConnection
Test-Authentication

# Endpoint tests
Test-GetAllPlayers
Test-GetOnlinePlayers
Test-GetPlayerByUuid
Test-GetPlayerByName
Test-GetPlayersByGroup
Test-SearchPlayers

# Print Summary
Write-Host "`nTest Summary:" -ForegroundColor Cyan
Write-Host "Passed: $($TestResults.Passed)" -ForegroundColor Green
Write-Host "Failed: $($TestResults.Failed)" -ForegroundColor Red

if ($TestResults.Errors.Count -gt 0) {
    Write-Host "`nErrors:" -ForegroundColor Red
    foreach ($err in $TestResults.Errors) {
        Write-Host "Test: $($err.Test)" -ForegroundColor Yellow
        Write-Host "Message: $($err.Message)"
        Write-Host "Error: $($err.Error.Exception.Message)" -ForegroundColor Red
        Write-Host ""
    }
}

# Exit with error if any tests failed
if ($TestResults.Failed -gt 0) {
    exit 1
}
