"""Provider cache-token boundaries for workflows that execute PR-head code.

Run after yamllint (which supplies PyYAML) in the `repo-quality` job:
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
TRUSTED_CACHE_PUBLISHER = "publish-gradle-warm-state"
GRADLE_DIAGNOSTIC_JOBS = {
    "app-unit",
    "samsung-unit",
    "android-static",
    "android-build",
    "device",
}


def validate_cache_contract(workflow, *, executes_target, trusted_publishers=frozenset()):
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
            if job_id in trusted_publishers:
                assert effective_mode == "write-only", (
                    f"{job_id}: trusted cache publisher must be write-only"
                )
            else:
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
                    trusted_publishers=(
                        {TRUSTED_CACHE_PUBLISHER}
                        if path.name == "diagnose.yml"
                        else frozenset()
                    ),
                )

    def test_every_target_job_inherits_a_safe_mode(self):
        for name in sorted(TARGET_WORKFLOWS):
            workflow = yaml.safe_load((WORKFLOWS / name).read_text())
            for mode in READ_ONLY_MODES:
                with self.subTest(workflow=name, mode=mode):
                    candidate = deepcopy(workflow)
                    candidate["cache-mode"] = mode
                    validate_cache_contract(
                            candidate,
                            executes_target=True,
                            trusted_publishers=(
                                {TRUSTED_CACHE_PUBLISHER}
                                if name == "diagnose.yml"
                                else frozenset()
                            ),
                        )

    def test_missing_workflow_boundary_fails_even_with_read_only_repository_token(self):
        for name in sorted(TARGET_WORKFLOWS):
            workflow = yaml.safe_load((WORKFLOWS / name).read_text())
            del workflow["cache-mode"]
            self.assertEqual(workflow["permissions"], {"contents": "read"})
            with self.subTest(workflow=name), self.assertRaisesRegex(
                AssertionError, "explicitly cap"
            ):
                validate_cache_contract(
                    workflow,
                    executes_target=True,
                    trusted_publishers=(
                        {TRUSTED_CACHE_PUBLISHER}
                        if name == "diagnose.yml"
                        else frozenset()
                    ),
                )

    def test_write_capability_fails_at_workflow_and_every_job(self):
        for name in sorted(TARGET_WORKFLOWS):
            workflow = yaml.safe_load((WORKFLOWS / name).read_text())
            for mode in ("write", "write-only"):
                candidate = deepcopy(workflow)
                candidate["cache-mode"] = mode
                with self.subTest(workflow=name, mode=mode), self.assertRaises(AssertionError):
                    validate_cache_contract(
                            candidate,
                            executes_target=True,
                            trusted_publishers=(
                                {TRUSTED_CACHE_PUBLISHER}
                                if name == "diagnose.yml"
                                else frozenset()
                            ),
                        )
                for job_id in workflow["jobs"]:
                    if job_id == TRUSTED_CACHE_PUBLISHER and mode == "write-only":
                        continue
                    candidate = deepcopy(workflow)
                    candidate["jobs"][job_id]["cache-mode"] = mode
                    with self.subTest(workflow=name, job=job_id, mode=mode):
                        message = (
                            "trusted cache publisher must be write-only"
                            if job_id == TRUSTED_CACHE_PUBLISHER
                            else "cache-write capability"
                        )
                        with self.assertRaisesRegex(AssertionError, message):
                            validate_cache_contract(
                            candidate,
                            executes_target=True,
                            trusted_publishers=(
                                {TRUSTED_CACHE_PUBLISHER}
                                if name == "diagnose.yml"
                                else frozenset()
                            ),
                        )

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


    def test_trusted_publisher_allowance_is_diagnose_only(self):
        diagnose = yaml.safe_load((WORKFLOWS / "diagnose.yml").read_text())
        validate_cache_contract(
            diagnose,
            executes_target=True,
            trusted_publishers={TRUSTED_CACHE_PUBLISHER},
        )
        with self.assertRaisesRegex(AssertionError, "cache-write capability"):
            validate_cache_contract(diagnose, executes_target=True)

    def test_pr_local_gradle_cache_has_one_trusted_write_only_publisher(self):
        diagnose = yaml.safe_load((WORKFLOWS / "diagnose.yml").read_text())
        verify_text = (WORKFLOWS / "verify.yml").read_text()
        publisher = diagnose["jobs"][TRUSTED_CACHE_PUBLISHER]

        self.assertEqual(publisher["cache-mode"], "write-only")
        self.assertEqual(publisher["permissions"], {"contents": "read"})
        self.assertEqual(set(publisher["needs"]), GRADLE_DIAGNOSTIC_JOBS)

        publisher_uses = [
            step["uses"]
            for step in publisher["steps"]
            if "uses" in step
        ]
        self.assertEqual(
            publisher_uses,
            [
                "actions/download-artifact@37930b1c2abaa49bbe596cd826c3c89aef350131",
                "actions/cache/save@0400d5f644dc74513175e3cd8d07132dd4860809",
            ],
        )
        self.assertFalse(
            any("checkout" in use or "assert-dispatch-target" in use for use in publisher_uses)
        )

        publisher_text = yaml.safe_dump(publisher, sort_keys=False)
        self.assertIn("appt-pr-gradle-${{ env.PR_NUMBER }}-", publisher_text)
        self.assertNotIn("inputs.target_ref", publisher_text)
        self.assertNotIn("inputs.focus", publisher_text)

        for job_id in sorted(GRADLE_DIAGNOSTIC_JOBS):
            job = diagnose["jobs"][job_id]
            self.assertNotIn("cache-mode", job, f"{job_id} must inherit workflow read-only mode")
            uses = [step.get("uses", "") for step in job["steps"]]
            self.assertIn(
                "actions/cache/restore@0400d5f644dc74513175e3cd8d07132dd4860809",
                uses,
                f"{job_id} must restore only through the trusted PR-number key",
            )
            self.assertFalse(
                any(use.startswith("actions/cache/save@") for use in uses),
                f"{job_id} must never receive a cache save action",
            )
            restore = next(
                step
                for step in job["steps"]
                if step.get("uses", "").startswith("actions/cache/restore@")
            )
            restore_text = yaml.safe_dump(restore, sort_keys=False)
            self.assertIn("steps.assert-target.outputs.pr-number", restore_text)
            self.assertNotIn("inputs.target_ref", restore_text)

        self.assertNotIn("appt-pr-gradle-", verify_text)
        self.assertNotIn("publish-gradle-warm-state", verify_text)

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
