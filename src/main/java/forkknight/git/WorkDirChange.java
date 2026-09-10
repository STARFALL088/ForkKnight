package forkknight.git;

/**
 * Immutable model of a working-copy change. A path may appear twice: once
 * staged (index) and once unstaged (worktree).
 */
public record WorkDirChange(String newPath, String oldPath, String statusCode,
                            boolean staged) {

    /** True when the change is a rename or copy. */
    public boolean isRename() {
        return !oldPath.equals(newPath);
    }

    /** Human-readable description of the status code. */
    public String description() {
        return switch (statusCode) {
            case "M" -> "Modified";
            case "A" -> "Added";
            case "D" -> "Deleted";
            case "R" -> "Renamed";
            case "C" -> "Copied";
            case "?" -> "Untracked";
            case "!" -> "Ignored";
            default -> statusCode;
        };
    }
}
