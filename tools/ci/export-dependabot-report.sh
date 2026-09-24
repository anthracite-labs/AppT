#!/usr/bin/env bash
#
# AppT Dependabot alert report generator (Maintenance workflow,
# export-dependabot operation).
#
# Pure function: reads the fetched alerts JSON array and writes the Markdown
# export. No network, no gh calls, no deletion.
#
# Severity contract (GitHub Dependabot alerts API): advisory severities are
# `low`, `medium`, `high`, and `critical`. The old exporter counted `moderate`
# instead of `medium`; run 35914176376 proved that silently zeroed 27 real
# medium alerts (Issue #52 review fix). Every alert must therefore land in
# exactly one visible bucket: the four known severities plus an explicit
# `unknown` bucket for any other value (e.g. `negligible` or a missing field).
# The buckets must reconcile exactly to TOTAL or the script fails closed — a
# severity class can never silently vanish from the report again.
#
# Sort order: critical, high, medium, low, then the unknown class, each
# followed by package name and alert number.
#
# Usage: export-dependabot-report.sh <alerts.json> <report.md>
# Env:   GITHUB_REPOSITORY, GITHUB_SHA (report header; defaults in tests)
#
# Exit status: 0 when the report is written and reconciles, non-zero
# otherwise.

set -euo pipefail

# reconcile_buckets <total> <critical> <high> <medium> <low> <unknown>
#
# Fail-closed consistency check: the buckets must add up to the total alert
# count exactly. Exposed at top level for
# tools/ci/test/export-dependabot-report.test.sh.
reconcile_buckets() {
  local total="$1" critical="$2" high="$3" medium="$4" low="$5" unknown="$6"
  local sum=$((critical + high + medium + low + unknown))
  if [ "$sum" -ne "$total" ]; then
    echo "::error::Dependabot severity buckets do not reconcile to TOTAL: critical=$critical high=$high medium=$medium low=$low unknown=$unknown (sum $sum) but total=$total." >&2
    return 1
  fi
  return 0
}

main() {
  local alerts_json="${1:?usage: export-dependabot-report.sh <alerts.json> <report.md>}"
  local report_md="${2:?usage: export-dependabot-report.sh <alerts.json> <report.md>}"

  [ -f "$alerts_json" ] || {
    echo "::error::alerts file not found: $alerts_json" >&2
    exit 1
  }
  jq -e 'type == "array"' "$alerts_json" >/dev/null 2>&1 ||
    {
      echo "::error::alerts JSON is not an array: $alerts_json" >&2
      exit 1
    }

  local total critical high medium low unknown
  total="$(jq 'length' "$alerts_json")"
  critical="$(jq '[.[] | select(((.security_advisory.severity // "") | tostring) == "critical")] | length' "$alerts_json")"
  high="$(jq '[.[] | select(((.security_advisory.severity // "") | tostring) == "high")] | length' "$alerts_json")"
  medium="$(jq '[.[] | select(((.security_advisory.severity // "") | tostring) == "medium")] | length' "$alerts_json")"
  low="$(jq '[.[] | select(((.security_advisory.severity // "") | tostring) == "low")] | length' "$alerts_json")"
  unknown="$(jq '[.[] | select((((.security_advisory.severity // "") | tostring) as $s | ($s == "critical" or $s == "high" or $s == "medium" or $s == "low") | not))] | length' "$alerts_json")"

  reconcile_buckets "$total" "$critical" "$high" "$medium" "$low" "$unknown"

  local repository="${GITHUB_REPOSITORY:-appT (local)}"
  local source_commit="${GITHUB_SHA:-unknown}"

  {
    echo "# AppT Dependabot Alert Export"
    echo
    echo "Generated: $(date -u +"%Y-%m-%d %H:%M:%S UTC")"
    echo
    echo "Repository: \`${repository}\`"
    echo
    echo "Source commit: \`${source_commit}\`"
    echo
    echo "## Summary"
    echo
    echo "| Severity | Open alerts |"
    echo "| --- | ---: |"
    echo "| Critical | ${critical} |"
    echo "| High | ${high} |"
    echo "| Medium | ${medium} |"
    echo "| Low | ${low} |"
    echo "| Unknown | ${unknown} |"
    echo "| **Total** | **${total}** |"
    echo
    echo "## Alerts"
    echo
  } >"$report_md"

  jq -r '
    def severity_rank:
      if . == "critical" then 0
      elif . == "high" then 1
      elif . == "medium" then 2
      elif . == "low" then 3
      else 4
      end;

    sort_by(
      ((.security_advisory.severity // "") | severity_rank),
      .dependency.package.name,
      .number
    )
    | .[]
    |
    "### Alert #\(.number) — \(.dependency.package.name)\n\n" +
    "- **Severity:** \(.security_advisory.severity // "unknown")\n" +
    "- **Package:** `\(.dependency.package.name)`\n" +
    "- **Ecosystem:** \(.dependency.package.ecosystem)\n" +
    "- **Manifest:** `\(.dependency.manifest_path)`\n" +
    "- **Scope:** \(.dependency.scope // "unknown")\n" +
    "- **Relationship:** \(.dependency.relationship // "unknown")\n" +
    "- **GHSA:** \(.security_advisory.ghsa_id)\n" +
    "- **CVE:** \(.security_advisory.cve_id // "None")\n" +
    "- **CVSS:** \(.security_advisory.cvss.score // "Not listed")\n" +
    "- **Vulnerable versions:** `\(.security_vulnerability.vulnerable_version_range)`\n" +
    "- **First patched version:** `" +
      (.security_vulnerability.first_patched_version.identifier // "No patched version listed") +
    "`\n" +
    "- **Summary:** " +
      ((.security_advisory.summary // "No summary") | gsub("\n"; " ")) +
    "\n" +
    "- **GitHub:** \(.html_url)\n"
  ' "$alerts_json" >>"$report_md"

  if [ "$unknown" -gt 0 ]; then
    echo "export-dependabot-report: ${unknown} alert(s) carry an unrecognized severity value; they are listed under the Unknown bucket." >&2
  fi
}

# Direct execution runs main; sourcing (tests) only exposes the functions.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
