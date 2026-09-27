package forkknight;

import forkknight.core.Background;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/**
 * One road the knight rides in the background: a Task that carries its
 * own thread-pool name and reports a broken road in a single voice.
 *
 * <p>Subclasses implement {@link #call()} (the work) and may wire
 * {@code setOnSucceeded} like any task; a failure is handled by this
 * class instead of every call site - it is led to the reporter given at
 * construction, prefixed with the words that site chose. A road built
 * without a reporter fails silently, and sites with stranger failures
 * (disabling a box, a cut-off far call) override {@link #failed()} or
 * {@link #failureText(Throwable)}.
 */
public abstract class RealmTask<T> extends Task<T> {

    private final String name;
    private final Consumer<String> reporter;
    private final String failurePrefix;

    /** A road whose failures stay unheard. */
    protected RealmTask(String name) {
        this(name, null, "");
    }

    /** A road whose failures are told to the reporter, led by the prefix. */
    protected RealmTask(String name, Consumer<String> reporter,
            String failurePrefix) {
        this.name = Objects.requireNonNull(name, "a road needs a name");
        this.reporter = reporter;
        this.failurePrefix = failurePrefix == null ? "" : failurePrefix;
    }

    /** The name this road's pool thread wears while it runs. */
    public final String name() {
        return name;
    }

    /** Rides the shared stable under this job's own name. */
    public final void start() {
        Background.shared().start(this, name);
    }

    @Override
    protected void failed() {
        if (reporter != null) {
            reporter.accept(failureText(getException()));
        }
    }

    /** What a broken road says; subclasses may sharpen the words. */
    protected String failureText(Throwable cause) {
        String why;
        if (cause == null) {
            why = "for no reason at all";
        } else if (cause.getMessage() != null) {
            why = cause.getMessage();
        } else {
            why = cause.getClass().getSimpleName();
        }
        return failurePrefix + why;
    }
}
