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
