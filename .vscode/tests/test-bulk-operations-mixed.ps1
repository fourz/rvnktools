#!/usr/bin/env pwsh
# Test script for bulk operations with mixed valid/invalid IDs

$ErrorActionPreference = "Continue"

# API Configuration
$baseUrl = "https://localhost:8081"
$headers = @{
    'X-API-Key' = '9067FFAetF34576893'
    'Content-Type' = 'application/json'
}

Write-Host "=== BULK OPERATIONS TEST WITH MIXED IDs ===" -ForegroundColor Cyan
Write-Host "Testing both valid and invalid announcement IDs" -ForegroundColor Yellow

# Test data: mix of valid and invalid IDs
$testIds = @(
    "ann_001",     # Should exist
    "ann_002",     # Should exist  
    "invalid_001", # Should NOT exist
    "ann_003",     # Should exist
    "invalid_002"  # Should NOT exist
)

Write-Host "`nTest IDs: $($testIds -join ', ')" -ForegroundColor Gray

# Test 1: Bulk Activation with Mixed IDs
Write-Host "`n--- TEST 1: BULK ACTIVATION (Mixed IDs) ---" -ForegroundColor Green
$activatePayload = @{
    ids = $testIds
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/bulk/activate" `
        -Method PUT `
        -Headers $headers `
        -Body $activatePayload `
        -SkipCertificateCheck

    Write-Host "SUCCESS: Bulk activation completed" -ForegroundColor Green
    Write-Host "Total: $($response.total)" -ForegroundColor Gray
    Write-Host "Successful: $($response.successful)" -ForegroundColor Green
    Write-Host "Failed: $($response.failed)" -ForegroundColor Red
    
    Write-Host "`nDetailed Results:" -ForegroundColor Yellow
    foreach ($result in $response.results) {
        if ($result.success) {
            Write-Host "  ✓ $($result.id): SUCCESS" -ForegroundColor Green
        } else {
            Write-Host "  ✗ $($result.id): FAILED - $($result.error)" -ForegroundColor Red
        }
    }
} catch {
    Write-Host "FAILED: Bulk activation error" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        Write-Host "Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    }
}

Start-Sleep -Seconds 2

# Test 2: Bulk Deactivation with Mixed IDs
Write-Host "`n--- TEST 2: BULK DEACTIVATION (Mixed IDs) ---" -ForegroundColor Green
$deactivatePayload = @{
    ids = $testIds
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/v1/announcements/bulk/deactivate" `
        -Method PUT `
        -Headers $headers `
        -Body $deactivatePayload `
        -SkipCertificateCheck

    Write-Host "SUCCESS: Bulk deactivation completed" -ForegroundColor Green
    Write-Host "Total: $($response.total)" -ForegroundColor Gray
    Write-Host "Successful: $($response.successful)" -ForegroundColor Green
    Write-Host "Failed: $($response.failed)" -ForegroundColor Red
    
    Write-Host "`nDetailed Results:" -ForegroundColor Yellow
    foreach ($result in $response.results) {
        if ($result.success) {
            Write-Host "  ✓ $($result.id): SUCCESS" -ForegroundColor Green
        } else {
            Write-Host "  ✗ $($result.id): FAILED - $($result.error)" -ForegroundColor Red
        }
    }
} catch {
    Write-Host "FAILED: Bulk deactivation error" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        Write-Host "Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    }
}

Write-Host "`n=== BULK OPERATIONS TEST COMPLETE ===" -ForegroundColor Cyan
