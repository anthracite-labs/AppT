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


class ProjectStateContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.state = STATE_PATH.read_text(encoding="utf-8")

    def assert_invalid_after(self, old: str, new: str) -> None:
        self.assertIn(old, self.state)
        errors = contract.validate_project_state(self.state.replace(old, new, 1))
        self.assertTrue(errors)

    def test_repository_state_is_consistent(self) -> None:
        self.assertEqual([], contract.validate_project_state(self.state))

    def test_next_action_cannot_disagree_with_objective(self) -> None:
        self.assert_invalid_after("compile and dispatch S04.", "compile and dispatch S05.")

    def test_next_slice_decision_cannot_disagree_with_objective(self) -> None:
        self.assert_invalid_after(
            "- S04 is the next authorized implementation slice.",
            "- S05 is the next authorized implementation slice.",
        )

    def test_blocker_projection_cannot_disagree_with_objective(self) -> None:
        self.assert_invalid_after("None for S04 dispatch.", "None for S03 dispatch.")

    def test_latest_milestone_must_be_immediate_predecessor(self) -> None:
        self.assert_invalid_after(
            "S03 — Pair on the television and the first command — accepted through PR #80.",
            "S02 — Local-network explanation and bounded discovery — accepted through PR #77.",
        )


if __name__ == "__main__":
    unittest.main()
