package forkknight.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The realm's scryer - an enchanted index over the chronicle that answers
 * queries far faster than scanning every feat one by one.
 *
 * Two structures work in concert:
 *
 * 1. A 26-way lowercase TRIE over every token of every feat (summary and
 *    body). Prefix search ("init" hits "initialize", "initiate"...) walks
 *    the trie in O(L) then harvests postings with a bounded DFS.
 *
 * 2. A BIGRAM INVERTED INDEX (token -> feats containing it) used as an
 *    intersection filter for multi-word queries: "fix login" first
 *    intersects the postings of "fix" and "login" (hash sets), and only
 *    then runs the exact substring check on the handful of survivors.
 *    This turns an O(N * text) scan into O(L + posting sizes).
 *
 * The scryer keeps its own Feat copies, so later surveys of other banners
 * never poison an earlier index.
 */
public final class Scryer {

    public enum Scope { SUMMARY, AUTHOR, HASH }

    // ------------------------------------------------------------------
    // Trie over lowercase characters a-z plus digits and '-' '_' '/' '.'
    // ------------------------------------------------------------------

    private static final class TrieNode {
        // Sparse children array keeps small alphabets fast and compact.
        private final TrieNode[] children = new TrieNode[43];
        private IntArrayList postings;

        private static int slot(char c) {
            if (c >= 'a' && c <= 'z') {
                return c - 'a';
            }
            if (c >= '0' && c <= '9') {
                return 26 + (c - '0');
            }
            return switch (c) {
                case '-' -> 36;
                case '_' -> 37;
                case '/' -> 38;
                case '.' -> 39;
                case ' ' -> 40;
                case '@' -> 41;
                default -> 42;   // bucket for everything else
            };
        }
    }

    /** Minimal growable int array: postings lists never need boxing. */
    private static final class IntArrayList {
        private int[] data = new int[4];
        private int size;

        void add(int value) {
            if (size == data.length) {
                int[] grown = new int[data.length * 2];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = value;
        }

        int[] toArray() {
            int[] copy = new int[size];
            System.arraycopy(data, 0, copy, 0, size);
            return copy;
        }
    }

    private final List<Feat> feats;
    private final Map<String, IntArrayList> tokenPostings = new HashMap<>();
    private final TrieNode summaryTrie = new TrieNode();
    private final TrieNode authorTrie = new TrieNode();
    private final TrieNode hashTrie = new TrieNode();
    private final Map<String, Integer> featIndexById = new HashMap<>();

    public Scryer(List<Feat> feats) {
        // Own copies: later surveys must not mutate what we indexed.
        this.feats = new ArrayList<>(feats);
        for (int i = 0; i < this.feats.size(); i++) {
            Feat feat = this.feats.get(i);
            featIndexById.put(feat.hash(), i);
            indexText(summaryTrie, tokenize(feat.summary()) + tokenize(feat.body()), i);
            indexText(authorTrie, tokenize(feat.author()), i);
            indexText(hashTrie, feat.hash() == null ? "" : feat.hash().toLowerCase(), i);
            for (String token : uniqueTokens(feat)) {
                tokenPostings.computeIfAbsent(token, k -> new IntArrayList()).add(i);
            }
        }
    }

    public List<Feat> feats() {
        return Collections.unmodifiableList(feats);
    }

    /** Position of a feat in the indexed list, -1 when absent. */
    public int indexOf(String hash) {
        Integer idx = featIndexById.get(hash);
        return idx == null ? -1 : idx;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * Case-insensitive search over the given scope. Multi-word queries
     * intersect bigram postings before the exact substring check; single
     * words walk the trie (prefix harvest) then verify.
     */
    public List<Feat> scry(String rawQuery, Scope scope) {
        String query = rawQuery == null ? "" : rawQuery.strip().toLowerCase();
        if (query.isEmpty()) {
            return new ArrayList<>(feats);
        }
        // Fast path: full-hash lookups are a single map hit.
        if (scope == Scope.HASH && featIndexById.containsKey(query)) {
            return new ArrayList<>(List.of(feats.get(featIndexById.get(query))));
        }

        List<Feat> hits = new ArrayList<>();
        String[] words = query.split("\\s+");
        int[] candidates = candidatesFor(words, scope);

        // Candidates arrive in ascending index order (= master survey
        // order), so hits inherit the chronicle's newest-first ordering
        // without any re-sort.
        for (int idx : candidates) {
            if (matches(feats.get(idx), query, words, scope)) {
                hits.add(feats.get(idx));
            }
        }
        return hits;
    }

    /** Predicate form for FX FilteredList wiring. */
    public Predicate<Feat> predicateFor(String query, Scope scope) {
        String q = query == null ? "" : query.strip().toLowerCase();
        String[] words = q.isEmpty() ? new String[0] : q.split("\\s+");
        return feat -> q.isEmpty() || matches(feat, q, words, scope);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private int[] candidatesFor(String[] words, Scope scope) {
        if (words.length == 0) {
            return new int[0];
        }
        if (words.length == 1) {
            return prefixHarvest(words[0], scope);
        }
        // Multi-word: intersect postings of every word.
        Set<Integer> survivors = null;
        for (String word : words) {
            IntArrayList postings = tokenPostings.get(word);
            if (postings == null) {
                return new int[0];   // one unknown word kills the query
            }
            Set<Integer> set = new HashSet<>();
            postingsToArray(postings, set);
            if (survivors == null) {
                survivors = set;
            } else {
                survivors.retainAll(set);
            }
            if (survivors.isEmpty()) {
                return new int[0];
            }
        }
        return survivors.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    private int[] prefixHarvest(String word, Scope scope) {
        TrieNode trie = switch (scope) {
            case AUTHOR -> authorTrie;
            case HASH -> hashTrie;
            default -> summaryTrie;
        };
        TrieNode walk = trie;
        for (char c : word.toCharArray()) {
            TrieNode next = walk.children[TrieNode.slot(c)];
            if (next == null) {
                // Prefix dies here; fall back to the full candidate set
                // only because the query may still match across token
                // boundaries (e.g. "log-in" vs summary "fix log in").
                Set<Integer> all = new HashSet<>();
                for (int i = 0; i < feats.size(); i++) {
                    all.add(i);
                }
                return all.stream().mapToInt(Integer::intValue).sorted().toArray();
            }
            walk = next;
        }
        Set<Integer> harvest = new HashSet<>();
        collectPostings(walk, harvest, 0);
        return harvest.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    /** Bounded DFS: stops early once everything below is harvested. */
    private void collectPostings(TrieNode node, Set<Integer> into, int depth) {
        if (node.postings != null) {
            postingsToArray(node.postings, into);
        }
        if (depth > 32) {
            return;   // defensive cap; token depth is bounded anyway
        }
        for (TrieNode child : node.children) {
            if (child != null) {
                collectPostings(child, into, depth + 1);
            }
        }
    }

    private boolean matches(Feat feat, String query, String[] words, Scope scope) {
        String text = switch (scope) {
            case AUTHOR -> feat.author() == null ? "" : feat.author().toLowerCase();
            case HASH -> feat.hash() == null ? "" : feat.hash().toLowerCase();
            default -> (feat.summary() == null ? "" : feat.summary().toLowerCase())
                    + "\n" + (feat.body() == null ? "" : feat.body().toLowerCase());
        };
        if (words.length <= 1) {
            return text.contains(query);
        }
        // Every word must appear somewhere (order-free AND).
        for (String word : words) {
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static void postingsToArray(IntArrayList postings, Set<Integer> into) {
        for (int i = 0; i < postings.size; i++) {
            into.add(postings.data[i]);
        }
    }

    private void indexText(TrieNode root, String normalized, int featIdx) {
        for (String token : normalized.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            TrieNode walk = root;
            for (char c : token.toCharArray()) {
                int slot = TrieNode.slot(c);
                if (walk.children[slot] == null) {
                    walk.children[slot] = new TrieNode();
                }
                walk = walk.children[slot];
            }
            if (walk.postings == null) {
                walk.postings = new IntArrayList();
            }
            walk.postings.add(featIdx);
        }
    }

    private Set<String> uniqueTokens(Feat feat) {
        Set<String> tokens = new HashSet<>();
        collectTokens(feat.summary(), tokens);
        collectTokens(feat.body(), tokens);
        collectTokens(feat.author(), tokens);
        if (feat.hash() != null) {
            tokens.add(feat.hash().toLowerCase());
        }
        return tokens;
    }

    private static void collectTokens(String text, Set<String> into) {
        if (text == null) {
            return;
        }
        for (String token : text.toLowerCase().split("\\s+")) {
            if (!token.isEmpty()) {
                into.add(token);
            }
        }
    }

    private static String tokenize(String text) {
        return text == null ? "" : text.toLowerCase();
    }
}
