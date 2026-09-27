package forkknight.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The stable where the knight's background work rides: one shared pool
 * of daemon threads instead of a fresh thread per job. While a job runs
 * its pool thread wears the job's own name, so the chronicle's readers
 * still say what they are.
 *
 * <p>Depart shuts the pool down; because every thread is a daemon, a
 * forgotten pool can never keep the JVM alive on its own.
 */
public final class Background {

    private static final Background SHARED = new Background("forkknight");

    private final ExecutorService pool;
    private final String prefix;

    Background(String prefix) {
        this.prefix = prefix;
        AtomicInteger counter = new AtomicInteger();
        this.pool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable,
                prefix + "-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The pool the whole app rides. */
    public static Background shared() {
        return SHARED;
    }

    /** Runs the work soon on a pool thread, under the job's own name. */
    public void start(Runnable work, String name) {
        pool.execute(() -> {
            Thread current = Thread.currentThread();
            String born = current.getName();
            current.setName(name);
            try {
                work.run();
            } finally {
                current.setName(born);
            }
        });
    }

    /** Stops accepting work and waits briefly for the road to clear. */
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
