# ForkKnight Architecture Documentation

Based on examining the source code, here's a detailed look at ForkKnight's architecture and design patterns.

## Core Design Pattern: Vocabulary Encapsulation

The most distinctive aspect of ForkKnight is how it encapsulates Git terminology behind a knightly vocabulary layer. This is implemented in the `Chronicle` class:

### The Codex Pattern

In `Chronicle.java`, the dragon tongue lives in exactly two places - the
`ORDERS` array (the known verbs) and the `CODEX` map (knightly order ->
dragon verb). A static initializer guard fails fast if any translation
points at an unknown verb:

```java
private static final String[] ORDERS = {
    "log", "status", "show", "rev-parse", "for-each-ref",
    "diff-tree", "stash", "commit", "add", "reset", "checkout",
    "switch", "branch", "tag", "rev-list", "merge", "fetch", "push",
    "remote", "pull"
};

private static final Map<String, String> CODEX = buildCodex();

private static Map<String, String> buildCodex() {
    Map<String, String> m = new HashMap<>();
    m.put("survey", "log");       // scout the campaign trail
    m.put("muster", "status");    // count the troops in the field
    m.put("recount", "show");     // retell one feat in full
    m.put("mark", "rev-parse");   // read the realm's marks
    m.put("banners", "for-each-ref"); // list every banner in the hall
    m.put("rally", "fetch");      // call the allied hosts home
    m.put("emissary", "push");    // send word to allied lands
    m.put("recall", "pull");      // bring allied wisdom here
    m.put("allies", "remote");   // the allied realms' roll
    // ... 20 mappings in total
    return Map.copyOf(m);
}

static {
    // Compile-time guard: every codex translation must hit a known
    // dragon verb, otherwise a typo would silently corrupt commands.
    for (String knightly : CODEX.keySet()) {
        String dragon = CODEX.get(knightly);
        if (Arrays.stream(ORDERS).noneMatch(dragon::equals)) {
            throw new ExceptionInInitializerError(
                    "Codex broken: " + knightly + " -> " + dragon);
        }
    }
}
```

This creates a clean separation where:
- Public API uses knightly terms: `surveyTrail()`, `muster()`, `recount()`, etc.
- Internal implementation maps these to actual Git commands via the CODEX map
- The raw Git commands are completely hidden from the rest of the application

## Class Responsibilities

### Core Domain Model (`forkknight.core` package)

1. **Chronicle** - Main interface to Git operations, encapsulates the CODEX mapping
2. **Feat** - Represents a Git commit, including parent hashes for the DAG ("a sealed victory in the realm's chronicle")
3. **Vault** - Generic bounded LRU cache implementation used for diff caching
4. **Banner** - Represents a Git branch ("a dispatch on a campaign front")
5. **Sigil** - Represents a Git tag ("a sigil placed on a feat")
6. **Weave** - DAG lane assignment for the commit graph column
7. **Scryer** - Search index (trie + bigram inverted index) plus recency/hero lenses
8. **Chronicler** - Realm statistics via bounded top-K min-heaps
9. **KnightMemory** - Memory facade: settings, notes and realm bookmarks,
   every call scoped to the signed-in knight
10. **KnightDatabase** - SQLite persistence (~/.forkknight/forkknight.db, JDBC),
    schema versioned through `PRAGMA user_version` (WAL, one reused connection,
    failures wrapped in `KnightDbException`)
11. **Account** / **AccountService** - a local knight (id, username,
    display name, password hash, guest flag) and the service that signs
    up / in / out, switches seats, changes passwords and dismisses;
    every refusal is an `AuthException` with a UI-readable message
12. **PasswordHasher** - PBKDF2-HMAC-SHA256 password hashing (see below)
13. **RealmSession** - one open realm: its chronicle, raised banner,
    cached trail (Weave + Scryer) and view state (R2)
14. **Dispatch** - A single file change entry

### UI Layer

1. **App** (`forkknight.App`) - Main JavaFX application: UI shell, menus,
   keyboard shorts, the realm picker, The Field tab, Herald/Council/Kamui
   wiring, and the seat swap described below
2. **TalePane** (`forkknight.ui`) - Feat details: dispatch list + per-path
   recounts cached through the Vault
3. **SealDialog, CouncilDialog, KnightsDialog** (`forkknight`) - Commit
   message dialog, statistics council, and the Order of Knights dialog
   (sign in / switch / join / claim password / sign out / dismiss)
4. **Shortcut** (`forkknight`) - The immutable keyboard catalogue the
   menus read their accelerators from (R1: display-only, never rebindable)

There is no separate git package: the rebrand folded the entire git CLI
into the Chronicle codex, so the only place raw verbs exist is the
private CODEX/ORDERS constants inside `Chronicle.java`.

## Key Architectural Principles Observed

### 1. Separation of Concerns
- UI completely unaware of Git specifics
- Domain model speaks only in knightly vocabulary
- Git implementation hidden in separate package

### 2. Caching Strategy
The `Vault` class implements a bounded LRU cache on an access-ordered
LinkedHashMap (get/put refresh recency; `removeEldestEntry` evicts
past capacity, all O(1)):
```java
public final class Vault<K, V> {
    private final LinkedHashMap<K, V> store;

    public Vault(int capacity) {
        // accessOrder = true: fetch() re-inserts the key, moving it to
        // the MRU end; eldest (LRU) head is dropped on overflow.
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }
    // synchronized fetch/acquire/purge/clear...
}
```
Used in `TalePane` to cache diff recounts (capacity 64) for instant
switching between feats. All Vault methods are synchronized, so it is
safe for the mixed FX/worker access the TalePane performs.

### 3. Background Task Processing
Long-running Git operations are wrapped in JavaFX `Task` objects to avoid blocking the UI thread:
```java
// Example from App.java
Task<Void> loadChronicleTask = new Task<>() {
    @Override protected Void call() throws Exception {
        // Git operations happen here
        return null;
    }
};
new Thread(loadChronicleTask).start();
```

### 4. Immutable Data Patterns
Many domain objects appear to be immutable or have controlled mutability, making them safe for use in UI components and caching. `Shortcut` is the explicit case: an enum of nine `KeyCodeCombination`s built once at class init, with no setter of any kind - the guarantee behind requirement R1.

### 5. Accounts, Scoping and the Seat Swap
Accounts are local profiles in the ledger - no server and no network:

- **`PasswordHasher`** (package-private in `forkknight.core`):
  PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte random salt, stored as
  `pbkdf2-sha256$iters$salt$hash` (both parts Base64). Iterations are read
  back from the stored string, so raising the cost later rehashes on the
  next sign-in instead of invalidating passwords; comparison is constant
  time via `MessageDigest.isEqual`, and `matches()` returns `false` for
  anything unrecognised (including the keeper's `locked$...` sentinel)
  without ever throwing. Tests call a package-private constructor of
  `AccountService` that injects a fast 10,000-iteration hasher, so the
  suite does not spend minutes in PBKDF2.
- **`AccountService`** is the only place sign up / sign in / switch /
  sign out / change password / delete account logic lives; it refuses
  with `AuthException` (message written for the knight). `guest` is a
  reserved username - the passwordless Wanderer.
- **Scoping**: `KnightMemory` is pointed at one account id at a time;
  `switchTo` / `signOut` repoint it and write `app_state.account.current`
  (global key/value, outside any knight's settings). All settings, notes
  and bookmarks are addressed by `(user_id, key)` composite primary keys.
- **The seat swap** (`App.summonKnights`): before showing `KnightsDialog`,
  App writes down the outgoing knight's open realms and window/sight
  (`rememberOpenRealms` + `persistMemory`). When the dialog closes with a
  different id in the seat, App restores the incoming knight's sight and
  window bounds (`applyKnightMemory`) and then hands him the seat
  (`restoreSeat`: clear the realm list, blank the view, reopen his
  remembered realms). The Wanderer's seat never blocks the git viewer.

### 6. Several Realms at Once (RealmSession)
`App` holds a `List<RealmSession>` plus an `activeRealm`; the toolbar's
realm picker is a `ComboBox<RealmSession>` (sessions compare equal by
path, so re-opening an open realm just activates it). Each session caches
its own `Weave` and `Scryer`, so switching paints from memory and then
calls a quiet background re-survey (`refreshTrailQuietly`) to pick up
external changes without disturbing the selection. The open set and
active path are persisted per knight under the settings keys `realms`
(newline-separated) and `realm`, and restored on a background thread at
launch.

## Documentation Summary

This architecture successfully achieves its educational goal:
1. Students learn Git concepts through memorable analogies
2. The encapsulation teaches good software design principles
3. The clean separation makes the code easier to understand and modify
4. The JavaFX implementation provides a modern desktop application experience

The project demonstrates:
- Domain-driven design through vocabulary mapping
- Proper encapsulation and information hiding
- Effective use of JavaFX for desktop applications
- Thoughtful application of caching and background processing