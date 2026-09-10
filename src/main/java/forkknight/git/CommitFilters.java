package forkknight.git;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Builds predicates that filter commits by a case-insensitive substring
 * search over different scopes.
 */
public final class CommitFilters {

    public static final String SCOPE_MESSAGE = "Message";
    public static final String SCOPE_AUTHOR = "Author";
    public static final String SCOPE_HASH = "Hash";

    private CommitFilters() {
    }

    /** Matches everything; used when the query is blank. */
    public static Predicate<Commit> all() {
        return c -> true;
    }

    /**
     * Case-insensitive substring filter over the given scope.
     *
     * @param scope one of {@link #SCOPE_MESSAGE}, {@link #SCOPE_AUTHOR},
     *              {@link #SCOPE_HASH}; anything else falls back to message
     * @param query substring to look for; blank matches everything
     */
    public static Predicate<Commit> byScope(String scope, String query) {
        if (query == null || query.isBlank()) {
            return all();
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return switch (scope == null ? SCOPE_MESSAGE : scope) {
            case SCOPE_AUTHOR -> c -> c.author() != null
                    && c.author().toLowerCase(Locale.ROOT).contains(needle);
            case SCOPE_HASH -> c -> c.hash() != null
                    && c.hash().toLowerCase(Locale.ROOT).contains(needle);
            default -> c -> (c.message() != null
                    && c.message().toLowerCase(Locale.ROOT).contains(needle))
                    || (c.body() != null
                    && c.body().toLowerCase(Locale.ROOT).contains(needle));
        };
    }
}
