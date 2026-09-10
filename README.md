# ForkKnight

A lightweight desktop Git client/visualizer for the AP Lab JavaFX assignment.

## Project Structure

- `src/main/java/forkknight/` - Java source code
- `build.gradle` - Gradle build script
- `settings.gradle` - Gradle settings
- `src/main/java/module-info.java` - Java module descriptor

## Prerequisites

- JDK 17 or later (we tested with JDK 26)
- JavaFX 24.0.2 (matching the version in build.gradle)

## Setup

### Option 1: Install OpenJFX via package manager (Arch Linux example)

```bash
sudo pacman -S openjfx
```

### Option 2: Manual download

1. Download JavaFX SDK from https://gluonhq.com/products/javafx/
2. Extract and note the path to the `lib` directory.

## Running the Application

### Using Gradle (if JavaFX is set up via the plugin)

```bash
./gradlew run
```

### Using Java directly (if you have JavaFX in a custom location)

Replace `/path/to/javafx-sdk-24.0.2/lib` with the actual path.

```bash
java --module-path /path/to/javafx-sdk-24.0.2/lib --add-modules javafx.controls,javafx.fxml -cp build/classes/java/main forkknight.App
```

## Next Steps for Development

1. ~~Implement a directory chooser to select a local Git repository.~~ (done)
2. ~~Use JGit or ProcessBuilder to fetch Git log and display commits.~~ (done)
3. ~~Show a commit graph on a Canvas or using a list.~~ (done, table-based)
4. ~~Allow selecting a commit to view the file tree and diff.~~ (done)
5. ~~Add branch selector / show all branches in the log.~~ (done)
6. ~~Stage and commit changes from within the app.~~ (done)
7. Search/filter the commit history.
8. Create/switch/delete branches from the UI.

## Notes

- Selecting a commit in the log shows its changed files (with add/delete/rename status) and the unified diff of the selected file.
- All Git operations run on background threads to avoid blocking the UI.
- Git access goes through the git CLI (see `GitService`); rename detection is enabled for changed-file listings.