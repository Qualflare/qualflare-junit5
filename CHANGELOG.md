# Changelog

## Unreleased

- Corrected a claim in the docs. The reporter's notes said the JUnit Platform constructs a
  new listener instance per `TestPlan`. It does not. What actually happened was that Maven
  Surefire up to 3.5.3 opened a new launcher session per re-run, and a new session means a
  new `Launcher` and a freshly loaded listener. Surefire 3.5.4 fixed the session scoping:
  from that version one session covers all re-runs and one listener instance sees every
  plan. Run state stays JVM-scoped, because 3.5.3 and earlier are still in use.
- CI now runs the integration fixture on both sides of that change, 3.5.3 and 3.5.6, and
  the fixture's own Surefire is no longer pinned to 3.5.2.
- The compatibility matrix's JUnit versions now reach the assertions in `verify.py`. That
  script re-ran Maven itself with the fixture's default JUnit, so the 5.13 and 6.x legs had
  only been proving that the fixture compiled.

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
