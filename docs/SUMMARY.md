# ForkKnight Documentation Summary

This repository contains comprehensive documentation for the ForkKnight project, a creative JavaFX-based Git client that teaches version control concepts through knightly vocabulary metaphors.

## Documentation Files

1. **OVERVIEW.md** - High-level project overview explaining the core concept and architecture
2. **ARCHITECTURE.md** - Detailed technical analysis of the codebase structure, design patterns, and implementation details
3. **ALGORITHMS.md** - The DS/algo inventory: Scryer, Weave, Chronicle parsing, Vault, Chronicler top-K, KnightMemory/KnightDatabase
4. **DATABASE.md** - The Ledger: versioned schema and migrations (v1-v3), accounts and per-knight scoping, DAO/facade design, UI touchpoints, tests, inspection commands
5. **DEVELOPER_GUIDE.md** - Practical guide for building, running, testing, and contributing to the project
6. **USER_GUIDE.md** - End-user manual matching the real UI (menus, tabs, shortcuts)
7. **TUTORIAL.md** - Workflow walkthroughs with knightly terms mapped to Git concepts
8. **VOCABULARY_REFERENCE.md** - Complete glossary of knightly terms and Git equivalents
9. **CONTRIBUTING.md** - Contribution process, coding standards, metaphor guidelines
10. **IMPROVEMENTS.md** - Future improvement ideas, kept aligned with what has landed
11. **FEATURES.md** - Copy of the project's living feature.md ledger
12. **README.md** - Copy of the project README with vocabulary mapping and build info

`build.gradle` and `settings.gradle` are copies of the project's build
files - re-copy them when dependencies change (the project's
`build.gradle` carries `org.xerial:sqlite-jdbc`).

## Current Project State (mirrors feature.md)

- 127 unit tests green (Chronicle 34, AccountService 16, Scryer 14,
  KnightDatabase 10, KnightMemory 10, Weave 8, Chronicler 8, Vault 7,
  PasswordHasher 6, RealmSession 6, Shortcut 4, KnightsDialog 4)
- Feature-complete for local work: woven DAG history, details + diffs,
  staging/committing (with amend), branch/tag/merge management with
  dispute handling, stash, three-lens search, statistics, remote
  operations (fetch/pull/push), and settings/notes/bookmark
  persistence in a SQLite ledger
- Beyond the core editor: multiple realms open at once (each remembered
  in full), local accounts with PBKDF2 passwords (the Order of Knights,
  optional sign-in, seat restored across launches) and an immutable
  keyboard shortcut catalogue (R1)

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
`16566f3` ("feat: the order of knights - join, sign in, switch seats,
claim a locked ledger"). When the project evolves, re-copy
`README.md`/`feature.md` and review the guides
for drift - especially FEATURES.md (the ledger), DATABASE.md (the
SQLite store), USER_GUIDE.md (the UI) and the package trees in
OVERVIEW/DEVELOPER_GUIDE.