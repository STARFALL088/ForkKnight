package forkknight.core;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing for local accounts: PBKDF2-HMAC-SHA256 with a random salt,
 * stored as {@code pbkdf2-sha256$iterations$salt$hash} (both parts Base64).
 * Iterations are read back from the stored string, so raising the cost later
 * never invalidates a password - it simply rehashes on the next sign-in.
 *
 * <p>{@link #matches} answers {@code false} for anything it does not
 * recognise (including the keeper's {@code locked$} sentinel and an empty
 * hash), and never throws.
 */
final class PasswordHasher {

    static final int ITERATIONS = 600_000;
    private static final String PREFIX = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int MAX_ITERATIONS = 10_000_000;

    private PasswordHasher() {
    }

    static String hash(char[] password) {
        return hash(password, ITERATIONS);
    }

    static String hash(char[] password, int iterations) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("A password is required");
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Unreasonable iteration count: " + iterations);
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] derived = derive(password, salt, iterations);
        return PREFIX + "$" + iterations + "$" + encode(salt) + "$" + encode(derived);
    }

    static boolean matches(char[] password, String stored) {
        if (password == null || password.length == 0 || stored == null) {
            return false;
        }
        int iterations = parseIterations(stored);
        if (iterations < 0) {
            return false;
        }
        String[] parts = stored.split("\\$", -1);
        try {
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the stored hash is malformed or was made with another cost. */
    static boolean needsRehash(String stored, int iterations) {
        return parseIterations(stored) != iterations;
    }

    /** Iterations embedded in the stored hash, or -1 when it is not ours. */
    private static int parseIterations(String stored) {
        if (stored == null) {
            return -1;
        }
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0]) || parts[1].isEmpty()) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(parts[1]);
            return parsed >= 1 && parsed <= MAX_ITERATIONS ? parsed : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            try {
                return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            // PBKDF2WithHmacSHA256 ships with the JDK; this would be a broken install.
            throw new IllegalStateException("Password hashing is unavailable", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
