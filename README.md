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

1. Implement a directory chooser to select a local Git repository.
2. Use JGit or ProcessBuilder to fetch Git log and display commits.
3. Show a commit graph on a Canvas or using a list.
4. Allow selecting a commit to view the file tree and diff.

## Notes

- The current starter just shows a welcome screen.
- All Git operations should be performed on background threads to avoid blocking the UI.
- Consider using the [Eclipse JGit](https://www.eclipse.org/jgit/) library for Git operations.