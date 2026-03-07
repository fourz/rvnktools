# Simple Announcements API Test
# Test the basic functionality of the RVNKCore Announcements REST API

param(
    [string]$BaseUrl = "https://localhost:8081/api/v1",
    [string]$ApiKey = "rvnk-test-key-2024"
)

# Ignore self-signed SSL certificate warnings for testing
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Headers = @{
    "X-API-Key" = $ApiKey
    "Content-Type" = "application/json"
}

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "RVNKCore Announcements API Test Suite" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl" -ForegroundColor Yellow
Write-Host "API Key: $ApiKey" -ForegroundColor Yellow
Write-Host ""

function Test-Endpoint {
    param(
        [string]$Method,
        [string]$Endpoint,
        [string]$Description,
        [hashtable]$Body = $null
    )
    
    Write-Host "Testing: $Description" -ForegroundColor Green
    Write-Host "  $Method $Endpoint" -ForegroundColor Gray
    
    try {
        $params = @{
            Uri = "$BaseUrl$Endpoint"
            Method = $Method
            Headers = $Headers
        }
        
        if ($Body) {
            $params.Body = $Body | ConvertTo-Json
        }
        
        $response = Invoke-WebRequest @params
        Write-Host "  Status: $($response.StatusCode) $($response.StatusDescription)" -ForegroundColor Green
        
        if ($response.Content) {
            $json = $response.Content | ConvertFrom-Json -ErrorAction SilentlyContinue
            if ($json) {
                Write-Host "  Response: $($json | ConvertTo-Json -Compress)" -ForegroundColor Cyan
            } else {
                Write-Host "  Response: $($response.Content)" -ForegroundColor Cyan
            }
        }
        
        Write-Host ""
        return $true
        
    } catch {
        Write-Host "  ERROR: $($_.Exception.Message)" -ForegroundColor Red
        if ($_.Exception.Response) {
            Write-Host "  Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        }
        Write-Host ""
        return $false
    }
}

# Test 1: Get all announcements
Test-Endpoint -Method "GET" -Endpoint "/announcements" -Description "Get all announcements"

# Test 2: Get active announcements  
Test-Endpoint -Method "GET" -Endpoint "/announcements/active" -Description "Get active announcements"

# Test 3: Get announcement count
Test-Endpoint -Method "GET" -Endpoint "/announcements/count" -Description "Get announcement count"

# Test 4: Get active announcement count
Test-Endpoint -Method "GET" -Endpoint "/announcements/count/active" -Description "Get active announcement count"

# Test 5: Create a new announcement
$newAnnouncement = @{
    title = "Test Announcement"
    message = "This is a test announcement created by the API test script"
    type = "INFO"
    active = $true
}

$createSuccess = Test-Endpoint -Method "POST" -Endpoint "/announcements" -Description "Create new announcement" -Body $newAnnouncement

# Test 6: Search announcements
Test-Endpoint -Method "GET" -Endpoint "/announcements/search?q=test" -Description "Search announcements"

# Test 7: Get announcements by type
Test-Endpoint -Method "GET" -Endpoint "/announcements/type/INFO" -Description "Get announcements by type"

# Test 8: Test error handling with non-existent announcement
Test-Endpoint -Method "GET" -Endpoint "/announcements/non-existent-id" -Description "Test 404 error handling"

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Test Suite Complete" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
