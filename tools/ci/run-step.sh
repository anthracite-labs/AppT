#!/usr/bin/env bash
# Run a Gradle step, tee its output, and mirror the failure reason into
# GitHub Actions annotations.
#
# Run-log and artifact download are both unavailable to the agent that authors
# this branch; the checks API exposes annotations, so that is the only usable
# diagnostic channel. Each step continues on failure so that one CI cycle can
# report every remaining problem instead of one per five-minute run. The final
# `Assert steps` gate re-reads the recorded statuses and fails the job, so
# nothing here converts a hard failure into a warning.
#
# Usage: tools/ci/run-step.sh <label> <command...>
set -uo pipefail

label="$1"
shift

log="step-${label}.log"
"$@" > "$log" 2>&1
status=$?

echo "=== ${label} exited ${status} ==="
tail -n 120 "$log"

mkdir -p .ci-status
echo "$status" > ".ci-status/${label}"

if [ "$status" -ne 0 ]; then
  # The 'What went wrong' block carries the actual reason; stack frames do not.
  awk '/\* What went wrong:/{f=1} f{print} /\* Try:/{f=0}' "$log" \
    | grep -v "^[[:space:]]*at " | head -n 16 \
    | while IFS= read -r line; do echo "::error::${label}: ${line}"; done

  # Dependency-verification and locking failures print their detail outside the
  # 'What went wrong' block, so surface those distinctive lines too.
  grep -E "^(Dependency verification failed|.*is not in its lock state|.*Lock file|Resolved '.*' which is not part of the lock state)" "$log" \
    | sort -u | head -n 8 \
    | while IFS= read -r line; do echo "::error::${label}-detail: ${line}"; done
fi

exit 0
