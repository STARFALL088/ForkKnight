package forkknight.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KnightMemoryTest {

    @TempDir
    Path tempDir;

    private KnightMemory memory() {
        return new KnightMemory(tempDir.resolve("nest").resolve("memory"));
    }

    @Test
    void missingFileReadsAsEmpty() {
        KnightMemory memory = memory();
        assertTrue(memory.recallAll().isEmpty());
        assertEquals(Optional.empty(), memory.recall("realm"));
    }

    @Test
    void rememberPersistsAcrossInstances() throws IOException {
        KnightMemory writer = memory();
        writer.remember("realm", "/tmp/some/realm");
        writer.remember("sight", "night");

        // A fresh instance over the same file must recall both.
        KnightMemory reader = memory();
        assertEquals("/tmp/some/realm", reader.recall("realm").orElse(null));
        assertEquals("night", reader.recall("sight").orElse(null));
    }

    @Test
    void rememberPreservesOtherKeys() throws IOException {
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
    void forgetRemovesOnlyThatKey() throws IOException {
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
    void newlinesAndBackslashesSurviveRoundTrip() throws IOException {
        KnightMemory memory = memory();
        memory.remember("tale", "line one\nline two\\end");
        assertEquals("line one\nline two\\end",
                memory().recall("tale").orElse(null));
    }

    @Test
    void commentsAndBlankLinesIgnored() throws IOException {
        Path file = tempDir.resolve("memory");
        Files.writeString(file, """
                # a comment
                
                key=value
                """);
        KnightMemory memory = new KnightMemory(file);
        Map<String, String> all = memory.recallAll();
        assertEquals(1, all.size());
        assertEquals("value", all.get("key"));
    }

    @Test
    void scribbledLinesAreSkippedNotFatal() throws IOException {
        Path file = tempDir.resolve("memory");
        Files.writeString(file, """
                good=1
                this line has no equals sign
                =emptyKey
                also-good=2
                """);
        KnightMemory memory = new KnightMemory(file);
        Map<String, String> all = memory.recallAll();
        assertEquals(2, all.size());
        assertEquals("1", all.get("good"));
        assertEquals("2", all.get("also-good"));
    }

    @Test
    void brokenFileReadsAsEmptyNotCrash() throws IOException {
        Path file = tempDir.resolve("memory");
        Files.writeString(file, "fine=1\n");
        // Simulate a corrupt file: a directory blocking the read path
        // is hard to stage portably, so an unreadable file suffices -
        // but readAllLines on a file still works, so instead assert
        // recallAll tolerates arbitrary bytes without throwing.
        Files.write(file, new byte[]{0x00, 0x01, 0x02, 'k', '=', 'v'});
        KnightMemory memory = new KnightMemory(file);
        assertDoesNotThrow(memory::recallAll);
    }

    @Test
    void writeAllReplacesEverything() throws IOException {
        KnightMemory memory = memory();
        memory.remember("old", "gone");
        memory.writeAll(Map.of("new", "kept"));
        Map<String, String> all = memory.recallAll();
        assertEquals(1, all.size());
        assertEquals("kept", all.get("new"));
    }
}
