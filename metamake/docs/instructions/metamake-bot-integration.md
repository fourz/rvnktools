# Metamake Bot Integration Guide

This guide provides step-by-step instructions for integrating metamake project management capabilities into any existing project using GitHub Copilot.

## Overview

Metamake is a document-based project management framework that works with GitHub Copilot to help you organize, plan, and execute projects with structured documentation and workflow guidance. This integration allows you to add metamake's project management capabilities to any existing project while preserving your current structure and processes.

## Prerequisites

- Visual Studio Code with GitHub Copilot extension
- An existing project repository
- Basic familiarity with markdown documentation

## Quick Start Integration

### Step 1: Add Metamake to Your Project

1. **Download or copy the `metamake/` folder** from this repository into your project root directory
2. **Verify the structure** - you should now have a `metamake/` folder containing:
   - `README.md` - Metamake overview and usage
   - `prompts/` - Integration and scaffolding prompts
   - `projects/` - Example and template projects
   - `template/` - Project detail templates

### Step 2: Prepare Integration Context

Before running the integration, gather the following information about your project:

#### Project Information

- **Project name and description**
- **Primary technology stack** (e.g., Python/Django, Node.js/React, .NET Core)
- **Domain or focus area** (e.g., web development, data science, DevOps, mobile)
- **Team size and structure**

#### Current Structure Analysis

- **Documentation location** - Where do you keep project docs? (`docs/`, `documentation/`, `wiki/`)
- **Script organization** - Where are automation scripts stored? (`scripts/`, `tools/`, `automation/`)
- **Project planning approach** - How do you currently manage projects and tasks?
- **Development workflow** - Code review, testing, deployment processes

#### Requirements and Context

- **Key components or services** in your project
- **Infrastructure elements** (cloud services, databases, external APIs)
- **Compliance or security requirements**
- **Quality assurance processes**
- **Management or customer feedback** you want to address

### Step 3: Run Integration Prompt

1. **Open the integration prompt** at `metamake/prompts/prompt-metamake-integration.md`
2. **Copy the entire prompt content**
3. **Start a new conversation with GitHub Copilot** in VS Code
4. **Provide the prompt along with your context:**

```text
[Paste the entire prompt-metamake-integration.md content here]

---

Project Context:
- Project Name: [Your Project Name]
- Technology Stack: [Your Technologies]
- Domain: [Your Domain]
- Current Structure: [Description of current folders and organization]

[Include any additional context, requirements, documentation, 
management feedback, or specific needs here]
```

### Step 4: Review and Customize Generated Instructions

The integration will generate a `.github/copilot-instructions.md` file customized for your project. Review and verify:

- [ ] **Folder paths** match your actual project structure
- [ ] **Technical knowledge sections** reflect your technology stack
- [ ] **Workflow descriptions** align with your processes
- [ ] **Domain-specific information** is accurate and relevant
- [ ] **All placeholder text** has been replaced with meaningful content

### Step 5: Start Using Metamake

Once integration is complete, you can:

1. **Create new projects** using metamake templates in `metamake/projects/`
2. **Use Copilot** with your new instructions for project-specific guidance
3. **Follow metamake workflows** for structured project management
4. **Generate documentation** using metamake prompts and templates

## Advanced Integration Options

### Custom Folder Structure

If your project uses non-standard folder names:

1. **Document your structure** clearly in the integration context
2. **Specify exact folder names** you want to use
3. **Review generated paths** carefully for consistency
4. **Test relative path references** in documentation

Example custom structure context:

```text
Custom Folder Structure:
- Documentation: `documentation/` (not `docs/`)
- Scripts: `tools/` (not `scripts/`)
- Project Plans: `documentation/planning/`
- Architecture Docs: `documentation/architecture/`
```

### Multi-Project Repositories

For repositories with multiple projects:

1. **Consider separate metamake instances** for each project
2. **Use project-specific folder prefixes** (e.g., `frontend-metamake/`, `backend-metamake/`)
3. **Maintain clear boundaries** between project documentation
4. **Document cross-project dependencies** clearly

### Legacy Project Integration

When working with established projects:

1. **Analyze existing documentation** and project management approaches
2. **Plan migration strategy** for current project plans and documentation
3. **Ensure backward compatibility** with existing workflows
4. **Document changes** and provide transition guidance for team members

## Common Integration Scenarios

### Web Application Projects

- Technology stacks: React/Node.js, Django/Python, ASP.NET, etc.
- Focus areas: Frontend/backend architecture, database design, API development
- Common structures: `src/`, `docs/`, `scripts/`, `tests/`

### Data Science Projects

- Technology stacks: Python/Jupyter, R, Spark, etc.
- Focus areas: Data processing, model development, analysis workflows
- Common structures: `notebooks/`, `data/`, `models/`, `docs/`

### DevOps/Infrastructure Projects

- Technology stacks: Terraform, Ansible, Kubernetes, etc.
- Focus areas: Infrastructure as code, deployment automation, monitoring
- Common structures: `infrastructure/`, `automation/`, `monitoring/`, `docs/`

### Mobile Application Projects

- Technology stacks: React Native, Flutter, Swift/iOS, Kotlin/Android
- Focus areas: App development, testing, deployment
- Common structures: `src/`, `assets/`, `docs/`, `scripts/`

## Quality Assurance

After integration, verify that:

- [ ] **All team members** can access and understand the new structure
- [ ] **Existing workflows** continue to function properly
- [ ] **Documentation links** are still valid
- [ ] **Automation scripts** use correct folder references
- [ ] **Integration feels natural** within your existing project culture

## Support and Troubleshooting

### Common Issues

**Problem**: Generated instructions reference wrong folder names
**Solution**: Re-run integration with clearer folder structure specification

**Problem**: Technical knowledge sections are too generic
**Solution**: Provide more detailed technology stack and domain context

**Problem**: Workflow sections don't match team processes
**Solution**: Include detailed workflow descriptions in integration context

### Getting Help

1. **Review example projects** in `metamake/projects/` for guidance
2. **Check metamake documentation** in `metamake/README.md`
3. **Use metamake prompts** in `metamake/prompts/` for specific tasks
4. **Iterate on integration** by re-running with refined context

## Next Steps

After successful integration:

1. **Train your team** on the new metamake-enhanced workflow
2. **Create your first metamake project** using the available templates
3. **Customize templates** to match your specific needs
4. **Develop team-specific prompts** for common project types
5. **Share feedback** and improvements with the metamake community

---

**Remember**: The integration process is iterative. Don't hesitate to refine and re-run the integration as your understanding of your project needs evolves.
