package forkknight;

import forkknight.core.Account;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the "who rides now" readouts: wanderer, locked ledger, knight. */
class KnightsDialogTest {

    private static Account account(String username, String displayName, String hash, boolean guest) {
        return new Account(7, username, displayName, hash, guest, "2026-09-25T00:00:00Z");
    }

    @Test
    void theWandererIsNamedAsSuch() {
        String readout = KnightsDialog.describe(account("guest", "The Wanderer", "", true));
        assertTrue(readout.contains("the Wanderer"), readout);
        assertTrue(readout.contains("no password"), readout);
    }

    @Test
    void aLockedLedgerIsCalledOut() {
        String readout = KnightsDialog.describe(account("keeper", "Keeper", "locked$deadbeef", false));
        assertTrue(readout.contains("locked ledger"), readout);
        assertTrue(readout.contains("claim"), readout);
    }

    @Test
    void anOrdinaryKnightIsJustNamed() {
        assertEquals("Who rides now: luna",
            KnightsDialog.describe(account("luna", "luna", "pbkdf2-sha256$1$ab$cd", false)));
    }

    @Test
    void aKnownAsShowsOnlyWhenItAddsSomething() {
        String readout = KnightsDialog.describe(account("luna", "Luna Star", "pbkdf2-sha256$1$ab$cd", false));
        assertTrue(readout.contains("(Luna Star)"), readout);
    }
}
