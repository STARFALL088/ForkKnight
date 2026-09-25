package forkknight.core;

/**
 * A knight registered in the local ledger. Accounts are local profiles:
 * they scope settings, notes and realm bookmarks, and they unlock only
 * through a password (except guests, which never carry one).
 */
public record Account(long id,
                      String username,
                      String displayName,
                      String passwordHash,
                      boolean guest,
                      String createdAt) {
}
