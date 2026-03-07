---
description: Automated documentation batch sync to Archon knowledge base with RAG validation
argument-hint: [folder: docs|spec|guide|api|reference] [action: sync|validate|dryrun] (default: docs sync)
---

# Automated Documentation Batch Sync

Streamlined batch synchronization of documentation to Archon knowledge base with automatic format conversion, MCP upload, and RAG validation in a single command.

**Workflow Type**: Recurring Template (Weekly/after doc changes)
**Example Task Name**: `recurr-D` (or your project-specific identifier)
**Prerequisites**: Archon MCP connection, long-term documentation

**Usage**: `/doc-batch-sync [folder] [action]`

---

## Scan Paths Inventory

**Standard documentation folders** (adapt to your project):

- **spec** - docs/spec/ (specifications, architecture)
- **api** - docs/api/ (API documentation)

**Note**: Command discovers all markdown files in specified folder. Adjust paths to match your project structure.

---

## Action Modes

### sync (Default)
Full synchronization: discover → convert → upload → report

### validate
RAG validation: run targeted queries → measure coverage → report indexing status

### dryrun
Preview only: show files → preview format → estimate time (no upload)

---

## Sync Process Details

**For each discovered markdown file:**

1. **Convert** markdown → JSON (preserving structure)
2. **Find** existing Archon document (by file_path match)
3. **Update** via `mcp__archon__manage_document(action="update", ...)`
4. **Wait** 3 seconds (REQUIRED for RAG stability)
5. **Track** success/failure for report
6. **Move to next** file

**Critical Safety Features:**
- **Sequential updates**: No concurrency (prevents race conditions)
- **Rate limiting**: 3 second delay between updates (prevents RAG crashes and indexing corruption)
- **Error handling**: Continue on failure, log in report
- **Batch size**: Recommend <20 docs per batch

**Time estimates:**
- Small batch (1-5 docs): 5-10 minutes
- Medium batch (6-15 docs): 10-20 minutes
- Large batch (16-30 docs): 20-35 minutes

---

## JSON Format

Each markdown file converted to structured Archon format:

```json
{
  "title": "Coding Standards",
  "tags": ["coding-standards", "java"],
  "summary": "Core Java 17+ coding conventions...",
  "file_path": "docs/standard/coding-standards.md",
  "full_text": "[complete markdown preserved]",
  "key_sections": ["Naming Conventions", "Async Patterns"],
  "full_markdown_available": true
}
```

**Key features:** Full text preserved, summary for RAG, tags for filtering, sections extracted from headings

---

## Validation Queries

**Customize per folder** (examples):

**spec/**: "system architecture", "design patterns", "data flow"
**guide/**: "getting started", "setup instructions", "deployment"
**api/**: "API endpoints", "authentication", "error handling"

**Workflow**: Wait 5-10 min for Archon indexing → execute queries → calculate coverage → report

---

## Example Output (Abbreviated)

### Sync
```
📂 Discovered: 6 files
📝 Converting...
🔄 Uploading... (3s delay per file)
📊 Updated: 6/6 docs | Time: 7 min
📄 Report: docs/summary/batch-sync-20251222.md
⏳ RAG indexing: 5-10 min
```

### Validate
```
🔍 Testing queries...
✓ 10/10 queries successful
📊 Coverage: 100%
✅ All docs indexed
```

### Dryrun
```
📂 4 files found (docs/spec/)
📋 Format preview shown
⏱️  Estimate: 8-12 min
✅ Ready to sync
```

---

## Integration

**Sequential Workflow:**
1. `/doc-cleanup` - Organize documentation
2. `/doc-batch-sync` - Sync to Archon
3. Validate RAG indexing (automatic)

**When to Use:**
- After documentation reorganization
- Weekly sync of updated docs
- Before knowledge base queries

---

## Performance

**Total execution times** (includes required 3s delays):
- Small batch: 5-10 min
- Medium batch: 10-20 min
- Large batch: 20-35 min

**Frequency:** Weekly or after doc changes

**Safety:** 3 second delays between updates prevent RAG crashes and indexing corruption (REQUIRED)
