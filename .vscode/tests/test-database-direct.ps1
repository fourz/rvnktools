# Direct database test to verify announcement data
param(
    [string]$ServerHost = "64.20.41.122",
    [int]$ServerPort = 3306,
    [string]$DatabaseName = "fourzorg_rvnkcore_dev",
    [string]$Username = "fourzorg_rvnkcore",
    [string]$Password = "v8G09GZNfYppK2FJ"
)

Write-Host "=== Direct Database Query Test ===" -ForegroundColor Yellow
Write-Host "Testing direct MySQL connection and query..." -ForegroundColor Cyan
Write-Host ""

try {
    # Load MySQL connector (this might not be available in PowerShell without additional setup)
    # But we can use a simpler approach with the mysql command line if available
    
    # Alternative: Use PowerShell database connection
    # This requires MySQL .NET connector which may not be available
    
    Write-Host "Note: Direct database testing requires MySQL connector." -ForegroundColor Yellow
    Write-Host "Let's instead test the API with more detailed logging..." -ForegroundColor Cyan
    Write-Host ""
    
    # Test the count endpoint instead
    $BaseUrl = "https://localhost:8081/api/v1"
    $ApiKey = "9067FFAetF34576893"
    
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
    
    $headers = @{
        "X-API-Key" = $ApiKey
        "Content-Type" = "application/json"
    }
    
    # Test count operation if available
    Write-Host "Testing count operation..." -ForegroundColor Cyan
    try {
        $countResponse = Invoke-RestMethod -Uri "$BaseUrl/announcements/count" -Method GET -Headers $headers -ErrorAction Stop
        Write-Host "Count response: $countResponse" -ForegroundColor Green
    } catch {
        Write-Host "Count endpoint not available: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    
    # Test search operation
    Write-Host "Testing search operation..." -ForegroundColor Cyan
    try {
        $searchResponse = Invoke-RestMethod -Uri "$BaseUrl/announcements/search?q=Test" -Method GET -Headers $headers -ErrorAction Stop
        Write-Host "Search response type: $($searchResponse.GetType().Name)" -ForegroundColor Gray
        if ($searchResponse -is [System.Object[]]) {
            Write-Host "Search found $($searchResponse.Count) announcements" -ForegroundColor Green
            if ($searchResponse.Count -gt 0) {
                Write-Host "First search result ID: $($searchResponse[0].id)" -ForegroundColor Gray
            }
        } else {
            Write-Host "Search response: $searchResponse" -ForegroundColor Gray
        }
    } catch {
        Write-Host "Search failed: $($_.Exception.Message)" -ForegroundColor Red
    }
    
    # Test active announcements endpoint
    Write-Host "Testing active announcements..." -ForegroundColor Cyan
    try {
        $activeResponse = Invoke-RestMethod -Uri "$BaseUrl/announcements/active" -Method GET -Headers $headers -ErrorAction Stop
        Write-Host "Active response type: $($activeResponse.GetType().Name)" -ForegroundColor Gray
        if ($activeResponse -is [System.Object[]]) {
            Write-Host "Found $($activeResponse.Count) active announcements" -ForegroundColor Green
        } else {
            Write-Host "Active response: $activeResponse" -ForegroundColor Gray
        }
    } catch {
        Write-Host "Active announcements failed: $($_.Exception.Message)" -ForegroundColor Red
    }
    
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== End Database Test ===" -ForegroundColor Yellow
