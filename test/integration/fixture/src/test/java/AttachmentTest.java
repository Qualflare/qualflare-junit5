import com.qualflare.junit5.Qualflare;
import com.qualflare.junit5.QualflareExtension;
import org.junit.jupiter.api.extension.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;

/** Attachments, both routes. Never uploaded anywhere. */
@ExtendWith(QualflareExtension.class)
class AttachmentTest {

    /** Text inlines, because the upload endpoint has nowhere else to put it. */
    @Test
    void attachesText(TestReporter reporter) {
        reporter.publishFile("note.txt", MediaType.TEXT_PLAIN,
                f -> Files.writeString(f, "a text attachment from the fixture"));
    }

    /**
     * A PNG goes out of band: copied into outputDir and referenced by name, so it never
     * competes for the run's inline budget.
     */
    @Test
    void attachesImage(TestReporter reporter) {
        reporter.publishFile("shot.png", MediaType.IMAGE_PNG, f ->
                // The smallest valid PNG: 1x1, transparent. Real bytes, not a stub, so the
                // size and the copy are genuinely exercised.
                Files.write(f, new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                        (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                        0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                        0x42, 0x60, (byte) 0x82}));
        Qualflare.label("has", "screenshot");
    }
}
