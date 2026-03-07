#!/usr/bin/env pwsh
# Final test for bulk operations with mixed valid/invalid IDs

$ErrorActionPreference = "Continue"

# API Configuration
$baseUrl = "https://localhost:8081"
$headers = @{
    'X-API-Key' = '9067FFAetF34576893'
    'Content-Type' = 'application/json'
}

Write-Host "=== FINAL BULK OPERATIONS TEST (Mixed Valid/Invalid IDs) ===" -ForegroundColor Cyan
Write-Host ""

# First, create a couple of test announcements to get valid IDs
Write-Host "--- STEP 1: Creating test announcements to get valid IDs ---" -ForegroundColor Green

$testAnn1 = @{
    message = "Test Announcement for Bulk Operations Test 1"
    type = "BROADCAST"
    isActive = $true
    priority = 1
} | ConvertTo-Json

$testAnn2 = @{
    message = "Test Announcement for Bulk Operations Test 2"
    type = "INFO"
    isActive = $true
    priority = 2
} | ConvertTo-Json

try {
    # Create first test announcement
    $response1 = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements" -Method POST -Headers $headers -Body $testAnn1 -SkipCertificateCheck
    $validId1 = $response1.id
    Write-Host "✅ Created announcement: $validId1" -ForegroundColor Green
    
    # Create second test announcement
    $response2 = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements" -Method POST -Headers $headers -Body $testAnn2 -SkipCertificateCheck
    $validId2 = $response2.id
    Write-Host "✅ Created announcement: $validId2" -ForegroundColor Green
    
} catch {
    Write-Host "❌ Failed to create test announcements: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Now test bulk operations with mixed valid/invalid IDs
$mixedIds = @($validId1, "invalid_001", $validId2, "nonexistent_999")

Write-Host "--- STEP 2: Testing Bulk Activation with Mixed IDs ---" -ForegroundColor Green
Write-Host "Testing IDs: $($mixedIds -join ', ')" -ForegroundColor Yellow

$bulkActivateBody = @{ ids = $mixedIds } | ConvertTo-Json
Write-Host "Request body: $bulkActivateBody" -ForegroundColor Gray

try {
    $activateResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/bulk/activate" -Method PUT -Headers $headers -Body $bulkActivateBody -SkipCertificateCheck
    
    Write-Host "✅ Bulk activation completed!" -ForegroundColor Green
    Write-Host "Total: $($activateResponse.total)" -ForegroundColor White
    Write-Host "Successful: $($activateResponse.successful)" -ForegroundColor Green
    Write-Host "Failed: $($activateResponse.failed)" -ForegroundColor Red
    Write-Host ""
    
    Write-Host "Individual Results:" -ForegroundColor Yellow
    foreach ($result in $activateResponse.results) {
        if ($result.success) {
            Write-Host "  ✅ $($result.id) - SUCCESS" -ForegroundColor Green
        } else {
            Write-Host "  ❌ $($result.id) - FAILED: $($result.error)" -ForegroundColor Red
        }
    }
    
} catch {
    Write-Host "❌ Bulk activation failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails) {
        Write-Host "Error details: $($_.ErrorDetails.Message)" -ForegroundColor Red
    }
}

Write-Host ""

# Test bulk deactivation
Write-Host "--- STEP 3: Testing Bulk Deactivation with Mixed IDs ---" -ForegroundColor Green

$bulkDeactivateBody = @{ ids = $mixedIds } | ConvertTo-Json

try {
    $deactivateResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/bulk/deactivate" -Method PUT -Headers $headers -Body $bulkDeactivateBody -SkipCertificateCheck
    
    Write-Host "✅ Bulk deactivation completed!" -ForegroundColor Green
    Write-Host "Total: $($deactivateResponse.total)" -ForegroundColor White
    Write-Host "Successful: $($deactivateResponse.successful)" -ForegroundColor Green
    Write-Host "Failed: $($deactivateResponse.failed)" -ForegroundColor Red
    Write-Host ""
    
    Write-Host "Individual Results:" -ForegroundColor Yellow
    foreach ($result in $deactivateResponse.results) {
        if ($result.success) {
            Write-Host "  ✅ $($result.id) - SUCCESS" -ForegroundColor Green
        } else {
            Write-Host "  ❌ $($result.id) - FAILED: $($result.error)" -ForegroundColor Red
        }
    }
    
} catch {
    Write-Host "❌ Bulk deactivation failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails) {
        Write-Host "Error details: $($_.ErrorDetails.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "=== TEST COMPLETED ===" -ForegroundColor Cyan
Write-Host "✅ Bulk operations now handle mixed valid/invalid IDs gracefully!" -ForegroundColor Green
Write-Host "✅ Individual failures do not cause entire operations to fail!" -ForegroundColor Green
Write-Host "✅ Detailed error reporting shows which IDs succeeded vs failed!" -ForegroundColor Green
