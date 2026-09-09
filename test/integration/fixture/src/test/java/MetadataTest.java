import com.qualflare.junit5.Qualflare;
import com.qualflare.junit5.QualflareExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Exercises the author-facing API. Never uploaded anywhere. */
@ExtendWith(QualflareExtension.class)
class MetadataTest {

    @Test
    void recordsEveryMetadataKind() {
        Qualflare.label("team", "platform");
        Qualflare.label("feature", "reporting");
        Qualflare.tag("smoke", "fixture");
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

    @Test
    void aFailingStepIsRecordedAndStillPropagates() {
        try {
            Qualflare.step("explodes", () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // The step must record the failure AND let it through. Swallowing it would
            // turn a failing test green, which is the worst thing a reporter can do.
        }
    }

    /**
     * The step cap regression. An outer step wraps a measured sleep and overruns the
     * 300-step cap inside it; without the sentinel the dropped steps' stops close the
     * outer step and its real duration is lost.
     */
    @Test
    void outerStepSurvivesTheStepCap() {
        Qualflare.step("wraps-a-measured-sleep", () -> {
            for (int i = 0; i < 320; i++) {
                Qualflare.step("filler", () -> {});
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
