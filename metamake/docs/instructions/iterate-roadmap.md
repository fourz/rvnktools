# Iterate Roadmap Usage Guide

This guide provides comprehensive instructions for using the roadmap iteration process in Metamake projects. The roadmap iteration workflow helps maintain alignment between project vision, current capabilities, and active development efforts through systematic analysis, planning, and execution cycles.

## Overview

The roadmap iteration process is a structured approach to project management that ensures:

- **Continuous Alignment**: Keep project reality in sync with documented plans
- **Adaptive Planning**: Adjust priorities based on actual progress and changing requirements
- **Quality Delivery**: Maintain high standards while progressing toward project objectives
- **Documentation Integration**: Ensure all project materials evolve together consistently

## When to Use Roadmap Iteration

### Regular Iteration Schedule

- **Weekly Iterations**: For active development projects with frequent changes
- **Bi-weekly Iterations**: For projects with moderate development velocity
- **Monthly Iterations**: For maintenance projects or projects in planning phase
- **Event-Driven Iterations**: After major milestones, scope changes, or significant discoveries

### Specific Triggers

- Before starting a new development sprint or phase
- After completing major features or implementation guides
- When project requirements or constraints change
- Before project reviews or stakeholder meetings
- When the roadmap appears outdated or misaligned with reality

## Step-by-Step Process

### Phase 1: Current State Analysis

#### Preparation

1. Open the project directory (`projects/XX-project-name/`)
2. Review the current `ROADMAP.md` file
3. Have the `project-details.md` file available for reference
4. Prepare to take notes on discoveries and changes

#### Project Structure Assessment

- **File Inventory**: List all files in `features/`, `implementation/`, and `validation/` directories
- **Content Quality Review**: Assess completeness and accuracy of existing documentation
- **Gap Analysis**: Identify missing features, incomplete implementations, or outdated content
- **Alignment Check**: Verify consistency between project details and current capabilities

#### Progress Evaluation

- **Milestone Review**: Compare current state against roadmap milestones
- **Status Classification**: Categorize items as completed, in-progress, blocked, or not started
- **Quality Assessment**: Evaluate the thoroughness of completed work
- **Scope Changes**: Document any requirement shifts or new discoveries

#### Dependency Analysis

- **External Dependencies**: Review status of third-party tools, APIs, or services
- **Internal Dependencies**: Check prerequisite work and resource availability
- **Blocking Issues**: Identify impediments to progress
- **Integration Points**: Consider connections with other projects or systems

### Phase 2: Initial Roadmap Update

#### Current Capabilities Update

- Move completed items from goals to current capabilities section
- Update status of partially completed work with specific progress notes
- Remove obsolete or no-longer-relevant items
- Add new capabilities discovered during analysis

#### Priority Rebalancing

- Reorder short-term goals based on current project needs and constraints
- Adjust medium-term goals based on lessons learned and changing requirements
- Update long-term vision if project scope or objectives have evolved
- Consider resource availability and external factors in prioritization

#### Next Steps Clarification

- Identify 1-3 highest-priority items for immediate work
- Define clear acceptance criteria and success metrics for each priority item
- Verify that prerequisites are met or can be quickly addressed
- Estimate effort and timeline for priority work

### Phase 3: Development Execution

#### Work Planning

- Choose the highest-priority roadmap item for development
- Break down the work into manageable tasks
- Identify required resources, tools, or research
- Set specific, measurable completion criteria

#### Feature Development

- Create comprehensive feature documentation in the `features/` directory
- Include clear requirements, user stories, and acceptance criteria
- Define success metrics and validation approaches
- Ensure alignment with project objectives and roadmap vision

#### Implementation Guide Creation

- Develop step-by-step implementation guides in the `implementation/` directory
- Test all procedures in a clean environment to verify accuracy
- Include prerequisites, setup steps, and troubleshooting guidance
- Cross-reference with official documentation and industry best practices

#### Validation Checklist Development

- Create thorough validation checklists in the `validation/` directory
- Cover both functional requirements and non-functional aspects
- Provide clear pass/fail criteria for each validation item
- Map validation items to feature requirements and business objectives

#### Documentation Integration

- Update relevant documentation in the `docs/` directory
- Ensure consistency across all project materials
- Update `README.md` if project scope or usage has changed
- Maintain alignment between `project-details.md` and current work

### Phase 4: Final Roadmap Update

#### Progress Documentation

- Move newly completed items from goals to current capabilities
- Update the status of any work that became partially complete during development
- Document lessons learned and process improvements discovered
- Note any scope changes or requirement evolution that occurred

#### Future Planning Adjustment

- Adjust remaining priorities based on insights from completed work
- Add new opportunities or requirements that emerged during development
- Update effort estimates and timelines based on actual experience
- Consider the impact of completed work on medium and long-term goals

#### Quality Verification

- Ensure the roadmap accurately reflects current project reality
- Verify alignment between roadmap, project details, and actual capabilities
- Check that next priorities are well-defined and actionable
- Confirm the roadmap continues to support overall project vision and objectives

## Use Cases and Examples

### Use Case 1: New Project Setup

**Scenario**: You've just created a new Metamake project using the scaffold prompt and need to establish an initial development rhythm.

**Approach**:

1. Perform initial analysis to understand the generated project structure
2. Customize the roadmap to reflect your specific project objectives
3. Execute the first 1-2 priority items to establish momentum
4. Update the roadmap to reflect early learnings and adjust future priorities

**Benefits**: Establishes realistic expectations and creates a foundation for systematic development.

### Use Case 2: Mid-Project Course Correction

**Scenario**: Your project has been in development for several weeks, but the roadmap seems out of sync with actual progress and new requirements have emerged.

**Approach**:

1. Conduct thorough current state analysis to understand what's actually been completed
2. Update the roadmap to reflect reality and incorporate new requirements
3. Re-prioritize remaining work based on current context and constraints
4. Execute adjusted priorities and update the roadmap again

**Benefits**: Realigns project trajectory with reality and ensures continued progress toward relevant objectives.

### Use Case 3: Stakeholder Review Preparation

**Scenario**: You need to prepare for a project review meeting and want to demonstrate clear progress and future plans.

**Approach**:

1. Analyze current state to prepare accurate status reporting
2. Update roadmap to reflect completed work and remaining priorities
3. Prepare materials that show progress and future direction
4. Use the updated roadmap as the basis for stakeholder discussions

**Benefits**: Provides clear, accurate communication materials and demonstrates systematic project management.

### Use Case 4: Scope Expansion

**Scenario**: New opportunities or requirements have been identified that expand the project's scope.

**Approach**:

1. Analyze current state to establish a solid baseline
2. Update roadmap to incorporate new scope while maintaining existing commitments
3. Execute work that addresses both original and new requirements
4. Update roadmap to reflect expanded capabilities and adjusted timeline

**Benefits**: Manages scope growth systematically while maintaining project coherence and quality.

## Best Practices and Tips

### Timing and Frequency

- **Regular Schedule**: Establish a consistent iteration schedule that matches your development rhythm
- **Event-Driven**: Don't wait for scheduled iterations if significant changes or discoveries occur
- **Balanced Approach**: Balance thorough analysis with development velocity
- **Team Coordination**: Align iteration schedule with team meetings and planning cycles

### Quality Standards

- **Honesty in Assessment**: Be realistic about current capabilities and progress
- **Specific Commitments**: Make roadmap items specific, measurable, and time-bound
- **Clear Documentation**: Ensure all updates are clearly documented with rationale
- **Stakeholder Communication**: Keep relevant stakeholders informed of significant roadmap changes

### Common Pitfalls to Avoid

- **Analysis Paralysis**: Don't spend excessive time on analysis at the expense of development work
- **Wishful Planning**: Avoid roadmaps that don't reflect realistic constraints and capabilities
- **Inconsistent Updates**: Don't let roadmaps become stale or misaligned with reality
- **Scope Creep**: Be intentional about scope changes and their impact on timelines

### Integration with Other Metamake Processes

- **Scaffold Integration**: Use roadmap iteration after creating new projects with scaffold prompts
- **Documentation Alignment**: Ensure roadmap updates align with documentation-as-code practices
- **Quality Standards**: Apply the same quality standards from scaffold prompts to roadmap work
- **Template Consistency**: Use consistent formatting and structure across all project materials

## Troubleshooting Common Issues

### Issue: Roadmap Doesn't Match Reality

**Symptoms**: Completed work isn't reflected in the roadmap, or roadmap items seem irrelevant.

**Solution**: Conduct thorough current state analysis and be honest about what's actually been accomplished. Update the roadmap to reflect reality before planning future work.

### Issue: Priorities Keep Changing

**Symptoms**: Difficulty maintaining focus on roadmap items due to constantly shifting priorities.

**Solution**: Establish clear criteria for priority changes and require explicit decision-making about scope adjustments. Document rationale for priority changes in roadmap updates.

### Issue: Development Work Doesn't Follow Roadmap

**Symptoms**: Team members are working on items not reflected in the roadmap.

**Solution**: Improve communication about roadmap priorities and establish processes for proposing and approving changes to planned work.

### Issue: Roadmap Updates Are Inconsistent

**Symptoms**: Different team members update roadmaps differently, leading to inconsistency.

**Solution**: Establish clear templates and guidelines for roadmap updates. Use this guide as a reference and provide training on the iteration process.

## Measuring Success

### Key Metrics

- **Alignment Score**: How well does the roadmap match actual project state?
- **Completion Rate**: What percentage of roadmap commitments are being met?
- **Quality Consistency**: How consistent is the quality of deliverables across iterations?
- **Stakeholder Satisfaction**: Are stakeholders confident in project direction and progress?

### Success Indicators

- Roadmaps accurately reflect project reality
- Development work consistently aligns with roadmap priorities
- Project objectives are being met systematically
- Team members understand and follow project direction
- Stakeholders have confidence in project management and progress

By following this comprehensive guide, you'll be able to effectively use the roadmap iteration process to maintain project alignment, ensure quality delivery, and adapt to changing requirements while preserving project coherence and momentum.
