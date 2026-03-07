# RVNKCore Announcements REST API Testing Checklist

**Date**: August 23, 2025  
**Status**: REST API Validation Framework  
**Target**: Comprehensive HTTP/HTTPS endpoint testing and validation

---

## API Environment Setup

### Prerequisites

- [x] RVNKCore HTTP server (Jetty) running on port 8080
- [x] RVNKCore HTTPS server (Jetty) running on port 8081 (if configured)
- [x] API key authentication configured
- [x] CORS settings configured for web integration
- [x] SSL certificates configured for HTTPS (development)
- [ ] Rate limiting configured for production
- [ ] API documentation accessible (OpenAPI/Swagger)

### Test Tools and Scripts

- [x] PowerShell test script: `Test-AnnouncementsAPI.ps1`
- [x] VS Code REST Client extension (optional)
- [x] Postman collection (optional)
- [ ] Automated test runner configured
- [ ] Performance testing tools available

---

## Authentication and Authorization Testing

### API Key Authentication Tests

#### Basic Authentication Tests

- [ ] **Test: Valid API Key Access**
  - Header: `X-API-Key: valid-test-key`
  - Expected: 200 OK response, full API access granted
  - Validate: All protected endpoints accessible

- [ ] **Test: Invalid API Key Rejection**
  - Header: `X-API-Key: invalid-key`
  - Expected: 401 Unauthorized response
  - Validate: Error message descriptive, no sensitive data leaked

- [ ] **Test: Missing API Key Rejection**
  - Header: No `X-API-Key` header provided
  - Expected: 401 Unauthorized response
  - Validate: Authentication required message clear

- [ ] **Test: Malformed API Key Handling**
  - Header: `X-API-Key: malformed-key-format!@#`
  - Expected: 401 Unauthorized response
  - Validate: Input validation prevents injection attacks

#### Authorization Level Tests

- [ ] **Test: Read-Only API Key**
  - Setup: API key with read-only permissions
  - Validate: GET requests succeed, POST/PUT/DELETE requests fail
  - Expected: 403 Forbidden for write operations

- [ ] **Test: Admin API Key**
  - Setup: API key with full administrative permissions
  - Validate: All CRUD operations succeed
  - Expected: Full access to all endpoints and operations

- [ ] **Test: Permission-Based Resource Access**
  - Setup: Different permission levels for different announcement types
  - Validate: Access granted/denied based on resource and user permissions
  - Expected: Consistent authorization enforcement

---

## Core CRUD Operations Testing

### Create Operations (POST)

#### Single Announcement Creation

- [ ] **Test: POST /api/v1/announcements**
  - **Input**: Valid announcement JSON with all required fields

  ```json
  {
    "content": "Welcome to our server!",
    "type": "WELCOME",
    "priority": 1,
    "active": true,
    "targetWorld": "world",
    "targetGroup": "default",
    "displayDuration": 5000,
    "permission": "announce.view"
  }
  ```

  - **Expected**: 201 Created, Location header with resource URI
  - **Validate**: Response contains complete announcement object with generated ID
  - **VS Code Task**: `RVNKTools Debug` → Verify service logs show creation

- [ ] **Test: POST with Minimal Required Fields**
  - **Input**: JSON with only required fields (`content`, `type`)
  - **Expected**: 201 Created, default values applied for optional fields
  - **Validate**: Default values correctly set (active=true, priority=1, etc.)

- [ ] **Test: POST with Invalid Data**
  - **Input**: JSON with missing required fields
  - **Expected**: 400 Bad Request, validation error details
  - **Validate**: Error message specifies exactly which fields are invalid

- [ ] **Test: POST with Oversized Content**
  - **Input**: Announcement content exceeding maximum length
  - **Expected**: 400 Bad Request, content length validation error
  - **Validate**: Error message indicates content length limits

#### Bulk Announcement Creation

- [ ] **Test: POST /api/v1/announcements/bulk**
  - **Input**: Array of valid announcement objects
  - **Expected**: 201 Created, array of created announcement objects
  - **Validate**: All announcements created atomically or none created
  - **Performance**: Bulk creation significantly faster than individual calls

- [ ] **Test: POST Bulk with Mixed Valid/Invalid Data**
  - **Input**: Array containing both valid and invalid announcements
  - **Expected**: 400 Bad Request, transaction rolled back
  - **Validate**: No partial creation, clear error indicating invalid items

### Read Operations (GET)

#### Individual Announcement Retrieval

- [ ] **Test: GET /api/v1/announcements/{id}**
  - **Input**: Valid announcement UUID
  - **Expected**: 200 OK, complete announcement object
  - **Validate**: All fields present and correctly formatted
  - **Performance**: Response time < 50ms for database lookup

- [ ] **Test: GET with Non-Existent ID**
  - **Input**: Non-existent UUID
  - **Expected**: 404 Not Found, descriptive error message
  - **Validate**: Error response format consistent with API standards

- [ ] **Test: GET with Malformed UUID**
  - **Input**: Invalid UUID format
  - **Expected**: 400 Bad Request, UUID format validation error
  - **Validate**: Input validation prevents database errors

#### Collection Retrieval

- [ ] **Test: GET /api/v1/announcements**
  - **Expected**: 200 OK, array of active announcements
  - **Validate**: Only active announcements returned by default
  - **Pagination**: Response includes pagination metadata if applicable

- [ ] **Test: GET with Pagination Parameters**
  - **Input**: Query parameters `?page=1&size=10`
  - **Expected**: 200 OK, paginated results with metadata
  - **Validate**: Pagination headers (Total-Count, Page-Size) present
  - **Performance**: Large datasets paginated efficiently

- [ ] **Test: GET with Include Inactive Flag**
  - **Input**: Query parameter `?includeInactive=true`
  - **Expected**: 200 OK, both active and inactive announcements
  - **Validate**: Active status clearly indicated in response

#### Filtered Retrieval

- [ ] **Test: GET /api/v1/announcements/type/{type}**
  - **Input**: Valid announcement type (BROADCAST, WELCOME, etc.)
  - **Expected**: 200 OK, type-filtered announcements
  - **Validate**: Only announcements of specified type returned

- [ ] **Test: GET /api/v1/announcements/world/{world}**
  - **Input**: World name parameter
  - **Expected**: 200 OK, world-specific and global announcements
  - **Validate**: Proper filtering includes global announcements (targetWorld=null)

- [ ] **Test: GET /api/v1/announcements/group/{group}**
  - **Input**: Permission group name
  - **Expected**: 200 OK, group-specific and public announcements  
  - **Validate**: Permission-based filtering working correctly

#### Search Operations

- [ ] **Test: GET /api/v1/announcements/search?q={query}**
  - **Input**: Search query parameter
  - **Expected**: 200 OK, search results matching query
  - **Validate**: Case-insensitive search, partial matching working
  - **Performance**: Search performance acceptable for large datasets

- [ ] **Test: GET Search with Empty Query**
  - **Input**: Empty or whitespace-only search query
  - **Expected**: 400 Bad Request, search query validation error
  - **Validate**: Error message indicates query requirements

- [ ] **Test: GET Search with Special Characters**
  - **Input**: Search query with special characters and SQL injection attempts
  - **Expected**: 200 OK, safe search results (no SQL injection)
  - **Validate**: Input sanitization prevents security vulnerabilities

### Update Operations (PUT)

#### Individual Announcement Updates

- [ ] **Test: PUT /api/v1/announcements/{id}**
  - **Input**: Valid announcement UUID and complete updated object
  - **Expected**: 200 OK, updated announcement object returned
  - **Validate**: All specified fields updated, modification timestamp changed
  - **Concurrency**: Optimistic locking prevents conflicting updates

- [ ] **Test: PUT with Partial Updates**
  - **Input**: Announcement UUID and partial update object
  - **Expected**: 200 OK, only specified fields updated
  - **Validate**: Unspecified fields remain unchanged

- [ ] **Test: PUT with Invalid Data**
  - **Input**: Update object with invalid field values
  - **Expected**: 400 Bad Request, validation error details
  - **Validate**: Original announcement remains unchanged

- [ ] **Test: PUT Non-Existent Announcement**
  - **Input**: Non-existent UUID with valid update data
  - **Expected**: 404 Not Found, resource not found error
  - **Validate**: No new resource created, clear error message

#### Bulk Status Updates

- [ ] **Test: PUT /api/v1/announcements/activate**
  - **Input**: Array of announcement IDs to activate
  - **Expected**: 200 OK, activation confirmation
  - **Validate**: All specified announcements marked as active
  - **Transaction**: All activations succeed or all fail atomically

- [ ] **Test: PUT /api/v1/announcements/deactivate**
  - **Input**: Array of announcement IDs to deactivate
  - **Expected**: 200 OK, deactivation confirmation
  - **Validate**: All specified announcements marked as inactive
  - **Audit**: Deactivation timestamps recorded correctly

### Delete Operations (DELETE)

#### Soft Delete Implementation

- [ ] **Test: DELETE /api/v1/announcements/{id}**
  - **Input**: Valid announcement UUID
  - **Expected**: 204 No Content, announcement soft deleted
  - **Validate**: Announcement marked inactive but remains in database
  - **Verify**: Announcement no longer appears in active listings

- [ ] **Test: DELETE Non-Existent Announcement**
  - **Input**: Non-existent UUID
  - **Expected**: 404 Not Found, resource not found error
  - **Validate**: Error response consistent with GET 404 responses

- [ ] **Test: DELETE Already Deleted Announcement**
  - **Input**: UUID of already deleted/inactive announcement
  - **Expected**: 410 Gone or 404 Not Found (based on API design)
  - **Validate**: Clear indication of resource state

---

## Advanced API Features Testing

### Metadata and Extended Properties

#### Metadata Operations

- [ ] **Test: POST/PUT with Complex Metadata**
  - **Input**: Announcement with nested JSON metadata
  - **Expected**: 200/201 response, metadata stored and retrievable
  - **Validate**: JSON metadata properly serialized/deserialized

- [ ] **Test: GET with Metadata Filtering**
  - **Input**: Query parameters for metadata-based filtering
  - **Expected**: 200 OK, filtered results based on metadata values
  - **Validate**: Complex JSON queries working correctly

#### Extended Properties Support

- [ ] **Test: Custom Field Extensions**
  - **Input**: Announcements with custom fields in metadata
  - **Expected**: Custom fields preserved through CRUD operations
  - **Validate**: Extensibility maintained without schema changes

### Scheduling and Time-Based Operations

#### Scheduled Announcement Support

- [ ] **Test: POST with Scheduled Activation**
  - **Input**: Announcement with future activation timestamp
  - **Expected**: 201 Created, announcement created but inactive until scheduled time
  - **Validate**: Scheduling metadata properly stored

- [ ] **Test: GET Scheduled Announcements**
  - **Input**: Request for announcements scheduled for future activation
  - **Expected**: 200 OK, scheduled announcements with timing information
  - **Validate**: Schedule information properly exposed via API

#### Expiration Handling

- [ ] **Test: Announcements with Expiration**
  - **Input**: Announcement with expiration timestamp
  - **Expected**: Announcement automatically deactivated after expiration
  - **Validate**: Expired announcements no longer active in listings

---

## Performance and Load Testing

### Response Time Testing

#### Individual Operation Performance

- [ ] **Test: Single Record GET Performance**
  - **Metric Target**: < 50ms response time for cached records
  - **Metric Target**: < 100ms response time for database lookups
  - **Load Test**: 100+ concurrent GET requests

- [ ] **Test: Collection GET Performance**
  - **Metric Target**: < 150ms for paginated collection retrieval
  - **Dataset**: Test with 10,000+ announcements in database
  - **Pagination**: Performance consistent across all pages

- [ ] **Test: Search Operation Performance**
  - **Metric Target**: < 200ms for complex search queries
  - **Index Usage**: Database query plans show proper index utilization
  - **Scalability**: Performance acceptable as dataset grows

#### Bulk Operation Performance

- [ ] **Test: Bulk Create Performance**
  - **Input**: 100+ announcements in single bulk request
  - **Metric Target**: < 2 seconds for bulk creation
  - **Validate**: Bulk operations significantly faster than individual calls

- [ ] **Test: Bulk Update Performance**
  - **Input**: Status updates for 500+ announcements
  - **Metric Target**: < 1 second for bulk status updates
  - **Database**: Batch operations properly optimized

### Concurrent Load Testing

#### High Concurrency Scenarios

- [ ] **Test: Concurrent Read Operations**
  - **Load**: 50+ simultaneous GET requests
  - **Validate**: No performance degradation, all requests complete successfully
  - **Resource Usage**: Server resource usage remains within limits

- [ ] **Test: Mixed Read/Write Load**
  - **Load**: Simultaneous GET, POST, PUT, DELETE operations
  - **Validate**: No data corruption, consistent response times
  - **Isolation**: Write operations don't significantly impact read performance

- [ ] **Test: Sustained Load Testing**
  - **Duration**: Continuous API load for 30+ minutes
  - **Validate**: Memory usage stable, no memory leaks detected
  - **Performance**: Response times remain consistent throughout test

---

## Security Testing

### Input Validation and Sanitization

#### Injection Attack Prevention

- [ ] **Test: SQL Injection Attempts**
  - **Input**: Malicious SQL in content, search queries, and parameters
  - **Expected**: Input properly sanitized, no database compromise
  - **Validate**: Database queries use parameterized statements

- [ ] **Test: XSS Prevention**
  - **Input**: JavaScript code in announcement content
  - **Expected**: Content properly escaped in JSON responses
  - **Validate**: No script execution possible in consuming applications

- [ ] **Test: JSON Injection Attempts**
  - **Input**: Malformed JSON with injection attempts
  - **Expected**: JSON parsing errors handled gracefully
  - **Validate**: No server errors or information leakage

#### Input Length and Format Validation

- [ ] **Test: Buffer Overflow Prevention**
  - **Input**: Extremely long strings in all fields
  - **Expected**: Input length validation prevents overflow
  - **Validate**: Error messages indicate specific length limits

- [ ] **Test: Special Character Handling**
  - **Input**: Unicode characters, emojis, control characters
  - **Expected**: Characters properly stored and retrieved
  - **Validate**: UTF-8 encoding maintained throughout API operations

### Authentication Security

#### API Key Security Tests

- [ ] **Test: API Key Brute Force Protection**
  - **Attack**: Rapid API key guessing attempts
  - **Expected**: Rate limiting prevents brute force attacks
  - **Validate**: Account lockout or throttling after multiple failures

- [ ] **Test: API Key Exposure Prevention**
  - **Check**: API keys not logged or exposed in error messages
  - **Validate**: Log files don't contain sensitive authentication data
  - **Security**: Error responses don't leak authentication information

### Authorization Security

#### Permission Boundary Testing

- [ ] **Test: Privilege Escalation Prevention**
  - **Attack**: Attempts to access resources beyond permission level
  - **Expected**: Authorization checks prevent unauthorized access
  - **Validate**: Consistent permission enforcement across all endpoints

- [ ] **Test: Resource Ownership Validation**
  - **Setup**: User attempting to modify announcements owned by others
  - **Expected**: Ownership validation prevents unauthorized modifications
  - **Validate**: Clear error messages for authorization failures

---

## Error Handling and Response Testing

### HTTP Status Code Validation

#### Success Response Codes

- [ ] **Test: 200 OK for Successful GET Operations**
  - **Validate**: Successful retrieval operations return 200
  - **Content**: Response body contains expected data format

- [ ] **Test: 201 Created for Successful POST Operations**
  - **Validate**: Resource creation returns 201 with Location header
  - **Content**: Response body contains created resource representation

- [ ] **Test: 204 No Content for Successful DELETE Operations**
  - **Validate**: Successful deletion returns 204 with empty body
  - **Verify**: Resource actually removed/deactivated as expected

#### Error Response Codes

- [ ] **Test: 400 Bad Request for Invalid Input**
  - **Scenarios**: Invalid JSON, missing required fields, invalid data types
  - **Validate**: Descriptive error messages in response body
  - **Format**: Error response format consistent across endpoints

- [ ] **Test: 401 Unauthorized for Authentication Failures**
  - **Scenarios**: Missing API key, invalid API key
  - **Validate**: Clear authentication error messages
  - **Security**: No sensitive information leaked in error responses

- [ ] **Test: 403 Forbidden for Authorization Failures**
  - **Scenarios**: Valid authentication but insufficient permissions
  - **Validate**: Clear distinction between authentication and authorization errors

- [ ] **Test: 404 Not Found for Non-Existent Resources**
  - **Scenarios**: Invalid IDs, non-existent endpoints
  - **Validate**: Consistent 404 response format and messaging

- [ ] **Test: 429 Too Many Requests for Rate Limiting**
  - **Scenario**: Exceed configured rate limits
  - **Expected**: 429 response with Retry-After header
  - **Validate**: Rate limiting properly implemented and communicated

#### Server Error Responses

- [ ] **Test: 500 Internal Server Error Handling**
  - **Scenario**: Simulate server errors (database unavailable, etc.)
  - **Expected**: 500 response with generic error message
  - **Validate**: No sensitive system information exposed

- [ ] **Test: 503 Service Unavailable for Maintenance**
  - **Scenario**: Service in maintenance mode
  - **Expected**: 503 response with appropriate retry guidance
  - **Validate**: Graceful degradation during service maintenance

### Error Response Format Consistency

- [ ] **Test: Consistent Error Response Structure**
  - **Validate**: All error responses follow same JSON structure

  ```json
  {
    "error": {
      "code": "VALIDATION_ERROR",
      "message": "Descriptive error message",
      "details": ["Specific field errors"],
      "timestamp": "2025-08-23T10:30:00Z"
    }
  }
  ```

---

## Content-Type and Data Format Testing

### Request/Response Content Types

#### JSON Content Type Handling

- [ ] **Test: Correct Content-Type Headers**
  - **Request**: `Content-Type: application/json`
  - **Response**: `Content-Type: application/json; charset=utf-8`
  - **Validate**: Headers properly set and validated

- [ ] **Test: Incorrect Content-Type Handling**
  - **Request**: Wrong Content-Type header (text/plain, etc.)
  - **Expected**: 415 Unsupported Media Type response
  - **Validate**: Clear error message about expected content type

#### Character Encoding

- [ ] **Test: UTF-8 Character Encoding**
  - **Input**: Announcements with international characters
  - **Validate**: Characters properly encoded in JSON responses
  - **Round-Trip**: Characters identical after create/retrieve cycle

---

## API Documentation and Discoverability

### OpenAPI/Swagger Documentation

- [ ] **Test: API Documentation Accessibility**
  - **URL**: Documentation endpoint accessible (e.g., `/api/docs`)
  - **Validate**: Complete endpoint documentation available
  - **Interactive**: API explorer functionality working

- [ ] **Test: Documentation Accuracy**
  - **Validate**: All endpoints documented with correct parameters
  - **Examples**: Request/response examples work correctly
  - **Schema**: Response schemas match actual API responses

### API Versioning

- [ ] **Test: Version Header Support**
  - **Header**: API version specified in requests
  - **Validate**: Correct API version handling and routing
  - **Backward Compatibility**: Multiple API versions supported if applicable

---

## Cross-Origin Resource Sharing (CORS)

### CORS Configuration Testing

- [ ] **Test: CORS Preflight Requests**
  - **Method**: OPTIONS request with CORS headers
  - **Expected**: Proper CORS response headers
  - **Validate**: Allowed origins, methods, and headers configured

- [ ] **Test: Cross-Origin Actual Requests**
  - **Setup**: Request from different origin (web application)
  - **Validate**: Requests succeed with proper CORS headers
  - **Security**: Only authorized origins allowed

---

## Integration Testing with VS Code Tasks

### Development Workflow Integration

#### API Testing via Command Palette

- [ ] **Test: PowerShell API Test Script**
  - **Task**: Execute `Test-AnnouncementsAPI.ps1` via terminal
  - **Parameters**: `-Detail -CreateTestData -HttpOnly`
  - **Validate**: All test scenarios execute successfully
  - **Output**: Clear pass/fail results with detailed error information

- [ ] **Test: API Performance Monitoring**
  - **Task**: Custom performance test script execution
  - **Validate**: Response time metrics collected and analyzed
  - **Alerting**: Performance degradation detected and reported

#### Service Integration Testing

- [ ] **Test: End-to-End API to Database Flow**
  - **Process**: POST announcement via API → Query database directly
  - **Validate**: API operations properly reflected in database
  - **Task Integration**: `Query Console - Plugin Messages` shows operation logs

- [ ] **Test: Service Restart API Availability**
  - **Process**: `Restart Server` task → API availability check
  - **Validate**: API endpoints accessible after service restart
  - **Recovery**: Service properly initialized with all endpoints functional

---

## Final API Validation

### Production Readiness Tests

- [ ] **Test: Production Configuration Validation**
  - **Setup**: Production-like API configuration
  - **Validate**: Security headers, rate limiting, monitoring enabled
  - **Performance**: API meets production SLA requirements

- [ ] **Test: SSL/HTTPS Configuration**
  - **Setup**: HTTPS endpoint with proper SSL certificates
  - **Validate**: Secure connection established, certificate valid
  - **Security**: HTTP redirects to HTTPS, secure headers present

### Operational Readiness Tests

- [ ] **Test: Health Check Endpoints**
  - **Endpoints**: `/health`, `/health/ready`, `/health/live`
  - **Validate**: Health status accurately reflects service state
  - **Monitoring**: Health checks suitable for load balancer integration

- [ ] **Test: Metrics and Monitoring Integration**
  - **Endpoints**: `/metrics` for application metrics
  - **Validate**: Key performance indicators exposed
  - **Integration**: Metrics compatible with monitoring systems

---

## Notes and Issue Tracking

**API Performance Metrics:**

- Document baseline response times for all endpoint categories
- Record API throughput capabilities under load
- Note any performance optimization changes and their impact

**Security Testing Results:**

- Document all security tests performed and results
- Record any vulnerabilities discovered and remediation steps
- Note security best practices implemented

**Integration Issues:**

- Document any API integration challenges encountered
- Record solutions for cross-origin resource sharing issues
- Note any client-specific integration requirements

---

**REST API Testing Completed By:** _________________  
**Date:** _________________  
**API Version Tested:** _________________  
**Next Review Date:** _________________

This comprehensive REST API testing checklist ensures the RVNKCore announcements API provides reliable, secure, and performant HTTP/HTTPS endpoints for web integration and external access.
