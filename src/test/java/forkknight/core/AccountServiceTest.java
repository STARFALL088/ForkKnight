package forkknight.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {

    /** A cheap cost so the suite stays quick; the production path is 600k. */
    private static final int FAST = 10_000;

    @TempDir
    Path tempDir;

    private KnightDatabase db;
    private KnightMemory memory;
    private AccountService service;
    private long keeperId;

    @BeforeEach
    void setUp() {
        db = new KnightDatabase("jdbc:sqlite:" + tempDir.resolve("accounts.db").toAbsolutePath());
        memory = new KnightMemory(db);
        service = new AccountService(memory, FAST);
        keeperId = db.findUserByUsername("keeper").orElseThrow().id();
    }

    private Account signUp(String name) {
        return service.signUp(name, name, "correct horse", "correct horse");
    }

    // -------------------- Sign up --------------------

    @Test
    void signUpRegistersAndSignsTheKnightIn() {
        Account alice = signUp("alice");
        assertFalse(alice.guest());
        assertFalse(AccountService.locked(alice));
        assertEquals(alice.id(), service.current().id());
        assertEquals(alice.id(), memory.currentUserId());
        assertTrue(service.accounts().stream().anyMatch(a -> a.username().equals("alice")));
    }

    @Test
    void signUpRejectsNamesThatCannotRide() {
        assertThrows(AuthException.class, () -> service.signUp("ab", "A", "correct horse", "correct horse"));
        assertThrows(AuthException.class, () -> service.signUp("two words", "A", "correct horse", "correct horse"));
        assertThrows(AuthException.class, () -> service.signUp(".hidden", "A", "correct horse", "correct horse"));
        assertThrows(AuthException.class, () -> service.signUp(null, "A", "correct horse", "correct horse"));
    }

    @Test
    void signUpRefusesTheWanderersName() {
        AuthException refused = assertThrows(AuthException.class,
            () -> service.signUp("Guest", "Sneaky", "correct horse", "correct horse"));
        assertTrue(refused.getMessage().contains("wanderer"));
    }

    @Test
    void signUpRefusesWeakOrMismatchedPasswords() {
        assertThrows(AuthException.class, () -> service.signUp("alice", "A", "short", "short"));
        assertThrows(AuthException.class, () -> service.signUp("alice", "A", "correct horse", "correct horser"));
        assertThrows(AuthException.class, () -> service.signUp("alice", "A", null, null));
        // Nothing was registered by any of those.
        assertFalse(db.usernameExists("alice"));
    }

    @Test
    void signUpRefusesANameThatIsAlreadyRidden() {
        signUp("alice");
        AuthException taken = assertThrows(AuthException.class,
            () -> service.signUp("ALICE", "Impostor", "another password", "another password"));
        assertTrue(taken.getMessage().contains("already rides"));
        assertEquals(1, service.accounts().stream().filter(a -> a.username().equalsIgnoreCase("alice")).count());
    }

    // -------------------- Sign in --------------------

    @Test
    void logInOnlyOpensTheRightPassword() {
        signUp("alice");
        service.logOut();
        assertEquals("guest", service.current().username());

        Account alice = service.logIn("alice", "correct horse");
        assertEquals("alice", alice.username());
        assertEquals(alice.id(), service.current().id());

        service.logOut();
        AuthException wrong = assertThrows(AuthException.class,
            () -> service.logIn("alice", "correct horser"));
        assertTrue(wrong.getMessage().contains("does not open"));
        assertEquals("guest", service.current().username());
    }

    @Test
    void logInAnswersForUnknownNames() {
        AuthException unknown = assertThrows(AuthException.class,
            () -> service.logIn("nobody", "correct horse"));
        assertTrue(unknown.getMessage().contains("No knight"));
        assertThrows(AuthException.class, () -> service.logIn("   ", "correct horse"));
        assertThrows(AuthException.class, () -> service.logIn(null, "correct horse"));
    }

    @Test
    void logInIgnoresCaseAndTrailingSpace() {
        signUp("alice");
        service.logOut();
        assertEquals("alice", service.logIn("  ALICE ", "correct horse").username());
    }

    @Test
    void theWandererKeepsNoPassword() {
        service.logOut();
        AuthException refused = assertThrows(AuthException.class,
            () -> service.logIn("guest", "anything at all"));
        assertTrue(refused.getMessage().contains("wanderer"));
        assertThrows(AuthException.class, () -> service.changePassword(
            service.current().id(), null, "correct horse", "correct horse"));
    }

    // -------------------- The locked keeper --------------------

    @Test
    void theLockedKeeperCannotBeSignedIntoAndCanBeClaimed() {
        AuthException lockedOut = assertThrows(AuthException.class,
            () -> service.logIn("keeper", "anything at all"));
        assertTrue(lockedOut.getMessage().contains("locked"));

        // Claiming needs no current password while the lock stands...
        service.changePassword(keeperId, null, "correct horse", "correct horse");
        // ...and from then on it is an ordinary account.
        Account keeper = service.logIn("keeper", "correct horse");
        assertFalse(AccountService.locked(keeper));
        assertThrows(AuthException.class, () -> service.logIn("keeper", "wrong password"));
    }

    @Test
    void changingAPasswordNeedsTheCurrentOne() {
        Account alice = signUp("alice");
        assertThrows(AuthException.class,
            () -> service.changePassword(alice.id(), "wrong password", "a brand new secret", "a brand new secret"));
        assertThrows(AuthException.class,
            () -> service.changePassword(alice.id(), null, "a brand new secret", "a brand new secret"));

        service.changePassword(alice.id(), "correct horse", "a brand new secret", "a brand new secret");
        service.logOut();
        assertEquals("alice", service.logIn("alice", "a brand new secret").username());
        assertThrows(AuthException.class, () -> service.logIn("alice", "correct horse"));
    }

    // -------------------- Sign out, switch, dismiss --------------------

    @Test
    void signOutDropsToTheWanderer() {
        signUp("alice");
        Account guest = db.guestAccount();
        service.logOut();

        assertEquals("guest", service.current().username());
        assertTrue(service.current().guest());
        assertEquals(guest.id(), service.current().id());
    }

    @Test
    void switchingKnightsSwitchesTheirLedger() {
        signUp("alice");
        memory.remember("sight", "day");
        memory.rememberNote("deadbeef", "alice's note");

        service.logOut();
        signUp("bob");
        // Bob starts with a clean slate - no shared settings, no shared notes.
        assertTrue(memory.recallAll().isEmpty());
        assertEquals(Optional.empty(), memory.recallNote("deadbeef"));

        memory.remember("sight", "night");
        service.logOut();
        service.logIn("alice", "correct horse");
        assertEquals("day", memory.recall("sight").orElse(null));
        assertEquals("alice's note", memory.recallNote("deadbeef").orElse(null));
    }

    @Test
    void dismissingTheSignedInKnightSignsOutAndErasesHim() {
        Account alice = signUp("alice");
        memory.remember("sight", "day");
        memory.rememberNote("deadbeef", "alice's note");

        service.deleteAccount(alice.id());

        assertEquals("guest", service.current().username());
        assertFalse(db.usernameExists("alice"));
        // Alice's rows fell with her; the keeper's survived.
        assertEquals(Optional.empty(), db.getSetting(alice.id(), "sight"));
        assertEquals(Optional.empty(), db.getNote(alice.id(), "deadbeef"));
        assertEquals("keeper", db.findUserById(keeperId).orElseThrow().username());
    }

    @Test
    void theWandererCannotBeDismissed() {
        service.logOut();
        AuthException refused = assertThrows(AuthException.class,
            () -> service.deleteAccount(service.current().id()));
        assertTrue(refused.getMessage().contains("wanderer"));
        assertFalse(service.accounts().isEmpty());
    }

    // -------------------- The knight's own page --------------------

    @Test
    void updateProfileSealsAndProfileOfSpeaksTheTruth() {
        Account alice = signUp("alice");
        db.setNote(alice.id(), "aaaa1111", "one");
        db.setBookmark(alice.id(), "/alice/realm", "Alice's Realm");

        service.updateProfile(alice.id(), "Alice the Bold", "I ride at dawn.",
            KnightRank.Paladin);

        KnightProfile page = service.profileOf(alice.id());
        assertEquals("Alice the Bold", page.account().displayName());
        assertEquals("I ride at dawn.", page.account().bio());
        assertEquals("Paladin", page.account().title());
        assertEquals(1, page.noteCount());
        assertEquals(1, page.bookmarkCount());
        // The seat itself carries the new name.
        assertEquals("Alice the Bold", service.current().displayName());
    }

    @Test
    void updateProfileRefusesWhatCannotBeWritten() {
        Account alice = signUp("alice");

        assertThrows(AuthException.class,
            () -> service.updateProfile(alice.id(), "  ", "bio", KnightRank.Squire));
        assertThrows(AuthException.class,
            () -> service.updateProfile(alice.id(), "x".repeat(41), "bio", KnightRank.Squire));
        assertThrows(AuthException.class,
            () -> service.updateProfile(alice.id(), "Name", "x".repeat(201), KnightRank.Squire));
        assertThrows(AuthException.class,
            () -> service.updateProfile(alice.id(), "Name", "bio", null));
        assertThrows(AuthException.class,
            () -> service.updateProfile(alice.id() + 999, "Name", "bio", KnightRank.Squire));

        // Nothing was written.
        assertEquals("", db.findUserById(alice.id()).orElseThrow().bio());

        // The wanderer has no page to write in.
        service.logOut();
        Account guest = service.current();
        AuthException refused = assertThrows(AuthException.class,
            () -> service.updateProfile(guest.id(), "Sneaky", "bio", KnightRank.Warden));
        assertTrue(refused.getMessage().contains("wanderer"));
    }

    @Test
    void profileOfAnUnknownKnightRefuses() {
        assertThrows(AuthException.class, () -> service.profileOf(9999));
    }

    // -------------------- The drawbridge --------------------

    @Test
    void signInRequiredStartsLoweredAndStaysAsSet() {
        assertFalse(service.signInRequired());

        service.setSignInRequired(true);
        assertTrue(service.signInRequired());

        // Persisted in app_state: a fresh service over the same ledger agrees.
        AccountService fresh = new AccountService(new KnightMemory(db), FAST);
        assertTrue(fresh.signInRequired());

        service.setSignInRequired(false);
        assertFalse(fresh.signInRequired());
    }

    // -------------------- Cost --------------------

    @Test
    void aRisenCostRehashesOnTheNextSignIn() {
        signUp("alice");
        String cheap = db.findUserByUsername("alice").orElseThrow().passwordHash();
        assertEquals(String.valueOf(FAST), cheap.split("\\$")[1]);

        // A later release, hashing harder: the same password is enough.
        AccountService costlier = new AccountService(memory, FAST * 2);
        costlier.logIn("alice", "correct horse");

        String rehashed = db.findUserByUsername("alice").orElseThrow().passwordHash();
        assertEquals(String.valueOf(FAST * 2), rehashed.split("\\$")[1]);
        assertTrue(PasswordHasher.matches("correct horse".toCharArray(), rehashed));
    }
}
