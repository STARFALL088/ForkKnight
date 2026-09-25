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

    // -------------------- Who is signed in --------------------

    @Test
    void theSignedInKnightSurvivesALaunch() {
        KnightMemory first = memory();
        AccountService accounts = new AccountService(first, 10_000);
        Account alice = accounts.signUp("alice", "Alice", "correct horse", "correct horse");
        first.remember("sight", "day");
        first.close();

        // A fresh launch over the same ledger: same knight, same memory.
        KnightMemory second = memory();
        assertEquals(alice.id(), second.currentUserId());
        assertEquals("alice", second.currentAccount().orElseThrow().username());
        assertEquals("day", second.recall("sight").orElse(null));
    }

    @Test
    void signingOutSurvivesALaunch() {
        KnightMemory first = memory();
        new AccountService(first, 10_000).signUp("alice", "Alice", "correct horse", "correct horse");
        first.signOut();
        first.close();

        assertTrue(memory().currentAccount().orElseThrow().guest());
    }

    @Test
    void aStaleOrUnreadableMarkDoesNotHauntTheLaunch() {
        KnightMemory first = memory();
        long keeper = first.currentUserId();

        // A knight who no longer rides (deleted elsewhere)...
        first.ledger().setGlobal("account.current", "999999");
        assertEquals(keeper, memory().currentUserId());

        // ...and a mark nobody could ever read both fall back to the keeper.
        first.ledger().setGlobal("account.current", "not-a-number");
        assertEquals(keeper, memory().currentUserId());
    }

    @Test
    void switchingKnightsRemembersWhoRodeOff() {
        KnightMemory first = memory();
        AccountService accounts = new AccountService(first, 10_000);
        Account alice = accounts.signUp("alice", "Alice", "correct horse", "correct horse");
        Account bob = accounts.signUp("bob", "Bob", "another secret", "another secret");
        assertNotEquals(alice.id(), bob.id());
        first.close();

        KnightMemory second = memory();
        assertEquals(bob.id(), second.currentUserId());
        second.switchTo(alice.id());
        second.close();
        assertEquals(alice.id(), memory().currentUserId());
    }
}
