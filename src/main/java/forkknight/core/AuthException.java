package forkknight.core;

/**
 * Raised when an account operation is refused. The message is written to be
 * shown to the knight as-is.
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
