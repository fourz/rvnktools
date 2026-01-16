name: "LLMs.txt Generator Skill - Documentation Optimizer with Archon KB Integration"
description: |

---

## Goal

**Feature Goal**: Create a Claude Code skill that generates llms.txt files for any project and uploads them to the Archon knowledge base for RAG-enhanced documentation retrieval.

**Deliverable**:
1. Claude Code skill (`llms-txt-generator.md`) that analyzes project structure and generates llms.txt
2. Slash command (`/generate-llmstxt`) for quick invocation
3. Archon KB integration for automatic upload of generated documentation

**Success Definition**:
- Skill generates valid llms.txt files following llmstxt.org specification
- Generated files are automatically uploaded to Archon KB as a new source
- RAG queries can retrieve project documentation via the uploaded llms.txt

## User Persona

**Target User**: Developer or AI agent working on Ravenkraft ecosystem projects

**Use Case**: Generate LLM-friendly documentation for any project to improve AI-assisted coding capabilities

**User Journey**:
1. Navigate to project directory
2. Run `/generate-llmstxt` or ask Claude to "generate llms.txt"
3. Skill analyzes project structure (README, docs/, code files)
4. Generates llms.txt with proper format and links
5. Optionally uploads to Archon KB for RAG indexing

**Pain Points Addressed**:
- AI assistants struggle to navigate large codebases without structured documentation
- Manual llms.txt creation is tedious and error-prone
- Documentation scattered across files is hard for LLMs to retrieve efficiently

## Why

- **Better AI retrieval**: LLMs can quickly understand project structure and find relevant docs
- **Standardized format**: Follows llmstxt.org specification for universal compatibility
- **Knowledge persistence**: Archon KB integration enables cross-session knowledge retrieval
- **Automation**: Eliminates manual documentation curation for AI consumption

## What

### Core Features

1. **Project Analysis**: Scan project for documentation files (README, docs/, CLAUDE.md, etc.)
2. **llms.txt Generation**: Create properly formatted llms.txt following specification
3. **Project Type Detection**: Identify project type (library, CLI, framework, plugin) for appropriate template
4. **Archon KB Upload**: Sync generated llms.txt to Archon knowledge base

### Success Criteria

- [ ] Generates valid llms.txt with H1 title, blockquote summary, and H2 sections
- [ ] Detects project type and uses appropriate template
- [ ] Links include full URLs with descriptive notes
- [ ] Uploads to Archon KB via manage_document or RAG source creation
- [ ] Provides before/after validation of documentation quality

## All Needed Context

### Context Completeness Check

_This PRP provides complete context for implementing the llms.txt generator skill._

### Documentation & References

```yaml
# MUST READ - Include these in your context window
- url: https://llmstxt.org/
  why: Official specification for llms.txt format
  critical: Required format (H1, blockquote, H2 sections, markdown links)

- url: https://github.com/alonw0/llm-docs-optimizer
  why: Reference implementation of similar functionality
  pattern: Skill structure, workflow patterns, template selection

- file: .claude/skills/git-worktree-manager.md
  why: Pattern for skill structure in this codebase
  pattern: Skill activation triggers, workflow steps, output format

- file: .claude/commands/worktree.md
  why: Pattern for slash command structure
  pattern: Parameter handling, subcommand structure

- file: CLAUDE.md
  why: Archon MCP integration patterns
  pattern: find_documents(), manage_document(), rag_search_knowledge_base()
```

### Current Codebase Tree

```bash
.claude/
├── agents/           # 29+ specialized agents
├── commands/         # Slash commands including worktree, archon-sync
├── skills/           # Skills including git-worktree-manager
└── settings.json

archon/
├── PRPs/
│   ├── templates/    # PRP templates
│   └── ai_docs/      # AI-specific documentation
└── ...
```

### Desired Codebase Tree

```bash
.claude/
├── skills/
│   └── llms-txt-generator.md    # NEW: Main skill file
├── commands/
│   └── generate-llmstxt.md      # NEW: Slash command
└── ...

# Output (per project):
<project-root>/
└── llms.txt                     # Generated file
```

### Known Gotchas

```yaml
# CRITICAL: llms.txt specification requirements
# - H1 heading is the ONLY required element
# - Blockquote summary should be 1-3 sentences
# - Links MUST use full URLs, not relative paths
# - "Optional" section for secondary resources

# CRITICAL: Archon integration
# - Use manage_document() for project-specific docs
# - RAG KB requires source_id from rag_get_available_sources()
# - llms.txt content should be converted to JSON for document storage
```

## Implementation Blueprint

### Data Models

```yaml
# No Pydantic models needed - this is a markdown-based skill

# llms.txt Structure:
LlmsTxtFile:
  title: str              # H1 heading (project name)
  summary: str            # Blockquote (1-3 sentences)
  sections: list          # H2 sections with links
  optional_section: list  # Secondary resources

# Section Structure:
Section:
  heading: str            # H2 title (e.g., "## Documentation")
  links: list             # Markdown links with descriptions

# Link Structure:
Link:
  title: str              # Display text
  url: str                # Full URL
  description: str        # Optional colon-prefixed note
```

### Implementation Tasks (ordered by dependencies)

```yaml
Task 1: CREATE .claude/skills/llms-txt-generator.md
  - IMPLEMENT: Skill file with activation triggers and workflow
  - FOLLOW pattern: .claude/skills/git-worktree-manager.md
  - SECTIONS:
    - Skill Activation (trigger phrases)
    - Project Analysis Workflow
    - Template Selection Logic
    - llms.txt Generation
    - Archon KB Upload
    - Validation Checklist
  - PLACEMENT: .claude/skills/

Task 2: CREATE .claude/commands/generate-llmstxt.md
  - IMPLEMENT: Slash command for quick invocation
  - FOLLOW pattern: .claude/commands/worktree.md
  - PARAMETERS: [project-path] [--upload] [--dry-run]
  - SUBCOMMANDS: generate, validate, upload
  - PLACEMENT: .claude/commands/

Task 3: DEFINE Project Analysis Logic (in skill file)
  - SCAN: README.md, CLAUDE.md, docs/, src/, package.json/pom.xml
  - DETECT: Project type (java-plugin, node-app, python-lib, etc.)
  - CATALOG: Available documentation files with descriptions
  - OUTPUT: Structured data for template selection

Task 4: DEFINE Template Selection Logic (in skill file)
  - TEMPLATES: Java Plugin, Node/React App, Python Library, CLI Tool, Framework
  - CRITERIA: File presence (pom.xml → Java, package.json → Node, etc.)
  - FALLBACK: Generic project template

Task 5: DEFINE llms.txt Generation Logic (in skill file)
  - FORMAT: Follow llmstxt.org specification exactly
  - SECTIONS:
    - Getting Started (README, quickstart)
    - Core API/Commands (main functionality)
    - Examples (code samples, tutorials)
    - Configuration (settings, env vars)
    - Development (contributing, architecture)
    - Optional (changelog, blog, community)
  - LINKS: Full URLs with descriptive notes

Task 6: DEFINE Archon KB Integration (in skill file)
  - OPTION 1: manage_document() - Store as project document
  - OPTION 2: Custom RAG source - For cross-project retrieval
  - CONVERT: llms.txt markdown to JSON for storage
  - VALIDATE: Successful upload and retrieval test
```

### Implementation Patterns

```markdown
# Skill Activation Pattern
The skill activates when user asks:
- "Generate llms.txt for this project"
- "Create LLM documentation"
- "Make my docs AI-friendly"
- "/generate-llmstxt"

# Project Analysis Pattern
1. Check for README.md (required)
2. Scan docs/ folder for additional documentation
3. Check for CLAUDE.md or .github/copilot-instructions.md
4. Identify build files (pom.xml, package.json, setup.py)
5. Catalog code examples (examples/, samples/, tests/)

# Template Selection Pattern
IF pom.xml exists → Java Plugin Template
IF package.json exists → Node/React Template
IF setup.py OR pyproject.toml exists → Python Template
IF Cargo.toml exists → Rust Template
ELSE → Generic Project Template

# llms.txt Output Pattern
# {Project Name}

> {One-sentence description of what the project does and its primary purpose}

## Documentation
- [README](url): Project overview and getting started guide
- [API Reference](url): Core API documentation

## Examples
- [Quick Start](url): Basic usage examples
- [Tutorials](url): Step-by-step guides

## Optional
- [Changelog](url): Version history
- [Contributing](url): How to contribute

# Archon Upload Pattern
1. Convert llms.txt to JSON structure
2. Call manage_document("create", project_id="...", title="llms.txt",
     document_type="guide", content={...})
3. Verify with find_documents(project_id="...", query="llms")
```

### Integration Points

```yaml
ARCHON_MCP:
  - tool: manage_document
    action: "create"
    params:
      project_id: "4787f505-e92e-474d-ba54-f5ac7993ccfe"  # Ravenkraft Dev
      title: "llms.txt - {project_name}"
      document_type: "guide"
      content: {llms_txt_as_json}

  - tool: find_documents
    params:
      project_id: "..."
      query: "llms.txt"

FILE_SYSTEM:
  - output: "{project_root}/llms.txt"
  - backup: "{project_root}/llms.txt.bak" (if exists)
```

## Validation Loop

### Level 1: Syntax & Format Validation

```bash
# Verify llms.txt format compliance
# - H1 heading present
# - Blockquote summary present
# - H2 sections properly formatted
# - All links use markdown syntax [text](url)
# - URLs are full paths, not relative

# Manual check:
cat llms.txt | head -20
# Expected: # Title, > Summary, ## Sections
```

### Level 2: Content Validation

```bash
# Verify all linked files exist
# For each [text](url) in llms.txt:
#   - If local file: verify file exists
#   - If external URL: optional link check

# Verify project type detection accuracy
# - Template matches actual project type
# - Sections are appropriate for project
```

### Level 3: Archon Integration Validation

```bash
# Test document upload
find_documents(project_id="...", query="llms.txt")
# Expected: Returns uploaded document

# Test RAG retrieval
rag_search_knowledge_base(query="llms.txt {project_name}")
# Expected: Returns relevant results (if source indexed)
```

### Level 4: End-to-End Validation

```bash
# Test full workflow
1. Run /generate-llmstxt in target project
2. Verify llms.txt created at project root
3. Verify content follows specification
4. Verify Archon upload successful
5. Test AI retrieval using generated documentation
```

## Final Validation Checklist

### Technical Validation

- [ ] Skill file follows existing skill patterns
- [ ] Slash command properly configured
- [ ] Project analysis detects all major file types
- [ ] Template selection logic handles edge cases
- [ ] llms.txt output follows specification

### Feature Validation

- [ ] Generates valid llms.txt for Java plugin projects
- [ ] Generates valid llms.txt for Node/React projects
- [ ] Handles projects with minimal documentation
- [ ] Archon upload creates retrievable document
- [ ] /generate-llmstxt command works with all parameters

### Integration Validation

- [ ] Works with existing Archon MCP tools
- [ ] Compatible with project-level CLAUDE.md patterns
- [ ] Follows documentation-specialist agent patterns

---

## Anti-Patterns to Avoid

- Do not generate relative URLs in llms.txt (use full paths)
- Do not skip H1 heading (only required element)
- Do not create overly long summaries (keep to 1-3 sentences)
- Do not include private/internal documentation links
- Do not duplicate content that's better linked
- Do not hardcode project-specific values in skill file

## RVNK-Specific Templates

### Java Plugin Template (RVNKCore, RVNKLore, etc.)

```markdown
# {Plugin Name}

> Minecraft plugin for {purpose}. Part of the Ravenkraft Network ecosystem.

## Documentation
- [README](README.md): Overview and installation
- [CLAUDE.md](CLAUDE.md): AI assistant instructions
- [Coding Standards](docs/standards/coding-standards.md): Development guidelines

## Core API
- [Commands](docs/commands.md): Available commands
- [Permissions](docs/permissions.md): Permission nodes
- [Configuration](docs/config.md): Plugin configuration

## Integration
- [RVNKCore Integration](docs/rvnkcore-integration.md): ServiceRegistry, Repository patterns
- [Database Patterns](docs/database.md): MySQL/SQLite setup

## Examples
- [src/main/java](src/main/java): Source code examples

## Optional
- [Changelog](CHANGELOG.md): Version history
```

### Node/React Template (RVNKWebUI)

```markdown
# {Project Name}

> {Description of web application purpose}

## Documentation
- [README](README.md): Project overview
- [Setup Guide](docs/setup.md): Installation and configuration

## Core Features
- [Components](src/components/): React components
- [API Routes](src/api/): Backend endpoints
- [Hooks](src/hooks/): Custom React hooks

## Development
- [Contributing](CONTRIBUTING.md): How to contribute
- [Architecture](docs/architecture.md): System design

## Optional
- [Changelog](CHANGELOG.md): Version history
```
