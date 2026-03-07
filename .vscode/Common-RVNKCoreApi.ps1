# Common RVNKCore API Configuration Module
# Provides unified RVNKCore API configuration loading across all development scripts
# Uses project.json configuration with secure API key handling
# Supports both HTTP and HTTPS endpoints with SSL certificate management

function Get-RVNKCoreApiConfig {
    <#
    .SYNOPSIS
    Gets RVNKCore API configuration from project.json
    
    .DESCRIPTION
    Loads RVNKCore API connection parameters from project.json configuration file.
    Ensures API keys are never exposed in console output.
    
    .PARAMETER ConfigPath
    Optional path to project.json file. Defaults to script directory.
    
    .EXAMPLE
    $apiConfig = Get-RVNKCoreApiConfig
    
    .EXAMPLE  
    $apiConfig = Get-RVNKCoreApiConfig -ConfigPath "C:\path\to\project.json"
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
        
        if (-not $config.RVNKCoreAPI) {
            throw "RVNKCoreAPI configuration section not found in project.json"
        }
        
        $apiConfig = $config.RVNKCoreAPI
        
        # Validate required API configuration fields based on actual structure
        if (-not $apiConfig.httpUrl) {
            throw "Missing required RVNKCore API configuration field: httpUrl"
        }
        if (-not $apiConfig.httpsUrl) {
            throw "Missing required RVNKCore API configuration field: httpsUrl"
        }
        if (-not $apiConfig.apiKey) {
            throw "Missing required RVNKCore API configuration field: apiKey"
        }
        
        # Parse URLs to extract hosts and ports
        $httpUri = [Uri]$apiConfig.httpUrl
        $httpsUri = [Uri]$apiConfig.httpsUrl
        
        # Return standardized configuration object as PSCustomObject for proper property access
        return [PSCustomObject]@{
            HttpUrl = $apiConfig.httpUrl
            HttpsUrl = $apiConfig.httpsUrl
            HttpHost = $httpUri.Host
            HttpPort = $httpUri.Port
            HttpsHost = $httpsUri.Host
            HttpsPort = $httpsUri.Port
            ApiKey = $apiConfig.apiKey
            ApiPath = "/api"
            Version = "v1"
        }
    }
    catch {
        throw "Failed to load RVNKCore API configuration: $($_.Exception.Message)"
    }
}

function Get-RVNKCoreSecureApiKey {
    <#
    .SYNOPSIS
    Retrieves RVNKCore API key as SecureString from project configuration
    
    .DESCRIPTION
    Loads the RVNKCore API key from project.json and converts it to SecureString
    for secure handling in memory. The API key is never exposed as plain text.
    
    .PARAMETER ConfigPath
    Optional path to project.json file. Defaults to script directory.
    
    .OUTPUTS
    System.Security.SecureString - The API key as SecureString
    
    .EXAMPLE
    $secureApiKey = Get-RVNKCoreSecureApiKey
    #>
    param(
        [string]$ConfigPath = ""
    )
    
    try {
        $config = Get-RVNKCoreApiConfig -ConfigPath $ConfigPath
        if (-not $config.ApiKey) {
            throw "API key not found in RVNKCore configuration"
        }
        
        # Convert plain text API key to SecureString
        $secureApiKey = ConvertTo-SecureString -String $config.ApiKey -AsPlainText -Force
        return $secureApiKey
    }
    catch {
        throw "Failed to get RVNKCore secure API key: $($_.Exception.Message)"
    }
}

function ConvertFrom-SecureStringToPlain {
    <#
    .SYNOPSIS
    Safely converts SecureString to plain text for API requests
    
    .DESCRIPTION
    Converts SecureString to plain text using NetworkCredential method for reliability.
    Use this function only when plain text is required for HTTP header construction.
    
    .PARAMETER SecureString
    The SecureString to convert
    
    .OUTPUTS
    String - Plain text API key (use immediately and dispose)
    
    .EXAMPLE
    $secureApiKey = Get-RVNKCoreSecureApiKey
    $plainApiKey = ConvertFrom-SecureStringToPlain -SecureString $secureApiKey
    # Use $plainApiKey immediately, then clear it
    $plainApiKey = $null
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

function Get-RVNKCoreHeaders {
    <#
    .SYNOPSIS
    Creates standard HTTP headers for RVNKCore API requests
    
    .DESCRIPTION
    Generates the standard headers required for RVNKCore API authentication,
    including the X-API-Key header and Content-Type settings.
    
    .PARAMETER ConfigPath
    Optional path to project.json file. Defaults to script directory.
    
    .PARAMETER ContentType
    Content-Type header value. Defaults to 'application/json'.
    
    .OUTPUTS
    Hashtable - Headers for HTTP requests
    
    .EXAMPLE
    $headers = Get-RVNKCoreHeaders
    
    .EXAMPLE
    $headers = Get-RVNKCoreHeaders -ContentType "application/x-www-form-urlencoded"
    #>
    param(
        [string]$ConfigPath = "",
        [string]$ContentType = "application/json"
    )
    
    try {
        $secureApiKey = Get-RVNKCoreSecureApiKey -ConfigPath $ConfigPath
        $plainApiKey = ConvertFrom-SecureStringToPlain -SecureString $secureApiKey
        
        $headers = @{
            'X-API-Key' = $plainApiKey
            'Content-Type' = $ContentType
            'Accept' = 'application/json'
            'User-Agent' = 'RVNKCore-PowerShell-Client/1.0'
        }
        
        # Clear sensitive data immediately
        $plainApiKey = $null
        $secureApiKey = $null
        
        return $headers
    }
    catch {
        throw "Failed to create RVNKCore API headers: $($_.Exception.Message)"
    }
}

function Initialize-SSLSettings {
    <#
    .SYNOPSIS
    Configures SSL/TLS settings for RVNKCore API requests
    
    .DESCRIPTION
    Sets up SSL certificate handling for development environments.
    Allows bypassing certificate validation for self-signed certificates.
    
    .PARAMETER IgnoreSSLErrors
    Switch to disable SSL certificate validation (development only)
    
    .EXAMPLE
    Initialize-SSLSettings -IgnoreSSLErrors
    #>
    param(
        [switch]$IgnoreSSLErrors
    )
    
    if ($IgnoreSSLErrors) {
        # Modern PowerShell approach to bypass SSL certificate validation
        if ($PSVersionTable.PSVersion.Major -ge 6) {
            # PowerShell 6+ approach - will be used in request parameters
            Write-RVNKCoreStatus "SSL certificate validation will be skipped (PowerShell 6+)" -Type "WARN"
        } else {
            # Windows PowerShell 5.1 and earlier approach
            if (-not ([System.Management.Automation.PSTypeName]'TrustAllCertsPolicy').Type) {
                Add-Type @'
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAllCertsPolicy : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint srvPoint, X509Certificate certificate, WebRequest request, int certificateProblem) {
        return true;
    }
}
'@
            }
            
            [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
            [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
            Write-RVNKCoreStatus "SSL certificate validation disabled for development (Windows PowerShell)" -Type "WARN"
        }
    } else {
        # Use default certificate validation
        if ($PSVersionTable.PSVersion.Major -lt 6) {
            [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
        }
        Write-RVNKCoreStatus "Using default SSL certificate validation" -Type "DEBUG"
    }
}

function Write-RVNKCoreStatus {
    <#
    .SYNOPSIS
    Writes status message for RVNKCore API operations
    
    .DESCRIPTION
    Standardized status message formatting for RVNKCore API-related scripts.
    Ensures consistent logging format and never exposes API keys in output.
    
    .PARAMETER Message
    Status message to display
    
    .PARAMETER Type
    Message type: INFO (default), WARN, ERROR, DEBUG
    
    .EXAMPLE
    Write-RVNKCoreStatus "API request successful"
    Write-RVNKCoreStatus "Authentication failed" -Type "ERROR"
    #>
    
    param(
        [string]$Message, 
        [ValidateSet("INFO", "WARN", "ERROR", "DEBUG")]
        [string]$Type = "INFO"
    )
    
    $timestamp = Get-Date -Format "HH:mm:ss"
    
    # Ensure no API keys are accidentally logged
    $sanitizedMessage = $Message -replace "X-API-Key: [^,\s]*", "X-API-Key: ***"
    $sanitizedMessage = $sanitizedMessage -replace "apiKey=[^;&]*", "apiKey=***"
    $sanitizedMessage = $sanitizedMessage -replace """apiKey"":\s*""[^""]*""", """apiKey"": ""***"""
    
    switch ($Type) {
        "INFO"  { Write-Host "[$timestamp] $sanitizedMessage" -ForegroundColor Green }
        "WARN"  { Write-Host "[$timestamp] WARNING: $sanitizedMessage" -ForegroundColor Yellow }
        "ERROR" { Write-Host "[$timestamp] ERROR: $sanitizedMessage" -ForegroundColor Red }
        "DEBUG" { Write-Host "[$timestamp] DEBUG: $sanitizedMessage" -ForegroundColor Gray }
    }
}

function Invoke-RVNKCoreApiRequest {
    <#
    .SYNOPSIS
    Executes an HTTP request against the RVNKCore API
    
    .DESCRIPTION
    Makes HTTP requests to RVNKCore API endpoints with proper authentication,
    error handling, and response formatting. Supports both HTTP and HTTPS.
    
    .PARAMETER Endpoint
    The API endpoint path (e.g., "/api/v1/announcements")
    
    .PARAMETER Method
    HTTP method (GET, POST, PUT, DELETE, etc.)
    
    .PARAMETER Body
    Request body for POST/PUT requests
    
    .PARAMETER UseHttps
    Switch to use HTTPS instead of HTTP
    
    .PARAMETER IgnoreSSLErrors
    Switch to disable SSL certificate validation
    
    .PARAMETER ConfigPath
    Optional path to project.json file
    
    .PARAMETER ReturnRaw
    Switch to return raw response instead of parsed JSON
    
    .PARAMETER ShowDebug
    Switch to enable debug output
    
    .OUTPUTS
    PSCustomObject with response data or error information
    
    .EXAMPLE
    $response = Invoke-RVNKCoreApiRequest -Endpoint "/api/v1/announcements" -Method "GET" -UseHttps
    
    .EXAMPLE
    $body = '{"title": "Test", "message": "Hello"}'
    $response = Invoke-RVNKCoreApiRequest -Endpoint "/api/v1/announcements" -Method "POST" -Body $body -UseHttps -IgnoreSSLErrors
    #>
    param(
        [Parameter(Mandatory = $true)]
        [string]$Endpoint,
        
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")]
        [string]$Method,
        
        [string]$Body = "",
        
        [switch]$UseHttps,
        
        [switch]$IgnoreSSLErrors,
        
        [string]$ConfigPath = "",
        
        [switch]$ReturnRaw,
        
        [switch]$ShowDebug
    )
    
    try {
        $config = Get-RVNKCoreApiConfig -ConfigPath $ConfigPath
        $headers = Get-RVNKCoreHeaders -ConfigPath $ConfigPath
        
        # Initialize SSL settings if needed
        if ($UseHttps -and $IgnoreSSLErrors) {
            Initialize-SSLSettings -IgnoreSSLErrors
        }
        
        # Build request URL
        $baseUrl = if ($UseHttps) { $config.HttpsUrl } else { $config.HttpUrl }
        $requestUrl = $baseUrl + $Endpoint
        
        if ($ShowDebug) {
            Write-RVNKCoreStatus "Making $Method request to: $requestUrl" -Type "DEBUG"
            Write-RVNKCoreStatus "Headers: $($headers.Keys -join ', ')" -Type "DEBUG"
            if ($Body) {
                Write-RVNKCoreStatus "Body length: $($Body.Length) characters" -Type "DEBUG"
            }
        }
        
        # Prepare request parameters
        $requestParams = @{
            Uri = $requestUrl
            Method = $Method
            Headers = $headers
            ErrorAction = 'Stop'
        }
        
        # Add SSL bypass for PowerShell 6+ if requested
        if ($IgnoreSSLErrors -and $PSVersionTable.PSVersion.Major -ge 6) {
            $requestParams.SkipCertificateCheck = $true
        }
        
        # Add body for methods that support it
        if ($Method -in @("POST", "PUT", "PATCH") -and $Body) {
            $requestParams.Body = $Body
        }
        
        # Execute request
        $response = Invoke-RestMethod @requestParams
        
        # Clear sensitive data
        $headers = $null
        
        if ($ShowDebug) {
            Write-RVNKCoreStatus "Request completed successfully" -Type "DEBUG"
        }
        
        # Return response
        if ($ReturnRaw) {
            return $response
        } else {
            return [PSCustomObject]@{
                Success = $true
                Data = $response
                StatusCode = 200  # RestMethod doesn't provide status code on success
                Method = $Method
                Endpoint = $Endpoint
                Message = "Request completed successfully"
            }
        }
    }
    catch [System.Net.WebException] {
        # Handle HTTP errors
        $statusCode = [int]$_.Exception.Response.StatusCode
        $statusDescription = $_.Exception.Response.StatusDescription
        
        $errorResponse = $null
        if ($_.Exception.Response) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $errorResponse = $reader.ReadToEnd()
                $reader.Close()
            } catch {
                $errorResponse = "Unable to read error response"
            }
        }
        
        Write-RVNKCoreStatus "HTTP $statusCode ($statusDescription): $errorResponse" -Type "ERROR"
        
        return [PSCustomObject]@{
            Success = $false
            Data = $null
            StatusCode = $statusCode
            Method = $Method
            Endpoint = $Endpoint
            Message = $statusDescription
            ErrorResponse = $errorResponse
        }
    }
    catch {
        Write-RVNKCoreStatus "Request failed: $($_.Exception.Message)" -Type "ERROR"
        
        return [PSCustomObject]@{
            Success = $false
            Data = $null
            StatusCode = 0
            Method = $Method
            Endpoint = $Endpoint
            Message = $_.Exception.Message
            ErrorResponse = $null
        }
    }
    finally {
        # Clear sensitive data
        if ($headers) { $headers = $null }
    }
}

function Test-RVNKCoreApiConnection {
    <#
    .SYNOPSIS
    Tests RVNKCore API connectivity
    
    .DESCRIPTION
    Tests the RVNKCore API connection using a simple health check endpoint.
    Tests both HTTP and HTTPS if available.
    
    .PARAMETER ConfigPath
    Optional path to project.json file. Defaults to script directory.
    
    .PARAMETER TestHttps
    Switch to test HTTPS connection
    
    .PARAMETER TestHttp
    Switch to test HTTP connection
    
    .PARAMETER IgnoreSSLErrors
    Switch to disable SSL certificate validation for HTTPS tests
    
    .PARAMETER ShowDebug
    Switch to enable debug output
    
    .OUTPUTS
    PSCustomObject with connection test results
    
    .EXAMPLE
    $status = Test-RVNKCoreApiConnection -TestHttps -IgnoreSSLErrors
    
    .EXAMPLE
    $status = Test-RVNKCoreApiConnection -TestHttp -TestHttps -ShowDebug
    #>
    param(
        [string]$ConfigPath = "",
        [switch]$TestHttps,
        [switch]$TestHttp,
        [switch]$IgnoreSSLErrors,
        [switch]$ShowDebug
    )
    
    try {
        $config = Get-RVNKCoreApiConfig -ConfigPath $ConfigPath
        $results = @()
        
        if ($ShowDebug) {
            Write-RVNKCoreStatus "Starting RVNKCore API connection tests" -Type "DEBUG"
        }
        
        # Test HTTPS if requested
        if ($TestHttps) {
            if ($ShowDebug) {
                Write-RVNKCoreStatus "Testing HTTPS connection to $($config.HttpsUrl)" -Type "DEBUG"
            }
            
            $httpsResult = Invoke-RVNKCoreApiRequest -Endpoint "/api/v1/announcements/count" -Method "GET" -UseHttps -IgnoreSSLErrors:$IgnoreSSLErrors -ConfigPath $ConfigPath -ShowDebug:$ShowDebug
            
            $results += [PSCustomObject]@{
                Protocol = "HTTPS"
                Port = $config.HttpsPort
                Success = $httpsResult.Success
                StatusCode = $httpsResult.StatusCode
                Message = $httpsResult.Message
                Url = "$($config.HttpsUrl)/api/v1/announcements/count"
            }
        }
        
        # Test HTTP if requested
        if ($TestHttp) {
            if ($ShowDebug) {
                Write-RVNKCoreStatus "Testing HTTP connection to $($config.HttpUrl)" -Type "DEBUG"
            }
            
            $httpResult = Invoke-RVNKCoreApiRequest -Endpoint "/api/v1/announcements/count" -Method "GET" -ConfigPath $ConfigPath -ShowDebug:$ShowDebug
            
            $results += [PSCustomObject]@{
                Protocol = "HTTP"
                Port = $config.HttpPort
                Success = $httpResult.Success
                StatusCode = $httpResult.StatusCode
                Message = $httpResult.Message
                Url = "$($config.HttpUrl)/api/v1/announcements/count"
            }
        }
        
        # If no specific protocol requested, default to HTTPS
        if (-not $TestHttps -and -not $TestHttp) {
            return Test-RVNKCoreApiConnection -TestHttps -IgnoreSSLErrors:$IgnoreSSLErrors -ConfigPath $ConfigPath -ShowDebug:$ShowDebug
        }
        
        return [PSCustomObject]@{
            OverallSuccess = ($results | Where-Object { $_.Success }).Count -gt 0
            Results = $results
            TestedAt = Get-Date
            Config = @{
                Host = $config.Host
                HttpsPort = $config.HttpsPort
                HttpPort = $config.HttpPort
            }
        }
    }
    catch {
        Write-RVNKCoreStatus "Connection test failed: $($_.Exception.Message)" -Type "ERROR"
        
        return [PSCustomObject]@{
            OverallSuccess = $false
            Results = @()
            TestedAt = Get-Date
            Error = $_.Exception.Message
        }
    }
}

function Get-RVNKCoreApiEndpoints {
    <#
    .SYNOPSIS
    Returns a list of common RVNKCore API endpoints
    
    .DESCRIPTION
    Provides a reference list of available API endpoints organized by category.
    Useful for testing and development scripts.
    
    .OUTPUTS
    PSCustomObject with categorized endpoint information
    
    .EXAMPLE
    $endpoints = Get-RVNKCoreApiEndpoints
    $endpoints.Announcements | ForEach-Object { Write-Host $_.Path }
    #>
    
    return [PSCustomObject]@{
        Announcements = @(
            @{ Path = "/api/v1/announcements"; Methods = @("GET", "POST"); Description = "List all announcements or create new" }
            @{ Path = "/api/v1/announcements/{id}"; Methods = @("GET", "PUT", "DELETE"); Description = "Get, update, or delete specific announcement" }
            @{ Path = "/api/v1/announcements/active"; Methods = @("GET"); Description = "Get active announcements only" }
            @{ Path = "/api/v1/announcements/count"; Methods = @("GET"); Description = "Get total announcement count" }
            @{ Path = "/api/v1/announcements/count/active"; Methods = @("GET"); Description = "Get active announcement count" }
            @{ Path = "/api/v1/announcements/search"; Methods = @("GET"); Description = "Search announcements (param: q)" }
            @{ Path = "/api/v1/announcements/type/{type}"; Methods = @("GET"); Description = "Get announcements by type" }
            @{ Path = "/api/v1/announcements/world/{world}"; Methods = @("GET"); Description = "Get announcements for world" }
            @{ Path = "/api/v1/announcements/group/{group}"; Methods = @("GET"); Description = "Get announcements for group" }
            @{ Path = "/api/v1/announcements/metrics"; Methods = @("GET"); Description = "Get announcement system metrics" }
            @{ Path = "/api/v1/announcements/bulk-import"; Methods = @("POST"); Description = "Bulk import announcements" }
            @{ Path = "/api/v1/announcements/{id}/activate"; Methods = @("PUT"); Description = "Activate announcement" }
            @{ Path = "/api/v1/announcements/{id}/deactivate"; Methods = @("PUT"); Description = "Deactivate announcement" }
        )
        
        Players = @(
            @{ Path = "/api/v1/players"; Methods = @("GET"); Description = "List all players" }
            @{ Path = "/api/v1/players/{uuid}"; Methods = @("GET", "PUT", "DELETE"); Description = "Get, update, or delete specific player" }
            @{ Path = "/api/v1/players/search"; Methods = @("GET"); Description = "Search players (param: q)" }
            @{ Path = "/api/v1/players/online"; Methods = @("GET"); Description = "Get online players only" }
            @{ Path = "/api/v1/players/count"; Methods = @("GET"); Description = "Get total player count" }
        )
        
        Worlds = @(
            @{ Path = "/api/v1/worlds"; Methods = @("GET"); Description = "List all worlds" }
            @{ Path = "/api/v1/worlds/{name}"; Methods = @("GET"); Description = "Get specific world by name" }
            @{ Path = "/api/v1/worlds/with-players"; Methods = @("GET"); Description = "Get worlds with players" }
            @{ Path = "/api/v1/worlds/statistics"; Methods = @("GET"); Description = "Get world statistics" }
            @{ Path = "/api/v1/worlds/environment/{environment}"; Methods = @("GET"); Description = "Get worlds by environment" }
        )
        
        Health = @(
            @{ Path = "/api/health"; Methods = @("GET"); Description = "API health check" }
            @{ Path = "/api/status"; Methods = @("GET"); Description = "API status information" }
        )
    }
}

# Main functions available for dot-sourcing:
# Get-RVNKCoreApiConfig, Get-RVNKCoreSecureApiKey, ConvertFrom-SecureStringToPlain
# Get-RVNKCoreHeaders, Initialize-SSLSettings, Write-RVNKCoreStatus
# Invoke-RVNKCoreApiRequest, Test-RVNKCoreApiConnection, Get-RVNKCoreApiEndpoints
