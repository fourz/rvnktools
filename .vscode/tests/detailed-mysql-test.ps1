# Detailed MySQL SqlDatabase Connection Test
# Test SqlDatabase connection with detailed error reporting

Write-Host "=== Detailed MySQL SqlDatabase Test ===" -ForegroundColor Cyan

# Import the common MySQL module
. "$PSScriptRoot\Common-MySQL.ps1"

try {
    # Test with debug output
    Write-Host "`nTesting connection with debug output..." -ForegroundColor Green
    $connectionTest = Test-MySQLConnection -ShowDebug
    
    if ($connectionTest.IsConnected) {
        Write-Host "`n✅ CONNECTION SUCCESSFUL!" -ForegroundColor Green
        Write-Host "Testing table listing..." -ForegroundColor Green
        
        # Test a simple query
        $result = Invoke-MySQLQuery -Query "SHOW TABLES" -ReturnData -ShowDebug
        if ($result) {
            Write-Host "Query returned data successfully" -ForegroundColor Green
        } else {
            Write-Host "Query returned no data" -ForegroundColor Yellow
        }
    } else {
        Write-Host "`n❌ CONNECTION FAILED!" -ForegroundColor Red
        Write-Host "Method: $($connectionTest.Method)" -ForegroundColor Yellow
        Write-Host "Message: $($connectionTest.Message)" -ForegroundColor Yellow
    }
    
} catch {
    Write-Host "`n❌ Test failed with exception:" -ForegroundColor Red
    Write-Host "Exception: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Full Exception:" -ForegroundColor Red
    Write-Host $_.Exception -ForegroundColor Red
}

Write-Host "`n=== Detailed Test Complete ===" -ForegroundColor Cyan
