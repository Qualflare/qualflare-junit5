package com.qualflare.junit5;

import org.junit.platform.engine.TestExecutionResult;

import java.util.concurrent.TimeoutException;

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

    /** Deep enough for any real wrapping, short enough that a cycle cannot hang a build. */
    private static final int MAX_CAUSE_DEPTH = 32;

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
     * throwable.
     *
     * <p>Classified by TYPE, not by class name. An earlier version matched a list of exact
     * names and got this wrong for JUnit 4: {@code org.junit.ComparisonFailure} extends
     * {@code AssertionError} but is not in any such list, so every Vintage assertion
     * failure was reported as an error -- in a reporter whose README advertises that
     * JUnit 4 works. Measured: JUnit 5 throws {@code org.opentest4j.AssertionFailedError},
     * a bare {@code assert} throws {@code java.lang.AssertionError}, and both are
     * {@code instanceof AssertionError}. So is every assertion library worth supporting.
     */
    private static String failureKind(Throwable t) {
        if (t == null) {
            return FAILED;
        }
        // Walk the cause chain: a wrapped assertion is still an assertion failure.
        //
        // DEPTH-BOUNDED rather than cycle-detecting. Java forbids direct self-causation
        // (initCause throws), so the obvious `getCause() == c` guard defends against the
        // one cycle that cannot happen while missing the one that can: a and b each
        // holding the other as a cause is legal, and would spin here forever -- hanging
        // the reporter at the exact moment a build is already failing.
        int depth = 0;
        for (Throwable c = t; c != null && depth < MAX_CAUSE_DEPTH; c = c.getCause(), depth++) {
            if (c instanceof AssertionError) {
                return FAILED;
            }
            // Timeout is its own wire status and must not be flattened into error: the
            // difference between "this test is broken" and "this test hung" is the whole
            // point of having the status. Measured: @Timeout throws
            // java.util.concurrent.TimeoutException, not a JUnit-specific type.
            if (c instanceof TimeoutException) {
                return TIMEOUT;
            }
        }
        return ERROR;
    }
}
