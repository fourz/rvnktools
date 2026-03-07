# COPILOT-INSTRUCTIONS.md

## Project-Specific Development Guidelines

### Announcement Endpoint Analysis Project Context

This metamake project focuses on **identifying and fixing non-functional announcement REST API endpoints** in the RVNKCore system. Follow these specific guidelines when working on this project:

### Issue Resolution Priorities

1. **HTTP Protocol Connectivity** (Critical)
   - HTTP server on port 8080 not responding
   - HTTPS on port 8081 working correctly
   - Dual protocol support required for production

2. **Missing Endpoint Implementations** (High)
   - Metrics endpoint returning empty responses
   - Stub implementations in controller methods
   - Service layer methods not fully implemented

3. **API Specification Compliance** (Medium)
   - Response format consistency
   - Error handling standardization
   - HTTP status code accuracy

### Development Approach

#### When Implementing Fixes

- **Start with service layer**: Ensure business logic is complete before fixing controllers
- **Maintain async patterns**: All database operations must use CompletableFuture
- **Use proper DTOs**: Create appropriate data transfer objects for complex responses
- **Follow RVNK patterns**: Adhere to established logging, error handling, and architecture patterns

#### When Testing Solutions

- **Test both protocols**: Verify HTTP and HTTPS functionality
- **Use provided testing tools**: Leverage PowerShell API testing scripts
- **Validate end-to-end**: Ensure database integration works correctly
- **Check performance**: Monitor response times and resource usage

### Code Quality Standards

#### Controller Implementation

```java
// Use proper async handling in controllers
future.thenAccept(result -> {
    try {
        String json = buildResponse(result);
        sendSuccessResponse(response, json);
    } catch (IOException e) {
        logger.error("Error sending response", e);
    }
}).exceptionally(ex -> {
    try {
        sendErrorResponse(response, 500, "Operation failed: " + ex.getMessage());
    } catch (IOException e) {
        logger.error("Error sending error response", e);
    }
    return null;
});
```

#### Service Implementation

```java
// Use proper async service patterns
@Override
public CompletableFuture<ResultDTO> performOperation(String parameter) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            // Validate input
            validateParameter(parameter);
            
            // Execute operation
            ResultDTO result = repository.performOperation(parameter).join();
            
            // Log success
            logger.info("Operation completed successfully for: {}", parameter);
            
            return result;
        } catch (Exception e) {
            logger.error("Operation failed for parameter: {}", parameter, e);
            throw new RuntimeException("Operation failed", e);
        }
    }, executorService);
}
```

### Testing Integration

Use the existing testing framework:

```powershell
# Test specific endpoints
.\Test-RestRVNKCoreAPI.ps1 -Tests announcements -HttpsOnly -Detail

# Test custom endpoints
.\query-server-DEV.ps1 command "custom /api/v1/announcements/metrics GET -IgnoreSSLErrors"
```

### Documentation Requirements

- **Update API documentation** when adding new endpoints
- **Document error codes** and response formats
- **Provide usage examples** for complex operations
- **Update metamake project status** as issues are resolved

### Success Verification

Each fix must pass these verification steps:

1. **Unit testing**: Service methods work correctly in isolation
2. **Integration testing**: End-to-end request/response flow functional
3. **Performance testing**: Response times within acceptable limits
4. **Error testing**: Proper error handling for edge cases

### Project Status Updates

Update the following files as work progresses:

- `ROADMAP.md`: Mark completed milestones and update timelines
- `features/01-endpoint-inventory.md`: Update endpoint status
- `validation/01-comprehensive-endpoint-testing.md`: Check off completed tests

### Collaboration Guidelines

When working with this project:

- **Commit frequently** with clear messages describing what was fixed
- **Test immediately** after each change using provided tools
- **Document issues** discovered during implementation
- **Update metamake documentation** to reflect current status

This project will establish the announcement REST API as a fully functional, production-ready system supporting all required operations for web dashboard integration and external system communication.
