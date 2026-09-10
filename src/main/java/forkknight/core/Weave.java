package forkknight.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The chronicle weave - lays the DAG of feats onto numbered lanes so the
 * UI can draw connecting lines without any two stories crossing.
 *
 * Algorithm (single pass over the newest-first survey):
 *
 * 1. For each feat, find its lane. If it has parents on this screen, the
 *    feat continues the highest-priority parent's lane; otherwise it
 *    claims a fresh lane from the free pool.
 *
 * 2. A FREE-LANE POOL (TreeSet of lane numbers) hands out the lowest
 *    available lane in O(log L). When a feat's lineage block is fully
 *    rendered (all children drawn, its parent lane reached), the lane
 *    returns to the pool. This keeps the weave compact - lanes are
 *    recycled like barracks bunks rather than sprawling.
 *
 * 3. Each feat also records which lanes to draw CURVES from (parent
 *    lanes), so the UI can render fusions (merges) with bends.
 */
public final class Weave {

    /** Where a feat sits in the weave, and what to draw around it. */
    public record Woven(Feat feat, int lane, List<Integer> parentLanes, boolean fork) {}

    private final List<Woven> woven;
    private final int laneCount;

    private Weave(List<Woven> woven, int laneCount) {
        this.woven = woven;
        this.laneCount = laneCount;
    }

    public static Weave of(List<Feat> featsNewestFirst) {
        // Position of every feat hash in the input list.
        Map<String, Integer> rowOf = new HashMap<>();
        for (int i = 0; i < featsNewestFirst.size(); i++) {
            rowOf.put(featsNewestFirst.get(i).hash(), i);
        }

        // laneOf: hash -> lane currently assigned to its lineage.
        Map<String, Integer> laneOf = new LinkedHashMap<>();
        TreeSet<Integer> freeLanes = new TreeSet<>();
        List<Woven> result = new ArrayList<>(featsNewestFirst.size());
        int maxLane = -1;

        for (int row = 0; row < featsNewestFirst.size(); row++) {
            Feat feat = featsNewestFirst.get(row);
            Integer lane = laneOf.remove(feat.hash());

            if (lane == null) {
                // A brand-new lineage tip (or a feat whose children are
                // off-screen): claim the lowest free lane.
                Integer recycled = freeLanes.pollFirst();
                lane = recycled == null ? claimFreshLane(maxLane) : recycled;
            }
            if (lane > maxLane) {
                maxLane = lane;
            }

            List<Integer> parentLanes = new ArrayList<>();
            boolean fork = false;
            int firstParentLane = lane;
            boolean continues = false;
            for (int p = 0; p < feat.parentHashes().size(); p++) {
                String parentHash = feat.parentHashes().get(p);
                Integer parentRow = rowOf.get(parentHash);
                if (parentRow == null || parentRow <= row) {
                    continue;   // parent off-screen or malformed: skip
                }
                continues = true;
                Integer parentLane = laneOf.remove(parentHash);
                if (p == 0) {
                    // First parent continues this lane when free.
                    if (parentLane != null) {
                        firstParentLane = parentLane;
                        freeLanes.remove(parentLane);
                    } else {
                        firstParentLane = lane;
                    }
                    parentLanes.add(firstParentLane);
                } else {
                    fork = true;
                    // Secondary parents branch: give them their own lane.
                    if (parentLane != null) {
                        freeLanes.remove(parentLane);
                        parentLanes.add(parentLane);
                    } else {
                        Integer recycled = freeLanes.pollFirst();
                        int branchLane = recycled == null ? maxLane + 1 : recycled;
                        if (branchLane > maxLane) {
                            maxLane = branchLane;
                        }
                        parentLanes.add(branchLane);
                    }
                    laneOf.put(parentHash, parentLanes.get(parentLanes.size() - 1));
                }
            }
            if (continues) {
                laneOf.put(feat.parentHashes().get(0), firstParentLane);
            } else {
                // This feat ends a visible lineage (root or cut-off):
                // its lane returns to the pool for the next lineage.
                freeLanes.add(lane);
            }
            result.add(new Woven(feat, lane, parentLanes, fork));
        }
        return new Weave(result, maxLane + 1);
    }

    private static int claimFreshLane(int maxLane) {
        return maxLane + 1;
    }

    public List<Woven> rows() {
        return woven;
    }

    public int laneCount() {
        return laneCount;
    }

    // ------------------------------------------------------------------
    // Trail queries used by the UI
    // ------------------------------------------------------------------

    /**
     * Bloodline of a feat: every ancestor reachable through parent links,
     * in survey order. Uses an explicit stack (no recursion) so deep
     * histories cannot blow the Java call stack.
     */
    public List<String> bloodline(String hash, List<Feat> featsNewestFirst) {
        Map<String, List<String>> parentsOf = new HashMap<>();
        for (Feat feat : featsNewestFirst) {
            parentsOf.put(feat.hash(), feat.parentHashes());
        }
        List<String> lineage = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        Deque<String> frontier = new ArrayDeque<>(parentsOf.getOrDefault(hash, List.of()));
        while (!frontier.isEmpty()) {
            String current = frontier.pop();
            if (!seen.add(current)) {
                continue;
            }
            lineage.add(current);
            for (String parent : parentsOf.getOrDefault(current, List.of())) {
                if (!seen.contains(parent)) {
                    frontier.push(parent);
                }
            }
        }
        return lineage;
    }
}
