# Context Optimization Skill

**Purpose**: Optimize token usage by selectively loading instruction modules based on current development task.

**Use When**: Planning to include supplementary instruction modules, need to reduce token usage, want to improve AI response relevance.

---

## Module Selection Strategy

Supplementary instruction modules provide specialized guidance for specific development contexts. Unlike primary instructions, supplementary modules should be loaded **selectively** to optimize token usage and improve AI assistance effectiveness.

---

## Context Optimization Rules (LLM Best Practices)

### ⚠️ CRITICAL: Avoid Token Waste

**HIGH-COST MODULES** (>500 tokens each - avoid unless essential):
- `installation.md` (1200+ tokens) - Only for deployment/setup tasks
- `tests.md` (800+ tokens) - Only when writing comprehensive test suites
- `sparkedhost.md` (600+ tokens) - Only for SparkedHost-specific API work
- `mcss.md` (500+ tokens) - Only for MCSS-specific API work

**MEDIUM-COST MODULES** (200-400 tokens each):
- `database.md`, `files.md`, `providers.md` - Include selectively

**LOW-COST MODULES** (<200 tokens):
- `metamake.md` - Safe to include when relevant

**🚫 NEVER INCLUDE**:
- Multiple provider modules unless doing cross-provider work
- "Just in case" modules without immediate need
- Modules that don't directly impact current answer

---

## Pre-Inclusion Decision Tree

Ask yourself these questions in order:

1. **Immediate Need Test**: "Am I writing code for this specific functionality RIGHT NOW?"
   - If NO → Skip the module

2. **Token Cost Analysis**: "Will this add >300 tokens without changing my specific answer?"
   - If YES → Skip the module

3. **Alternative Check**: "Can I ask for a targeted code snippet instead?"
   - If YES → Ask for snippet, skip module

4. **Specificity Rule**: "Do I need the ENTIRE module or just a pattern?"
   - If only a pattern → Ask for specific guidance instead

**If ANY answer suggests skipping → SKIP THE MODULE**

---

## Module Organization Reference

### Supplementary Modules Directory: `.github/supplemental/`

**Provider Integration**:
- `copilot-instructions.sparkedhost.md` - SparkedHost API patterns, authentication, tools
- `copilot-instructions.mcss.md` - MCSS API patterns, naming conventions, batch operations

**Operations & Infrastructure**:
- `copilot-instructions.database.md` - MySQL integration, connection management
- `copilot-instructions.files.md` - SFTP operations, connection pooling, batch management
- `copilot-instructions.tests.md` - Testing frameworks, validation strategies
- `copilot-instructions.testing.md` - Test-specific patterns and assertions

**Deployment & Setup**:
- `copilot-instructions.installation.md` - Cross-platform setup, VS Code MCP, deployment
- `copilot-instructions.metamake.md` - Metamake project management, structure
- `copilot-instructions.documents.md` - Document management and organization
- `copilot-instructions.handoff.md` - Copilot-to-Claude handoff patterns

**Architecture & Code**:
- `copilot-instructions.providers.md` - Multi-provider architecture patterns
- `copilot-instructions.architecture.md` - Design patterns, code organization
- `copilot-instructions.coding.md` - Code quality standards, conventions

---

## Example Usage Patterns

### SFTP File Operations with SparkedHost

```
Task: Implement file transfer with SparkedHost SFTP
✅ Include: copilot-instructions.files.md (file operations)
✅ Include: copilot-instructions.sparkedhost.md (provider-specific)
❌ Skip: database, tests, installation modules
```

### Database Integration Testing

```
Task: Write database integration tests
✅ Include: copilot-instructions.database.md (database operations)
✅ Include: copilot-instructions.tests.md (test frameworks)
❌ Skip: provider modules, installation
```

### Provider Architecture Review

```
Task: Review and improve provider abstraction
✅ Include: copilot-instructions.providers.md (architecture)
✅ Include: copilot-instructions.architecture.md (design patterns)
❌ Skip: provider-specific, installation modules
```

### Installation & Deployment

```
Task: Configure VS Code MCP environment
✅ Include: copilot-instructions.installation.md (setup)
❌ Skip: everything else (installation covers what's needed)
```

### Metamake Project Setup

```
Task: Structure new metamake project
✅ Include: copilot-instructions.metamake.md (framework)
✅ Include: copilot-instructions.documents.md (documentation)
❌ Skip: other modules
```

---

## Best Practices

1. **Default to NOT including** supplementary modules
2. **Ask for specific guidance** instead of loading entire modules
3. **Combine related modules** when working on overlapping functionality
4. **Monitor token usage** - Aim to keep context window focused
5. **Reference module index** in `.github/supplemental/` for complete guidance
6. **Update context** as development focus changes

---

## Quick Reference Checklist

When deciding to load a module:

- [ ] Am I actively working on this functionality RIGHT NOW?
- [ ] Does this module directly improve my current answer?
- [ ] Is token cost justified by the value it adds?
- [ ] Can I ask for a targeted snippet instead?
- [ ] Am I avoiding "just in case" loading?

If you answered "no" to any question → **SKIP THE MODULE**
