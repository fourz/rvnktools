# Metamake Integration Prompt

Use this prompt to integrate metamake project management framework into any existing project structure.

## Purpose

This prompt guides GitHub Copilot in creating a customized `.github/copilot-instructions.md` file for any project that wants to adopt metamake project management capabilities while maintaining their existing organizational structure.

## Prerequisites

- Metamake folder structure should be placed in the project root
- Project context and requirements should be documented
- Existing project structure should be analyzed

## Integration Process

### Step 1: Project Analysis

Analyze the target project to identify:

1. **Project Domain and Technology Stack**
   - Primary technology stack (e.g., Python/Django, Node.js/React, .NET, DevOps, etc.)
   - Domain focus (e.g., web development, data science, infrastructure, mobile, etc.)
   - Existing folder structure and organization patterns

2. **Current Documentation Structure**
   - Location of project documentation (docs/, documentation/, wiki/, etc.)
   - Existing project planning approach
   - Script/automation organization
   - Configuration file locations

3. **Project-Specific Requirements**
   - Key components or services
   - Infrastructure elements
   - Development workflow preferences
   - Quality assurance processes

### Step 2: Folder Structure Mapping

Create a mapping of standard metamake references to project-specific folders:

- `[METAMAKE_FOLDER]` → Actual metamake folder name (default: "metamake")
- `[DOCS_FOLDER]` → Project documentation folder (e.g., "docs", "documentation", "wiki")
- `[SCRIPTS_FOLDER]` → Automation scripts folder (e.g., "scripts", "automation", "tools")
- `[PROJECT_PLANS_FOLDER]` → Project planning subfolder within docs
- `[COMPONENT_FOLDER]` → Component/service documentation subfolder
- `[ARCHITECTURE_FOLDER]` → Architecture/design documentation subfolder
- `[INVENTORY_FILE]` → Resource inventory file name

### Step 3: Content Customization

Based on project analysis, customize these template sections:

#### Project-Specific Infrastructure

Replace `[REPLACE_WITH_PROJECT_SPECIFIC_INFRASTRUCTURE]` with:

- Key systems, services, or platforms
- Development/production environments
- External integrations or dependencies
- Cloud services or hosting details

#### Domain Knowledge Areas

Replace knowledge sections with relevant technical domains:

- Primary technology framework knowledge
- Integration patterns and best practices
- Security and compliance requirements
- Performance and scalability considerations

#### Workflow Customization

Adapt workflow sections to match:

- Existing development processes
- Code review and quality assurance
- Deployment and release management
- Documentation maintenance practices

### Step 4: Generate Copilot Instructions

Using the template `/template/metamake-integration-template.copilot-instructions.md`, create a customized `.github/copilot-instructions.md` file by:

1. **Replacing all bracketed placeholders** with project-specific values
2. **Customizing domain knowledge sections** with relevant technical information
3. **Adapting workflow sections** to match existing project processes
4. **Ensuring folder path consistency** throughout the document
5. **Adding project-specific formatting guidelines** and examples

### Step 5: Validation Checklist

Verify the integration by checking:

- [ ] All bracketed placeholders have been replaced
- [ ] Folder paths are consistent and accurate
- [ ] Domain knowledge reflects project technology stack
- [ ] Workflow sections align with existing processes
- [ ] Formatting guidelines include relevant examples
- [ ] Metamake references point to correct folder structure
- [ ] Technical considerations match project architecture

## Special Considerations

### Variable Folder Names

When projects use non-standard folder names:

- **Consistency**: Ensure all references use the same folder names
- **Relative Paths**: Maintain accurate relative path references
- **Documentation Links**: Update any existing documentation that references changed paths
- **Script Paths**: Verify automation scripts use correct folder references

### Multi-Project Repositories

For repositories containing multiple projects:

- Consider separate metamake instances per project
- Use project-specific folder prefixes
- Maintain clear boundaries between project documentation
- Ensure metamake project names avoid conflicts

### Legacy Integration

When integrating with established projects:

- Preserve existing documentation structure where possible
- Create migration paths for existing project plans
- Maintain backward compatibility with current workflows
- Document changes and provide transition guidance

## Example Usage

```text
# For a Python/Django web application project:

Given project context:
- Technology: Python/Django with PostgreSQL
- Structure: Standard Django project with docs/ and scripts/
- Domain: E-commerce web application
- Infrastructure: AWS deployment with Docker

Generate a customized .github/copilot-instructions.md that:
1. References Django/Python best practices
2. Maps to existing docs/ and scripts/ folders
3. Includes e-commerce domain knowledge
4. Integrates with AWS deployment workflow
5. Supports Docker containerization approach
```

## Output Requirements

The generated `.github/copilot-instructions.md` should:

- Be immediately usable without further customization
- Maintain metamake integration capabilities
- Reflect accurate project structure and processes
- Include relevant technical knowledge for the domain
- Provide clear guidance for both simple and complex project management

---

**Usage**: Provide this prompt along with your project context, requirements, documentation, and any specific management or QA feedback to generate a fully customized metamake integration for your project.
