# Coding Standards

## Enforced baseline

Google Java Style is enforced by **Checkstyle** (`checkstyle.xml` and
`checkstyle-suppressions.xml`, both at the repository root) and **Spotless**
(googleJavaFormat, configured in `build.gradle`). Those config files are the
single source of truth for mechanical rules - naming, imports, whitespace,
line length, Javadoc presence.
When a mechanical rule is in question, read the config; this document does not
restate it.

**Guardrail:** the pre-commit hook (`hooks/pre-commit`, wired via
`git config core.hooksPath hooks`) runs `./gradlew check` on every commit.

```bash
./gradlew check                        # checkstyle + spotless + tests (tests need Docker)
./gradlew checkstyleMain spotlessCheck # fast: style only
```

Without Docker the hook skips tests with a visible warning; style and
checkstyle always run.

## Judgement rules (not mechanically enforced)

- **Javadoc quality** on the public API: a real summary, no filler
  ("This method returns..."), `@param`/`@return`/`@throws` that carry meaning.
  Private elements need none.
- **Names carry meaning**: descriptive over clever; `var` only when the type
  is obvious from the right-hand side.
- **Exceptions**: catch the specific type, never bare `Exception`; an empty
  catch block must say why ignoring is safe.
- **Comments explain why**, not what. TODOs: `TODO:` + the task.
- **Tests**: named `test[Subject][Action]_[Outcome]_[Condition]`; the test's
  Javadoc states the scenario (precondition + expected behavior); assertions
  target behavior, not implementation detail.

## Suppressions and exceptions

- Suppress only for a legitimate reason, and only in
  `checkstyle-suppressions.xml`.
- A style rule that conflicts with a design decision: record the decision in
  an ADR under `docs/adr/` before suppressing.

## Review checklist

Mechanical items are already enforced by `./gradlew check`; this checklist is
the judgement layer plus the spec axis:

- [ ] `./gradlew check` passes
- [ ] Public API has real Javadoc, not filler
- [ ] Naming, structure, and test coverage meet the judgement rules above
- [ ] **Spec axis - PR closes an issue:** every acceptance criterion in the
      issue holds against the *final* diff. A criterion the diff no longer
      satisfies is a review failure, even if the code is clean.
- [ ] **Verification currency:** if the branch was refactored since the last
      verification comment (triage or review), re-run the verification against
      the current head and update the record before merging.
- [ ] **Verification provenance:** every verification comment names the commit
      SHA it verified; if verified-at and merged-at differ, the currency check
      above happens.
- [ ] **No scratch artifacts:** review notes and working documents live in PR/issue
      comments (or a local, ignored file), never committed to the repo root or the diff.

## Reference

- Google Java Style: https://google.github.io/styleguide/javaguide.html
- Checkstyle: http://checkstyle.org
- ADRs: `docs/adr/` - architectural decisions that may override defaults
