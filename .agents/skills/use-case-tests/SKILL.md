---
name: use-case-tests
description: Write and run tests for a use case. Use when writing tests for an implemented use case.
---

# Writing Tests for a Use Case

Tests are organized around the **use case**, not around individual views or layers. A use case may span multiple views, services, and packages — its tests live together so the use case's behaviour can be read and run as a single unit.

## Structure

- **One test class per use case.** Name it after the use case: `UC001BrowseMovies`, `UC002BuyTickets`, etc. The `UC-NNN` prefix is the traceability link back to `spec/use-cases/use-case-NNN-*.md`.
- **One folder per use case.** Place the class (and any supporting fixtures) in `src/test/java/com/example/specdriven/usecases/uc001_browse_movies/` (or `src/test/frontend/usecases/uc-001-browse-movies/` for tests that must run in the browser). Folder name mirrors the spec filename.
- **Method names map directly to the spec.** Use the segments from the use-case document so a reader can jump from a failing test back to the exact line of spec it covers:
  - `mainFlow_*` — one method per scenario through the Main Flow.
  - `af1_*`, `af2_*`, … — one method per Alternative Flow, named for its condition.
  - `br01_*`, `br02_*`, … — one method per Business Rule.

## Choosing the test mechanism

The tech is an implementation detail picked per method, not per use case. Within a single use-case class, mix as needed:

- **Vaadin Browserless Test** (`SpringBrowserlessTest`, `@SpringBootTest`) for Flow views. Use `navigate(ViewClass.class)`, `$(ComponentClass.class)`, `test(component)`. Use `@WithMockUser(roles = "ADMIN")` for admin areas, `@WithAnonymousUser` for access-control checks.
- **Vitest + React Testing Library** for Hilla/React views. Lives under `src/test/frontend/usecases/uc-NNN-*/`. Mock `@BrowserCallable` endpoints.
- **Plain Spring `@SpringBootTest`** when a flow or business rule is best verified directly against a `@Service` (no UI involved). Prefer testing the service over the `@BrowserCallable` endpoint.

A use case that combines an admin curation flow with a public browse flow ends up with both browserless and (if applicable) Vitest tests sharing the same `UC0xx*` name and the same per-use-case folder. They are the same use case's tests, written in whichever mechanism fits each step.

## Coverage Requirements

The use case's flows and business rules *are* the acceptance criteria. There is no separate acceptance-criteria list. Every test method must trace to one of:

- **Main Flow** — at least one `mainFlow_*` test exercising the happy path end to end.
- **Alternative Flows** — one `afN_*` test per `AF-N`, triggering its condition and asserting its branch.
- **Business Rules** — one `brNN_*` test per `BR-N`, especially edge cases (limits, validation, error handling).
- **Postconditions** — asserted inside the relevant flow tests.

If a test doesn't map to Main Flow, an AF, or a BR, either it belongs to a different use case or the spec is missing the rule it covers — fix the spec, don't orphan the test.
