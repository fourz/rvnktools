# MCSS API V2
$actionuri = "http://localhost:25560/api/v2/servers/execute/command"
$queryuri = "http://localhost:25560/api/v2/servers"
$serverId = "1eb313b1-40f7-4209-aa9d-352128214206"  

$headers = @{
    apiKey = "aJSdlPsoIvFK5pZxzhjUN.eNRJABkdU6j7CQlerybU6pWCus9cNLaignw5khcEI8DGI.-9zzQLx2N3miq1x5yLbrv80a.9NUvs5Wix86cnigs3KOcvxKlYk7"
}
$body = @{
    serverids = 
        @("$serverId")
    command = 'reload confirm'
} | ConvertTo-Json -Depth 2

try {
    Invoke-RestMethod -Uri $actionuri -Method Post -Headers $headers -Body $body -ContentType "application/json"

    Write-Host "Plugin reload initiated started."

} catch {

    Write-Error "Error during server reload: $($_.Exception.Message)"
}

try {
    $status = Invoke-RestMethod -Uri $queryuri -Method Get -Headers $headers 

} catch {
    
    Write-Error "Error during status check: $($_.Exception.Message)"
}

$serverup = ($status | Where-Object {$_.serverId -eq $serverId }).status -eq 1

$serverup