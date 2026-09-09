package com.qualflare.junit5;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Holds the running test's context so {@link Qualflare} can be called statically.
 *
 * <p>Without this you would have to inject {@code TestReporter} into every test signature
 * that wants a label. JUnit offers no ambient handle on the current test -- there is no
 * equivalent of Jest's {@code expect.getState()} or Vitest's {@code task.meta} -- so the
 * context has to be stashed somewhere the static API can reach.
 *
 * <p>The stash is a {@link ThreadLocal} and that is load-bearing, not incidental: under
 * {@code junit.jupiter.execution.parallel.enabled} several tests run at once on
 * ForkJoinPool workers, and a shared field would attribute one test's labels to another.
 * Wrong metadata is worse than absent metadata, because it looks right.
 *
 * <p>Register it either way:
 * <pre>
 *   &#64;ExtendWith(QualflareExtension.class)     // explicit, per class
 *   junit.jupiter.extensions.autodetection.enabled=true   // once, then nothing per class
 * </pre>
 */
public final class QualflareExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ThreadLocal<ExtensionContext> CURRENT = new ThreadLocal<>();

    @Override
    public void beforeEach(ExtensionContext context) {
        CURRENT.set(context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // remove(), not set(null): a ForkJoinPool worker is reused for the next test, and
        // a stale entry would silently attach this test's context to that one.
        CURRENT.remove();
    }

    static ExtensionContext current() {
        return CURRENT.get();
    }
}
