# Quick HTTPS Test for RVNKCore API
# Automatically handles self-signed certificate issues

param (
    [Parameter(Mandatory = $true)]
    [string]$ApiKey,
    [Parameter(Mandatory = $false)]
    [string]$BaseUrl = "https://localhost:8081"
)

# Configure TLS and certificate bypass for self-signed certificates
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Write-Host "Testing RVNKCore HTTPS API..." -ForegroundColor Cyan
Write-Host "URL: $BaseUrl" -ForegroundColor Gray
Write-Host "API Key: $ApiKey" -ForegroundColor Gray

try {
    $headers = @{
        "X-API-Key" = $ApiKey
        "Accept" = "application/json"
    }
    
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players" -Headers $headers -SkipCertificateCheck
    
    Write-Host "`nSUCCESS! ✓" -ForegroundColor Green
    Write-Host "Status: API is responding correctly" -ForegroundColor Green
    
    if ($response.success) {
        Write-Host "Response: $($response.data.Count) players found" -ForegroundColor Green
    } else {
        Write-Host "Response: $($response | ConvertTo-Json -Depth 2)" -ForegroundColor Yellow
    }
    
} catch {
    Write-Host "`nFAILED! ✗" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    }
    
    Write-Host "`nTroubleshooting:" -ForegroundColor Yellow
    Write-Host "1. Check if the server is running" -ForegroundColor Gray
    Write-Host "2. Verify the API key is correct" -ForegroundColor Gray
    Write-Host "3. Ensure port 8081 is accessible" -ForegroundColor Gray
    
    exit 1
}


Write-Host "Note: Using -SkipCertificateCheck for self-signed certificate" -ForegroundColor DarkGray
