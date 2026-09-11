import com.qualflare.junit5.Qualflare;
import com.qualflare.junit5.QualflareExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.MediaType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;

/**
 * The suite this reporter reports on itself, uploaded to a PUBLIC Qualflare project.
 *
 * <p><b>Every case here is meant to pass</b>, so a red run is a real regression rather than
 * a fixture failing on purpose. The deliberately awkward cases -- a throwing {@code
 * @BeforeAll}, a test that never recovers, a run that blows past the step cap -- live in
 * {@code test/integration/fixture}, which is never uploaded.
 *
 * <p>That separation is not fastidiousness. The Go reporter's dogfood suite once shipped
 * 320 steps named "filler" to a report people actually read, because the step-cap
 * regression test was put in the uploaded suite. Names here are chosen to mean something
 * to a reader who has never seen this code.
 */
@ExtendWith(QualflareExtension.class)
class DogfoodTest {

    @Test
    void recordsEveryMetadataKind() {
        Qualflare.label("team", "platform");
        Qualflare.label("feature", "reporting");
        Qualflare.tag("smoke", "dogfood");
        Qualflare.link("https://github.com/Qualflare/qualflare-junit5", Qualflare.ISSUE, "QJ-1");
        Qualflare.priority(Qualflare.HIGH);
        Qualflare.description("exercises every metadata call the README documents");
        Qualflare.parameter("plan", "pro");
        Qualflare.maskedParameter("token");
    }

    @Test
    void nestsSteps() {
        Qualflare.step("add to cart", () -> {
            Qualflare.parameter("sku", "widget");
            Qualflare.step("set quantity", () -> Qualflare.parameter("qty", "2"));
        });
    }

    /** A masked parameter emitted where the secret is genuinely in scope. */
    @Test
    void aMaskedParameterInsideAStep() {
        Qualflare.step("authenticate", () -> Qualflare.maskedParameter("api-token"));
    }

    @Test
    void attachesContent(TestReporter reporter) {
        reporter.publishFile("note.txt", MediaType.TEXT_PLAIN,
                f -> Files.writeString(f, "a text attachment from the dogfood suite"));
    }

    /** Each row is its own case, with the value in the name. */
    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    void parameterizedRowsAreTheirOwnCases(String browser) {
        Qualflare.label("browser", browser);
    }

    /**
     * A report entry the reporter must NOT consume. Java suites publish their own entries
     * for their own reasons, and swallowing them would be taking something that is not ours.
     */
    @Test
    void entriesOutsideOurNamespacePassThrough(TestReporter reporter) {
        reporter.publishEntry("app.buildId", "not-a-qualflare-key");
        Qualflare.label("after", "the-decoy");
    }

    @Nested
    class NestedClassesAreTheirOwnSuite {
        @Test
        void reportsFromANestedClass() {
            Qualflare.tag("nested");
        }
    }
}
