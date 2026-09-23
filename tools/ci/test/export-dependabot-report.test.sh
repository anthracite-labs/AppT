#!/usr/bin/env bash
#
# Tests for tools/ci/export-dependabot-report.sh (Issue #52 review fix).
#
# GitHub's Dependabot alerts API reports advisory severity as `low`,
# `medium`, `high`, `critical` — run 35914176376 proved the old exporter's
# `moderate` bucket silently zeroed 27 real `medium` alerts. These tests pin
# the fixed contract:
#
#   * `medium` is counted, ranked, and shown (regression for run 35914176376:
#     27 medium alerts must appear as 27 in the Medium bucket);
#   * the four known severities plus an explicit unknown bucket reconcile
#     exactly to TOTAL, and the script fails closed on any mismatch;
#   * unrecognized severity values (e.g. `negligible`, missing) land in the
#     unknown bucket and are ranked last — never silently omitted.
#
# Deterministic and offline.
#
#   bash tools/ci/test/export-dependabot-report.test.sh
#
# Exit status: 0 when every assertion passes, 1 otherwise.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPORT_SCRIPT="$SCRIPT_DIR/../export-dependabot-report.sh"

[ -f "$REPORT_SCRIPT" ] || { echo "FAIL: report script not found at $REPORT_SCRIPT" >&2; exit 1; }

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

# expect_row <case> <report> <row-prefix> <count>
#
# Table rows are `| Label | count |`; with -F'|' the count is field 3.
expect_row() { # <case> <report> <row-prefix> <count>
  local case_name="$1" report="$2" row="$3" count="$4" line
  line="$(grep -F "$row" "$report" | head -1 || true)"
  if [ -z "$line" ]; then
    fail_case "$case_name: row '$row' missing from report"
    return 0
  fi
  local shown
  shown="$(printf '%s' "$line" | awk -F'|' '{ gsub(/[ *]/, "", $3); print $3 }')"
  expect "$case_name row $row" "$shown" "$count"
}

# make_alerts <out.json> <severity>... — one alert per severity argument
# (empty argument stands in for a missing severity field).
make_alerts() {
  local out="$1"; shift
  local i=0
  {
    echo '['
    for sev in "$@"; do
      i=$((i + 1))
      if [ -n "$sev" ]; then
        jq -cn --arg s "$sev" --argjson n "$i" '{
          number: $n,
          state: "open",
          dependency: { package: { ecosystem: "npm", name: ("pkg" + ($n|tostring)) },
                        manifest_path: "backend/package.json", scope: "runtime", relationship: "direct" },
          security_advisory: { ghsa_id: ("GHSA-0000-0000-000" + ($n|tostring)),
                               cve_id: ("CVE-2026-000" + ($n|tostring)),
                               summary: ("summary " + ($n|tostring)), severity: $s },
          security_vulnerability: { vulnerable_version_range: ">=0 <1",
                                    first_patched_version: { identifier: "1.0.0" } },
          html_url: ("https://github.com/advisories/" + ($n|tostring))
        }'
      else
        jq -cn --argjson n "$i" '{
          number: $n,
          state: "open",
          dependency: { package: { ecosystem: "npm", name: ("pkg" + ($n|tostring)) },
                        manifest_path: "backend/package.json", scope: "runtime", relationship: "direct" },
          security_advisory: { ghsa_id: ("GHSA-0000-0000-000" + ($n|tostring)),
                               summary: ("summary " + ($n|tostring)) },
          security_vulnerability: { vulnerable_version_range: ">=0 <1" },
          html_url: ("https://github.com/advisories/" + ($n|tostring))
        }'
      fi
    done | paste -sd, -
    echo ']'
  } | jq '.' > "$out"
}

run_report() { # <case> <alerts.json>
  local case_name="$1" alerts="$2"
  REPORT_OUT="$tmp/$case_name.md"
  if ! LAST_OUTPUT="$(bash "$REPORT_SCRIPT" "$alerts" "$REPORT_OUT" 2>&1)"; then
    LAST_EXIT=$?
  else
    LAST_EXIT=0
  fi
}

# ---------------------------------------------------------------------------
# 1. Regression for run 35914176376: 27 medium alerts + one of each other
#    known severity. The Medium bucket must show 27, and the report must
#    list every alert.
# ---------------------------------------------------------------------------
sevs=()
for i in $(seq 1 27); do sevs+=("medium"); done
make_alerts "$tmp/mixed.json" "critical" "high" "${sevs[@]}" "low"
run_report mixed "$tmp/mixed.json"
expect "mixed exit" "$LAST_EXIT" 0
expect_row mixed "$REPORT_OUT" '| Critical |' 1
expect_row mixed "$REPORT_OUT" '| High |' 1
expect_row mixed "$REPORT_OUT" '| Medium |' 27
expect_row mixed "$REPORT_OUT" '| Low |' 1
expect_row mixed "$REPORT_OUT" '| Unknown |' 0
expect_row mixed "$REPORT_OUT" '| **Total** |' 30
expect "mixed alert entries" "$(grep -c '^### Alert #' "$REPORT_OUT")" 30
# Sort order: critical first, then high, then the medium block, then low.
expect "mixed first alert is critical" \
  "$(grep -m1 '^- \*\*Severity:\*\*' "$REPORT_OUT")" "- **Severity:** critical"
expect "mixed last alert is low" \
  "$(grep '^- \*\*Severity:\*\*' "$REPORT_OUT" | tail -1)" "- **Severity:** low"

# ---------------------------------------------------------------------------
# 2. Unknown severity values: `negligible` and a missing field land in the
#    explicit unknown bucket (ranked last) instead of vanishing.
# ---------------------------------------------------------------------------
make_alerts "$tmp/unknown.json" "negligible" "" "low" "medium"
run_report unknown "$tmp/unknown.json"
expect "unknown exit" "$LAST_EXIT" 0
expect_row unknown "$REPORT_OUT" '| Critical |' 0
expect_row unknown "$REPORT_OUT" '| High |' 0
expect_row unknown "$REPORT_OUT" '| Medium |' 1
expect_row unknown "$REPORT_OUT" '| Low |' 1
expect_row unknown "$REPORT_OUT" '| Unknown |' 2
expect_row unknown "$REPORT_OUT" '| **Total** |' 4
# The missing-severity alert (pkg2) sorts after the `negligible` one (pkg1)
# within the rank-4 unknown class, and prints as `unknown`.
expect "unknown last alert prints as unknown" \
  "$(grep '^- \*\*Severity:\*\*' "$REPORT_OUT" | tail -1)" "- **Severity:** unknown"

# ---------------------------------------------------------------------------
# 3. Empty export: all buckets zero, total zero, clean exit.
# ---------------------------------------------------------------------------
echo '[]' > "$tmp/empty.json"
run_report empty "$tmp/empty.json"
expect "empty exit" "$LAST_EXIT" 0
expect_row empty "$REPORT_OUT" '| Critical |' 0
expect_row empty "$REPORT_OUT" '| **Total** |' 0

# ---------------------------------------------------------------------------
# 4. Fail-closed reconciliation: the bucket check itself must reject
#    inconsistent totals (the mechanism that guarantees a severity class can
#    never silently vanish again).
# ---------------------------------------------------------------------------
# shellcheck disable=SC1091
source "$REPORT_SCRIPT"
if declare -f reconcile_buckets >/dev/null 2>&1; then
  if reconcile_buckets 5 2 1 1 1 1 >/dev/null 2>&1; then
    fail_case "reconcile: buckets 2+1+1+1+1=6 vs total 5 must fail"
  else
    pass=$((pass + 1))
  fi
  if reconcile_buckets 5 2 1 1 1 0 >/dev/null 2>&1; then
    pass=$((pass + 1))
  else
    fail_case "reconcile: buckets 2+1+1+1+0=5 vs total 5 must pass"
  fi
  if reconcile_buckets 0 0 0 0 0 0 >/dev/null 2>&1; then
    pass=$((pass + 1))
  else
    fail_case "reconcile: all-zero must pass"
  fi
else
  fail_case "reconcile_buckets function not exposed for testing"
fi

echo ""
echo "export-dependabot-report tests: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
