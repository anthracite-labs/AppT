#!/usr/bin/env bash
#
# AppT CI path classifier — the single decision point of the verification
# workflow (.github/workflows/ci.yml).
#
# Every job in that workflow consumes this script's key=value outputs instead
# of re-diffing the tree or re-querying GitHub's commit-to-pull-request
# association. The classification rules are pinned by
# tools/ci/test/classify-changes.test.sh, which must stay green before a
# classifier change merges (the test file is itself CI plumbing: changing it
# re-runs every gate, see the ci-config rule below).
#
# Usage:
#   classify-changes.sh <event-name> <base-sha> <head-sha> \
#                       <associated-pr-count> <base-ref>
#
#   <event-name>            pull_request | push | schedule | workflow_dispatch
#   <base-sha>              the pull request's base sha, or the push's
#                           `before` sha ('' or the zero sha falls back to
#                           <head-sha>^)
#   <head-sha>              the commit being verified (GITHUB_SHA)
#   <associated-pr-count>   pull requests associated with <head-sha>, from
#                           GET /repos/{repo}/commits/{sha}/pulls (0 for
#                           events without an association lookup)
#   <base-ref>              the branch the pull request targets ('' outside
#                           pull_request events)
#
# stdout: key=value lines, ready to append to $GITHUB_OUTPUT.
# stderr: human-readable diagnostics (changed-file list and decisions).
#
# Gate model:
#   <flag>         a path set that can affect that job's result changed.
#   ci_config      the workflow file or tools/ci changed: the code that
#                  decides which jobs run changed, so every gate re-runs once.
#   pr_validated   a main push created by merging an already-validated pull
#                  request does not repeat the heavy jobs the pull request
#                  already passed. Schedule/dispatch events set it too,
#                  because no diff exists for them to classify.
#   run_<job>      the job starts only when this is true.

set -euo pipefail

usage() {
  cat >&2 <<'EOF'
usage: classify-changes.sh <event-name> <base-sha> <head-sha> <associated-pr-count> <base-ref>

  <event-name>            pull_request | push | schedule | workflow_dispatch
  <base-sha>              pull request base sha, or the push's `before` sha
  <head-sha>              the commit being verified (GITHUB_SHA)
  <associated-pr-count>   non-negative integer from the commits/{sha}/pulls API
  <base-ref>              branch the pull request targets ('' when n/a)
EOF
  exit 2
}

event="${1:-}"
base_sha="${2:-}"
head_sha="${3:-}"
pr_count="${4:-0}"
base_ref="${5:-}"

[ $# -ge 3 ] || usage
case "$event" in
  pull_request | push | schedule | workflow_dispatch) ;;
  *)
    echo "classify-changes: unknown event name: '$event'" >&2
    usage
    ;;
esac
[ -n "$head_sha" ] || { echo "classify-changes: <head-sha> is required" >&2; usage; }
case "$pr_count" in
  '' | *[!0-9]*)
    echo "classify-changes: <associated-pr-count> must be a non-negative integer, got '$pr_count'" >&2
    usage
    ;;
esac

# ---------------------------------------------------------------------------
# Diff the change (pull_request and push events only).
# ---------------------------------------------------------------------------
changed_files="changed-files.txt"
rm -f "$changed_files"
: > "$changed_files"

if [ "$event" = "pull_request" ] || [ "$event" = "push" ]; then
  zeros="0000000000000000000000000000000000000000"
  if [ -z "$base_sha" ] || [ "$base_sha" = "$zeros" ] || ! git cat-file -e "${base_sha}^{commit}" 2>/dev/null; then
    fallback="$(git rev-parse "${head_sha}^" 2>/dev/null || true)"
    if [ -z "$fallback" ]; then
      echo "classify-changes: cannot resolve a base commit (base='$base_sha', head='$head_sha' has no parent); refusing to classify." >&2
      exit 1
    fi
    echo "classify-changes: base '$base_sha' unavailable; falling back to $fallback (head^)." >&2
    base_sha="$fallback"
  fi
  git diff --name-only "$base_sha" "$head_sha" > "$changed_files"
  echo "classify-changes: diffing $base_sha..$head_sha" >&2
else
  echo "classify-changes: '$event' carries no diff; classifying as a scheduled/manual CodeQL run." >&2
fi

# ---------------------------------------------------------------------------
# Path flags. The pattern lists are the accepted cost-aware rules from PR
# #48 (ci.yml) plus the CodeQL path filter from the former codeql.yml and the
# dependency-input set from the former dependency-review.yml.
#
# Bash `case` globs: `*` also matches `/`, so `app/*` covers every depth and
# `*.gradle.kts` covers build files at any depth.
# ---------------------------------------------------------------------------
android=false
detekt=false
welcome=false
backend=false
codeql=false
dependency=false
ci_config=false

while IFS= read -r path; do
  [ -n "$path" ] || continue

  case "$path" in
    .github/workflows/ci.yml | tools/ci/*) ci_config=true ;;
  esac

  case "$path" in
    app/* | samsung/* | macrobenchmark/* | gradle/* | build.gradle.kts | settings.gradle.kts | gradle.properties | tools/ci/*)
      android=true
      ;;
  esac

  case "$path" in
    app/*.kt | samsung/*.kt | config/detekt/* | build.gradle.kts | settings.gradle.kts | gradle/libs.versions.toml | gradle/verification-metadata.xml)
      detekt=true
      ;;
  esac

  case "$path" in
    app/* | samsung/* | gradle/libs.versions.toml | build.gradle.kts | settings.gradle.kts | gradle.properties | tools/ci/welcome-launch.sh)
      welcome=true
      ;;
  esac

  case "$path" in
    backend/*) backend=true ;;
  esac

  case "$path" in
    app/* | samsung/* | macrobenchmark/* | backend/* | gradle/* | *.gradle.kts | gradle.properties | tools/security/*)
      codeql=true
      ;;
  esac

  case "$path" in
    # `settings.gradle.kts` is intentionally spelled only through the
    # `*.gradle.kts` glob: an explicit duplicate after the glob is dead and
    # ShellCheck rejects the pair (SC2221/SC2222).
    *.gradle.kts | gradle.properties | gradle/libs.versions.toml | *.gradle.lockfile | settings-gradle.lockfile | gradle/verification-metadata.xml | gradle/wrapper/gradle-wrapper.properties | backend/package.json | backend/package-lock.json)
      dependency=true
      ;;
  esac

  echo "classify-changes:   $path" >&2
done < "$changed_files"

# CI-plumbing rule: the workflow file and tools/ci are the code that decides
# whether every job below runs. A change to that code must exercise every
# gate once, fail-closed, rather than trusting the new classifier to excuse
# itself.
if [ "$ci_config" = "true" ]; then
  android=true
  detekt=true
  welcome=true
  backend=true
  codeql=true
  dependency=true
fi

# ---------------------------------------------------------------------------
# Merged-PR reuse (PR #49) and the no-diff events.
# ---------------------------------------------------------------------------
pr_validated=false
case "$event" in
  schedule | workflow_dispatch)
    pr_validated=true
    ;;
  push)
    if [ "$pr_count" -gt 0 ]; then
      pr_validated=true
      echo "classify-changes: $head_sha is associated with $pr_count pull request(s); heavy jobs will not be repeated." >&2
    fi
    ;;
esac

# ---------------------------------------------------------------------------
# Job gates.
# ---------------------------------------------------------------------------
run_secret_scan=false
case "$event" in
  pull_request | push) run_secret_scan=true ;;
esac

run_android=false
run_detekt=false
run_welcome=false
run_backend=false

if [ "$pr_validated" = "false" ]; then
  if [ "$android" = "true" ]; then
    run_android=true
  fi
  if [ "$detekt" = "true" ]; then
    run_detekt=true
  fi
  if [ "$welcome" = "true" ]; then
    run_welcome=true
  fi
  if [ "$backend" = "true" ]; then
    run_backend=true
  fi
fi

# CodeQL analysis is a main-target check: pull requests run it against the
# main target only (as the pre-consolidation codeql.yml trigger required),
# and every push this workflow sees is already main. Schedule and dispatch
# always run it — that is what they exist for.
run_codeql=false
case "$event" in
  schedule | workflow_dispatch)
    run_codeql=true
    ;;
  *)
    if [ "$pr_validated" = "false" ] && [ "$codeql" = "true" ]; then
      if [ "$event" != "pull_request" ] || [ "$base_ref" = "main" ]; then
        run_codeql=true
      fi
    fi
    ;;
esac

# Dependency review stays the main-targeting pull-request check it was before
# consolidation; the graph comparison happens only when dependency inputs
# changed (the `dependency` flag).
run_dependency_review=false
if [ "$event" = "pull_request" ] && [ "$dependency" = "true" ] && [ "$base_ref" = "main" ]; then
  run_dependency_review=true
fi

# ---------------------------------------------------------------------------
# Outputs (stdout, for $GITHUB_OUTPUT).
# ---------------------------------------------------------------------------
echo "android=$android"
echo "detekt=$detekt"
echo "welcome=$welcome"
echo "backend=$backend"
echo "codeql=$codeql"
echo "dependency=$dependency"
echo "ci_config=$ci_config"
echo "pr_validated=$pr_validated"
echo "run_secret_scan=$run_secret_scan"
echo "run_android=$run_android"
echo "run_detekt=$run_detekt"
echo "run_welcome=$run_welcome"
echo "run_backend=$run_backend"
echo "run_codeql=$run_codeql"
echo "run_dependency_review=$run_dependency_review"
