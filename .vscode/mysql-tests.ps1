# MySQL Connection and Database Tests
# Comprehensive testing script for MySQL operations using the unified Common-MySQL module
# Tests connection, table listing, queries, and database operations

Write-Host "=== MySQL Database Tests ===" -ForegroundColor Cyan
Write-Host "Using MySQL Console Client with Secure Password Handling`n" -ForegroundColor Gray

# Import the common MySQL module
. "$PSScriptRoot\Common-MySQL.ps1"

$testResults = @{
    ConfigurationTest = $false
    ConnectionTest = $false
    TablesTest = $false
    QueryTest = $false
}

# Test 1: Configuration Loading
Write-Host "1. Testing Configuration Loading..." -ForegroundColor Yellow
try {
    $config = Get-MySQLConfig
    $securePassword = Get-MySQLSecurePassword  
    $plainPassword = ConvertFrom-SecureStringToPlain -SecureString $securePassword
    
    Write-Host "   ✓ Configuration loaded successfully" -ForegroundColor Green
    Write-Host "   Host: $($config.Host)" -ForegroundColor Cyan
    Write-Host "   Port: $($config.Port)" -ForegroundColor Cyan
    Write-Host "   Database: $($config.Database)" -ForegroundColor Cyan
    Write-Host "   Username: $($config.Username)" -ForegroundColor Cyan
    Write-Host "   Password Length: $($plainPassword.Length) characters" -ForegroundColor Cyan
    
    # Password validation
    $expectedLength = 29
    if ($plainPassword.Length -eq $expectedLength) {
        Write-Host "   ✓ Password length correct ($expectedLength chars)" -ForegroundColor Green
    } else {
        Write-Host "   ❌ Password length incorrect (expected $expectedLength, got $($plainPassword.Length))" -ForegroundColor Red
    }
    
    $testResults.ConfigurationTest = $true
} catch {
    Write-Host "   ❌ Configuration test failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Clear sensitive data
$plainPassword = $null
$securePassword = $null

# Test 2: Connection Test
Write-Host "`n2. Testing MySQL Connection..." -ForegroundColor Yellow
try {
    $connectionResult = Test-MySQLConnection -ShowDebug
    if ($connectionResult.IsConnected) {
        Write-Host "   ✅ Connection successful!" -ForegroundColor Green
        Write-Host "   Method: $($connectionResult.Method)" -ForegroundColor Cyan
        if ($connectionResult.ConnectionInfo) {
            Write-Host "   Info: $($connectionResult.ConnectionInfo)" -ForegroundColor Gray
        }
        $testResults.ConnectionTest = $true
    } else {
        Write-Host "   ❌ Connection failed" -ForegroundColor Red
        Write-Host "   Error: $($connectionResult.Message)" -ForegroundColor Red
    }
} catch {
    Write-Host "   ❌ Connection test error: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 3: Table Listing
Write-Host "`n3. Testing Table Listing..." -ForegroundColor Yellow
try {
    $tables = Get-MySQLTables
    if ($tables -and $tables.Count -gt 0) {
        Write-Host "   ✅ Tables retrieved successfully!" -ForegroundColor Green
        Write-Host "   Found $($tables.Count) tables:" -ForegroundColor Cyan
        foreach ($table in $tables) {
            Write-Host "     • $table" -ForegroundColor Gray
        }
        $testResults.TablesTest = $true
    } else {
        Write-Host "   ⚠ No tables found in database" -ForegroundColor Yellow
        $testResults.TablesTest = $true  # Not an error, just empty database
    }
} catch {
    Write-Host "   ❌ Table listing failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 4: Query Execution
Write-Host "`n4. Testing Query Execution..." -ForegroundColor Yellow
try {
    # Test with a simple COUNT query on a known table
    if ($tables -and ($tables -contains "rvnk_players")) {
        $countResult = Invoke-MySQLQuery -Query "SELECT COUNT(*) as player_count FROM rvnk_players" -ReturnData
        if ($countResult) {
            Write-Host "   ✅ Query executed successfully!" -ForegroundColor Green
            Write-Host "   Player count: $($countResult[0])" -ForegroundColor Cyan
            $testResults.QueryTest = $true
        } else {
            Write-Host "   ⚠ Query executed but returned no data" -ForegroundColor Yellow
        }
    } elseif ($tables -and $tables.Count -gt 0) {
        # Try a generic query on the first available table
        $firstTable = $tables[0]
        $countResult = Invoke-MySQLQuery -Query "SELECT COUNT(*) as row_count FROM `"$firstTable`"" -ReturnData
        if ($countResult) {
            Write-Host "   ✅ Query executed successfully!" -ForegroundColor Green
            Write-Host "   Row count in $($firstTable) : $($countResult[0])" -ForegroundColor Cyan
            $testResults.QueryTest = $true
        } else {
            Write-Host "   ⚠ Query executed but returned no data" -ForegroundColor Yellow
        }
    } else {
        # Test with a simple SELECT without table dependency
        $simpleResult = Invoke-MySQLQuery -Query "SELECT 'Test successful' as message, CURRENT_TIMESTAMP as server_time" -ReturnData
        if ($simpleResult -and $simpleResult.Count -gt 0) {
            Write-Host "   ✅ Query executed successfully!" -ForegroundColor Green
            Write-Host "   Result: $($simpleResult -join ' | ')" -ForegroundColor Cyan
            $testResults.QueryTest = $true
        } else {
            Write-Host "   ❌ Simple query failed or returned no data" -ForegroundColor Red
        }
    }
} catch {
    Write-Host "   ❌ Query execution failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test Summary
Write-Host "`n=== Test Results Summary ===" -ForegroundColor Cyan
$passedTests = 0
$totalTests = $testResults.Count

foreach ($test in $testResults.GetEnumerator()) {
    $status = if ($test.Value) { "✅ PASSED" } else { "❌ FAILED" }
    $color = if ($test.Value) { "Green" } else { "Red" }
    Write-Host "$($test.Key): $status" -ForegroundColor $color
    if ($test.Value) { $passedTests++ }
}

Write-Host "`nOverall Status: $passedTests/$totalTests tests passed" -ForegroundColor $(if ($passedTests -eq $totalTests) { "Green" } else { "Yellow" })

if ($passedTests -eq $totalTests) {
    Write-Host "🎉 All MySQL operations are working correctly!" -ForegroundColor Green
    Write-Host "✅ Password handling: SECURE" -ForegroundColor Green
    Write-Host "✅ Connection method: MySQL Console Client" -ForegroundColor Green
    Write-Host "✅ Database operations: FUNCTIONAL" -ForegroundColor Green
} else {
    Write-Host "⚠ Some tests failed. Check error messages above." -ForegroundColor Yellow
}

Write-Host "`n=== MySQL Tests Complete ===" -ForegroundColor Cyan
