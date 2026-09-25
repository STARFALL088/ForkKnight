package forkknight.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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

    @Test
    void testSettingsCrud() {
        KnightDatabase db = db();
        
        // Create
        db.setSetting("theme", "dark");
        assertEquals("dark", db.getSetting("theme").orElse(null));

        // Update
        db.setSetting("theme", "light");
        assertEquals("light", db.getSetting("theme").orElse(null));

        // Read all
        db.setSetting("window", "maximized");
        Map<String, String> all = db.getAllSettings();
        assertEquals(2, all.size());
        assertEquals("light", all.get("theme"));
        assertEquals("maximized", all.get("window"));

        // Delete
        db.deleteSetting("theme");
        assertFalse(db.getSetting("theme").isPresent());
    }

    @Test
    void testNotesCrud() {
        KnightDatabase db = db();
        
        // Create
        db.setNote("abc1234", "Fix bug");
        assertEquals("Fix bug", db.getNote("abc1234").orElse(null));

        // Update
        db.setNote("abc1234", "Fix critical bug");
        assertEquals("Fix critical bug", db.getNote("abc1234").orElse(null));

        // Delete
        db.deleteNote("abc1234");
        assertFalse(db.getNote("abc1234").isPresent());
    }

    @Test
    void testReopenKeepsDataAndDoesNotReRunMigrations() {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("reopen.db").toAbsolutePath();

        KnightDatabase first = new KnightDatabase(dbUrl);
        first.setSetting("sight", "night");
        first.setNote("abc1234", "Persist me");
        first.close();

        int versionAfterFirst = userVersion(dbUrl);
        assertEquals(1, versionAfterFirst);

        KnightDatabase second = new KnightDatabase(dbUrl);
        assertEquals("night", second.getSetting("sight").orElse(null));
        assertEquals("Persist me", second.getNote("abc1234").orElse(null));
        assertEquals(versionAfterFirst, userVersion(dbUrl));
        second.close();
    }

    @Test
    void testMigratesALegacyDatabaseWithoutLosingRows() {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("legacy.db").toAbsolutePath();

        // A database exactly as it stood before migrations: tables present,
        // but user_version still 0.
        try (java.sql.Connection raw = java.sql.DriverManager.getConnection(dbUrl);
             java.sql.Statement stmt = raw.createStatement()) {
            stmt.executeUpdate("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            stmt.executeUpdate("CREATE TABLE notes (hash TEXT PRIMARY KEY, note TEXT NOT NULL)");
            stmt.executeUpdate("INSERT INTO settings (key, value) VALUES ('realm', '/old/realm')");
            stmt.executeUpdate("INSERT INTO notes (hash, note) VALUES ('deadbeef', 'kept')");
        } catch (java.sql.SQLException e) {
            fail("could not prepare the legacy database: " + e.getMessage());
        }
        assertEquals(0, userVersion(dbUrl));

        KnightDatabase db = new KnightDatabase(dbUrl);
        assertEquals("/old/realm", db.getSetting("realm").orElse(null));
        assertEquals("kept", db.getNote("deadbeef").orElse(null));
        assertEquals(1, userVersion(dbUrl));

        // The ledger still works after the upgrade.
        db.setSetting("sight", "day");
        assertEquals("day", db.getSetting("sight").orElse(null));
        db.close();
    }

    @Test
    void testCloseThenReopenRecoversTheConnection() {
        KnightDatabase db = db();
        db.setSetting("window.width", "800");
        db.close();

        // A call after close() must transparently reopen, not explode.
        assertEquals("800", db.getSetting("window.width").orElse(null));
        db.deleteSetting("window.width");
        assertFalse(db.getSetting("window.width").isPresent());
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

    @Test
    void testBookmarksCrud() {
        KnightDatabase db = db();
        
        // Create
        db.setBookmark("/path/to/realm", "MyRealm");
        assertEquals("MyRealm", db.getBookmarkName("/path/to/realm").orElse(null));

        // Update
        db.setBookmark("/path/to/realm", "RenamedRealm");
        assertEquals("RenamedRealm", db.getBookmarkName("/path/to/realm").orElse(null));

        // Delete
        db.deleteBookmark("/path/to/realm");
        assertFalse(db.getBookmarkName("/path/to/realm").isPresent());
    }
}
