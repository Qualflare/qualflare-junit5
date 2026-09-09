package com.qualflare.junit5;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * The reporter. Registered by ServiceLoader through
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}, so a user
 * adds the dependency and nothing else -- no build-tool configuration at all.
 *
 * <p>Makes no network calls. It writes a report directory that {@code qualflare-cli}
 * uploads, which is what lets any number of forked JVMs or sharded CI jobs merge into a
 * single Launch.
 */
public final class QualflareListener implements TestExecutionListener {

    /**
     * JVM-scoped, NOT an instance field. The Platform constructs a fresh listener for
     * every TestPlan, and Surefire runs a plan per rerun -- instance state would hand
     * each retry an empty accumulator. See {@link Run}.
     */
    private static Accumulator acc() {
        return Run.accumulator();
    }

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {
        // The hook, not testPlanExecutionFinished, is what writes: that callback fires
        // once per plan, and Surefire runs one plan per rerun.
        Run.ensureHook();
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (!id.isTest()) {
            return;
        }
        acc().started(id.getUniqueId(), System.nanoTime());
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        long now = System.nanoTime();

        if (id.isTest()) {
            Throwable t = result.getThrowable().orElse(null);
            acc().finished(id.getUniqueId(), suiteOf(id), classOf(id),
                    id.getDisplayName(), id.getLegacyReportingName(),
                    Status.of(result), now, messageOf(t), traceOf(t));
            return;
        }

        // THE BACKSTOP. A container that fails -- @BeforeAll throwing, a class that will
        // not initialise -- emits NO events for the tests it guarded. Not a failure, not
        // a skip: they vanish. Measured: a class with two tests and a throwing @BeforeAll
        // produced zero test events and one container FAILED.
        //
        // Without this, that suite reports zero cases and zero failures -- a green launch
        // for a run that never happened. The same shape as the Go reporter's pre-1.24
        // build-failure hole, and it gets the same answer: synthesise a case so the
        // failure is impossible to miss.
        if (result.getStatus() == TestExecutionResult.Status.FAILED) {
            Throwable t = result.getThrowable().orElse(null);
            String container = id.getDisplayName();
            acc().finished(id.getUniqueId() + "/[qf-container-failure]",
                    suiteOf(id), classOf(id),
                    container + " [container failure]", container,
                    Status.ERROR, now, messageOf(t), traceOf(t));
        }
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        if (!id.isTest()) {
            return;
        }
        acc().skipped(id.getUniqueId(), suiteOf(id), classOf(id),
                id.getDisplayName(), id.getLegacyReportingName(), reason);
    }

    /** Package-private so tests can drive a write without waiting for JVM shutdown. */
    void write() {
        Run.write();
    }

    // ---- identity ---------------------------------------------------------------

    /**
     * The fully-qualified class name, which is the stable half of a case's identity.
     * A nested class arrives as {@code Outer$Inner}, which is what we want: it is a
     * different class and its tests are different cases.
     */
    private static String classOf(TestIdentifier id) {
        return id.getSource().map(s -> {
            if (s instanceof MethodSource) {
                return ((MethodSource) s).getClassName();
            }
            if (s instanceof ClassSource) {
                return ((ClassSource) s).getClassName();
            }
            return "";
        }).orElse("");
    }

    /** One suite per test class, matching how every other reporter in the family groups. */
    private static String suiteOf(TestIdentifier id) {
        String cls = classOf(id);
        return cls.isEmpty() ? "JUnit" : cls;
    }

    private static String messageOf(Throwable t) {
        if (t == null) {
            return "";
        }
        String m = t.getMessage();
        return m == null ? t.getClass().getName() : t.getClass().getSimpleName() + ": " + m;
    }

    private static String traceOf(Throwable t) {
        if (t == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
