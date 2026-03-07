# Prompt: Scaffold Details and Accuracy for Metamake Projects

This prompt provides detailed instructions for generating, organizing, and verifying solution implementation content within the Metamake framework, with a focus on technical accuracy, documentation integration, and process consistency. Use this prompt when scaffolding new projects or expanding existing ones to ensure all materials meet Metamake's standards for quality and reliability.

## 1. Project Scaffolding Process
- Begin by referencing the `projects/00-example/` directory as a template for structure, file naming, and organization.
- For each new project, create a dedicated folder under `projects/` following the naming convention `XX-project-name/`.
- Ensure the following files are present and customized for the project:
  - `README.md`: Overview and purpose of the project.
  - `ROADMAP.md`: Outlines current capabilities, short/medium/long-term goals, and vision.
  - `COPILOT-INSTRUCTIONS.md`: Clear, actionable instructions for users and Copilot.
  - `project-details.md`: Context, workflow, and objectives, using the template from `template/`.
- Organize content into `features/`, `implementation/`, `validation/`, and `docs/` subfolders, ensuring modular solution flow and clear mapping between features, implementation guides, validation checklists, and documentation.
- Reference prompt files in `prompts/` for guidance on documentation, project details, and Copilot instructions.

## 2. Accuracy and Consistency Guidelines
- Follow the chain-of-thought process:
  1. **Initial Research**: Use official documentation (e.g., Microsoft Learn for Azure) to define scope, terminology, and dependencies.
  2. **Structural Consistency**: Maintain uniform formatting and organization across all content types. Connect implementation guides to their related features and design validation checklists to reinforce both functional and non-functional requirements.
  3. **Technical Verification**: Cross-reference all technical steps and details with official sources. Include version numbers, dates, and notes on UI or feature changes. Provide reference links for further exploration.
  4. **Web Search Integration**: When documenting procedures or UI steps, verify with recent screenshots or documentation. Prioritize results from official sources and reputable community forums.

## 3. Implementation Steps
- When creating or updating content:
  - Start with the latest official documentation as your primary source.
  - Test implementation steps in a live environment when possible.
  - Clearly specify prerequisites, error handling, and cleanup steps in implementation guides.
  - For validation checklists, ensure items are directly tied to solution requirements and provide clear acceptance criteria.
- Use the provided templates and prompts to maintain consistency and quality.
- Document any assumptions, version-specific notes, or potential changes in the technology.
- Integrate documentation writing and review as part of every feature/module.

## 4. Quality Assurance Checklist
- [ ] Content aligns with current official documentation and service capabilities
- [ ] Implementation guides and validation checklists are verified and complete
- [ ] Terminology and formatting are consistent across all materials
- [ ] Solution progression is logical and builds on previous modules
- [ ] Real-world scenarios and applications are included
- [ ] Reference links to official documentation are provided
- [ ] Validation checklists are technically feasible and comprehensive
- [ ] Documentation is integrated and up-to-date

By following this prompt, all new and updated Metamake projects will maintain high standards of accuracy, consistency, and solution value, supporting both implementers and contributors in building reliable, up-to-date technical content with integrated documentation.
