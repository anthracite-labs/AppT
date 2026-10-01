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
GRADLE_DIAGNOSTIC_JOBS = {
    "app-unit",
    "samsung-unit",
    "android-static",
    "android-build",
    "device",
}
TRUSTED_CACHE_WRITER_JOB = "publish-gradle-warm-state"


def validate_trusted_cache_writer(job_id, job):
    """Prove the one cache writer is trusted-only and cannot execute target code."""
    assert job_id == TRUSTED_CACHE_WRITER_JOB
    assert job.get("cache-mode") == "write-only", (
        "trusted cache publisher must be write-only, never read/write"
    )
    assert job.get("permissions") == {"contents": "read"}, (
        "trusted cache publisher must not gain repository write permissions"
    )

    rendered = yaml.safe_dump(job)
    assert "actions/checkout@" not in rendered, (
        "trusted cache publisher must never check out target code"
    )
    assert "./.github/actions/" not in rendered, (
        "trusted cache publisher must not execute repository-local actions"
    )
    assert "${{ secrets." not in rendered, (
        "trusted cache publisher must not receive repository secrets"
    )

    steps = {step.get("name"): step for step in job.get("steps", [])}
    self_contained = {
        "Download current diagnostic warm-state artifact",
        "Validate and stage PR-local Gradle build cache",
        "Save trusted PR-local Gradle build cache",
    }
    assert set(steps) == self_contained, (
        "trusted cache publisher must stay limited to download, validation, and cache save"
    )

    download = steps["Download current diagnostic warm-state artifact"]
    assert "actions/download-artifact@" in download["uses"]
    assert download["with"]["artifact-ids"] == "${{ env.ARTIFACT_ID }}"

    validate = steps["Validate and stage PR-local Gradle build cache"]["run"]
    assert 'path.parts[0] != "build-cache-1"' in validate
    assert "member.isfile() or member.isdir()" in validate
    assert "max_bytes = 512 * 1024 * 1024" in validate
    assert "max_files = 100_000" in validate
    assert "filter=\"data\"" in validate

    save = steps["Save trusted PR-local Gradle build cache"]
    assert "actions/cache/save@" in save["uses"]
    assert save["with"]["path"] == "~/.gradle/caches/build-cache-1"
    assert "${{ env.PR_NUMBER }}" in save["with"]["key"]
    assert "${{ github.run_id }}" in save["with"]["key"]


def validate_cache_contract(workflow, *, executes_target, trusted_cache_writer_jobs=frozenset()):
    """Cap target-code jobs at read-only; allow only structurally proven trusted writers."""
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

        if not executes_target or effective_mode in READ_ONLY_MODES:
            continue

        assert job_id in trusted_cache_writer_jobs, (
            f"{job_id}: target workflow job must not receive cache-write capability"
        )
        validate_trusted_cache_writer(job_id, job)


def executes_validated_target(workflow):
    """Also cover future workflows using the repository's target assertion."""
    return any(
        "assert-dispatch-target" in step.get("uses", "")
        for job in workflow["jobs"].values()
        for step in job.get("steps", [])
    )


class WorkflowCacheContractTests(unittest.TestCase):
    @staticmethod
    def trusted_writers(name):
        return {TRUSTED_CACHE_WRITER_JOB} if name == "diagnose.yml" else set()

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
                    trusted_cache_writer_jobs=self.trusted_writers(path.name),
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
                        trusted_cache_writer_jobs=self.trusted_writers(name),
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
                    trusted_cache_writer_jobs=self.trusted_writers(name),
                )

    def test_write_capability_fails_at_workflow_and_untrusted_jobs(self):
        for name in sorted(TARGET_WORKFLOWS):
            workflow = yaml.safe_load((WORKFLOWS / name).read_text())
            trusted = self.trusted_writers(name)

            for mode in ("write", "write-only"):
                candidate = deepcopy(workflow)
                candidate["cache-mode"] = mode
                with self.subTest(workflow=name, mode=mode), self.assertRaises(AssertionError):
                    validate_cache_contract(
                        candidate,
                        executes_target=True,
                        trusted_cache_writer_jobs=trusted,
                    )

            for job_id in workflow["jobs"]:
                for mode in ("write", "write-only"):
                    candidate = deepcopy(workflow)
                    candidate["jobs"][job_id]["cache-mode"] = mode

                    if job_id == TRUSTED_CACHE_WRITER_JOB and mode == "write-only":
                        validate_cache_contract(
                            candidate,
                            executes_target=True,
                            trusted_cache_writer_jobs=trusted,
                        )
                        continue

                    with self.subTest(workflow=name, job=job_id, mode=mode):
                        with self.assertRaisesRegex(
                            AssertionError,
                            "cache-write capability|write-only",
                        ):
                            validate_cache_contract(
                                candidate,
                                executes_target=True,
                                trusted_cache_writer_jobs=trusted,
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

    def test_pr_local_gradle_warm_state_is_pr_bound_and_terminally_isolated(self):
        diagnose = yaml.safe_load((WORKFLOWS / "diagnose.yml").read_text())
        verify_text = (WORKFLOWS / "verify.yml").read_text()

        self.assertEqual(diagnose["cache-mode"], "read")
        self.assertNotIn("appt-pr-gradle-", verify_text)
        self.assertNotIn("publish-gradle-warm-state", verify_text)

        publisher = diagnose["jobs"][TRUSTED_CACHE_WRITER_JOB]
        validate_trusted_cache_writer(TRUSTED_CACHE_WRITER_JOB, publisher)
        self.assertEqual(set(publisher["needs"]), GRADLE_DIAGNOSTIC_JOBS)

        for job_id in sorted(GRADLE_DIAGNOSTIC_JOBS):
            with self.subTest(job=job_id):
                job = diagnose["jobs"][job_id]
                self.assertNotIn("cache-mode", job)
                self.assertEqual(
                    job["permissions"],
                    {"actions": "read", "contents": "read"},
                )
                steps = job["steps"]
                by_name = {step.get("name"): step for step in steps}

                restore = by_name["Restore PR-local Gradle build cache"]
                self.assertIn("actions/cache/restore@", restore["uses"])
                self.assertIn("steps.assert-target.outputs.pr-number", restore["if"])
                self.assertEqual(
                    restore["with"]["path"],
                    "~/.gradle/caches/build-cache-1",
                )
                self.assertIn(
                    "${{ steps.assert-target.outputs.pr-number }}",
                    restore["with"]["key"],
                )
                self.assertIn(
                    "appt-pr-gradle-${{ steps.assert-target.outputs.pr-number }}-",
                    restore["with"]["restore-keys"],
                )

                exact_checkout = by_name["Check out the exact expected SHA"]
                self.assertIn("actions/checkout@", exact_checkout["uses"])
                self.assertEqual(
                    exact_checkout["with"]["ref"],
                    "${{ steps.assert-target.outputs.sha }}",
                )

                names = [step.get("name") for step in steps]
                self.assertLess(
                    names.index("Restore PR-local Gradle build cache"),
                    names.index("Check out the exact expected SHA"),
                )

                package = by_name["Package PR-local Gradle warm state"]["run"]
                self.assertIn(
                    'cache_dir="$HOME/.gradle/caches/build-cache-1"',
                    package,
                )
                self.assertIn("524288", package)
                self.assertIn("536870912", package)

                upload = by_name["Upload PR-local Gradle warm state"]
                self.assertEqual(upload["with"]["retention-days"], 1)
                self.assertEqual(upload["with"]["compression-level"], 0)
                self.assertIn(
                    "${{ steps.assert-target.outputs.pr-number }}",
                    upload["with"]["name"],
                )
                self.assertIn("${{ github.run_id }}", upload["with"]["name"])

    def test_dependency_state_refresh_is_read_only_and_bounded(self):
        diagnose = yaml.safe_load((WORKFLOWS / "diagnose.yml").read_text())
        job = diagnose["jobs"]["dependency-state"]

        self.assertEqual(job["permissions"], {"contents": "read"})
        self.assertNotIn("cache-mode", job)

        steps = {step.get("name"): step for step in job["steps"]}
        exact_checkout = steps["Check out the exact expected SHA"]
        self.assertEqual(
            exact_checkout["with"]["ref"],
            "${{ steps.assert-target.outputs.sha }}",
        )
        self.assertEqual(exact_checkout["with"]["persist-credentials"], False)

        boundary = steps["Validate bounded lockfile output"]["run"]
        for path in (
            "gradle.lockfile",
            "settings-gradle.lockfile",
            "app/gradle.lockfile",
            "samsung/gradle.lockfile",
            "macrobenchmark/gradle.lockfile",
        ):
            self.assertIn(f'"{path}"', boundary)
        self.assertNotIn("verification-metadata.xml", boundary)
        self.assertIn("Unexpected tracked change", boundary)
        self.assertIn("[ -L \"$path\" ]", boundary)
        self.assertIn("5242880", boundary)

        upload = steps["Upload generated Gradle lock state"]
        self.assertIn("actions/upload-artifact@", upload["uses"])
        self.assertEqual(upload["with"]["retention-days"], 7)
        self.assertEqual(
            upload["with"]["path"],
            "${{ runner.temp }}/dependency-state/**",
        )

        rendered = yaml.safe_dump(job)
        self.assertNotIn("contents: write", rendered)
        self.assertNotIn("secrets.", rendered)
        self.assertNotIn("git push", rendered)

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
            validate_cache_contract(
                workflow,
                executes_target=executes_validated_target(workflow),
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
