package forkknight.git;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommitFiltersTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 1);

    private static Commit commit(String hash, String author, String message, String body) {
        return new Commit(hash, author, "e@example.com", DATE, message, body);
    }

    @Test
    void blankQueryMatchesEverything() {
        List<Commit> commits = List.of(
                commit("aaaaaaa", "Alice", "fix bug", ""),
                commit("bbbbbbb", "Bob", "add feature", ""));
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_MESSAGE, "").test(commits.get(0)));
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_MESSAGE, "   ").test(commits.get(1)));
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_MESSAGE, null).test(commits.get(0)));
    }

    @Test
    void messageScopeIsCaseInsensitive() {
        Commit c = commit("aaaaaaa", "Alice", "Fix Login Bug", "");
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_MESSAGE, "login").test(c));
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_MESSAGE, "FIX").test(c));
        assertFalse(CommitFilters.byScope(CommitFilters.SCOPE_MESSAGE, "zephyr").test(c));
    }

    @Test
    void messageScopeAlsoSearchesBody() {
        Commit c = commit("aaaaaaa", "Alice", "summary", "details about the LOGIN page");
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_MESSAGE, "login").test(c));
    }

    @Test
    void authorScopeMatchesAuthorName() {
        Commit alice = commit("aaaaaaa", "Alice Smith", "work", "");
        Commit bob = commit("bbbbbbb", "Bob Jones", "work", "");
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_AUTHOR, "alice").test(alice));
        assertFalse(CommitFilters.byScope(CommitFilters.SCOPE_AUTHOR, "alice").test(bob));
    }

    @Test
    void hashScopeMatchesFullOrShortHash() {
        Commit c = commit("0123456789abcdef", "Alice", "work", "");
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_HASH, "0123456").test(c));
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_HASH, "0123456789abcdef").test(c));
        assertFalse(CommitFilters.byScope(CommitFilters.SCOPE_HASH, "ffffff").test(c));
    }

    @Test
    void unknownScopeFallsBackToMessage() {
        Commit c = commit("aaaaaaa", "Alice", "important fix", "");
        assertTrue(CommitFilters.byScope(null, "important").test(c));
        assertTrue(CommitFilters.byScope("Bogus", "important").test(c));
        assertFalse(CommitFilters.byScope(null, "alice").test(c));
    }

    @Test
    void nullFieldsNeverMatch() {
        Commit c = commit("aaaaaaa", null, null, null);
        assertFalse(CommitFilters.byScope(CommitFilters.SCOPE_AUTHOR, "a").test(c));
        assertFalse(CommitFilters.byScope(CommitFilters.SCOPE_MESSAGE, "a").test(c));
        assertTrue(CommitFilters.byScope(CommitFilters.SCOPE_HASH, "a").test(c));
    }
}
