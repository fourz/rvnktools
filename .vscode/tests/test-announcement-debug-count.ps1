# Test announcement count vs findAll
$apiKey = "9067FFAetF34576893"
$baseUrl = "https://localhost:8081"

# Skip SSL certificate validation
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

Write-Host "=== Announcement Count vs FindAll Debug Test ==="

try {
    # Test 1: Get count
    Write-Host "`n1. Getting announcement count..."
    $countResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/count" -Method GET -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
    }
    
    Write-Host "Count result: $($countResponse | ConvertTo-Json)"
    
    # Test 2: Create a new announcement to make sure we have data
    Write-Host "`n2. Creating a test announcement..."
    $createBody = @{
        title = "Debug Test"
        message = "Debug test announcement - $(Get-Date)"
        type = "BROADCAST"
        isActive = $true
    } | ConvertTo-Json -Depth 3
    
    $createResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements" -Method POST -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
        'Content-Type' = 'application/json'
    } -Body $createBody
    
    Write-Host "Created announcement: $($createResponse.id)" -ForegroundColor Green
    
    # Test 3: Get count again
    Write-Host "`n3. Getting updated announcement count..."
    $countResponse2 = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/count" -Method GET -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
    }
    
    Write-Host "Updated count: $($countResponse2 | ConvertTo-Json)"
    
    # Test 4: Try findAll again
    Write-Host "`n4. Getting all announcements (should now show results)..."
    $allResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements" -Method GET -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
    }
    
    Write-Host "FindAll result count: $($allResponse.announcements.Count)" -ForegroundColor Yellow
    Write-Host "FindAll result: $($allResponse | ConvertTo-Json -Depth 2)"
    
    # Test 5: Try to get the specific announcement we just created
    Write-Host "`n5. Getting the specific announcement by ID..."
    $getResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/$($createResponse.id)" -Method GET -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
    }
    
    Write-Host "Get by ID works: $($getResponse.id)" -ForegroundColor Green
    
    Write-Host "`n=== Test completed ==="
    
} catch {
    Write-Host "❌ Error during testing: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails) {
        Write-Host "Error details: $($_.ErrorDetails.Message)" -ForegroundColor Red
    }
}
