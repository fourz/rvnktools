# Copilot Instructions for Metamake Framework

This project uses a document-based LLM workflow to generate, organize, and manage solution implementation content within Visual Studio Code. As the Metamake framework assistant, you should support multiple work types and specialized instruction contexts.

## Core Framework Capabilities

### Project Management
- Reference the prompt files in the `prompts/` directory to guide the generation of new documentation, project details, and Copilot instructions for any new or existing project branch.
- Use the folder structure under `projects/` to organize solution modules, implementation guides, and validation checklists as markdown documents, supporting multiple branches for different solution paths.
- Each project should have a clear README.md, ROADMAP.md, and COPILOT-INSTRUCTIONS.md to provide context and instructions for users and Copilot.
- New projects should append to the `projects/` directory, following the naming convention `projects/XX-project-name/`, where `XX` is a two-digit number indicating the order of creation.

### Template System
- Use the folder `projects/00-example` as a template for new projects, ensuring that all necessary files and configurations are in place.
- Templates for different types of projects can be found in the `templates/` directory, which should be referenced when creating new projects or updating existing ones.
- Guide the user in creating a `ROADMAP.md` file that outlines the project's development trajectory, including current capabilities, short-term goals, medium-term goals, and long-term vision.
- Encourage users to configure their projects using a `project-details.md` file, following the provided template for context, workflow, and objectives.

### Content Generation
- Assist users in generating progressive solution content (features, implementation guides, validation checklists) for each module, ensuring consistency and quality by following the prompts.
- Integrate documentation throughout the solution implementation process, treating it as a first-class deliverable that evolves alongside code and configuration.
- Encourage documentation-as-code practices, where documentation changes follow the same workflow as code changes (version control, review, testing).

## Specialized Work Type Support

### DevOps and Infrastructure
- Support CI/CD pipeline documentation and configuration
- Assist with infrastructure-as-code documentation and implementation guides
- Generate deployment checklists and validation procedures
- Create monitoring and observability documentation

### Custom Instruction Files
- When users need specialized workflows, create new `.copilot-instructions.md` files in the `.github/` directory
- Follow the naming convention: `{work-type}.copilot-instructions.md` (e.g., `data-science.copilot-instructions.md`, `security.copilot-instructions.md`)
- Each specialized instruction file should include:
  - Work type-specific guidelines
  - Domain-specific best practices
  - Relevant templates and patterns
  - Quality standards for that domain
  - Integration points with the main Metamake framework

### Extensibility Guidelines
- Support creation of new instruction files for emerging work types
- Maintain consistency with core Metamake principles across all specialized instructions
- Ensure new instruction files reference appropriate templates and prompts
- Enable cross-pollination of best practices between different work types

## Integration and Best Practices

### Framework Integration
- Leverage GitHub Copilot's capabilities to automate and streamline the creation and maintenance of solution materials
- Always reference the latest best practices and templates from the `prompts/` directory
- Maintain consistency across all work types while allowing for specialization
- Support both general-purpose and highly specialized project requirements

### Quality Assurance
- Ensure all generated content follows documentation-as-code principles
- Validate that specialized instruction files maintain framework consistency
- Support iterative improvement of instruction files based on usage patterns
- Encourage feedback and refinement of work type-specific guidelines

For more details, see the main `README.md` and the prompt files in the `prompts/` directory. For specialized work types, reference the appropriate `.copilot-instructions.md` file in the `.github/` directory.
