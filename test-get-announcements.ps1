# Test announcements endpoint
Add-Type @'
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAllCertsPolicy : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint srvPoint, X509Certificate certificate, WebRequest request, int certificateProblem) {
        return true;
    }
}
'@

[System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$headers = @{
    'X-API-Key' = '55c08fa3-2b61-497b-9e7e-4df35d79b58b'
}

Write-Host "Testing GET all announcements..."
try {
    $response = Invoke-RestMethod -Uri 'https://localhost:8081/api/v1/announcements' -Method GET -Headers $headers
    Write-Host "Success! Response received."
    if ($response) {
        $jsonResponse = $response | ConvertTo-Json -Depth 3
        Write-Host "Response: $jsonResponse"
    } else {
        Write-Host "Response was null or empty"
    }
} catch {
    Write-Host "Error getting announcements: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        Write-Host "Status: $($_.Exception.Response.StatusCode)"
    }
}

Write-Host ""
Write-Host "Testing GET metrics endpoint..."
try {
    $response = Invoke-RestMethod -Uri 'https://localhost:8081/api/v1/announcements/metrics' -Method GET -Headers $headers
    Write-Host "Success! Metrics response received."
    if ($response) {
        $jsonResponse = $response | ConvertTo-Json -Depth 3
        Write-Host "Metrics Response: $jsonResponse"
    } else {
        Write-Host "Metrics response was null or empty"
    }
} catch {
    Write-Host "Error getting metrics: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        Write-Host "Status: $($_.Exception.Response.StatusCode)"
    }
}
