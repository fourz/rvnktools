# Common MySQL Configuration Module
# Provides unified MySQL configuration loading across all RVNKCore development scripts
# Uses MySQL console client for reliable database operations
# Always pulls configuration from project.json with secure password handling

function Get-MySQLConfig {
    <#
    .SYNOPSIS
    Gets MySQL configuration from project.json
    
    .DESCRIPTION
    Loads MySQL connection parameters from project.json configuration file.
    Ensures passwords are never exposed in console output.
    
    .PARAMETER ConfigPath
    Optional path to project.json file. Defaults to script directory.
    
    .EXAMPLE
    $mysqlConfig = Get-MySQLConfig
    
    .EXAMPLE  
    $mysqlConfig = Get-MySQLConfig -ConfigPath "C:\path\to\project.json"
    #>
    
    param(
        [string]$ConfigPath = ""
    )
    
    # Determine config file path
    if ([string]::IsNullOrEmpty($ConfigPath)) {
        $ConfigPath = Join-Path $PSScriptRoot "project.json"
    }
    
    if (-not (Test-Path $ConfigPath)) {
        throw "Configuration file not found: $ConfigPath"
    }
    
    try {
        $config = Get-Content -Path $ConfigPath | ConvertFrom-Json
        
        if (-not $config.MySQL) {
            throw "MySQL configuration section not found in project.json"
        }
        
        # Validate required MySQL configuration
        $requiredFields = @("host", "port", "database", "username", "password")
        foreach ($field in $requiredFields) {
            if (-not $config.MySQL.$field) {
                throw "Missing required MySQL configuration: $field"
            }
        }
        
        # Return standardized configuration object as PSCustomObject for proper property access
        return [PSCustomObject]@{
            Host = $config.MySQL.host
            Port = $config.MySQL.port
            Database = $config.MySQL.database
            Username = $config.MySQL.username
            Password = $config.MySQL.password
            UseSSL = if ($null -ne $config.MySQL.useSSL) { $config.MySQL.useSSL } else { $false }
        }
    }
    catch {
        throw "Failed to load MySQL configuration: $($_.Exception.Message)"
    }
}

function Get-MySQLSecurePassword {
    <#
    .SYNOPSIS
    Retrieves MySQL password as SecureString from project configuration
    
    .DESCRIPTION
    Loads the MySQL password from project.json and converts it to SecureString
    for secure handling in memory. The password is never exposed as plain text.
    
    .PARAMETER ConfigPath
    Optional path to project.json file. Defaults to script directory.
    
    .OUTPUTS
    System.Security.SecureString - The MySQL password as SecureString
    
    .EXAMPLE
    $securePassword = Get-MySQLSecurePassword
    #>
    param(
        [string]$ConfigPath = ""
    )
    
    try {
        $config = Get-MySQLConfig -ConfigPath $ConfigPath
        if (-not $config.Password) {
            throw "MySQL password not found in project configuration"
        }
        
        # Convert plain text password to SecureString
        $securePassword = ConvertTo-SecureString -String $config.Password -AsPlainText -Force
        return $securePassword
    }
    catch {
        throw "Failed to get MySQL secure password: $($_.Exception.Message)"
    }
}

function ConvertFrom-SecureStringToPlain {
    <#
    .SYNOPSIS
    Safely converts SecureString to plain text for command execution
    
    .DESCRIPTION
    Converts SecureString to plain text using NetworkCredential method for reliability.
    Use this function only when plain text is required for external command execution.
    
    .PARAMETER SecureString
    The SecureString to convert
    
    .OUTPUTS
    String - Plain text password (use immediately and dispose)
    
    .EXAMPLE
    $securePassword = Get-MySQLSecurePassword
    $plainPassword = ConvertFrom-SecureStringToPlain -SecureString $securePassword
    # Use $plainPassword immediately, then clear it
    $plainPassword = $null
    #>
    param([Parameter(Mandatory = $true)][System.Security.SecureString]$SecureString)
    
    try {
        # Use .NET's NetworkCredential approach which is proven reliable
        $credential = New-Object System.Net.NetworkCredential("", $SecureString)
        return $credential.Password
    }
    catch {
        throw "Failed to convert SecureString to plain text: $($_.Exception.Message)"
    }
}

function Get-MySQLExecutable {
    <#
    .SYNOPSIS
    Finds the MySQL console client executable
    
    .DESCRIPTION
    Locates the MySQL command-line client executable, checking common installation paths
    and the system PATH.
    
    .OUTPUTS
    String - Path to mysql.exe
    
    .EXAMPLE
    $mysqlPath = Get-MySQLExecutable
    #>
    
    # Common MySQL installation paths
    $commonPaths = @(
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe",
        "C:\Program Files (x86)\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe"
    )
    
    # Check common installation paths first
    foreach ($path in $commonPaths) {
        if (Test-Path $path) {
            return $path
        }
    }
    
    # Fall back to PATH
    try {
        $pathResult = Get-Command mysql -ErrorAction Stop
        return $pathResult.Source
    }
    catch {
        throw "MySQL client not found. Please install MySQL client or add it to PATH."
    }
}

function Write-MySQLStatus {
    <#
    .SYNOPSIS
    Writes status message for MySQL operations
    
    .DESCRIPTION
    Standardized status message formatting for MySQL-related scripts.
    Ensures consistent logging format across all RVNKCore development tools.
    Never exposes passwords in output.
    
    .PARAMETER Message
    Status message to display
    
    .PARAMETER Type
    Message type: INFO (default), WARN, ERROR, DEBUG
    
    .EXAMPLE
    Write-MySQLStatus "Database connection successful"
    Write-MySQLStatus "Connection failed" -Type "ERROR"
    #>
    
    param(
        [string]$Message, 
        [ValidateSet("INFO", "WARN", "ERROR", "DEBUG")]
        [string]$Type = "INFO"
    )
    
    $timestamp = Get-Date -Format "HH:mm:ss"
    
    # Ensure no passwords are accidentally logged
    $sanitizedMessage = $Message -replace "password=[^;]*", "password=***"
    $sanitizedMessage = $sanitizedMessage -replace "--password=[^\s]*", "--password=***"
    
    switch ($Type) {
        "INFO"  { Write-Host "[$timestamp] $sanitizedMessage" -ForegroundColor Green }
        "WARN"  { Write-Host "[$timestamp] WARNING: $sanitizedMessage" -ForegroundColor Yellow }
        "ERROR" { Write-Host "[$timestamp] ERROR: $sanitizedMessage" -ForegroundColor Red }
        "DEBUG" { Write-Host "[$timestamp] DEBUG: $sanitizedMessage" -ForegroundColor Gray }
    }
}

function Invoke-MySQLQuery {
    <#
    .SYNOPSIS
    Executes a MySQL query using MySQL console client
    
    .DESCRIPTION
    Executes a SQL query against MySQL database using the MySQL command-line client.
    Automatically handles secure password management and connection parameters.
    
    .PARAMETER Query
    The SQL query to execute
    
    .PARAMETER ConfigPath
    Optional path to project.json file. Defaults to script directory.
    
    .PARAMETER ReturnData
    Whether to return query results (for SELECT statements)
    
    .PARAMETER ShowDebug
    Switch to enable debug output (without password exposure)
    
    .OUTPUTS
    Query execution results or boolean success indicator
    
    .EXAMPLE
    $tables = Invoke-MySQLQuery -Query "SHOW TABLES" -ReturnData
    
    .EXAMPLE
    Invoke-MySQLQuery -Query "DROP TABLE IF EXISTS test_table"
    
    .EXAMPLE
    $result = Invoke-MySQLQuery -Query "SELECT COUNT(*) FROM rvnk_players" -ReturnData
    #>
    param(
        [Parameter(Mandatory = $true)]
        [string]$Query,
        
        [string]$ConfigPath = "",
        
        [switch]$ReturnData,
        
        [switch]$ShowDebug
    )
    
    try {
        $config = Get-MySQLConfig -ConfigPath $ConfigPath
        $securePassword = Get-MySQLSecurePassword -ConfigPath $ConfigPath
        $plainPassword = ConvertFrom-SecureStringToPlain -SecureString $securePassword
        $mysqlExe = Get-MySQLExecutable
        
        if ($ShowDebug) {
            Write-MySQLStatus "Executing query using MySQL console client" -Type "DEBUG"
            Write-MySQLStatus "Host: $($config.Host), Database: $($config.Database)" -Type "DEBUG"
        }
        
        # Build MySQL command arguments
        $mysqlArgs = @(
            "--host=$($config.Host)",
            "--port=$($config.Port)",
            "--user=$($config.Username)",
            "--password=$plainPassword",
            "--database=$($config.Database)",
            "--batch",
            "--skip-column-names",
            "--silent",
            "--execute=$Query"
        )
        
        # Execute MySQL command
        $result = & $mysqlExe $mysqlArgs 2>&1
        $exitCode = $LASTEXITCODE
        
        # Clear sensitive data immediately
        $plainPassword = $null
        $securePassword = $null
        
        if ($exitCode -eq 0) {
            if ($ShowDebug) {
                Write-MySQLStatus "Query executed successfully" -Type "DEBUG"
            }
            
            if ($ReturnData) {
                # Parse and return the results, filtering out MySQL warnings
                if ($result) {
                    $lines = $result -split "`r?`n" | Where-Object { 
                        $_ -and $_.Trim() -and 
                        -not $_.StartsWith("mysql: [Warning]") -and
                        -not $_.StartsWith("mysql:") 
                    }
                    return $lines
                } else {
                    return @()
                }
            } else {
                return $true
            }
        } else {
            $errorMessage = if ($result) { $result -join "; " } else { "Unknown MySQL error" }
            throw "MySQL query failed (exit code $exitCode): $errorMessage"
        }
    }
    catch {
        # Clear sensitive data on error
        if ($plainPassword) { $plainPassword = $null }
        if ($securePassword) { $securePassword = $null }
        
        throw "Failed to execute MySQL query: $($_.Exception.Message)"
    }
}

function Test-MySQLConnection {
    <#
    .SYNOPSIS
    Tests MySQL database connectivity using MySQL console client
    
    .DESCRIPTION
    Tests the MySQL connection using the configuration from project.json.
    Returns connection status and any error messages.
    
    .PARAMETER ConfigPath
    Optional path to project.json file. Defaults to script directory.
    
    .PARAMETER ShowDebug
    Switch to enable debug output
    
    .OUTPUTS
    PSCustomObject with IsConnected (bool), Message (string), and Method (string) properties
    
    .EXAMPLE
    $status = Test-MySQLConnection
    if ($status.IsConnected) { Write-Host "Connected successfully" }
    
    .EXAMPLE
    $status = Test-MySQLConnection -ShowDebug
    #>
    param(
        [string]$ConfigPath = "",
        [switch]$ShowDebug
    )
    
    try {
        if ($ShowDebug) {
            Write-MySQLStatus "Testing MySQL connection using console client" -Type "DEBUG"
        }
        
        # Test basic connectivity with a simple query
        $result = Invoke-MySQLQuery -Query "SELECT 1 as test" -ReturnData -ConfigPath $ConfigPath -ShowDebug:$ShowDebug
        
        if ($result -and $result.Count -gt 0) {
            return [PSCustomObject]@{
                IsConnected = $true
                Message = "MySQL connection successful via console client"
                Method = "MySQL Console Client"
                ConnectionInfo = $result -join "; "
            }
        } else {
            return [PSCustomObject]@{
                IsConnected = $false
                Message = "MySQL connection test returned no results"
                Method = "MySQL Console Client"
            }
        }
    }
    catch {
        if ($ShowDebug) {
            Write-MySQLStatus "Connection test failed: $($_.Exception.Message)" -Type "ERROR"
        }
        
        return [PSCustomObject]@{
            IsConnected = $false
            Message = "MySQL connection failed: $($_.Exception.Message)"
            Method = "MySQL Console Client"
        }
    }
}

function Get-MySQLTables {
    <#
    .SYNOPSIS
    Gets list of tables in the MySQL database
    
    .DESCRIPTION
    Retrieves a list of all tables in the configured MySQL database using SHOW TABLES command.
    
    .PARAMETER ConfigPath
    Optional path to project.json file. Defaults to script directory.
    
    .PARAMETER ShowDebug
    Switch to enable debug output
    
    .OUTPUTS
    Array of table names
    
    .EXAMPLE
    $tables = Get-MySQLTables
    Write-Host "Found $($tables.Count) tables"
    #>
    param(
        [string]$ConfigPath = "",
        [switch]$ShowDebug
    )
    
    try {
        $tables = Invoke-MySQLQuery -Query "SHOW TABLES" -ReturnData -ConfigPath $ConfigPath -ShowDebug:$ShowDebug
        
        if ($tables) {
            # Filter out any empty lines
            $filteredTables = $tables | Where-Object { $_ -and $_.Trim() }
            return $filteredTables
        } else {
            return @()
        }
    }
    catch {
        throw "Failed to get MySQL tables: $($_.Exception.Message)"
    }
}

# Main functions available for dot-sourcing:
# Get-MySQLConfig, Get-MySQLSecurePassword, ConvertFrom-SecureStringToPlain
# Get-MySQLExecutable, Write-MySQLStatus, Invoke-MySQLQuery 
# Test-MySQLConnection, Get-MySQLTables
