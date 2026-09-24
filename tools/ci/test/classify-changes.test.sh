#!/usr/bin/env bash
#
# Tests for tools/ci/classify-changes.sh — the single decision point of the
# CI verification workflow (.github/workflows/ci.yml).
#
# The classifier is CI plumbing: it decides which expensive jobs run at all.
# It must therefore be provable without throwaway pull requests. Each case
# builds a throwaway git repository (baseline commit + a representative
# changed-file set) and asserts the exact job gates the workflow consumes,
# covering the scenarios required by Issue #52:
#
#   * docs-only pull request
#   * Android/Kotlin source pull request
#   * backend TypeScript pull request
#   * dependency / version-catalog / lockfile pull request
#   * workflow and security-configuration pull request
#   * merged-PR reuse on the resulting main push
#   * direct push to main (fail-closed)
#   * weekly schedule and manual dispatch (CodeQL only)
#   * pull request targeting a non-main branch
#   * invalid input handling
#
# Deterministic and offline: local git only, no network, no GitHub access.
#
#   bash tools/ci/test/classify-changes.test.sh
#
# Exit status: 0 when every assertion passes, 1 otherwise.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
CLASSIFIER="$SCRIPT_DIR/../classify-changes.sh"

[ -f "$CLASSIFIER" ] || {
  echo "FAIL: classifier not found at $CLASSIFIER" >&2
  exit 1
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

pass=0
fail=0
LAST_OUTPUT=""

note() { printf '%s\n' "$*"; }

fail_case() {
  note "FAIL: $1"
  fail=$((fail + 1))
}

expect_gate() { # <case> <key> <expected>
  local case_name="$1" key="$2" expected="$3" actual
  actual="$(printf '%s\n' "$LAST_OUTPUT" | awk -F= -v k="$key" '$1 == k { print $2 }')"
  if [ "$actual" != "$expected" ]; then
    fail_case "$case_name: $key expected '$expected', got '${actual:-<absent>}'"
  else
    pass=$((pass + 1))
  fi
}

# run_case <case> <event> <base-ref> <pr-count> [file]...
#
# Builds a repository with a baseline commit, applies the listed changed
# files, then runs the classifier with base = baseline and head = the new
# commit, exactly as the workflow's `changes` job would.
run_case() {
  local case_name="$1" event="$2" base_ref="$3" pr_count="$4"
  shift 4
  local repo base_sha head_sha f
  repo="$tmp/$case_name"
  mkdir -p "$repo"
  git -C "$repo" init -q
  git -C "$repo" config user.email "ci-classifier-test@appt.local"
  git -C "$repo" config user.name "AppT CI classifier test"
  echo baseline >"$repo/seed.txt"
  git -C "$repo" add -A
  git -C "$repo" commit -qm baseline
  base_sha="$(git -C "$repo" rev-parse HEAD)"
  for f in "$@"; do
    mkdir -p "$repo/$(dirname "$f")"
    echo changed >"$repo/$f"
  done
  git -C "$repo" add -A
  if ! git -C "$repo" diff --cached --quiet; then
    git -C "$repo" commit -qm "case: $case_name"
  fi
  head_sha="$(git -C "$repo" rev-parse HEAD)"
  if LAST_OUTPUT="$(cd "$repo" && bash "$CLASSIFIER" "$event" "$base_sha" "$head_sha" "$pr_count" "$base_ref" 2>/dev/null)"; then
    LAST_EXIT=0
  else
    LAST_EXIT=$?
  fi
}

# expect_run <case> <run_secret_scan> <run_android> <run_detekt> <run_welcome>
#            <run_backend> <run_codeql> <run_dependency_review> <pr_validated>
expect_run() {
  local case_name="$1"
  shift
  expect_gate "$case_name" run_secret_scan "$1"
  expect_gate "$case_name" run_android "$2"
  expect_gate "$case_name" run_detekt "$3"
  expect_gate "$case_name" run_welcome "$4"
  expect_gate "$case_name" run_backend "$5"
  expect_gate "$case_name" run_codeql "$6"
  expect_gate "$case_name" run_dependency_review "$7"
  expect_gate "$case_name" pr_validated "$8"
}

T=true
F=false

# ---------------------------------------------------------------------------
# 1. Docs-only pull request: only the cheap always-required work runs.
# ---------------------------------------------------------------------------
note "== docs-only-pull"
run_case docs-only-pull pull_request main 0 docs/README.md
[ "$LAST_EXIT" -eq 0 ] || fail_case "docs-only-pull: classifier exited $LAST_EXIT"
expect_run docs-only-pull "$T" "$F" "$F" "$F" "$F" "$F" "$F" "$F"
expect_gate docs-only-pull android "$F"
expect_gate docs-only-pull dependency "$F"

# ---------------------------------------------------------------------------
# 2. Android/Kotlin source: Android floor, detekt, emulator acceptance and
#    CodeQL; not backend, not dependency review.
# ---------------------------------------------------------------------------
note "== android-source-pull"
run_case android-source-pull pull_request main 0 app/src/main/kotlin/appt/WelcomeScreen.kt
[ "$LAST_EXIT" -eq 0 ] || fail_case "android-source-pull: classifier exited $LAST_EXIT"
expect_run android-source-pull "$T" "$T" "$T" "$T" "$F" "$T" "$F" "$F"
expect_gate android-source-pull backend "$F"
expect_gate android-source-pull dependency "$F"

# ---------------------------------------------------------------------------
# 3. Backend TypeScript: backend checks and CodeQL; no Android-side jobs.
# ---------------------------------------------------------------------------
note "== backend-source-pull"
run_case backend-source-pull pull_request main 0 backend/src/entitlements.ts
[ "$LAST_EXIT" -eq 0 ] || fail_case "backend-source-pull: classifier exited $LAST_EXIT"
expect_run backend-source-pull "$T" "$F" "$F" "$F" "$T" "$T" "$F" "$F"
expect_gate backend-source-pull android "$F"

# ---------------------------------------------------------------------------
# 4. Dependency / version catalog / lockfile: dependency review runs, and the
#    Gradle-side gates that a catalog change can affect all run.
# ---------------------------------------------------------------------------
note "== dependency-change-pull"
run_case dependency-change-pull pull_request main 0 gradle/libs.versions.toml app/gradle.lockfile
[ "$LAST_EXIT" -eq 0 ] || fail_case "dependency-change-pull: classifier exited $LAST_EXIT"
expect_run dependency-change-pull "$T" "$T" "$T" "$T" "$F" "$T" "$T" "$F"
expect_gate dependency-change-pull dependency "$T"

# ---------------------------------------------------------------------------
# 5. Workflow configuration: the file that carries every job changed, so
#    every gate exercises itself once before merge (fail-closed).
# ---------------------------------------------------------------------------
note "== workflow-config-pull"
run_case workflow-config-pull pull_request main 0 .github/workflows/ci.yml
[ "$LAST_EXIT" -eq 0 ] || fail_case "workflow-config-pull: classifier exited $LAST_EXIT"
expect_run workflow-config-pull "$T" "$T" "$T" "$T" "$T" "$T" "$T" "$F"
expect_gate workflow-config-pull ci_config "$T"

# ---------------------------------------------------------------------------
# 6. The classifier itself changed: same fail-closed rule as the workflow
#    file — every gate re-runs, the new classifier cannot excuse itself.
# ---------------------------------------------------------------------------
note "== classifier-change-pull"
run_case classifier-change-pull pull_request main 0 tools/ci/classify-changes.sh
[ "$LAST_EXIT" -eq 0 ] || fail_case "classifier-change-pull: classifier exited $LAST_EXIT"
expect_run classifier-change-pull "$T" "$T" "$T" "$T" "$T" "$T" "$T" "$F"
expect_gate classifier-change-pull ci_config "$T"

# ---------------------------------------------------------------------------
# 7. tools/security configuration: only CodeQL analysis is affected.
# ---------------------------------------------------------------------------
note "== security-script-pull"
run_case security-script-pull pull_request main 0 tools/security/run.sh
[ "$LAST_EXIT" -eq 0 ] || fail_case "security-script-pull: classifier exited $LAST_EXIT"
expect_run security-script-pull "$T" "$F" "$F" "$F" "$F" "$T" "$F" "$F"
expect_gate security-script-pull ci_config "$F"

# ---------------------------------------------------------------------------
# 8. Main push created by merging an already-validated PR: secret scanning
#    still runs; no heavy job is repeated.
# ---------------------------------------------------------------------------
note "== merged-pr-main-push"
run_case merged-pr-main-push push main 1 app/src/main/kotlin/appt/WelcomeScreen.kt
[ "$LAST_EXIT" -eq 0 ] || fail_case "merged-pr-main-push: classifier exited $LAST_EXIT"
expect_run merged-pr-main-push "$T" "$F" "$F" "$F" "$F" "$F" "$F" "$T"
expect_gate merged-pr-main-push android "$T"

# ---------------------------------------------------------------------------
# 9. Direct push to main: no association, so every relevant heavy gate runs
#    (fail-closed).
# ---------------------------------------------------------------------------
note "== direct-main-push"
run_case direct-main-push push main 0 app/src/main/kotlin/appt/WelcomeScreen.kt backend/src/entitlements.ts
[ "$LAST_EXIT" -eq 0 ] || fail_case "direct-main-push: classifier exited $LAST_EXIT"
expect_run direct-main-push "$T" "$T" "$T" "$T" "$T" "$T" "$F" "$F"

# ---------------------------------------------------------------------------
# 10/11. Weekly schedule and manual dispatch: CodeQL analysis only — no diff
#     exists on these events, so no other job starts.
# ---------------------------------------------------------------------------
note "== schedule-run"
run_case schedule-run schedule "" 0
[ "$LAST_EXIT" -eq 0 ] || fail_case "schedule-run: classifier exited $LAST_EXIT"
expect_run schedule-run "$F" "$F" "$F" "$F" "$F" "$T" "$F" "$T"

note "== dispatch-run"
run_case dispatch-run workflow_dispatch "" 0
[ "$LAST_EXIT" -eq 0 ] || fail_case "dispatch-run: classifier exited $LAST_EXIT"
expect_run dispatch-run "$F" "$F" "$F" "$F" "$F" "$T" "$F" "$T"

# ---------------------------------------------------------------------------
# 12. PR targeting a non-main branch: the ordinary gates still run (as with
#     the pre-consolidation ci.yml), but CodeQL analysis and dependency
#     review stay main-target checks.
# ---------------------------------------------------------------------------
note "== non-main-target-pull"
run_case non-main-target-pull pull_request dev 0 gradle/libs.versions.toml
[ "$LAST_EXIT" -eq 0 ] || fail_case "non-main-target-pull: classifier exited $LAST_EXIT"
expect_run non-main-target-pull "$T" "$T" "$T" "$T" "$F" "$F" "$F" "$F"
expect_gate non-main-target-pull dependency "$T"
expect_gate non-main-target-pull codeql "$T"

# ---------------------------------------------------------------------------
# 13/14. Invalid input fails closed with a non-zero status and no gates.
# ---------------------------------------------------------------------------
note "== bad-event"
run_case bad-event not_an_event main 0 docs/README.md
[ "$LAST_EXIT" -ne 0 ] || fail_case "bad-event: expected non-zero exit, got 0"
[ -z "$LAST_OUTPUT" ] || fail_case "bad-event: expected no gate output"

note "== bad-pr-count"
run_case bad-pr-count pull_request main x docs/README.md
[ "$LAST_EXIT" -ne 0 ] || fail_case "bad-pr-count: expected non-zero exit, got 0"
[ -z "$LAST_OUTPUT" ] || fail_case "bad-pr-count: expected no gate output"

# ---------------------------------------------------------------------------
note ""
note "classify-changes tests: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
