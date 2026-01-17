# Test async timing issues with API calls
param(
    [string]$BaseUrl = "https://localhost:8081/api/v1",
    [string]$ApiKey = "9067FFAetF34576893",
    [switch]$IgnoreSSL
)

Write-Host "=== Testing Async Timing Issues ===" -ForegroundColor Yellow

if ($IgnoreSSL) {
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

$headers = @{
    "X-API-Key" = $ApiKey
    "Content-Type" = "application/json"
}

Write-Host ""
Write-Host "=== Test 1: Multiple Individual Calls ===" -ForegroundColor Cyan
$testIds = @("ann_1d00f656", "ann_85df1962", "ann_3ba01e46", "ann_de7faf1f", "ann_249ef700")

$individualResults = @()
foreach ($testId in $testIds) {
    try {
        $startTime = Get-Date
        $individual = Invoke-RestMethod -Uri "$BaseUrl/announcements/$testId" -Method GET -Headers $headers -ErrorAction Stop
        $endTime = Get-Date
        $duration = ($endTime - $startTime).TotalMilliseconds
        
        $individualResults += [PSCustomObject]@{
            ID = $testId
            Success = $true
            Duration = $duration
            HasTitle = -not [string]::IsNullOrEmpty($individual.title)
        }
        Write-Host "✓ $testId - ${duration}ms - Title: $($individual.title)" -ForegroundColor Green
    } catch {
        $individualResults += [PSCustomObject]@{
            ID = $testId
            Success = $false
            Duration = 0
            Error = $_.Exception.Message
        }
        Write-Host "✗ $testId - ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 100  # Brief delay between requests
}

Write-Host ""
Write-Host "=== Test 2: Multiple List Calls ===" -ForegroundColor Cyan
$listResults = @()

for ($i = 1; $i -le 5; $i++) {
    try {
        $startTime = Get-Date
        $listResponse = Invoke-RestMethod -Uri "$BaseUrl/announcements" -Method GET -Headers $headers -ErrorAction Stop
        $endTime = Get-Date
        $duration = ($endTime - $startTime).TotalMilliseconds
        
        $responseType = $listResponse.GetType().Name
        $responseLength = if ($listResponse -is [string]) { $listResponse.Length } else { "N/A" }
        
        $listResults += [PSCustomObject]@{
            Attempt = $i
            Success = $true
            Duration = $duration
            ResponseType = $responseType
            ResponseLength = $responseLength
            IsEmpty = ($listResponse -is [string] -and $listResponse.Length -eq 0)
        }
        
        Write-Host "✓ Attempt $i - ${duration}ms - Type: $responseType - Length: $responseLength" -ForegroundColor Yellow
    } catch {
        $listResults += [PSCustomObject]@{
            Attempt = $i
            Success = $false
            Duration = 0
            Error = $_.Exception.Message
        }
        Write-Host "✗ Attempt $i - ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 200  # Brief delay between requests
}

Write-Host ""
Write-Host "=== Results Analysis ===" -ForegroundColor Yellow
Write-Host "Individual calls successful: $($individualResults | Where-Object { $_.Success }).Count / $($individualResults.Count)" -ForegroundColor Gray
Write-Host "List calls successful: $($listResults | Where-Object { $_.Success }).Count / $($listResults.Count)" -ForegroundColor Gray
Write-Host "Individual avg duration: $([math]::Round(($individualResults | Where-Object { $_.Success } | Measure-Object -Property Duration -Average).Average, 2))ms" -ForegroundColor Gray
Write-Host "List avg duration: $([math]::Round(($listResults | Where-Object { $_.Success } | Measure-Object -Property Duration -Average).Average, 2))ms" -ForegroundColor Gray
Write-Host "All list responses empty: $(($listResults | Where-Object { $_.Success -and $_.IsEmpty }).Count -eq ($listResults | Where-Object { $_.Success }).Count)" -ForegroundColor Gray

Write-Host ""
Write-Host "=== End Timing Test ===" -ForegroundColor Yellow
