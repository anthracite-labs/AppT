"""Validate inert scan inputs before the secret-bearing Sonar action runs.

This script is loaded from the dispatch anchor, never from the target checkout.
It does not import, execute, or load scanner settings from target/artifact files.
"""

import argparse
from pathlib import Path
import stat


KOVER_REPORTS = (
    "build/reports/kover/report.xml",
    "app/build/reports/kover/report.xml",
    "app/build/reports/kover/reportDebug.xml",
    "build/reports/kover/reportDebug.xml",
)


def regular_tree(root):
    """Reject symlinks and special files, including the root itself."""
    if not stat.S_ISDIR(root.lstat().st_mode):
        raise ValueError(f"Scan input must be a real directory: {root}")
    for path in root.rglob("*"):
        mode = path.lstat().st_mode
        if not (stat.S_ISREG(mode) or stat.S_ISDIR(mode)):
            raise ValueError(f"Scan input contains a symlink or special file: {path}")


def validate_inputs(source, android, backend, work, home):
    # Trusted workflow supplies separate checkout/download/scratch locations.
    # Never resolve away an untrusted root symlink before checking its type.
    inputs = (source, android, backend)
    for path in inputs:
        regular_tree(path)
    roots = [path.resolve() for path in (*inputs, work, home)]
    for i, left in enumerate(roots):
        for right in roots[i + 1:]:
            if left == right or left in right.parents or right in left.parents:
                raise ValueError("Sonar source, artifacts and scanner state must be separate")
    lcov = backend / "lcov.info"
    if not lcov.is_file() or lcov.stat().st_size == 0:
        raise ValueError("Backend LCOV coverage is missing or empty")
    if not any((android / report).is_file() and (android / report).stat().st_size > 0
               for report in KOVER_REPORTS):
        raise ValueError("Kover XML coverage is missing or empty")
    # Pre-existing scanner state must not be trusted, including state supplied
    # by target files/artifacts. Fresh hosted-runner directories only; no reuse.
    for path in (work, home):
        if path.exists() or path.is_symlink():
            raise ValueError(f"Sonar scanner state must start empty: {path}")
    for path in (work, home):
        path.mkdir(mode=0o700)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "android", "backend", "work", "home"):
        parser.add_argument(f"--{name}", type=Path, required=True)
    args = parser.parse_args()
    validate_inputs(args.source, args.android, args.backend, args.work, args.home)
    print("Sonar source and coverage inputs validated; scanner state is fresh.")


if __name__ == "__main__":
    main()
