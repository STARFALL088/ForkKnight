package forkknight;

import javafx.application.Platform;

/**
 * Wakes the FX toolkit once for the whole test run.
 *
 * <p>A Task fires its state events through Platform.runLater, so tests
 * that build controls or run tasks need a live toolkit. It is never
 * exited: the FX thread is no daemon, and the Gradle worker ends the
 * JVM itself. On a machine with no display the toolkit stays down and
 * callers skip their FX-bound proofs.
 */
final class FxKit {

    private static boolean checked;
    private static boolean awake;

    private FxKit() {
    }

    static boolean awake() {
        if (!checked) {
            checked = true;
            try {
                Platform.startup(() -> { });
                awake = true;
            } catch (IllegalStateException alreadyAwake) {
                awake = true;
            } catch (Throwable noDisplay) {
                awake = false;
            }
        }
        return awake;
    }
}
