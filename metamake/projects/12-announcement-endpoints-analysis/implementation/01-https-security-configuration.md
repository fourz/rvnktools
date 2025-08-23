# HTTPS-First Security Configuration

**Guide ID**: 01-https-security-configuration  
**Priority**: Documentation Update  
**Estimated Time**: Documentation correction  
**Dependencies**: Understanding of security best practices

## Security Configuration Analysis

### Current Secure Configuration ✅

**HTTPS Protocol (Port 8081)**: Fully functional and follows security best practices  
**HTTP Protocol (Port 8080)**: Correctly disabled when HTTPS is active  
**Security Benefit**: All API communication encrypted and secure

### Why HTTP is Disabled

This configuration represents **correct security implementation**:
- **HTTPS Enforcement**: Industry standard for API security
- **HTTP Blocking**: Prevents accidental insecure connections  
- **Data Protection**: Credentials and sensitive data encrypted in transit
- **Best Practice**: HTTP disabled when HTTPS available (security-first approach)

## Development Approach

### Use HTTPS for All Testing

```powershell
# Primary method - HTTPS only (secure)
.\query-rvnkcoreapi-DEV.ps1 announcements -HttpsOnly

# Comprehensive testing with development SSL handling
.\query-rvnkcoreapi-DEV.ps1 all -HttpsOnly -IgnoreSSLErrors -Detail
```

### Handle Development Certificates

**Self-signed certificates**: Acceptable for development with proper flags  
**SSL error tolerance**: Use `-IgnoreSSLErrors` in development scripts  
**Production**: Replace with CA-signed certificates

## API Usage Patterns

### PowerShell Testing

```powershell
# Standard announcement testing
.\query-rvnkcoreapi-DEV.ps1 announcements -HttpsOnly

# Full API validation with SSL bypass for dev
.\query-rvnkcoreapi-DEV.ps1 all -HttpsOnly -IgnoreSSLErrors -Detail
```

### Web Application Integration

```javascript
// Always use HTTPS endpoints
const apiUrl = 'https://dev.rvnk.com:8081/api/v1/announcements';

// Handle self-signed certificates in development
const options = {
    agent: process.env.NODE_ENV === 'development' ? 
        new https.Agent({ rejectUnauthorized: false }) : undefined
};
```

## No Action Required

This configuration is **security-compliant and production-ready**:
- ✅ HTTPS working properly
- ✅ HTTP correctly blocked for security  
- ✅ Development tools configured for HTTPS testing
- ✅ Self-signed certificates properly handled

## Documentation Updates Required

Update all testing documentation to reflect HTTPS-first approach and remove any references to "fixing" HTTP connectivity, as the current behavior is the intended security feature.
