# The Ledger - SQLite Persistence in ForkKnight

How the knight's memory is kept: an embedded SQLite database behind a
thin DAO (`KnightDatabase`), with `KnightMemory` as the facade the rest
of the app talks to. Landed in commit `8acf711` ("feat: integrate SQLite
database for persistence"), hardened in `67017e2` (versioned migrations,
WAL, one reused connection) and given accounts in `ba16ec3`, `ae83921`
and `09ce669`.

## Why SQLite

- **Zero-install**: `org.xerial:sqlite-jdbc` ships the native library;
  the whole database is one file, no server to run.
- **Syllabus fit**: the AP Lab plan (Week 6) asks for relational-database
  work in JavaFX - database connection, table creation, insert, update,
  delete and query operations - all exercised here.
- **One store**: settings, feat notes, realm bookmarks and the accounts
  themselves live in a single transactional store instead of a hand-rolled
  text file - the bookmark feature and the Order of Knights ride on the
  same tables.

## Where it lives

| what | where |
|---|---|
| database file | `~/.forkknight/forkknight.db` (parent dir created on first use) |
| driver | `org.xerial:sqlite-jdbc:3.45.3.0` (Gradle `implementation`) |
| module system | `module-info.java` declares `requires java.sql;`; the driver jar (automatic module) is supplied on the runtime module path by Gradle |
| DAO | `forkknight.core.KnightDatabase` (failures wrapped in `KnightDbException`) |
| facade | `forkknight.core.KnightMemory` (settings, notes, bookmarks - scoped to the signed-in knight) |

## Schema

The schema is **versioned**: every change is an entry in `KnightDatabase`'s
`MIGRATIONS` list, recorded with `PRAGMA user_version` and applied once, in
order, inside a single transaction. `KnightDatabase.schemaVersion()` exposes
the target version for tests. Current version: **3**.

| version | what it did |
|---|---|
| v1 | the original ledger: `settings`, `notes`, `realm_bookmarks`, each with a single-column primary key |
| v2 | accounts: creates `users`, auto-creates the `keeper` account, then rebuilds the three legacy tables with user-scoped composite primary keys and copies every existing row to the keeper (rename -> create -> `INSERT ... SELECT ?, ...` -> drop) |
| v3 | `app_state` - facts about the app, not about any one knight (currently `account.current`) |

Current tables, as SQLite reports them (`sqlite_master`):

| table | columns | purpose |
|---|---|---|
| `users` | `id INTEGER PK AUTOINCREMENT, username TEXT NOT NULL COLLATE NOCASE UNIQUE, display_name TEXT NOT NULL, password_hash TEXT NOT NULL, is_guest INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL` | the Order of Knights (PBKDF2 hashes; `guest` is the passwordless Wanderer) |
| `settings` | `user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(user_id, key)` | per knight: open realms, sight, window bounds |
| `notes` | `user_id ... REFERENCES users(id) ON DELETE CASCADE, hash TEXT NOT NULL, note TEXT NOT NULL, PRIMARY KEY(user_id, hash)` | a knight's notes on feats |
| `realm_bookmarks` | `user_id ... REFERENCES users(id) ON DELETE CASCADE, realm_path TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(user_id, realm_path)` | named realms on the roll |
| `app_state` | `key TEXT PRIMARY KEY, value TEXT NOT NULL` | global: who rode last (`account.current`) |

Every settings/notes/bookmark row belongs to exactly one account, so two
knights can share a realm, a note or a window size without colliding; the
`ON DELETE CASCADE` foreign keys drop a dismissed knight's rows with him.

## The DAO - KnightDatabase

Connection policy: one connection per `KnightDatabase`, opened on first
use and reused for the lifetime of the object, with every call
serialized on an internal lock. Opening it sets `PRAGMA journal_mode=WAL`,
`busy_timeout=5000`, `foreign_keys=ON` and `synchronous=NORMAL`, then
applies any pending migrations; `close()` releases the connection and the
next call reopens (and re-checks the version) on demand. Migrations run
inside a single transaction - a failure rolls every step back and raises
`KnightDbException` rather than leaving a half-upgraded ledger.

Every write is a prepared statement; inserts use SQLite's upsert form so
create and update are one code path (the conflict target is now the
composite key):

```sql
INSERT INTO settings (user_id, key, value) VALUES (?, ?, ?)
ON CONFLICT(user_id, key) DO UPDATE SET value = excluded.value
```

Operations (full CRUD over each table; everything but `users` and
`app_state` takes the knight's `user_id` first, so rows are per-account):

| area | create / update | read | delete |
|---|---|---|---|
| accounts | `createUser`, `setPassword` | `findUserById`, `findUserByUsername`, `listUsers`, `usernameExists`, `guestAccount` | `deleteUser` |
| settings | `setSetting` | `getSetting`, `getAllSettings` | `deleteSetting` |
| notes | `setNote` | `getNote` | `deleteNote` |
| bookmarks | `setBookmark` | `getBookmarkName`, `getAllBookmarks` | `deleteBookmark` |
| app state | `setGlobal` | `getGlobal` | - |

Two constructors: the default one points at the real
`~/.forkknight/forkknight.db`; `KnightDatabase(String dbUrl)` lets tests
use a temp file (or `jdbc:sqlite::memory:`). The package-private static
`schemaVersion()` returns `MIGRATIONS.size()` (3) so tests can assert a
fully migrated ledger reports exactly that.

## Facade - KnightMemory

`KnightMemory` kept its public API (`recall`, `remember`, `forget`,
`rememberNote`, `recallNote`, bookmark helpers) and now forwards every
call to the database - callers never see JDBC. Blank notes delete the
row; blank hashes are ignored; failed writes surface as runtime errors
rather than silent corruption.

Since v2 every call is scoped to the **current account**
(`currentUserId`): `switchTo(id)` and `signOut()` re-point the facade and
write `app_state.account.current` so the seat survives a restart, and
`currentAccount()` reads the knight back. `App` therefore never touches
user ids - it just remembers settings under whoever rides now.

## UI touchpoints

- **launch**: the seat is restored from `app_state.account.current` (falling
  back to `keeper`, then the guest Wanderer), then the remembered open
  realms, window bounds and favored sight are restored from that knight's `settings`
- **Seek Realm / open realm / realm switch**: `realms` (the open set, one
  path per line) and `realm` (the active one) settings updated
- **departure** (window close): sight, bounds and open realms written back
- **tale pane > Annotate...**: writes to `notes`
- **Realm > Bookmark Current Realm... / Bookmarked Realms...**: writes/reads `realm_bookmarks`; the dialog opens the chosen realm (double-click works too)
- **Realm > Knights...**: sign up / sign in / switch / sign out / claim /
  dismiss through `users`; a changed seat writes `app_state.account.current`
  and swaps the workspace

## Tests

- `KnightDatabaseTest` (10 tests, JUnit `@TempDir`): migrations up to
  `schemaVersion()`, keeper adoption of legacy rows, and
  settings/notes/bookmark create-update-read-delete cycles - including
  per-user scoping - against a real temporary SQLite file.
- `KnightMemoryTest` (10 tests): the facade's settings and note round trips,
  plus seat restore/switch/sign-out.
- `PasswordHasherTest` (6) and `AccountServiceTest` (16) cover the accounts
  and password rows of the same ledger (see ARCHITECTURE.md for PBKDF2).
- Whole suite: **127 green** via `./gradlew test`.

## Inspecting the ledger by hand

```bash
sqlite3 ~/.forkknight/forkknight.db '.tables'
sqlite3 ~/.forkknight/forkknight.db 'PRAGMA user_version;'
sqlite3 ~/.forkknight/forkknight.db 'SELECT username, display_name, is_guest FROM users;'
sqlite3 ~/.forkknight/forkknight.db 'SELECT * FROM app_state;'
sqlite3 ~/.forkknight/forkknight.db 'SELECT key FROM settings WHERE user_id = 1;'
```

## Notes for the viva / report

- Connection handling, table creation, insert, update, delete and query
  are each demonstrated in `KnightDatabase` (the rubric's checklist in
  one file).
- Prepared statements for every row operation - no user input is ever
  concatenated into SQL - plus the `ON CONFLICT ... DO UPDATE` upsert
  idiom (the migrations themselves use fixed, internal table names).
- The migration story: `PRAGMA user_version`, one transaction per upgrade,
  and v2's rename/create/copy/drop that hands every legacy row to the
  auto-created `keeper` account without losing memory.
- Possible next steps: importing the legacy `~/.forkknight/memory` file if
  one is found on disk, and a simple export/backup of the ledger.
