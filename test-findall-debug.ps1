# Test script to debug findAll endpoint issue
param(
    [string]$BaseUrl = "https://localhost:8081/api/v1",
    [string]$ApiKey = "dev-key-2023",
    [switch]$IgnoreSSL
)

Write-Host "=== Debug FindAll API Call ===" -ForegroundColor Yellow
Write-Host "URL: $BaseUrl/announcements" -ForegroundColor Gray
Write-Host "API Key: $ApiKey" -ForegroundColor Gray
Write-Host ""

if ($IgnoreSSL) {
    # Disable SSL certificate validation
    add-type @"
        using System.Net;
        using System.Security.Cryptography.X509Certificates;
        public class TrustAllCertsPolicy : ICertificatePolicy {
            public bool CheckValidationResult(ServicePoint srvPoint, X509Certificate certificate, WebRequest request, int certificateProblem) {
                return true;
            }
        }
"@
    [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
}

try {
    # Test GET all announcements
    Write-Host "Testing GET all announcements..." -ForegroundColor Cyan
    $headers = @{
        "X-API-Key" = $ApiKey
        "Content-Type" = "application/json"
    }
    
    $response = Invoke-RestMethod -Uri "$BaseUrl/announcements" -Method GET -Headers $headers -ErrorAction Stop
    
    Write-Host "SUCCESS: API call completed" -ForegroundColor Green
    Write-Host "Response Type: $($response.GetType().Name)" -ForegroundColor Gray
    
    if ($response -is [System.Object[]]) {
        Write-Host "Response is an array with $($response.Count) items" -ForegroundColor Gray
        
        if ($response.Count -eq 0) {
            Write-Host "ISSUE: Array is empty despite database containing data" -ForegroundColor Red
        } else {
            Write-Host "SUCCESS: Found $($response.Count) announcements" -ForegroundColor Green
            for ($i = 0; $i -lt [Math]::Min(3, $response.Count); $i++) {
                $announcement = $response[$i]
                Write-Host "  [$i] ID: $($announcement.id)" -ForegroundColor Gray
                Write-Host "      Title: $($announcement.title)" -ForegroundColor Gray
                Write-Host "      Active: $($announcement.active)" -ForegroundColor Gray
            }
        }
    } else {
        Write-Host "Response structure:" -ForegroundColor Gray
        Write-Host ($response | ConvertTo-Json -Depth 2) -ForegroundColor Gray
    }
    
    # Also test individual retrieval to compare
    Write-Host ""
    Write-Host "Testing individual retrieval for comparison..." -ForegroundColor Cyan
    
    # Try to get a specific announcement we know exists
    $testIds = @("ann_1d00f656", "ann_85df1962", "ann_3ba01e46", "ann_de7faf1f", "ann_249ef700")
    
    foreach ($testId in $testIds) {
        try {
            $individual = Invoke-RestMethod -Uri "$BaseUrl/announcements/$testId" -Method GET -Headers $headers -ErrorAction Stop
            Write-Host "SUCCESS: Retrieved individual announcement $testId" -ForegroundColor Green
            Write-Host "  Title: $($individual.title)" -ForegroundColor Gray
            break
        } catch {
            Write-Host "Could not retrieve $testId (may not exist)" -ForegroundColor Yellow
        }
    }
    
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode
        Write-Host "Status Code: $statusCode" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "=== End Debug Test ===" -ForegroundColor Yellow
