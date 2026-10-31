# peony-application

# Project

Full-stack app. Backend: Spring Boot 4.x, Java 25, Gradle, PostgreSQL, Spring Data JPA (Hibernate 7), Liquibase, Lombok, JWT auth.
Frontend: React Native + TypeScript + Expo, one codebase for iOS, Android and web (react-native-web). REST/JSON between them.

## Commands
- Backend build/test: `./gradlew build`, `./gradlew test`
- Backend run: `./gradlew bootRun`
- Frontend run: `npx expo start`
- Frontend checks: `npx tsc --noEmit`, `npm run lint`
  (Adjust to your actual paths and scripts.)

## Working rules
- If something is ambiguous, ask me. Do not assume.
- If unsure about an API, version detail or library behavior, say so. Never invent classes, methods, annotations, props or dependencies.
- Follow existing project style. Do not refactor unrelated code.
- Read the error and stack trace first when debugging. Name the most likely root cause, then fix. If several causes are plausible, list them by likelihood.
- Mention security, performance or concurrency problems you notice in one or two lines.

## Backend (Spring Boot 4 / Java 25)
- Modern Java: records for DTOs, sealed types, pattern matching, switch expressions, text blocks, `var` where it helps readability.
- Constructor injection only (Lombok `@RequiredArgsConstructor`). No field injection.
- Use `jakarta.*` for Jakarta EE APIs (persistence, servlet, validation, transaction). Never `javax.persistence`, `javax.servlet`, `javax.validation`. JDK packages like `javax.crypto` are fine.
- Jackson 3: use `tools.jackson.*` packages, not `com.fasterxml.jackson.databind`. Annotations stay in `com.fasterxml.jackson.annotation`.
- Layers: Controller (thin: validation + mapping) -> Service (`@Transactional`) -> Repository. Never return JPA entities from the API; map to DTO records.
- Validate with Jakarta Validation (`@Valid`, `@NotBlank`). Errors via `@RestControllerAdvice` and RFC 9457 `ProblemDetail`.
- Use `RestClient` or `@HttpExchange` clients, not `RestTemplate`.
- Config via `@ConfigurationProperties` records. Never hard-code secrets; use environment variables.
- Liquibase owns the schema. Never edit an already-applied changeset; add a new one. Do not use `ddl-auto` to change the schema.
- Tests: JUnit, Mockito, `@WebMvcTest`, `@DataJpaTest`, Testcontainers.
- Virtual threads and structured logging only when asked.

## Imports (Java)
- Explicit imports only. No wildcard imports.
- Never use fully qualified class names inline. Import and use the simple name.
  Wrong: `java.util.List<com.example.dto.UserDto> users`
  Right: `List<UserDto> users`
- Exception: qualify one class only when two imported classes share a simple name.

## Frontend (React Native / TypeScript / Expo)
- Functional components and hooks only. Strict TypeScript; no `any` unless justified in a comment.
- TypeScript: named imports (`import { View, Text } from 'react-native'`). Avoid `import * as X` unless a library requires it.
- Cross-platform by default. For platform differences use `Platform.select` or `.web.tsx` / `.ios.tsx` / `.android.tsx`, and say why.
- Only React Native primitives (View, Text, Pressable, FlatList). No HTML elements.
- `StyleSheet.create` (or the project's styling lib). Responsive flexbox layouts for small phones and wide web screens.
- `FlatList`/`SectionList` for long lists, never `ScrollView` + `map()`.
- API calls live in a service layer with typed request/response models matching backend DTOs. Every screen handles loading, error and empty states.
- Tokens: `expo-secure-store` on native, never AsyncStorage. Note the web fallback.
- Any suggested library must support iOS, Android and web. If not, say so and offer an alternative.

## Code quality
- Clear names, small functions, no dead code. Comment only the "why".

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
