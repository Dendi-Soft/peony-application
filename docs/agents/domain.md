# Domain Docs

## Layout: Single-Context

This repo uses single-context domain docs at the repo root:

- **Glossary**: `GLOSSARY.md` - Shared terminology and naming conventions
- **ADRs**: `docs/adr/` - Architectural decisions and design history
- **Coding Standards**: `docs/agents/coding-standards.md` - Enforced baseline, judgement rules, and the review checklist

## ADR Status Discipline

Each ADR carries a **Status** line. Only `Accepted` ADRs describe the current
design; `Superseded` ADRs are history - follow their *Supersedes* /
*Superseded-by* pointers to the ADR that is in force. When an issue or PR body
conflicts with an Accepted ADR, the ADR wins.

## Agent Reference Files

- **Issue Tracker**: `docs/agents/issue-tracker.md` - How to work with GitHub issues
- **Triage Labels**: `docs/agents/triage-labels.md` - Mapping between roles and labels

## Reading Order

1. `docs/adr/` for architectural context (check each ADR's Status line)
2. `GLOSSARY.md` for shared terminology
3. `docs/agents/coding-standards.md` for style rules and the review checklist

This ordering ensures you understand the "why" before the "what", and follow conventions consistently.
