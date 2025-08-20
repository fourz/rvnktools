# RVNKCoreServer API Testing with Invoke-WebRequest Examples
# Simple examples using iwr (Invoke-WebRequest alias)

# Configuration
$BaseUrl = "http://localhost:8080"
$HttpsUrl = "https://localhost:8081"
$ApiKey = "9067FFAetF34576893"
$ContextPath = "/api"

# Headers for authentication
$Headers = @{
    "X-API-Key" = $ApiKey
    "Content-Type" = "application/json"
    "Accept" = "application/json"
}

Write-Host "RVNKCoreServer API Testing with Invoke-WebRequest" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# Basic connection test
Write-Host "`n1. Basic Connection Test" -ForegroundColor Yellow
try {
    $response = iwr "$BaseUrl$ContextPath/v1/players" -Headers $Headers
    Write-Host "✓ Connection successful - Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "Response Length: $($response.Content.Length) bytes"
} catch {
    Write-Host "✗ Connection failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test authentication with invalid key
Write-Host "`n2. Authentication Test (Invalid Key)" -ForegroundColor Yellow
try {
    $badHeaders = @{ "X-API-Key" = "invalid-key" }
    $response = iwr "$BaseUrl$ContextPath/v1/players" -Headers $badHeaders
    Write-Host "✗ Authentication test failed - should have been rejected" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "✓ Authentication working - correctly rejected invalid key" -ForegroundColor Green
    } else {
        Write-Host "✗ Unexpected error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Get all players
Write-Host "`n3. GET All Players" -ForegroundColor Yellow
try {
    $response = iwr "$BaseUrl$ContextPath/v1/players" -Headers $Headers
    Write-Host "✓ Players endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
    $players = $response.Content | ConvertFrom-Json
    Write-Host "Players found: $($players.Count)"
} catch {
    Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Get online players
Write-Host "`n4. GET Online Players" -ForegroundColor Yellow
try {
    $response = iwr "$BaseUrl$ContextPath/v1/players/online" -Headers $Headers
    Write-Host "✓ Online players endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
    $onlinePlayers = $response.Content | ConvertFrom-Json
    Write-Host "Online players: $($onlinePlayers.Count)"
} catch {
    Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Get player count
Write-Host "`n5. GET Player Count" -ForegroundColor Yellow
try {
    $response = iwr "$BaseUrl$ContextPath/v1/players/count" -Headers $Headers
    Write-Host "✓ Player count endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
    $count = $response.Content | ConvertFrom-Json
    Write-Host "Total player count: $($count.count)"
} catch {
    Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Search players
Write-Host "`n6. GET Search Players" -ForegroundColor Yellow
try {
    $response = iwr "$BaseUrl$ContextPath/v1/players/search?name=test" -Headers $Headers
    Write-Host "✓ Search endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
    $searchResults = $response.Content | ConvertFrom-Json
    Write-Host "Search results: $($searchResults.Count)"
} catch {
    Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Get player by UUID (example UUID)
Write-Host "`n7. GET Player by UUID" -ForegroundColor Yellow
try {
    $testUuid = "94c37976-5134-40b0-9e03-722ae6664fea"
    $response = iwr "$BaseUrl$ContextPath/v1/players/$testUuid" -Headers $Headers
    Write-Host "✓ Player by UUID endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
    $player = $response.Content | ConvertFrom-Json
    Write-Host "Player name: $($player.name)"
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host "○ Player not found (404) - this is expected if player doesn't exist" -ForegroundColor Yellow
    } else {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Get player by name
Write-Host "`n8. GET Player by Name" -ForegroundColor Yellow
try {
    $testName = "wizardofire"
    $response = iwr "$BaseUrl$ContextPath/v1/player/name/$testName" -Headers $Headers
    Write-Host "✓ Player by name endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
    $player = $response.Content | ConvertFrom-Json
    Write-Host "Player UUID: $($player.uuid)"
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host "○ Player not found (404) - this is expected if player doesn't exist" -ForegroundColor Yellow
    } else {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Get player name history
Write-Host "`n8b. GET Player Name History" -ForegroundColor Yellow
try {
    $testName = "wizardofire"
    $response = iwr "$BaseUrl$ContextPath/v1/player/name/$testName/history" -Headers $Headers
    Write-Host "✓ Player name history endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
    $history = $response.Content | ConvertFrom-Json
    Write-Host "Current name: $($history.currentName)"
    Write-Host "Name history count: $($history.nameHistory.Count)"
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host "○ Player not found (404) - this is expected if player doesn't exist" -ForegroundColor Yellow
    } else {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Get players by group
Write-Host "`n9. GET Players by Group" -ForegroundColor Yellow
try {
    $testGroup = "default"
    $response = iwr "$BaseUrl$ContextPath/v1/players/group/$testGroup" -Headers $Headers
    Write-Host "✓ Players by group endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
    $groupPlayers = $response.Content | ConvertFrom-Json
    Write-Host "Players in group '$testGroup': $($groupPlayers.Count)"
} catch {
    Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# PUT - Update player location
Write-Host "`n10. PUT Update Player Location" -ForegroundColor Yellow
try {
    $testUuid = "94c37976-5134-40b0-9e03-722ae6664fea"
    $locationData = @{
        world = "world"
        x = 100.5
        y = 64.0
        z = -200.3
    } | ConvertTo-Json
    
    $response = iwr "$BaseUrl$ContextPath/v1/players/$testUuid/location" -Method PUT -Headers $Headers -Body $locationData
    Write-Host "✓ Update location endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host "○ Player not found (404) - this is expected if player doesn't exist" -ForegroundColor Yellow
    } else {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# PUT - Update player groups
Write-Host "`n11. PUT Update Player Groups" -ForegroundColor Yellow
try {
    $testUuid = "94c37976-5134-40b0-9e03-722ae6664fea"
    $groupData = @{
        action = "set"
        groups = @("default", "vip")
    } | ConvertTo-Json
    
    $response = iwr "$BaseUrl$ContextPath/v1/players/$testUuid/groups" -Method PUT -Headers $Headers -Body $groupData
    Write-Host "✓ Update groups endpoint - Status: $($response.StatusCode)" -ForegroundColor Green
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host "○ Player not found (404) - this is expected if player doesn't exist" -ForegroundColor Yellow
    } else {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# HTTPS Test (if enabled)
Write-Host "`n12. HTTPS Connection Test" -ForegroundColor Yellow
try {
    # Bypass SSL certificate validation for self-signed certificates
    [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
    
    $response = iwr "$HttpsUrl$ContextPath/v1/players" -Headers $Headers
    Write-Host "✓ HTTPS connection successful - Status: $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "○ HTTPS not available or failed: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "`n=== Simple One-Liner Examples ===" -ForegroundColor Cyan

Write-Host "`n# Basic connection test:"
Write-Host 'iwr http://localhost:8080/api/v1/players -Headers @{"X-API-Key"="test-api-key"}' -ForegroundColor White

Write-Host "`n# Get online players:"
Write-Host 'iwr http://localhost:8080/api/v1/players/online -Headers @{"X-API-Key"="test-api-key"}' -ForegroundColor White

Write-Host "`n# Get player count:"
Write-Host 'iwr http://localhost:8080/api/v1/players/count -Headers @{"X-API-Key"="test-api-key"}' -ForegroundColor White

Write-Host "`n# Search players:"
Write-Host 'iwr "http://localhost:8080/api/v1/players/search?name=test" -Headers @{"X-API-Key"="test-api-key"}' -ForegroundColor White

Write-Host "`n# Update player location (PUT):"
Write-Host '$body = @{world="world"; x=100; y=64; z=-200} | ConvertTo-Json' -ForegroundColor White
Write-Host 'iwr http://localhost:8080/api/v1/players/YOUR-UUID/location -Method PUT -Headers @{"X-API-Key"="test-api-key";"Content-Type"="application/json"} -Body $body' -ForegroundColor White

Write-Host "`n=== Testing Complete ===" -ForegroundColor Cyan
