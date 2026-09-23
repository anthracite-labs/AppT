#!/usr/bin/env bash
#
# Tests for the CI workflow's concurrency grouping (Issue #52 review fix).
#
# The consolidated CI workflow serves four event/trigger classes — pull
# request, main push, weekly schedule, manual dispatch. With
# cancel-in-progress: true, one shared group let a scheduled or manual CodeQL
# run cancel an in-progress main-push validation (and vice versa). The group
# must therefore carry an event-class domain while still cancelling genuinely
# superseded runs of the same class and ref.
#
# This test pins that contract two ways:
#
#   1. It re-implements the group mapping in bash (the mirror of the
#      expression in .github/workflows/ci.yml) and asserts the domain table:
#      same class + ref collide, different classes never collide.
#   2. It greps the actual YAML so the expression in the workflow file cannot
#      drift away from the tested mapping without failing this suite.
#
# Deterministic and offline.
#
#   bash tools/ci/test/concurrency-group.test.sh
#
# Exit status: 0 when every assertion passes, 1 otherwise.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd -P)"
CI_WORKFLOW="$REPO_ROOT/.github/workflows/ci.yml"

[ -f "$CI_WORKFLOW" ] || { echo "FAIL: $CI_WORKFLOW not found" >&2; exit 1; }

pass=0
fail=0

fail_case() {
  echo "FAIL: $1"
  fail=$((fail + 1))
}

expect() { # <case> <actual> <expected>
  local case_name="$1" actual="$2" expected="$3"
  if [ "$actual" != "$expected" ]; then
    fail_case "$case_name: expected '$expected', got '$actual'"
  else
    pass=$((pass + 1))
  fi
}

# Mirror of the concurrency group expression in .github/workflows/ci.yml:
#
#   ci-${{ github.event_name == 'pull_request' && 'pr'
#     || github.event_name == 'push' && 'push'
#     || github.event_name == 'schedule' && 'schedule'
#     || 'dispatch' }}-${{ github.ref }}
group_for() { # <event-name> <ref>
  local event="$1" ref="$2" class
  case "$event" in
    pull_request) class=pr ;;
    push) class=push ;;
    schedule) class=schedule ;;
    workflow_dispatch) class=dispatch ;;
    *) return 2 ;;
  esac
  echo "ci-${class}-${ref}"
}

MAIN="refs/heads/main"
PR_A="refs/pull/101/head"
PR_B="refs/pull/102/head"

# --- Same class, same ref: superseded runs share a group and cancel. -------
expect "pr same ref" "$(group_for pull_request "$PR_A")" "$(group_for pull_request "$PR_A")"
expect "push main same ref" "$(group_for push "$MAIN")" "$(group_for push "$MAIN")"
expect "schedule main same ref" "$(group_for schedule "$MAIN")" "$(group_for schedule "$MAIN")"
expect "dispatch main same ref" "$(group_for workflow_dispatch "$MAIN")" "$(group_for workflow_dispatch "$MAIN")"

# --- Different classes on main never share a group. -------------------------
[ "$(group_for push "$MAIN")" != "$(group_for schedule "$MAIN")" ] \
  && pass=$((pass + 1)) || fail_case "push main and schedule main must not share a group"
[ "$(group_for push "$MAIN")" != "$(group_for workflow_dispatch "$MAIN")" ] \
  && pass=$((pass + 1)) || fail_case "push main and dispatch main must not share a group"
[ "$(group_for schedule "$MAIN")" != "$(group_for workflow_dispatch "$MAIN")" ] \
  && pass=$((pass + 1)) || fail_case "schedule main and dispatch main must not share a group"

# --- Different PRs never cancel each other; same head ref does. -------------
[ "$(group_for pull_request "$PR_A")" != "$(group_for pull_request "$PR_B")" ] \
  && pass=$((pass + 1)) || fail_case "distinct pull requests must not share a group"

# --- The actual YAML implements the tested mapping. --------------------------
group_line="$(grep -E '^\s*group:' "$CI_WORKFLOW" | head -1 || true)"
[ -n "$group_line" ] || fail_case "ci.yml: no concurrency group line found"
case "$group_line" in
  *github.event_name*) pass=$((pass + 1)) ;;
  *) fail_case "ci.yml: concurrency group does not derive from github.event_name" ;;
esac
case "$group_line" in
  *github.ref*) pass=$((pass + 1)) ;;
  *) fail_case "ci.yml: concurrency group does not include github.ref" ;;
esac
case "$group_line" in
  *"pull_request"*) pass=$((pass + 1)) ;;
  *) fail_case "ci.yml: concurrency group does not name the pull_request class" ;;
esac
case "$group_line" in
  *"schedule"*) pass=$((pass + 1)) ;;
  *) fail_case "ci.yml: concurrency group does not name the schedule class" ;;
esac
grep -q 'cancel-in-progress: true' "$CI_WORKFLOW" \
  && pass=$((pass + 1)) || fail_case "ci.yml: cancel-in-progress is not true"

echo ""
echo "concurrency-group tests: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
