# ForkKnight Developer Guide

This guide explains how to build, run, test, and contribute to the ForkKnight project.

## Prerequisites

Before you begin, ensure you have installed:
- **JDK 17 or later** (tested with JDK 26)
- **Git** (for version control)
- **Internet access** (to download dependencies via Maven Central)

## Getting the Source Code

```bash
# Clone the repository
git clone <repository-url>
cd ForkKnight

# Or if you already have the code:
cd /home/prantor/Coding/Projects/ForkKnight
```

## Building the Project

ForkKnight uses Gradle as its build system. The project includes the Gradle wrapper, so you don't need to install Gradle separately.

### Compile the Source Code

```bash
./gradlew compileJava
```

### Build the JAR File

```bash
./gradlew jar
```

The compiled JAR will be available at `build/libs/ForkKnight.jar`.

### Build Everything

```bash
./gradlew build
```

This will compile, test, and package the application.

## Running the Application

### Using Gradle (Recommended)

```bash
./gradlew run
```

### Running the Built JAR

```bash
java --module-path /path/to/javafx-sdk-24.0.2/lib \
     --add-modules javafx.controls,javafx.fxml \
     -cp build/classes/java/main:build/resources/main \
     forkknight.App
```

**Note**: You need to specify the JavaFX module path. If you've installed JavaFX via a package manager or manually, adjust the path accordingly.

### Development Tips

1. **Automatic Recompilation**: For faster development cycles, you can use:
   ```bash
   ./gradlew --continuous run
   ```
   This will automatically recompile and restart the application when source files change.

2. **Debugging**: To run with debugging enabled:
   ```bash
   ./gradlew run --debug-jvm
   ```

## Running Tests

### Unit Tests

```bash
./gradlew test
```

### Test Reports

After running tests, you can find HTML reports at:
- `build/reports/tests/test/index.html`

### Specific Test Classes

```bash
# Run only Chronicle tests
./gradlew test --tests forkknight.core.ChronicleTest

# Run only Vault tests
./gradlew test --tests forkknight.core.VaultTest
```

## Project Structure Overview

```
ForkKnight/
├── build.gradle                  # Gradle build configuration
├── settings.gradle               # Project settings
├── feature.md                    # Living ledger of development progress
├── gradlew                       # Unix/Linux Gradle wrapper
├── gradlew.bat                   # Windows Gradle wrapper
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── module-info.java             # Module descriptor
│   │   │   └── forkknight/                   # Main source code
│   │   │       ├── App.java                  # JavaFX application: shell, menus, shorts
│   │   │       ├── CouncilDialog.java        # Statistics dialog
│   │   │       ├── SealDialog.java           # Commit message dialog
│   │   │       ├── core/                     # Domain model with knightly vocabulary
│   │   │       │   ├── Chronicle.java        # Git command encapsulation (CODEX pattern)
│   │   │       │   ├── Feat.java             # Commit representation (with parent hashes)
│   │   │       │   ├── Banner.java           # Branch representation
│   │   │       │   ├── Sigil.java            # Tag representation
│   │   │       │   ├── Dispatch.java         # File change entry
│   │   │       │   ├── Scryer.java           # Search: trie + inverted index + lenses
│   │   │       │   ├── Weave.java            # Commit graph lane assignment
│   │   │       │   ├── Vault.java            # LRU cache implementation
│   │   │       │   ├── Chronicler.java       # Statistics via bounded top-K heaps
│   │   │       │   ├── KnightMemory.java     # Memory facade (settings, notes, bookmarks)
│   │   │       │   └── KnightDatabase.java   # SQLite persistence (JDBC)
│   │   │       └── ui/                       # User interface components
│   │   │           └── TalePane.java         # Feat details + diff view
│   │   └── resources/
│   │       └── forkknight/
│   │           └── dark-theme.css            # Night Sight theme styling
│   └── test/
│       └── java/
│           └── forkknight/core/              # Tests for all core classes
│               ├── ChronicleTest.java        # 34 tests
│               ├── ScryerTest.java          # 14 tests
│               ├── WeaveTest.java           # 8 tests
│               ├── ChroniclerTest.java      # 8 tests
│               ├── KnightMemoryTest.java    # 6 tests
│               ├── KnightDatabaseTest.java  # 3 tests
│               └── VaultTest.java           # 7 tests
```

**Note**: there is no `forkknight.git` package - the rebrand folded the
entire git CLI into `Chronicle`'s private CODEX. All tests live under
`forkknight.core` (80 total).

## Key Implementation Details

### The CODEX Pattern (Vocabulary Encapsulation)

The `Chronicle` class demonstrates a clever design pattern for encapsulating Git terminology:

```java
// Public API uses knightly terms
public List<Feat> surveyTrail() { ... }   // Maps to "git log"
public List<Dispatch> muster() { ... }     // Maps to "git status"
public String recount(String hash) { ... } // Maps to "git show"
public Feat seal(String words) { ... }     // Maps to "git commit"
public void rally(String ally) { ... }     // Maps to "git fetch"

// Private implementation hides Git commands behind the CODEX map,
// built once at class init and guarded so a broken translation fails
// fast:
private static final Map<String, String> CODEX = buildCodex();

private Command command(String knightly) {
    return new Command(realmDir, CODEX.get(knightly));
}
```

### Caching with Vault

The `Vault` class implements an LRU (Least Recently Used) cache on an
access-ordered LinkedHashMap (all operations synchronized):

```java
public final class Vault<K, V> {
    private final LinkedHashMap<K, V> store;

    public Vault(int capacity) {
        // accessOrder = true: get() refreshes recency; eldest entry is
        // evicted automatically once size passes capacity.
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public V fetch(K key) { synchronized (this) { return store.get(key); } }
    public V acquire(K key, Function<K, V> loader) { ... } // loads outside the lock
    // ... purge, clear, size
}
```

Used in `TalePane` to cache per-file diff recounts (capacity 64) for
instant viewing when switching between feats.

### Background Processing

Long-running Git operations are executed in daemon background threads
via a small helper, keeping the UI responsive:

```java
// From App.java - every survey/muster/herald runs like this
Task<Weave> task = new Task<>() {
    @Override protected Weave call() throws Exception {
        List<Feat> feats = service.surveyTrail(banner, Integer.MAX_VALUE);
        scryer = new Scryer(feats);   // index on the worker thread
        return Weave.of(feats);
    }
};
task.setOnSucceeded(e -> chronicleData.setAll(task.getValue().rows()));
task.setOnFailed(e -> showError("The survey failed: " + ...));
startDaemon(task, "trail-survey");   // daemon Thread under a name
```

Result callbacks (setOnSucceeded/setOnFailed) fire on the JavaFX
Application Thread, so UI updates are safe there without
Platform.runLater.

## Contributing Guidelines

### Making Changes

1. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** following the existing code style:
   - Use 4-space indentation
   - Follow Java naming conventions
   - Add Javadoc comments for public methods and classes
   - Keep the knightly vocabulary theme consistent

3. **Test your changes**:
   ```bash
   ./gradlew test
   ```

4. **Commit your changes**:
   ```bash
   git add .
   git commit -m "Description of your changes"
   ```

5. **Push and create a pull request**:
   ```bash
   git push origin feature/your-feature-name
   ```

### Code Style

- Follow the existing Java code style in the project
- Import statements are grouped: standard Java, JavaFX, project-specific
- Classes and methods have Javadoc comments
- Constants are in UPPER_SNAKE_CASE
- Variables and methods use camelCase

### Documentation

- Update documentation in the `docs/` directory if your changes affect usage
- Keep the knightly vocabulary theme consistent in comments and documentation
- Explain any new domain concepts in terms of the existing metaphor

## Troubleshooting

### Common Issues

1. **JavaFX not found**:
   ```
   Error: JavaFX runtime components are missing, and are required to run this application
   ```
   Solution: Ensure you're specifying the correct `--module-path` to your JavaFX installation.

2. **Build failures**:
   ```
   Could not resolve all dependencies for configuration ':compileClasspath'.
   ```
   Solution: Check your internet connection and try `./gradlew --refresh-dependencies`.

3. **Test failures**:
   - Ensure you're using JDK 17 or later
   - Some tests may depend on specific system states

### Getting Help

- Check the `README.md` for basic information
- Look at the test cases in `src/test/java/` for examples of how to use the API
- Examine the source code comments for implementation details

## License

This project is created for educational purposes as part of an AP Lab assignment.

---

*This documentation was created to help developers understand and work with the ForkKnight project without modifying the original source files.*