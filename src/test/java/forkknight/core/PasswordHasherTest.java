package forkknight.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private static final int FAST = 10_000;

    @Test
    void matchesThePasswordItWasHashedFrom() {
        String stored = PasswordHasher.hash("swordfish".toCharArray(), FAST);
        assertTrue(PasswordHasher.matches("swordfish".toCharArray(), stored));
        assertFalse(PasswordHasher.matches("swordfishx".toCharArray(), stored));
        assertFalse(PasswordHasher.matches("".toCharArray(), stored));
        assertFalse(PasswordHasher.matches(null, stored));
    }

    @Test
    void twoHashesOfTheSamePasswordNeverCollide() {
        String first = PasswordHasher.hash("swordfish".toCharArray(), FAST);
        String second = PasswordHasher.hash("swordfish".toCharArray(), FAST);
        assertNotEquals(first, second);
        assertTrue(PasswordHasher.matches("swordfish".toCharArray(), first));
        assertTrue(PasswordHasher.matches("swordfish".toCharArray(), second));
    }

    @Test
    void carriesItsCostInTheStoredString() {
        String stored = PasswordHasher.hash("swordfish".toCharArray());
        assertTrue(stored.startsWith("pbkdf2-sha256$" + PasswordHasher.ITERATIONS + "$"));
        assertEquals(4, stored.split("\\$").length);
    }

    @Test
    void needsRehashSpotsADifferentCost() {
        String cheap = PasswordHasher.hash("swordfish".toCharArray(), FAST);
        assertFalse(PasswordHasher.needsRehash(cheap, FAST));
        assertTrue(PasswordHasher.needsRehash(cheap, PasswordHasher.ITERATIONS));
        assertTrue(PasswordHasher.needsRehash(PasswordHasher.hash("swordfish".toCharArray()), FAST));
    }

    @Test
    void anythingUnrecognisedIsSimplyNotAPassword() {
        // The keeper's lock, an empty hash, half a hash, a non-numeric cost,
        // an out-of-range cost, Base64 that is not Base64: all must answer
        // false without exploding.
        String[] unworthy = {
            "",
            "locked$0123456789abcdef",
            "garbage",
            "pbkdf2-sha256",
            "pbkdf2-sha256$",
            "pbkdf2-sha256$abc$d$e",
            "pbkdf2-sha256$0$c2FsdA==$aGFzaA==",
            "pbkdf2-sha256$99999999999$c2FsdA==$aGFzaA==",
            "sha1$1000$c2FsdA==$aGFzaA==",
            "pbkdf2-sha256$10000$!!!$aGFzaA=="
        };
        for (String stored : unworthy) {
            assertFalse(PasswordHasher.matches("swordfish".toCharArray(), stored),
                "should not match: " + stored);
            assertFalse(PasswordHasher.matches(null, stored));
        }
        assertTrue(PasswordHasher.needsRehash("locked$0123456789abcdef", PasswordHasher.ITERATIONS));
    }

    @Test
    void refusesToHashAnEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(new char[0], FAST));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null, FAST));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("x".toCharArray(), 0));
    }
}
