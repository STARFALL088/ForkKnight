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
    /** Ledger state key: the knight who was signed in when the app last left. */
    private static final String CURRENT_ACCOUNT = "account.current";

    private final KnightDatabase db;
    private long currentUserId;

    public KnightMemory() {
        this(new KnightDatabase());
    }

    KnightMemory(KnightDatabase db) {
        this.db = db;
        this.currentUserId = restoreAccountId(db);
    }

    /**
     * Who was signed in last time: read back from the ledger, or the keeper
     * when nobody has been marked yet (and the keeper when the mark went
     * stale - a dismissed knight does not haunt the launch).
     */
    private static long restoreAccountId(KnightDatabase db) {
        Optional<String> saved = db.getGlobal(CURRENT_ACCOUNT);
        if (saved.isPresent()) {
            try {
                long id = Long.parseLong(saved.get().trim());
                if (db.findUserById(id).isPresent()) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                // An unreadable mark is no mark at all.
            }
        }
        return defaultAccountId(db);
    }

    private static long defaultAccountId(KnightDatabase db) {
        return db.findUserByUsername("keeper")
            .map(Account::id)
            .orElseGet(() -> db.guestAccount().id());
    }

    private void rememberCurrentAccount() {
        db.setGlobal(CURRENT_ACCOUNT, String.valueOf(currentUserId));
    }

    /** Internal: the ledger behind this memory, for the account services. */
    KnightDatabase ledger() {
        return db;
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

    /** Serves another knight's ledger from now on, and remembers him. */
    public void switchTo(long userId) {
        Account account = db.findUserById(userId)
            .orElseThrow(() -> new KnightDbException("No such knight: " + userId));
        currentUserId = account.id();
        rememberCurrentAccount();
    }

    /** Drops back to the passwordless wanderer, and remembers that. */
    public void signOut() {
        currentUserId = db.guestAccount().id();
        rememberCurrentAccount();
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
