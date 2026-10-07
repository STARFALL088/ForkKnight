# ForkKnight

A lightweight desktop client for the realm's chronicles, written in JavaFX
for the AP Lab assignment. Every git concept is re-imagined in knightly
(and occasionally anime) vocabulary - the raw "git" tongue is confined to
a single codex inside `Chronicle` and never leaks into the UI.

Several realms stay open at once, local accounts (the Order of Knights,
optional - the Wanderer rides without signing in) decide whose sight and
notes are shown, the knight's own page records who you are in the realm,
and the keyboard shortcuts are one immutable, display-only catalogue
(`Shortcut`).

## Documentation

The full suite lives in [`docs/`](docs/):

| Guide | What it covers |
|---|---|
| [OVERVIEW.md](docs/OVERVIEW.md) | What the project is, at a glance |
| [USER_GUIDE.md](docs/USER_GUIDE.md) | Every menu and dialog, end to end |
| [TUTORIAL.md](docs/TUTORIAL.md) | Step-by-step walkthroughs of common work |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, seams, and why they are cut where they are |
| [ALGORITHMS.md](docs/ALGORITHMS.md) | Scryer, Weave, Vault, Chronicler in detail |
| [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) | Building, running, testing, contributing |
| [DATABASE.md](docs/DATABASE.md) | The Ledger: schema, migrations, WAL |
| [CONTRIBUTING.md](docs/CONTRIBUTING.md) | House rules for the realm |
| [FEATURES.md](docs/FEATURES.md) | The feature ledger - every feat, and its state |
| [IMPROVEMENTS.md](docs/IMPROVEMENTS.md) | What could still be better |
| [SUMMARY.md](docs/SUMMARY.md) | Index of the suite |
| [VOCABULARY_REFERENCE.md](docs/VOCABULARY_REFERENCE.md) | Every term in the realm's tongue |
| [docs/README.md](docs/README.md) | The suite's own README |

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
| Banish | discard changes |
| Vanquish | delete untracked file |
| Enlist all | git add -A |
| Press / Melt a sigil | create / delete a tag |
| Raise / Fell a banner | create / delete a branch |
| March to a banner | checkout a branch |
| Fusion | merge |
| Kamui (vanish / summon) | git stash push / pop |
| Re-seal | git commit --amend |
| Allies / Rally / Recall / Emissary | remotes / fetch / pull / push |
| The Council (Ctrl+I) | repository statistics |
| Scry | search |
| Night/Day Sight | dark/light theme |
| Hero | author |
| Seal | git commit |
| Muster | git status |
| Dispatch | file change entry |
| Toll of a feat | diff of a commit |
| Recount | show (full diff) |
| Far Call to the Wider Realm | HTTP GET to a REST API (JSON) |
| Profile (the knight's own page) | user profile |
| The Ledger | SQLite persistence layer |
| The Order of Knights | local accounts |
| The Wanderer | the guest / anonymous seat |

## Project Structure

- `src/main/java/forkknight/core/` - the realm's heart:
  - `Chronicle` - the command codex + engine (git CLI confined here)
  - `Feat`, `Banner`, `Sigil`, `Dispatch` - domain records
  - `Scryer` - trie + bigram inverted index search, plus recency/hero lenses
  - `Weave` - DAG lane assignment for the commit graph
  - `Vault` - O(1) LRU cache for diffs
  - `Chronicler` - realm statistics via bounded top-K heaps
  - `Chronicler`, `KnightMemory` - memory facade (settings, notes, bookmarks)
  - `KnightDatabase` - SQLite vault at ~/.forkknight/forkknight.db (JDBC),
    schema versioned via `PRAGMA user_version`, WAL, one reused connection
  - `AccountService`, `Account`, `PasswordHasher`, `AuthException` - the
    Order of Knights: local accounts, PBKDF2 passwords, the guest
    Wanderer, the knight's own page (`KnightProfile`, `KnightRank`)
  - `RealmSession` - one open realm: its chronicle, banner and view state
  - `Json` - a hand-rolled RFC 8259 JSON reader (escapes, reals, depth
    guard, typed helpers)
  - `HttpGateway` / `JdkHttpGateway` / `Beacon` - HTTP GET behind an
    interface + a GitHub-style REST client (report + newest feats)
  - `Background` - the shared cached pool of named daemon threads every
    background job rides; shut down when the app departs
- `src/main/java/forkknight/` - `App` (UI shell), `SealDialog`,
  `CouncilDialog`, `KnightsDialog` (accounts), `ProfileDialog` (the
  knight's own page), `Shortcut` (immutable keys), `BeaconDialog` (the
  far call), `RealmTask` (abstract background job: pool name + one-voiced
  failure reporting)
- `src/main/java/forkknight/ui/` - `TalePane` (feat details + diff)
- `src/main/java/module-info.java` - the module descriptor (exports
  `forkknight`, `forkknight.core`, `forkknight.ui`)
- `src/main/resources/forkknight/dark-theme.css` - Night Sight theme
- `src/test/java/forkknight/` - 20 test classes, 163 tests

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
  the Council's leader boards ( heroes, days, path heat).
- **KnightMemory / KnightDatabase**: the memory facade rides on an
  embedded SQLite vault - JDBC prepared statements,
  `ON CONFLICT ... DO UPDATE` upserts, and a schema versioned through
  `PRAGMA user_version` (legacy rows migrated, then adopted by the
  `keeper` account), running in WAL mode over one reused connection.
- **Chronicle**: NUL-separated porcelain parsing (rename-aware), a
  compile-time codex guard, and dual-stream subprocess draining with
  timeouts.
- **Background / RealmTask**: one cached named-daemon-thread pool shared
  by every background job, with failures reported through a single
  voice so a task cannot double-report or swallow an error.

## Prerequisites

- JDK 17 or later (developed and tested with JDK 27)
- JavaFX 24.0.2 (the `org.openjfx.javafxplugin` Gradle plugin fetches it
  automatically - a manual install is not needed)

- Git on the PATH

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

163 tests, all passing (verified: 163 run, 0 failures, 0 skipped),
cover the Chronicle codex (surveys, tolls, muster, banners,
sigils, Kamui, fusion, allies, re-seal, divergence tallies), the Scryer
(all scopes, prefixes, multi-word AND, recency/hero lenses), the Weave
(lanes, forks, recycling, deep bloodlines), the Vault (LRU eviction,
purge, capacity), the Chronicler (rankings, ties, empty trails), the
ledger (migrations, settings/notes/bookmark CRUD, per-knight scoping
on a temp SQLite file), the KnightMemory (settings and feat notes
round trips), the immutable Shortcut catalogue, multi-realm sessions
(RealmSession), the accounts - PasswordHasher (PBKDF2 format and
verification), AccountService (sign up / in / out / switch, locked
ledgers, reserved guest, profile sealing/validation, launch gate) and
KnightsDialog (the seat readouts), the knight's own page (v3->v4
migration, per-knight profile stats, ProfileDialog helpers,
KnightRank) - the far call (Json parsing, Beacon against a canned
gateway, the JDK gateway against a loopback HTTP server), the shared
thread pool (Background), RealmTask's failure routing, and the
window-share bindings. UI-bound proofs wake a live FX toolkit through
`FxKit` and skip themselves on machines with no display.

## License

No license file is present in this repository.