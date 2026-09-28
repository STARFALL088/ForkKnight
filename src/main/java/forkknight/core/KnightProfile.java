package forkknight.core;

/**
 * A knight's own page: the account itself plus the keepsakes he has
 * gathered - how many notes and realm bookmarks the ledger holds for him.
 */
public record KnightProfile(Account account, long noteCount, long bookmarkCount) {
}
