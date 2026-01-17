---
name: rvnkdev-doc-standards
description: Documentation writing standards, organization rules, and instruction file management. Use when writing docs or reviewing documentation quality.
allowed-tools: Read, Grep
---

# RVNKDev Documentation Standards

**Purpose**: Ensure consistent documentation placement, Archon-first project tracking, instruction file management, and proper document organization.

---

## Instruction File Hierarchy

### Primary: CLAUDE.md (Canonical)

**Contains:**
- ARCHON-FIRST RULE
- Task-driven development workflow
- RAG search best practices
- Quick reference (Projects, Tasks, Knowledge Base)
- RVNK project ecosystem overview
- **INDEX** linking to detailed docs

### Secondary: .github/copilot-instructions.md

**Contains:**
- Brief ARCHON-FIRST RULE
- Link to CLAUDE.md for complete workflow
- Reference sections only

### User-Facing: README.md

**Contains:**
- Project overview
- Quick links to docs/
- Getting started (3-5 steps)
- **NO** instruction content

### Deduplication Rules

1. CLAUDE.md is canonical - contains authoritative version
2. .github/copilot-instructions.md **references** CLAUDE.md sections
3. README.md does NOT duplicate instruction content
4. No direct agent file links in instruction files

---

## Document Placement Rules

### Root-Level Files (Project Instructions Only)

**Allowed files in project root**:
- `README.md` - Project overview, getting started, directory structure
- `ROADMAP.md` - Project timeline, milestones, status
- `CLAUDE.md` - Primary AI instruction file
- `AGENTS.md` - Agent architecture overview (if multi-agent)
- `LICENSE` - License file

**NOT allowed in root** (move to `docs/`):
- PRP documents -> `docs/prp/`
- API specifications -> `docs/api/`
- Architecture guides -> `docs/architecture/`
- Release notes -> `docs/releases/`
- Design documents -> `docs/design/`
- Reports and analysis -> `docs/reports/`
- Tutorials and guides -> `docs/guide/`
- How-to documents -> `docs/guide/how-to-*.md`

**Example cleanup**:
```
Before:
README.md
ROADMAP.md
API-REFERENCE.md          -> Move to docs/api/reference.md
ARCHITECTURE.md           -> Move to docs/architecture/design.md
DEVELOPER-GUIDE.md        -> Move to docs/guide/developer-guide.md

After:
README.md
ROADMAP.md
docs/
├── api/
│   └── reference.md
├── architecture/
│   └── design.md
└── guide/
    ├── developer-guide.md
    └── installation.md
```

### Documentation Directory Structure

**Standard structure** (`docs/` folder):

```
docs/
├── README.md              # Documentation index
├── api/                   # API specifications
│   ├── endpoints.md
│   ├── authentication.md
│   └── error-codes.md
├── architecture/          # Design and architecture docs
│   ├── overview.md
│   ├── data-flow.md
│   └── deployment.md
├── guide/                 # Tutorials and guides
│   ├── getting-started.md
│   ├── installation.md
│   └── how-to-*.md
├── reference/             # Reference documentation
│   ├── configuration.md
│   ├── environment-variables.md
│   └── cli-reference.md
├── spec/                  # Persistent specifications
│   ├── component-inventory.md
│   ├── system-requirements.md
│   └── feature-spec-name.md
├── prp/                   # Project requirements/plans
│   └── prd-feature-name.md
├── fix/                   # Bug fixes and incidents (TIMESTAMPS)
│   └── YYYY-MM-DD-issue-name.md
└── archive/               # Deprecated or historical docs
```

---

## Archon-First Project Tracking

**CRITICAL RULE**: Use **Archon task management** as the primary project tracking system, NOT scattered documentation files.

**When to use Archon**:
- Feature tracking -> `manage_task()` in Archon
- Bug tracking -> Create task in Archon
- Progress status -> Update task status (todo -> doing -> review -> done)
- Work coordination -> Assign tasks to agents/users
- Milestone tracking -> Link completed tasks to releases

**When to use documentation**:
- Implementation guidance -> `docs/guide/`
- Architecture decisions -> `docs/architecture/`
- API specifications -> `docs/api/`
- Reference material -> `docs/reference/`
- Specifications -> `docs/spec/`

**Anti-Pattern**:
- Creating separate project status documents outside Archon
- Tracking progress in markdown files instead of Archon tasks
- Maintaining manual "what's done" checklists (use Archon task board)

---

## File Naming Conventions

**Temporal files** (short-lived, timestamped):
```
docs/fix/YYYY-MM-DD-issue-description.md
docs/fix/2025-01-15-auth-timeout-fix.md
```
- Include date prefix (YYYY-MM-DD)
- Archive after resolution
- Reference in task description only

**Persistent files** (long-lived, no timestamps):
```
docs/api/authentication.md
docs/guide/getting-started.md
docs/spec/system-requirements.md
```
- NO date prefix
- Updated as content evolves
- Sync to Archon RAG/KB if core knowledge

---

## Documentation Update Workflow

### 1. Gap Analysis
- Check root level for non-instruction files
- Verify docs/ files are in proper categories
- Identify missing categories

### 2. Categorization
- Determine document type (guide, api, prp, report)
- Move to appropriate `docs/<category>/` folder
- Update cross-references

### 3. Instruction Sync
- **If updating core instruction**: Update CLAUDE.md, then update references
- **If updating user-facing**: Update README.md, add to docs/ if substantial

### 4. Archon Sync
- Run sync script after documentation updates
- Verify in Archon: `rag_search_knowledge_base(query="...")`

---

## Templates

### Instruction File Header
```markdown
# <Instruction Type>: <Title>

**Purpose**: <One-line purpose>
**Audience**: <Developers, users, etc.>

[See [CLAUDE.md](../CLAUDE.md) for complete workflow]
```

### PRP Document Header
```markdown
# Product Requirements Plan: <Project Name>

**Version**: 1.0
**Date**: YYYY-MM-DD
**Status**: draft/active/completed

## Executive Summary
<1-2 paragraph overview>
```

### API Document Header
```markdown
# <API Name> API Documentation

**Version**: 1.0.0
**Base URL**: http://localhost:8080/api/
**Authentication**: [Describe]

## Overview
<Brief API overview>
```

---

## Docstring Format (Google Style)

**Pattern**:

```python
def authenticate(user_id: str, password: str) -> Token:
    """Authenticate user and return access token.

    Validates user credentials against database and generates JWT token.

    Args:
        user_id: Unique user identifier (email or username)
        password: User password (plaintext, will be hashed)

    Returns:
        Token: JWT access token with 24-hour expiration

    Raises:
        AuthenticationError: If credentials invalid

    Examples:
        >>> token = authenticate("user@example.com", "password123")
        >>> token.expires_in
        86400
    """
    pass
```

**Components**:
1. **Summary line**: One-sentence description
2. **Extended description**: Detailed explanation
3. **Args section**: Parameter names, types, descriptions
4. **Returns section**: Return type and description
5. **Raises section**: Exception types
6. **Examples section**: Usage examples

---

## Documentation Placement Decision Tree

```
Is it instruction for AI/developers?
├─ YES -> Root level file (README, CLAUDE.md, AGENTS.md)
└─ NO

Is it API specification?
├─ YES -> docs/api/
└─ NO

Is it architecture/design?
├─ YES -> docs/architecture/
└─ NO

Is it a tutorial/how-to guide?
├─ YES -> docs/guide/
└─ NO

Is it reference material (config, CLI)?
├─ YES -> docs/reference/
└─ NO

Is it a feature/system specification?
├─ YES -> docs/spec/
└─ NO

Is it a bug fix or temporal issue?
├─ YES -> docs/fix/YYYY-MM-DD-*.md
└─ NO

Is it a project requirement/plan?
├─ YES -> docs/prp/
└─ NO

-> Unknown type: Create in docs/ and document rationale
```

---

## Documentation Sync to Archon RAG/KB

**Files that sync**:
- `docs/spec/` - Specifications (sync to RAG)
- `docs/guide/` - Guides and tutorials (sync to RAG)
- `docs/architecture/` - Architecture docs (sync to RAG)
- `docs/reference/` - Reference material (sync to RAG)
- `docs/api/` - API documentation (sync to RAG)

**Files that DON'T sync**:
- `docs/fix/` - Bug fix reports (temporal, task-referenced only)
- `docs/prp/` - Project plans (versioned in Archon PRPs)
- Root-level CLAUDE.md (use for instruction only)

---

## Common Documentation Mistakes

### Mistake 1: Scattered Status Files
```
BAD:
PROJECT-STATUS.md
PROGRESS.md
TODO.md
```
**Solution**: Use Archon tasks for all status tracking

### Mistake 2: Outdated Root-Level Documentation
```
BAD:
INSTALLATION.md            # Out of sync with docs/guide/
QUICKSTART.md              # Duplicate of docs/guide/getting-started.md
```
**Solution**: Keep root files as navigation only

### Mistake 3: Missing File Extensions in Links
```
BAD:  See [Getting Started](docs/guide/getting-started)
GOOD: See [Getting Started](docs/guide/getting-started.md)
```

---

## Documentation Checklist

Before committing documentation changes:

- [ ] File placed in correct directory (per decision tree)
- [ ] File naming follows conventions (no timestamps for persistent docs)
- [ ] Links include `.md` extension
- [ ] Headings properly formatted (# -> ##)
- [ ] Code blocks have language specified
- [ ] Google-style docstrings in code
- [ ] No duplicate content across files
- [ ] Related Archon task referenced (if applicable)
- [ ] Outdated docs removed or archived
- [ ] Root-level files point to proper docs/ locations
- [ ] CLAUDE.md references updated if instruction changed
- [ ] Archon knowledge base synced

---

**Merged from**: documentation-standards + documentation-specialist (January 2026)
