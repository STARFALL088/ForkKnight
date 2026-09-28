package forkknight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfileDialogTest {

    @Test
    void theAvatarWearsTheFirstLettersOfTheName() {
        assertEquals("L", ProfileDialog.initialsOf("luna"));
        assertEquals("LB", ProfileDialog.initialsOf("Luna the Bold"));
        assertEquals("LR", ProfileDialog.initialsOf("  luna rose  "));
        assertEquals("?", ProfileDialog.initialsOf(null));
        assertEquals("?", ProfileDialog.initialsOf("   "));
    }

    @Test
    void theJoinedDayIsTheFirstTenLettersOfTheLedgersStamp() {
        assertEquals("2026-09-16", ProfileDialog.joinedOn("2026-09-16T04:51:10Z"));
        assertEquals("an unknown day", ProfileDialog.joinedOn(null));
        assertEquals("an unknown day", ProfileDialog.joinedOn("short"));
    }
}
