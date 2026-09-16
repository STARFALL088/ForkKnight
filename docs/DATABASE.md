# The Ledger - SQLite Persistence in ForkKnight

How the knight's memory is kept: an embedded SQLite database behind a
thin DAO (`KnightDatabase`), with `KnightMemory` as the facade the rest
of the app talks to. Landed in commit `8acf711` ("feat: integrate SQLite
database for persistence").

## Why SQLite

- **Zero-install**: `org.xerial:sqlite-jdbc` ships the native library;
  the whole database is one file, no server to run.
- **Syllabus fit**: the AP Lab plan (Week 6) asks for relational-database
  work in JavaFX - database connection, table creation, insert, update,
  delete and query operations - all exercised here.
- **One store**: settings, feat notes and realm bookmarks now live in a
  single transactional store instead of a hand-rolled text file, and the
  new bookmark feature rides on the same tables.

## Where it lives

| what | where |
|---|---|
| database file | `~/.forkknight/forkknight.db` (parent dir created on first use) |
| driver | `org.xerial:sqlite-jdbc:3.45.3.0` (Gradle `implementation`) |
| module system | `module-info.java` declares `requires java.sql;`; the driver jar (automatic module) is supplied on the runtime module path by Gradle |
| DAO | `forkknight.core.KnightDatabase` |
| facade | `forkknight.core.KnightMemory` (settings, notes, bookmarks API) |

## Schema

Created on the first connection (`CREATE TABLE IF NOT EXISTS ...`):

| table | columns | purpose |
|---|---|---|
| `settings` | `key TEXT PRIMARY KEY, value TEXT NOT NULL` | last realm, sight, window bounds |
| `notes` | `hash TEXT PRIMARY KEY, note TEXT NOT NULL` | knight's notes on feats |
| `realm_bookmarks` | `realm_path TEXT PRIMARY KEY, name TEXT NOT NULL` | named realms on the roll |

## The DAO - KnightDatabase

Connection policy: a fresh `DriverManager.getConnection(url)` per
operation, always in try-with-resources. SQLite opens local files
cheaply, and confining each statement to one connection keeps the class
safe to call from background tasks.

Every write is a prepared statement; inserts use SQLite's upsert form so
create and update are one code path:

```sql
INSERT INTO settings (key, value) VALUES (?, ?)
ON CONFLICT(key) DO UPDATE SET value = excluded.value
```

Operations (full CRUD over each table):

| area | create / update | read | delete |
|---|---|---|---|
| settings | `setSetting` | `getSetting`, `getAllSettings` | `deleteSetting` |
| notes | `setNote` | `getNote` | `deleteNote` |
| bookmarks | `setBookmark` | `getBookmarkName`, `getAllBookmarks` | `deleteBookmark` |

Two constructors: the default one points at the real
`~/.forkknight/forkknight.db`; `KnightDatabase(String dbUrl)` lets tests
use a temp file (or `jdbc:sqlite::memory:`).

## Facade - KnightMemory

`KnightMemory` kept its public API (`recall`, `remember`, `forget`,
`rememberNote`, `recallNote`, bookmark helpers) and now forwards every
call to the database - callers never see JDBC. Blank notes delete the
row; blank hashes are ignored; failed writes surface as runtime errors
rather than silent corruption.

## UI touchpoints

- **launch**: last realm, window bounds and favored sight restored from `settings`
- **Seek Realm / open realm**: `realm` setting updated
- **departure** (window close): sight and bounds written back
- **tale pane > Annotate...**: writes to `notes`
- **Realm > Bookmark Current Realm... / Bookmarked Realms...**: writes/reads `realm_bookmarks`; the dialog opens the chosen realm (double-click works too)

## Tests

- `KnightDatabaseTest` (3 tests, JUnit `@TempDir`): settings, notes and
  bookmark create-update-read-delete cycles against a real temporary
  SQLite file.
- `KnightMemoryTest` (6 tests): the facade's settings and note round trips.
- Whole suite: **80 green** via `./gradlew test`.

## Inspecting the ledger by hand

```bash
sqlite3 ~/.forkknight/forkknight.db '.tables'
sqlite3 ~/.forkknight/forkknight.db 'SELECT * FROM realm_bookmarks;'
sqlite3 ~/.forkknight/forkknight.db 'SELECT key FROM settings;'
```

## Notes for the viva / report

- Connection handling, table creation, insert, update, delete and query
  are each demonstrated in `KnightDatabase` (the rubric's checklist in
  one file).
- Prepared statements everywhere - no string-built SQL - plus the
  `ON CONFLICT ... DO UPDATE` upsert idiom.
- Possible next steps: WAL mode for heavier use, reusing a connection
  across a task, and importing the legacy `~/.forkknight/memory` file if
  one is found on disk.
