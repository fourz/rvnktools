# RVNKCoreServer HTTP & HTTPS API Test Script
# Tests both HTTP and HTTPS endpoints comprehensively for Player and Announcement APIs
# PowerShell 7+ required

# USAGE EXAMPLES:
# Test all APIs: .\Test-RestRVNKCoreAPI.ps1 -Tests all
# Test player API only: .\Test-RestRVNKCoreAPI.ps1 -Tests player -HttpsOnly
# Test player world API only: .\Test-RestRVNKCoreAPI.ps1 -Tests playerworld -HttpOnly
# Test announcements only: .\Test-RestRVNKCoreAPI.ps1 -Tests announcement -HttpOnly

# MANUAL TESTING EXAMPLES:
# HTTP:  Invoke-WebRequest http://localhost:8080/api/v1/player/name/wizardofire -Headers @{"X-API-Key"="your-api-key"}
# HTTPS: [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
#        Invoke-WebRequest https://localhost:8081/api/v1/player/name/wizardofire -Headers @{"X-API-Key"="your-api-key"}

param (
    [Parameter(Mandatory = $false)]
    [string]$HttpUrl = "",
    [Parameter(Mandatory = $false)]
    [string]$HttpsUrl = "",
    [Parameter(Mandatory = $false)]
    [string]$ApiKey = "",
    [Parameter(Mandatory = $false)]
    [switch]$IgnoreSSLErrors,
    [Parameter(Mandatory = $false)]
    [switch]$HttpOnly,
    [Parameter(Mandatory = $false)]
    [switch]$HttpsOnly,
    [Parameter(Mandatory = $false)]
    [switch]$Detail,
    [Parameter(Mandatory = $false)]
    [ValidateSet("all", "player", "playerworld", "announcement")]
    [string]$Tests = "all"
)

# Set TLS 1.2 for secure connections (required for modern HTTPS)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Load configuration from project.json
$projectJsonPath = Join-Path $PSScriptRoot "..\..\..\.vscode\project.json"
$config = $null
if (Test-Path $projectJsonPath) {
    try {
        $config = Get-Content $projectJsonPath | ConvertFrom-Json
        Write-Host "Configuration loaded from project.json" -ForegroundColor Green
    } catch {
        Write-Warning "Failed to load project.json configuration: $($_.Exception.Message)"
    }
} else {
    Write-Warning "project.json not found at: $projectJsonPath"
}

# Use configuration values if not provided as parameters and config is available
if (-not $HttpUrl -and $config -and $config.RVNKCoreAPI -and $config.RVNKCoreAPI.httpUrl) {
    $HttpUrl = $config.RVNKCoreAPI.httpUrl
} elseif (-not $HttpUrl) {
    $HttpUrl = "http://localhost:8080"
}

if (-not $HttpsUrl -and $config -and $config.RVNKCoreAPI -and $config.RVNKCoreAPI.httpsUrl) {
    $HttpsUrl = $config.RVNKCoreAPI.httpsUrl
} elseif (-not $HttpsUrl) {
    $HttpsUrl = "https://localhost:8081"
}

if (-not $ApiKey -and $config -and $config.RVNKCoreAPI -and $config.RVNKCoreAPI.apiKey) {
    $ApiKey = $config.RVNKCoreAPI.apiKey
    Write-Host "Using API key from project.json configuration" -ForegroundColor Green
} elseif (-not $ApiKey) {
    $ApiKey = "test-api-key"
    Write-Warning "Using default API key - update project.json for production use"
}

Write-Host "Configuration: HTTP=$HttpUrl, HTTPS=$HttpsUrl, API Key=[REDACTED]" -ForegroundColor Cyan

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
    # Player API Endpoints
    Players = "/api/v1/players"
    OnlinePlayers = "/api/v1/players/online"
    PlayerByUuid = "/api/v1/players/{uuid}"
    PlayerByName = "/api/v1/player/name/{name}"
    PlayerNameHistory = "/api/v1/player/name/{name}/history"
    PlayersByGroup = "/api/v1/players/group/{group}"
    SearchPlayers = "/api/v1/players/search"
    PlayerCount = "/api/v1/players/count"
    
    # Player World API Endpoints (NEW)
    PlayerWorlds = "/api/v1/players/{uuid}/worlds"
    PlayerWorldData = "/api/v1/players/{uuid}/worlds/{world}"
    PlayerWorldLocation = "/api/v1/players/{uuid}/worlds/{world}/location"
    PlayerVisitedWorlds = "/api/v1/players/{uuid}/worlds/visited"
    PlayerWorldStats = "/api/v1/players/{uuid}/worlds/stats"
    
    # Announcement API Endpoints
    Announcements = "/api/v1/announcements"
    AnnouncementById = "/api/v1/announcements/{id}"
    AnnouncementsByType = "/api/v1/announcements/type/{type}"
    AnnouncementsByWorld = "/api/v1/announcements/world/{world}"
    AnnouncementsByGroup = "/api/v1/announcements/group/{group}"
    AnnouncementSearch = "/api/v1/announcements/search"
    BulkAnnouncements = "/api/v1/announcements/bulk"
    BulkActivate = "/api/v1/announcements/bulk/activate"
    BulkDeactivate = "/api/v1/announcements/bulk/deactivate"
    AnnouncementCount = "/api/v1/announcements/count"
    AnnouncementMetrics = "/api/v1/announcements/metrics"
}

$TestData = @{
    # Player test data
    PlayerUuid = "94c37976-5134-40b0-9e03-722ae6664fea"
    PlayerName = "wizardofire"
    GroupName = "default"
    SearchQuery = "wizard"
    
    # Player World test data
    TestWorld = "world"
    AltWorld = "world_nether"
    
    # Announcement test data
    NewAnnouncement = @{
        content = "Welcome to our test server! This is a test announcement."
        type = "BROADCAST"
        priority = 1
        world = "world"
        targetGroup = "players"
        isActive = $true
        tags = @("test", "api", "welcome")
        metadata = @{
            author = "TestScript"
            category = "general"
        }
    }
    AnnouncementType = "BROADCAST"
    WorldName = "world"
    CreateTestData = $false
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
        
        # Add SkipCertificateCheck for PowerShell 7+ when IgnoreSSLErrors is enabled
        if ($IgnoreSSLErrors -and $PSVersionTable.PSVersion.Major -ge 7) {
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

function Test-GetPlayerNameHistory {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.PlayerNameHistory -replace "{name}", $TestData.PlayerName
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    if ($result.Success) {
        $historyCount = if ($result.Data.nameHistory -is [array]) { $result.Data.nameHistory.Count } else { if ($result.Data.nameHistory) { 1 } else { 0 } }
        $message = "Player name history retrieved ($historyCount historical names found)"
        
        # Show detailed history data if Detail flag is enabled
        if ($Detail -and $result.Data) {
            $message += "`n    Raw Response Data: " + ($result.Data | ConvertTo-Json -Compress)
        }
    } else {
        $message = "Failed to retrieve player name history"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get Player Name History" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
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
        $countValue = if ($result.Data -and $null -ne $result.Data.count) { $result.Data.count } else { "unknown" }
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

# ===========================================
# PLAYER WORLD API TEST FUNCTIONS
# ===========================================

function Test-GetPlayerWorlds {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.PlayerWorlds -replace '\{uuid\}', $TestData.PlayerUuid
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $message = if ($result.Success) { "Retrieved player world data" } else { "Failed to retrieve player world data" }
    Write-TestResult -Protocol $Protocol -TestName "Get Player Worlds" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetPlayerWorldData {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.PlayerWorldData -replace '\{uuid\}', $TestData.PlayerUuid -replace '\{world\}', $TestData.TestWorld
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $message = if ($result.Success) { "Retrieved player world data for specific world" } else { "Failed to retrieve player world data for specific world" }
    Write-TestResult -Protocol $Protocol -TestName "Get Player World Data" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetPlayerWorldLocation {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.PlayerWorldLocation -replace '\{uuid\}', $TestData.PlayerUuid -replace '\{world\}', $TestData.TestWorld
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $message = if ($result.Success) { "Retrieved player last known location" } else { "Failed to retrieve player last known location" }
    Write-TestResult -Protocol $Protocol -TestName "Get Player World Location" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetPlayerVisitedWorlds {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.PlayerVisitedWorlds -replace '\{uuid\}', $TestData.PlayerUuid
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $message = if ($result.Success) { "Retrieved player visited worlds" } else { "Failed to retrieve player visited worlds" }
    Write-TestResult -Protocol $Protocol -TestName "Get Player Visited Worlds" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetPlayerWorldStats {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.PlayerWorldStats -replace '\{uuid\}', $TestData.PlayerUuid
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $message = if ($result.Success) { "Retrieved player world statistics" } else { "Failed to retrieve player world statistics" }
    Write-TestResult -Protocol $Protocol -TestName "Get Player World Stats" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

# ===========================================
# ANNOUNCEMENT API TEST FUNCTIONS
# ===========================================

$Script:CreatedAnnouncementId = $null
$Script:BulkCreatedIds = @()

function Test-AnnouncementConnection {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Announcements
    $message = if ($result.Success) { "Announcement API connected successfully" } else { "Failed to connect to announcement API" }
    Write-TestResult -Protocol $Protocol -TestName "Announcement Connection" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAllAnnouncements {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Announcements
    if ($result.Success) {
        $count = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Retrieved $count announcements successfully"
        if ($Detail -and $result.Data) {
            $message += "`n    Raw Response Data: " + ($result.Data | ConvertTo-Json -Compress)
        }
    } else {
        $message = "Failed to retrieve announcements"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get All Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-CreateAnnouncement {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Announcements -Method "POST" -Body $TestData.NewAnnouncement
    if ($result.Success) {
        $message = "Announcement created successfully"
        if ($result.Data.id) {
            $Script:CreatedAnnouncementId = $result.Data.id
        }
    } else {
        $message = "Failed to create announcement"
    }
    Write-TestResult -Protocol $Protocol -TestName "Create Announcement" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAnnouncementById {
    param ([string]$Protocol, [string]$BaseUrl)
    if (-not $Script:CreatedAnnouncementId) {
        Write-TestResult -Protocol $Protocol -TestName "Get Announcement by ID" -Success $false -Message "No announcement ID available for testing"
        return
    }
    $endpoint = $Endpoints.AnnouncementById -replace "{id}", $Script:CreatedAnnouncementId
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $message = if ($result.Success) { "Announcement retrieved by ID successfully" } else { "Failed to retrieve announcement by ID" }
    Write-TestResult -Protocol $Protocol -TestName "Get Announcement by ID" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-UpdateAnnouncement {
    param ([string]$Protocol, [string]$BaseUrl)
    if (-not $Script:CreatedAnnouncementId) {
        Write-TestResult -Protocol $Protocol -TestName "Update Announcement" -Success $false -Message "No announcement ID available for testing"
        return
    }
    $updateData = $TestData.NewAnnouncement.Clone()
    $updateData.content = "Updated: $($updateData.content)"
    $updateData.priority = 3
    $endpoint = $Endpoints.AnnouncementById -replace "{id}", $Script:CreatedAnnouncementId
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint -Method "PUT" -Body $updateData
    $message = if ($result.Success) { "Announcement updated successfully" } else { "Failed to update announcement" }
    Write-TestResult -Protocol $Protocol -TestName "Update Announcement" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-SearchAnnouncements {
    param ([string]$Protocol, [string]$BaseUrl)
    $searchQuery = @{
        query = "test"
        type = "BROADCAST"
        isActive = $true
    }
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.AnnouncementSearch -Method "POST" -Body $searchQuery
    if ($result.Success) {
        $count = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Found $count announcements matching search criteria"
    } else {
        $message = "Failed to search announcements"
    }
    Write-TestResult -Protocol $Protocol -TestName "Search Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAnnouncementsByType {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.AnnouncementsByType -replace "{type}", $TestData.AnnouncementType
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    if ($result.Success) {
        $count = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Found $count announcements of type '$($TestData.AnnouncementType)'"
    } else {
        $message = "Failed to retrieve announcements by type"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get Announcements by Type" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAnnouncementsByWorld {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.AnnouncementsByWorld -replace "{world}", $TestData.WorldName
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    if ($result.Success) {
        $count = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Found $count announcements for world '$($TestData.WorldName)'"
    } else {
        $message = "Failed to retrieve announcements by world"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get Announcements by World" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAnnouncementsByGroup {
    param ([string]$Protocol, [string]$BaseUrl)
    $endpoint = $Endpoints.AnnouncementsByGroup -replace "{group}", $TestData.GroupName
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    if ($result.Success) {
        $count = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Found $count announcements for group '$($TestData.GroupName)'"
    } else {
        $message = "Failed to retrieve announcements by group"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get Announcements by Group" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-BulkCreateAnnouncements {
    param ([string]$Protocol, [string]$BaseUrl)
    $bulkData = @(
        @{
            content = "Bulk announcement 1"
            type = "BROADCAST"
            priority = 1
            world = "world"
            isActive = $true
        },
        @{
            content = "Bulk announcement 2"
            type = "INFO"
            priority = 2
            world = "world"
            isActive = $true
        }
    )
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.BulkAnnouncements -Method "POST" -Body $bulkData
    if ($result.Success) {
        $count = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Created $count announcements in bulk"
        if ($result.Data -is [array]) {
            $Script:BulkCreatedIds = $result.Data | ForEach-Object { $_.id }
        }
    } else {
        $message = "Failed to create announcements in bulk"
    }
    Write-TestResult -Protocol $Protocol -TestName "Bulk Create Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-BulkActivateAnnouncements {
    param ([string]$Protocol, [string]$BaseUrl)
    if (-not $Script:BulkCreatedIds) {
        Write-TestResult -Protocol $Protocol -TestName "Bulk Activate Announcements" -Success $false -Message "No announcement IDs available for bulk activation testing"
        return
    }
    $bulkData = @{ ids = $Script:BulkCreatedIds }
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.BulkActivate -Method "POST" -Body $bulkData
    $message = if ($result.Success) { "Bulk activation completed successfully" } else { "Failed to activate announcements in bulk" }
    Write-TestResult -Protocol $Protocol -TestName "Bulk Activate Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-BulkDeactivateAnnouncements {
    param ([string]$Protocol, [string]$BaseUrl)
    if (-not $Script:BulkCreatedIds) {
        Write-TestResult -Protocol $Protocol -TestName "Bulk Deactivate Announcements" -Success $false -Message "No announcement IDs available for bulk deactivation testing"
        return
    }
    $bulkData = @{ ids = $Script:BulkCreatedIds }
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.BulkDeactivate -Method "POST" -Body $bulkData
    $message = if ($result.Success) { "Bulk deactivation completed successfully" } else { "Failed to deactivate announcements in bulk" }
    Write-TestResult -Protocol $Protocol -TestName "Bulk Deactivate Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAnnouncementCount {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.AnnouncementCount
    if ($result.Success) {
        $count = $result.Data.count
        $message = "Total announcement count: $count"
        if ($Detail -and $result.Data) {
            $message += "`n    Raw Response Data: " + ($result.Data | ConvertTo-Json -Compress)
        }
    } else {
        $message = "Failed to retrieve announcement count"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get Announcement Count" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAnnouncementMetrics {
    param ([string]$Protocol, [string]$BaseUrl)
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.AnnouncementMetrics
    if ($result.Success) {
        $metrics = $result.Data
        $message = "Retrieved metrics: Active=$($metrics.active) Total=$($metrics.total)"
        if ($Detail -and $result.Data) {
            $message += "`n    Raw Response Data: " + ($result.Data | ConvertTo-Json -Compress)
        }
    } else {
        $message = "Failed to retrieve announcement metrics"
    }
    Write-TestResult -Protocol $Protocol -TestName "Get Announcement Metrics" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-DeleteAnnouncement {
    param ([string]$Protocol, [string]$BaseUrl)
    if (-not $Script:CreatedAnnouncementId) {
        Write-TestResult -Protocol $Protocol -TestName "Delete Announcement" -Success $false -Message "No announcement ID available for deletion testing"
        return
    }
    $endpoint = $Endpoints.AnnouncementById -replace "{id}", $Script:CreatedAnnouncementId
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint -Method "DELETE"
    $message = if ($result.Success) { "Announcement deleted successfully" } else { "Failed to delete announcement" }
    Write-TestResult -Protocol $Protocol -TestName "Delete Announcement" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-AnnouncementErrorHandling {
    param ([string]$Protocol, [string]$BaseUrl)
    # Test 404 - Non-existent announcement
    $endpoint = $Endpoints.AnnouncementById -replace "{id}", "non-existent-id"
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $is404 = (-not $result.Success -and $result.Error.Exception.Response.StatusCode -eq 404)
    $message404 = if ($is404) { "404 error properly returned for non-existent resource" } else { "Failed to return 404 for non-existent resource" }
    Write-TestResult -Protocol $Protocol -TestName "Announcement 404 Error Handling" -Success $is404 -Message $message404 -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

# ===========================================
# TEST RUNNER FUNCTIONS
# ===========================================

function Invoke-PlayerTests {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n--- Player API Tests ---" -ForegroundColor Yellow
    
    # GET endpoint tests
    Test-GetAllPlayers -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetOnlinePlayers -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerByUuid -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerByName -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerNameHistory -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayersByGroup -Protocol $Protocol -BaseUrl $BaseUrl
    Test-SearchPlayers -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerCount -Protocol $Protocol -BaseUrl $BaseUrl
    
    # PUT endpoint tests
    Test-PutPlayerLocation -Protocol $Protocol -BaseUrl $BaseUrl
    Test-PutPlayerGroups -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Player World API tests
    Write-Host "`n--- Player World API Tests ---" -ForegroundColor Cyan
    Test-GetPlayerWorlds -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerWorldData -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerWorldLocation -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerVisitedWorlds -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerWorldStats -Protocol $Protocol -BaseUrl $BaseUrl
}

function Invoke-PlayerWorldTests {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n--- Player World API Tests ---" -ForegroundColor Cyan
    Test-GetPlayerWorlds -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerWorldData -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerWorldLocation -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerVisitedWorlds -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetPlayerWorldStats -Protocol $Protocol -BaseUrl $BaseUrl
}

function Invoke-AnnouncementTests {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n--- Announcement API Tests ---" -ForegroundColor Yellow
    
    # Initialize test data
    $Script:CreatedAnnouncementId = $null
    $Script:BulkCreatedIds = @()
    
    # Connection and authentication
    Test-AnnouncementConnection -Protocol $Protocol -BaseUrl $BaseUrl
    
    # CRUD operations
    Test-GetAllAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    Test-CreateAnnouncement -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetAnnouncementById -Protocol $Protocol -BaseUrl $BaseUrl
    Test-UpdateAnnouncement -Protocol $Protocol -BaseUrl $BaseUrl
    Test-SearchAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Query by attributes
    Test-GetAnnouncementsByType -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetAnnouncementsByWorld -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetAnnouncementsByGroup -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Bulk operations
    Test-BulkCreateAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    Test-BulkActivateAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    Test-BulkDeactivateAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Metrics and counts
    Test-GetAnnouncementCount -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetAnnouncementMetrics -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Error handling
    Test-AnnouncementErrorHandling -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Cleanup - delete created announcement
    if ($Script:CreatedAnnouncementId) {
        Test-DeleteAnnouncement -Protocol $Protocol -BaseUrl $BaseUrl
    }
}

function Invoke-AllTests {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n=== Testing $Protocol API ($BaseUrl) ===" -ForegroundColor Cyan
    
    # Basic tests (always run)
    Test-Connection -Protocol $Protocol -BaseUrl $BaseUrl
    Test-Authentication -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Run specific test suites based on $Tests parameter
    switch ($Tests.ToLower()) {
        "all" {
            Invoke-PlayerTests -Protocol $Protocol -BaseUrl $BaseUrl
            Invoke-AnnouncementTests -Protocol $Protocol -BaseUrl $BaseUrl
        }
        "player" {
            Invoke-PlayerTests -Protocol $Protocol -BaseUrl $BaseUrl
        }
        "playerworld" {
            Invoke-PlayerWorldTests -Protocol $Protocol -BaseUrl $BaseUrl
        }
        "announcement" {
            Invoke-AnnouncementTests -Protocol $Protocol -BaseUrl $BaseUrl
        }
    }
}

# Main execution
Write-Host "RVNKCoreServer REST API Comprehensive Test Suite" -ForegroundColor Magenta
Write-Host "================================================" -ForegroundColor Magenta
Write-Host "Running tests for: $Tests" -ForegroundColor Cyan

if ($IgnoreSSLErrors) {
    Write-Host "SSL Certificate Validation: DISABLED" -ForegroundColor Yellow
}

# Run tests for HTTP, HTTPS, or both depending on parameters:
# - If -HttpOnly is supplied, only test HTTP
# - If -HttpsOnly is supplied, only test HTTPS  
# - If neither is supplied, test both
if ($HttpOnly -and -not $HttpsOnly) {
    Invoke-AllTests -Protocol "HTTP" -BaseUrl $HttpUrl
} elseif ($HttpsOnly -and -not $HttpOnly) {
    Invoke-AllTests -Protocol "HTTPS" -BaseUrl $HttpsUrl
} else {
    Invoke-AllTests -Protocol "HTTP" -BaseUrl $HttpUrl
    Invoke-AllTests -Protocol "HTTPS" -BaseUrl $HttpsUrl
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
