# RVNKCore Announcements API VS Code Tasks Configuration

This file provides additional VS Code tasks specifically designed for testing and validating the RVNKCore Announcements API implementation. These tasks integrate with the existing development workflow and provide comprehensive testing capabilities.

## Additional Tasks for tasks.json

Add these tasks to your `.vscode/tasks.json` file to enable comprehensive API testing through the Command Palette:

```json
{
    "label": "Test Announcements API - HTTP",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "${workspaceFolder}\\metamake\\projects\\07-rvnkcore-announcements-api\\validation\\Test-AnnouncementsAPI.ps1",
        "-HttpOnly",
        "-Detail"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Test RVNKCore Announcements API HTTP endpoints with detailed output.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Test Announcements API - HTTPS",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "${workspaceFolder}\\metamake\\projects\\07-rvnkcore-announcements-api\\validation\\Test-AnnouncementsAPI.ps1",
        "-HttpsOnly",
        "-IgnoreSSLErrors",
        "-Detail"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Test RVNKCore Announcements API HTTPS endpoints with SSL validation disabled for development.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Test Announcements API - Full Suite",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "${workspaceFolder}\\metamake\\projects\\07-rvnkcore-announcements-api\\validation\\Test-AnnouncementsAPI.ps1",
        "-Detail",
        "-CreateTestData"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Run complete Announcements API test suite with test data creation and detailed reporting.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Test Announcements API - Performance",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "${workspaceFolder}\\metamake\\projects\\07-rvnkcore-announcements-api\\validation\\Test-AnnouncementsAPI.ps1",
        "-HttpOnly",
        "-CreateTestData"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Performance-focused API testing with test data creation for load testing.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Validate Announcements Database Schema",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        "$query = \"SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'rvnk_announcements' ORDER BY ORDINAL_POSITION;\"; if ($env:RVNK_MYSQL_PASSWORD) { mysql -h localhost -u root -p$env:RVNK_MYSQL_PASSWORD rvnkcore_dev -e \"$query\" } else { Write-Host 'MySQL password not set. Use Set MySQL Password task first.' -ForegroundColor Yellow }"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Validate the announcements database schema structure and column definitions.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Count Announcements in Database",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        "$countQuery = \"SELECT COUNT(*) as total_announcements, COUNT(CASE WHEN active = 1 THEN 1 END) as active_announcements FROM rvnk_announcements;\"; if ($env:RVNK_MYSQL_PASSWORD) { mysql -h localhost -u root -p$env:RVNK_MYSQL_PASSWORD rvnkcore_dev -e \"$countQuery\" } else { Write-Host 'MySQL password not set. Use Set MySQL Password task first.' -ForegroundColor Yellow }"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Get count of total and active announcements in the database.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Test Announcements Service Integration",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "C:\\tools\\rvnktools\\.vscode\\query-server-DEV.ps1",
        "command",
        "rvnkcore service-status announcements"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Test RVNKCore announcements service integration and status via server command.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Monitor Announcements Service Logs",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "C:\\tools\\rvnktools\\.vscode\\query-server-DEV.ps1",
        "console",
        "100",
        "-FilterText",
        "announcement"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Monitor recent console logs for announcement-related messages.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Validate Announcements Cache Performance",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "C:\\tools\\rvnktools\\.vscode\\query-server-DEV.ps1",
        "command",
        "rvnkcore cache-stats announcements"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Check announcements service cache performance statistics.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Test Database Connection Pool",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "C:\\tools\\rvnktools\\.vscode\\query-server-DEV.ps1",
        "command",
        "rvnkcore db-pool-stats"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "test",
    "detail": "Test database connection pool statistics and health.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Backup Announcements YAML Before Migration",
    "type": "shell",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        "$backupDir = \"${workspaceFolder}\\backup\\announcements\\$(Get-Date -Format 'yyyy-MM-dd_HH-mm-ss')\"; New-Item -ItemType Directory -Path $backupDir -Force; Copy-Item \"${workspaceFolder}\\*announcements*.yml\" $backupDir -Recurse -ErrorAction SilentlyContinue; Write-Host \"YAML files backed up to: $backupDir\" -ForegroundColor Green"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "build",
    "detail": "Backup existing announcements YAML files before migration.",
    "runOptions": {
        "runOn": "default"
    }
},
{
    "label": "Run Announcements Migration",
    "type": "shell",
    "dependsOn": "Backup Announcements YAML Before Migration",
    "command": "powershell",
    "args": [
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "C:\\tools\\rvnktools\\.vscode\\query-server-DEV.ps1",
        "command",
        "rvnktools migrate-announcements --from-yaml --verify"
    ],
    "options": {
        "cwd": "${workspaceFolder}"
    },
    "group": "build",
    "detail": "Run announcements YAML to database migration with verification.",
    "runOptions": {
        "runOn": "default"
    }
}
```

## Input Variables for tasks.json

Add these input variables to your `.vscode/tasks.json` file to support interactive tasks:

```json
"inputs": [
    {
        "id": "apiTestType",
        "description": "Select API test type",
        "default": "http",
        "type": "pickString",
        "options": [
            {
                "label": "HTTP Only",
                "value": "http"
            },
            {
                "label": "HTTPS Only", 
                "value": "https"
            },
            {
                "label": "Both HTTP and HTTPS",
                "value": "both"
            }
        ]
    },
    {
        "id": "testDetailLevel",
        "description": "Select test detail level",
        "default": "normal",
        "type": "pickString",
        "options": [
            {
                "label": "Normal Output",
                "value": "normal"
            },
            {
                "label": "Detailed Output",
                "value": "detail"
            },
            {
                "label": "Minimal Output",
                "value": "minimal"
            }
        ]
    },
    {
        "id": "migrationAction",
        "description": "Select migration action",
        "default": "migrate",
        "type": "pickString",
        "options": [
            {
                "label": "Migrate YAML to Database",
                "value": "migrate"
            },
            {
                "label": "Verify Migration Results",
                "value": "verify"
            },
            {
                "label": "Rollback Migration",
                "value": "rollback"
            }
        ]
    }
]
```

## Keyboard Shortcuts for tasks.json

Add these keyboard shortcuts to your VS Code keybindings for quick access to testing tasks:

```json
[
    {
        "key": "ctrl+shift+f9",
        "command": "workbench.action.tasks.runTask",
        "args": "Test Announcements API - HTTP"
    },
    {
        "key": "ctrl+shift+f10",
        "command": "workbench.action.tasks.runTask", 
        "args": "Test Announcements API - Full Suite"
    },
    {
        "key": "ctrl+shift+f11",
        "command": "workbench.action.tasks.runTask",
        "args": "Monitor Announcements Service Logs"
    },
    {
        "key": "ctrl+shift+f12",
        "command": "workbench.action.tasks.runTask",
        "args": "RVNKTools Debug"
    }
]
```

## Task Usage Workflow

### Development Workflow

1. **Build and Deploy**: Use existing `Build & Deploy` task
2. **Test API Integration**: Run `Test Announcements Service Integration`
3. **Validate Database**: Run `Validate Announcements Database Schema`
4. **Test HTTP Endpoints**: Run `Test Announcements API - HTTP`
5. **Monitor Logs**: Use `Monitor Announcements Service Logs` for real-time monitoring

### Performance Testing Workflow

1. **Create Test Data**: Run `Test Announcements API - Performance`
2. **Check Cache Performance**: Run `Validate Announcements Cache Performance`
3. **Monitor Connection Pool**: Run `Test Database Connection Pool`
4. **Full Load Test**: Run `Test Announcements API - Full Suite`

### Migration Testing Workflow

1. **Backup Data**: Run `Backup Announcements YAML Before Migration`
2. **Clean Database**: Run `Clean MySQL Database - DEV` if needed
3. **Run Migration**: Run `Run Announcements Migration`
4. **Verify Results**: Run `Count Announcements in Database`
5. **Test Functionality**: Run `Test Announcements API - Full Suite`

## Task Integration with Checklists

### Mapping Tasks to Validation Checklists

**Service Implementation Checklist**:

- Use `Test Announcements Service Integration` for service discovery tests
- Use `RVNKTools Debug` for service registration verification
- Use `Monitor Announcements Service Logs` for async operation monitoring

**Database Integration Checklist**:

- Use `Validate Announcements Database Schema` for schema validation
- Use `Count Announcements in Database` for data verification
- Use `Test Database Connection Pool` for connection testing

**REST API Testing Checklist**:

- Use `Test Announcements API - HTTP/HTTPS` for endpoint validation
- Use `Test Announcements API - Full Suite` for comprehensive testing
- Use `Test Announcements API - Performance` for load testing

**Migration Validation Checklist**:

- Use `Backup Announcements YAML Before Migration` for pre-migration backup
- Use `Run Announcements Migration` for migration execution
- Use validation tasks for post-migration verification

## Troubleshooting Common Issues

### API Test Failures

1. **Connection Refused**: Check if server is running with `Query Server Status`
2. **Authentication Errors**: Verify API key configuration
3. **SSL Certificate Errors**: Use `-IgnoreSSLErrors` flag for development testing

### Database Issues

1. **Schema Not Found**: Run database migration first
2. **Connection Errors**: Check MySQL password with `Verify MySQL Password`
3. **Permission Issues**: Verify database user permissions

### Service Integration Issues

1. **Service Not Found**: Check service registration in RVNKCore
2. **Cache Issues**: Clear cache and restart service
3. **Log Monitoring**: Use console monitoring tasks for real-time debugging

This comprehensive task configuration provides a complete testing framework integrated with VS Code's Command Palette, enabling efficient development, testing, and validation workflows for the RVNKCore Announcements API implementation.
