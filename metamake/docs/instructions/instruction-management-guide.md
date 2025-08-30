# Managing Multiple Copilot Instruction Files

This guide explains how to create, organize, and maintain specialized Copilot instruction files within the Metamake framework.

## Overview

The Metamake framework supports multiple work types through specialized `.copilot-instructions.md` files. This approach allows for:

- **Domain Expertise**: Tailored guidance for specific work types
- **Scalability**: Easy addition of new specializations without affecting existing ones
- **Consistency**: Maintained framework principles across all work types
- **Flexibility**: Customized workflows while preserving core structure

## File Organization

### Current Instruction Files

- `copilot-instructions.md` - Main framework instructions
- `devops.copilot-instructions.md` - Updated framework with extensibility support
- `projects.copilot-instructions.md` - Project-specific guidance
- `storyboard-planner.copilot-instructions.md` - Example specialized work type
- `instruction-template.copilot-instructions.md` - Template for creating new instruction files

### Naming Convention

Follow this pattern: `{work-type}.copilot-instructions.md`

Examples:

- `data-science.copilot-instructions.md`
- `frontend-development.copilot-instructions.md`
- `security-engineering.copilot-instructions.md`
- `api-development.copilot-instructions.md`

## Creating New Instruction Files

### Step 1: Identify the Work Type

- Define the specific domain or specialization
- Determine unique requirements not covered by existing instructions
- Identify the target audience and use cases

### Step 2: Use the Template

- Copy `templates/instruction-template.copilot-instructions.md`
- Rename following the naming convention
- Fill in all template sections with domain-specific content

### Step 3: Validate Integration

- Ensure alignment with core Metamake principles
- Verify consistency with existing instruction files
- Test with a sample project or use case

### Step 4: Document and Share

- Add the new instruction file to this guide
- Update relevant documentation
- Share with team members for feedback

## Best Practices

### Consistency Guidelines

1. **Framework Alignment**: All instruction files must support core Metamake principles
2. **Structure Consistency**: Follow the same organizational patterns across files
3. **Cross-References**: Link related instruction files where appropriate
4. **Quality Standards**: Maintain high documentation standards across all work types

### Content Guidelines

1. **Specificity**: Focus on domain-specific guidance not covered elsewhere
2. **Practicality**: Include actionable, implementable instructions
3. **Examples**: Provide concrete examples and use cases
4. **Evolution**: Plan for updates and improvements based on usage

### Maintenance Guidelines

1. **Regular Review**: Schedule periodic reviews of instruction files
2. **User Feedback**: Collect and incorporate feedback from users
3. **Version Control**: Track changes and maintain version history
4. **Deprecation**: Have a process for retiring outdated instruction files

## Usage Patterns

### For Users

1. **Identify Work Type**: Determine which instruction file best matches your project
2. **Reference Multiple Files**: Use core instructions + specialized instructions as needed
3. **Provide Feedback**: Share experiences to help improve instruction quality
4. **Create New Types**: Follow the template to create new instruction files for emerging needs

### For Copilot

1. **Context Awareness**: Understand which instruction files are relevant for the current task
2. **Precedence Rules**: Apply specialized instructions while maintaining framework consistency
3. **Integration**: Seamlessly blend guidance from multiple instruction files
4. **Adaptation**: Adjust responses based on the specific work type context

## Integration with Projects

### Project Structure

Each project should reference appropriate instruction files:

```text
projects/XX-project-name/
├── COPILOT-INSTRUCTIONS.md (project-specific)
├── project-details.md (includes work type specification)
└── README.md (references relevant instruction files)
```

### Work Type Declaration

In `project-details.md`, specify the relevant work types:

```markdown
**Work Types:** Data Science, DevOps
**Instruction Files:** data-science.copilot-instructions.md, devops.copilot-instructions.md
```

## Future Enhancements

### Planned Features

- **Instruction File Discovery**: Automatic detection of relevant instruction files
- **Dependency Management**: Handle relationships between different work types
- **Template Validation**: Automated checking of instruction file completeness
- **Usage Analytics**: Track which instruction files are most effective

### Extension Points

- **Custom Workflows**: Support for organization-specific instruction patterns
- **External Integration**: Connection with external documentation systems
- **AI Enhancement**: Machine learning to improve instruction effectiveness
- **Community Contributions**: Framework for sharing instruction files across organizations

## Troubleshooting

### Common Issues

1. **Conflicting Instructions**: When multiple files provide contradictory guidance
2. **Missing Context**: When no instruction file covers a specific use case
3. **Outdated Content**: When instruction files become stale or incorrect
4. **Performance Impact**: When too many instruction files slow down Copilot

### Solutions

1. **Priority System**: Establish clear precedence rules for instruction files
2. **Gap Analysis**: Regular review to identify missing instruction coverage
3. **Maintenance Schedule**: Regular updates and reviews of instruction files
4. **Optimization**: Performance monitoring and optimization of instruction content

## Getting Help

- Review existing instruction files for examples
- Use the template as a starting point for new files
- Consult the main README.md for framework overview
- Reference the prompts/ directory for content generation guidance
- Check projects/00-example/ for implementation examples
