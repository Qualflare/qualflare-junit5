# Changelog

## 0.2.0

### Fixed

- **Every report claimed version `0.0.0-dev`, including the published 0.1.0.** The jar
  manifest carried no `Implementation-Version`, so the fallback shipped on every run. If you
  are looking at reports produced by 0.1.0, that field is wrong in all of them.
- **JUnit 4 assertion failures were reported as `error` rather than `failed`.** Status was
  decided from a list of exact exception class names, and `org.junit.ComparisonFailure`
  extends `AssertionError` but was not on it — so every Vintage assertion failure was
  mislabelled. Status is now decided by type (`instanceof AssertionError`), which also
  covers AssertJ, Truth, Hamcrest and TestNG.
- **Truncated steps are now reported.** When a case exceeded the 300-step cap the warning
  was recorded and never read, so the report just quietly held fewer steps than the test
  produced.

### Added

- **The report is written when the launcher session closes**, not only from a JVM shutdown
  hook. `launcherSessionClosed` is the Launcher API's own "no more tests will be discovered
  or executed" signal; a shutdown hook has no ordering guarantees and is skipped entirely on
  a hard kill. The hook stays as a fallback for tools that build a `Launcher` without ever
  opening a session.

### Documentation

- **Corrected why run state is JVM-scoped.** Earlier notes said the JUnit Platform
  constructs a new listener instance per `TestPlan`. It does not. Maven Surefire up to 3.5.3
  opened a new launcher session per re-run, and a new session means a new `Launcher` and a
  freshly loaded listener. Surefire 3.5.4 fixed that: from there one session covers all
  re-runs and one listener instance sees every plan. The state stays JVM-scoped because
  3.5.3 and earlier are still in use, and because `forkCount > 1` and session-less tools
  need it regardless.
- **Gradle `test-retry` loses per-attempt history, and this is now measured rather than
  hedged.** Gradle forks a fresh JVM per retry, so each attempt lands in its own file as a
  single-attempt case. Measured on Gradle 9.7.1 with test-retry 1.6.2 against the published
  0.1.0: three files, three pids, no `attempts` array anywhere. Surefire re-runs in the same
  JVM and does not have this problem.

### Testing

- CI runs the integration fixture against Surefire 3.5.3 and 3.5.6, one either side of the
  3.5.4 session change, and against JUnit 5.12.2 (the compile floor), 5.14.4 and 6.1.3.
- Fixed a hole in that matrix: `verify.py` re-ran Maven with the fixture's default JUnit, so
  the non-floor legs had only been proving the fixture compiled.
- A dogfood suite in `e2e/` now reports on the reporter itself and uploads on every merge to
  `main`, which is what the README badge reads.

## 0.1.0

First release of `qualflare-junit5` — a native JUnit Platform reporter for Qualflare.

Captures what Surefire's JUnit XML cannot: per-attempt retry history with each attempt's
own status, duration and error; nested steps; attachments; and author-facing metadata.
Registers itself through `ServiceLoader`, so adding the dependency is the whole setup.

Two behaviours to know about, both found by measuring rather than reading docs:

- **A container failure no longer reads as green.** When `@BeforeAll` throws, the tests it
  guarded emit no events at all — not a failure, not a skip. A reporter listening only for
  test events would write zero cases and zero failures for a suite that never ran. A
  synthetic `[container failure]` case is emitted instead.
- **Re-runs accumulate onto one case.** Surefire runs each re-run as a separate
  `TestPlan`, and up to Surefire 3.5.3 also gave each one its own launcher session and so
  its own listener instance. Run state is therefore JVM-scoped rather than held in listener
  fields. Verified with `rerunFailingTestsCount=3`:
  `attempts = [failed, failed, passed]`, `isFlaky = true`.

Requires JUnit Platform 1.12+ / Jupiter 5.12+ and Java 11+. Zero runtime dependencies.
