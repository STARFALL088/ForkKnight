package forkknight.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Memory layer that forwards all persistence to {@link KnightDatabase}.
 * Provides settings, notes and realm bookmark management, all scoped to the
 * account the knight is signed in as. Until he signs in, that account is the
 * keeper - the one who adopted every row that predates the accounts.
 */
public final class KnightMemory {
    private final KnightDatabase db;
    private long currentUserId;

    public KnightMemory() {
        this.db = new KnightDatabase();
        this.currentUserId = defaultAccountId(db);
    }

    KnightMemory(KnightDatabase db) {
        this.db = db;
        this.currentUserId = defaultAccountId(db);
    }

    private static long defaultAccountId(KnightDatabase db) {
        return db.findUserByUsername("keeper")
            .map(Account::id)
            .orElseGet(() -> db.guestAccount().id());
    }

    // -------------------- Who is signed in --------------------

    /** The id of the knight currently served by this memory. */
    public long currentUserId() {
        return currentUserId;
    }

    /** The knight currently served by this memory, if he still rides. */
    public Optional<Account> currentAccount() {
        return db.findUserById(currentUserId);
    }

    /** Serves another knight's ledger from now on. */
    public void switchTo(long userId) {
        Account account = db.findUserById(userId)
            .orElseThrow(() -> new KnightDbException("No such knight: " + userId));
        currentUserId = account.id();
    }

    /** Drops back to the passwordless wanderer. */
    public void signOut() {
        currentUserId = db.guestAccount().id();
    }

    // -------------------- Settings --------------------

    /** Reads all settings of the signed-in knight. */
    public Map<String, String> recallAll() {
        return new HashMap<>(db.getAllSettings(currentUserId));
    }

    public Optional<String> recall(String key) {
        return db.getSetting(currentUserId, key);
    }

    public Optional<String> recall(String key, String fallback) {
        return db.getSetting(currentUserId, key).or(() -> Optional.ofNullable(fallback));
    }

    /** Stores or updates a setting. */
    public void remember(String key, String value) {
        db.setSetting(currentUserId, key, value == null ? "" : value);
    }

    /** Stores or deletes a note for a feat hash. */
    public void rememberNote(String hash, String note) {
        if (hash == null || hash.isBlank()) return;
        if (note == null || note.isBlank()) {
            db.deleteNote(currentUserId, hash);
        } else {
            db.setNote(currentUserId, hash, note);
        }
    }

    public Optional<String> recallNote(String hash) {
        if (hash == null || hash.isBlank()) return Optional.empty();
        return db.getNote(currentUserId, hash);
    }

    public void forget(String key) {
        db.deleteSetting(currentUserId, key);
    }

    // Realm bookmark helpers -------------------------------------------------
    public Map<String, String> recallAllBookmarks() {
        return db.getAllBookmarks(currentUserId);
    }

    public Optional<String> recallBookmarkName(String realmPath) {
        return db.getBookmarkName(currentUserId, realmPath);
    }

    public void setBookmarkName(String realmPath, String name) {
        db.setBookmark(currentUserId, realmPath, name);
    }

    public void forgetBookmark(String realmPath) {
        db.deleteBookmark(currentUserId, realmPath);
    }

    /** Releases the shared ledger connection. */
    public void close() {
        db.close();
    }
}
