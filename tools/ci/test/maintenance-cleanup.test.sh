#!/usr/bin/env bash
#
# Tests for tools/ci/maintenance-cleanup-plan.sh and
# tools/ci/maintenance-cleanup-operations.sh (Issue #52 review fix).
#
# After consolidation, the three maintenance operations share one
# Maintenance workflow_id. Grouping deletions by workflow_id alone let a
# cleanup run delete the latest export/cache-purge evidence. These tests pin
# the fixed contract against a deterministic fixture of the Actions API run
# shape:
#
#   * the newest run of every non-Maintenance workflow is preserved, older
#     completed runs are deletion candidates (unchanged from PR #50);
#   * for the Maintenance workflow, the newest run of every OPERATION is
#     preserved — a cleanup run may not delete the latest export or
#     cache-purge run;
#   * the current run is never a deletion candidate;
#   * active/queued runs are never deleted;
#   * a Maintenance run whose operation could not be resolved is
#     quarantined: preserved and reported, never deleted;
#   * the resolver's job selection (the pure resolve_job_name_from_jobs
#     seam) is pinned against the real GitHub Jobs API shape: skipped jobs
#     are status=completed with conclusion=skipped, and ambiguous, empty
#     or unexpected job sets stay unresolved.
#
# A cross-file check also pins that every `operation` choice in
# .github/workflows/maintenance.yml has a job whose name the operations
# resolver recognizes, so the identity cannot drift.
#
# Deterministic and offline.
#
#   bash tools/ci/test/maintenance-cleanup.test.sh
#
# Exit status: 0 when every assertion passes, 1 otherwise.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd -P)"
PLAN_SCRIPT="$SCRIPT_DIR/../maintenance-cleanup-plan.sh"
OPS_SCRIPT="$SCRIPT_DIR/../maintenance-cleanup-operations.sh"
MAINT_WORKFLOW="$REPO_ROOT/.github/workflows/maintenance.yml"

for f in "$PLAN_SCRIPT" "$OPS_SCRIPT" "$MAINT_WORKFLOW"; do
  [ -f "$f" ] || { echo "FAIL: $f not found" >&2; exit 1; }
done

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

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

# Sourcing exposes the resolver's pure functions for offline testing; the
# script's direct-execution branch is guarded by BASH_SOURCE, so sourcing
# performs no API calls.
# shellcheck disable=SC1091
source "$OPS_SCRIPT"

# ---------------------------------------------------------------------------
# Fixture: the Actions API run shape as fetched by the cleanup job
# (a flat array of run objects — see make_fixture).
#
#   CI (workflow_id 111): three completed runs → keep newest, delete two.
#   Maintenance (workflow_id 222):
#     export-dependabot: 10 completed, 11 completed, 14 queued (newest)
#     purge-actions-caches: 12 completed (newest), 17 in_progress (older)
#     cleanup-workflow-runs: 13 completed, 99 in_progress (current run)
#     unresolved: 15 completed, no operation in the map
# ---------------------------------------------------------------------------
CURRENT_RUN=99

# The fetch step (unchanged from PR #50) writes a FLAT array of run objects:
#   gh api --paginate --slurp ... | jq '[.[].workflow_runs[]]' > all-runs.json
make_fixture() {
  local out="$1"
  cat > "$out" <<'EOF'
  [
    {"id": 1,  "workflow_id": 111, "name": "CI",          "event": "push",             "status": "completed", "conclusion": "success", "created_at": "2026-09-01T00:00:00Z", "head_branch": "main"},
    {"id": 2,  "workflow_id": 111, "name": "CI",          "event": "push",             "status": "completed", "conclusion": "failure", "created_at": "2026-09-02T00:00:00Z", "head_branch": "main"},
    {"id": 3,  "workflow_id": 111, "name": "CI",          "event": "push",             "status": "completed", "conclusion": "success", "created_at": "2026-09-03T00:00:00Z", "head_branch": "main"},
    {"id": 10, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch","status": "completed", "conclusion": "success", "created_at": "2026-09-10T00:00:00Z", "head_branch": "main"},
    {"id": 11, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch","status": "completed", "conclusion": "success", "created_at": "2026-09-11T00:00:00Z", "head_branch": "main"},
    {"id": 12, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch","status": "completed", "conclusion": "success", "created_at": "2026-09-12T00:00:00Z", "head_branch": "main"},
    {"id": 13, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch","status": "completed", "conclusion": "success", "created_at": "2026-09-13T00:00:00Z", "head_branch": "main"},
    {"id": 14, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch","status": "queued",    "conclusion": null,      "created_at": "2026-09-14T00:00:00Z", "head_branch": "main"},
    {"id": 15, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch","status": "completed", "conclusion": "success", "created_at": "2026-09-15T00:00:00Z", "head_branch": "main"},
    {"id": 17, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch","status": "in_progress","conclusion": null,      "created_at": "2026-09-11T12:00:00Z", "head_branch": "main"},
    {"id": 99, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch","status": "in_progress","conclusion": null,      "created_at": "2026-09-19T00:00:00Z", "head_branch": "main"}
  ]
EOF
}

# The raw page-array shape (as --slurp emits, before the fetch step flattens
# it) is tolerated by the scripts: one page object holding all the runs.
make_fixture_page_shape() {
  local out="$1"
  jq '[{total_count: length, workflow_runs: .}]' "$tmp/all-runs.json" > "$out"
}

make_operations() {
  local out="$1"
  cat > "$out" <<'EOF'
  [
    {"id": 10, "operation": "export-dependabot"},
    {"id": 11, "operation": "export-dependabot"},
    {"id": 14, "operation": "export-dependabot"},
    {"id": 12, "operation": "purge-actions-caches"},
    {"id": 17, "operation": "purge-actions-caches"},
    {"id": 13, "operation": "cleanup-workflow-runs"},
    {"id": 99, "operation": "cleanup-workflow-runs"}
  ]
EOF
}

make_fixture "$tmp/all-runs.json"
make_operations "$tmp/operations.json"

out_dir="$tmp/plan"
mkdir -p "$out_dir"
if LAST_OUTPUT="$(GITHUB_RUN_ID="$CURRENT_RUN" bash "$PLAN_SCRIPT" "$tmp/all-runs.json" "$tmp/operations.json" "$out_dir" 2>&1)"; then
  PLANNER_EXIT=0
else
  PLANNER_EXIT=$?
fi
[ "$PLANNER_EXIT" -eq 0 ] || echo "planner output: $LAST_OUTPUT"
expect "planner exit" "$PLANNER_EXIT" 0

# tsv_ids <file> [id-column] — ids of a plan TSV (delete/active/quarantined
# carry the id in column 1; kept carries it in column 3).
tsv_ids() { # <file> [column]
  local f="$1" col="${2:-1}"
  [ -f "$f" ] || { echo "MISSING:$f"; return 0; }
  cut -f"$col" "$f" | sort -n | tr '\n' ' ' | sed 's/ $//'
}

# --- Deletions: only older COMPLETED runs, never the current, never
# --- active/queued, never quarantined; one group per operation. -------------
expect "delete ids" "$(tsv_ids "$out_dir/delete-runs.tsv")" "1 2 10 11 13"

# --- Preserved: newest per workflow / per operation, incl. the current run.
# --- (The quarantined run is listed in quarantined-runs.tsv, not kept.) ----
expect "kept ids" "$(tsv_ids "$out_dir/kept-runs.tsv" 3)" "3 12 14 99"

# --- Older active runs are preserved, never deleted. --------------------------
expect "active-older ids" "$(tsv_ids "$out_dir/active-older-runs.tsv")" "17"

# --- Quarantine: unresolved Maintenance run is reported, never deleted. ------
expect "quarantined ids" "$(tsv_ids "$out_dir/quarantined-runs.tsv")" "15"

# --- The current run must never appear in the delete plan. -------------------
if [ -f "$out_dir/delete-runs.tsv" ] && grep -q "^${CURRENT_RUN}" "$out_dir/delete-runs.tsv"; then
  fail_case "current run $CURRENT_RUN appears in the delete plan"
else
  pass=$((pass + 1))
fi

# --- Delete rows carry the operation column (8 columns, op last). ------------
if [ -f "$out_dir/delete-runs.tsv" ]; then
  expect "delete row column count" "$(awk -F'\t' '{print NF}' "$out_dir/delete-runs.tsv" | sort -u | tr '\n' ' ' | sed 's/ $//')" "8"
  expect "delete row 13 operation" "$(awk -F'\t' '$1 == 13 {print $8}' "$out_dir/delete-runs.tsv")" "cleanup-workflow-runs"
else
  fail_case "delete-runs.tsv missing"
fi

# --- Summary mentions the quarantined run. ------------------------------------
case "$LAST_OUTPUT" in
  *"Quarantined (operation unresolved, never deleted): **1**"*) pass=$((pass + 1)) ;;
  *) fail_case "planner summary does not report the quarantined run count" ;;
esac

# ---------------------------------------------------------------------------
# Empty plan edge: no runs at all → no delete file rows, clean exit.
# ---------------------------------------------------------------------------
# The fetch step writes [] for a runless repository.
echo '[]' > "$tmp/empty-runs.json"
out_dir2="$tmp/plan-empty"
mkdir -p "$out_dir2"
if GITHUB_RUN_ID="$CURRENT_RUN" bash "$PLAN_SCRIPT" "$tmp/empty-runs.json" "$tmp/operations.json" "$out_dir2" >/dev/null 2>&1; then
  pass=$((pass + 1))
else
  fail_case "planner must succeed with zero runs"
fi
expect "empty delete ids" "$(tsv_ids "$out_dir2/delete-runs.tsv")" ""

# ---------------------------------------------------------------------------
# Input-shape tolerance: the raw page array (before the fetch step flattens
# it) must produce the identical plan.
# ---------------------------------------------------------------------------
make_fixture_page_shape "$tmp/paged-runs.json"
out_dir3="$tmp/plan-paged"
mkdir -p "$out_dir3"
if GITHUB_RUN_ID="$CURRENT_RUN" bash "$PLAN_SCRIPT" "$tmp/paged-runs.json" "$tmp/operations.json" "$out_dir3" >/dev/null 2>&1; then
  pass=$((pass + 1))
else
  fail_case "planner must succeed with page-shaped input"
fi
expect "page-shape delete ids" "$(tsv_ids "$out_dir3/delete-runs.tsv")" "$(tsv_ids "$out_dir/delete-runs.tsv")"
expect "page-shape kept ids" "$(tsv_ids "$out_dir3/kept-runs.tsv" 3)" "$(tsv_ids "$out_dir/kept-runs.tsv" 3)"

# ---------------------------------------------------------------------------
# Pure resolver seam: resolve_job_name_from_jobs consumes the exact
# /actions/runs/{id}/jobs API response of a consolidated Maintenance run —
# all three conditional jobs, the two unselected ones in GitHub's real
# skipped shape (status=completed, conclusion=skipped; there is no status
# "skipped") — and prints the selected job's name only when it is
# unambiguous.
# ---------------------------------------------------------------------------
mk_job() { # <name> <status> <conclusion-json>
  jq -cn --arg n "$1" --arg s "$2" --argjson c "$3" \
    '{id: 9000, run_id: 777, name: $n, head_sha: "deadbeef",
      url: "https://github.com/x/jobs/9000", html_url: "https://github.com/x/jobs/9000",
      status: $s, conclusion: $c,
      started_at: "2026-09-15T12:00:00Z", completed_at: "2026-09-15T12:00:01Z"}'
}

jobs_fixture() { # <file> <job-json>...
  local out="$1" first=1 j; shift
  printf '{"total_count": %d, "jobs": [' "$#" > "$out"
  for j in "$@"; do
    [ "$first" -eq 1 ] || printf ', ' >> "$out"
    printf '%s' "$j" >> "$out"
    first=0
  done
  printf ']}\n' >> "$out"
}

SKIP_CLEANUP="$(mk_job "Keep latest run per workflow" completed '"skipped"')"
SKIP_PURGE="$(mk_job "Delete all GitHub Actions caches" completed '"skipped"')"

# A completed selected job (non-skipped conclusion) plus two skipped jobs.
jobs_fixture "$tmp/jobs-completed.json" \
  "$(mk_job "Export Dependabot alerts" completed '"success"')" "$SKIP_CLEANUP" "$SKIP_PURGE"
expect "jobs: completed selected job resolves" \
  "$(resolve_job_name_from_jobs < "$tmp/jobs-completed.json")" "Export Dependabot alerts"

# An in_progress selected job (conclusion null) plus two skipped jobs.
jobs_fixture "$tmp/jobs-inprogress.json" \
  "$SKIP_CLEANUP" "$(mk_job "Delete all GitHub Actions caches" in_progress 'null')" "$SKIP_PURGE"
expect "jobs: in_progress selected job (conclusion null) resolves" \
  "$(resolve_job_name_from_jobs < "$tmp/jobs-inprogress.json")" "Delete all GitHub Actions caches"

# A queued selected job (conclusion null) plus two skipped jobs.
jobs_fixture "$tmp/jobs-queued.json" \
  "$(mk_job "Keep latest run per workflow" queued 'null')" "$SKIP_CLEANUP" "$SKIP_PURGE"
expect "jobs: queued selected job (conclusion null) resolves" \
  "$(resolve_job_name_from_jobs < "$tmp/jobs-queued.json")" "Keep latest run per workflow"

# Ambiguous: two non-skipped jobs → no resolution.
jobs_fixture "$tmp/jobs-ambiguous.json" \
  "$(mk_job "Export Dependabot alerts" completed '"success"')" \
  "$(mk_job "Keep latest run per workflow" completed '"success"')" "$SKIP_PURGE"
expect "jobs: two non-skipped jobs stay unresolved" \
  "$(resolve_job_name_from_jobs < "$tmp/jobs-ambiguous.json")" ""

# All three skipped (run cancelled before any job started).
jobs_fixture "$tmp/jobs-allskipped.json" \
  "$(mk_job "Export Dependabot alerts" completed '"skipped"')" "$SKIP_CLEANUP" "$SKIP_PURGE"
expect "jobs: all skipped stays unresolved" \
  "$(resolve_job_name_from_jobs < "$tmp/jobs-allskipped.json")" ""

# Run still queued: the jobs list is empty.
printf '{"total_count": 0, "jobs": []}\n' > "$tmp/jobs-empty.json"
expect "jobs: empty job list stays unresolved" \
  "$(resolve_job_name_from_jobs < "$tmp/jobs-empty.json")" ""

# Unexpected shape: no jobs key at all.
printf '{}\n' > "$tmp/jobs-missing.json"
expect "jobs: missing .jobs stays unresolved" \
  "$(resolve_job_name_from_jobs < "$tmp/jobs-missing.json")" ""

# ---------------------------------------------------------------------------
# End-to-end: a Maintenance run whose job set could not be resolved (as
# above) is absent from the operations input, and the planner quarantines
# it — preserved and reported, never a deletion candidate — while the
# resolvable run of the same workflow is still preserved per operation.
# ---------------------------------------------------------------------------
cat > "$tmp/quarantine-runs.json" <<'EOF'
[
  {"id": 50, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch", "status": "completed", "conclusion": "success", "created_at": "2026-09-15T00:00:00Z", "head_branch": "main"},
  {"id": 51, "workflow_id": 222, "name": "Maintenance", "event": "workflow_dispatch", "status": "completed", "conclusion": "success", "created_at": "2026-09-16T00:00:00Z", "head_branch": "main"}
]
EOF
echo '[{"id": 51, "operation": "export-dependabot"}]' > "$tmp/quarantine-ops.json"
out_q="$tmp/plan-quarantine"
mkdir -p "$out_q"
if GITHUB_RUN_ID=99 bash "$PLAN_SCRIPT" "$tmp/quarantine-runs.json" "$tmp/quarantine-ops.json" "$out_q" >/dev/null 2>&1; then
  pass=$((pass + 1))
else
  fail_case "planner must succeed with an unresolved Maintenance run"
fi
expect "unresolved run is quarantined" "$(tsv_ids "$out_q/quarantined-runs.tsv")" "50"
expect "unresolved run is never deleted" "$(tsv_ids "$out_q/delete-runs.tsv")" ""
expect "resolved run of the same workflow still preserved" "$(tsv_ids "$out_q/kept-runs.tsv" 3)" "51"

# ---------------------------------------------------------------------------
# Cross-file identity: every operation choice in maintenance.yml has a job
# whose name the operations resolver recognizes, and the set is exactly the
# three operations.
# ---------------------------------------------------------------------------
declare -f job_name_for_operation >/dev/null 2>&1 || fail_case "job_name_for_operation not exposed by the resolver"
declare -f resolve_job_name_from_jobs >/dev/null 2>&1 || fail_case "resolve_job_name_from_jobs not exposed by the resolver"

options="$(grep -E '^\s+- (export-dependabot|cleanup-workflow-runs|purge-actions-caches)\s*$' "$MAINT_WORKFLOW" | sed -E 's/^\s+- //' | sort)"
expect "maintenance.yml options" "$options" "$(printf 'cleanup-workflow-runs\nexport-dependabot\npurge-actions-caches\n')"

job_names="$(grep -E '^    name: ' "$MAINT_WORKFLOW" | sed -E 's/^    name: //' | sort)"
expect "maintenance.yml job names" "$job_names" "$(printf 'Delete all GitHub Actions caches\nExport Dependabot alerts\nKeep latest run per workflow\n')"

for op in export-dependabot cleanup-workflow-runs purge-actions-caches; do
  recognized="$(job_name_for_operation "$op" 2>/dev/null || true)"
  if [ -n "$recognized" ] && printf '%s\n' "$job_names" | grep -qxF "$recognized"; then
    pass=$((pass + 1))
  else
    fail_case "operation '$op' maps to job '$recognized' which maintenance.yml does not define"
  fi
done

echo ""
echo "maintenance-cleanup tests: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
