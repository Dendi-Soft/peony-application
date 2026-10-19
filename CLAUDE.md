# peony-application

Backend service (Java/Kotlin, Gradle)

## Agent skills

### Issue tracker

Issues live on GitHub at https://github.com/Dendi-Soft/peony-application/issues. Use the `gh` CLI to interact with them. See `docs/agents/issue-tracker.md`.

### Triage labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses single-context domain docs: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Coding standards

Code style is enforced by Checkstyle and Spotless; the pre-commit hook runs `./gradlew check` on every commit. See `docs/agents/coding-standards.md` for the enforced baseline, judgement rules, and the review checklist.

## Development setup

1. Clone the repository
2. Enable the pre-commit guardrail: `git config core.hooksPath hooks`
3. Start with docker compose:
   ```sh
   docker compose watch
   ```
