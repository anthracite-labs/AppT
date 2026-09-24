#!/usr/bin/env bash
#
# AppT Maintenance workflow-run cleanup planner (cleanup-workflow-runs
# operation).
#
# Pure decision function: reads the fetched Actions run pages plus the
# operation map resolved for the Maintenance workflow (see
# maintenance-cleanup-operations.sh) and writes the keep/delete plans. No
# network, no deletion — the workflow job performs the deletions from
# delete-runs.tsv.
#
# Grouping (Issue #52 review fix): one group per (workflow_id, operation).
# Every non-Maintenance workflow groups by workflow_id alone, as before.
# Maintenance runs group by their operation, so the cleanup run itself can
# never become the "latest run" that preserves the export or cache-purge
# evidence: the newest run of every operation is kept, older completed runs
# are deletion candidates, active/queued runs are never deleted, and the
# current run is never a deletion candidate. A Maintenance run whose
# operation could not be resolved is quarantined: preserved, reported in
# quarantined-runs.tsv, and never deleted.
#
# Plan files (tab-separated, in <out-dir>):
#   kept-runs.tsv           workflow_id name id status conclusion created_at head_branch event operation
#   delete-runs.tsv         id workflow_id name conclusion created_at head_branch event operation
#   active-older-runs.tsv   id workflow_id name status created_at head_branch event operation
#   quarantined-runs.tsv    id name created_at event
#
# Usage: maintenance-cleanup-plan.sh <all-runs.json> <operations.json> <out-dir>
# Env:   GITHUB_RUN_ID (the current run; required, never a deletion candidate)
#
# stdout: a Markdown plan summary (the workflow appends it to the run
# summary). Exit status: 0 on a consistent plan, non-zero otherwise.

set -euo pipefail

all_runs="${1:?usage: maintenance-cleanup-plan.sh <all-runs.json> <operations.json> <out-dir>}"
operations="${2:?usage: maintenance-cleanup-plan.sh <all-runs.json> <operations.json> <out-dir>}"
out_dir="${3:?usage: maintenance-cleanup-plan.sh <all-runs.json> <operations.json> <out-dir>}"

current_run="${GITHUB_RUN_ID:-}"
[ -n "$current_run" ] || {
  echo "::error::GITHUB_RUN_ID is not set; refusing to plan deletions." >&2
  exit 1
}
[ -f "$all_runs" ] || {
  echo "::error::all-runs.json not found: $all_runs" >&2
  exit 1
}
[ -f "$operations" ] || {
  echo "::error::operations.json not found: $operations" >&2
  exit 1
}

mkdir -p "$out_dir"

# The plan, as one JSON object, computed entirely in jq:
#
#   kept        newest run of every (workflow_id, operation) group
#   delete      older runs in a group that are completed and not the current run
#   active      older runs in a group that are not completed (never deleted)
#   quarantined Maintenance runs with no resolved operation (never deleted)
plan="$(jq -n \
  --arg current "$current_run" \
  --slurpfile pages "$all_runs" \
  --slurpfile ops "$operations" '
  # all-runs.json is the flat array of run objects the fetch step writes
  # (the PR #50 contract); the page array and single-object shapes are
  # tolerated too.
  def runs_from($in):
    (if ($in | type) == "array" then $in else [$in] end) as $maybe
    | if ($maybe | length) == 0 then []
      elif ($maybe[0] | type) == "object" and ($maybe[0] | has("workflow_runs")) then [ $maybe[] | .workflow_runs[]? ]
      else $maybe
      end;
  runs_from(if ($pages[0] | type) == "array" then $pages[0] else [$pages[0]] end) as $runs
  | ($ops[0] // [] | map({key: (.id | tostring), value: .operation}) | from_entries) as $opmap
  | [ $runs[] | . + {operation: ($opmap[(.id | tostring)] // "")} ] as $anno
  | [ $anno[] | select(.name != "Maintenance" or .operation != "") ] as $grouped
  | [ $grouped[] | . + {group_key: ((.workflow_id | tostring) + "\u0001" + .operation)} ] as $keyed
  | [ $keyed | group_by(.group_key)[] | sort_by(.created_at, .id) | reverse ] as $groups
  | {
      total: ($runs | length),
      kept: [ $groups[] | .[0] ],
      delete: [ $groups[] | .[1:][] | select(.status == "completed") | select((.id | tostring) != $current) ],
      active: [ $groups[] | .[1:][] | select(.status != "completed") | select((.id | tostring) != $current) ],
      quarantined: [ $anno[] | select(.name == "Maintenance" and .operation == "") ]
    }
')"

# Defense in depth: the current run must never be a deletion candidate.
if [ "$(jq --arg c "$current_run" '[.delete[] | select((.id | tostring) == $c)] | length' <<<"$plan")" != "0" ]; then
  echo "::error::the current run ($current_run) appears in the delete plan; aborting." >&2
  exit 1
fi

jq -r '.kept[] | [(.workflow_id | tostring), .name, (.id | tostring), .status, (.conclusion // ""), .created_at, (.head_branch // ""), .event, .operation] | @tsv' \
  <<<"$plan" >"$out_dir/kept-runs.tsv"
jq -r '.delete[] | [(.id | tostring), (.workflow_id | tostring), .name, (.conclusion // ""), .created_at, (.head_branch // ""), .event, .operation] | @tsv' \
  <<<"$plan" >"$out_dir/delete-runs.tsv"
jq -r '.active[] | [(.id | tostring), (.workflow_id | tostring), .name, .status, .created_at, (.head_branch // ""), .event, .operation] | @tsv' \
  <<<"$plan" >"$out_dir/active-older-runs.tsv"
jq -r '.quarantined[] | [(.id | tostring), .name, .created_at, .event] | @tsv' \
  <<<"$plan" >"$out_dir/quarantined-runs.tsv"

total="$(jq '.total' <<<"$plan")"
kept="$(jq '.kept | length' <<<"$plan")"
deleted="$(jq '.delete | length' <<<"$plan")"
active="$(jq '.active | length' <<<"$plan")"
quarantined="$(jq '.quarantined | length' <<<"$plan")"

{
  echo "### Workflow run cleanup plan"
  echo
  echo "- Total runs found: **${total}**"
  echo "- Newest runs preserved: **${kept}** (one per workflow, and one per Maintenance operation)"
  echo "- Older completed runs to delete: **${deleted}**"
  echo "- Older active runs preserved: **${active}**"
  echo "- Quarantined (operation unresolved, never deleted): **${quarantined}**"
  echo
  echo "The newest run for every workflow — and for every Maintenance operation — is always preserved. Active and queued runs are never deleted."
}
