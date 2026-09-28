#!/usr/bin/env python3
"""Regression tests for the project-state consistency contract."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools" / "ci" / "project_state_contract.py"
STATE_PATH = ROOT / ".project-ai" / "PROJECT_STATE.md"

spec = importlib.util.spec_from_file_location("project_state_contract", MODULE_PATH)
assert spec is not None and spec.loader is not None
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)

GOOD = """# Project State

## Phase

Implementation.

## Current Objective

Compile and dispatch S04 — Example active slice.

## Accepted Decisions

- S03 is accepted through PR #80.
- S04 is the next authorized implementation slice.

## Durable Blockers

None for S04 dispatch.

## Latest Accepted Milestone

S03 — Example accepted slice — accepted through PR #80.

## Next Authorized Action

Compile and dispatch S04.
"""


class ProjectStateContractTest(unittest.TestCase):
    def assert_invalid_after(self, old: str, new: str) -> None:
        self.assertIn(old, GOOD)
        errors = contract.validate_project_state(GOOD.replace(old, new, 1))
        self.assertTrue(errors)

    def test_repository_state_is_consistent(self) -> None:
        state = STATE_PATH.read_text(encoding="utf-8")
        self.assertEqual([], contract.validate_project_state(state))

    def test_reference_fixture_is_consistent(self) -> None:
        self.assertEqual([], contract.validate_project_state(GOOD))

    def test_objective_cannot_disagree_with_next_slice(self) -> None:
        self.assert_invalid_after(
            "Compile and dispatch S04 — Example active slice.",
            "Compile and dispatch S05 — Wrong slice.",
        )

    def test_next_action_cannot_disagree_with_next_slice(self) -> None:
        self.assert_invalid_after("Compile and dispatch S04.", "Compile and dispatch S05.")

    def test_blocker_none_projection_cannot_disagree_with_next_slice(self) -> None:
        self.assert_invalid_after("None for S04 dispatch.", "None for S03 dispatch.")

    def test_real_blocker_text_does_not_require_none_for_shape(self) -> None:
        blocked = GOOD.replace(
            "None for S04 dispatch.",
            "Provider administration blocks terminal verification.",
        ).replace(
            "Compile and dispatch S04.",
            "Resolve the provider administration blocker before dispatch.",
        )
        self.assertEqual([], contract.validate_project_state(blocked))

    def test_latest_milestone_must_be_immediate_predecessor(self) -> None:
        self.assert_invalid_after(
            "S03 — Example accepted slice — accepted through PR #80.",
            "S02 — Wrong predecessor — accepted through PR #77.",
        )

    def test_duplicate_next_slice_projection_is_rejected(self) -> None:
        duplicate = GOOD.replace(
            "- S04 is the next authorized implementation slice.",
            "- S04 is the next authorized implementation slice.\n"
            "- S05 is the next authorized implementation slice.",
        )
        self.assertTrue(contract.validate_project_state(duplicate))


if __name__ == "__main__":
    unittest.main()
