
# Metamake: Document-Based LLM Workflow for Solution Implementation

Metamake is a framework for building and managing document-based solution implementation workflows using GitHub Copilot within Visual Studio Code. It enables users to create, organize, and extend solution-driven projects by generating modular features, implementation guides, and validation checklists as markdown documents. The system is designed for flexibility, supporting multiple project branches and customizable workflows.

## High-Level Overview

- **Document-Driven Solution Building:** Each feature, implementation guide, and validation checklist is a markdown document, generated and managed within a structured folder hierarchy.
- **Progressive Solution Development:** Solution materials are organized into modules, with each module containing feature documentation, hands-on implementation steps, and validation criteria.
- **Copilot Integration:** GitHub Copilot is used to assist in generating and evolving solution documentation, code examples, and project plans.
- **VS Code Native:** Designed for seamless use within Visual Studio Code, leveraging its editing, navigation, and extension capabilities.
- **Multi-Branch Support:** The folder structure supports multiple branches or tracks, enabling parallel development of different solution paths or project variants.

## Project Structure

- `README.md` – Project overview and usage instructions
- `ROADMAP.md` – Development trajectory and future goals
- `.github/` – Copilot instruction files for different work types and framework guidance
- `docs/` – Additional documentation including instruction management guides
- `prompts/` – Contains prompt files for generating documentation and configuring new projects:
  - `prompt-readme.md` – Instructions for generating a comprehensive README
  - `prompt-roadmap.md` – Roadmap generation guidelines
  - `prompt-copilot-instructions.md` – Copilot context and usage instructions
  - `prompt-scaffold.md` – Basic scaffolding guidance
  - `prompt-scaffold-details-accuracy.md` – Detailed scaffolding with quality assurance
  - `prompt-iterate-roadmap.md` – Iterative roadmap management and development workflow
- `projects/` – Example and user-created project branches
- `template/` – Project templates for different configurations

## Getting Started

Metamake provides built-in scaffolding tools to help you quickly create and maintain high-quality solution implementation projects:

1. **Create a New Project:**
   - Use the basic scaffold prompt (`prompts/prompt-scaffold.md`) to generate your project structure
   - This creates a new directory under `projects/` with all required files and folders
   - Example project available in `projects/00-example/`

2. **Develop with Accuracy:**
   - Reference the detailed scaffold prompt (`prompts/prompt-scaffold-details-accuracy.md`) while creating content
   - Follow the included quality assurance checklist
   - Create features, implementation guides, and validation checklists that map to clear solution requirements
   - Verify technical content against official documentation

3. **Maintain Quality:**
   - Use the scaffold prompts as iterative guides throughout development
   - Ensure all new materials maintain consistency with existing content
   - Review changes against the quality checklist before finalizing
   - Share prompts with contributors to align on standards

4. **Iterate and Evolve:**
   - Use the roadmap iteration prompt (`prompts/prompt-iterate-roadmap.md`) to maintain project alignment
   - Regularly analyze current state and update roadmaps based on actual progress
   - Execute development work based on roadmap priorities and update roadmaps again when complete
   - Maintain continuous alignment between project vision and reality

The scaffolding system helps maintain Metamake's standards for solution value and technical accuracy while simplifying the project creation process. The iterative roadmap system ensures projects stay aligned with their objectives and adapt effectively to changing requirements.

## Specialized Work Types: Usage and Extensibility Guide

Metamake supports diverse development workflows through specialized Copilot instruction files. This extensible system allows you to customize the framework for specific domains while maintaining consistency across projects.

### How to Use Specialized Instructions

- **Domain-Specific Guidance**: Each work type provides tailored instructions for specific development contexts
- **Flexible Integration**: Combine multiple instruction files as needed for complex projects
- **Consistent Framework**: All specializations build upon core Metamake principles while adding domain expertise
- **Project Integration**: Reference appropriate instruction files in your project's `project-details.md`

### Available Work Types

The `.github/` directory contains specialized instruction files:

- **Core Framework**: `copilot-instructions.md` - Base instructions for all projects
- **DevOps Integration**: `devops.copilot-instructions.md` - Infrastructure and deployment workflows
- **Project Management**: `projects.copilot-instructions.md` - Project-specific guidance and structure
- **Storyboard Planning**: `storyboard-planner.copilot-instructions.md` - Visual planning and design workflows

### Creating New Work Types

To extend Metamake for new domains:

1. **Use the Template**: Start with `docs/instructions/instruction-template.copilot-instructions.md`
2. **Follow Naming Convention**: Use `{work-type}.copilot-instructions.md` format
3. **Maintain Consistency**: Align with core framework principles while adding specialized guidance
4. **Document Integration**: Update project templates and documentation to reference new work types

### Management and Best Practices

For comprehensive guidance on creating, organizing, and maintaining specialized instruction files, see `docs/instructions/instruction-management-guide.md`. This guide covers:

- File organization and naming conventions
- Content guidelines and quality standards
- Integration patterns with projects
- Maintenance and evolution strategies
- Troubleshooting common issues

## Usage of Prompts

- The prompt files in `prompts/` are designed to guide both users and Copilot in generating high-quality, consistent documentation and solution content.
- Reference these prompts when starting new projects or extending existing ones to ensure best practices and project consistency.
- Use specialized instruction files in `.github/` alongside prompts to provide domain-specific guidance while maintaining framework consistency.

## Example Projects

- See `projects/00-example/` for sample project structures and content organization.

---

For more details on configuring and extending your solution implementation workflow, see the prompt files in the `prompts/` directory and the instruction management guide in `docs/instructions/instruction-management-guide.md`.

