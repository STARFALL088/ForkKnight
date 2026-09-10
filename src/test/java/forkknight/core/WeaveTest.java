package forkknight.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WeaveTest {

    private static final LocalDate D = LocalDate.of(2026, 1, 1);

    private static Feat feat(String hash, String... parents) {
        return new Feat(hash, List.of(parents), "A", "a", D, hash, "");
    }

    @Test
    void linearTrailUsesOneLane() {
        Weave weave = Weave.of(List.of(feat("c3", "c2"), feat("c2", "c1"), feat("c1")));
        assertEquals(1, weave.laneCount());
        assertEquals(0, weave.rows().get(0).lane());
    }

    @Test
    void fusionCreatesForkWithTwoLanes() {
        // main: c1-c2-c3, side: s1(from c2)-s2, fusion m(c3, s2)
        Weave weave = Weave.of(List.of(
                feat("m", "c3", "s2"),
                feat("c3", "c2"),
                feat("s2", "s1"),
                feat("s1", "c2"),
                feat("c2", "c1"),
                feat("c1")));
        assertEquals(2, weave.laneCount());
        Weave.Woven fusion = weave.rows().get(0);
        assertTrue(fusion.fork());
        assertEquals(2, fusion.parentLanes().size());
        // side lineage lives on its own lane
        assertEquals(1, weave.rows().stream()
                .filter(r -> r.feat().hash().equals("s2"))
                .findFirst().orElseThrow().lane());
    }

    @Test
    void threeWayFusionUsesThreeLanes() {
        Weave weave = Weave.of(List.of(
                feat("m", "a", "b", "c"),
                feat("a", "root"),
                feat("b", "root"),
                feat("c", "root"),
                feat("root")));
        assertTrue(weave.rows().get(0).fork());
        assertTrue(weave.laneCount() >= 3);
    }

    @Test
    void lanesAreRecycledWhenLineagesEnd() {
        // Three sequential lineages, never more than one alive at a time:
        // recycling must keep the weave to a single lane.
        Weave weave = Weave.of(List.of(
                feat("t1", "p1"), feat("p1"),
                feat("t2", "p2"), feat("p2"),
                feat("t3", "p3"), feat("p3")));
        assertEquals(1, weave.laneCount());
        for (Weave.Woven row : weave.rows()) {
            assertEquals(0, row.lane());
        }
    }

    @Test
    void offscreenParentsAreIgnored() {
        // Parent not in the visible list: weave must not crash and must
        // still assign a lane.
        Weave weave = Weave.of(List.of(feat("c2", "ghost"), feat("c1")));
        assertEquals(0, weave.rows().get(0).lane());
        assertTrue(weave.rows().get(0).parentLanes().isEmpty());
    }

    @Test
    void emptyTrailIsEmpty() {
        Weave weave = Weave.of(List.of());
        assertEquals(0, weave.laneCount());
        assertTrue(weave.rows().isEmpty());
    }

    @Test
    void bloodlineWalksAllAncestors() {
        // m(c3, s2); c3->c2->c1; s2->s1->c2  =>  c3, s2, s1, c2, c1
        List<Feat> trail = List.of(
                feat("m", "c3", "s2"),
                feat("c3", "c2"),
                feat("s2", "s1"),
                feat("s1", "c2"),
                feat("c2", "c1"),
                feat("c1"));
        Weave weave = Weave.of(trail);
        List<String> lineage = weave.bloodline("m", trail);
        assertEquals(5, lineage.size());
        assertTrue(lineage.containsAll(List.of("c1", "c2", "c3", "s1", "s2")));
    }

    @Test
    void deepBloodlineDoesNotBlowStack() {
        // 50k-deep chain must survive the iterative walk.
        int depth = 50_000;
        java.util.List<Feat> trail = new java.util.ArrayList<>(depth);
        for (int i = depth; i >= 1; i--) {
            trail.add(i == depth ? feat("f" + i) : feat("f" + i, "f" + (i + 1)));
        }
        Weave weave = Weave.of(trail);
        assertEquals(depth - 1, weave.bloodline("f1", trail).size());
    }
}
