#!/usr/bin/env python3
"""Summarize Android lint XML reports as GitHub Actions annotations.

Run-log download is not available to the authoring agent, so lint errors are
mirrored into annotations, which the checks API does expose.
"""
import glob
import sys
import xml.etree.ElementTree as ET

total = 0
for path in sorted(glob.glob("*/build/reports/lint-results-*.xml")):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        print(f"could not parse {path}: {exc}")
        continue
    for issue in root.findall("issue"):
        if issue.get("severity") not in ("Error", "Fatal"):
            continue
        location = issue.find("location")
        where = "?"
        if location is not None:
            where = "{}:{}".format(
                location.get("file", "?").split("/AppT/")[-1],
                location.get("line", "?"),
            )
        line = "{} @ {}: {}".format(issue.get("id"), where, issue.get("message"))
        print(line)
        if total < 20:
            print("::error::LINT: " + line)
        total += 1

print(f"total lint errors: {total}")
sys.exit(0)
