#!/usr/bin/env python3
"""Runs the fixture twice -- serial and parallel -- and checks the report is the same.

A reporter that is correct only when tests run one at a time is not correct. Under
`junit.jupiter.execution.parallel.enabled` the listener is invoked from ForkJoinPool
workers, so every accumulation is a read-modify-write that can interleave. This is the
check that would catch a lost attempt or a label attributed to the wrong test.

Also asserts the properties that have bitten this family before, so a regression is loud:
  - a container failure is REPRESENTED, not silently absent
  - a rerun becomes attempts on ONE case, not several one-attempt cases
  - the step cap keeps the outer step's measured duration
  - a masked parameter carries no value
"""
import json
import glob
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURE = os.path.join(HERE, "fixture")
MVN = os.environ.get("MVN", "mvn")

failures = []


def check(label, ok, detail=""):
    print(("  PASS  " if ok else "  FAIL  ") + label + ("" if ok else "   -> " + str(detail)))
    if not ok:
        failures.append(label)


def run(parallel):
    """One full fixture run; returns the parsed report."""
    results = os.path.join(FIXTURE, "qualflare-results")
    shutil.rmtree(results, ignore_errors=True)
    # The flaky fixture counts attempts on disk so it survives a forked JVM.
    counter = os.path.join(
        os.environ.get("TMPDIR", "/tmp"), "qf-junit5-fixture-counter")
    if os.path.exists(counter):
        os.remove(counter)

    cmd = [MVN, "-q", "test"]
    if parallel:
        cmd += ["-Djunit.jupiter.execution.parallel.enabled=true",
                "-Djunit.jupiter.execution.parallel.mode.default=concurrent"]
    subprocess.run(cmd, cwd=FIXTURE, capture_output=True, text=True)

    files = glob.glob(os.path.join(results, "*.json"))
    if not files:
        print("  no report written")
        sys.exit(1)
    # One file per JVM. More than one here means state escaped its JVM scope.
    if len(files) != 1:
        print("  expected exactly 1 report file, got %d" % len(files))
        sys.exit(1)
    with open(files[0]) as fh:
        return json.load(fh)


def index(report):
    out = {}
    for suite in report["suites"]:
        for case in suite["cases"]:
            out[suite["name"] + "::" + case["name"]] = case
    return out


def main():
    print("serial run")
    serial = index(run(parallel=False))
    print("parallel run")
    concurrent = index(run(parallel=True))

    check("the same cases appear in both runs",
          set(serial) == set(concurrent),
          sorted(set(serial) ^ set(concurrent)))

    for key in sorted(set(serial) & set(concurrent)):
        a, b = serial[key], concurrent[key]
        if a["status"] != b["status"]:
            check("%s has the same status in both runs" % key, False,
                  "%s vs %s" % (a["status"], b["status"]))

    # The traps, asserted against the serial run.
    container = [k for k in serial if "container failure" in k]
    check("a container failure is represented rather than silently absent",
          len(container) == 1, container)
    if container:
        check("and it is red", serial[container[0]]["status"] in ("error", "failed"),
              serial[container[0]]["status"])

    flaky = serial.get("FixtureTest::flakyRecovers()")
    check("a rerun becomes ONE case", flaky is not None)
    if flaky:
        att = flaky.get("attempts") or []
        check("with every attempt recorded", [x["status"] for x in att] == ["failed", "failed", "passed"],
              [x["status"] for x in att])
        check("marked flaky", flaky.get("isFlaky") is True, flaky.get("isFlaky"))

    hard = serial.get("FixtureTest::failsHard()")
    if hard:
        check("a test that never recovers is NOT flaky",
              not hard.get("isFlaky"), hard.get("isFlaky"))

    cap = serial.get("MetadataTest::outerStepSurvivesTheStepCap()")
    check("the step cap case is present", cap is not None)
    if cap:
        steps = cap.get("steps") or []
        check("the cap was applied", len(steps) <= 300, len(steps))
        check("the outer step is the one that wraps the sleep",
              steps and steps[0]["name"] == "wraps-a-measured-sleep",
              steps[0]["name"] if steps else "none")
        check("the outer step KEPT its measured duration",
              steps and steps[0]["duration"] >= 40_000_000,
              "%.3fms" % (steps[0]["duration"] / 1e6) if steps else "none")

    meta = serial.get("MetadataTest::recordsEveryMetadataKind()")
    if meta:
        props = meta.get("properties") or {}
        check("a masked parameter carries no value", props.get("token") == "", props.get("token"))
        check("an unmasked one does", props.get("plan") == "pro", props.get("plan"))

    blob = json.dumps(serial)
    check("no secret literal reaches the report", "qf-junit5-secret" not in blob)

    print()
    if failures:
        print("FAILED: %d check(s)" % len(failures))
        sys.exit(1)
    print("all checks passed (%d cases)" % len(serial))


if __name__ == "__main__":
    main()
