# ForkKnight

A lightweight desktop client for the realm's chronicles, written in JavaFX
for the AP Lab assignment. Every git concept is re-imagined in knightly
(and occasionally anime) vocabulary - the raw "git" tongue is confined to
a single codex inside `Chronicle` and never leaks into the UI.

## The Realm's Vocabulary

| ForkKnight | Common tongue |
|---|---|
| Realm | repository |
| Feat | commit |
| Banner | branch |
| Sigil | tag |
| Chronicle | git history |
| Survey the trail | git log |
| Enlist | git add (stage) |
| Release | git reset (unstage) |
| Seal | git commit |
| Muster | git status |
| Dispatch | file change entry |
| Toll of a feat | diff of a commit |
| Recount | show (full diff) |
| Raise / Fell a banner | create / delete a branch |
| March to a banner | checkout a branch |
| Fusion | merge |
| Press / Melt a sigil | create / delete a tag |
| Kamui (vanish / summon) | git stash push / pop |
| Re-seal | git commit --amend |
| Allies / Rally / Recall / Emissary | remotes / fetch / pull / push |
| The Council (Ctrl+I) | repository statistics |
| Scry | search |
| Night/Day Sight | dark/light theme |
| Hero | author |
| Banish | discard changes |
| Vanquish | delete untracked file |

## Project Structure

- `src/main/java/forkknight/core/` - the realm's heart:
  - `Chronicle` - the command codex + engine (git CLI confined here)
  - `Feat`, `Banner`, `Sigil`, `Dispatch` - domain records
  - `Scryer` - trie + bigram inverted index search, plus recency/hero lenses
  - `Weave` - DAG lane assignment for the commit graph
  - `Vault` - O(1) LRU cache for diffs
  - `Chronicler` - realm statistics via bounded top-K heaps
  - `KnightMemory` - the knight's memory facade (settings, notes, bookmarks)
  - `KnightDatabase` - SQLite vault at ~/.forkknight/forkknight.db (JDBC),
    schema versioned via `PRAGMA user_version`, WAL, one reused connection
  - `AccountService`, `Account`, `PasswordHasher`, `AuthException` - the
    Order of Knights: local accounts, PBKDF2 passwords, the guest Wanderer
  - `RealmSession` - one open realm: its chronicle, banner and view state
- `src/main/java/forkknight/` - `App` (UI shell), `SealDialog`,
  `CouncilDialog`, `KnightsDialog` (accounts), `Shortcut` (immutable keys)
- `src/main/java/forkknight/ui/` - `TalePane` (feat details + diff)
- `src/main/resources/forkknight/dark-theme.css` - Night Sight theme
- `src/test/java/forkknight/core/` - unit tests

## Algorithms & Data Structures

- **Scryer**: a 43-slot sparse trie over commit tokens gives O(L) prefix
  search; a bigram inverted index (token -> postings) intersects
  multi-word queries with hash sets before any substring check runs;
  recency and hero lenses AND-compose over the result.
- **Weave**: first-fit lane assignment over the commit DAG using a
  TreeSet free-lane pool - O(n log n) - keeps the drawn graph compact;
  bloodline traversal is an explicit-stack DFS (stack-overflow safe to
  50k+ depth).
- **Vault**: access-ordered LinkedHashMap LRU - O(1) hit, miss and
  evict - caching per-file diffs.
- **Chronicler**: bounded min-heap top-K selection (O(n log k)) powers
  the Council's leader boards (heroes, days, path heat).
- **KnightMemory / KnightDatabase**: the memory facade now rides on an
  embedded SQLite vault - JDBC prepared statements,
  `ON CONFLICT ... DO UPDATE` upserts, and a schema versioned through
  `PRAGMA user_version` (legacy rows migrated, then adopted by the
  `keeper` account), running in WAL mode over one reused connection.
- **Chronicle**: NUL-separated porcelain parsing (rename-aware), a
  compile-time codex guard, and dual-stream subprocess draining with
  timeouts.

## Prerequisites

- JDK 17 or later (we tested with JDK 26)
- JavaFX 24.0.2 (matching the version in build.gradle)

## Running the Application

```bash
./gradlew run
```

Or directly (replace the module path with your JavaFX install):

```bash
java --module-path /path/to/javafx-sdk-24.0.2/lib \
     --add-modules javafx.controls,javafx.fxml \
     -cp build/classes/java/main:build/resources/main forkknight.App
```

## Persistence (the Ledger)

The knight's memory rides on an embedded SQLite database at
`~/.forkknight/forkknight.db` (driver `org.xerial:sqlite-jdbc`, no
server to install). The schema is versioned through
`PRAGMA user_version` - legacy data is migrated, then adopted by the
`keeper` account - and runs in WAL mode over one reused connection.
Tables: `users` (the Order of Knights: PBKDF2-hashed passwords, the
guest Wanderer), `settings`, `notes` and `realm_bookmarks` (each
scoped per knight with composite keys), and `app_state` (who rides
now). The open realms and active realm live per knight in `settings`.
Realm menu: "Knights..." (local accounts: join, sign in, switch
seats, claim a locked ledger), "Bookmark Current Realm..." and
"Bookmarked Realms..." (dialog opens the chosen realm).

## Testing

```bash
./gradlew test
```

127 tests cover the Chronicle codex (surveys, tolls, muster, banners,
sigils, Kamui, fusion, allies, re-seal, divergence tallies), the Scryer
(all scopes, prefixes, multi-word AND, recency/hero lenses), the Weave
(lanes, forks, recycling, deep bloodlines), the Vault (LRU eviction,
purge, capacity), the Chronicler (rankings, ties, empty trails), the
ledger (migrations, settings/notes/bookmark CRUD, per-knight scoping
on a temp SQLite file), the KnightMemory (settings and feat notes
round trips), the immutable Shortcut catalogue, multi-realm sessions
(RealmSession), and the accounts - PasswordHasher (PBKDF2 format and
verification), AccountService (sign up / in / out / switch, locked
ledgers, reserved guest) and KnightsDialog (the seat readouts).
