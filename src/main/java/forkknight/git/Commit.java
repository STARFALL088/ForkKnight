package forkknight.git;

import java.time.LocalDate;

/** Immutable model of a git commit. */
public record Commit(String hash, String author, String email, LocalDate date,
                     String message, String body) {

    public String shortHash() {
        return hash != null && hash.length() >= 7 ? hash.substring(0, 7) : hash;
    }

    public String summary() {
        return message;
    }
}
