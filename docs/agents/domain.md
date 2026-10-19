# Domain Docs

## Layout: Single-Context

This repo uses single-context domain docs at the repo root:

- **Glossary**: `/home/adi/Workspace/peony-application/GLOSSARY.md` - Shared terminology and naming conventions
- **ADRs**: `/home/adi/Workspace/peony-application/docs/adr/` - Architectural decisions and design history
- **Coding Standards**: `/home/adi/Workspace/peony-application/docs/agents/coding-standards.md` - Code style rules for agents

## Agent Reference Files

Additional agent-specific documentation:

- **Issue Tracker**: `docs/agents/issue-tracker.md` - How to work with GitHub issues
- **Triage Labels**: `docs/agents/triage-labels.md` - Mapping between roles and labels

When skills read domain terminology or architectural decisions, they refer to these single locations.

### Reading Order

1. Start with `docs/adr/` for architectural context and design history
2. Then `GLOSSARY.md` for shared terminology  
3. Then `docs/agents/coding-standards.md` for code style rules

This ordering ensures you understand the "why" before the "what", and follow conventions consistently.