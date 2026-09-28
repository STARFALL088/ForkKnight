package forkknight.core;

/**
 * A knight registered in the local ledger. Accounts are local profiles:
 * they scope settings, notes and realm bookmarks, and they unlock only
 * through a password (except guests, which never carry one).
 *
 * <p>The knight's own page lives here too: a display name other knights
 * know him by, a bio in his own words, and a title from the fixed ranks
 * ({@link KnightRank}).
 */
public record Account(long id,
                      String username,
                      String displayName,
                      String bio,
                      String title,
                      String passwordHash,
                      boolean guest,
                      String createdAt) {
}
