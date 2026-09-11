#!/usr/bin/env python3
"""Checks the dogfood report BEFORE it is uploaded.

A report can be structurally valid and semantically wrong: that is exactly how
`Case.attempts` went missing for three qualflare-cli releases while every run stayed
green. Uploading first and inspecting later would repeat it.

Failures accumulate rather than aborting on the first, so one run tells you everything
that is wrong.
"""
import glob
import json
import os
import sys

OUT = os.environ.get("QUALFLARE_OUTPUT_DIR", "qualflare-results")

failures = []


def check(label, ok, detail=""):
    print(("  ok    " if ok else "  FAIL  ") + label + ("" if ok else "   -> " + str(detail)))
    if not ok:
        failures.append(label)


def main():
    files = sorted(glob.glob(os.path.join(OUT, "*.json")))
    if not files:
        print("no report in %s" % OUT)
        sys.exit(1)

    report = json.load(open(files[0]))

    check("framework is junit5", report.get("framework") == "junit5", report.get("framework"))
    # Value-or-null, never omitted: the server treats a missing key differently from an
    # explicit null, and "not detected" is a real answer.
    for key in ("branch", "commit", "milestone"):
        check("%s is present as value-or-null" % key, key in report, "missing")

    cases = {}
    for suite in report["suites"]:
        check("suite %s has a category" % suite["name"], bool(suite.get("category")))
        for case in suite["cases"]:
            cases[case["name"]] = case

    check("every case passed", all(c["status"] == "passed" for c in cases.values()),
          [n for n, c in cases.items() if c["status"] != "passed"])

    meta = cases.get("recordsEveryMetadataKind()")
    check("the metadata case is present", meta is not None)
    if meta:
        check("labels recorded", len(meta.get("labels") or []) == 2, meta.get("labels"))
        check("tags recorded", sorted(meta.get("tags") or []) == ["dogfood", "smoke"], meta.get("tags"))
        check("link recorded", len(meta.get("links") or []) == 1, meta.get("links"))
        check("priority recorded", meta.get("priority") == "high", meta.get("priority"))
        check("description recorded", bool(meta.get("description")))
        props = meta.get("properties") or {}
        check("an unmasked parameter keeps its value", props.get("plan") == "pro", props.get("plan"))
        check("a masked parameter does not", props.get("token") == "", props.get("token"))

    steps = cases.get("nestsSteps()")
    check("the steps case is present", steps is not None)
    if steps:
        s = steps.get("steps") or []
        check("two steps recorded", len(s) == 2, len(s))
        if len(s) == 2:
            check("the outer step is top level", s[0].get("parentIndex") is None, s[0].get("parentIndex"))
            check("the inner step points at the outer", s[1].get("parentIndex") == 0, s[1].get("parentIndex"))
            check("step names are meaningful, not placeholders",
                  s[0]["name"] == "add to cart" and s[1]["name"] == "set quantity",
                  [x["name"] for x in s])

    att = cases.get("attachesContent(TestReporter)")
    check("the attachment case is present", att is not None)
    if att:
        check("one attachment recorded", len(att.get("attachments") or []) == 1,
              len(att.get("attachments") or []))

    rows = [n for n in cases if n.startswith("[") and ("chrome" in n or "firefox" in n)]
    check("parameterized rows are separate cases", len(rows) == 2, rows)

    decoy = cases.get("entriesOutsideOurNamespacePassThrough(TestReporter)")
    if decoy:
        check("a foreign report entry did not become a label",
              all(l.get("name") != "app.buildId" for l in (decoy.get("labels") or [])),
              decoy.get("labels"))

    blob = json.dumps(report)
    check("no unmasked secret reaches the report", "api-token-value" not in blob)

    print()
    if failures:
        print("FAILED: %d check(s) in %s" % (len(failures), files[0]))
        sys.exit(1)
    print("%d cases verified in %s -- report matches what the suite declared"
          % (len(cases), os.path.basename(files[0])))


if __name__ == "__main__":
    main()
