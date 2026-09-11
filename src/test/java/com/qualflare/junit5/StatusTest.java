package com.qualflare.junit5;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The failed/error/timeout split.
 *
 * <p>This exists because the first implementation matched a list of exact class names and
 * got JUnit 4 wrong: {@code org.junit.ComparisonFailure} extends {@code AssertionError}
 * but was in no list, so every Vintage assertion failure was reported as an error -- in a
 * reporter that advertises JUnit 4 support. These assert the TYPE-based rule that replaced
 * it, including with a stand-in for a third-party assertion class the list could not have
 * known about.
 */
class StatusTest {

    /** Stands in for JUnit 4's ComparisonFailure, AssertJ, Truth, Hamcrest, TestNG... */
    private static final class SomeLibrarysAssertionError extends AssertionError {
        SomeLibrarysAssertionError() {
            super("from a library this code has never heard of");
        }
    }

    private static String of(Throwable t) {
        return Status.of(TestExecutionResult.failed(t));
    }

    @Test
    void an_assertion_failure_is_failed_whatever_library_threw_it() {
        assertEquals(Status.FAILED, of(new AssertionError("bare")));
        assertEquals(Status.FAILED, of(new SomeLibrarysAssertionError()));
        assertEquals(Status.FAILED, of(new org.opentest4j.AssertionFailedError("junit5")));
    }

    @Test
    void anything_else_thrown_is_an_error() {
        // A hundred of these usually means one broken fixture; a hundred failures means a
        // hundred broken expectations. The split is what makes triage possible.
        assertEquals(Status.ERROR, of(new IllegalStateException("not an assertion")));
        assertEquals(Status.ERROR, of(new NullPointerException()));
    }

    @Test
    void a_timeout_keeps_its_own_status() {
        // Measured: @Timeout throws java.util.concurrent.TimeoutException, not a
        // JUnit-specific type. Flattening it into error would lose the difference between
        // "this test is broken" and "this test hung".
        assertEquals(Status.TIMEOUT, of(new TimeoutException("timed out")));
    }

    @Test
    void a_wrapped_assertion_is_still_an_assertion() {
        assertEquals(Status.FAILED,
                of(new RuntimeException("wrapper", new AssertionError("inner"))));
    }

    @Test
    void a_cyclic_cause_chain_terminates() {
        // Java forbids DIRECT self-causation -- initCause throws -- so the cycle worth
        // defending against is the two-element one, which is perfectly legal to build.
        // An unbounded walk here would hang the reporter while a build is already failing.
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertEquals(Status.ERROR, of(a));
    }

    @Test
    void a_cycle_containing_an_assertion_is_still_found() {
        RuntimeException a = new RuntimeException("a");
        AssertionError boom = new AssertionError("inner");
        RuntimeException b = new RuntimeException("b", boom);
        a.initCause(b);
        assertEquals(Status.FAILED, of(a));
    }

    @Test
    void aborted_maps_to_skipped_not_aborted() {
        // A failed assumption is a test declining to run. The wire's "aborted" means a run
        // that was killed, which would paint healthy suites red.
        assertEquals(Status.SKIPPED, Status.of(TestExecutionResult.aborted(
                new org.opentest4j.TestAbortedException("assumption not met"))));
    }

    @Test
    void successful_is_passed() {
        assertEquals(Status.PASSED, Status.of(TestExecutionResult.successful()));
    }

    @Test
    void a_failure_with_no_throwable_is_failed_not_error() {
        assertEquals(Status.FAILED, Status.of(TestExecutionResult.failed(null)));
    }
}
