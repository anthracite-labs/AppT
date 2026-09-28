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


def require_slice(pattern: str, value: str, label: str, errors: list[str]) -> int | None:
    """Extract one Sxx slice from an expected projection sentence."""
    match = re.search(pattern, value, re.MULTILINE)
    if match is None:
        errors.append(f"{label}: expected slice reference is missing")
        return None
    return int(match.group(1))


def validate_project_state(text: str) -> list[str]:
    """Return contradictions in the current project-state projection."""
    errors: list[str] = []

    try:
        objective = section(text, "Current Objective")
        decisions = section(text, "Accepted Decisions")
        blockers = section(text, "Durable Blockers")
        milestone = section(text, "Latest Accepted Milestone")
        next_action = section(text, "Next Authorized Action")
    except ValueError as exc:
        return [str(exc)]

    active = require_slice(
        r"^Compile and dispatch S(\d{2})\b",
        objective.strip(),
        "Current Objective",
        errors,
    )
    decision_active = require_slice(
        r"^- S(\d{2}) is the next authorized implementation slice\.$",
        decisions,
        "Accepted Decisions",
        errors,
    )
    blocker_active = require_slice(
        r"^None for S(\d{2}) dispatch\.$",
        blockers.strip(),
        "Durable Blockers",
        errors,
    )
    action_active = require_slice(
        r"compile and dispatch S(\d{2})\.",
        next_action.strip(),
        "Next Authorized Action",
        errors,
    )
    latest = require_slice(
        r"^S(\d{2})\b",
        milestone.strip(),
        "Latest Accepted Milestone",
        errors,
    )

    known = [
        ("Accepted Decisions", decision_active),
        ("Durable Blockers", blocker_active),
        ("Next Authorized Action", action_active),
    ]
    if active is not None:
        for label, candidate in known:
            if candidate is not None and candidate != active:
                errors.append(
                    f"{label}: projects S{candidate:02d} while Current Objective projects S{active:02d}"
                )

        if active > 1:
            expected_previous = active - 1
            if latest is not None and latest != expected_previous:
                errors.append(
                    "Latest Accepted Milestone: "
                    f"expected S{expected_previous:02d} before active S{active:02d}, "
                    f"found S{latest:02d}"
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
