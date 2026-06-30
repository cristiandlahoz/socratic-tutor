# Changelog

## 1.1.0 - 2026-06-29

### Added
- Multi-tenant workspace, onboarding, invitation, and role-based access flows.
- Conversation memory migration to Spring AI Session with snapshot-based persistence.
- Grounding document ingestion and pgvector retrieval updates.
- Training activity/formative activity workflows and refreshed professor/student UI.
- OpenAI-compatible chat backend support and inference-engine scripts for llama-swap with multiple llama.cpp backends.
- Tutor guard advisor wiring and lightweight support-model defaults for guard, title, and C example preparation jobs.

### Changed
- Refreshed chat, sidebar, workspace, login, debugger, and theme visuals.
- Renamed legacy evaluation concepts to training/formative activity terminology.
- Reworked routing to use route classes and updated route-access tests.
- Updated email templating and application configuration for the current deployment model.

### Fixed
- Route access, academic context loading, evaluation/training activity state handling, chat client tab handling, and C runner parsing issues.
