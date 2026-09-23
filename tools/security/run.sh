#!/usr/bin/env bash
#
# AppT repository-owned security entrypoint.
#
# Purpose (Issue #36): make the deterministic, repository-local security checks
# reproducible from a developer machine with one command, and keep that logic
# from drifting between CI and local runs.
#
# Scope boundary — what this script deliberately does NOT do:
#
#   * It does not orchestrate CodeQL. `init` / `analyze` lifecycle semantics stay
#     visibly owned by .github/workflows/codeql.yml, because hiding them behind a
#     bespoke shell abstraction would make the SAST pipeline unauditable.
#   * It makes no network calls of its own: no curl, no wget, no downloads, no
#     registry access. (Gradle resolves dependencies the same way it does for any
#     other build; that is Gradle's behaviour, not this script's.)
#   * It requires no secrets and reads no credential, token or keystore.
#   * It does not self-heal or regenerate any supply-chain artifact. A missing or
#     drifted lockfile or verification entry fails the run rather than being
#     rewritten, exactly as ci.yml intends.
#
# Exit status: non-zero if any constituent check fails. The script fails closed
# when a required tool is absent rather than silently skipping the check, so a
# green run always means the checks actually executed.
#
# Usage: tools/security/run.sh [mode]     (see `tools/security/run.sh help`)

set -euo pipefail

# ---------------------------------------------------------------------------
# Normalize to the repository root, so the script behaves identically no matter
# where it is invoked from.
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
SELF="${SCRIPT_DIR}/$(basename -- "${BASH_SOURCE[0]}")"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd -P)"
cd "$REPO_ROOT"

MODE="${1:-all}"

# The exact strict build used for CodeQL's manual Kotlin extraction, in one
# place so the workflow and this script cannot drift apart. Keep this in sync
# with .github/workflows/codeql.yml ("Build Kotlin for extraction").
GRADLE_STRICT_FLAGS=(--no-daemon --dependency-verification=strict)
GRADLE_BUILD_TASKS=(assembleDebug)

log()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }

die() {
  printf '\n\033[31mFAILED:\033[0m %s\n' "$*" >&2
  exit 1
}

# Fail closed: a missing required tool is a failure, never a silent skip.
require() {
  command -v "$1" >/dev/null 2>&1 ||
    die "required tool '$1' is not on PATH. Install it, or run a narrower mode (see: $SELF help)."
}

usage() {
  cat <<'USAGE'
AppT security entrypoint — deterministic, repository-local checks.

Usage: tools/security/run.sh [mode]

Modes:
  all        (default) Every check this script owns: secrets, detekt, build.
  secrets    Secret-scanner self-tests, then a full tree scan. Pass a git ref as
             the second argument to also scan the diff against it:
                 tools/security/run.sh secrets origin/main
  detekt     Kotlin static analysis via the Gradle detekt task.
  build      The exact strict Gradle build used for CodeQL Kotlin extraction:
                 ./gradlew --no-daemon --dependency-verification=strict assembleDebug
  help       This message.

Notes:
  * Runs from the repository root; it normalizes there itself.
  * Requires no secrets and makes no network calls of its own.
  * Fails closed: a missing required tool is an error, not a skipped check.
  * Exits non-zero if any constituent check fails.
  * CodeQL is intentionally not driven from here; see
    .github/workflows/codeql.yml.
USAGE
}

check_secrets() {
  local base="${1:-}"
  log "Secret scanner self-tests"
  require node
  node --test "tools/secret-scan/test/secret-scan.test.mjs"

  log "Secret scan of the tracked tree${base:+ and the diff against $base}"
  if [[ -n "$base" ]]; then
    # A missing base ref must fail loudly rather than silently narrow the scan;
    # the scanner itself exits non-zero in that case.
    node tools/secret-scan/secret-scan.mjs --base "$base"
  else
    node tools/secret-scan/secret-scan.mjs
  fi
}

check_detekt() {
  log "detekt — Kotlin static analysis (repository-owned configuration)"
  require java
  [[ -x ./gradlew ]] || chmod +x ./gradlew
  # The task name is the detekt Gradle plugin's own task. It runs against the
  # repository-owned config/detekt/detekt.yml, has no generated baseline, and
  # fails on any configured violation.
  if ! ./gradlew --no-daemon --dry-run detekt >/dev/null 2>&1; then
    die "the 'detekt' Gradle task is not available. detekt is not yet integrated
    into this build; see the Issue #36 follow-up note before relying on this mode."
  fi
  ./gradlew "${GRADLE_STRICT_FLAGS[@]}" detekt
}

check_build() {
  log "Strict Gradle build (CodeQL Kotlin extraction command)"
  require java
  [[ -x ./gradlew ]] || chmod +x ./gradlew
  info "./gradlew ${GRADLE_STRICT_FLAGS[*]} ${GRADLE_BUILD_TASKS[*]}"
  ./gradlew "${GRADLE_STRICT_FLAGS[@]}" "${GRADLE_BUILD_TASKS[@]}"
}

case "$MODE" in
  help|-h|--help)
    usage
    ;;
  secrets)
    check_secrets "${2:-}"
    ;;
  detekt)
    check_detekt
    ;;
  build)
    check_build
    ;;
  all)
    check_secrets ""
    check_detekt
    check_build
    log "All repository-local security checks passed."
    ;;
  *)
    usage >&2
    die "unknown mode: $MODE"
    ;;
esac
