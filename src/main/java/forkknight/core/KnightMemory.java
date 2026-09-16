package forkknight.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Memory layer that forwards all persistence to {@link KnightDatabase}.
 * Provides settings, notes, and realm bookmark management.
 */
public final class KnightMemory {
    private final KnightDatabase db;

    public KnightMemory() {
        this.db = new KnightDatabase();
        // Database schema is initialized lazily by KnightDatabase.
    }

    KnightMemory(KnightDatabase db) {
        this.db = db;
    }

    /** Reads all settings from the database. */
    public Map<String, String> recallAll() {
        return new HashMap<>(db.getAllSettings());
    }

    public Optional<String> recall(String key) {
        return db.getSetting(key);
    }

    public Optional<String> recall(String key, String fallback) {
        return db.getSetting(key).or(() -> Optional.ofNullable(fallback));
    }

    /** Stores or updates a setting. */
    public void remember(String key, String value) {
        db.setSetting(key, value == null ? "" : value);
    }

    /** Stores or deletes a note for a feat hash. */
    public void rememberNote(String hash, String note) {
        if (hash == null || hash.isBlank()) return;
        if (note == null || note.isBlank()) {
            db.deleteNote(hash);
        } else {
            db.setNote(hash, note);
        }
    }

    public Optional<String> recallNote(String hash) {
        if (hash == null || hash.isBlank()) return Optional.empty();
        return db.getNote(hash);
    }

    public void forget(String key) {
        db.deleteSetting(key);
    }

    // Realm bookmark helpers -------------------------------------------------
    public Map<String, String> recallAllBookmarks() {
        return db.getAllBookmarks();
    }

    public Optional<String> recallBookmarkName(String realmPath) {
        return db.getBookmarkName(realmPath);
    }

    public void setBookmarkName(String realmPath, String name) {
        db.setBookmark(realmPath, name);
    }

    public void forgetBookmark(String realmPath) {
        db.deleteBookmark(realmPath);
    }
}
