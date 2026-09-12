package com.qualflare.junit5;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Writes the report when a launcher session closes.
 *
 * <p>This is the hook the Launcher API offers for "no more tests will be discovered or
 * executed", which is what the reporter needs. A JVM shutdown hook has no ordering
 * guarantees and is skipped entirely on a hard kill.
 *
 * <p>A session is not necessarily once per JVM. The documentation says it "usually
 * corresponds to the lifecycle of the test JVM". Up to Maven Surefire 3.5.3 it did not:
 * with {@code rerunFailingTestsCount=3} that version opened three sessions in one JVM,
 * one per re-run. Surefire 3.5.4 fixed this and now opens one. The console launcher always
 * opened one, so this was Surefire's session scoping rather than a JUnit defect.
 *
 * <p>So this listener holds no state and decides nothing: it asks {@link Run} to write
 * what has accumulated so far. Each write rewrites the same file, so on an older Surefire
 * the intermediate writes are simply earlier snapshots of the last one.
 *
 * <p>The shutdown hook stays as a fallback for tools that build a {@code Launcher}
 * directly instead of through {@code LauncherFactory.openSession()}, which never open a
 * session at all.
 */
public final class QualflareSessionListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        // Nothing to do. The session can open before any test is seen, and the hook is
        // cheap to install lazily.
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        Run.write();
    }
}
