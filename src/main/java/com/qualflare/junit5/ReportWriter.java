package com.qualflare.junit5;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Serialises the accumulated run into the report directory {@code qf collect} reads. */
final class ReportWriter {

    /** The server drops an attempts array shorter than this, so it is not worth sending. */
    private static final int MIN_ATTEMPTS_TO_SEND = 2;

    private ReportWriter() {}

    static Path write(Collection<CaseRecord> cases) throws IOException {
        Path dir = Paths.get(Config.outputDir());
        Files.createDirectories(dir);

        // Uniquely named per JVM, never a fixed filename: with forkCount > 1 or
        // reuseForks=false each forked JVM writes its own file into the SAME directory,
        // and `qf collect` merges every file it finds into one Launch. That is the same
        // directory-merge model pytest-xdist and Vitest --shard already use, so a forked
        // build needs no extra configuration -- but only if the files cannot collide.
        String name = String.format("qualflare-junit5-%d-%d-%d.json",
                ProcessHandle.current().pid(),
                System.currentTimeMillis(),
                ThreadLocalRandom.current().nextInt(100000));

        Path file = dir.resolve(name);
        Files.write(file, render(cases).getBytes(StandardCharsets.UTF_8));
        System.out.println("[qualflare-junit5] wrote " + cases.size() + " case(s) to " + file);
        return file;
    }

    static String render(Collection<CaseRecord> cases) {
        Map<String, List<CaseRecord>> bySuite = new LinkedHashMap<>();
        for (CaseRecord c : cases) {
            bySuite.computeIfAbsent(c.suiteName, k -> new ArrayList<>()).add(c);
        }

        StringBuilder suites = new StringBuilder("[");
        boolean firstSuite = true;
        for (Map.Entry<String, List<CaseRecord>> e : bySuite.entrySet()) {
            if (!firstSuite) {
                suites.append(',');
            }
            firstSuite = false;
            suites.append(renderSuite(e.getKey(), e.getValue()));
        }
        suites.append(']');

        String metadata = Json.object()
                .field("version", Version.VALUE)
                .field("timestamp", Instant.now().toString())
                .field("cliName", "qualflare-junit5")
                .end();

        return Json.object()
                .field("framework", "junit5")
                .field("platform", Config.platform())
                .field("os", System.getProperty("os.name", "") + " " + System.getProperty("os.arch", ""))
                .field("browser", "")
                .field("environment", Config.environment())
                .field("language", Config.language())
                .raw("metadata", metadata)
                // Explicit nulls, not omissions: the wire contract distinguishes "not
                // detected" from "absent", and the server treats a missing key differently.
                .nullableField("branch", Config.branch())
                .nullableField("commit", Config.commit())
                .raw("milestone", "null")
                .raw("suites", suites.toString())
                .end();
    }

    private static String renderSuite(String suiteName, List<CaseRecord> cases) {
        long total = 0L;
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < cases.size(); i++) {
            if (i > 0) {
                arr.append(',');
            }
            CaseRecord c = cases.get(i);
            total += c.durationNanos();
            arr.append(renderCase(c));
        }
        arr.append(']');

        return Json.object()
                .field("name", suiteName)
                .field("duration", total)
                // "unit" everywhere: the JUnit Platform runs unit, integration and E2E
                // suites alike, and the framework cannot tell them apart. Guessing from
                // the runner would mislabel a Selenium suite as a unit test.
                .field("category", "unit")
                .raw("cases", arr.toString())
                .end();
    }

    private static String renderCase(CaseRecord c) {
        Json j = Json.object()
                .field("id", c.uniqueId)
                .field("name", c.displayName)
                .field("status", c.status())
                .field("duration", c.durationNanos());

        if (!c.className.isEmpty()) {
            j.field("className", c.className);
        }
        String msg = c.message();
        if (!msg.isEmpty()) {
            j.field("error", msg);
        }
        if (c.retryCount() > 0) {
            j.field("retryCount", c.retryCount());
        }
        if (c.isFlaky()) {
            j.field("isFlaky", true);
        }
        if (c.attempts.size() >= MIN_ATTEMPTS_TO_SEND) {
            j.raw("attempts", renderAttempts(c));
        }
        return j.end();
    }

    private static String renderAttempts(CaseRecord c) {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < c.attempts.size(); i++) {
            if (i > 0) {
                arr.append(',');
            }
            Attempt a = c.attempts.get(i);
            Json j = Json.object()
                    .field("attempt", i + 1) // 1-based; the server drops anything lower
                    .field("status", a.status)
                    .field("duration", a.durationNanos);
            if (!a.message.isEmpty()) {
                j.field("message", a.message);
            }
            if (!a.trace.isEmpty()) {
                j.field("trace", a.trace);
            }
            arr.append(j.end());
        }
        return arr.append(']').toString();
    }
}
