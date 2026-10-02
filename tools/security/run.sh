#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
SELF="${SCRIPT_DIR}/$(basename -- "${BASH_SOURCE[0]}")"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd -P)"
cd "$REPO_ROOT"

MODE="${1:-all}"

GRADLE_STRICT_FLAGS=(
  --no-daemon
  --no-build-cache
  -Dorg.gradle.parallel=false
  -Pkotlin.compiler.execution.strategy=in-process
  --dependency-verification=strict
)
GRADLE_BUILD_TASKS=(assembleDebug)

log() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }

die() {
  printf '\n\033[31mFAILED:\033[0m %s\n' "$*" >&2
  exit 1
}

require() {
  command -v "$1" >/dev/null 2>&1 ||
    die "required tool '$1' is not on PATH. Install it, or run a narrower mode (see: $SELF help)."
}

usage() {
  cat <<'USAGE'
AppT security entrypoint — deterministic, repository-local checks.

Usage: tools/security/run.sh [mode]

Modes:
  all        (default) Every check this script owns: secrets, deps, detekt, build.
  secrets    Secret-scanner self-tests, then a full tree scan. Pass a git ref as
             the second argument to also scan the diff against it:
                 tools/security/run.sh secrets origin/main
  deps       Gradle build-time tooling constraint floor (Issue #68): self-tests,
             then the repository-state check that every known tooling advisory
             is constrained at a Dependabot-mutable seam and resolved at a
             patched version in gradle/verification-metadata.xml.
  detekt     The repository-local Kotlin static/security subset: detekt over
             production Kotlin in :app and :samsung against
             config/detekt/detekt.yml. This is narrower than the full
             `android-static` domain and `ciCheck`.
  build      The exact strict Gradle build used for CodeQL Kotlin extraction.
             The command is printed below rather than documented by hand, so
             this help text cannot drift from what the script actually runs.
  help       This message.

Notes:
  * Runs from the repository root; it normalizes there itself.
  * Requires no secrets and makes no network calls of its own.
  * Fails closed: a missing required tool is an error, not a skipped check.
  * Exits non-zero if any constituent check fails.
  * CodeQL is intentionally not driven from here; AppT's current CodeQL
    Advanced Setup is `.github/workflows/codeql.yml`.
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
    node tools/secret-scan/secret-scan.mjs --base "$base"
  else
    node tools/secret-scan/secret-scan.mjs
  fi
}

check_deps() {
  log "Gradle build-time tooling constraint floor (Issue #68)"
  require node
  node --test "tools/security/test/enforce-gradle-tooling-constraints.test.mjs"
  info "node tools/security/enforce-gradle-tooling-constraints.mjs"
  node tools/security/enforce-gradle-tooling-constraints.mjs
}

check_detekt() {
  log "detekt — Kotlin static analysis (production Kotlin in :app and :samsung)"
  require java
  [[ -x ./gradlew ]] || chmod +x ./gradlew
  info "./gradlew ${GRADLE_STRICT_FLAGS[*]} detekt"
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
help | -h | --help)
  usage
  ;;
secrets)
  check_secrets "${2:-}"
  ;;
deps)
  check_deps
  ;;
detekt)
  check_detekt
  ;;
build)
  check_build
  ;;
all)
  check_secrets ""
  check_deps
  check_detekt
  check_build
  log "All repository-local security checks passed."
  ;;
*)
  usage >&2
  die "unknown mode: $MODE"
  ;;
esac
