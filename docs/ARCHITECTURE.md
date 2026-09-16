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
9. **KnightMemory** - Memory facade: settings, notes and realm bookmarks
10. **KnightDatabase** - SQLite persistence (~/.forkknight/forkknight.db, JDBC)
11. **Dispatch** - A single file change entry

### UI Layer

1. **App** (`forkknight.App`) - Main JavaFX application: UI shell, menus,
   keyboard shorts, The Field tab, Herald/Council/Kamui wiring
2. **TalePane** (`forkknight.ui`) - Feat details: dispatch list + per-path
   recounts cached through the Vault
3. **SealDialog, CouncilDialog** (`forkknight`) - Commit message dialog and
   the statistics council

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
Many domain objects appear to be immutable or have controlled mutability, making them safe for use in UI components and caching.

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