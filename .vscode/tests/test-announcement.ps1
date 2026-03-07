$headers = @{
    'X-API-Key' = '55c08fa3-2b61-497b-9e7e-4df35d79b58b'
    'Content-Type' = 'application/json'
}

$body = @{
    title = 'Test Announcement'
    message = 'This is a test announcement for API testing'
    type = 'broadcast'
    active = $true
    interval_seconds = 300
} | ConvertTo-Json

Write-Host "Body: $body"

try {
    $response = Invoke-RestMethod -Uri 'https://localhost:8081/api/v1/announcements' -Method POST -Headers $headers -Body $body -SkipCertificateCheck
    Write-Host "POST response: $($response | ConvertTo-Json -Depth 3)"
} catch {
    Write-Host "Error creating announcement: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        Write-Host "Status: $($_.Exception.Response.StatusCode)"
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseText = $reader.ReadToEnd()
        Write-Host "Response body: $responseText"
    }
}
