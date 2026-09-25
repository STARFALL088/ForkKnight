package forkknight.core;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * One realm the knight has open (requirement R2): its chronicle, plus the
 * trail and view state that belong to it alone. Switching realms swaps the
 * active session instead of reopening one from disk, so each realm keeps
 * its cached weave, scrying index, raised banner and selected feat.
 *
 * <p>Sessions are equal when they point at the same directory, which is
 * what the realm picker keys on.
 */
public final class RealmSession {

    private final Chronicle chronicle;
    private String banner;
    private String selectedHash;
    private Weave weave;
    private Scryer scryer;

    public RealmSession(Chronicle chronicle) {
        this.chronicle = Objects.requireNonNull(chronicle, "a session needs a chronicle");
    }

    public Chronicle chronicle() {
        return chronicle;
    }

    public File realmDir() {
        return chronicle.getRealmDir();
    }

    public String path() {
        return chronicle.getRealmDir().getAbsolutePath();
    }

    /** The banner being surveyed here, or null for the realm's own. */
    public String banner() {
        return banner;
    }

    public void chooseBanner(String banner) {
        this.banner = banner;
    }

    /** The feat last selected in this realm's trail. */
    public String selectedHash() {
        return selectedHash;
    }

    public void selectFeat(String hash) {
        this.selectedHash = hash;
    }

    /** True once this realm's trail has been surveyed into memory. */
    public boolean surveyed() {
        return weave != null && scryer != null;
    }

    public Weave weave() {
        return weave;
    }

    public Scryer scryer() {
        return scryer;
    }

    /** Caches a freshly surveyed trail so a later switch costs no git. */
    public void rememberTrail(Weave weave, Scryer scryer) {
        this.weave = Objects.requireNonNull(weave, "weave");
        this.scryer = Objects.requireNonNull(scryer, "scryer");
    }

    /** Drops the cache, forcing the next visit to survey the realm again. */
    public void forgetTrail() {
        this.weave = null;
        this.scryer = null;
    }

    // ------------------------------------------------------------------
    // The remembered set of open realms
    // ------------------------------------------------------------------

    /** The stored form of the open set: distinct paths, one per line. */
    public static String encodeRealms(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String path : paths) {
            if (path != null && !path.isBlank()) {
                distinct.add(path);
            }
        }
        return String.join("\n", distinct);
    }

    /**
     * Reads back a stored open set: one path per line, blanks dropped,
     * first occurrence kept, order preserved. A null or empty mark reads
     * as no realms at all.
     */
    public static List<String> decodeRealms(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String line : stored.split("\n", -1)) {
            if (!line.isBlank()) {
                distinct.add(line);
            }
        }
        return List.copyOf(distinct);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RealmSession session)) {
            return false;
        }
        return path().equals(session.path());
    }

    @Override
    public int hashCode() {
        return path().hashCode();
    }

    @Override
    public String toString() {
        return realmDir().getName();
    }
}
