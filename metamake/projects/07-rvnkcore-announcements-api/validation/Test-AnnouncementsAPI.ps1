# RVNKCore Announcements API Test Script
# Tests both HTTP and HTTPS endpoints comprehensively
# PowerShell 7+ required for advanced features

# MANUAL TESTING EXAMPLES:
# HTTP:  Invoke-WebRequest http://localhost:8080/api/v1/announcements -Headers @{"X-API-Key"="your-api-key"}
# HTTPS: [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
#        Invoke-WebRequest https://localhost:8081/api/v1/announcements -Headers @{"X-API-Key"="your-api-key"}

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
    [switch]$Detail,
    [Parameter(Mandatory = $false)]
    [switch]$CreateTestData
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
public class ServerCertificateValidationCallback
{
    public static void Ignore()
    {
        if(ServicePointManager.ServerCertificateValidationCallback ==null)
        {
            ServicePointManager.ServerCertificateValidationCallback += 
                delegate
                (
                    Object obj, 
                    X509Certificate certificate, 
                    X509Chain chain, 
                    SslPolicyErrors errors
                )
                {
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

# Define API endpoints for announcements
$Endpoints = @{
    Announcements = "/api/v1/announcements"
    AnnouncementById = "/api/v1/announcements/{id}"
    AnnouncementsByType = "/api/v1/announcements/type/{type}"
    AnnouncementsByWorld = "/api/v1/announcements/world/{world}"
    AnnouncementsByGroup = "/api/v1/announcements/group/{group}"
    SearchAnnouncements = "/api/v1/announcements/search"
    BulkAnnouncements = "/api/v1/announcements/bulk"
    ActivateAnnouncements = "/api/v1/announcements/activate"
    DeactivateAnnouncements = "/api/v1/announcements/deactivate"
    AnnouncementCount = "/api/v1/announcements/count"
    AnnouncementMetrics = "/api/v1/announcements/metrics"
}

# Test data for announcements
$TestData = @{
    # Test announcement creation data
    NewAnnouncement = @{
        content = "Welcome to our test server! Enjoy your stay."
        type = "WELCOME"
        priority = 1
        active = $true
        targetWorld = "world"
        targetGroup = "default"
        displayDuration = 5000
        permission = "announce.view"
        metadata = @{
            author = "TestAdmin"
            category = "server"
        }
    }
    
    # Batch announcement data
    BulkAnnouncements = @(
        @{
            content = "Server maintenance scheduled for tonight"
            type = "BROADCAST"
            priority = 2
            active = $true
            targetWorld = $null
            targetGroup = $null
            displayDuration = 10000
        },
        @{
            content = "New player guide available at /help"
            type = "INFO"
            priority = 1
            active = $true
            targetWorld = "world"
            targetGroup = "newcomer"
            displayDuration = 7000
        }
    )
    
    # Search and filter parameters
    SearchQuery = "welcome"
    AnnouncementType = "BROADCAST"
    WorldName = "world"
    GroupName = "default"
}

$Headers = @{
    "X-API-Key" = $ApiKey
    "Content-Type" = "application/json"
    "Accept" = "application/json"
    "User-Agent" = "RVNKCore-Announcements-API-Test/1.0"
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
        [object]$RequestInfo = $null,
        [object]$ResponseInfo = $null
    )
    
    $status = if ($Success) { "PASS" } else { "FAIL" }
    $color = if ($Success) { "Green" } else { "Red" }
    
    Write-Host "  [$Protocol] $status - $TestName`: $Message" -ForegroundColor $color
    
    if ($Success) {
        $TestResults[$Protocol].Passed++
    } else {
        $TestResults[$Protocol].Failed++
        if ($Err) {
            $TestResults[$Protocol].Errors += "$TestName`: $($Err.Exception.Message)"
        }
    }
    
    if ($Detail -and $RequestInfo) {
        Write-Host "    Request: $($RequestInfo | ConvertTo-Json -Compress)" -ForegroundColor Gray
    }
    
    if ($Detail -and $ResponseInfo) {
        Write-Host "    Response: $($ResponseInfo | ConvertTo-Json -Compress)" -ForegroundColor Gray
    } elseif ($Detail -and $Err) {
        Write-Host "    Error: $($Err.Exception.Message)" -ForegroundColor Yellow
        if ($Err.Exception.Response) {
            Write-Host "    Status: $($Err.Exception.Response.StatusCode)" -ForegroundColor Yellow
        }
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
        $QueryString = ($QueryParams.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "&"
        $Uri += "?$QueryString"
    }
    
    $requestInfo = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers.Clone()
        Body = if ($Body) { $Body | ConvertTo-Json -Depth 10 } else { $null }
    }
    
    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            Headers = $Headers
        }
        
        if ($Body) {
            $params.Body = $Body | ConvertTo-Json -Depth 10
        }
        
        $response = Invoke-RestMethod @params
        
        return @{
            Success = $true
            Data = $response
            RequestInfo = $requestInfo
            ResponseInfo = $response
        }
    } catch {
        return @{
            Success = $false
            Error = $_
            RequestInfo = $requestInfo
            ResponseInfo = $null
        }
    }
}

function Test-Connection {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n🔗 Testing Connection ($Protocol)" -ForegroundColor Cyan
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Announcements
    $message = if ($result.Success) { "Connected successfully" } else { "Failed to connect: $($result.Error.Exception.Message)" }
    Write-TestResult -Protocol $Protocol -TestName "Connection" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-Authentication {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n🔐 Testing Authentication ($Protocol)" -ForegroundColor Cyan
    
    # Test with invalid API key
    $originalApiKey = $Headers["X-API-Key"]
    $Headers["X-API-Key"] = "invalid-key"
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Announcements
    $Headers["X-API-Key"] = $originalApiKey
    
    $isAuthFail = (-not $result.Success -and $result.Error.Exception.Response.StatusCode -eq 401)
    $message = if ($isAuthFail) { "Rejected invalid API key" } else { "Failed to reject invalid API key" }
    Write-TestResult -Protocol $Protocol -TestName "Authentication" -Success $isAuthFail -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
    
    # Test with valid API key
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Announcements
    $message = if ($result.Success) { "Valid API key accepted" } else { "Valid API key rejected" }
    Write-TestResult -Protocol $Protocol -TestName "Valid Authentication" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAllAnnouncements {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n📋 Testing Get All Announcements ($Protocol)" -ForegroundColor Cyan
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Announcements
    
    if ($result.Success) {
        $count = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Retrieved $count announcements successfully"
    } else {
        $message = "Failed to retrieve announcements"
    }
    
    Write-TestResult -Protocol $Protocol -TestName "Get All Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-CreateAnnouncement {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n➕ Testing Create Announcement ($Protocol)" -ForegroundColor Cyan
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Announcements -Method "POST" -Body $TestData.NewAnnouncement
    
    if ($result.Success) {
        $message = "Announcement created successfully"
        # Store the created announcement ID for later tests
        if ($result.Data.id) {
            $TestData.CreatedAnnouncementId = $result.Data.id
        }
    } else {
        $message = "Failed to create announcement"
    }
    
    Write-TestResult -Protocol $Protocol -TestName "Create Announcement" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
    return $result
}

function Test-GetAnnouncementById {
    param ([string]$Protocol, [string]$BaseUrl, [string]$AnnouncementId)
    
    if (-not $AnnouncementId) {
        Write-TestResult -Protocol $Protocol -TestName "Get Announcement by ID" -Success $false -Message "No announcement ID available for testing"
        return
    }
    
    Write-Host "`nTesting Get Announcement by ID ($Protocol)" -ForegroundColor Cyan
    $endpoint = $Endpoints.AnnouncementById -replace "{id}", $AnnouncementId
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    
    $message = if ($result.Success) { "Announcement retrieved by ID successfully" } else { "Failed to retrieve announcement by ID" }
    Write-TestResult -Protocol $Protocol -TestName "Get Announcement by ID" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-UpdateAnnouncement {
    param ([string]$Protocol, [string]$BaseUrl, [string]$AnnouncementId)
    
    if (-not $AnnouncementId) {
        Write-TestResult -Protocol $Protocol -TestName "Update Announcement" -Success $false -Message "No announcement ID available for testing"
        return
    }
    
    Write-Host "`nTesting Update Announcement ($Protocol)" -ForegroundColor Cyan
    $updateData = $TestData.NewAnnouncement.Clone()
    $updateData.content = "Updated: $($updateData.content)"
    $updateData.priority = 3
    
    $endpoint = $Endpoints.AnnouncementById -replace "{id}", $AnnouncementId
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint -Method "PUT" -Body $updateData
    
    $message = if ($result.Success) { "Announcement updated successfully" } else { "Failed to update announcement" }
    Write-TestResult -Protocol $Protocol -TestName "Update Announcement" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-SearchAnnouncements {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n🔎 Testing Search Announcements ($Protocol)" -ForegroundColor Cyan
    $queryParams = @{ q = $TestData.SearchQuery }
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.SearchAnnouncements -QueryParams $queryParams
    
    if ($result.Success) {
        $count = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Found $count announcements matching search query"
    } else {
        $message = "Failed to search announcements"
    }
    
    Write-TestResult -Protocol $Protocol -TestName "Search Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAnnouncementsByType {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n📂 Testing Get Announcements by Type ($Protocol)" -ForegroundColor Cyan
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
    
    Write-Host "`n🌍 Testing Get Announcements by World ($Protocol)" -ForegroundColor Cyan
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
    
    Write-Host "`n👥 Testing Get Announcements by Group ($Protocol)" -ForegroundColor Cyan
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
    
    Write-Host "`n📦 Testing Bulk Create Announcements ($Protocol)" -ForegroundColor Cyan
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.BulkAnnouncements -Method "POST" -Body $TestData.BulkAnnouncements
    
    if ($result.Success) {
        $count = if ($result.Data -is [array]) { $result.Data.Count } else { if ($result.Data) { 1 } else { 0 } }
        $message = "Created $count announcements in bulk operation"
        # Store created IDs for later tests
        if ($result.Data -is [array]) {
            $TestData.BulkCreatedIds = $result.Data | ForEach-Object { $_.id } | Where-Object { $_ }
        }
    } else {
        $message = "Failed to create announcements in bulk"
    }
    
    Write-TestResult -Protocol $Protocol -TestName "Bulk Create Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-BulkActivateAnnouncements {
    param ([string]$Protocol, [string]$BaseUrl)
    
    if (-not $TestData.BulkCreatedIds -or $TestData.BulkCreatedIds.Count -eq 0) {
        Write-TestResult -Protocol $Protocol -TestName "Bulk Activate Announcements" -Success $false -Message "No announcement IDs available for bulk activation testing"
        return
    }
    
    Write-Host "`n🔛 Testing Bulk Activate Announcements ($Protocol)" -ForegroundColor Cyan
    $activationData = @{ ids = $TestData.BulkCreatedIds }
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.ActivateAnnouncements -Method "PUT" -Body $activationData
    
    $message = if ($result.Success) { "Bulk activation completed successfully" } else { "Failed to bulk activate announcements" }
    Write-TestResult -Protocol $Protocol -TestName "Bulk Activate Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-BulkDeactivateAnnouncements {
    param ([string]$Protocol, [string]$BaseUrl)
    
    if (-not $TestData.BulkCreatedIds -or $TestData.BulkCreatedIds.Count -eq 0) {
        Write-TestResult -Protocol $Protocol -TestName "Bulk Deactivate Announcements" -Success $false -Message "No announcement IDs available for bulk deactivation testing"
        return
    }
    
    Write-Host "`n🔛 Testing Bulk Deactivate Announcements ($Protocol)" -ForegroundColor Cyan
    $deactivationData = @{ ids = $TestData.BulkCreatedIds }
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.DeactivateAnnouncements -Method "PUT" -Body $deactivationData
    
    $message = if ($result.Success) { "Bulk deactivation completed successfully" } else { "Failed to bulk deactivate announcements" }
    Write-TestResult -Protocol $Protocol -TestName "Bulk Deactivate Announcements" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAnnouncementCount {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n🔢 Testing Get Announcement Count ($Protocol)" -ForegroundColor Cyan
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.AnnouncementCount
    
    if ($result.Success) {
        $count = $result.Data.count
        $message = "Total announcement count: $count"
    } else {
        $message = "Failed to retrieve announcement count"
    }
    
    Write-TestResult -Protocol $Protocol -TestName "Get Announcement Count" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-GetAnnouncementMetrics {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`nTesting Get Announcement Metrics ($Protocol)" -ForegroundColor Cyan
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.AnnouncementMetrics
    
    if ($result.Success) {
        $metrics = $result.Data
        $message = "Retrieved metrics: Active=$($metrics.activeCount), Total=$($metrics.totalCount)"
    } else {
        $message = "Failed to retrieve announcement metrics"
    }
    
    Write-TestResult -Protocol $Protocol -TestName "Get Announcement Metrics" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-DeleteAnnouncement {
    param ([string]$Protocol, [string]$BaseUrl, [string]$AnnouncementId)
    
    if (-not $AnnouncementId) {
        Write-TestResult -Protocol $Protocol -TestName "Delete Announcement" -Success $false -Message "No announcement ID available for deletion testing"
        return
    }
    
    Write-Host "`nTesting Delete Announcement ($Protocol)" -ForegroundColor Cyan
    $endpoint = $Endpoints.AnnouncementById -replace "{id}", $AnnouncementId
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint -Method "DELETE"
    
    $message = if ($result.Success) { "Announcement deleted successfully" } else { "Failed to delete announcement" }
    Write-TestResult -Protocol $Protocol -TestName "Delete Announcement" -Success $result.Success -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
}

function Test-ErrorHandling {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`nTesting Error Handling ($Protocol)" -ForegroundColor Cyan
    
    # Test 404 - Non-existent announcement
    $endpoint = $Endpoints.AnnouncementById -replace "{id}", "non-existent-id"
    $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $endpoint
    $is404 = (-not $result.Success -and $result.Error.Exception.Response.StatusCode -eq 404)
    $message = if ($is404) { "404 error properly returned for non-existent resource" } else { "Failed to return 404 for non-existent resource" }
    Write-TestResult -Protocol $Protocol -TestName "404 Error Handling" -Success $is404 -Message $message -Err $result.Error -RequestInfo $result.RequestInfo -ResponseInfo $result.ResponseInfo
    
    # Test 400 - Invalid JSON
    try {
        $invalidJson = "{ `"content`": `"Test`" invalid json }"
        $result = Invoke-WebRequest -Uri "$BaseUrl$($Endpoints.Announcements)" -Method "POST" -Headers $Headers -Body $invalidJson
        $is400 = $false
        $message = "Failed to return 400 for invalid JSON"
    } catch {
        $is400 = ($_.Exception.Response.StatusCode -eq 400)
        $message = if ($is400) { "400 error properly returned for invalid JSON" } else { "Unexpected error for invalid JSON: $($_.Exception.Response.StatusCode)" }
    }
    Write-TestResult -Protocol $Protocol -TestName "400 Error Handling" -Success $is400 -Message $message
}

function Create-TestData {
    param ([string]$Protocol, [string]$BaseUrl)
    
    if (-not $CreateTestData) {
        return
    }
    
    Write-Host "`n🔧 Creating Test Data ($Protocol)" -ForegroundColor Yellow
    
    # Create multiple test announcements
    $testAnnouncements = @(
        @{
            content = "Welcome to our Minecraft server! Type /help for commands."
            type = "WELCOME"
            priority = 1
            active = $true
            targetWorld = "world"
            targetGroup = "newcomer"
            displayDuration = 8000
            permission = "announce.view.welcome"
        },
        @{
            content = "Server restart scheduled for maintenance in 1 hour."
            type = "BROADCAST"
            priority = 3
            active = $true
            targetWorld = $null
            targetGroup = $null
            displayDuration = 15000
            permission = "announce.view.broadcast"
        },
        @{
            content = "PvP arena is now open! Type /arena join to participate."
            type = "EVENT"
            priority = 2
            active = $true
            targetWorld = "pvp_arena"
            targetGroup = "default"
            displayDuration = 10000
            permission = "announce.view.event"
        },
        @{
            content = "Daily rewards are available! Visit /rewards to claim yours."
            type = "INFO"
            priority = 1
            active = $true
            targetWorld = $null
            targetGroup = "default"
            displayDuration = 7000
            permission = "announce.view.info"
        }
    )
    
    foreach ($announcement in $testAnnouncements) {
        $result = Invoke-ApiRequest -BaseUrl $BaseUrl -Endpoint $Endpoints.Announcements -Method "POST" -Body $announcement
        if ($result.Success) {
            Write-Host "    Created: $($announcement.content.Substring(0, 50))..." -ForegroundColor Green
        } else {
            Write-Host "    Failed to create: $($announcement.content.Substring(0, 50))..." -ForegroundColor Red
        }
    }
}

function Run-AllTests {
    param ([string]$Protocol, [string]$BaseUrl)
    
    Write-Host "`n" + "="*60 -ForegroundColor Magenta
    Write-Host "Running RVNKCore Announcements API Tests ($Protocol)" -ForegroundColor Magenta
    Write-Host "Base URL: $BaseUrl" -ForegroundColor Gray
    Write-Host "="*60 -ForegroundColor Magenta
    
    # Core functionality tests
    Test-Connection -Protocol $Protocol -BaseUrl $BaseUrl
    Test-Authentication -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetAllAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Create test data if requested
    Create-TestData -Protocol $Protocol -BaseUrl $BaseUrl
    
    # CRUD operation tests
    $createResult = Test-CreateAnnouncement -Protocol $Protocol -BaseUrl $BaseUrl
    $announcementId = if ($createResult.Success -and $createResult.Data.id) { $createResult.Data.id } else { $null }
    
    Test-GetAnnouncementById -Protocol $Protocol -BaseUrl $BaseUrl -AnnouncementId $announcementId
    Test-UpdateAnnouncement -Protocol $Protocol -BaseUrl $BaseUrl -AnnouncementId $announcementId
    
    # Search and filter tests
    Test-SearchAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetAnnouncementsByType -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetAnnouncementsByWorld -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetAnnouncementsByGroup -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Bulk operation tests
    Test-BulkCreateAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    Test-BulkActivateAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    Test-BulkDeactivateAnnouncements -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Metrics and statistics
    Test-GetAnnouncementCount -Protocol $Protocol -BaseUrl $BaseUrl
    Test-GetAnnouncementMetrics -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Error handling tests
    Test-ErrorHandling -Protocol $Protocol -BaseUrl $BaseUrl
    
    # Cleanup - delete created test announcement
    if ($announcementId) {
        Test-DeleteAnnouncement -Protocol $Protocol -BaseUrl $BaseUrl -AnnouncementId $announcementId
    }
    
    Write-Host "`n" + "-"*60 -ForegroundColor Cyan
    Write-Host "$Protocol Test Summary:" -ForegroundColor Cyan
    Write-Host "  Passed: $($TestResults[$Protocol].Passed)" -ForegroundColor Green
    Write-Host "  Failed: $($TestResults[$Protocol].Failed)" -ForegroundColor Red
    Write-Host "-"*60 -ForegroundColor Cyan
}

# Main execution
Write-Host "RVNKCore Announcements API Comprehensive Test Suite" -ForegroundColor Magenta
Write-Host "====================================================" -ForegroundColor Magenta

if ($IgnoreSSLErrors) {
    Write-Host "SSL certificate validation disabled for testing" -ForegroundColor Yellow
}

if ($CreateTestData) {
    Write-Host "Test data creation enabled" -ForegroundColor Yellow
}

# Run tests for HTTP, HTTPS, or both depending on parameters
if ($HttpOnly -and -not $HttpsOnly) {
    Run-AllTests -Protocol "HTTP" -BaseUrl $HttpUrl
} elseif ($HttpsOnly -and -not $HttpOnly) {
    Run-AllTests -Protocol "HTTPS" -BaseUrl $HttpsUrl
} else {
    Run-AllTests -Protocol "HTTP" -BaseUrl $HttpUrl
    Run-AllTests -Protocol "HTTPS" -BaseUrl $HttpsUrl
}

# Print comprehensive summary
Write-Host "`n" + "="*70 -ForegroundColor Cyan
Write-Host "COMPREHENSIVE TEST SUMMARY" -ForegroundColor Cyan
Write-Host "="*70 -ForegroundColor Cyan

$totalPassed = 0
$totalFailed = 0

foreach ($protocol in $TestResults.Keys) {
    if ($TestResults[$protocol].Passed -gt 0 -or $TestResults[$protocol].Failed -gt 0) {
        Write-Host "`n$protocol Results:" -ForegroundColor White
        Write-Host "  Passed: $($TestResults[$protocol].Passed)" -ForegroundColor Green
        Write-Host "  Failed: $($TestResults[$protocol].Failed)" -ForegroundColor Red
        $totalPassed += $TestResults[$protocol].Passed
        $totalFailed += $TestResults[$protocol].Failed
    }
}

Write-Host "`nOverall Results:" -ForegroundColor Cyan
Write-Host "Total Passed: $totalPassed" -ForegroundColor Green
Write-Host "Total Failed: $totalFailed" -ForegroundColor Red

# Show errors if any
$hasErrors = $false
foreach ($protocol in $TestResults.Keys) {
    if ($TestResults[$protocol].Errors.Count -gt 0) {
        if (-not $hasErrors) {
            Write-Host "`nERRORS ENCOUNTERED:" -ForegroundColor Red
            $hasErrors = $true
        }
        Write-Host "`n$protocol Errors:" -ForegroundColor Red
        foreach ($er in $TestResults[$protocol].Errors) {
            Write-Host "  • $er" -ForegroundColor Red
        }
    }
}

if ($totalFailed -gt 0) {
    Write-Host "`nSome tests failed. Check the errors above for details." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "`nAll tests passed successfully!" -ForegroundColor Green
    exit 0
}
