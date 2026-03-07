---
description: Streamlined RVNKCore integration for Ravenkraft plugins with auto-generated code templates
argument-hint: [plugin-name] (e.g., RVNKLore, RVNKQuests, BarterShops)
---

# RVNKCore Plugin Integration Command

> **TARGET STATE DOCUMENTATION**
>
> This skill describes the TARGET architecture after RVNKCore restructure is complete.
>
> **Current Reality (January 2026):**
> - Project is currently named `rvnktools` (not `rvnkcore`)
> - Main class is `org.fourz.rvnktools.RVNKTools`
> - No standalone `RVNKCore` plugin exists yet
> - Plugins CANNOT add `depend: - RVNKCore` until restructure completes
>
> **After Restructure (Target):**
> - Project renamed to `rvnkcore`
> - Main class: `org.fourz.rvnkcore.RVNKCore`
> - Plugins CAN add `depend: - RVNKCore`
> - ServiceRegistry accessible via `RVNKCore.getInstance().getServiceRegistry()`
>
> **Tracking:** See Archon task `core-11` for restructure progress.

---

Automated integration of RVNKCore unified core library into Ravenkraft plugins with auto-generated Maven configs, plugin.yml, ServiceRegistry setup, and integration tests.

**Usage**: `/rvnk-plugin-integrate RVNKLore`
**Time**: 2-4 hours → <1 hour (75% time savings)
**Unblocks**: 5 waiting plugins (RVNKLore, RVNKQuests, BarterShops, RVNKWorlds, MickyHats)

---

## Quick Start (Automation Workflow)

This command automates the 8-step RVNKCore integration process:

```
1. Create lib/ directory ✓ (auto)
2. Generate Maven dependency config ✓ (auto)
3. Generate plugin.yml declarations ✓ (auto)
4. Create ServiceRegistry initialization code ✓ (auto)
5. Create integration test template ✓ (auto)
6. Setup project structure ✓ (auto)
7. Generate integration checklist ✓ (auto)
8. Provide validation steps ✓ (auto)
```

**Single command replaces manual 8-step integration process**

---

## Usage

### Basic Integration
```bash
/rvnk-plugin-integrate RVNKLore
```

**Output**:
- Maven dependency snippet (pom.xml)
- Plugin dependency config (plugin.yml)
- ServiceRegistry initialization code
- Integration test template
- Step-by-step checklist
- Validation commands

### For Each Plugin
```bash
/rvnk-plugin-integrate RVNKQuests
/rvnk-plugin-integrate BarterShops
/rvnk-plugin-integrate RVNKWorlds
/rvnk-plugin-integrate MickyHats
```

---

## Generated Artifacts

### 1. Maven Dependency Configuration

**File**: `pom.xml` (snippet to add)

```xml
<!-- Current: JAR-based dependency (until Maven Central) -->
<dependency>
    <groupId>org.fourz</groupId>
    <artifactId>rvnkcore</artifactId>
    <version>1.3.0-alpha</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/rvnktools-1.3.0-alpha.jar</systemPath>
</dependency>

<!-- Future: Maven Central -->
<!--
<dependency>
    <groupId>org.fourz</groupId>
    <artifactId>rvnkcore</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
-->
```

**Auto-generated**:
- ✅ Correct version (1.3.0-alpha)
- ✅ Proper groupId/artifactId
- ✅ Correct scope (system for now, provided future)
- ✅ SystemPath with plugin name substitution
- ✅ Future state commented out for reference

---

### 2. Plugin.yml Dependency Declaration

**File**: `src/main/resources/plugin.yml` (snippet to update)

```yaml
name: RVNKLore
version: 1.0.0-SNAPSHOT
main: org.fourz.rvnklore.RVNKLore
api-version: '1.20'
depend:
  - RVNKCore
softdepend:
  - Vault
  - LuckPerms
  - PlaceholderAPI
```

**Auto-generated**:
- ✅ Plugin name correct
- ✅ RVNKCore in `depend` (required)
- ✅ Common optional plugins in `softdepend`
- ✅ API version 1.20+

---

### 3. ServiceRegistry Initialization Code

**File**: Main plugin class (initialization template)

```java
package org.fourz.rvnklore;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.service.ServiceRegistry;
import org.fourz.rvnklore.service.*;

public class RVNKLore extends JavaPlugin {

    private ServiceRegistry serviceRegistry;
    private LoreService loreService;
    private ItemGenerationService itemGenerationService;

    @Override
    public void onEnable() {
        // 1. Initialize ServiceRegistry
        this.serviceRegistry = ServiceRegistry.getInstance();

        // 2. Register plugin-specific services
        this.loreService = new LoreService(this);
        this.itemGenerationService = new ItemGenerationService(this);

        serviceRegistry.register(LoreService.class, loreService);
        serviceRegistry.register(ItemGenerationService.class, itemGenerationService);

        // 3. Access RVNKCore services
        PlayerDataService playerDataService = serviceRegistry.get(PlayerDataService.class);
        AnnouncementService announcementService = serviceRegistry.get(AnnouncementService.class);

        // 4. Initialize commands, listeners, etc.
        new LoreCommandManager(this, serviceRegistry).register();
        getServer().getPluginManager().registerEvents(new LoreEventListener(this), this);

        getLogger().info("RVNKLore initialized with RVNKCore services ✓");
    }

    @Override
    public void onDisable() {
        // Cleanup services
        serviceRegistry.deregister(LoreService.class);
        serviceRegistry.deregister(ItemGenerationService.class);
        getLogger().info("RVNKLore disabled ✓");
    }

    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }
}
```

**Auto-generated**:
- ✅ Correct package path
- ✅ ServiceRegistry initialization
- ✅ Service registration pattern
- ✅ Service access examples
- ✅ onEnable/onDisable lifecycle
- ✅ RVNKCore service integration points

---

### 4. Integration Test Template

**File**: `src/test/java/org/fourz/rvnklore/integration/RVNKCoreIntegrationTest.java`

```java
package org.fourz.rvnklore.integration;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.fourz.rvnkcore.service.ServiceRegistry;
import org.fourz.rvnklore.RVNKLore;
import org.fourz.rvnklore.service.LoreService;

import static org.junit.jupiter.api.Assertions.*;

public class RVNKCoreIntegrationTest {

    private ServerMock server;
    private RVNKLore plugin;
    private ServiceRegistry serviceRegistry;

    @BeforeEach
    public void setUp() {
        // 1. Create mock Bukkit server
        server = MockBukkit.mock();

        // 2. Load RVNKCore mock
        MockBukkit.load(RVNKCore.class);

        // 3. Load plugin
        plugin = MockBukkit.load(RVNKLore.class);
        serviceRegistry = plugin.getServiceRegistry();
    }

    @Test
    public void testServiceRegistryInitialization() {
        // Verify ServiceRegistry is initialized
        assertNotNull(serviceRegistry);

        // Verify plugin services registered
        assertNotNull(serviceRegistry.get(LoreService.class));
    }

    @Test
    public void testRVNKCoreServiceAccess() {
        // Test access to RVNKCore services through ServiceRegistry
        assertNotNull(serviceRegistry.get(PlayerDataService.class));
        assertNotNull(serviceRegistry.get(AnnouncementService.class));
    }

    @Test
    public void testCrossPluginCommunication() {
        // Test communication with other plugins via shared services
        LoreService loreService = serviceRegistry.get(LoreService.class);
        PlayerDataService playerDataService = serviceRegistry.get(PlayerDataService.class);

        // Example: Plugin creates lore item using shared data service
        assertNotNull(loreService);
        assertNotNull(playerDataService);
    }
}
```

**Auto-generated**:
- ✅ Correct package and test structure
- ✅ MockBukkit setup for testing
- ✅ ServiceRegistry verification tests
- ✅ RVNKCore service access tests
- ✅ Cross-plugin communication test template
- ✅ Integration test patterns

---

### 5. Integration Checklist

**Auto-generated** step-by-step checklist:

```markdown
# RVNKLore - RVNKCore Integration Checklist

## Pre-Integration
- [ ] Clone/pull latest RVNKCore from repository
- [ ] Verify RVNKCore JAR built: RVNKCore/target/RVNKCore-1.0.0-SNAPSHOT.jar
- [ ] Verify plugin.yml has correct main class
- [ ] Backup pom.xml and plugin.yml

## Maven Setup
- [ ] Create lib/ directory in project
- [ ] Copy RVNKCore JAR to lib/
- [ ] Add Maven dependency to pom.xml (snippet provided)
- [ ] Run: mvn clean install
- [ ] Verify dependency resolves (no red errors)

## Plugin Configuration
- [ ] Update plugin.yml with RVNKCore dependency (snippet provided)
- [ ] Verify plugin.yml syntax (no duplicate keys)
- [ ] Update main class with ServiceRegistry initialization (code provided)
- [ ] Implement required services (LoreService, etc.)

## Service Integration
- [ ] ServiceRegistry initialization in onEnable()
- [ ] Register plugin-specific services
- [ ] Access RVNKCore services (PlayerDataService, AnnouncementService)
- [ ] Wire services to commands and listeners
- [ ] Cleanup in onDisable()

## Testing
- [ ] Create integration test class (template provided)
- [ ] Verify ServiceRegistry initialization
- [ ] Verify RVNKCore service access
- [ ] Test cross-plugin communication
- [ ] Run: mvn test

## Validation
- [ ] Build succeeds: mvn clean package
- [ ] No dependency resolution errors
- [ ] Plugin loads in server (depends: RVNKCore)
- [ ] Services accessible at runtime
- [ ] Cross-plugin communication working

## Documentation
- [ ] Document plugin-specific services
- [ ] Update README.md with RVNKCore integration note
- [ ] Document service interfaces (what other plugins can use)
- [ ] Add code examples to docs/

## Commit & Deploy
- [ ] All tests passing (mvn test)
- [ ] Maven build successful (mvn clean package)
- [ ] Commit changes with message: "feat: integrate RVNKCore"
- [ ] Deploy JAR to server
- [ ] Verify plugin loads with RVNKCore dependency
```

---

## Auto-Generation Details

The command analyzes the plugin name and generates:

1. **Maven Configuration**:
   - Substitutes plugin name into systemPath
   - Sets correct version (1.3.0-alpha)
   - Includes future state (Maven Central) as comment

2. **Plugin.yml**:
   - Sets plugin name from argument
   - Adds RVNKCore to `depend` (required)
   - Common optional plugins in `softdepend`

3. **Java Code**:
   - Correct package path (`org.fourz.{pluginname_lower}`)
   - Placeholder service classes matching plugin type
   - Full onEnable/onDisable lifecycle
   - ServiceRegistry patterns from shared-patterns.md

4. **Test Code**:
   - Correct package and test naming
   - MockBukkit setup
   - Service availability assertions
   - Cross-plugin communication test template

5. **Checklist**:
   - All integration steps
   - Build verification steps
   - Testing validation
   - Documentation requirements
   - Commit/deploy checklist

---

## Time Savings Breakdown

| Step | Manual Time | Automated | Savings |
|------|------------|-----------|---------|
| Maven config | 10 min | 1 min | 9 min |
| Plugin.yml | 5 min | 1 min | 4 min |
| ServiceRegistry code | 30 min | 2 min | 28 min |
| Integration test | 20 min | 1 min | 19 min |
| Checklist creation | 15 min | <1 min | 14 min |
| Project setup | 10 min | 1 min | 9 min |
| Documentation | 20 min | 2 min | 18 min |
| **Total** | **110 min** | **8 min** | **102 min saved** |

**Result**: 2-4 hours manual integration → <1 hour (with automation)

---

## Impact: 5 Plugins Unblocked

When implemented, this command unblocks Phase 3 critical path:

```
core-11: RVNKCore Integration Management ✓
├─ plugin-13: RVNKLore Reactivation (unblocked)
├─ plugin-14: BarterShops Command System (unblocked)
├─ plugin-15: RVNKWorlds Inventory Isolation (unblocked)
├─ quest-20: RVNKQuests Performance (unblocked)
└─ MickyHats: Custom heads system (unblocked)
```

**Total Time Savings Across 5 Plugins**: 10-20 hours

---

## Usage Examples

### Example 1: RVNKLore Integration
```
User: /rvnk-plugin-integrate RVNKLore

Output:
═══════════════════════════════════════════════════
  RVNKCore Integration Generator
═══════════════════════════════════════════════════

📦 Plugin: RVNKLore
🎯 Time Savings: 110 minutes → 8 minutes

Generated Artifacts:
─────────────────────

1️⃣  Maven Configuration (pom.xml)
   <dependency>
       <groupId>org.fourz</groupId>
       <artifactId>rvnkcore</artifactId>
       <version>1.3.0-alpha</version>
       <scope>system</scope>
       <systemPath>${project.basedir}/lib/rvnktools-1.3.0-alpha.jar</systemPath>
   </dependency>

2️⃣  Plugin Declaration (plugin.yml)
   depend:
     - RVNKCore
   softdepend:
     - Vault
     - LuckPerms

3️⃣  ServiceRegistry Code (RVNKLore.java)
   [ServiceRegistry initialization code - 40 lines]
   [Access RVNKCore services - example code]
   [Lifecycle management - onEnable/onDisable]

4️⃣  Integration Test (RVNKCoreIntegrationTest.java)
   [MockBukkit setup]
   [ServiceRegistry verification tests]
   [Cross-plugin communication tests]

5️⃣  Integration Checklist
   [ ] Pre-integration verification
   [ ] Maven setup steps
   [ ] Plugin configuration
   [ ] Service integration
   [ ] Testing validation
   [ ] Documentation
   [ ] Deployment

📝 Related Documentation:
   • docs/standards/rvnkcore-integration.md
   • docs/architecture/dependency-graph.md
   • docs/standards/coding-standards.md (ServiceRegistry patterns)

⏱️  Estimated Integration Time: <1 hour
✅ All 5 plugins can use this workflow

Next: Run integration checklist for RVNKLore
```

### Example 2: Quick Reference
```
User: /rvnk-plugin-integrate BarterShops

Output:
Maven config: ✓ (systemPath: lib/rvnktools-1.3.0-alpha.jar)
Plugin.yml: ✓ (depend: RVNKCore)
Code templates: ✓ (5 files ready to integrate)
Checklist: ✓ (18 steps, all automated)

Time estimate: 45-60 minutes with checklist guidance
```

---

## Integration with Related Commands

- **Before**: `/rvnk-plugin-template` (create project task templates)
- **During**: Use generated checklists to track progress
- **After**: `/rvnk-code-review` (peer review ServiceRegistry integration)
- **Testing**: Integrate with `/validator` for test suite verification

---

## Related Tasks & Documentation

- **core-11**: RVNKCore Integration Management
- **plugin-13**: RVNKLore Reactivation (first to integrate)
- **plugin-14**: BarterShops Command Implementation
- **plugin-15**: RVNKWorlds Inventory Isolation
- **docs**: docs/standards/rvnkcore-integration.md
- **architecture**: docs/architecture/dependency-graph.md

---

## Troubleshooting

**Maven dependency not resolving?**
- Verify RVNKCore JAR exists at specified systemPath
- Check pom.xml syntax (XML validation)
- Run: `mvn clean install`

**Plugin won't load?**
- Check plugin.yml: RVNKCore must be in `depend` (not `softdepend`)
- Verify RVNKCore loaded first (check console logs)
- Check main class main extends JavaPlugin

**ServiceRegistry not found?**
- Verify Maven dependency added
- Run: `mvn clean package`
- Ensure RVNKCore JAR in classpath
- Restart IDE/build system

**Tests failing?**
- Verify MockBukkit setup in test @BeforeEach
- Check ServiceRegistry availability in mock environment
- See integration test template for examples

---

*Automated command generated by recurr-B Discovery (December 15, 2025)*
*Unblocks 5 critical Phase 3 plugins with 102+ minutes per integration (10-20 hours total)*
