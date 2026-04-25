# AI TOOL GUIDANCE

This file provides guidance when working with code in this repository.

## Technology Stack

This is a Vaadin application built with:
- Java
- Spring Boot
- Spring Data JPA with PostgreSQL
- Spring AI vector storage backed by pgvector
- Maven build system

## Development Commands

These commands are backed by `pom.xml`, `compose.yml`, and the current test tree.

### Local Services
```bash
docker compose up -d postgres docling  # PostgreSQL/pgvector and Docling services
```

`compose.yml` exposes PostgreSQL on `${POSTGRES_PORT:-4321}` and Docling on `${DOCLING_PORT:-5001}`.

### Running the Application
```bash
./mvnw                  # Uses the Maven default goal: spring-boot:run
./mvnw spring-boot:run  # Explicit development mode
```

The app uses `server.port=${PORT:8080}` from `src/main/resources/application.yml`.

### Testing
```bash
./mvnw test                                      # Run the current test suite
./mvnw test -Dtest=StudentQuestionExchangeTest  # Chat question exchange unit coverage
./mvnw test -Dtest=AskStudentQuestionToolTest   # Spring AI tool wrapper coverage
./mvnw test -Dtest=DoclingRealIntegrationTest   # Requires real Docling at docling.base-url, default http://localhost:5001
```

### Formatting and Packaging
```bash
./mvnw spotless:check  # Verify Spotless/Cleanthat/google-java-format rules
./mvnw package         # Build the application JAR and Vaadin frontend
```

## Architecture

This project follows a **feature-based package structure** rather than traditional layered architecture. Code is organized by functional units (features), not by technical layers.

### Package Structure

- **`com.wornux.Application`**: Spring Boot entry point.
- **`com.wornux.MainLayout`**: Vaadin `AppLayout` shell with the chat/document drawer and conversation timeline.
- **`com.wornux.chat`**: Chat UI, Spring AI `ChatClient` wiring, advisors, PostgreSQL chat memory, tutor prompts, routing, profile inference, and tools.
- **`com.wornux.documentingest`**: PDF ingestion, Docling client integration, cataloging, document review UI, embeddings, and pgvector-backed retrieval.
- **`src/main/resources/tutor`**: `.st` prompt resources loaded through `TutorPromptResources`.
- **`src/main/resources/db/migration`**: Flyway migrations for chat, document ingestion, cataloging, and vector metadata.

### Key Architecture Patterns

1. **Feature Packages**: Each feature is self-contained with its own UI, business logic, data access, and tests
2. **Navigation**: Views use `@Route(..., layout = MainLayout.class)` or the shared `@Layout` shell; drawer links are hand-built in `MainLayout`
3. **Service Layer**: Use `@Transactional` for write operations and `@Transactional(readOnly = true)` for read operations
4. **Validation**: Keep domain invariants close to the entity/value object and keep request/UI validation at the boundary
5. **Dependency Injection**: Constructor injection throughout (no @Autowired on fields)
6. **Design Patterns**: Use established Java/Spring patterns when they reduce real complexity. Prefer Strategy for runtime behavior choices, Factory for type-driven creation, Adapter for third-party boundaries, Builder for complex immutable objects, and Spring events for decoupled reactions. Do not add pattern-shaped ceremony when direct code is clearer.

## Prompt Management

- Tutor prompts live in `.st` files under `src/main/resources/tutor/`.
- Load `.st` resources through `com.wornux.chat.prompt.TutorPromptResources`; do not scatter prompt literals through services, advisors, or views.
- Keep prompt files focused by role: base identity, routing policy, guard policy, and examples.
- When prompt behavior changes, test the behavior at the caller boundary that uses the prompt. Resource-exists tests alone are not enough.
- If a prompt needs dynamic context, compose that context in Java and keep the stable instruction text in `.st`.

## Spring AI Rules

- This project uses Spring AI `2.0.0-M4`; verify the exact version in `pom.xml` before relying on API details.
- For every Spring AI design or code decision, use the local documentation checkout at `spring-ai-docs/` as the source of truth. This folder is intentionally gitignored.
- Use the `$spring-ai-docs-lookup` skill for Spring AI work. When subagents are available and delegation is authorized, use a subagent for the documentation lookup and report the doc paths, findings, and proof.
- Spring AI changes include ChatClient/ChatModel usage, advisors, tools, structured output, memory, embeddings, vector stores, pgvector, Ollama config, prompt resource handling, observability, and Spring AI test strategy.
- If the local docs are missing, incomplete, or inconsistent with `pom.xml`, stop and call out the mismatch before changing Spring AI code.

## Testing Standard

- Tests must increase confidence in the behavior users or maintainers actually care about. A pretty green test that only mocks away the risk is worse than no test because it lies.
- Prefer the narrowest test that exercises the real boundary:
  - pure domain logic: unit test
  - JPA/Flyway/PostgreSQL behavior: integration test with PostgreSQL/Testcontainers or the repo's real dev service
  - Spring AI advisors/tools/vector stores/model client behavior: integration or contract test at the Spring AI boundary, backed by local Spring AI docs
  - Vaadin component state and server-side UI behavior: Vaadin UI unit tests
  - critical browser flows, client-side behavior, or visual regressions: Vaadin TestBench
- Do not replace a real integration concern with mocks just to get a fast green pass.
- Keep scoped verification by default (`./mvnw test -Dtest=...`), then broaden only when the change touches shared behavior.

## Vaadin UI and Styling

- For Vaadin work, use the Vaadin plugin/skills and verify API details there instead of guessing.
- Prefer Vaadin layout APIs, component variants, theme utilities, and Tailwind utility classes over new custom CSS.
- CSS is allowed only for behavior or styling that cannot be expressed cleanly with Vaadin/Tailwind. Keep it local, named for the feature, and remove it as soon as it becomes stale.
- When Java class names, component structure, or UI state change, remove matching stale CSS selectors in the same change.
- Avoid duplicating styles across Java and CSS. Pick one owner; Tailwind/utilities in Java should be the default for new UI.
- For critical flows, use TestBench real-browser tests. For smaller component logic, prefer browserless Vaadin UI unit tests.

## Adding New Features

When creating a new feature:
1. Create a new package under `com.wornux` (e.g., `com.wornux.myfeature`)
2. Keep UI, service/domain logic, persistence, and tests inside that feature package unless there is a real shared abstraction.
3. Follow the current `chat` and `documentingest` package patterns instead of resurrecting the deleted example feature.
4. Add Flyway migrations for schema changes and real-boundary tests for persistence, Spring AI, Docling, or Vaadin flows.

## Vaadin-Specific Notes

- **Server-side rendering**: UI components are Java classes extending Vaadin components
- **App shell**: `Application` configures Aura and `styles.css` with `@StyleSheet`; do not assume a Lumo theme folder exists
- **Routing**: `@Route("")` for root path, `@Route("path")` for specific paths
- **Drawer navigation**: `MainLayout` owns drawer links and timeline rendering directly

## Database

- PostgreSQL is the development database, provided by `compose.yml` as `pgvector/pgvector:pg18`
- The default JDBC URL is `jdbc:postgresql://localhost:${POSTGRES_PORT:4321}/socratic-tutor`
- Spring AI uses pgvector for vector storage in the `public.vector_store` table
- JPA entities use `@GeneratedValue(strategy = GenerationType.SEQUENCE)`
- Keep entity equality stable and ID-based when an entity needs custom `equals`/`hashCode`

## Session Learnings

- After CSS/resource-heavy drawer changes, `./mvnw -q -DskipTests package` may leave the running app serving stale `styles.css`; use `./mvnw -q -DskipTests clean package` before restarting when browser output does not match edited resource files.
