package forkknight.git;

/**
 * Immutable model of a file changed in a commit. For renames, {@code oldPath}
 * and {@code newPath} differ; for all other entries they are equal.
 */
public record FileChange(String oldPath, String newPath, String status) {

    public boolean isRename() {
        return !oldPath.equals(newPath);
    }

    /** A abbreviated, display-friendly form of the git status code. */
    public String displayStatus() {
        if (status.startsWith("R")) {
            return "Renamed";
        }
        return switch (status) {
            case "A" -> "Added";
            case "D" -> "Deleted";
            case "M" -> "Modified";
            default -> status;
        };
    }

    public String displayPath() {
        return isRename() ? oldPath + " -> " + newPath : newPath;
    }
}
