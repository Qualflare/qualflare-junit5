package com.qualflare.junit5;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JVM-scoped run state.
 *
 * <p>This class exists because of a fact that is easy to get wrong and expensive to miss:
 * <b>the JUnit Platform creates a NEW listener instance for every TestPlan.</b> Surefire
 * runs one plan per rerun, so a listener holding its state in instance fields gets a fresh,
 * empty accumulator for every retry.
 *
 * <p>Measured before this class existed: {@code rerunFailingTestsCount=3} against a test
 * that fails twice then passes produced <b>four report files from one JVM</b> (same pid,
 * same millisecond) holding 7, 4, 4 and 3 cases. The flaky test appeared as three separate
 * one-attempt cases instead of one case with three attempts, which is precisely the retry
 * history the reporter exists to capture.
 *
 * <p>The earlier spike missed it because its plan counter was itself {@code static}, so it
 * incremented across instances and looked like a single listener. The integration fixture
 * caught it; the unit tests could not, because they drive {@link Accumulator} directly.
 *
 * <p>So: one accumulator per JVM, and one FILE per JVM -- though it is written several
 * times, on every launcher-session close and again from the shutdown hook, each write
 * replacing the last with everything accumulated so far. With {@code forkCount > 1} each
 * forked JVM has its own accumulator and its own file, which is the directory-merge model
 * {@code qf collect} already uses for pytest-xdist and Vitest shards.
 */
final class Run {

    private static final Accumulator ACCUMULATOR = new Accumulator();
    private static final AtomicBoolean HOOKED = new AtomicBoolean(false);

    private Run() {}

    static Accumulator accumulator() {
        return ACCUMULATOR;
    }

    /** Installs the shutdown hook exactly once, however many listeners are constructed. */
    static void ensureHook() {
        if (HOOKED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(Run::write, "qualflare-write"));
        }
    }

    /**
     * Rewrites the report with everything accumulated so far.
     *
     * <p>Deliberately REPEATABLE rather than once-only. It is called on every
     * launcher-session close -- of which Surefire opens one per rerun -- and again from
     * the shutdown hook. Each call rewrites the same file (see {@code ReportWriter}'s
     * per-JVM filename), so intermediate writes are earlier snapshots of the final one
     * and the last writer wins with the complete picture.
     *
     * <p>Synchronized because two triggers can race: a session closing on the main thread
     * while the shutdown hook runs on its own. Two writers interleaving on one file would
     * produce truncated JSON, which is worse than either result alone.
     */
    static synchronized void write() {
        if (ACCUMULATOR.isEmpty()) {
            return;
        }
        try {
            ReportWriter.write(ACCUMULATOR.cases());
        } catch (Exception e) {
            // A reporter must never be the reason a build fails.
            System.err.println("[qualflare-junit5] could not write the report: " + e);
        }
    }

    /** Test-only: lets a test start from a clean JVM-scoped state. */
    static void resetForTest() {
        ACCUMULATOR.clear();
    }
}
