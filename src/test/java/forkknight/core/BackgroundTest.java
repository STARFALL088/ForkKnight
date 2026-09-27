package forkknight.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundTest {

    @Test
    void startRunsTheWorkSoonUnderTheJobsOwnName() throws Exception {
        Background stable = new Background("test-stable");
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicReference<Boolean> daemon = new AtomicReference<>();

        stable.start(() -> {
            threadName.set(Thread.currentThread().getName());
            daemon.set(Thread.currentThread().isDaemon());
            done.countDown();
        }, "realm-restore");

        assertTrue(done.await(2, TimeUnit.SECONDS), "the work never ran");
        assertEquals("realm-restore", threadName.get());
        assertEquals(Boolean.TRUE, daemon.get());
        stable.shutdown();
    }

    @Test
    void manyJobsRideAtOnceWithoutQueueingForever() throws Exception {
        Background stable = new Background("test-stable");
        int jobs = 32;
        CountDownLatch all = new CountDownLatch(jobs);

        for (int i = 0; i < jobs; i++) {
            stable.start(all::countDown, "job-" + i);
        }

        assertTrue(all.await(2, TimeUnit.SECONDS), "some job never ran");
        stable.shutdown();
    }

    @Test
    void aShutDownStableTakesNoNewWork() {
        Background stable = new Background("test-stable");
        stable.start(() -> { }, "last-ride");
        stable.shutdown();

        assertThrows(RejectedExecutionException.class,
            () -> stable.start(() -> { }, "too-late"));
    }

    @Test
    void theSharedStableIsOnePool() {
        assertSame(Background.shared(), Background.shared());
    }
}
