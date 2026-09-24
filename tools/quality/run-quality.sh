#!/usr/bin/env bash
# Narrow quality specialists for the AppT verification `quality` group
# (Issue #56; docs/architecture/release.md#repository-verification-workflow).
#
# Runs, in order:
#
#   1. actionlint  — every workflow in .github/workflows
#   2. ShellCheck  — every tracked *.sh plus the Gradle wrapper script
#   3. jscpd       — the repository's sole duplication detector,
#                    reporting-only (tools/quality/jscpd.json keeps the
#                    threshold at 100 until a reviewed baseline exists)
#
# Supply chain — every tool input is exact, recorded, and verified before a
# single byte executes (Issue #56: no floating versions, no `latest`, no
# container tags, nothing fetched without a recorded digest):
#
#   actionlint v1.7.12 (MIT)
#     Official release asset from github.com/rhysd/actionlint:
#       actionlint_1.7.12_linux_amd64.tar.gz
#       sha256 8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8
#     The digest is upstream's published value in
#     actionlint_1.7.12_checksums.txt, mirrored through the pypi-anchored
#     actionlint-py==1.7.12.25 sdist (_custom_build/checksums.cfg), whose
#     installer verifies this same asset with it.
#
#   ShellCheck v0.11.0 (GPL-3.0-or-later)
#     Official release asset from github.com/koalaman/shellcheck:
#       asset shellcheck-v0.11.0.linux.x86_64.tar.xz
#       sha256 8c3be12b05d5c177a04c29e3c78ce89ac86f1595681cab149b65b97c4e227198
#     The same digest is pinned in the pypi-anchored shellcheck-py==0.11.0.1
#     sdist (setup.cfg), which downloads and verifies that exact tarball.
#
#   jscpd 5.3.2 (MIT)
#     Installed by `npm ci --prefix tools/quality` from the committed
#     tools/quality/package-lock.json (npm registry integrity hashes cover
#     jscpd and its platform binary). Never `npx`, never a floating tag.
#
# A digest mismatch fails the run before extraction (fail closed), and a
# cached binary whose `--version` does not match the pin is rejected too.
#
# Linux x86_64 (GitHub-hosted runners, Linux development hosts). Usage, from
# the repository root:
#
#   bash tools/quality/run-quality.sh

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd -- "$script_dir/../.." && pwd -P)"
cd "$repo_root"

actionlint_version="1.7.12"
actionlint_tarball="actionlint_${actionlint_version}_linux_amd64.tar.gz"
actionlint_sha256="8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8"

shellcheck_version="0.11.0"
shellcheck_tarball="shellcheck-v${shellcheck_version}.linux.x86_64.tar.xz"
shellcheck_sha256="8c3be12b05d5c177a04c29e3c78ce89ac86f1595681cab149b65b97c4e227198"

tool_cache="${TOOLS_QUALITY_CACHE:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}}/appt-quality-tools"
mkdir -p "$tool_cache"

fetch_release_asset() { # <url> <expected sha256> <file name> -> verified path on stdout
  local url="$1" expected="$2" name="$3"
  local dest="$tool_cache/$name"
  local actual

  if [ ! -f "$dest" ]; then
    echo "quality: fetching $url" >&2
    if ! curl -fsSL --retry 3 --retry-delay 2 -o "$dest.tmp" "$url"; then
      echo "quality: download failed for $name" >&2
      rm -f "$dest.tmp"
      return 1
    fi
    mv "$dest.tmp" "$dest"
  fi

  actual="$(sha256sum "$dest" | awk '{ print $1 }')"
  if [ "$actual" != "$expected" ]; then
    echo "quality: supply-chain digest mismatch for $name" >&2
    echo "  expected: $expected" >&2
    echo "  actual:   $actual" >&2
    rm -f "$dest"
    return 1
  fi
  printf '%s\n' "$dest"
}

extract_binary() { # <tarball path> <target file name> <binary name> -> target path on stdout
  local tarball_path="$1" target="$2" binary_name="$3"
  local tmp_extract located

  tmp_extract="$(mktemp -d)"
  case "$tarball_path" in
    *.tar.gz) tar -xzf "$tarball_path" -C "$tmp_extract" ;;
    *.tar.xz) tar -xJf "$tarball_path" -C "$tmp_extract" ;;
    *)
      echo "quality: unsupported archive format: $tarball_path" >&2
      rm -rf "$tmp_extract"
      return 1
      ;;
  esac

  located="$(find "$tmp_extract" -type f -name "$binary_name" -print -quit)"
  if [ -z "$located" ]; then
    echo "quality: $binary_name not found inside $tarball_path" >&2
    rm -rf "$tmp_extract"
    return 1
  fi
  mv "$located" "$target"
  rm -rf "$tmp_extract"
  printf '%s\n' "$target"
}

# --- 1. actionlint ----------------------------------------------------------
actionlint_bin="$tool_cache/actionlint-${actionlint_version}-bin"
if [ ! -x "$actionlint_bin" ]; then
  actionlint_tarball_path="$(fetch_release_asset \
    "https://github.com/rhysd/actionlint/releases/download/v${actionlint_version}/${actionlint_tarball}" \
    "$actionlint_sha256" "$actionlint_tarball")"
  extract_binary "$actionlint_tarball_path" "$actionlint_bin" "actionlint" >/dev/null
fi

# --- 2. ShellCheck ----------------------------------------------------------
shellcheck_bin="$tool_cache/shellcheck-v${shellcheck_version}-bin"
if [ ! -x "$shellcheck_bin" ]; then
  shellcheck_tarball_path="$(fetch_release_asset \
    "https://github.com/koalaman/shellcheck/releases/download/v${shellcheck_version}/${shellcheck_tarball}" \
    "$shellcheck_sha256" "$shellcheck_tarball")"
  extract_binary "$shellcheck_tarball_path" "$shellcheck_bin" "shellcheck" >/dev/null
fi

# Fail closed if a cached binary is not the pinned release (poisoned cache).
if ! "$actionlint_bin" --version 2>&1 | grep -Fq "$actionlint_version"; then
  echo "quality: cached actionlint is not v$actionlint_version" >&2
  exit 1
fi
if ! "$shellcheck_bin" --version | grep -Fq "version: $shellcheck_version"; then
  echo "quality: cached ShellCheck is not v$shellcheck_version" >&2
  exit 1
fi

echo "=== actionlint $actionlint_version — all workflows ==="
# -shellcheck points actionlint at the same pinned ShellCheck binary, so
# embedded run: blocks get one reviewed ShellCheck, not a second copy.
"$actionlint_bin" -shellcheck="$shellcheck_bin"

echo "=== ShellCheck $shellcheck_version — tracked shell scripts + gradlew ==="
mapfile -t shell_files < <(git ls-files '*.sh')
shell_files+=(gradlew)
"$shellcheck_bin" "${shell_files[@]}"

echo "=== jscpd 5.3.2 — reporting-only duplication inventory ==="
# The one duplicate detector for this repository (release.md#repository-
# verification-workflow): `quality` owns duplication, nothing else reports it.
# Install only from the committed lockfile; `--no-audit --no-fund` keeps the
# install deterministic and quiet without weakening verification (npm ci
# enforces the integrity hashes either way).
npm ci --prefix tools/quality --no-audit --no-fund
"tools/quality/node_modules/.bin/jscpd" \
  --config tools/quality/jscpd.json \
  app/src samsung/src macrobenchmark/src backend/src backend/test tools

echo "quality: OK (actionlint $actionlint_version, ShellCheck $shellcheck_version, jscpd 5.3.2)"
