# Test bulk activate with detailed logging
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
if (-not ('TrustAllCertsPolicy' -as [type])) {
    add-type @'
        using System.Net;
        using System.Security.Cryptography.X509Certificates;
        public class TrustAllCertsPolicy : ICertificatePolicy {
            public bool CheckValidationResult(ServicePoint sPoint, X509Certificate cert, WebRequest wRequest, int certProblem) {
                return true;
            }
        }
'@
    [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
}

# Create fresh announcements first
$bulkData = @(
    @{
        message = 'Debug bulk activate 1'
        type = 'BROADCAST'
        priority = 1
        world = 'world'
        isActive = $true
    },
    @{
        message = 'Debug bulk activate 2'
        type = 'INFO'
        priority = 2
        world = 'world'
        isActive = $true
    }
)

$headers = @{
    'X-API-Key' = '9067FFAetF34576893'
    'Accept' = 'application/json'
    'Content-Type' = 'application/json'
}

Write-Host 'Step 1: Creating announcements'
try {
    $body = $bulkData | ConvertTo-Json -Depth 3
    $createResponse = Invoke-RestMethod -Uri 'https://localhost:8081/api/v1/announcements/bulk' -Method 'POST' -Headers $headers -Body $body
    Write-Host 'Created announcements:'
    $createResponse.announcements | ForEach-Object { 
        Write-Host "- $($_.id): $($_.message)" 
    }
    
    if ($createResponse.announcements -and $createResponse.announcements.Count -gt 0) {
        $ids = $createResponse.announcements | ForEach-Object { $_.id }
        
        Write-Host ''
        Write-Host "Step 2: Bulk activating with IDs: $($ids -join ', ')"
        $activateData = @{ ids = $ids }
        $activateBody = $activateData | ConvertTo-Json -Depth 3
        Write-Host "Activate request body: $activateBody"
        
        $activateResponse = Invoke-RestMethod -Uri 'https://localhost:8081/api/v1/announcements/bulk/activate' -Method 'PUT' -Headers $headers -Body $activateBody
        Write-Host "Activate response: $activateResponse"
    }
} catch {
    Write-Host "Error: $($_.Exception.Message)"
}
