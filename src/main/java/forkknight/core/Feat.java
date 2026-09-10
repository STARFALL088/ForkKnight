package forkknight.core;

import java.time.LocalDate;
import java.util.List;

/**
 * A sealed victory in the realm's chronicle - what other tools call a
 * "commit". Holds the hero's mark (hash), author, date, summary and the
 * full chronicle entry (message body).
 */
public record Feat(String hash, List<String> parentHashes, String author, String email,
                   LocalDate date, String summary, String body) {

    /** Short display form of the hash, like 0f4c2ab. */
    public String shortHash() {
        return hash != null && hash.length() >= 7 ? hash.substring(0, 7) : hash;
    }

    public boolean isFusion() {
        return parentHashes != null && parentHashes.size() > 1;
    }
}
