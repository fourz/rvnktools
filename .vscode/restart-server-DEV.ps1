# MCSS API V2
# https://docs.mcserversoft.com/apis/v2#tag/Servers

$percent = 0
$hostname = 'localhost'
$apiport = '25560'
$statusport = '3979'
$actionuri = "http://${hostname}:$apiport/api/v2/servers/execute/action"
$queryuri = "http://${hostname}:$apiport/api/v2/servers"
$headers = @{
    apiKey = "aJSdlPsoIvFK5pZxzhjUN.eNRJABkdU6j7CQlerybU6pWCus9cNLaignw5khcEI8DGI.-9zzQLx2N3miq1x5yLbrv80a.9NUvs5Wix86cnigs3KOcvxKlYk7"
}

# 4 = restart

$body = @{
    serverids = 
        @("1eb313b1-40f7-4209-aa9d-352128214206")
    action = 4 
} | ConvertTo-Json -Depth 2

try {
    Invoke-RestMethod -Uri $actionuri -Method Post -Headers $headers -Body $body -ContentType "application/json"

    Write-Host "Server restart initiated on ${hostname}:$statusport."

} catch {

    Write-Error "Error during server restart: $($_.Exception.Message)"
}

try {
    $status = Invoke-RestMethod -Uri $queryuri -Method Get -Headers $headers 

} catch {
    
    Write-Error "Error during status check: $($_.Exception.Message)"
}

$serverup = ($status | Where-Object {$_.serverId -eq $serverId }).status -eq 1

$serverup