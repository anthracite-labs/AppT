#!/usr/bin/env bash
#
# AppT Maintenance workflow-run operation resolver (input to
# maintenance-cleanup-plan.sh, cleanup-workflow-runs operation).
#
# The Actions API run object identifies a workflow by name; after the
# consolidation (Issue #52) all three maintenance operations share the one
# "Maintenance" workflow_id, so the explicit operation identity is the job
# name of the run's single non-skipped job: a dispatched run contains all
# three conditional jobs, exactly one of which executes (the other two
# complete with status=completed, conclusion=skipped — GitHub's model for a
# skipped job; there is no status "skipped"). Each job name is defined in
# .github/workflows/maintenance.yml.
#
# A run whose operation cannot be determined (queued before its jobs exist,
# an API failure, or an unexpected job set) is omitted from the output; the
# planner quarantines such runs and never deletes them.
#
# Usage: maintenance-cleanup-operations.sh <all-runs.json>
# Output: JSON array of {id, operation} objects on stdout.
# Env:   GITHUB_REPOSITORY, GH_TOKEN

set -euo pipefail

# Must match the job names in .github/workflows/maintenance.yml exactly
# (checked by tools/ci/test/maintenance-cleanup.test.sh).
job_name_for_operation() {
  case "$1" in
    export-dependabot) echo "Export Dependabot alerts" ;;
    cleanup-workflow-runs) echo "Keep latest run per workflow" ;;
    purge-actions-caches) echo "Delete all GitHub Actions caches" ;;
    *) return 1 ;;
  esac
}

# Pure resolver seam (the testable core of the operation identity): reads a
# /actions/runs/{id}/jobs API response on stdin and prints the selected
# operation's job name if — and only if — exactly one job is not skipped.
# GitHub models a skipped job as status=completed, conclusion=skipped (there
# is no status "skipped"), so the filter is on conclusion, never on status:
#   * selected job still running → status queued/in_progress, conclusion null
#   * selected job finished      → status completed, conclusion success/…
#   * unselected conditional job → status completed, conclusion skipped
# Any ambiguous or unexpected shape (zero or two+ non-skipped jobs, a
# missing job list, …) prints nothing, so the caller leaves the operation
# unresolved and the planner quarantines the run instead of deleting it.
resolve_job_name_from_jobs() {
  jq -r '[(.jobs // [])[] | select(.conclusion != "skipped") | .name]
         | if length == 1 then .[0] else "" end'
}

resolve_all() {
  local all_runs="${1:?usage: maintenance-cleanup-operations.sh <all-runs.json>}"
  local ids id job_name op cand results='[]'

  [ -f "$all_runs" ] || { echo "::error::all-runs.json not found: $all_runs" >&2; exit 1; }
  [ -n "${GITHUB_REPOSITORY:-}" ] || { echo "::error::GITHUB_REPOSITORY is not set" >&2; exit 1; }

  # all-runs.json is the flat array of run objects the fetch step writes
  # (the PR #50 contract); the page array and single-object shapes are
  # tolerated too.
  ids="$(jq -r '
    def runs:
      (if type == "array" then . else [.] end) as $maybe
      | if ($maybe | length) == 0 then []
        elif ($maybe[0] | type) == "object" and ($maybe[0] | has("workflow_runs")) then [ $maybe[] | .workflow_runs[]? ]
        else $maybe
        end;
    runs | .[] | select(.name == "Maintenance") | .id | tostring
  ' "$all_runs" | sort -u)"

  for id in $ids; do
    [ -n "$id" ] || continue

    # A dispatched Maintenance run contains all three conditional jobs; the
    # selected operation is the one job whose conclusion is not "skipped".
    # Anything else (queued before its jobs exist, failed startup, ambiguous
    # or unexpected job set, API failure) leaves the operation unresolved.
    job_name="$(gh api \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "/repos/${GITHUB_REPOSITORY}/actions/runs/${id}/jobs" \
      2>/dev/null | resolve_job_name_from_jobs || true)"

    op=""
    for cand in export-dependabot cleanup-workflow-runs purge-actions-caches; do
      if [ "$job_name" = "$(job_name_for_operation "$cand")" ]; then
        op="$cand"
      fi
    done

    if [ -n "$op" ]; then
      results="$(jq --argjson id "$id" --arg op "$op" '. + [{id: $id, operation: $op}]' <<<"$results")"
    else
      echo "maintenance-cleanup-operations: run $id has an unresolved operation (job='${job_name:-<none>}'); it will be quarantined and never deleted." >&2
    fi
  done

  printf '%s\n' "$results"
}

# Direct execution resolves from the API; sourcing (tests) only exposes the
# functions.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  resolve_all "$@"
fi
