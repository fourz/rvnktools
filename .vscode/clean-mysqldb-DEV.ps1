# Clean MySQL Database - Development Environment
# Removes all tables from the RVNKCore test database
# Use when schema changes are expected to break existing version

param(
    [switch]$Force = $false,
    [switch]$ListOnly = $false
)

# Load common MySQL configuration module
. (Join-Path $PSScriptRoot "Common-MySQL.ps1")

# Load MySQL configuration from project.json
try {
    $MySQLConfig = Get-MySQLConfig
} catch {
    Write-MySQLStatus "Failed to load MySQL configuration: $($_.Exception.Message)" -Type "ERROR"
    exit 1
}

function Write-Status {
    param([string]$Message, [string]$Type = "INFO")
    Write-MySQLStatus -Message $Message -Type $Type
}

function Invoke-MySQLCommand {
    param(
        [string]$Query,
        [switch]$ReturnData
    )
    
    try {
        if ($ReturnData) {
            # Use PowerShell cmdlet-based query with data return
            return Invoke-MySQLQuery -Query $Query -ReturnData
        } else {
            # Use PowerShell cmdlet-based query without data return
            return Invoke-MySQLQuery -Query $Query
        }
    }
    catch {
        Write-Status "Failed to execute MySQL command: $($_.Exception.Message)" "ERROR"
        throw $_.Exception
    }
}

function Get-DatabaseTables {
    try {
        return Get-MySQLTables
    }
    catch {
        Write-Status "Failed to get database tables: $($_.Exception.Message)" "ERROR"
        return @()
    }
}

function Remove-AllTables {
    param([string[]]$Tables)
    
    if ($Tables.Count -eq 0) {
        Write-Status "No tables found to remove"
        return $true
    }
    
    Write-Status "Found $($Tables.Count) tables to remove"
    
    # Disable foreign key checks
    try {
        Invoke-MySQLCommand -Query "SET FOREIGN_KEY_CHECKS = 0"
        Write-Status "Disabled foreign key checks"
    }
    catch {
        Write-Status "Failed to disable foreign key checks: $($_.Exception.Message)" "WARN"
    }
    
    $removedCount = 0
    foreach ($table in $Tables) {
        try {
            Write-Status "Dropping table: $table" "DEBUG"
            $dropQuery = "DROP TABLE IF EXISTS ``$table``"
            $result = Invoke-MySQLCommand -Query $dropQuery
            if ($result) {
                $removedCount++
                Write-Status "Dropped table: $table"
            } else {
                Write-Status "Failed to drop table: $table" "ERROR"
            }
        }
        catch {
            Write-Status "Failed to drop table '$table': $($_.Exception.Message)" "ERROR"
        }
    }
    
    # Re-enable foreign key checks
    try {
        Invoke-MySQLCommand -Query "SET FOREIGN_KEY_CHECKS = 1"
        Write-Status "Re-enabled foreign key checks"
    }
    catch {
        Write-Status "Failed to re-enable foreign key checks: $($_.Exception.Message)" "WARN"
    }
    
    Write-Status "Successfully removed $removedCount of $($Tables.Count) tables"
    return $removedCount -eq $Tables.Count
}

# Main execution
Write-Status "=== MySQL Database Cleanup - Development Environment ==="

# Load MySQL configuration
try {
    $mysqlConfig = Get-MySQLConfig
    if (-not $mysqlConfig) {
        Write-Status "Failed to load MySQL configuration" "ERROR"
        exit 1
    }
    Write-Status "Target: $($mysqlConfig.Host):$($mysqlConfig.Port)/$($mysqlConfig.Database)"
} catch {
    Write-Status "Failed to load MySQL configuration: $($_.Exception.Message)" "ERROR"
    exit 1
}

# Test connection by getting table list
Write-Status "Testing database connection with SqlDatabase PowerShell module..."
try {
    # First test basic connectivity
    $connectionTest = Test-MySQLConnection
    if (-not $connectionTest.IsConnected) {
        Write-Status "Connection test failed: $($connectionTest.Message)" "ERROR"
        exit 1
    }
    Write-Status "Connection test successful: $($connectionTest.Message)"
    
    # Then get table list
    $tables = Get-DatabaseTables
    Write-Status "Successfully connected and retrieved table list via $($connectionTest.Method)"
} catch {
    Write-Status "Failed to connect to database: $($_.Exception.Message)" "ERROR"
    exit 1
}

if ($null -eq $tables) {
    $tables = @()
}

if ($tables.Count -eq 0) {
    Write-Status "No tables found in database"
    exit 0
}

if ($ListOnly) {
    Write-Status "Tables in database:"
    foreach ($table in $tables) {
        Write-Host "  - $table"
    }
    exit 0
}

# Confirm action unless Force is specified
if (-not $Force) {
    Write-Status "This will remove ALL $($tables.Count) tables from database '$($mysqlConfig.Database)'" "WARN"
    Write-Status "Tables to be removed:" "WARN"
    foreach ($table in $tables) {
        Write-Host "  - $table" -ForegroundColor Yellow
    }
    
    $confirmation = Read-Host "Are you sure you want to continue? (type 'YES' to confirm)"
    if ($confirmation -ne "YES") {
        Write-Status "Operation cancelled by user"
        exit 0
    }
}

# Remove all tables
$success = Remove-AllTables -Tables $tables

if ($success) {
    Write-Status "Database cleanup completed successfully"
    exit 0
} else {
    Write-Status "Database cleanup completed with errors" "WARN"
    exit 1
}
