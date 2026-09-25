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
        assertEquals(2, versionAfterFirst);

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
        assertEquals(2, userVersion(dbUrl));

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
