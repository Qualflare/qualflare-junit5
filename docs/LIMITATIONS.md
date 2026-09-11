# Limitations

Every one of these was measured, not inferred. Where a version number appears, that is the
version it was observed on.

## A container failure loses the tests it stopped

When `@BeforeAll` throws, the tests it guarded emit **no events whatsoever** — not a
failure, not a skip. The failure surfaces only at container level:

```
CONT BeforeAllBlowsUp -> FAILED (IllegalStateException: container setup failed)
...and zero TEST events. Launcher exit code: 1
```

A reporter filtering on `id.isTest()` would write zero cases and zero failures for a suite
that never ran — a green launch for a broken build. This reporter emits a synthetic
`[container failure]` case instead, so the run cannot read as green.

**What it cannot do** is tell you how many tests were lost or what they were called. That
information does not exist in the stream. The class name is in the case name; the count is
not recoverable.

Same shape as the hole `qualflare-go` has for Go 1.21/1.23 builds, where a compile failure
produces no `fail` event at all.

## Metadata comes from the final attempt only

Under a rerun, `Case.attempts` records each attempt's status, duration and error — but
steps, labels, tags, priority, parameters and attachments come from the **last** attempt.
An abandoned attempt's step trace is discarded rather than replayed alongside the final
one, because showing both would present a step tree that never existed in that shape.

The same rule the CucumberJS reporter documents.

## `@RepeatedTest` and `@ParameterizedTest` produce cases, not attempts

Each invocation arrives with its own `uniqueId`, so each is its own case:

```
name=[1] "chrome"    uid=.../[test-template:parameterized(String)]/[test-template-invocation:#1]
name=[2] "firefox"   uid=.../[test-template:parameterized(String)]/[test-template-invocation:#2]
```

That is usually what you want — per-row history is the point, and a flaky *row* is visible
rather than averaged into its parent — but it means a 200-row parameterized test is 200
rows in the report. Only a build-tool rerun produces attempts.

## Steps are explicit only

JUnit has no step concept, so nothing appears unless `Qualflare.step` is called. A unit
test shows none, which is correct: steps are an E2E and BDD idiom, and wrapping an
arrange-act-assert unit test in them is ceremony.

Past **300 steps per attempt** the rest are dropped and a warning is recorded. The cap is
the client's; the server's own is 1000. Stopping lower keeps one pathological test from
crowding out the rest of the report.

## Gradle `test-retry` loses per-attempt history

Measured against Gradle 9.7.1 with `org.gradle.test-retry` 1.6.2, `maxRetries = 3`, and the
published `0.1.0` artifact. A test that fails twice then passes produced **three report
files from three different JVMs**:

```
pid 98369  ->  flakyRecovers() failed    passes() passed
pid 98370  ->  flakyRecovers() failed
pid 98371  ->  flakyRecovers() passed
```

Every one of those is a **single-attempt** case. No `attempts` array is written anywhere,
because one attempt is below the wire's floor of two, and `isFlaky` is false in all three
because no JVM ever saw more than one attempt.

**Gradle forks a fresh JVM per retry.** That is the whole difference from Surefire, which
reruns in the *same* JVM as a new `TestPlan`, which is what lets the accumulator join the
attempts into one case. Nothing on the reporter's side can bridge separate processes.

The same case id therefore appears several times in one collected launch, once per attempt.
What the server does with those duplicates is **not covered by this measurement**; only the
client side was tested.

### What to do instead

Nothing, in most cases. **Qualflare scores flakiness from a test's pass/fail record across
launches**, which needs no in-run retries at all and is the more accurate method anyway: an
in-run retry only ever fires for a test that happened to flake while you were watching. A
Gradle suite still gets flaky detection; it just comes from history rather than from the
retry plugin.

If you specifically want per-attempt detail in the report, Surefire's
`rerunFailingTestsCount` provides it and Gradle's plugin cannot.

## The metadata API needs an extension

`Qualflare.label` and friends need the running test's context. JUnit exposes no ambient
handle on it — there is no equivalent of Jest's `expect.getState()` or Vitest's
`task.meta` — so `QualflareExtension` has to be registered, by annotation or by
auto-detection.

Without it, results are still reported in full and metadata calls are **dropped with a
warning** rather than attached to whichever test happens to be running.

## `MediaType` has two locations

Import `org.junit.jupiter.api.extension.MediaType`. Verified by listing both jars: 5.12.2
contains only that one, while 6.1.3 contains it **and** `org.junit.jupiter.api.MediaType`.
The shorter import therefore compiles on 6.x and fails on 5.x.

Not a limitation of this reporter — it reads `org.junit.platform.engine.reporting.FileEntry`,
which is identical across both — but it is the error people will bring here first.

## Attachments need CLI v0.1.24+

Images are referenced by `localImagePath` rather than inlined. An older CLI ignores that
field, and because such an attachment carries neither content nor a storage key the server
records it from its name alone — an undownloadable placeholder. Upgrade the CLI first.

## Suite category is always `unit`

The JUnit Platform runs unit, integration and E2E suites alike and cannot tell them apart.
Guessing from the runner would mislabel a Selenium suite driven by JUnit as a unit test,
so the reporter does not guess.
