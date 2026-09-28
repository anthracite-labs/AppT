"""Provider cache-token boundaries for workflows that execute PR-head code.

Run after yamllint (which supplies PyYAML) in repository-quality CI:
    python3 tools/ci/test/workflow-cache-contract.test.py

This parses YAML rather than grepping for a reassuring cache-mode line: a job
can override the workflow default, including via a flow mapping or YAML alias.
No provider cache writes or target-controlled builds are executed by these tests.
"""

from copy import deepcopy
from pathlib import Path
import unittest

import yaml


WORKFLOWS = Path(__file__).resolve().parents[3] / ".github" / "workflows"
TARGET_WORKFLOWS = {"verify.yml", "diagnose.yml"}
VALID_MODES = {"read", "none", "write", "write-only"}
READ_ONLY_MODES = {"read", "none"}


def validate_cache_contract(workflow, *, executes_target):
    """Check provider syntax everywhere and cap target workflows at read-only."""
    workflow_mode = workflow.get("cache-mode")
    if "cache-mode" in workflow:
        assert isinstance(workflow_mode, str) and workflow_mode in VALID_MODES, (
            "workflow cache-mode must be a literal provider-supported value"
        )
    if executes_target:
        assert workflow_mode in READ_ONLY_MODES, (
            "target workflow must explicitly cap provider cache access at read/none"
        )
    for job_id, job in workflow["jobs"].items():
        effective_mode = job.get("cache-mode", workflow_mode)
        if "cache-mode" in job:
            assert isinstance(effective_mode, str) and effective_mode in VALID_MODES, (
                f"{job_id}: job cache-mode must be a literal provider-supported value"
            )
        if executes_target:
            assert effective_mode in READ_ONLY_MODES, (
                f"{job_id}: target workflow job must not receive cache-write capability"
            )


def executes_validated_target(workflow):
    """Also cover future workflows using the repository's target assertion."""
    return any(
        "assert-dispatch-target" in step.get("uses", "")
        for job in workflow["jobs"].values()
        for step in job.get("steps", [])
    )


class WorkflowCacheContractTests(unittest.TestCase):
    def test_repository_workflows(self):
        paths = sorted(WORKFLOWS.glob("*.yml")) + sorted(WORKFLOWS.glob("*.yaml"))
        self.assertTrue(TARGET_WORKFLOWS <= {path.name for path in paths})
        for path in paths:
            with self.subTest(workflow=path.name):
                workflow = yaml.safe_load(path.read_text())
                validate_cache_contract(
                    workflow,
                    executes_target=(
                        path.name in TARGET_WORKFLOWS or executes_validated_target(workflow)
                    ),
                )

    def test_every_target_job_inherits_a_safe_mode(self):
        for name in sorted(TARGET_WORKFLOWS):
            workflow = yaml.safe_load((WORKFLOWS / name).read_text())
            for mode in READ_ONLY_MODES:
                with self.subTest(workflow=name, mode=mode):
                    candidate = deepcopy(workflow)
                    candidate["cache-mode"] = mode
                    validate_cache_contract(candidate, executes_target=True)

    def test_missing_workflow_boundary_fails_even_with_read_only_repository_token(self):
        for name in sorted(TARGET_WORKFLOWS):
            workflow = yaml.safe_load((WORKFLOWS / name).read_text())
            del workflow["cache-mode"]
            self.assertEqual(workflow["permissions"], {"contents": "read"})
            with self.subTest(workflow=name), self.assertRaisesRegex(
                AssertionError, "explicitly cap"
            ):
                validate_cache_contract(workflow, executes_target=True)

    def test_write_capability_fails_at_workflow_and_every_job(self):
        for name in sorted(TARGET_WORKFLOWS):
            workflow = yaml.safe_load((WORKFLOWS / name).read_text())
            for mode in ("write", "write-only"):
                candidate = deepcopy(workflow)
                candidate["cache-mode"] = mode
                with self.subTest(workflow=name, mode=mode), self.assertRaises(AssertionError):
                    validate_cache_contract(candidate, executes_target=True)
                for job_id in workflow["jobs"]:
                    candidate = deepcopy(workflow)
                    candidate["jobs"][job_id]["cache-mode"] = mode
                    with self.subTest(workflow=name, job=job_id, mode=mode):
                        with self.assertRaisesRegex(AssertionError, "cache-write capability"):
                            validate_cache_contract(candidate, executes_target=True)

    def test_flow_mapping_and_alias_cannot_hide_a_write_override(self):
        for jobs in (
            'build: {cache-mode: write}',
            'build: {"cache-mode": write-only}',
            'build: &job {cache-mode: write}\n  test: *job',
        ):
            workflow = yaml.safe_load(f"cache-mode: read\njobs:\n  {jobs}\n")
            with self.subTest(jobs=jobs), self.assertRaisesRegex(
                AssertionError, "cache-write capability"
            ):
                validate_cache_contract(workflow, executes_target=True)

    def test_cache_schema_rejects_expressions_invalid_values_and_wrong_types(self):
        for value in ("${{ inputs.cache_mode }}", "typo", "", None, True, [], {}):
            for workflow in (
                {"cache-mode": value, "jobs": {"build": {}}},
                {"cache-mode": "read", "jobs": {"build": {"cache-mode": value}}},
            ):
                with self.subTest(workflow=workflow), self.assertRaisesRegex(
                    AssertionError, "literal provider-supported value"
                ):
                    validate_cache_contract(workflow, executes_target=True)

    def test_future_target_workflows_are_discovered(self):
        workflow = yaml.safe_load("""
permissions: {contents: read}
jobs:
  build:
    steps:
      - uses: ./.github/actions/assert-dispatch-target
""")
        self.assertTrue(executes_validated_target(workflow))
        with self.assertRaisesRegex(AssertionError, "explicitly cap"):
            validate_cache_contract(workflow, executes_target=executes_validated_target(workflow))


if __name__ == "__main__":
    unittest.main(verbosity=2)
