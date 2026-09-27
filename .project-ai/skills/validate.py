#!/usr/bin/env python3
"""Validate lifecycle skill structure and control-plane routing coverage."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROJECT_AI_ROOT = ROOT.parent
ROUTING_OWNER_PATHS = [
    PROJECT_AI_ROOT / "hosts" / "chatgpt-project.md",
    PROJECT_AI_ROOT / "routing" / "capabilities.md",
    PROJECT_AI_ROOT / "routing" / "route.md",
    PROJECT_AI_ROOT / "execution" / "arena-dispatch.md",
    PROJECT_AI_ROOT / "execution" / "verification.md",
    PROJECT_AI_ROOT / "bootstrap" / "project.md",
]
FRONTMATTER_OPEN = "---\n"
FRONTMATTER_CLOSE = "\n---\n"
IGNORED_SUPPORT_DIRS = {"__pycache__"}
REQUIRED_FRONTMATTER_KEYS = {"name", "description"}

REQUIRED_HEADINGS = [
    "Purpose",
    "Workflow",
    "Output contract",
    "Boundaries",
    "Completion gate",
]

BANNED_RUNTIME_MARKERS = [
    "## Provenance",
    "_bmad/",
    ".sdlc/",
    "docs/superpowers/",
    ".superpowers/",
]

LEGACY_RUNTIME_TERMS = [
    "Normal ChatGPT",
    "Arena product work",
    "Arena product PRs",
    "full repository acceptance",
    "terminal acceptance",
    "terminal repository acceptance",
]

FRONTMATTER_LINE = re.compile(r"^(name|description): (.+)$")
YAML_PLAIN_FORBIDDEN_START = tuple("-?:,[]{}#&*!|>'\"%@")


def parse_frontmatter(text: str) -> tuple[dict[str, str], str]:
    """Parse the canonical two-field plain-scalar YAML frontmatter subset."""
    if not text.startswith(FRONTMATTER_OPEN):
        raise ValueError("frontmatter must start on line 1")

    end = text.find(FRONTMATTER_CLOSE, len(FRONTMATTER_OPEN))
    if end == -1:
        raise ValueError("frontmatter closing delimiter not found")

    raw = text[len(FRONTMATTER_OPEN) : end]
    body = text[end + len(FRONTMATTER_CLOSE) :]

    fields: dict[str, str] = {}
    for line in raw.splitlines():
        match = FRONTMATTER_LINE.fullmatch(line)
        if match is None:
            raise ValueError(
                "frontmatter must use exactly 'name: <plain scalar>' and "
                "'description: <plain scalar>' lines"
            )

        key, value = match.groups()
        if key in fields:
            raise ValueError(f"duplicate frontmatter key {key!r}")

        if (
            not value
            or value != value.strip()
            or value.startswith(YAML_PLAIN_FORBIDDEN_START)
            or ": " in value
            or " #" in value
        ):
            raise ValueError(
                f"frontmatter {key!r} must be a canonical YAML plain scalar"
            )

        fields[key] = value

    missing = REQUIRED_FRONTMATTER_KEYS - fields.keys()
    if missing:
        raise ValueError(
            "missing frontmatter key(s): " + ", ".join(sorted(missing))
        )

    return fields, body


def heading_positions(body: str) -> dict[str, int]:
    """Return level-two heading positions for canonical-order validation."""
    positions: dict[str, int] = {}
    for match in re.finditer(r"^##\s+(.+?)\s*$", body, re.MULTILINE):
        positions[match.group(1)] = match.start()
    return positions


def validate_skill(skill_dir: Path) -> list[str]:
    """Return structural errors for one immediate skill directory."""
    errors: list[str] = []
    skill_file = skill_dir / "SKILL.md"
    if not skill_file.exists():
        return [f"{skill_dir.name}: missing SKILL.md"]

    text = skill_file.read_text(encoding="utf-8")
    line_count = len(text.splitlines())

    try:
        fields, body = parse_frontmatter(text)
    except ValueError as exc:
        return [f"{skill_dir.name}: {exc}"]

    name = fields["name"]
    description = fields["description"]

    if name != skill_dir.name:
        errors.append(
            f"{skill_dir.name}: frontmatter name {name!r} does not match directory"
        )
    if not description.startswith("Use when "):
        errors.append(f"{skill_dir.name}: description must start with 'Use when '")
    if len(description) > 500:
        errors.append(
            f"{skill_dir.name}: description is {len(description)} chars; keep <= 500"
        )
    if line_count > 500:
        errors.append(f"{skill_dir.name}: SKILL.md is {line_count} lines; keep <= 500")

    headings = heading_positions(body)
    last = -1
    for heading in REQUIRED_HEADINGS:
        if heading not in headings:
            errors.append(f"{skill_dir.name}: missing required heading '## {heading}'")
            continue
        if headings[heading] <= last:
            errors.append(
                f"{skill_dir.name}: required headings are not in canonical order"
            )
        last = headings[heading]

    for marker in BANNED_RUNTIME_MARKERS:
        if marker in text:
            errors.append(
                f"{skill_dir.name}: runtime file contains maintenance/upstream marker {marker!r}"
            )

    for term in LEGACY_RUNTIME_TERMS:
        if term in text:
            errors.append(
                f"{skill_dir.name}: runtime file contains legacy control-plane term {term!r}"
            )

    return errors


def validate_routing_coverage(skill_names: set[str]) -> list[str]:
    """Require every skill to be reachable from canonical control-plane owners."""
    errors: list[str] = []
    owner_texts: list[str] = []

    for owner in ROUTING_OWNER_PATHS:
        if not owner.exists():
            errors.append(f"routing owner missing: {owner.relative_to(PROJECT_AI_ROOT)}")
            continue
        owner_texts.append(owner.read_text(encoding="utf-8"))

    combined = "\n".join(owner_texts)
    referenced = set(re.findall(r"skills/([a-z0-9-]+)/SKILL\.md", combined))

    for name in sorted(skill_names - referenced):
        errors.append(f"{name}: skill has no canonical control-plane routing reference")

    for name in sorted(referenced - skill_names):
        errors.append(f"routing references unknown skill {name!r}")

    return errors


def main() -> int:
    """Validate every immediate skill directory and return a process status."""
    skill_dirs = sorted(
        path
        for path in ROOT.iterdir()
        if path.is_dir()
        and not path.name.startswith(".")
        and path.name not in IGNORED_SUPPORT_DIRS
    )

    errors: list[str] = []
    names: set[str] = set()

    for skill_dir in skill_dirs:
        errors.extend(validate_skill(skill_dir))

        skill_file = skill_dir / "SKILL.md"
        if not skill_file.exists():
            continue

        try:
            fields, _ = parse_frontmatter(skill_file.read_text(encoding="utf-8"))
        except ValueError:
            continue

        name = fields["name"]
        if name in names:
            errors.append(f"{skill_dir.name}: duplicate skill name {name!r}")
        names.add(name)

    errors.extend(validate_routing_coverage(names))

    if errors:
        print("Skill validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"Skill validation passed: {len(skill_dirs)} skills; routing coverage complete")
    return 0


if __name__ == "__main__":
    sys.exit(main())
