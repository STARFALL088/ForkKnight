package forkknight.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KnightMemoryTest {

    @TempDir
    Path tempDir;

    private KnightMemory memory() {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("memory.db").toAbsolutePath();
        return new KnightMemory(new KnightDatabase(dbUrl));
    }

    @Test
    void missingFileReadsAsEmpty() {
        KnightMemory memory = memory();
        assertTrue(memory.recallAll().isEmpty());
        assertEquals(Optional.empty(), memory.recall("realm"));
    }

    @Test
    void rememberPersistsAcrossInstances() {
        KnightMemory writer = memory();
        writer.remember("realm", "/tmp/some/realm");
        writer.remember("sight", "night");

        // A fresh instance over the same file must recall both.
        KnightMemory reader = memory();
        assertEquals("/tmp/some/realm", reader.recall("realm").orElse(null));
        assertEquals("night", reader.recall("sight").orElse(null));
    }

    @Test
    void rememberPreservesOtherKeys() {
        KnightMemory memory = memory();
        memory.remember("a", "1");
        memory.remember("b", "2");
        memory.remember("a", "3");
        Map<String, String> all = memory.recallAll();
        assertEquals("3", all.get("a"));
        assertEquals("2", all.get("b"));
        assertEquals(2, all.size());
    }

    @Test
    void forgetRemovesOnlyThatKey() {
        KnightMemory memory = memory();
        memory.remember("a", "1");
        memory.remember("b", "2");
        memory.forget("a");
        assertFalse(memory.recallAll().containsKey("a"));
        assertEquals("2", memory.recall("b").orElse(null));
        // Forgetting a missing key is a no-op, not an error.
        assertDoesNotThrow(() -> memory.forget("ghost"));
    }

    @Test
    void newlinesAndBackslashesSurviveRoundTrip() {
        KnightMemory memory = memory();
        memory.remember("tale", "line one\nline two\\end");
        assertEquals("line one\nline two\\end",
                memory().recall("tale").orElse(null));
    }

    @Test
    void featNoteCanBeRememberedRecalledAndCleared() {
        KnightMemory memory = memory();
        String hash = "abc123456789";
        assertEquals(Optional.empty(), memory.recallNote(hash));

        memory.rememberNote(hash, "Important release feat!");
        assertEquals("Important release feat!", memory.recallNote(hash).orElse(null));

        // Clearing note forgets it.
        memory.rememberNote(hash, "");
        assertEquals(Optional.empty(), memory.recallNote(hash));
    }
}
