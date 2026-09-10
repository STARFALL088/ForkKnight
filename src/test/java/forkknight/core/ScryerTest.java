package forkknight.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ScryerTest {

    private static final LocalDate D = LocalDate.of(2026, 1, 1);

    private static Feat feat(String hash, String author, String summary, String body) {
        return new Feat(hash, List.of(), author, "e@x", D, summary, body);
    }

    private final List<Feat> feats = List.of(
            feat("aaaa1111", "Alice", "Initialize the forge", "boot the realm"),
            feat("bbbb2222", "Bob", "fix login page", ""),
            feat("cccc3333", "Alice", "add login tests", "covers login and logout"));

    @Test
    void blankQueryReturnsEverything() {
        Scryer scryer = new Scryer(feats);
        assertEquals(3, scryer.scry("", Scryer.Scope.SUMMARY).size());
        assertEquals(3, scryer.scry("   ", Scryer.Scope.SUMMARY).size());
    }

    @Test
    void prefixMatchesBeyondTokenEnd() {
        Scryer scryer = new Scryer(feats);
        assertEquals(1, scryer.scry("init", Scryer.Scope.SUMMARY).size());
        assertEquals(1, scryer.scry("initialize", Scryer.Scope.SUMMARY).size());
        assertEquals(0, scryer.scry("zzzz", Scryer.Scope.SUMMARY).size());
    }

    @Test
    void summaryScopeSearchesBodyToo() {
        Scryer scryer = new Scryer(feats);
        List<Feat> hits = scryer.scry("logout", Scryer.Scope.SUMMARY);
        assertEquals(1, hits.size());
        assertEquals("cccc3333", hits.get(0).hash());
    }

    @Test
    void multiWordQueryIntersectsWords() {
        Scryer scryer = new Scryer(feats);
        // "fix" only in feat 2; "login" in 2 and 3 -> only feat 2 survives.
        assertEquals(1, scryer.scry("fix login", Scryer.Scope.SUMMARY).size());
        // "login tests" -> only feat 3.
        List<Feat> hits = scryer.scry("login tests", Scryer.Scope.SUMMARY);
        assertEquals(1, hits.size());
        assertEquals("cccc3333", hits.get(0).hash());
        // A word absent from every feat kills the whole query.
        assertEquals(0, scryer.scry("forge dragon", Scryer.Scope.SUMMARY).size());
    }

    @Test
    void authorScopeMatches() {
        Scryer scryer = new Scryer(feats);
        assertEquals(2, scryer.scry("alice", Scryer.Scope.AUTHOR).size());
        assertEquals(1, scryer.scry("bob", Scryer.Scope.AUTHOR).size());
        assertEquals(0, scryer.scry("zoro", Scryer.Scope.AUTHOR).size());
    }

    @Test
    void hashScopeFullAndPrefix() {
        Scryer scryer = new Scryer(feats);
        assertEquals(1, scryer.scry("aaaa1111", Scryer.Scope.HASH).size());
        assertEquals(1, scryer.scry("bb", Scryer.Scope.HASH).size());
        // Only cccc3333 starts with 'c'.
        assertEquals(1, scryer.scry("c", Scryer.Scope.HASH).size());
    }

    @Test
    void nullFieldsNeverThrow() {
        Scryer scryer = new Scryer(List.of(new Feat("h1", null, null, null, D, null, null)));
        assertEquals(0, scryer.scry("anything", Scryer.Scope.AUTHOR).size());
        assertEquals(0, scryer.scry("anything", Scryer.Scope.SUMMARY).size());
        assertEquals(1, scryer.scry("h1", Scryer.Scope.HASH).size());
    }

    @Test
    void predicateWiringMatchesScry() {
        Scryer scryer = new Scryer(feats);
        var predicate = scryer.predicateFor("login", Scryer.Scope.SUMMARY);
        assertEquals(scryer.scry("login", Scryer.Scope.SUMMARY).size(),
                feats.stream().filter(predicate).count());
    }

    @Test
    void indexOfResolvesHashes() {
        Scryer scryer = new Scryer(feats);
        assertEquals(0, scryer.indexOf("aaaa1111"));
        assertEquals(2, scryer.indexOf("cccc3333"));
        assertEquals(-1, scryer.indexOf("nope"));
    }

    @Test
    void laterListMutationDoesNotPoisonIndex() {
        List<Feat> shared = new java.util.ArrayList<>(feats);
        Scryer scryer = new Scryer(shared);
        shared.clear();
        assertEquals(3, scryer.feats().size());
        assertEquals(2, scryer.scry("login", Scryer.Scope.SUMMARY).size());
    }
}
