#!/usr/bin/env python3
"""Validate lifecycle skill structure and control-plane routing coverage."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROJECT_AI_ROOT = ROOT.parent
ROUTING_OWNER = PROJECT_AI_ROOT / "routing" / "capabilities.md"
ROUTING_HEADING = "## Mandatory lifecycle skill routing"
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
    """Require every skill exactly once in the mandatory lifecycle routing table."""
    errors: list[str] = []

    if not ROUTING_OWNER.exists():
        return [
            f"routing owner missing: {ROUTING_OWNER.relative_to(PROJECT_AI_ROOT)}"
        ]

    text = ROUTING_OWNER.read_text(encoding="utf-8")
    heading = re.search(
        rf"^{re.escape(ROUTING_HEADING)}$",
        text,
        re.MULTILINE,
    )
    if heading is None:
        return ["mandatory lifecycle skill routing heading is missing"]

    section_start = heading.end() + 1
    section_end = text.find("\n## ", section_start)
    section = text[section_start:] if section_end == -1 else text[section_start:section_end]

    lines = section.splitlines()
    try:
        header_index = next(
            index
            for index, line in enumerate(lines)
            if line.strip() == "| Trigger | Required skill |"
        )
    except StopIteration:
        return ["mandatory lifecycle skill routing table header is missing"]

    if header_index + 1 >= len(lines) or not re.fullmatch(
        r"\|\s*:?-+:?\s*\|\s*:?-+:?\s*\|",
        lines[header_index + 1].strip(),
    ):
        return ["mandatory lifecycle skill routing table separator is invalid"]

    references: list[str] = []
    for line in lines[header_index + 2 :]:
        stripped = line.strip()
        if not stripped.startswith("|"):
            break

        cells = [cell.strip() for cell in stripped.strip("|").split("|")]
        if len(cells) != 2 or not cells[0]:
            errors.append(f"invalid mandatory lifecycle routing row: {stripped!r}")
            continue

        match = re.fullmatch(
            r"`\.\./skills/([a-z0-9-]+)/SKILL\.md`",
            cells[1],
        )
        if match is None:
            errors.append(
                "mandatory lifecycle routing row has invalid required skill cell "
                f"{cells[1]!r}"
            )
            continue

        references.append(match.group(1))

    if not references:
        errors.append("mandatory lifecycle skill routing table has no data rows")

    referenced = set(references)

    for name in sorted(skill_names - referenced):
        errors.append(f"{name}: missing from mandatory lifecycle skill routing")

    for name in sorted(referenced - skill_names):
        errors.append(f"mandatory routing references unknown skill {name!r}")

    duplicates = sorted({name for name in references if references.count(name) > 1})
    for name in duplicates:
        errors.append(f"{name}: appears more than once in mandatory lifecycle skill routing")

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
