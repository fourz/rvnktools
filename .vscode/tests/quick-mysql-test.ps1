# Quick MySQL Connection Test
# Test the corrected SecureString password conversion

Write-Host "=== Quick MySQL Connection Test ===" -ForegroundColor Cyan

# Import the common MySQL module
. "$PSScriptRoot\Common-MySQL.ps1"

Write-Host "`nTesting configuration and password handling..." -ForegroundColor Green

try {
    # Test configuration loading
    $config = Get-MySQLConfig
    Write-Host "✓ Configuration loaded successfully" -ForegroundColor Green
    
    # Test SecureString password conversion
    $securePassword = Get-MySQLSecurePassword
    $plainPassword = ConvertFrom-SecureStringToPlain -SecureString $securePassword
    
    Write-Host "✓ SecureString conversion successful" -ForegroundColor Green
    Write-Host "Password: '$plainPassword'" -ForegroundColor Cyan
    Write-Host "Password length: $($plainPassword.Length) characters" -ForegroundColor Cyan
    Write-Host "Password ends with 'D': $($plainPassword.EndsWith('D'))" -ForegroundColor $(if($plainPassword.EndsWith('D')) { 'Red' } else { 'Green' })
    
    # Test connection
    Write-Host "`nTesting MySQL connection..." -ForegroundColor Yellow
    $connectionTest = Test-MySQLConnection
    
    if ($connectionTest.IsConnected) {
        Write-Host "✅ CONNECTION SUCCESSFUL!" -ForegroundColor Green
        Write-Host "Method: $($connectionTest.Method)" -ForegroundColor Green
        Write-Host "Message: $($connectionTest.Message)" -ForegroundColor Green
    } else {
        Write-Host "❌ CONNECTION FAILED!" -ForegroundColor Red
        Write-Host "Message: $($connectionTest.Message)" -ForegroundColor Red
    }
    
    # Clean up sensitive data
    $plainPassword = $null
    $securePassword = $null
    
} catch {
    Write-Host "❌ Test failed: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== Test Complete ===" -ForegroundColor Cyan
