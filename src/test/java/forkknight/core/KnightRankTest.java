package forkknight.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnightRankTest {

    @Test
    void theRanksAnswerToTheStoredWord() {
        assertEquals(KnightRank.Warden, KnightRank.of("warden"));
        assertEquals(KnightRank.Paladin, KnightRank.of("PALADIN"));
        assertEquals(KnightRank.Squire, KnightRank.of("Squire"));
        assertEquals(KnightRank.Squire, KnightRank.of("something else"));
        assertEquals(KnightRank.Squire, KnightRank.of(null));
    }
}
