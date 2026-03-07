---
description: Consolidate completed tasks into numbered Epics in ROADMAP.md (recurring workflow)
argument-hint: [--what-if] [--threshold=N] (default: 10)
---

# Epic Consolidation Workflow

Consolidate 10+ completed tasks into numbered Epics in ROADMAP.md for clean history and scannable progress tracking.

**Workflow Type**: Recurring (Quarterly)
**Threshold**: 10 non-recurring done tasks minimum

---

## Flags

- `--what-if` - Preview without making changes
- `--threshold=N` - Set minimum tasks (default: 10)

**Examples**: `/epic-consolidation --what-if`, `/epic-consolidation --threshold=15`

---

## Critical Guidelines

### Never Consolidate Recurring Tasks

- Tasks prefixed `recurr-*` are workflow templates
- They remain on board permanently (mark done, NEVER delete)
- Only consolidate implementation tasks (doc-*, feat-*, impl-*, fix-*)

### Epic Numbering (Sequential)

```bash
# Find highest Epic number
grep -E "^### Epic [0-9]+" ROADMAP.md | tail -1
# Increment by 1
```

### Epic Title Format

✅ Good: "Epic 4: Documentation Infrastructure"
❌ Bad: "Epic 4: Oct-Dec 2025" (uses dates), "Epic tasks" (too generic)

---

## Workflow Steps

1. **Identify Completed Tasks**: Query done tasks, exclude recurr-*, check threshold
2. **Group by Theme**: Documentation, Automation, Testing, Standards, Performance
3. **Create ROADMAP Entries**: Use template below
4. **Update Task System**: Create Epic task, archive originals
5. **Update Recurring Task**: Track execution history
6. **Validate**: Check ROADMAP size (200-400 lines), verify numbering
7. **Commit**: Document consolidation

---

## Epic Template

```markdown
### Epic {N}: {Theme Title}

**Timeframe**: {Month-Month YYYY}
**Status**: ✅ Complete

**Deliverables**:
- {High-level deliverable 1}
- {High-level deliverable 2}

**Tasks Consolidated**: {Count} tasks
- {task-id}: {Brief description}

**Impact**: {1-2 sentence summary}
```

---

## Task Management

**Create Epic entry**:
- Title: "Epic {N}: {Theme}"
- Status: done, Feature: epic
- Description: Consolidated deliverables

**Archive original tasks**: Mark archived (hidden from active queries, retrievable with `include_closed=true`)

---

## Validation

**ROADMAP**: `wc -l ROADMAP.md` (target: 200-400 lines)
**Numbering**: `grep -E "^### Epic [0-9]+" ROADMAP.md` (verify sequential)
**Tasks**: Verify Epic created, originals archived

---

## Best Practices

✅ Group by theme, not dates
✅ Sequential numbering (check git history)
✅ Write impact statements
✅ Archive tasks (don't delete)

❌ Don't consolidate recurr-* tasks
❌ Don't skip numbering
❌ Don't delete tasks

---

## Related

**Commands**: [roadmap-update.md](roadmap-update.md) (structure), `/doc-batch-sync` (sync changes)

**Time**: 60-80 min for 10-15 tasks, 80-100 min for 30-40 tasks
