package forkknight.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VaultTest {

    @Test
    void missComputesAndCaches() {
        Vault<String, String> vault = new Vault<>();
        AtomicInteger loads = new AtomicInteger();
        String value = vault.acquire("k", k -> {
            loads.incrementAndGet();
            return "v";
        });
        assertEquals("v", value);
        assertEquals("v", vault.acquire("k", k -> "other"));
        assertEquals(1, loads.get());
    }

    @Test
    void lruEvictsEldestBeyondCapacity() {
        Vault<String, Integer> vault = new Vault<>(2);
        vault.acquire("a", k -> 1);
        vault.acquire("b", k -> 2);
        assertEquals(2, vault.size());
        // Touch "a": now "b" is the eldest.
        assertEquals(1, vault.fetch("a"));
        vault.acquire("c", k -> 3);
        assertEquals(2, vault.size());
        assertNull(vault.fetch("b"), "b was LRU and must be evicted");
        assertNotNull(vault.fetch("a"));
        assertNotNull(vault.fetch("c"));
    }

    @Test
    void fetchMissReturnsNull() {
        Vault<String, String> vault = new Vault<>();
        assertNull(vault.fetch("ghost"));
    }

    @Test
    void nullLoadResultsAreNotCached() {
        Vault<String, String> vault = new Vault<>();
        assertNull(vault.acquire("k", k -> null));
        assertEquals(0, vault.size());
    }

    @Test
    void purgeRemovesSingleKey() {
        Vault<String, String> vault = new Vault<>();
        vault.acquire("k", k -> "v");
        vault.purge("k");
        assertEquals(0, vault.size());
        assertNull(vault.fetch("k"));
    }

    @Test
    void clearEmptiesEverything() {
        Vault<String, String> vault = new Vault<>();
        vault.acquire("a", k -> "1");
        vault.acquire("b", k -> "2");
        vault.clear();
        assertEquals(0, vault.size());
    }

    @Test
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new Vault<String, String>(0));
    }
}
