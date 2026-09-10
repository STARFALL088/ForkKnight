package forkknight.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The war room's vault: a bounded LRU cache for battle recounts (diffs).
 *
 * Built on an access-ordered LinkedHashMap - the classic O(1) LRU:
 * every fetch bumps the key to the youngest end, and inserting past the
 * capacity automatically evicts the eldest entry (the least recently
 * used) in constant time. Recounts of big feats are expensive (a full
 * subprocess per diff), so caching keeps the UI snappy when the knight
 * flips between the same handful of feats.
 *
 * @param <K> key type (usually "hash:path" recount identities)
 * @param <V> cached value type (usually recount text)
 */
public final class Vault<K, V> {

    private static final int DEFAULT_CAPACITY = 64;

    private final LinkedHashMap<K, V> store;

    public Vault() {
        this(DEFAULT_CAPACITY);
    }

    public Vault(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        // accessOrder = true: fetch() re-inserts the key, moving it to
        // the MRU end; eldest (LRU) head is dropped on overflow.
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    /** Cached value or null; marks the key most-recently-used on a hit. */
    public V fetch(K key) {
        synchronized (this) {
            return store.get(key);
        }
    }

    /** Cache miss handler: computes, stores and returns the value. */
    public V acquire(K key, Function<K, V> loader) {
        synchronized (this) {
            V cached = store.get(key);
            if (cached != null) {
                return cached;   // get() already refreshed recency
            }
        }
        V fresh = loader.apply(key);
        if (fresh == null) {
            return null;
        }
        synchronized (this) {
            store.put(key, fresh);   // may evict the LRU entry
        }
        return fresh;
    }

    public void purge(K key) {
        synchronized (this) {
            store.remove(key);
        }
    }

    public void clear() {
        synchronized (this) {
            store.clear();
        }
    }

    public int size() {
        synchronized (this) {
            return store.size();
        }
    }
}
