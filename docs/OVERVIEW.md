# ForkKnight Project Overview

ForkKnight is a lightweight desktop client for Git operations, written in JavaFX for an AP Lab assignment. What makes this project unique is its creative approach to teaching Git concepts by re-imagining Git terminology in knightly (and occasionally anime) vocabulary.

## Core Concept - The Realm's Vocabulary

The project maps standard Git terminology to medieval/fantasy equivalents:

| ForkKnight Term | Common Git Term | Description |
|-----------------|-----------------|-------------|
| Realm           | Repository      | The collection of code being managed |
| Feat            | Commit          | A sealed victory in the realm's chronicle |
| Banner          | Branch          | A dispatch on a campaign front - one of the roads a knight may travel |
| Sigil           | Tag             | A sigil placed on a feat so it can be referenced forever |
| Chronicle       | Git History     | The realm's chronicle keeper that speaks ONLY in knightly vocabulary |
| Survey the trail| git log         | Viewing the history of commits |

## Architecture

The project follows a modular structure with clearly separated concerns:

### Main Packages

1. **forkknight.core** - Contains the core domain logic implementing the knightly vocabulary
   - `Chronicle.java` - Manages the realm's chronicle; the git CLI is
     confined to its private CODEX map (there is no separate git
     package - the rebrand folded it entirely into the codex)
   - `Feat.java` - Represents a sealed victory (commit), with parent hashes for the DAG
   - `Vault.java` - Bounded LRU cache for battle recounts (diffs)
   - `Banner.java` - Campaign fronts (branches)
   - `Sigil.java` - Permanent references to feats (tags)
   - `Weave.java` - DAG lane assignment powering the commit graph column
   - `Dispatch.java` - A single file change entry
   - `Scryer.java` - Trie + bigram inverted index search, plus recency/hero lenses
   - `Chronicler.java` - Realm statistics via bounded top-K heaps (powers the Council)
   - `KnightMemory.java` - Memory facade (settings, notes, realm bookmarks)
   - `KnightDatabase.java` - SQLite persistence at ~/.forkknight/forkknight.db

2. **forkknight** (root) - Application shell and dialogs
   - `App.java` - Main JavaFX application: UI shell, menus, keyboard
     shorts, Herald/Council/Kamui wiring
   - `SealDialog.java` - Commit message dialog (summary + tale)
   - `CouncilDialog.java` - Statistics dialog with leader boards

3. **forkknight.ui** - User interface components
   - `TalePane.java` - Feat details: dispatch list + per-path recounts
     cached through the Vault

4. **forkknight resources** - `src/main/resources/forkknight/dark-theme.css`
   - Night Sight theme overlay

## Key Design Principles

1. **Encapsulation of Git Complexity**: All direct Git operations are confined to specific classes, allowing the UI to remain purely in the knightly vocabulary domain.

2. **Educational Focus**: By mapping Git concepts to memorable medieval terms, the project helps students understand version control through analogy.

3. **JavaFX Implementation**: Built with modern JavaFX for a desktop GUI experience.

4. **Clean Separation**: Clear separation between domain model, Git implementation, and UI layers.

## Current State

- 21 commits on `main`; 80 unit tests green
- Feature-complete for local work: history with a woven DAG graph,
  details + diffs, staging/committing (with amend), branch/tag/merge
  management, stash, search with lenses, statistics, and remote
  operations (fetch/pull/push)
- Settings, notes and realm bookmarks persist in an embedded SQLite
  ledger (last realm, theme, window bounds included)

## Getting Started

See `README.md` and `FEATURES.md` for detailed information about setup, features, and development progress.

## Documentation

This documentation explains the ForkKnight project's architecture and design decisions. It mirrors the source files in the ForkKnight repository; the living ledger of development progress is `feature.md` (copied here as `FEATURES.md`).