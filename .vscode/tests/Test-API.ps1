add-type @"
    using System.Net;
    using System.Security.Cryptography.X509Certificates;
    public class TrustAllCertsPolicy : ICertificatePolicy {
        public bool CheckValidationResult(
            ServicePoint srvPoint, X509Certificate certificate,
            WebRequest request, int certificateProblem) {
            return true;
        }
    }
"@
[System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy

# Load configuration from project.json using unified approach
$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

try {
    $config = Get-Content -Path "project.json" | ConvertFrom-Json
    
    # Use RVNKCore API configuration from project.json
    $apiKey = $config.rvnkcore.apiKey
    $baseUrl = $config.rvnkcore.httpsBaseUrl + "/api"
    
    if (-not $apiKey -or -not $baseUrl) {
        throw "RVNKCore API configuration missing from project.json"
    }
    
    Write-Host "Using API configuration from project.json" -ForegroundColor Green
    Write-Host "Base URL: $baseUrl" -ForegroundColor Gray
    Write-Host "API Key: $($apiKey.Substring(0,4))..." -ForegroundColor Gray
} catch {
    Write-Host "Failed to load configuration from project.json: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Falling back to hardcoded values..." -ForegroundColor Yellow
    
    # Fallback configuration
    $apiKey = "9827-Fzpo-jUDH-sddd-fg3h"
    $baseUrl = "https://localhost:8443/api"
}

$headers = @{
    "X-API-Key" = $apiKey
    "Content-Type" = "application/json"
}

# Test Functions
function Test-ApiStatus {
    Write-Host "Testing API Status..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/status" -Headers $headers -Method Get
    Write-Host "Status: $response" -ForegroundColor Green
}

function Test-CreateAnnouncement {
    param($id)
    Write-Host "Testing Create Announcement..." -ForegroundColor Cyan
    $body = @{
        id = $id
        type = "advert"
        message = "Test announcement message"
        announcement_location = "chat"  # New property
        announcement_sound = "none"     # New property
    } | ConvertTo-Json
    
    $response = Invoke-RestMethod -Uri "$baseUrl/announcements" -Headers $headers -Method Post -Body $body
    Write-Host "Created announcement: $($response.id)" -ForegroundColor Green
}

function Test-GetAnnouncement {
    param($id)
    Write-Host "Testing Get Announcement..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/announcements/$id" -Headers $headers -Method Get
    Write-Host "Retrieved announcement: $($response.message)" -ForegroundColor Green
}

function Test-DeleteAnnouncement {
    param($id)
    Write-Host "Testing Delete Announcement..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/announcements/$id" -Headers $headers -Method Delete
    Write-Host "Deleted announcement: $id" -ForegroundColor Green
}

function Test-GetPlayersToday {
    Write-Host "Testing Get Players Today..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/players/today" -Headers $headers -Method Get
    Write-Host "Players active today: $($response | ConvertTo-Json)" -ForegroundColor Green
}

function Test-GetPlayersLast30Days {
    Write-Host "Testing Get Players Last 30 Days..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/players/recent?days=30" -Headers $headers -Method Get
    Write-Host "Players active in last 30 days: $($response | ConvertTo-Json)" -ForegroundColor Green
}


function Test-GetPlayerByUUID {
    param($uuid)
    Write-Host "Testing Get Player by UUID..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/players/uuid/$uuid" -Headers $headers -Method Get
    Write-Host "Retrieved player: $($response.name)" -ForegroundColor Green
}

function Test-GetPlayerByName {
    param($name)
    Write-Host "Testing Get Player by Name..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/players/name/$name" -Headers $headers -Method Get
    Write-Host "Retrieved player: $($response.uuid)" -ForegroundColor Green
}

function Test-ListWorlds {
    Write-Host "Testing List Worlds..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/worlds" -Headers $headers -Method Get
    Write-Host "Worlds: $($response | ConvertTo-Json)" -ForegroundColor Green
}

function Test-GetWorldDetails {
    param($worldName)
    Write-Host "Testing Get World Details..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/worlds/$worldName" -Headers $headers -Method Get
    Write-Host "World Details: $($response | ConvertTo-Json)" -ForegroundColor Green
}

function Test-GetWorldBoundaries {
    param($worldName)
    Write-Host "Testing Get World Boundaries..." -ForegroundColor Cyan
    $response = Invoke-RestMethod -Uri "$baseUrl/worlds/$worldName/boundaries" -Headers $headers -Method Get
    Write-Host "World Boundaries: $($response | ConvertTo-Json)" -ForegroundColor Green
}


# Run Tests
try {
    Test-ApiStatus
    Test-CreateAnnouncement -id "test-announce75"
    Test-GetAnnouncement -id "test-announce75"
    Test-DeleteAnnouncement -id "test-announce75"
    Test-GetPlayerByUUID -uuid "94c37976-5134-40b0-9e03-722ae6664fea"
    Test-GetPlayerByName -name "wizardofire"
    Test-GetPlayersToday
    Test-GetPlayersLast30Days
    Test-ListWorlds
    Test-GetWorldDetails -worldName "southmesa"
    Test-GetWorldBoundaries -worldName "southmesa"
    Write-Host "All tests completed successfully!" -ForegroundColor Green
} catch {
    Write-Host "Error occurred: $_" -ForegroundColor Red
}