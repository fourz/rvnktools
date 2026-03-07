# Prompt: Iterate Roadmap for Metamake Projects

This prompt provides instructions for analyzing project state, updating roadmaps, executing development work based on roadmap priorities, and maintaining roadmap accuracy throughout the development lifecycle. Use this prompt to maintain alignment between project vision, current capabilities, and active development efforts.

## Process Overview

The roadmap iteration process follows a systematic approach:

1. **Analyze Current State**: Review existing project files, features, implementation guides, and validation checklists
2. **Update Roadmap**: Reflect current progress and adjust priorities based on actual project state
3. **Execute Development**: Work on highest-priority items from the updated roadmap
4. **Update Roadmap Again**: Document completed work and adjust future priorities

## 1. Current State Analysis

Before updating the roadmap, conduct a comprehensive analysis:

### Project Structure Assessment

- Review all files in the project directory (`projects/XX-project-name/`)
- Inventory existing features, implementation guides, and validation checklists
- Identify gaps between documented features and actual implementation
- Check alignment between `project-details.md` objectives and current capabilities

### Progress Evaluation

- Compare current state against existing roadmap milestones
- Identify completed, in-progress, and blocked items
- Assess quality and completeness of existing deliverables
- Note any scope changes or requirement shifts since last update

### Dependency Analysis

- Review external dependencies and their current status
- Identify blockers or prerequisite work needed
- Assess resource availability and constraints
- Consider integration points with other projects or systems

## 2. Roadmap Update (Initial)

Update the `ROADMAP.md` file to reflect accurate current state:

### Current Capabilities Section

- Document all completed features and implementations
- Update status of partially completed work
- Remove outdated or obsolete items
- Add any new capabilities discovered during analysis

### Priority Adjustment

- Reorder short-term goals based on current project needs
- Move completed items from goals to current capabilities
- Add newly identified requirements or opportunities
- Adjust timelines based on actual progress and constraints

### Clear Next Steps

- Identify the top 1-3 highest-priority items for immediate work
- Ensure these items have clear acceptance criteria
- Verify prerequisites are met or can be addressed
- Estimate effort and timeline for priority items

## 3. Development Execution

Work on the highest-priority roadmap items:

### Feature Development

- Create or update feature documentation in `features/` directory
- Follow Metamake standards for content structure and quality
- Ensure features align with project objectives and roadmap vision
- Include clear requirements, acceptance criteria, and success metrics

### Implementation Guides

- Develop step-by-step implementation guides in `implementation/` directory
- Test all procedures and verify technical accuracy
- Include prerequisites, troubleshooting, and cleanup steps
- Cross-reference with official documentation and best practices

### Validation Checklists

- Create comprehensive validation checklists in `validation/` directory
- Ensure checklists cover both functional and non-functional requirements
- Include clear pass/fail criteria for each validation item
- Map validation items back to feature requirements and business objectives

### Documentation Integration

- Update relevant documentation in `docs/` directory
- Ensure consistency across all project materials
- Update `README.md` if project scope or usage changes
- Maintain `project-details.md` alignment with current work

## 4. Roadmap Update (Final)

After completing development work, update the roadmap again:

### Progress Documentation

- Move completed items from goals to current capabilities
- Update status of partially completed work
- Document any lessons learned or process improvements
- Note any scope changes or requirement evolution

### Future Planning

- Adjust remaining priorities based on completed work
- Add new opportunities or requirements that emerged
- Update timelines and resource estimates
- Consider impact on medium and long-term goals

### Quality Assurance

- Ensure roadmap accurately reflects project reality
- Verify alignment between roadmap, project details, and actual capabilities
- Check that next priorities are well-defined and actionable
- Confirm roadmap supports overall project vision and objectives

## Best Practices

### Frequency and Timing

- Iterate roadmaps regularly (weekly for active projects, monthly for maintenance)
- Always iterate before major development sprints
- Update roadmaps after significant milestones or deliverables
- Conduct thorough iterations at project phase transitions

### Quality Standards

- Maintain specific, measurable roadmap items
- Ensure all items have clear owners and timelines
- Keep roadmap items aligned with project objectives
- Document assumptions and dependencies explicitly

### Communication and Collaboration

- Share updated roadmaps with stakeholders and team members
- Use roadmap updates to facilitate project planning discussions
- Ensure roadmap changes are reflected in related project documentation
- Maintain version history of significant roadmap changes

## Integration with Metamake Framework

### Framework Alignment

- Use this process with any Metamake project structure
- Reference specialized Copilot instructions for domain-specific considerations
- Maintain consistency with other Metamake prompts and templates
- Ensure roadmap iterations support documentation-as-code practices

### Tool Integration

- Leverage GitHub Copilot for roadmap analysis and content generation
- Use VS Code's editing and navigation features for efficient updates
- Reference prompt files in `prompts/` directory for consistency
- Apply quality standards from scaffold prompts to roadmap work

By following this iterative roadmap process, projects maintain alignment between vision and reality, ensure continuous progress toward objectives, and adapt effectively to changing requirements and opportunities.
