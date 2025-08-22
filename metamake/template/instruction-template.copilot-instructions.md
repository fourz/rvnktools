# Template for Work Type-Specific Copilot Instructions

Use this template when creating new `.copilot-instructions.md` files for specialized work types within the Metamake framework.

## File Naming Convention
Name your instruction file: `{work-type}.copilot-instructions.md`
Examples:
- `data-science.copilot-instructions.md`
- `security.copilot-instructions.md`
- `frontend-development.copilot-instructions.md`
- `machine-learning.copilot-instructions.md`
- `api-development.copilot-instructions.md`

## Required Template Structure

### 1. Header and Work Type Definition
```markdown
# Copilot Instructions for [Work Type] in Metamake Framework

This instruction file provides specialized guidelines for [work type] projects within the Metamake document-based LLM workflow framework.

## Work Type Overview
[Describe the specific work type, its goals, and how it fits within the Metamake framework]
```

### 2. Core Framework Integration
```markdown
## Framework Integration
- Inherits all core Metamake principles from the main instruction files
- Follows the standard project structure under `projects/XX-project-name/`
- Maintains documentation-as-code practices
- Uses templates from the `templates/` directory when applicable
- References prompts from the `prompts/` directory for consistency
```

### 3. Work Type-Specific Guidelines
```markdown
## [Work Type] Specific Guidelines

### Content Generation
- [Specific content types for this work type]
- [Domain-specific documentation requirements]
- [Special formatting or structure considerations]

### Best Practices
- [Industry or domain-specific best practices]
- [Quality standards specific to this work type]
- [Common patterns and anti-patterns]

### Templates and Patterns
- [Specific templates needed for this work type]
- [Code patterns or configuration patterns]
- [Documentation patterns specific to the domain]
```

### 4. Feature and Implementation Guidance
```markdown
## Feature Development Guidelines

### Feature Documentation
When generating feature documentation for [work type]:
- [Specific requirements for feature docs in this domain]
- [Domain-specific considerations]
- [Integration points with other systems/domains]

### Implementation Guides
When creating implementation guides for [work type]:
- [Step-by-step requirements specific to this domain]
- [Tools and technologies commonly used]
- [Environment setup considerations]
- [Common troubleshooting scenarios]

### Validation Checklists
When developing validation checklists for [work type]:
- [Domain-specific validation criteria]
- [Compliance or regulatory considerations]
- [Performance or quality metrics]
- [Security considerations specific to this domain]
```

### 5. Integration Points
```markdown
## Integration with Other Work Types
- [How this work type integrates with DevOps]
- [Dependencies on or from other specialized domains]
- [Shared resources or common patterns]
- [Cross-functional collaboration guidelines]
```

### 6. Quality Standards
```markdown
## Quality Standards for [Work Type]
- [Code quality standards]
- [Documentation quality requirements]
- [Review and approval processes]
- [Continuous improvement practices]
```

### 7. Tools and Technologies
```markdown
## Recommended Tools and Technologies
- [Primary tools for this work type]
- [VS Code extensions or configurations]
- [External integrations]
- [Automation tools or frameworks]
```

## Usage Instructions

1. Replace all `[Work Type]` placeholders with your specific work type
2. Fill in all bracketed sections with relevant content
3. Add domain-specific sections as needed
4. Ensure alignment with core Metamake principles
5. Test the instructions with a sample project
6. Update based on user feedback and evolving practices

## Example Work Types to Consider

- **Data Science**: Focus on data analysis workflows, Jupyter notebooks, ML pipelines
- **Security**: Emphasis on threat modeling, security reviews, compliance documentation  
- **Frontend Development**: UI/UX documentation, component libraries, design systems
- **API Development**: API documentation, testing strategies, integration guides
- **Machine Learning**: Model documentation, training pipelines, deployment guides
- **Quality Assurance**: Test plans, automation frameworks, bug tracking workflows
- **Product Management**: Requirements documentation, user stories, roadmap planning
- **Technical Writing**: Documentation standards, style guides, content workflows

Remember: Each instruction file should enhance the Metamake framework for specific use cases while maintaining consistency with the core principles and structure.
