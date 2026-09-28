package forkknight.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KnightDatabaseTest {

    @TempDir
    Path tempDir;

    private KnightDatabase db() {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath();
        return new KnightDatabase(dbUrl);
    }

    /** The keeper, who exists from the moment the ledger is migrated. */
    private long keeper(KnightDatabase db) {
        return db.findUserByUsername("keeper").orElseThrow().id();
    }

    @Test
    void testSettingsCrud() {
        KnightDatabase db = db();
        long me = keeper(db);

        // Create
        db.setSetting(me, "theme", "dark");
        assertEquals("dark", db.getSetting(me, "theme").orElse(null));

        // Update
        db.setSetting(me, "theme", "light");
        assertEquals("light", db.getSetting(me, "theme").orElse(null));

        // Read all
        db.setSetting(me, "window", "maximized");
        Map<String, String> all = db.getAllSettings(me);
        assertEquals(2, all.size());
        assertEquals("light", all.get("theme"));
        assertEquals("maximized", all.get("window"));

        // Delete
        db.deleteSetting(me, "theme");
        assertFalse(db.getSetting(me, "theme").isPresent());
    }

    @Test
    void testNotesCrud() {
        KnightDatabase db = db();
        long me = keeper(db);

        // Create
        db.setNote(me, "abc1234", "Fix bug");
        assertEquals("Fix bug", db.getNote(me, "abc1234").orElse(null));

        // Update
        db.setNote(me, "abc1234", "Fix critical bug");
        assertEquals("Fix critical bug", db.getNote(me, "abc1234").orElse(null));

        // Delete
        db.deleteNote(me, "abc1234");
        assertFalse(db.getNote(me, "abc1234").isPresent());
    }

    @Test
    void testBookmarksCrud() {
        KnightDatabase db = db();
        long me = keeper(db);

        // Create
        db.setBookmark(me, "/path/to/realm", "MyRealm");
        assertEquals("MyRealm", db.getBookmarkName(me, "/path/to/realm").orElse(null));

        // Update
        db.setBookmark(me, "/path/to/realm", "RenamedRealm");
        assertEquals("RenamedRealm", db.getBookmarkName(me, "/path/to/realm").orElse(null));

        // Delete
        db.deleteBookmark(me, "/path/to/realm");
        assertFalse(db.getBookmarkName(me, "/path/to/realm").isPresent());
    }

    @Test
    void testReopenKeepsDataAndDoesNotReRunMigrations() {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("reopen.db").toAbsolutePath();

        KnightDatabase first = new KnightDatabase(dbUrl);
        long me = keeper(first);
        first.setSetting(me, "sight", "night");
        first.setNote(me, "abc1234", "Persist me");
        first.close();

        int versionAfterFirst = userVersion(dbUrl);
        assertEquals(KnightDatabase.schemaVersion(), versionAfterFirst);

        KnightDatabase second = new KnightDatabase(dbUrl);
        assertEquals("night", second.getSetting(me, "sight").orElse(null));
        assertEquals("Persist me", second.getNote(me, "abc1234").orElse(null));
        assertEquals(versionAfterFirst, userVersion(dbUrl));
        second.close();
    }

    @Test
    void testMigratesALegacyDatabaseAndHandsEveryRowToTheKeeper() {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("legacy.db").toAbsolutePath();

        // A database exactly as it stood before accounts: tables present,
        // no user_id anywhere, user_version still 0.
        try (java.sql.Connection raw = java.sql.DriverManager.getConnection(dbUrl);
             java.sql.Statement stmt = raw.createStatement()) {
            stmt.executeUpdate("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            stmt.executeUpdate("CREATE TABLE notes (hash TEXT PRIMARY KEY, note TEXT NOT NULL)");
            stmt.executeUpdate("CREATE TABLE realm_bookmarks (realm_path TEXT PRIMARY KEY, name TEXT NOT NULL)");
            stmt.executeUpdate("INSERT INTO settings (key, value) VALUES ('realm', '/old/realm')");
            stmt.executeUpdate("INSERT INTO notes (hash, note) VALUES ('deadbeef', 'kept')");
            stmt.executeUpdate("INSERT INTO realm_bookmarks (realm_path, name) VALUES ('/old/realm', 'OldRealm')");
        } catch (java.sql.SQLException e) {
            fail("could not prepare the legacy database: " + e.getMessage());
        }
        assertEquals(0, userVersion(dbUrl));

        KnightDatabase db = new KnightDatabase(dbUrl);
        long keeper = keeper(db);

        // Nothing was lost, and it all belongs to the keeper.
        assertEquals("/old/realm", db.getSetting(keeper, "realm").orElse(null));
        assertEquals("kept", db.getNote(keeper, "deadbeef").orElse(null));
        assertEquals("OldRealm", db.getBookmarkName(keeper, "/old/realm").orElse(null));
        assertEquals(KnightDatabase.schemaVersion(), userVersion(dbUrl));

        // The keeper was locked: no password rides with it.
        Account keeperAccount = db.findUserById(keeper).orElseThrow();
        assertTrue(keeperAccount.passwordHash().startsWith("locked$"));
        assertFalse(keeperAccount.guest());

        // The ledger still works after the upgrade.
        db.setSetting(keeper, "sight", "day");
        assertEquals("day", db.getSetting(keeper, "sight").orElse(null));
        db.close();
    }

    @Test
    void testAccountsNeverSeeEachOthersRows() {
        KnightDatabase db = db();
        long keeper = keeper(db);
        Account alice = db.createUser("alice", "Alice", "hash-a", false);
        Account bob = db.createUser("bob", "Bob", "hash-b", false);

        db.setSetting(keeper, "sight", "night");
        db.setSetting(alice.id(), "sight", "day");
        db.setNote(alice.id(), "deadbeef", "alice's note");
        db.setBookmark(bob.id(), "/path/to/realm", "Bob's Realm");

        // Same keys, same realm path - different owners, no bleed.
        assertEquals("day", db.getSetting(alice.id(), "sight").orElse(null));
        assertEquals("night", db.getSetting(keeper, "sight").orElse(null));
        assertEquals(Optional.empty(), db.getSetting(bob.id(), "sight"));
        assertEquals("alice's note", db.getNote(alice.id(), "deadbeef").orElse(null));
        assertEquals(Optional.empty(), db.getNote(keeper, "deadbeef"));
        assertEquals("Bob's Realm", db.getBookmarkName(bob.id(), "/path/to/realm").orElse(null));
        assertEquals(Optional.empty(), db.getBookmarkName(alice.id(), "/path/to/realm"));
    }

    @Test
    void testUsernamesAreUniqueButCaseInsensitive() {
        KnightDatabase db = db();
        Account alice = db.createUser("alice", "Alice", "hash-a", false);

        assertTrue(db.usernameExists("alice"));
        assertTrue(db.usernameExists("ALICE"));
        assertTrue(db.findUserByUsername("Alice").isPresent());
        assertEquals(alice.id(), db.findUserByUsername("ALICE").orElseThrow().id());

        KnightDbException taken = assertThrows(KnightDbException.class,
            () -> db.createUser("Alice", "Impostor", "hash-x", false));
        assertTrue(taken.getMessage().contains("already rides"));
        assertEquals(1, db.listUsers().stream().filter(a -> a.username().equalsIgnoreCase("alice")).count());
    }

    @Test
    void testDeletingAKnightErasesTheirLedger() {
        KnightDatabase db = db();
        long keeper = keeper(db);
        Account alice = db.createUser("alice", "Alice", "hash-a", false);

        db.setSetting(alice.id(), "sight", "day");
        db.setNote(alice.id(), "deadbeef", "alice's note");
        db.setBookmark(alice.id(), "/path/to/realm", "Alice's Realm");
        db.setSetting(keeper, "sight", "night");

        db.deleteUser(alice.id());

        // Alice's rows fell with her (ON DELETE CASCADE)...
        assertEquals(Optional.empty(), db.findUserById(alice.id()));
        assertEquals(Optional.empty(), db.getSetting(alice.id(), "sight"));
        assertEquals(Optional.empty(), db.getNote(alice.id(), "deadbeef"));
        assertEquals(Optional.empty(), db.getBookmarkName(alice.id(), "/path/to/realm"));
        // ...and the keeper's ledger is untouched.
        assertEquals("night", db.getSetting(keeper, "sight").orElse(null));
        assertEquals(1, db.listUsers().size());
    }

    @Test
    void testTheWandererIsCreatedOnce() {
        KnightDatabase db = db();
        Account guest = db.guestAccount();
        Account again = db.guestAccount();

        assertTrue(guest.guest());
        assertEquals(guest.id(), again.id());
        assertEquals("", guest.passwordHash());
        assertEquals(1, db.listUsers().stream().filter(Account::guest).count());

        // The guest name is reserved for the passwordless wanderer.
        KnightDbException reserved = assertThrows(KnightDbException.class,
            () -> db.createUser("guest", "Someone Else", "hash", false));
        assertTrue(reserved.getMessage().contains("already rides"));
    }

    @Test
    void testCloseThenReopenRecoversTheConnection() {
        KnightDatabase db = db();
        long me = keeper(db);
        db.setSetting(me, "window.width", "800");
        db.close();

        // A call after close() must transparently reopen, not explode.
        assertEquals("800", db.getSetting(me, "window.width").orElse(null));
        db.deleteSetting(me, "window.width");
        assertFalse(db.getSetting(me, "window.width").isPresent());
        db.close();
    }

    // -------------------- The knight's own page --------------------

    @Test
    void testMigratesAV3LedgerAndGivesEveryAccountItsOwnPage() {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("v3.db").toAbsolutePath();

        // A database exactly as v4 found it: accounts without profile
        // columns, app_state present, user_version still 3.
        try (java.sql.Connection raw = java.sql.DriverManager.getConnection(dbUrl);
             java.sql.Statement stmt = raw.createStatement()) {
            stmt.executeUpdate("CREATE TABLE users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL COLLATE NOCASE UNIQUE, "
                + "display_name TEXT NOT NULL, "
                + "password_hash TEXT NOT NULL, "
                + "is_guest INTEGER NOT NULL DEFAULT 0, "
                + "created_at TEXT NOT NULL)");
            stmt.executeUpdate("CREATE TABLE settings (user_id INTEGER NOT NULL "
                + "REFERENCES users(id) ON DELETE CASCADE, key TEXT NOT NULL, "
                + "value TEXT NOT NULL, PRIMARY KEY (user_id, key))");
            stmt.executeUpdate("CREATE TABLE notes (user_id INTEGER NOT NULL "
                + "REFERENCES users(id) ON DELETE CASCADE, hash TEXT NOT NULL, "
                + "note TEXT NOT NULL, PRIMARY KEY (user_id, hash))");
            stmt.executeUpdate("CREATE TABLE realm_bookmarks (user_id INTEGER NOT NULL "
                + "REFERENCES users(id) ON DELETE CASCADE, realm_path TEXT NOT NULL, "
                + "name TEXT NOT NULL, PRIMARY KEY (user_id, realm_path))");
            stmt.executeUpdate("CREATE TABLE app_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            stmt.executeUpdate("INSERT INTO users (username, display_name, "
                + "password_hash, is_guest, created_at) VALUES ('keeper', 'The Keeper', "
                + "'locked$1000$abc', 0, '2026-09-01T00:00:00Z')");
            stmt.executeUpdate("INSERT INTO users (username, display_name, "
                + "password_hash, is_guest, created_at) VALUES ('guest', 'Wandering Guest', "
                + "'', 1, '2026-09-01T00:00:00Z')");
            stmt.executeUpdate("INSERT INTO notes (user_id, hash, note) VALUES (1, 'deadbeef', 'kept')");
            stmt.executeUpdate("INSERT INTO app_state (key, value) VALUES ('gate.requireSignIn', 'true')");
            stmt.executeUpdate("PRAGMA user_version = 3");
        } catch (java.sql.SQLException e) {
            fail("could not prepare the v3 ledger: " + e.getMessage());
        }
        assertEquals(3, userVersion(dbUrl));

        KnightDatabase db = new KnightDatabase(dbUrl);

        // v4 ran: every old account carries the new page columns...
        Account found = db.findUserByUsername("keeper").orElseThrow();
        assertEquals("", found.bio());
        assertEquals("Squire", found.title());
        Account wanderer = db.findUserByUsername("guest").orElseThrow();
        assertEquals("", wanderer.bio());
        assertEquals("Squire", wanderer.title());
        assertEquals(KnightDatabase.schemaVersion(), userVersion(dbUrl));

        // ...nothing else was lost, and the page can be written.
        long keeper = keeper(db);
        assertEquals("kept", db.getNote(keeper, "deadbeef").orElse(null));
        assertEquals(Boolean.TRUE, db.getGlobal("gate.requireSignIn").map(Boolean::parseBoolean).orElse(null));
        db.updateProfile(keeper, "The Keeper", "I keep.", "Warden");
        assertEquals("I keep.", db.findUserById(keeper).orElseThrow().bio());
        assertEquals("Warden", db.findUserById(keeper).orElseThrow().title());
        db.close();
    }

    @Test
    void testProfileWritesAndKeepsKnightsApart() {
        KnightDatabase db = db();
        long keeper = keeper(db);
        Account alice = db.createUser("alice", "Alice", "hash-a", false);

        // Fresh accounts start with the defaults the migration set.
        assertEquals("", alice.bio());
        assertEquals("Squire", alice.title());

        db.updateProfile(alice.id(), "Alice the Bold", "I ride at dawn.", "Paladin");

        Account updated = db.findUserById(alice.id()).orElseThrow();
        assertEquals("Alice the Bold", updated.displayName());
        assertEquals("I ride at dawn.", updated.bio());
        assertEquals("Paladin", updated.title());

        // The keeper's own page never moved.
        Account untouched = db.findUserById(keeper).orElseThrow();
        assertEquals("Keeper of the Ledger", untouched.displayName());
        assertEquals("", untouched.bio());
        assertEquals("Squire", untouched.title());
        db.close();
    }

    @Test
    void testProfileStatsCountEachKnightsKeepsakes() {
        KnightDatabase db = db();
        long keeper = keeper(db);
        Account alice = db.createUser("alice", "Alice", "hash-a", false);

        db.setNote(alice.id(), "aaaa1111", "one");
        db.setNote(alice.id(), "bbbb2222", "two");
        db.setBookmark(alice.id(), "/alice/realm", "Alice's Realm");
        db.setNote(keeper, "cccc3333", "keeper's note");

        KnightDatabase.ProfileStats aliceStats = db.profileStats(alice.id());
        assertEquals(2, aliceStats.notes());
        assertEquals(1, aliceStats.bookmarks());

        KnightDatabase.ProfileStats keeperStats = db.profileStats(keeper);
        assertEquals(1, keeperStats.notes());
        assertEquals(0, keeperStats.bookmarks());
        db.close();
    }

    @Test
    void testDeletingAKnightTakesHisPageAndKeepsakesWithHim() {
        KnightDatabase db = db();
        Account alice = db.createUser("alice", "Alice", "hash-a", false);
        db.updateProfile(alice.id(), "Alice", "Gone soon.", "Knight");
        db.setNote(alice.id(), "aaaa1111", "one");
        db.setBookmark(alice.id(), "/alice/realm", "Alice's Realm");

        db.deleteUser(alice.id());

        assertEquals(Optional.empty(), db.findUserById(alice.id()));
        assertEquals(Optional.empty(), db.getNote(alice.id(), "aaaa1111"));
        assertEquals(Optional.empty(), db.getBookmarkName(alice.id(), "/alice/realm"));
        KnightDatabase.ProfileStats stats = db.profileStats(alice.id());
        assertEquals(0, stats.notes());
        assertEquals(0, stats.bookmarks());
        db.close();
    }

    private int userVersion(String dbUrl) {
        try (java.sql.Connection raw = java.sql.DriverManager.getConnection(dbUrl);
             java.sql.Statement stmt = raw.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (java.sql.SQLException e) {
            throw new AssertionError("could not read user_version", e);
        }
    }
}
