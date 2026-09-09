package com.qualflare.junit5;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The author-facing API: labels, tags, links, priority, parameters and steps.
 *
 * <p>Reporting works with none of this. Every result, status, duration, error and retry is
 * captured without a single call here -- this only adds the things JUnit has no concept of.
 *
 * <p><b>Nothing in this class can fail your test.</b> No method returns an error, none
 * throws, and every call is inert when no reporter is listening. That is not politeness:
 * a reporter that can break a build is a reporter people rip out.
 *
 * <pre>
 * &#64;Test
 * &#64;ExtendWith(QualflareExtension.class)
 * void checksOut() {
 *     Qualflare.label("feature", "checkout");
 *     Qualflare.tag("smoke");
 *     Qualflare.link("https://example.com/issue/42", Qualflare.ISSUE, "QF-42");
 *
 *     Qualflare.step("add to cart", () -&gt; {
 *         Qualflare.parameter("sku", "widget");
 *     });
 * }
 * </pre>
 */
public final class Qualflare {

    public static final String ISSUE = "issue";
    public static final String TMS = "tms";
    public static final String CUSTOM = "custom";

    public static final String HIGH = "high";
    public static final String MEDIUM = "medium";
    public static final String LOW = "low";

    /** Warn once per JVM, not once per call: a loop would drown the build log. */
    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    private Qualflare() {}

    public static void label(String name, String value) {
        emit(Keys.LABEL, kv(name, value));
    }

    public static void tag(String... tags) {
        if (tags == null) {
            return;
        }
        for (String t : tags) {
            if (t != null && !t.isEmpty()) {
                emit(Keys.TAG, t);
            }
        }
    }

    public static void link(String url) {
        link(url, CUSTOM, null);
    }

    public static void link(String url, String type, String name) {
        if (url == null || url.isEmpty()) {
            return;
        }
        emit(Keys.LINK, str(url) + Keys.SEP + str(type == null ? CUSTOM : type) + Keys.SEP + str(name));
    }

    public static void priority(String priority) {
        emit(Keys.PRIORITY, str(priority));
    }

    public static void description(String text) {
        emit(Keys.DESCRIPTION, str(text));
    }

    public static void parameter(String name, String value) {
        emit(Keys.PARAMETER, kv(name, value));
    }

    /**
     * A masked parameter takes no value at all.
     *
     * <p>{@code masked} is a display hint the server does not act on, so withholding the
     * value here is the only thing that actually keeps a secret out of the report. A
     * signature that cannot accept one cannot leak one.
     */
    public static void maskedParameter(String name) {
        emit(Keys.MASKED_PARAMETER, str(name));
    }

    /** A step whose duration is the real elapsed time around the body. Steps nest. */
    public static void step(String name, Runnable body) {
        if (body == null) {
            return;
        }
        if (current() == null) {
            body.run(); // inert, but the test's own work still has to happen
            return;
        }
        long started = System.nanoTime();
        emit(Keys.STEP_START, str(name));
        try {
            body.run();
        } catch (Throwable t) {
            closeStep(Status.FAILED, System.nanoTime() - started, messageOf(t));
            throw t; // never swallow: turning a failing test green is the worst thing a reporter can do
        }
        closeStep(Status.PASSED, System.nanoTime() - started, "");
    }

    private static void closeStep(String status, long nanos, String error) {
        emit(Keys.STEP_STOP, status + Keys.SEP + nanos + Keys.SEP + str(error));
    }

    // ---- plumbing ---------------------------------------------------------------

    private static ExtensionContext current() {
        return QualflareExtension.current();
    }

    /**
     * Dropped with a warning rather than guessed at. Attaching this to whichever test runs
     * next is silent wrong data, which is worse than no data.
     */
    private static void emit(String key, String value) {
        ExtensionContext ctx = current();
        if (ctx == null) {
            if (WARNED.compareAndSet(false, true)) {
                System.err.println("[qualflare-junit5] metadata call outside a registered test was"
                        + " dropped. Add @ExtendWith(QualflareExtension.class), or set"
                        + " junit.jupiter.extensions.autodetection.enabled=true.");
            }
            return;
        }
        try {
            ctx.publishReportEntry(Collections.singletonMap(key, value));
        } catch (RuntimeException ignored) {
            // Fire and forget. A metadata problem must never fail somebody's run.
        }
    }

    private static String kv(String name, String value) {
        return str(name) + Keys.SEP + str(value);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }
}
