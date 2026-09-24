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
#   * It does not orchestrate CodeQL. CodeQL SAST lifecycle semantics are
#     owned by GitHub Actions default setup rather than hidden behind a bespoke
#     shell abstraction.
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
# with the "Build Java and Kotlin targets" step of the `analyze` job in
# .github/workflows/codeql.yml.
#
# --no-daemon, --no-build-cache, -Dorg.gradle.parallel=false and
# -Pkotlin.compiler.execution.strategy=in-process are all extraction
# requirements rather than build policy: gradle.properties enables both the
# build cache and parallel execution for CI speed, and the Kotlin daemon is the
# Kotlin plugin's default compile strategy. Any of the three lets compilation
# happen outside the JVM CodeQL traces, and CodeQL then fails with "could not
# process any code written in Java/Kotlin". Running the identical command here
# means a green local `build` is real evidence about the extraction build.
GRADLE_STRICT_FLAGS=(
  --no-daemon
  --no-build-cache
  -Dorg.gradle.parallel=false
  -Pkotlin.compiler.execution.strategy=in-process
  --dependency-verification=strict
)
GRADLE_BUILD_TASKS=(assembleDebug :macrobenchmark:assembleBenchmark)

log() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
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
  detekt     Kotlin static analysis over the production Kotlin in :app and
             :samsung, against config/detekt/detekt.yml, plus
             dependencyLockCheck. Mirrors the `detekt` CI job exactly.
  build      The exact strict Gradle build used for CodeQL Kotlin extraction.
             The command is printed below rather than documented by hand, so
             this help text cannot drift from what the script actually runs.
  help       This message.

Notes:
  * Runs from the repository root; it normalizes there itself.
  * Requires no secrets and makes no network calls of its own.
  * Fails closed: a missing required tool is an error, not a skipped check.
  * Exits non-zero if any constituent check fails.
  * CodeQL is intentionally not driven from here; CodeQL is owned by
    GitHub Actions default setup.
USAGE
  printf '\nThe strict Gradle build command:\n    ./gradlew %s %s\n' \
    "${GRADLE_STRICT_FLAGS[*]}" "${GRADLE_BUILD_TASKS[*]}"
}

check_secrets() {
  local base="${1:-}"
  log "Secret scanner self-tests"
  require node
  node --test "tools/secret-scan/test/secret-scan.test.mjs"

  log "Secret scan of the tracked tree${base:+ and the diff against $base}"
  if [[ -n $base ]]; then
    # A missing base ref must fail loudly rather than silently narrow the scan;
    # the scanner itself exits non-zero in that case.
    node tools/secret-scan/secret-scan.mjs --base "$base"
  else
    node tools/secret-scan/secret-scan.mjs
  fi
}

check_detekt() {
  log "detekt — Kotlin static analysis (production Kotlin in :app and :samsung)"
  require java
  [[ -x ./gradlew ]] || chmod +x ./gradlew
  # Runs against the single repository-owned config/detekt/detekt.yml. There is
  # no baseline file and no --auto-correct, so a finding fails the run.
  #
  # The task list mirrors the detekt checks in ciCheck exactly —
  # detekt plus dependencyLockCheck in one invocation under strict verification —
  # so a green run here is the same evidence CI produces, not a weaker variant.
  info "./gradlew ${GRADLE_STRICT_FLAGS[*]} detekt dependencyLockCheck"
  ./gradlew "${GRADLE_STRICT_FLAGS[@]}" detekt dependencyLockCheck
}

check_build() {
  log "Strict Gradle build (CodeQL Kotlin extraction command)"
  require java
  [[ -x ./gradlew ]] || chmod +x ./gradlew
  info "./gradlew ${GRADLE_STRICT_FLAGS[*]} ${GRADLE_BUILD_TASKS[*]}"
  ./gradlew "${GRADLE_STRICT_FLAGS[@]}" "${GRADLE_BUILD_TASKS[@]}"
}

case "$MODE" in
help | -h | --help)
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
