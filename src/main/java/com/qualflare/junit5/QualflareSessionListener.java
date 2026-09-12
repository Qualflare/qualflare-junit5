package com.qualflare.junit5;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Writes the report when a launcher session closes.
 *
 * <p>Suggested on the JUnit extensions issue by a maintainer, and it is the right hook:
 * {@code launcherSessionClosed} is the API's own "no more tests will be discovered or
 * executed" signal, whereas a JVM shutdown hook is a blunt instrument with no ordering
 * guarantees that a hard kill skips entirely.
 *
 * <p><b>It is not, however, a once-per-JVM signal.</b> The documentation says a session
 * "usually corresponds to the lifecycle of the test JVM", and "usually" is doing real
 * work there. Measured on Maven Surefire 3.5.2 with {@code rerunFailingTestsCount=3}:
 *
 * <pre>
 * SESSION OPENED  pid=91672  instance=759156157
 * SESSION CLOSED  pid=91672  instance=759156157
 * SESSION OPENED  pid=91672  instance=1008315045   &lt;- rerun 1: a NEW session
 * SESSION CLOSED  pid=91672  instance=1008315045
 * SESSION OPENED  pid=91672  instance=1280851663   &lt;- rerun 2: another
 * </pre>
 *
 * <p>Three sessions, one JVM. The console launcher, by contrast, opens exactly one for
 * one plan -- so this is Surefire's session scoping rather than a JUnit defect.
 *
 * <p>That is why this listener does not own any state and does not decide anything: it
 * asks {@link Run} to write what has accumulated so far. Each write rewrites the same
 * file, so the last one is complete and the intermediate ones are simply earlier
 * snapshots of it. Holding run state per session -- the obvious reading of the docs --
 * would produce three partial reports under exactly this configuration.
 *
 * <p>The shutdown hook stays as a fallback. A tool that builds a {@code Launcher}
 * directly rather than through {@code LauncherFactory.openSession()} never opens a
 * session at all, and without the hook such a run would report nothing.
 */
public final class QualflareSessionListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        // Nothing to do. Registering the hook here would be tempting, but the session
        // may open before any test is seen and the hook is cheap to install lazily.
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        Run.write();
    }
}
