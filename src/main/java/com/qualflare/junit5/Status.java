package com.qualflare.junit5;

import org.junit.platform.engine.TestExecutionResult;

/**
 * The six statuses the wire contract accepts, and how JUnit's three map onto them.
 *
 * <p>JUnit is deliberately coarser than the wire: {@code TestExecutionResult.Status} has
 * only SUCCESSFUL, FAILED and ABORTED, and skips arrive by a different callback entirely.
 * That still beats JUnit XML, which has no way to say "aborted" at all -- an assumption
 * failure and a genuine skip are indistinguishable there.
 */
final class Status {
    static final String PASSED = "passed";
    static final String FAILED = "failed";
    static final String SKIPPED = "skipped";
    static final String ERROR = "error";
    static final String TIMEOUT = "timeout";
    static final String ABORTED = "aborted";

    private Status() {}

    /**
     * ABORTED maps to "skipped", not "aborted".
     *
     * <p>This is the one mapping worth arguing about. JUnit reports a failed
     * {@code Assumptions.assumeTrue(...)} as ABORTED, and the overwhelming majority of
     * ABORTED results are exactly that -- a test that declined to run because a
     * precondition was not met, which is semantically a skip. The wire's "aborted" means
     * a run that was killed, which is a different and much more alarming thing. Mapping
     * assumption failures onto it would paint healthy suites red.
     */
    static String of(TestExecutionResult result) {
        switch (result.getStatus()) {
            case SUCCESSFUL:
                return PASSED;
            case ABORTED:
                return SKIPPED;
            case FAILED:
            default:
                return failureKind(result.getThrowable().orElse(null));
        }
    }

    /**
     * An assertion failure is a "failed"; anything else thrown is an "error".
     *
     * <p>The distinction is the one triage actually uses: a hundred errors usually means
     * one broken fixture, a hundred failures means a hundred broken expectations. JUnit
     * does not draw it for us -- both arrive as FAILED -- so it is drawn here from the
     * throwable, matching what the pytest reporter does with its own error/failure split.
     */
    private static String failureKind(Throwable t) {
        if (t == null) {
            return FAILED;
        }
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            String name = c.getClass().getName();
            if (name.equals("org.opentest4j.AssertionFailedError")
                    || name.equals("org.opentest4j.MultipleFailuresError")
                    || name.equals("java.lang.AssertionError")
                    || name.startsWith("org.assertj.core.error")
                    || name.equals("org.mockito.exceptions.verification.WantedButNotInvoked")) {
                return FAILED;
            }
            // A timeout is its own wire status and must not be flattened into error:
            // it is the difference between "this test is broken" and "this test hung".
            if (name.equals("org.junit.jupiter.api.extension.TestTimedOutException")
                    || name.equals("java.util.concurrent.TimeoutException")
                    || (name.equals("org.opentest4j.TestAbortedException"))) {
                return name.contains("Timed") || name.contains("Timeout") ? TIMEOUT : SKIPPED;
            }
        }
        return ERROR;
    }
}
