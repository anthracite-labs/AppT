#!/usr/bin/env python3
"""Validate internal consistency of the bounded project-state projection."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE_PATH = ROOT / ".project-ai" / "PROJECT_STATE.md"


def section(text: str, heading: str) -> str:
    """Return one level-two Markdown section body."""
    marker = f"## {heading}\n"
    start = text.find(marker)
    if start == -1:
        raise ValueError(f"missing section {heading!r}")
    body_start = start + len(marker)
    end = text.find("\n## ", body_start)
    return text[body_start:] if end == -1 else text[body_start:end]


def first_content_line(value: str) -> str:
    """Return the first non-empty line from a section."""
    return next((line.strip() for line in value.splitlines() if line.strip()), "")


def slice_refs(value: str) -> list[int]:
    """Return Sxx references in textual order."""
    return [int(match) for match in re.findall(r"\bS(\d{2})\b", value)]


def validate_project_state(text: str) -> list[str]:
    """Return contradictions in the current project-state projection."""
    errors: list[str] = []

    try:
        phase = first_content_line(section(text, "Phase"))
        objective = section(text, "Current Objective")
        decisions = section(text, "Accepted Decisions")
        blockers = section(text, "Durable Blockers")
        milestone = section(text, "Latest Accepted Milestone")
        next_action = section(text, "Next Authorized Action")
    except ValueError as exc:
        return [str(exc)]

    if phase != "Implementation.":
        return errors

    next_matches = re.findall(
        r"^- S(\d{2}) is the next authorized implementation slice\.$",
        decisions,
        re.MULTILINE,
    )
    if len(next_matches) != 1:
        errors.append(
            "Accepted Decisions: expected exactly one "
            "'Sxx is the next authorized implementation slice' entry"
        )
        return errors

    active = int(next_matches[0])

    objective_refs = slice_refs(first_content_line(objective))
    if not objective_refs:
        errors.append("Current Objective: implementation objective must name the active slice")
    elif objective_refs[0] != active:
        errors.append(
            f"Current Objective: projects S{objective_refs[0]:02d} "
            f"while Accepted Decisions projects S{active:02d}"
        )

    blocker_match = re.search(r"^None for S(\d{2}) dispatch\.$", blockers.strip())
    if blocker_match is not None and int(blocker_match.group(1)) != active:
        errors.append(
            f"Durable Blockers: projects S{int(blocker_match.group(1)):02d} "
            f"while Accepted Decisions projects S{active:02d}"
        )

    action_refs = slice_refs(first_content_line(next_action))
    if action_refs and action_refs[0] != active:
        errors.append(
            f"Next Authorized Action: projects S{action_refs[0]:02d} "
            f"while Accepted Decisions projects S{active:02d}"
        )

    latest_refs = slice_refs(first_content_line(milestone))
    if active > 1:
        expected_previous = active - 1
        if not latest_refs:
            errors.append("Latest Accepted Milestone: expected a predecessor slice reference")
        elif latest_refs[0] != expected_previous:
            errors.append(
                "Latest Accepted Milestone: "
                f"expected S{expected_previous:02d} before active S{active:02d}, "
                f"found S{latest_refs[0]:02d}"
            )

        accepted_pattern = re.compile(
            rf"^- S{expected_previous:02d} is accepted through .+$",
            re.MULTILINE,
        )
        if accepted_pattern.search(decisions) is None:
            errors.append(
                "Accepted Decisions: "
                f"S{expected_previous:02d} must be recorded accepted before S{active:02d} is next"
            )

    return errors


def main() -> int:
    """Validate the repository's current state projection."""
    text = STATE_PATH.read_text(encoding="utf-8")
    errors = validate_project_state(text)

    if errors:
        print("Project-state contract failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Project-state contract passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
