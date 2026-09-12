# qualflare-junit5

[![Maven Central](https://img.shields.io/maven-central/v/com.qualflare/qualflare-junit5.svg)](https://central.sonatype.com/artifact/com.qualflare/qualflare-junit5)
[![CI](https://github.com/Qualflare/qualflare-junit5/actions/workflows/ci.yml/badge.svg)](https://github.com/Qualflare/qualflare-junit5/actions/workflows/ci.yml)
[![Qualflare](https://api.qualflare.com/p/qualflare-junit5/badge.svg)](https://reports.qualflare.com/p/qualflare-junit5/launches)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

A native JUnit Platform reporter for [Qualflare](https://qualflare.com) — captures results
directly from your test run: status, per-attempt retry history and flakiness, nested steps,
attachments, and author-facing metadata (labels, links, tags, priority, custom parameters).

Without it, JVM results reach Qualflare through Surefire's JUnit XML, which carries
pass/fail, a duration and a class name. No per-attempt history, no attachments, no
metadata — and no way to say "aborted", so a failed assumption and a deliberate skip are
indistinguishable in the file.

The reporter makes **no network calls**. It writes a report directory, and
[`qualflare-cli`](https://github.com/Qualflare/qualflare-cli) uploads it — which is what
lets any number of forked JVMs or sharded CI jobs merge into a single Launch.

## Install

```xml
<dependency>
  <groupId>com.qualflare</groupId>
  <artifactId>qualflare-junit5</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

```kotlin
testImplementation("com.qualflare:qualflare-junit5:0.1.0")
```

That is the whole setup. The reporter registers itself through the JUnit Platform's
`ServiceLoader` mechanism, so there is no build-tool configuration to add and no runner to
swap.

Requires **JUnit Platform 1.12+ / Jupiter 5.12+** and **Java 11+**. The floor is 1.12
because `fileEntryPublished`, the callback attachments arrive on, does not exist before it
— checked with `javap` against 1.11.3, 1.12.2, 1.13.4 and 6.1.3. The signature and the
`FileEntry` package are the same from 1.12 through 6.x, so one build serves both
generations.

**Zero runtime dependencies.** The JSON is hand-rolled. A test reporter sits on everyone's
test classpath, and putting a second copy of Jackson in front of whatever the project
already uses is a real source of breakage.

## Quickstart

```bash
mvn test
npm install -g @qualflare/cli
qf login my-project "$QUALFLARE_TOKEN" --force
qf my-project collect ./qualflare-results
```

`qualflare-cli` is a standalone Go binary, not a Java artifact — it is not on Maven
Central. Homebrew and npm are the two channels; the release page also carries plain
binaries for every platform.

## JUnit 4 comes too

The listener registers at Launcher level, so it sees every engine on the platform. That
includes `junit-vintage`, which runs JUnit 3 and 4 tests — no migration required to get
them reported. Kotlin, Scala and Groovy suites arrive the same way, as do Spring Boot and
Testcontainers, since all of them run on the JUnit Platform.

## Retries and flakiness

JUnit has no built-in retry, and `@RepeatedTest` is not one — it produces separate cases,
not attempts. Real reruns come from the build tool, and they are captured in full.

Measured on Maven Surefire 3.5.6 with `rerunFailingTestsCount=3`, on a test that fails
twice then passes:

```
attempts = [failed, failed, passed]    isFlaky = true    retryCount = 2
```

Surefire re-executes a failed test in a **new TestPlan but the same JVM**, and the
`uniqueId` is the same across those plans, which is what makes the attempt sequence
reconstructible. A test that fails every attempt is reported as **failed, not flaky**.

Surefire 3.5.4 changed how sessions are scoped around those re-runs: before it, each
re-run opened its own launcher session. The reporter handles both, and CI runs its
integration fixture on either side of that line.

## Forked and sharded runs

`forkCount > 1` and `reuseForks=false` need no configuration. Each JVM writes its own
uniquely-named file into the same `outputDir`, and `qf collect` merges every file in the
directory into one Launch. That is the same directory-merge model `pytest-xdist` and
Vitest `--shard` already use.

`junit.jupiter.execution.parallel.enabled` is supported too. The package's own integration
suite runs the fixture serially and in parallel and asserts the two reports match.

**Gradle's `test-retry` plugin is the exception.** Gradle forks a fresh JVM per retry, so
each attempt writes its own file as a single-attempt case and no per-attempt history
survives — measured on Gradle 9.7.1 with test-retry 1.6.2. Surefire re-runs in the same JVM
and does not have this problem. Flaky
detection still works either way, because Qualflare scores it from history across launches
rather than from in-run retries. See [`docs/LIMITATIONS.md`](./docs/LIMITATIONS.md).

## Enriching your tests

```java
import com.qualflare.junit5.Qualflare;
import com.qualflare.junit5.QualflareExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(QualflareExtension.class)
class CheckoutTest {

    @Test
    void checksOut() {
        Qualflare.label("feature", "checkout");
        Qualflare.tag("smoke");
        Qualflare.link("https://example.com/issue/42", Qualflare.ISSUE, "QF-42");
        Qualflare.priority(Qualflare.HIGH);

        Qualflare.step("add to cart", () -> {
            Qualflare.parameter("sku", "widget");
            Qualflare.maskedParameter("token");
        });
    }
}
```

To skip the annotation entirely, turn on extension auto-detection once:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

**The metadata API is optional.** Without it every result, status, duration, error and
retry is still reported in full; only the metadata needs a test to attach to. Calls made
outside a registered test are dropped with a warning rather than guessed at, because
attaching them to whichever test runs next would be wrong data that looks right.

**Nothing in the API can fail your test.** No method returns an error, none throws, and
calls are inert when no reporter is listening.

`Qualflare.maskedParameter` takes no value at all. `masked` is only a display hint that
the server does not act on, so withholding the value here is what actually keeps a secret
out of the report.

Full reference in [`docs/METADATA-API.md`](./docs/METADATA-API.md).

## Attachments

Anything published through `TestReporter.publishFile` is captured. png/jpeg/gif are copied
into `outputDir` and referenced by name, so a screenshot never competes for the run's
inline budget; everything else is base64-inlined against an 8 MB budget charged in
**encoded** bytes, because base64 inflates by roughly 4/3 and what matters is what travels.

Needs `@qualflare/cli` **v0.1.24 or newer**, the first release that reads
`localImagePath`. On an older CLI that field is ignored, and because such an attachment
carries neither content nor a storage key the server records it from its name alone — an
undownloadable placeholder. Upgrade the CLI before this reporter.

One wrinkle that is not ours but will bite you: **import
`org.junit.jupiter.api.extension.MediaType`**, not `org.junit.jupiter.api.MediaType`.

Checked inside both jars: 5.12.2 ships only the `extension` one, and 6.1.3 ships **both**.
So the `extension` location is the portable import — the shorter one compiles on 6.x and
fails on 5.x, which is a confusing error to hit when your build works locally and not in
CI. This affects the import in your test, not this listener, which reads
`org.junit.platform.engine.reporting.FileEntry` — identical in both.

## Configuration

Precedence, highest first: **system property → `QUALFLARE_*` environment → default**. The
same order the other reporters document, so one mental model covers every package. Every
option is in [`docs/CONFIGURATION.md`](./docs/CONFIGURATION.md).

There is deliberately no config file and no token option. The reporter makes no network
calls, so it has no credential; `qf login` holds it.

## Known limitations

- **A container failure is reported as one synthetic case, not as the tests it stopped.**
  When `@BeforeAll` throws, the tests it guarded emit no events at all — not a failure, not
  a skip, they simply never appear. The reporter emits a `[container failure]` case so the
  run cannot read as green, but it cannot know how many tests were lost or what they were
  called.
- **Metadata comes from the final attempt only.** Attempts carry their own status,
  duration and error, but steps, labels, tags and parameters come from the last one.
  An abandoned attempt's step trace is discarded rather than replayed alongside it.
- **`@RepeatedTest` and `@ParameterizedTest` rows are separate cases, not attempts.**
  That is what JUnit reports and it is usually what you want — per-row history is the point
  — but it means a 200-row parameterized test is 200 rows in the report.
- **Steps are explicit only.** JUnit has no step concept, so nothing appears unless
  `Qualflare.step` is called. A unit test will show none, which is correct: steps are an
  E2E and BDD idiom.

Full details in [`docs/LIMITATIONS.md`](./docs/LIMITATIONS.md).

## Test reports

This reporter is tested with itself. `e2e/` is a JUnit suite covering this package's own
behaviour — the metadata API, nested steps, attachments, parameterized rows as cases and
nested classes as their own suite — run by this reporter and uploaded to Qualflare on every
merge to `main` by the **published** `qualflare-cli`. The results below are that suite's,
reported through the code this README documents:

[![Qualflare](https://api.qualflare.com/p/qualflare-junit5/banner.svg)](https://reports.qualflare.com/p/qualflare-junit5/launches)

Every case there is meant to pass, so a red run is a real regression rather than a fixture
failing on purpose. The deliberately awkward cases — a throwing `@BeforeAll`, a test that
never recovers, a run that blows past the step cap — live in `test/integration/fixture`,
which is never uploaded.

## Development

```bash
mvn test                                # unit
mvn -q install -DskipTests              # publish locally for the fixture and the dogfood
python3 test/integration/verify.py      # runs the fixture serial AND parallel, then compares

# the dogfood, then the check that runs before any upload
cd e2e && mvn -q test -Dqualflare.version=0.1.0
QUALFLARE_OUTPUT_DIR=qualflare-results python3 verify.py
```

`test/integration/fixture` is a separate Maven project of deliberately difficult cases: a
class whose `@BeforeAll` throws, a test that fails twice then passes, an assumption
failure, an error that is not an assertion, and a test that blows past the 300-step cap.
It is **never uploaded anywhere** — exercising a step cap needs 300+ steps, and a report
people read should not be three hundred steps named `filler`.

## Related reporters

Qualflare has a native reporter for each framework, all writing the same report format so
one `qf collect` handles a polyglot repository:

[jest](https://github.com/Qualflare/qualflare-jest) ·
[vitest](https://github.com/Qualflare/qualflare-vitest) ·
[mocha](https://github.com/Qualflare/qualflare-mocha) ·
[cypress](https://github.com/Qualflare/qualflare-cypress) ·
[playwright](https://github.com/Qualflare/qualflare-playwright) ·
[cucumberjs](https://github.com/Qualflare/qualflare-cucumberjs) ·
[pytest](https://github.com/Qualflare/qualflare-pytest) ·
[go](https://github.com/Qualflare/qualflare-go) ·
[cli](https://github.com/Qualflare/qualflare-cli)

## License

Apache-2.0 — see [LICENSE](./LICENSE).
