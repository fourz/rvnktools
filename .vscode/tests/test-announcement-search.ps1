# Test announcement search functionality
$apiKey = "9067FFAetF34576893"
$baseUrl = "https://localhost:8081"

# Skip SSL certificate validation
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

Write-Host "=== Announcement API Search Test ==="

try {
    # Test 1: Search for announcements containing "test"
    Write-Host "`n1. Searching for announcements containing 'test'..."
    $searchBody = @{
        query = "test"
        type = "BROADCAST"
        isActive = $true
    } | ConvertTo-Json
    
    $searchResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/search" -Method POST -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
        'Content-Type' = 'application/json'
    } -Body $searchBody
    
    Write-Host "Search results: $($searchResponse.announcements.Count) announcements found"
    if ($searchResponse.announcements.Count -gt 0) {
        $searchResponse.announcements | Select-Object -First 3 id, message, type | Format-Table
    }
    
    # Test 2: Get all announcements to see what test data exists
    Write-Host "`n2. Getting all announcements..."
    $allResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements" -Method GET -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
    }
    
    Write-Host "Total announcements: $($allResponse.announcements.Count)"
    if ($allResponse.announcements.Count -gt 0) {
        Write-Host "Sample announcements:"
        $allResponse.announcements | Select-Object -First 5 id, title, message, type, active | Format-Table -AutoSize
    }
    
    # Test 3: Get announcements by type
    Write-Host "`n3. Getting BROADCAST type announcements..."
    $typeResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/type/BROADCAST" -Method GET -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
    }
    
    Write-Host "BROADCAST announcements: $($typeResponse.announcements.Count)"
    
    # Test 4: Get announcement metrics
    Write-Host "`n4. Getting announcement metrics..."
    $metricsResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/metrics" -Method GET -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
    }
    
    Write-Host "Metrics: Active=$($metricsResponse.active_count), Total=$($metricsResponse.total_count)"
    
    Write-Host "`n=== Test completed successfully ==="
    
} catch {
    Write-Host "Error during testing: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Response: $($_.Exception.Response)" -ForegroundColor Red
}
