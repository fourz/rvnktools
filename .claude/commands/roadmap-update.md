---
description: Update ROADMAP.md structure and archive old content for scannability
argument-hint: [action: audit|optimize|archive] [--what-if] [--force]
---

# ROADMAP Update & Archive Workflow

Maintain ROADMAP.md scannability (200-400 lines) by condensing completed sections and archiving historical content.

**Workflow Type**: Recurring (Quarterly)
**Default**: `audit` (check without changes)

---

## Actions

- **audit** - Check state, validate structure, identify archival candidates
- **optimize** - Condense verbose sections → scannable summaries
- **archive** - Move old content (>6mo) to ROADMAP.ARCHIVE.md

---

## Flags

- `--what-if` - Preview changes without modifying files
- `--force` - Skip confirmations (creates .backups/ROADMAP.md.YYYY-MM-DD.bak)

**Examples**: `/roadmap-update optimize --what-if`, `/roadmap-update archive --force`

---

## Target Metrics

**Optimal ROADMAP.md**:
- **Lines**: 200-400 (scan time: 2-3 min)
- **Sections**: Current focus + upcoming (not exhaustive history)
- **Detail**: High-level milestones (link to task system for granular details)

**Archive when**:
- Completed phases >6 months old
- Verbose sections >50 lines
- Historical context (git history sufficient)

---

## Workflow Steps

1. **Audit**: `wc -l ROADMAP.md` (Target: 200-400, Warning: >400, Critical: >500)
2. **Optimize**: Condense verbose sections (89 lines → 8 lines)
3. **Archive**: Move to ROADMAP.ARCHIVE.md with reference link
4. **Validate**: Check line count and structure (`grep -E "^#{1,2} " ROADMAP.md`)

---

## Condensing Pattern

**Before** (89 lines):
```markdown
### Phase 1: Foundation (Oct-Nov 2025)
**Status**: ✅ Complete
#### Objectives, Deliverables, Tasks (verbose)
```

**After** (8 lines):
```markdown
### Phase 1: Foundation
**Oct-Nov 2025** | ✅ Complete

Established coding standards, documentation framework, test infrastructure.

**Key Deliverables**: coding-standards.md, test framework, CI pipeline
```

---

## Archive Pattern

1. Copy section to ROADMAP.ARCHIVE.md with date header
2. Replace with condensed version in ROADMAP.md
3. Add reference link: `Archived: [ROADMAP.ARCHIVE.md](ROADMAP.ARCHIVE.md#phase-1-2)`

**First time**: `echo "# ROADMAP Archive" > ROADMAP.ARCHIVE.md`

---

## Example Outputs

### Audit
```
Lines: 512 (Critical: >500)
Completed sections >6mo: 3 found
Verbose sections >50 lines: 2 found
✅ Ready for optimization/archival
```

### Optimize --what-if
```
Sections to condense: 2 (89 → 8 lines each)
Estimated final: 350 lines
Preview complete (no changes made)
```

### Archive
```
Archived: 3 sections → ROADMAP.ARCHIVE.md
ROADMAP.md: 512 → 298 lines
Backup: .backups/ROADMAP.md.20251227.bak
✅ Complete
```

---

## Best Practices

✅ Preview first (`--what-if` before modifying)
✅ Archive history (preserve in ROADMAP.ARCHIVE.md)
✅ Keep current focus visible
✅ Execute after Epic consolidation or >500 lines

❌ Don't delete (archive instead)
❌ Don't exceed 500 lines (becomes unscannable)

---

## Related

**Commands**: [epic-consolidation.md](epic-consolidation.md) (consolidate tasks), [doc-cleanup.md](doc-cleanup.md) (documentation)
**Skills**: See [documentation-lifecycle.md](../skills/documentation-lifecycle.md) for archival patterns

**Time**: 15-25 min (audit + optimize), 25-35 min (with archive)
