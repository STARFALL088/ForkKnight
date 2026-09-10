package forkknight.core;

/**
 * A battle report for a single path - what other tools call a file status
 * entry. {@code staged} entries are forged into the war chest (index);
 * the rest await orders in the field (worktree).
 */
public record Dispatch(String path, String oldPath, String statusCode, boolean staged) {

    public boolean isRenaming() {
        return oldPath != null && !oldPath.equals(path);
    }

    /** Knightly vocabulary for the raw status code. */
    public String description() {
        return switch (statusCode) {
            case "M" -> "Reforged";
            case "A" -> "Conscripted";
            case "D" -> "Fallen";
            // Keep raw "?" (unscouted) and "!" (ignored) visible as-is.
            default -> switch (statusCode) {
                case "?" -> "Unscouted";
                case "!" -> "Shunned";
                default -> statusCode;
            };
        };
    }
}
