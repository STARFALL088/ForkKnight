package forkknight.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The realm's chronicler: turns the trail of feats into tales of
 * statistics - who fought most, when the realm was busiest, which paths
 * saw the most action.
 *
 * Uses a bounded min-heap for TOP-K selection: with k small (leader
 * boards), each element costs O(log k) and the heap always holds the k
 * largest seen so far - far cheaper than sorting everything.
 *
 * All methods operate on immutable inputs and produce plain records,
 * safe to call from any thread.
 */
public final class Chronicler {

    /** One hero's standing in the realm. */
    public record HeroStanding(String hero, int feats) {}

    /** One day's worth of action. */
    public record DayAction(LocalDate day, int feats) {}

    /** One path's total churn across the surveyed feats. */
    public record PathHeat(String path, int touches) {}

    private final List<Feat> feats;

    private Chronicler(List<Feat> feats) {
        this.feats = feats;
    }

    public static Chronicler of(List<Feat> featsNewestFirst) {
        return new Chronicler(List.copyOf(featsNewestFirst));
    }

    public int featCount() {
        return feats.size();
    }

    /** Feats per hero, descending; top-k only when k > 0. */
    public List<HeroStanding> heroStandings(int k) {
        Map<String, Integer> counts = new HashMap<>();
        for (Feat feat : feats) {
            if (feat.author() != null) {
                counts.merge(feat.author(), 1, Integer::sum);
            }
        }
        return topK(counts, k).stream()
                .map(e -> new HeroStanding(e.getKey(), e.getValue()))
                .toList();
    }

    /** Feats per day, newest day first; top-k when k > 0. */
    public List<DayAction> busiestDays(int k) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (Feat feat : feats) {
            if (feat.date() != null) {
                counts.merge(feat.date(), 1, Integer::sum);
            }
        }
        // Busiest first; ties broken by the more recent day.
        List<DayAction> all = counts.entrySet().stream()
                .map(e -> new DayAction(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(DayAction::feats).reversed()
                        .thenComparing(Comparator.comparing(DayAction::day).reversed()))
                .toList();
        return k > 0 ? all.subList(0, Math.min(k, all.size())) : all;
    }

    /** Total fusion (merge) count in the trail. */
    public int fusionCount() {
        return (int) feats.stream().filter(Feat::isFusion).count();
    }

    /**
     * Path heat: the k most-touched paths across the given tolls. Churn
     * counting uses a hash map pass, then the bounded heap for top-k.
     */
    public List<PathHeat> pathHeat(Map<String, List<Dispatch>> tollsByFeat, int k) {
        Map<String, Integer> heat = new HashMap<>();
        for (List<Dispatch> tolls : tollsByFeat.values()) {
            for (Dispatch dispatch : tolls) {
                heat.merge(dispatch.path(), 1, Integer::sum);
            }
        }
        return topK(heat, k).stream()
                .map(e -> new PathHeat(e.getKey(), e.getValue()))
                .toList();
    }

    /** Days between the newest and oldest feat; 0 for empty/single. */
    public long campaignSpanDays() {
        if (feats.isEmpty()) {
            return 0;
        }
        LocalDate newest = null;
        LocalDate oldest = null;
        for (Feat feat : feats) {
            if (feat.date() == null) {
                continue;
            }
            if (newest == null || feat.date().isAfter(newest)) {
                newest = feat.date();
            }
            if (oldest == null || feat.date().isBefore(oldest)) {
                oldest = feat.date();
            }
        }
        if (newest == null || oldest == null) {
            return 0;
        }
        return newest.toEpochDay() - oldest.toEpochDay();
    }

    // ------------------------------------------------------------------
    // Bounded min-heap top-k: O(n log k) instead of O(n log n)
    // ------------------------------------------------------------------

    private static <K> List<Map.Entry<K, Integer>> topK(Map<K, Integer> counts, int k) {
        if (k <= 0 || counts.size() <= k) {
            return counts.entrySet().stream()
                    .sorted(Map.Entry.<K, Integer>comparingByValue().reversed())
                    .toList();
        }
        // Min-heap of size k: the root is the weakest of the leaders;
        // anything beating it displaces it.
        PriorityQueue<Map.Entry<K, Integer>> heap = new PriorityQueue<>(
                Comparator.comparingInt(Map.Entry::getValue));
        for (Map.Entry<K, Integer> entry : counts.entrySet()) {
            if (heap.size() < k) {
                heap.offer(entry);
            } else if (entry.getValue() > heap.peek().getValue()) {
                heap.poll();
                heap.offer(entry);
            }
        }
        List<Map.Entry<K, Integer>> leaders = new ArrayList<>(heap);
        leaders.sort(Map.Entry.<K, Integer>comparingByValue().reversed());
        return leaders;
    }
}
