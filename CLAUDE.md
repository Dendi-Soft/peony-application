# peony-application

Bankend service (Java/Kotlin, Gradle)

## Agent skills

### Issue tracker

Issues live on GitHub at https://github.com/Dendi-Soft/peony-application/issues. Use the `gh` CLI to interact with them. See `docs/agents/issue-tracker.md`.

### Triage labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses single-context domain docs: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Coding standards

Agents must follow Google Java Style conventions enforced by Checkstyle and Spotless. See `docs/agents/coding-standards.md` for detailed rules and a quick reference guide.

## Development setup

1. Clone the repository
2. Start with docker compose:
   ```sh
   docker compose watch
   ```
