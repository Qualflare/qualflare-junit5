import org.junit.jupiter.api.*;
import java.nio.file.*;

/** Deliberately awkward cases. Never uploaded anywhere. */
class FixtureTest {
    @Test void passes() {}

    @Test void failsHard() { Assertions.fail("a real assertion failure"); }

    @Test void errors() { throw new IllegalStateException("not an assertion"); }

    @Test @Disabled("deliberately disabled") void disabled() {}

    @Test void assumptionNotMet() { Assumptions.assumeTrue(false, "precondition absent"); }

    /** Fails twice then passes -- counter on disk so it survives a forked JVM. */
    @Test void flakyRecovers() throws Exception {
        Path p = Paths.get(System.getProperty("java.io.tmpdir"), "qf-junit5-fixture-counter");
        int n = Files.exists(p) ? Integer.parseInt(Files.readString(p).trim()) : 0;
        Files.writeString(p, String.valueOf(n + 1));
        if (n < 2) Assertions.fail("flaky failure on attempt " + (n + 1));
    }
}

/** The trap: two tests that emit no events at all. */
class ContainerBlowsUpTest {
    @BeforeAll static void boom() { throw new IllegalStateException("container setup failed"); }
    @Test void neverRuns() {}
    @Test void alsoNeverRuns() {}
}
