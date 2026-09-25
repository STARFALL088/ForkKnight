package forkknight.core;

/**
 * Wraps every SQL failure raised while reading or writing the Knight's Ledger.
 * Unchecked so it can bubble through the existing persistence calls unchanged.
 */
public class KnightDbException extends RuntimeException {

    public KnightDbException(String message) {
        super(message);
    }

    public KnightDbException(String message, Throwable cause) {
        super(message, cause);
    }

    public KnightDbException(Throwable cause) {
        super(cause);
    }
}
