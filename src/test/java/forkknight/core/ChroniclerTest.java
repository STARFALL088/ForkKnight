package forkknight.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChroniclerTest {

    private static final LocalDate D1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 1, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 3, 15);

    private static Feat feat(String hash, String hero, LocalDate day, String... parents) {
        return new Feat(hash, List.of(parents), hero, "e@x", day, "s " + hash, "");
    }

    @Test
    void featCountAndSpan() {
        Chronicler chronicler = Chronicler.of(List.of(
                feat("a", "Alice", D3),
                feat("b", "Bob", D1)));
        assertEquals(2, chronicler.featCount());
        // 2026-01-01 -> 2026-03-15 = 73 days
        assertEquals(73, chronicler.campaignSpanDays());
    }

    @Test
    void emptyTrailIsAllZeros() {
        Chronicler chronicler = Chronicler.of(List.of());
        assertEquals(0, chronicler.featCount());
        assertEquals(0, chronicler.fusionCount());
        assertEquals(0, chronicler.campaignSpanDays());
        assertTrue(chronicler.heroStandings(5).isEmpty());
        assertTrue(chronicler.busiestDays(5).isEmpty());
    }

    @Test
    void heroStandingsRankByFeatCount() {
        Chronicler chronicler = Chronicler.of(List.of(
                feat("a", "Alice", D1),
                feat("b", "Bob", D1),
                feat("c", "Alice", D2),
                feat("d", "Alice", D2)));
        List<Chronicler.HeroStanding> standings = chronicler.heroStandings(0);
        assertEquals(2, standings.size());
        assertEquals("Alice", standings.get(0).hero());
        assertEquals(3, standings.get(0).feats());
        assertEquals(1, standings.get(1).feats());
    }

    @Test
    void heroStandingsTopKUsesBoundedHeap() {
        // 6 heroes, k=3: only the top three survive.
        Chronicler chronicler = Chronicler.of(List.of(
                feat("a", "H1", D1), feat("b", "H2", D1), feat("c", "H3", D1),
                feat("d", "H4", D1), feat("e", "H5", D1), feat("f", "H6", D1),
                feat("g", "H2", D2), feat("h", "H4", D2), feat("i", "H6", D2),
                feat("j", "H6", D2)));
        List<Chronicler.HeroStanding> top = chronicler.heroStandings(3);
        assertEquals(3, top.size());
        assertEquals("H6", top.get(0).hero());
        assertEquals(3, top.get(0).feats());
        // The remaining two each have 2 feats: H2 and H4.
        assertTrue(top.stream().map(Chronicler.HeroStanding::hero)
                .allMatch(h -> h.equals("H6") || h.equals("H2") || h.equals("H4")));
    }

    @Test
    void busiestDaysSortByCountThenRecency() {
        Chronicler chronicler = Chronicler.of(List.of(
                feat("a", "Alice", D1),
                feat("b", "Alice", D1),
                feat("c", "Alice", D2),
                feat("d", "Alice", D3),
                feat("e", "Alice", D3)));
        List<Chronicler.DayAction> days = chronicler.busiestDays(0);
        // D1 and D3 both have 2 feats: the more recent day (D3) leads.
        assertEquals(D3, days.get(0).day());
        assertEquals(2, days.get(0).feats());
        assertEquals(D1, days.get(1).day());
        assertEquals(D2, days.get(2).day());
    }

    @Test
    void fusionCountDetectsFusions() {
        Chronicler chronicler = Chronicler.of(List.of(
                feat("m", "Alice", D2, "a", "b"),   // fusion
                feat("a", "Alice", D1),
                feat("b", "Bob", D1)));
        assertEquals(1, chronicler.fusionCount());
    }

    @Test
    void pathHeatCountsTouchesAcrossTolls() {
        Map<String, List<Dispatch>> tolls = Map.of(
                "f1", List.of(new Dispatch("a.txt", "a.txt", "M", false),
                              new Dispatch("b.txt", "b.txt", "A", false)),
                "f2", List.of(new Dispatch("a.txt", "a.txt", "M", false)),
                "f3", List.of(new Dispatch("a.txt", "a.txt", "M", false),
                              new Dispatch("c.txt", "c.txt", "D", false)));
        Chronicler chronicler = Chronicler.of(List.of(
                feat("f1", "Alice", D1), feat("f2", "Alice", D1), feat("f3", "Bob", D2)));
        List<Chronicler.PathHeat> heat = chronicler.pathHeat(tolls, 2);
        assertEquals(2, heat.size());
        assertEquals("a.txt", heat.get(0).path());
        assertEquals(3, heat.get(0).touches());
        // The runner-up is either tie at one touch.
        assertEquals(1, heat.get(1).touches());
    }

    @Test
    void laterInputMutationDoesNotPoisonChronicler() {
        List<Feat> shared = new java.util.ArrayList<>(List.of(feat("a", "Alice", D1)));
        Chronicler chronicler = Chronicler.of(shared);
        shared.clear();
        assertEquals(1, chronicler.featCount());
    }
}
