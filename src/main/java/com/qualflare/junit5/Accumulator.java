package com.qualflare.junit5;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Run state that outlives a single TestPlan.
 *
 * <p>This class exists because of one measured fact: {@code testPlanExecutionFinished}
 * fires ONCE PER PLAN, and Surefire runs a fresh plan for every rerun -- four plans for
 * rerunFailingTestsCount=3. Writing the report from that callback would emit four partial
 * files, or overwrite until only the last rerun survived, silently discarding the retry
 * history that is the main reason to use a native reporter at all. So nothing is written
 * here; {@link QualflareListener} writes once, at JVM shutdown.
 *
 * <p>Synchronised rather than merely concurrent-collection based: under
 * {@code junit.jupiter.execution.parallel.enabled} the listener is called from
 * ForkJoinPool workers (observed: ForkJoinPool-1-worker-2), and appending an attempt is a
 * read-modify-write on a case that must not interleave.
 */
final class Accumulator {

    /** Insertion-ordered so the report lists cases in the order they first ran. */
    private final Map<String, CaseRecord> byUniqueId = new LinkedHashMap<>();
    private final Map<String, Long> startedNanos = new LinkedHashMap<>();

    synchronized void started(String uniqueId, long nanoTime) {
        startedNanos.put(uniqueId, nanoTime);
    }

    /**
     * @param nanoTime System.nanoTime() at finish; the elapsed time is computed here
     *                 rather than trusted from the caller so a missing start degrades to
     *                 zero instead of a wild negative.
     */
    synchronized void finished(String uniqueId, String suiteName, String className,
                               String displayName, String legacyName,
                               String status, long nanoTime, String message, String trace) {
        Long start = startedNanos.remove(uniqueId);
        long elapsed = start == null ? 0L : Math.max(0L, nanoTime - start);

        CaseRecord rec = byUniqueId.computeIfAbsent(uniqueId,
                id -> new CaseRecord(id, suiteName, className, displayName, legacyName));
        rec.attempts.add(new Attempt(status, elapsed, message, trace));
    }

    /**
     * A skip has no duration and no attempt of its own worth recording as a retry -- it
     * never ran. Recorded as a single attempt so the case exists in the report; a skipped
     * test missing entirely would look like a shrinking suite.
     */
    synchronized void skipped(String uniqueId, String suiteName, String className,
                              String displayName, String legacyName, String reason) {
        CaseRecord rec = byUniqueId.computeIfAbsent(uniqueId,
                id -> new CaseRecord(id, suiteName, className, displayName, legacyName));
        if (rec.attempts.isEmpty()) {
            rec.attempts.add(new Attempt(Status.SKIPPED, 0L, reason == null ? "" : reason, ""));
        }
    }

    synchronized Collection<CaseRecord> cases() {
        return new ArrayList<>(byUniqueId.values());
    }

    synchronized boolean isEmpty() {
        return byUniqueId.isEmpty();
    }

    synchronized void clear() {
        byUniqueId.clear();
        startedNanos.clear();
    }
}
