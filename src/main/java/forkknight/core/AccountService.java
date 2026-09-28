package forkknight.core;

import java.util.List;

/**
 * Signing in and out of local accounts: sign up, sign in, switch knights and
 * sign out, plus claiming and changing passwords. Every refusal raises an
 * {@link AuthException} whose message is meant for the knight to read.
 *
 * <p>Accounts are local profiles living in the ledger - there is no server
 * and no network. The signed-in knight is the one whose settings, notes and
 * bookmarks {@link KnightMemory} serves.
 */
public final class AccountService {

    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 1024;
    private static final String RESERVED = "guest";
    private static final java.util.regex.Pattern USERNAME =
        java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{2,23}$");

    private final KnightMemory memory;
    private final int iterations;

    public AccountService(KnightMemory memory) {
        this(memory, PasswordHasher.ITERATIONS);
    }

    /** Same service at a different cost; used by tests to stay quick. */
    AccountService(KnightMemory memory, int iterations) {
        this.memory = memory;
        this.iterations = iterations;
    }

    // -------------------- Who rides now --------------------

    /** The knight currently signed in, falling back to the wanderer. */
    public Account current() {
        return memory.currentAccount().orElseGet(() -> {
            memory.signOut();
            return memory.currentAccount()
                .orElseThrow(() -> new AuthException("No knight answers the ledger"));
        });
    }

    /** Every account in the ledger, by name. */
    public List<Account> accounts() {
        return memory.ledger().listUsers();
    }

    /** True when this account still holds the keeper's lock. */
    public static boolean locked(Account account) {
        return account.passwordHash().startsWith("locked$");
    }

    // -------------------- Sign up --------------------

    /** Registers a knight and signs him in. */
    public Account signUp(String username, String displayName, String password, String confirm) {
        requireUsername(username);
        requirePassword(password, confirm);
        if (RESERVED.equalsIgnoreCase(username)) {
            throw new AuthException("'" + RESERVED + "' is the wanderer's name - choose another");
        }
        if (memory.ledger().usernameExists(username)) {
            throw new AuthException("A knight named '" + username.trim() + "' already rides");
        }
        Account account = memory.ledger().createUser(username.trim(), displayName,
            PasswordHasher.hash(password.toCharArray(), iterations), false);
        memory.switchTo(account.id());
        return account;
    }

    // -------------------- Sign in / switch / sign out --------------------

    /** Signs a knight in, making him the one this memory serves. */
    public Account logIn(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new AuthException("Name the knight who signs in");
        }
        Account account = memory.ledger().findUserByUsername(username.trim())
            .orElseThrow(() -> new AuthException("No knight rides under that name"));
        if (account.guest()) {
            throw new AuthException("The wanderer keeps no password - sign out instead");
        }
        if (locked(account)) {
            throw new AuthException("'" + account.username() + "' still holds a locked ledger - "
                + "claim the account first");
        }
        if (password == null || !PasswordHasher.matches(password.toCharArray(), account.passwordHash())) {
            throw new AuthException("That password does not open this account");
        }
        if (PasswordHasher.needsRehash(account.passwordHash(), iterations)) {
            memory.ledger().setPassword(account.id(), PasswordHasher.hash(password.toCharArray(), iterations));
        }
        memory.switchTo(account.id());
        return account;
    }

    /** Drops back to the passwordless wanderer. */
    public void logOut() {
        memory.signOut();
    }

    // -------------------- Passwords --------------------

    /**
     * Sets a password. When the account still holds the keeper's lock the
     * current password may be omitted - that is how a locked ledger is
     * claimed. Otherwise the current password must open it.
     */
    public void changePassword(long userId, String current, String next, String confirm) {
        Account account = memory.ledger().findUserById(userId)
            .orElseThrow(() -> new AuthException("No such knight rides"));
        if (account.guest()) {
            throw new AuthException("The wanderer keeps no password");
        }
        requirePassword(next, confirm);
        if (!locked(account)) {
            if (current == null || !PasswordHasher.matches(current.toCharArray(), account.passwordHash())) {
                throw new AuthException("That password does not open this account");
            }
        }
        memory.ledger().setPassword(account.id(), PasswordHasher.hash(next.toCharArray(), iterations));
    }

    // -------------------- Dismissal --------------------

    /**
     * Dismisses a knight; settings, notes and bookmarks fall with them.
     * Signing out first when the dismissed knight was the current one.
     */
    public void deleteAccount(long userId) {
        Account account = memory.ledger().findUserById(userId)
            .orElseThrow(() -> new AuthException("No such knight rides"));
        if (account.guest()) {
            throw new AuthException("The wanderer cannot be dismissed");
        }
        if (memory.currentUserId() == userId) {
            memory.signOut();
        }
        memory.ledger().deleteUser(userId);
    }

    // -------------------- The knight's own page --------------------

    /**
     * Seals a knight's profile: display name, bio and title. The wanderer
     * has no ledger entry of his own, so he cannot write in one.
     */
    public void updateProfile(long userId, String displayName, String bio, KnightRank title) {
        Account account = memory.ledger().findUserById(userId)
            .orElseThrow(() -> new AuthException("No such knight rides"));
        if (account.guest()) {
            throw new AuthException(
                "The wanderer rides without a profile - join the Order to claim one");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new AuthException("A profile needs a display name of 1-40 characters");
        }
        String name = displayName.strip();
        if (name.length() > 40) {
            throw new AuthException("A profile needs a display name of 1-40 characters");
        }
        String words = bio == null ? "" : bio;
        if (words.length() > 200) {
            throw new AuthException("A bio runs at most 200 characters");
        }
        if (title == null) {
            throw new AuthException("Choose a title from the ranks");
        }
        memory.ledger().updateProfile(userId, name, words, title.name());
    }

    /** The knight's own page: his account plus what he has gathered. */
    public KnightProfile profileOf(long userId) {
        Account account = memory.ledger().findUserById(userId)
            .orElseThrow(() -> new AuthException("No such knight rides"));
        KnightDatabase.ProfileStats stats = memory.ledger().profileStats(userId);
        return new KnightProfile(account, stats.notes(), stats.bookmarks());
    }

    // -------------------- The drawbridge --------------------

    /** The ledger key the launch gate answers to. */
    private static final String GATE_KEY = "gate.requireSignIn";

    /** Whether the realm demands a sign-in before it opens. */
    public boolean signInRequired() {
        return memory.ledger().getGlobal(GATE_KEY)
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    /** Raises or lowers the launch gate. */
    public void setSignInRequired(boolean required) {
        memory.ledger().setGlobal(GATE_KEY, Boolean.toString(required));
    }

    // -------------------- Validation --------------------

    private static void requireUsername(String username) {
        if (username == null || !USERNAME.matcher(username.trim()).matches()) {
            throw new AuthException("A knight's name runs 3-24 characters: letters, digits, dot, dash or underscore");
        }
    }

    private static void requirePassword(String password, String confirm) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new AuthException("A password needs at least " + MIN_PASSWORD + " characters");
        }
        if (password.length() > MAX_PASSWORD) {
            throw new AuthException("That password is longer than " + MAX_PASSWORD + " characters");
        }
        if (!password.equals(confirm)) {
            throw new AuthException("The two passwords do not match");
        }
    }
}
