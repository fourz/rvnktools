# Endpoint Testing and Validation Framework

**Checklist ID**: 01-comprehensive-endpoint-testing  
**Priority**: Critical  
**Updated**: August 23, 2025

## Pre-Testing Setup

### Environment Validation

- [ ] **RVNKCore Server Running**
  - [ ] Server startup completed without errors
  - [ ] Database connectivity verified
  - [ ] Plugin loaded successfully

- [ ] **API Access Configuration**
  - [ ] API key configured and valid
  - [ ] SSL certificates properly installed
  - [ ] Port configurations verified (8080 HTTP, 8081 HTTPS)

- [ ] **Testing Tools Ready**
  - [ ] PowerShell API testing scripts available
  - [ ] Test data prepared for various scenarios
  - [ ] Monitoring tools active for performance tracking

## HTTP Protocol Testing

### HTTP Connectivity (Port 8080)

- [ ] **Basic Connectivity Test**
  - [ ] Can establish connection to http://localhost:8080
  - [ ] Server responds with appropriate HTTP status codes
  - [ ] No connection timeout or refused connection errors

- [ ] **API Key Authentication**
  - [ ] Valid API key allows access
  - [ ] Invalid API key returns 401/403 status
  - [ ] Missing API key returns proper error response

- [ ] **CORS Headers**
  - [ ] Appropriate CORS headers present in responses
  - [ ] Cross-origin requests handled correctly
  - [ ] Preflight OPTIONS requests supported

### HTTPS Connectivity (Port 8081)

- [ ] **SSL/TLS Connection**
  - [ ] Can establish secure connection to https://localhost:8081
  - [ ] Certificate validation (or proper bypass for dev environment)
  - [ ] TLS handshake completes successfully

- [ ] **Security Headers**
  - [ ] Appropriate security headers in HTTPS responses
  - [ ] No sensitive information exposed in headers
  - [ ] Proper content type headers set

## Core CRUD Operations Testing

### CREATE Operations (POST)

#### Basic Announcement Creation

- [ ] **POST /api/v1/announcements**
  - [ ] **Valid Request**: Creates announcement with all required fields
    ```json
    {
      "title": "Test Announcement",
      "message": "This is a test message",
      "type": "BROADCAST",
      "active": true,
      "intervalSeconds": 300
    }
    ```
  - [ ] **Response Validation**: Returns 201 Created with announcement object
  - [ ] **Database Validation**: Announcement stored in database correctly
  - [ ] **ID Generation**: Unique ID assigned and returned

- [ ] **Invalid Request Handling**
  - [ ] Missing required fields returns 400 Bad Request
  - [ ] Invalid field values return appropriate error messages
  - [ ] Malformed JSON returns 400 Bad Request

#### Bulk Import Operations

- [ ] **POST /api/v1/announcements/bulk-import**
  - [ ] **Current Status**: Should return "not implemented" (501 status)
  - [ ] **After Implementation**: Accepts array of announcements
  - [ ] **Transaction Integrity**: All succeed or all fail atomically
  - [ ] **Error Reporting**: Clear feedback on individual failures

### READ Operations (GET)

#### Individual Announcement Retrieval

- [ ] **GET /api/v1/announcements/{id}**
  - [ ] **Valid ID**: Returns announcement object with 200 OK
  - [ ] **Invalid ID**: Returns 404 Not Found
  - [ ] **Malformed ID**: Returns 400 Bad Request
  - [ ] **Response Format**: All fields present and correctly formatted

#### Collection Retrieval

- [ ] **GET /api/v1/announcements**
  - [ ] **Default Behavior**: Returns all announcements
  - [ ] **Empty Database**: Returns empty array with correct structure
  - [ ] **Large Dataset**: Handles multiple announcements efficiently
  - [ ] **Response Structure**: Includes count and announcements array

- [ ] **GET /api/v1/announcements/active**
  - [ ] **Filtering**: Only returns active announcements
  - [ ] **Accuracy**: Correctly filters based on active flag and dates
  - [ ] **Empty Result**: Handles no active announcements gracefully

#### Filtered Retrieval

- [ ] **GET /api/v1/announcements/type/{type}**
  - [ ] **Valid Type**: Returns announcements of specified type
  - [ ] **Case Sensitivity**: Handles type matching correctly
  - [ ] **Invalid Type**: Returns empty array or appropriate response
  - [ ] **Multiple Types**: Each type endpoint works independently

- [ ] **GET /api/v1/announcements/world/{world}**
  - [ ] **World Filtering**: Returns announcements targeting specific world
  - [ ] **World Name Handling**: Handles special characters in world names
  - [ ] **Global Announcements**: Includes announcements for all worlds

- [ ] **GET /api/v1/announcements/group/{group}**
  - [ ] **Group Filtering**: Returns announcements for specific permission group
  - [ ] **Group Name Validation**: Handles various group naming conventions
  - [ ] **Default Group**: Includes announcements for default/all groups

#### Search Operations

- [ ] **GET /api/v1/announcements/search?q={pattern}**
  - [ ] **Text Search**: Searches in title and message content
  - [ ] **Pattern Matching**: Supports wildcards or SQL LIKE patterns
  - [ ] **Case Insensitive**: Search works regardless of case
  - [ ] **Special Characters**: Handles search patterns with special chars
  - [ ] **Empty Query**: Handles empty or missing search parameter

#### Count and Statistics

- [ ] **GET /api/v1/announcements/count**
  - [ ] **Total Count**: Returns accurate total announcement count
  - [ ] **Response Format**: Simple JSON with count field
  - [ ] **Zero Count**: Handles empty database correctly

- [ ] **GET /api/v1/announcements/count/active**
  - [ ] **Active Count**: Returns count of only active announcements
  - [ ] **Accuracy**: Matches filtered active announcement query

- [ ] **GET /api/v1/announcements/metrics**
  - [ ] **Current Status**: Should return empty response (needs implementation)
  - [ ] **After Implementation**: Returns comprehensive metrics object
  - [ ] **Performance**: Calculates metrics efficiently
  - [ ] **Data Accuracy**: All metric values correct and up-to-date

### UPDATE Operations (PUT)

#### Individual Updates

- [ ] **PUT /api/v1/announcements/{id}**
  - [ ] **Current Status**: Should return "not implemented" (501 status)
  - [ ] **After Implementation**: Updates announcement with provided data
  - [ ] **Partial Updates**: Supports updating only specific fields
  - [ ] **Validation**: Validates updated data before saving
  - [ ] **Response**: Returns updated announcement object

#### Status Management

- [ ] **PUT /api/v1/announcements/{id}/activate**
  - [ ] **Activation**: Sets announcement active status to true
  - [ ] **Response**: Returns success confirmation
  - [ ] **Database**: Active flag updated correctly
  - [ ] **Already Active**: Handles already active announcements gracefully

- [ ] **PUT /api/v1/announcements/{id}/deactivate**
  - [ ] **Deactivation**: Sets announcement active status to false
  - [ ] **Response**: Returns success confirmation
  - [ ] **Database**: Active flag updated correctly
  - [ ] **Already Inactive**: Handles already inactive announcements gracefully

#### Bulk Operations

- [ ] **PUT /api/v1/announcements/bulk-activate**
  - [ ] **Current Status**: Not implemented (endpoint doesn't exist)
  - [ ] **After Implementation**: Activates multiple announcements
  - [ ] **ID Array**: Accepts array of announcement IDs
  - [ ] **Transaction**: All succeed or all fail atomically

- [ ] **PUT /api/v1/announcements/bulk-deactivate**
  - [ ] **Current Status**: Not implemented (endpoint doesn't exist)
  - [ ] **After Implementation**: Deactivates multiple announcements
  - [ ] **Error Handling**: Clear feedback on individual failures

### DELETE Operations

#### Individual Deletion

- [ ] **DELETE /api/v1/announcements/{id}**
  - [ ] **Valid ID**: Deletes announcement and returns 204 No Content
  - [ ] **Invalid ID**: Returns 404 Not Found
  - [ ] **Database Cleanup**: Announcement removed from database
  - [ ] **Cascade Effects**: Related data handled appropriately

#### Bulk Deletion

- [ ] **DELETE /api/v1/announcements/bulk** (if implemented)
  - [ ] **ID Array**: Accepts array of announcement IDs for deletion
  - [ ] **Transaction**: All succeed or all fail atomically
  - [ ] **Error Reporting**: Clear feedback on individual failures

## Error Handling and Edge Cases

### Input Validation

- [ ] **Malformed JSON**: Returns 400 Bad Request with clear error message
- [ ] **Invalid Field Types**: Rejects incorrect data types appropriately
- [ ] **Missing Required Fields**: Clear error messages for missing data
- [ ] **Field Length Limits**: Enforces reasonable field length constraints

### Database Error Handling

- [ ] **Connection Failures**: Graceful handling of database connectivity issues
- [ ] **Constraint Violations**: Appropriate error responses for database constraints
- [ ] **Transaction Failures**: Proper rollback and error reporting

### Performance and Load

- [ ] **Large Datasets**: Handles databases with many announcements efficiently
- [ ] **Concurrent Requests**: Multiple simultaneous requests handled correctly
- [ ] **Memory Usage**: No memory leaks or excessive resource consumption
- [ ] **Response Times**: All endpoints respond within acceptable time limits

## Success Criteria

### Functionality Metrics

- [ ] **All endpoints return proper HTTP status codes**
- [ ] **All responses include appropriate JSON content types**
- [ ] **Error responses include helpful error messages**
- [ ] **Database state remains consistent after all operations**

### Performance Metrics

- [ ] **Individual queries complete within 2 seconds**
- [ ] **Bulk operations complete within 10 seconds**
- [ ] **No timeout errors under normal load**
- [ ] **Memory usage remains stable over time**

### API Compliance

- [ ] **RESTful conventions followed consistently**
- [ ] **HTTP methods used appropriately**
- [ ] **Response formats consistent across endpoints**
- [ ] **Authentication and authorization working properly**

This comprehensive testing framework ensures all announcement endpoints function correctly and meet production quality standards.
