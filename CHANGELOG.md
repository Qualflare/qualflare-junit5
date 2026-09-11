# Changelog

## Unreleased

First release of `qualflare-junit5` — a native JUnit Platform reporter for Qualflare.

Captures what Surefire's JUnit XML cannot: per-attempt retry history with each attempt's
own status, duration and error; nested steps; attachments; and author-facing metadata.
Registers itself through `ServiceLoader`, so adding the dependency is the whole setup.

Two behaviours worth knowing about, both found by measurement rather than by reading docs:

- **A container failure no longer reads as green.** When `@BeforeAll` throws, the tests it
  guarded emit no events at all — not a failure, not a skip. A reporter listening only for
  test events would write zero cases and zero failures for a suite that never ran. A
  synthetic `[container failure]` case is emitted instead.
- **Reruns accumulate onto one case.** The JUnit Platform constructs a new listener per
  `TestPlan` and Surefire runs a plan per rerun, so run state is JVM-scoped rather than
  per-instance. Verified against Surefire 3.5.2 with `rerunFailingTestsCount=3`:
  `attempts = [failed, failed, passed]`, `isFlaky = true`.

Requires JUnit Platform 1.12+ / Jupiter 5.12+ and Java 11+. Zero runtime dependencies.
