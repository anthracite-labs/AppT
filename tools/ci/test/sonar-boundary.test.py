"""Offline Sonar control/data-boundary proofs; no scanner, builds or secrets."""

from copy import deepcopy
import importlib.util
import json
import os
import re
from pathlib import Path
import shlex
import sys
import tempfile
import unittest

import yaml

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "sonar_inputs", ROOT / "tools/ci/validate-sonar-inputs.py"
)
INPUTS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INPUTS)


class SonarInputTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.paths = [root / name for name in ("source", "android", "backend", "work", "home")]
        for path in self.paths[:3]:
            path.mkdir()
        report = self.paths[1] / INPUTS.KOVER_REPORTS[0]
        report.parent.mkdir(parents=True)
        report.write_text("<report/>")
        (self.paths[2] / "lcov.info").write_text("SF:src/index.ts\nend_of_record\n")

    def test_target_settings_and_scripts_are_only_data(self):
        contents = (
            "sonar.host.url=https://example.invalid\n"
            "sonar.scanner.javaExePath=./evil-java\nsonar.modules=evil\n"
        )
        config = self.paths[0] / "sonar-project.properties"
        config.write_text(contents)
        (self.paths[0] / "evil-java").write_text("this is inert fixture data, not executed")
        # Artifact-provided control filenames must not become scanner settings.
        (self.paths[1] / "sonar-project.properties").write_text(contents)
        INPUTS.validate_inputs(*self.paths)
        self.assertEqual(config.read_text(), contents)
        self.assertEqual(list(self.paths[3].iterdir()), [])
        self.assertEqual(list(self.paths[4].iterdir()), [])

    def test_missing_and_empty_coverage_fail_closed(self):
        for path in (self.paths[2] / "lcov.info", self.paths[1] / INPUTS.KOVER_REPORTS[0]):
            original = path.read_bytes()
            for missing in (False, True):
                if missing:
                    path.unlink()
                else:
                    path.write_bytes(b"")
                with self.subTest(path=path, missing=missing), self.assertRaisesRegex(
                    ValueError, "coverage is missing or empty"
                ):
                    INPUTS.validate_inputs(*self.paths)
                self.assertFalse(self.paths[3].exists())
                path.write_bytes(original)

    def test_symlinks_in_each_input_tree_fail_before_scan(self):
        for root in self.paths[:3]:
            for target in (self.paths[2] / "lcov.info", self.paths[2], root / "missing"):
                link = root / "link"
                link.symlink_to(target)
                with self.subTest(root=root, target=target), self.assertRaisesRegex(
                    ValueError, "symlink or special file"
                ):
                    INPUTS.validate_inputs(*self.paths)
                link.unlink()

    def test_symlink_root_fails(self):
        link = Path(self.temp.name) / "linked-source"
        link.symlink_to(self.paths[0], target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "real directory"):
            INPUTS.validate_inputs(link, *self.paths[1:])

    def test_special_file_is_rejected_without_opening_it(self):
        os.mkfifo(self.paths[0] / "fifo")
        with self.assertRaisesRegex(ValueError, "special file"):
            INPUTS.validate_inputs(*self.paths)

    def test_existing_scanner_state_is_rejected(self):
        for path in self.paths[3:]:
            path.mkdir()
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "start empty"):
                INPUTS.validate_inputs(*self.paths)
            path.rmdir()

    def test_scanner_state_cannot_overlap_source_or_artifacts(self):
        for root in self.paths[:3]:
            with self.subTest(root=root), self.assertRaisesRegex(ValueError, "separate"):
                INPUTS.validate_inputs(*self.paths[:3], root / "state", self.paths[4])


class SonarWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.workflow = yaml.safe_load((ROOT / ".github/workflows/verify.yml").read_text())

    def assert_boundary(self, workflow):
        job = workflow["jobs"]["quality-platform"]
        self.assertEqual(job["permissions"], {"contents": "read"})
        self.assertEqual(job.get("cache-mode", workflow["cache-mode"]), "read")
        self.assertEqual(job["needs"], ["android-unit", "backend-test"])
        self.assertNotIn("defaults", job)
        steps = job["steps"]
        checkouts = [step for step in steps if step.get("uses", "").startswith("actions/checkout@")]
        self.assertEqual(len(checkouts), 2)
        anchor, target = checkouts
        self.assertEqual(anchor["with"], {"persist-credentials": False})
        self.assertEqual(target["with"], {
            "ref": "${{ steps.assert-target.outputs.sha || github.sha }}",
            "path": "sonar-target",
            "persist-credentials": False,
        })
        assertion = next(step for step in steps if step.get("id") == "assert-target")
        self.assertLess(steps.index(anchor), steps.index(assertion))
        self.assertLess(steps.index(assertion), steps.index(target))
        downloads = [step for step in steps if step.get("uses", "").startswith("actions/download-artifact@")]
        self.assertEqual([step["with"] for step in downloads], [
            {"name": "kover-coverage-report", "path": "${{ runner.temp }}/sonar-kover"},
            {"name": "backend-coverage-report", "path": "${{ runner.temp }}/sonar-backend"},
        ])
        validate = next(step for step in steps if "validate-sonar-inputs.py" in step.get("run", ""))
        self.assertTrue(validate["run"].startswith("python3 tools/ci/validate-sonar-inputs.py "))
        for argument in (
            "--source sonar-target", '--android "$SCAN_TEMP/sonar-kover"',
            '--backend "$SCAN_TEMP/sonar-backend"', '--work "$SCAN_TEMP/sonar-work"',
            '--home "$SCAN_TEMP/sonar-home"',
        ):
            self.assertIn(argument, validate["run"])
        self.assertTrue(all(steps.index(step) < steps.index(validate) for step in downloads))
        scanner = steps[-1]
        self.assertEqual(scanner["uses"],
                         "sonarsource/sonarqube-scan-action@ba9859eae8dd6bd29e412f25ddbbef3d032000f4")
        self.assertEqual(set(scanner["with"]), {"args", "scannerVersion"})
        self.assertEqual(scanner["with"]["scannerVersion"], "8.1.0.6389")
        self.assertNotIn("projectBaseDir", scanner["with"],
                         "explicit trusted settings must use the action's anchor-root default")
        self.assertEqual(scanner["env"], {"SONAR_TOKEN": "${{ secrets.SONAR_TOKEN }}"})
        args = scanner["with"]["args"]
        for expression, value in (
            ("${{ github.workspace }}", "/anchor"),
            ("${{ runner.temp }}", "/scratch"),
            ("${{ steps.assert-target.outputs.sha || github.sha }}", "a" * 40),
        ):
            args = args.replace(expression, value)
        self.assertNotIn("${{", args, "no target-controlled expression may enter scanner options")
        # The pinned action uses string-argv 0.3.2, NOT shell/shlex semantics:
        # -Dkey="value" retains literal quote bytes. Restrict the workflow to
        # whole-argument quotes, where both parsers agree (also for spaces).
        self.assertRegex(args, r'\A(?:\s*"-D[^"\\\n]+")+\s*\Z')
        options = shlex.split(args)
        properties = dict(option.removeprefix("-D").split("=", 1) for option in options)
        self.assertEqual(len(properties), len(options), "duplicate properties must not hide overrides")
        self.assertEqual(properties, {
            "project.settings": "/anchor/sonar-project.properties",
            "sonar.sources": "sonar-target/app/src/main,sonar-target/samsung/src/main,sonar-target/backend/src",
            "sonar.tests": "sonar-target/app/src/test,sonar-target/backend/test",
            "sonar.java.binaries": "sonar-target/app/build/intermediates/javac/debug/classes,sonar-target/app/build/tmp/kotlin-classes/debug",
            "sonar.modules": "",
            "sonar.host.url": "https://sonarcloud.io",
            "sonar.scanner.sonarcloudUrl": "https://sonarcloud.io",
            "sonar.scanner.apiBaseUrl": "https://api.sonarcloud.io",
            "sonar.working.directory": "/scratch/sonar-work",
            "sonar.userHome": "/scratch/sonar-home",
            "sonar.coverage.jacoco.xmlReportPaths": ",".join(
                "/scratch/sonar-kover/" + path for path in INPUTS.KOVER_REPORTS
            ),
            "sonar.javascript.lcov.reportPaths": "/scratch/sonar-backend/lcov.info",
            "sonar.scm.revision": "a" * 40,
        })
        for step in steps[steps.index(target) + 1:]:
            self.assertNotIn("working-directory", step, "commands must not run from the target tree")
            self.assertFalse(step.get("uses", "").startswith(("./", "$/")))
            if "secrets." in json.dumps(step):
                self.assertLess(steps.index(validate), steps.index(step))
                self.assertIn(step.get("id", "scanner"), ("check_token", "scanner"))
        self.assertEqual([step["run"] for step in steps if "run" in step], [
            validate["run"], next(step["run"] for step in steps if step.get("id") == "check_token")
        ], "no target build, install, or script can run in the secret-bearing job")

    def test_actual_workflow_owns_all_secret_bearing_scanner_controls(self):
        self.assert_boundary(self.workflow)

    def test_target_control_of_scanner_settings_is_detected(self):
        for old, new in (
            ('"-Dproject.settings=${{ github.workspace }}/sonar-project.properties"', ""),
            ("/sonar-project.properties", "/sonar-target/sonar-project.properties"),
            ("-Dsonar.modules=", "-Dsonar.modules=untrusted"),
            ("https://sonarcloud.io", "https://example.invalid"),
            ("sonar-work", "sonar-target/.scannerwork"),
        ):
            candidate = deepcopy(self.workflow)
            scanner = candidate["jobs"]["quality-platform"]["steps"][-1]
            scanner["with"]["args"] = scanner["with"]["args"].replace(old, new)
            with self.subTest(old=old), self.assertRaises(AssertionError):
                self.assert_boundary(candidate)

    def test_target_project_base_dir_cannot_be_reintroduced(self):
        for value in ("sonar-target", ".", ""):
            candidate = deepcopy(self.workflow)
            candidate["jobs"]["quality-platform"]["steps"][-1]["with"]["projectBaseDir"] = value
            with self.subTest(value=value), self.assertRaises(AssertionError):
                self.assert_boundary(candidate)
        candidate = deepcopy(self.workflow)
        candidate["jobs"]["quality-platform"]["steps"][-1]["with"]["args"] += (
            ' "-Dsonar.projectBaseDir=sonar-target"'
        )
        with self.assertRaises(AssertionError):
            self.assert_boundary(candidate)

    def test_each_analysis_path_is_explicitly_target_rooted(self):
        original = self.workflow["jobs"]["quality-platform"]["steps"][-1]["with"]["args"]
        for key in ("sonar.sources", "sonar.tests", "sonar.java.binaries"):
            option = re.search(r'"-D' + re.escape(key) + r'=([^"\n]+)"', original)
            self.assertIsNotNone(option)
            replacements = [""]  # Omitting the override would select anchor paths.
            for path in option[1].split(","):
                replacements.append(option[0].replace(path, path.removeprefix("sonar-target/")))
            for replacement in replacements:
                candidate = deepcopy(self.workflow)
                candidate["jobs"]["quality-platform"]["steps"][-1]["with"]["args"] = (
                    original.replace(option[0], replacement)
                )
                with self.subTest(key=key, replacement=replacement), self.assertRaises(AssertionError):
                    self.assert_boundary(candidate)

    def test_value_only_quotes_are_rejected_for_every_path_option(self):
        original = self.workflow["jobs"]["quality-platform"]["steps"][-1]["with"]["args"]
        for key in ("project.settings", "sonar.working.directory", "sonar.userHome",
                    "sonar.coverage.jacoco.xmlReportPaths", "sonar.javascript.lcov.reportPaths"):
            option = re.search(r'"-D' + re.escape(key) + r'=([^"\n]+)"', original)
            self.assertIsNotNone(option)
            # This is the old spelling. shlex alone would silently accept it,
            # but string-argv passes literal quotes to Scanner CLI's Conf.java.
            candidate = deepcopy(self.workflow)
            candidate["jobs"]["quality-platform"]["steps"][-1]["with"]["args"] = (
                original.replace(option[0], f'-D{key}="{option[1]}"')
            )
            with self.subTest(key=key), self.assertRaises(AssertionError):
                self.assert_boundary(candidate)

    def test_artifacts_cannot_overwrite_trusted_checkout(self):
        candidate = deepcopy(self.workflow)
        steps = candidate["jobs"]["quality-platform"]["steps"]
        download = next(step for step in steps if step.get("uses", "").startswith("actions/download-artifact@"))
        download["with"]["path"] = "."
        with self.assertRaises(AssertionError):
            self.assert_boundary(candidate)


if __name__ == "__main__":
    unittest.main(verbosity=2)
