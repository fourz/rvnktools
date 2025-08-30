# RVNK Metamake Project Management Instructions

## Metamake Project Management Integration

When the user explicitly requests metamake functionality using phrases like "use metamake to..." or "with metamake", activate the integrated project management capabilities:

### Metamake Capabilities Available

- **Project Planning and Organization**: Structure complex features, refactoring tasks, and development phases
- **Document-Based Project Management**: Create structured project documentation with templates and validation checklists
- **Implementation Roadmaps**: Break down large architectural changes (like RVNKCore integration) into manageable phases
- **Quality Assurance Frameworks**: Generate validation checklists and testing procedures for plugin features
- **Cross-Plugin Architecture Planning**: Coordinate development across RVNK ecosystem plugins

### Metamake Project Structure

Metamake projects are organized in the `metamake/projects/` directory with this structure:

```text
metamake/projects/XX-project-name/
├── README.md              # Project overview and purpose
├── ROADMAP.md             # Implementation status and timelines
├── COPILOT-INSTRUCTIONS.md # Project-specific guidance
├── project-details.md     # Context, workflow, and objectives
├── features/              # Feature specifications
├── implementation/        # Step-by-step implementation guides
├── validation/           # Testing and quality assurance checklists
└── docs/                 # Supporting documentation
```

### RVNK-Specific Metamake Context

**Project Domain**: Minecraft Plugin Ecosystem Development
**Technology Stack**: Java/Maven, Spigot/Paper API, MySQL/SQLite, Jetty REST API, YAML Configuration
**Current Focus**: RVNKCore architectural refactor and plugin ecosystem consolidation

**Key Project Areas for Metamake Integration:**
- RVNKCore Phase 1/Phase 2 implementation planning
- Announcement system migration (YAML → Database)
- REST API framework expansion
- Cross-plugin service integration
- Database layer abstraction completion
- Web integration strategy implementation

**Documentation Integration:**
- Main documentation: `docs/` (requirements, implementation, API references)
- Project status: `README.md` and `ROADMAP.md` files
- Architecture guides: `docs/implementation/` and `docs/requirements/`
- API documentation: `docs/api/` for Java and REST endpoints

### Usage Examples

```text
"Use metamake to plan the Phase 2 RVNKCore implementation"
"With metamake, create a validation checklist for the announcement service migration"
"Use metamake to organize the cross-plugin REST API integration project"
"With metamake, structure the MySQL ConnectionProvider implementation project"
```

When metamake is invoked, reference the `metamake/prompts/` directory for specialized prompts and use the `metamake/template/` directory for project structure templates.

### Performance Rules
- Don't async operations that take <10ms
- Batch operations instead of individual async calls
- Consider thread pool limits
- **For caching strategies**: See [Performance and Monitoring](#performance-and-monitoring) section in main copilot instructions
