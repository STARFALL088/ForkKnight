# ForkKnight Documentation Summary

This repository contains comprehensive documentation for the ForkKnight project, a creative JavaFX-based Git client that teaches version control concepts through knightly vocabulary metaphors.

## Documentation Files

1. **OVERVIEW.md** - High-level project overview explaining the core concept and architecture
2. **ARCHITECTURE.md** - Detailed technical analysis of the codebase structure, design patterns, and implementation details
3. **ALGORITHMS.md** - The DS/algo inventory: Scryer, Weave, Chronicle parsing, Vault, Chronicler top-K, KnightMemory
4. **DEVELOPER_GUIDE.md** - Practical guide for building, running, testing, and contributing to the project
5. **USER_GUIDE.md** - End-user manual matching the real UI (menus, tabs, shortcuts)
6. **TUTORIAL.md** - Workflow walkthroughs with knightly terms mapped to Git concepts
7. **VOCABULARY_REFERENCE.md** - Complete glossary of knightly terms and Git equivalents
8. **CONTRIBUTING.md** - Contribution process, coding standards, metaphor guidelines
9. **IMPROVEMENTS.md** - Future improvement ideas, kept aligned with what has landed
10. **FEATURES.md** - Copy of the project's living feature.md ledger
11. **README.md** - Copy of the project README with vocabulary mapping and build info

`build.gradle` and `settings.gradle` are kept in sync with the project.

## Current Project State (mirrors feature.md)

- 81 unit tests green (Chronicle 34, Scryer 14,
  KnightMemory 10, Weave 8, Chronicler 8, Vault 7)
- Feature-complete for local work: woven DAG history, details + diffs,
  staging/committing (with amend), branch/tag/merge management with
  dispute handling, stash, three-lens search, statistics, remote
  operations (fetch/pull/push), and settings persistence

## Key Topics Covered

### Project Concept
- How ForkKnight re-imagines Git terminology in knightly/anime vocabulary
- The educational value of learning Git through memorable analogies
- Examples of the vocabulary mapping (Realm=repository, Feat=commit, Banner=branch, etc.)

### Technical Architecture
- The CODEX pattern for encapsulating Git commands behind knightly vocabulary
- Separation of concerns between UI and the domain core (no git package remains - the codex absorbed it)
- Caching strategy using the Vault LRU cache implementation
- Background thread processing for responsive UI
- Modular package structure (core, ui)

### Development Information
- Build instructions using Gradle wrapper
- How to run the application with JavaFX
- Testing procedures and test coverage
- Project structure overview
- Contributing guidelines and code style
- Troubleshooting common issues

## Verification Note

The documentation mirrors the ForkKnight source tree at commit
`a8cdb08` ("Add the Knight's Memory"). When the project evolves,
re-copy `README.md`/`feature.md` and review the guides for drift -
especially FEATURES.md (the ledger), USER_GUIDE.md (the UI) and the
package trees in OVERVIEW/DEVELOPER_GUIDE.