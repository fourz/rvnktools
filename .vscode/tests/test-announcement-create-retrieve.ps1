# Test announcement creation and retrieval
$apiKey = "9067FFAetF34576893"
$baseUrl = "https://localhost:8081"

# Skip SSL certificate validation
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

Write-Host "=== Announcement API Create & Retrieve Test ==="

try {
    # Test 1: Create a new announcement
    Write-Host "`n1. Creating a new test announcement..."
    $createBody = @{
        title = "API Test Announcement"
        message = "This is a test announcement created via API at $(Get-Date)"
        type = "BROADCAST"
        isActive = $true
        priority = 1
        targetGroup = "players"
        world = "world"
        tags = @("api-test", "automated")
        metadata = @{
            author = "API-Test"
            category = "testing"
        }
    } | ConvertTo-Json -Depth 3
    
    Write-Host "Creating announcement with payload:" -ForegroundColor Yellow
    Write-Host $createBody -ForegroundColor Cyan
    
    $createResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements" -Method POST -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
        'Content-Type' = 'application/json'
    } -Body $createBody
    
    Write-Host "✅ Announcement created successfully!" -ForegroundColor Green
    Write-Host "Created ID: $($createResponse.id)" -ForegroundColor Green
    
    # Test 2: Try to retrieve the announcement we just created
    Write-Host "`n2. Attempting to retrieve the created announcement..."
    $getResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/$($createResponse.id)" -Method GET -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
    }
    
    Write-Host "✅ Retrieved announcement:" -ForegroundColor Green
    $getResponse | ConvertTo-Json -Depth 3
    
    # Test 3: Get all announcements again
    Write-Host "`n3. Getting all announcements to verify it appears in the list..."
    $allResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements" -Method GET -Headers @{
        'X-API-Key' = $apiKey
        'Accept' = 'application/json'
    }
    
    Write-Host "Total announcements now: $($allResponse.announcements.Count)" -ForegroundColor Yellow
    
    if ($allResponse.announcements.Count -gt 0) {
        Write-Host "Recent announcements:"
        $allResponse.announcements | Sort-Object created_at -Descending | Select-Object -First 5 id, title, message, type, active | Format-Table -AutoSize
    }
    
    Write-Host "`n=== Test completed successfully ==="
    
} catch {
    Write-Host "❌ Error during testing: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "Response body: $responseBody" -ForegroundColor Red
    }
}
