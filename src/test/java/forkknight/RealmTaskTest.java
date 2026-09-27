package forkknight;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RealmTaskTest {

    private static boolean toolkit;

    /**
     * A Task fires its state events through Platform.runLater, so without
     * a live FX toolkit it dies before call() ever runs. On a machine with
     * no display the toolkit stays down and the start() proof is skipped.
     */
    @BeforeAll
    static void wakeTheFxToolkit() {
        try {
            Platform.startup(() -> { });
            toolkit = true;
        } catch (IllegalStateException alreadyAwake) {
            toolkit = true;
        } catch (Throwable noDisplay) {
            toolkit = false;
        }
    }

    @AfterAll
    static void restTheToolkit() {
        if (toolkit) {
            Platform.exit(); // the FX thread is no daemon; let it go
        }
    }

    @Test
    void carriesItsNameOntoAPoolDaemonWhenStarted() throws Exception {
        assumeTrue(toolkit, "no display for the FX toolkit");
        AtomicReference<String> seen = new AtomicReference<>();
        AtomicBoolean daemon = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        RealmTask<Void> road = new RealmTask<>("trail-survey") {
            @Override
            protected Void call() {
                seen.set(Thread.currentThread().getName());
                daemon.set(Thread.currentThread().isDaemon());
                done.countDown();
                return null;
            }
        };

        road.start();

        assertTrue(done.await(2, TimeUnit.SECONDS), "the road was never ridden");
        assertEquals("trail-survey", seen.get());
        assertTrue(daemon.get(), "background roads must ride daemons");
        assertEquals("trail-survey", road.name());
    }

    @Test
    void aBrokenRoadSpeaksInTheWordsItsSiteChose() {
        RealmTask<Void> road = new RealmTask<>("survey", message -> { },
                "The survey failed: ") {
            @Override
            protected Void call() {
                return null;
            }
        };

        assertEquals("The survey failed: boom",
            road.failureText(new IOException("boom")));
        assertEquals("The survey failed: IllegalStateException",
            road.failureText(new IllegalStateException()));
        assertEquals("The survey failed: for no reason at all",
            road.failureText(null));
    }

    @Test
    void aBrokenRoadActuallyTellsItsReporter() {
        List<String> told = new CopyOnWriteArrayList<>();
        RealmTask<Void> road = new RealmTask<>("survey", told::add,
                "The muster failed: ") {
            @Override
            protected Void call() {
                return null;
            }
        };

        road.failed();

        assertEquals(1, told.size());
        assertEquals("The muster failed: for no reason at all", told.get(0));
    }

    @Test
    void aSilentRoadTellsNobodyWhenItFails() {
        RealmTask<Void> road = new RealmTask<>("quiet") {
            @Override
            protected Void call() {
                return null;
            }
        };

        assertDoesNotThrow(road::failed);
    }

    @Test
    void aRoadMaySharpenItsOwnLastWords() {
        RealmTask<String> road = new RealmTask<>("far-call", message -> { }, "") {
            @Override
            protected String call() {
                return null;
            }

            @Override
            protected String failureText(Throwable cause) {
                if (cause instanceof InterruptedException) {
                    return "The far call was cut off.";
                }
                return super.failureText(cause);
            }
        };

        assertEquals("The far call was cut off.",
            road.failureText(new InterruptedException()));
        assertEquals("the network broke", road.failureText(
            new IOException("the network broke")));
    }

    @Test
    void everyRoadNeedsAName() {
        assertThrows(NullPointerException.class, () -> new RealmTask<Void>(null) {
            @Override
            protected Void call() {
                return null;
            }
        });
    }
}
