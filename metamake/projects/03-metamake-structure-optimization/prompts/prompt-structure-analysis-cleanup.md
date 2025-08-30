# Prompt: Metamake Structure Analysis and Cleanup Planning

This prompt guides comprehensive analysis of the metamake framework structure to identify inconsistencies, redundancies, and optimization opportunities. Use this prompt to systematically evaluate the current state and develop specific cleanup recommendations.

## Purpose

Generate a detailed analysis of the metamake framework structure that identifies specific issues, prioritizes them by impact, and provides actionable recommendations for optimization and cleanup.

## Analysis Framework

### 1. Root Structure Evaluation

**Objective**: Document the current organizational state and identify structural inconsistencies.

**Analysis Tasks**:

1. **Directory Organization Assessment**
   - Document all root-level directories and their stated purposes
   - Identify naming convention inconsistencies across folders
   - Note any directories that seem misplaced or have unclear purposes
   - Map the logical hierarchy and identify organizational gaps

2. **File Distribution Analysis**
   - Count files by type in each directory (markdown, templates, configs, etc.)
   - Identify files that are placed in illogical locations
   - Note any naming convention violations at the file level
   - Document file relationships and dependencies

3. **Organizational Logic Review**
   - Assess whether the current structure supports the framework's objectives
   - Identify areas where logic breaks down or becomes confusing
   - Note places where similar content is scattered across multiple locations
   - Evaluate the intuitive navigation paths for common user journeys

### 2. Content Redundancy Detection

**Objective**: Identify duplicate, overlapping, or unnecessarily redundant content across the framework.

**Analysis Tasks**:

1. **Template Overlap Analysis**
   - Compare all template files for functional similarities
   - Identify templates that serve nearly identical purposes
   - Document unique value propositions of each template
   - Note consolidation opportunities that preserve essential functionality

2. **Prompt File Redundancy Review**
   - Analyze prompt files for repeated guidance and instructions
   - Identify overlapping scope between different prompts
   - Note conflicting or contradictory instructions across prompts
   - Assess opportunities for modular prompt components

3. **Documentation Duplication Assessment**
   - Find information repeated across multiple documentation files
   - Identify conflicting information in different locations
   - Note opportunities for single sources of truth
   - Assess cross-reference opportunities to replace duplicate content

4. **Cross-Project Content Analysis**
   - Review projects for shared content that could be extracted
   - Identify common patterns that could become framework resources
   - Note project-specific variations that must be preserved
   - Assess framework-level resource utilization

### 3. Cross-Reference Validation

**Objective**: Ensure all internal links and references are accurate and properly maintained.

**Analysis Tasks**:

1. **Link Inventory and Validation**
   - Create comprehensive inventory of all markdown links
   - Test all internal cross-references for accuracy
   - Validate relative path references across the structure
   - Identify broken or outdated links

2. **Dependency Mapping**
   - Document files that reference other framework components
   - Map template dependencies on shared resources
   - Identify circular dependencies or overly complex relationship chains
   - Note missing dependencies that should exist

3. **Cross-Project Reference Audit**
   - Validate references between different projects in the framework
   - Check accuracy of framework component references from projects
   - Identify orphaned projects or components with no clear relationships
   - Assess reference maintenance overhead and fragility

### 4. Quality and Consistency Assessment

**Objective**: Evaluate consistency of formatting, style, and quality standards across the framework.

**Analysis Tasks**:

1. **Formatting Consistency Review**
   - Check markdown formatting standards across all files
   - Identify inconsistent heading structures and styles
   - Note deviations from established formatting patterns
   - Assess code block and list formatting consistency

2. **Content Quality Evaluation**
   - Review content accuracy and currency
   - Identify outdated information or obsolete guidance
   - Note incomplete or placeholder content
   - Assess technical accuracy against current best practices

3. **Naming Convention Standardization**
   - Document all naming patterns used across the framework
   - Identify inconsistencies in file and folder naming
   - Note unclear or non-descriptive names
   - Assess naming conventions for scalability and clarity

## Cleanup Recommendations Framework

### Issue Prioritization Criteria

**Critical Issues (Immediate Action Required)**:
- Broken functionality or links that prevent framework usage
- Contradictory information that confuses users
- Structural problems that impede navigation or discovery

**High Priority Issues (Next Sprint)**:
- Significant redundancies that create maintenance overhead
- Naming inconsistencies that impact user experience
- Missing dependencies that limit functionality

**Medium Priority Issues (Next Month)**:
- Optimization opportunities that improve efficiency
- Organizational improvements that enhance usability
- Content consolidation that reduces complexity

**Low Priority Issues (Future Consideration)**:
- Cosmetic inconsistencies with minimal user impact
- Minor optimization opportunities
- Enhancements that provide marginal value

### Recommendation Development

For each identified issue, provide:

1. **Specific Problem Description**: Clear explanation with examples
2. **Impact Assessment**: How the issue affects users and maintainers
3. **Recommended Solution**: Specific, actionable steps to address the issue
4. **Implementation Effort**: Estimated time and complexity for resolution
5. **Risk Assessment**: Potential negative impacts of the proposed change
6. **Validation Method**: How to verify that the solution works effectively

### Implementation Planning

Create a phased implementation plan that:

- Groups related changes for efficient execution
- Sequences changes based on dependencies
- Minimizes disruption to existing users
- Includes rollback procedures for major changes
- Provides validation checkpoints throughout the process

## Analysis Output Format

### Executive Summary
- Overall assessment of framework structural health
- Key findings and their impact on users and maintainers
- Summary of recommended actions with priorities

### Detailed Findings
- Comprehensive list of identified issues with examples
- Analysis of current state versus optimal organization
- Assessment of redundancy and optimization opportunities

### Specific Recommendations
- Prioritized list of actionable improvements
- Implementation steps for each recommendation
- Risk assessment and mitigation strategies

### Implementation Roadmap
- Phased approach to implementing recommendations
- Timeline estimates and resource requirements
- Success metrics and validation procedures

## Usage Instructions

1. **Preparation**: Ensure full access to the metamake framework repository and understanding of its intended use cases.

2. **Analysis Execution**: Work through each analysis task systematically, documenting findings with specific examples and evidence.

3. **Issue Documentation**: Categorize and prioritize all identified issues using the provided framework.

4. **Recommendation Development**: Create specific, actionable recommendations for each identified issue.

5. **Implementation Planning**: Develop a realistic roadmap for executing the recommendations.

6. **Validation**: Include methods for verifying that implemented changes achieve their intended benefits.

---

**Use this prompt to conduct systematic analysis of the metamake framework structure and generate actionable cleanup recommendations that improve organization, eliminate redundancy, and enhance user experience.**
