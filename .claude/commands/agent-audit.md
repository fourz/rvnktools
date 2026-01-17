---
description: Audit agent registry, skills, and instruction files for consistency and deduplication. Includes skillset optimization recommendations.
argument-hint: [focus: agents|skills|config|instructions|all] (default: all)
---

# Agent Audit & Configuration Validation

Execute recurring audit workflow to ensure consistency across component registry, skills, commands, and instruction files.

**Workflow Type**: Recurring (Monthly)
**Scope**: $ARGUMENTS (default: all)

**Uses skills**: `instruction-maintainer`, `new-skill-analyzer`, `anthropic-standards`, `component-management`
**Uses template**: `.claude/templates/agent-audit-report.md`

---

## Audit Checklist

### 1. Component Validation

**See**: [component-management.md](../skills/component-management.md) skill for all validation scripts:
- Inventory & Registry Validation (orphan detection, file existence)
- Configuration consistency checks
- agents.json integrity verification
- .claude/ directory structure validation

**Quick check**:
```bash
# Component counts
ls -la .claude/agents/*.md | wc -l
ls -la .claude/skills/*.md | wc -l
ls -la .claude/commands/*.md | wc -l
```

**Action for issues**: Use `new-skill-analyzer` skill for orphan recommendations (ACTIVATE | MERGE | ARCHIVE)

---

### 2. Naming Standards

**Standard**: Max 3 words (hyphenated compounds count as 1)

**Check compliance**: See [component-management.md](../skills/component-management.md) for naming validation scripts

**Common violations**:
- `minecraft-rvnk-admin` (3 words) → rename to `minecraft-admin`
- `mcp-test-engineer` (3 words) → rename to `mcp-tester`

---

### 3. Frontmatter Validation

**Uses skill**: `anthropic-standards`

**Required fields**:
- **Agents**: `name`, `description`, `model` (sonnet|opus|haiku), `tools`
- **Commands**: `description`, `argument-hint` (recommended)
- **Skills**: Markdown header (`# Title`)

**See**: `anthropic-standards` skill for detailed validation patterns

---

### 4. Cross-Reference Validation

**Check**:
- [ ] Agent `skills:` frontmatter references exist in `.claude/skills/`
- [ ] Command references to skills are valid
- [ ] Skill references in docs point to existing files

**See**: [component-management.md](../skills/component-management.md) for cross-reference validation scripts

---

### 5. Instruction File Deduplication

**Uses skill**: `instruction-maintainer`

**Rules**:
1. `CLAUDE.md` is canonical project instructions
2. No direct `.claude/agents/{name}.md` links in `CLAUDE.md` (use component management skill reference)
3. Secondary files (`.github/copilot-instructions.md`) reference `CLAUDE.md`
4. `README.md` has no instruction content (project overview only)

**See**: [component-management.md](../skills/component-management.md) for direct path reference verification

---

### 6. Skillset Optimization

**Uses command**: [skillsets.md](skillsets.md)

**Analysis**:
- [ ] Review enabled skillsets
- [ ] Identify unused active components (candidates for skillset grouping)
- [ ] Check context usage: `/context` command shows component token usage
- [ ] Consider disabling high-context, low-use components

**Find usage patterns**:
```bash
# Components referenced in recent commits
git log --since="3 months ago" --all --pretty=format:"%s %b" | \
  grep -oE '\.claude/(agents|commands|skills)/[a-z-]+\.md' | \
  sort | uniq -c | sort -rn | head -20
```

**Workflow**:
1. Identify component groups used together (3+ components)
2. Enable/disable via `/skillsets` command
3. Verify context savings with `/skillsets status`

**See**: [skillsets.md](skillsets.md) command for detailed management and configuration

---

### 7. Category Validation

**Expected categories**:
- `archon-integration` - Archon MCP-specific functionality
- `development` - Language/framework development
- `code-quality` - Review, testing, optimization
- `infrastructure` - Build, deploy, dependencies
- `documentation` - Documentation creation/maintenance
- `project-specific` - Tied to specific external projects
- `specialized` - Language/tech-specific (not primary)
- `duplicate` - Functionality covered elsewhere

**See**: [component-management.md](../skills/component-management.md) for category analysis scripts

---

## Audit Report Generation

**Uses template**: `.claude/templates/agent-audit-report.md`

Create report: `docs/spec/agent-audit-YYYYMMDD.md`

**Template sections**:
1. Summary table (component counts)
2. Validation results (pass/fail for each checklist item)
3. Issues found (orphans, missing files, violations)
4. Recommendations:
   - Files to activate/archive
   - Naming standard fixes
   - Skillset optimization opportunities
   - Configuration updates needed

**See**: [component-management.md](../skills/component-management.md) for report generation scripts

---

## Post-Audit Actions

### Component Management

**See**: [component-management.md](../skills/component-management.md) skill for:
- Adding orphaned components
- Moving components to inactive
- Activation/deactivation workflows
- Configuration update patterns

### Skillset Management

**See**: [skillsets.md](skillsets.md) command for:
- Enable/disable skillsets: `/skillsets enable <name>` or `/skillsets disable <name>`
- Configuration updates
- Context optimization patterns

---

## Integration Points

### With Skillsets System

1. **Run audit** → Identify component usage patterns
2. **Review skillsets** → See [skillsets.md](skillsets.md) for optimization
3. **Enable/disable** → Use `/skillsets` command for bulk operations
4. **Verify savings** → Check context usage with `/context`

### With Knowledge Base

1. **Sync audit reports** → Sync `docs/spec/agent-audit-*.md` to knowledge base
2. **Track as task** → Create task for audit execution
3. **Document changes** → Reference in project documentation

---

## Common Issues & Fixes

| Issue | Detection | Fix |
|-------|-----------|-----|
| Orphaned agent file | See component-management skill | Add to active/inactive with reason |
| Missing file reference | Config references non-existent file | Remove from config or restore file |
| Naming violation | 3+ word name | Rename file, update all references |
| Missing frontmatter | No `---` delimiter | Add Anthropic-compliant frontmatter |
| Duplicate instructions | Content in README.md | Move to CLAUDE.md, update references |
| Stale metadata | `last_updated` outdated | Update date, add to `recent_changes` |

---

## Best Practices

✅ **Run monthly** or after significant component changes
✅ **Reference skills** for validation patterns (component-management, anthropic-standards)
✅ **Use skillsets** for context optimization
✅ **Maintain audit trail** via component management skill

❌ **Don't hardcode** validation scripts (use component-management skill)
❌ **Don't duplicate** configuration info (reference living files)
❌ **Don't skip** orphan analysis (use new-skill-analyzer)
❌ **Don't delete** files (use inactive status instead)

---

## Related Commands

- [skillsets.md](skillsets.md) - Context window optimization via selective component loading
- `archon-sync` - Sync documentation to knowledge base
- `doc-cleanup` - Documentation organization

**Query current state**: `/context` command shows component token usage

---

**Note**: This is a checklist-driven audit. Reference [component-management.md](../skills/component-management.md) and [skillsets.md](skillsets.md) for detailed validation patterns and workflows.
