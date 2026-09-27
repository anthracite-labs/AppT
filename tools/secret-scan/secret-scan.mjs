#!/usr/bin/env node
/**
 * AppT secret scanning (docs/architecture/release.md, check 14).
 *
 * Scans the working tree and, when a base ref is supplied, the diff of the
 * change, and fails on anything that looks like a long-lived credential or a
 * key/certificate/keystore file.
 *
 * Why first-party rather than an off-the-shelf action:
 *
 *  * release.md requires every CI action to be pinned to an immutable commit
 *    SHA. The widely used `gitleaks-action` is pinnable, but its v2 terms
 *    require a paid licence key for organization-owned repositories, which
 *    would make the required check unrunnable for `anthracite-labs`.
 *  * Pulling a scanner *binary* at CI time would reintroduce exactly the
 *    supply-chain hole the SHA-pinning rule closes, because the release asset
 *    would be fetched by tag with no verified checksum.
 *  * This scanner has zero dependencies, so its provenance is the repository
 *    itself and its behaviour is reviewable in one file and covered by tests.
 *
 * This is a floor, not a claim of exhaustive detection. Adopting an additional
 * third-party scanner later is a reviewed change that must pin the tool and
 * record its provenance and licence.
 *
 * Usage:
 *   node tools/secret-scan/secret-scan.mjs [--base <ref>]
 */

import { execFileSync } from 'node:child_process';
import { readFileSync, statSync } from 'node:fs';
import { extname, basename } from 'node:path';

/** Files whose mere presence in the tree is a failure. */
const FORBIDDEN_EXTENSIONS = new Set([
  '.jks',
  '.keystore',
  '.p12',
  '.pfx',
  '.pem',
  '.key',
  '.der',
  '.crt',
  '.cer',
  '.ppk',
  '.asc',
]);

const FORBIDDEN_BASENAMES = new Set([
  'google-services.json',
  'GoogleService-Info.plist',
  'id_rsa',
  'id_dsa',
  'id_ecdsa',
  'id_ed25519',
  '.netrc',
  '.npmrc',
  'credentials.json',
]);

/**
 * Content patterns for credential material AppT itself must forbid.
 *
 * Ownership split (Issue #88, docs/architecture/release.md#github-owned-controls):
 * GitHub Secret Scanning and Push Protection own generic *provider* token
 * formats — Google API keys, AWS access key ids, GitHub tokens, Slack tokens,
 * Stripe secret keys. Those patterns used to live here as well, which resolved
 * the same tree twice for a control the platform already owns and made the
 * repository look like the owner of a check it does not run. They are gone; see
 * `PROVIDER_OWNED_SAMPLES` for the boundary the tests hold in place.
 *
 * What remains is the AppT-specific material floor: key, certificate and
 * keystore files (`isForbiddenFile`), private-key material, service-account and
 * Firebase credential documents, and hardcoded credential assignments. Note that
 * a provider key written as an assignment (`api_key = "AIza..."`) is still
 * caught by the assignment pattern below — that is the AppT policy guard, not
 * provider-token detection.
 */
export const CREDENTIAL_PATTERNS = [
  { name: 'PEM private key block', pattern: /-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----/ },
  { name: 'Google Cloud service-account key', pattern: /"type"\s*:\s*"service_account"/ },
  { name: 'JSON Web Token', pattern: /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/ },
  { name: 'Firebase Cloud Messaging server key', pattern: /\bAAAA[A-Za-z0-9_-]{7}:[A-Za-z0-9_-]{140,}\b/ },
  { name: 'private key assignment', pattern: /\b(?:private_key|privateKey)\s*[:=]\s*["'][^"']{32,}["']/ },
  {
    name: 'hardcoded credential assignment',
    pattern:
      /\b(?:api[_-]?key|secret[_-]?key|client[_-]?secret|access[_-]?token|auth[_-]?token|password|passwd)\s*[:=]\s*["'][^"'\s${}<>]{12,}["']/i,
  },
];

/**
 * Assemble a synthetic fixture from fragments.
 *
 * The provider samples below are deliberately fake inputs to a *negative*
 * test — they prove the repository scanner does NOT report a provider token.
 * Written as one contiguous literal, however, each of them is a
 * provider-shaped string, and the push protection that owns those very
 * formats blocks the push. Assembling from fragments keeps the fixture
 * behaviourally identical without ever presenting a provider-shaped literal
 * to a secret scanner. No real credential is involved at any point.
 */
const fixture = (...fragments) => fragments.join('');

/**
 * Provider token formats that GitHub Secret Scanning and Push Protection own.
 * The repository scanner must not detect them: duplicating a platform control
 * is how a repository ends up claiming a guarantee it does not actually
 * enforce. Exported so the tests can keep the boundary from drifting back.
 */
export const PROVIDER_OWNED_SAMPLES = [
  { name: 'Google API key', sample: fixture('AIza', 'B'.repeat(35)) },
  { name: 'AWS access key id', sample: fixture('AKIA', 'A'.repeat(16)) },
  { name: 'GitHub token', sample: fixture('ghp', '_', 'a'.repeat(36)) },
  {
    name: 'Slack token',
    sample: fixture('xoxb', '-', '1'.repeat(10), '-', 'a'.repeat(16)),
  },
  { name: 'Stripe secret key', sample: fixture('sk_', 'live', '_', 'a'.repeat(24)) },
];

/** Paths never scanned for content: generated, vendored, or binary. */
const SKIPPED_PATH_PATTERNS = [
  /(^|\/)node_modules\//,
  /(^|\/)build\//,
  /(^|\/)\.gradle\//,
  /(^|\/)lib\//,
  /(^|\/)coverage\//,
  /(^|\/)package-lock\.json$/,
  /(^|\/)gradle\/verification-metadata\.xml$/,
  /\.lockfile$/,
  /(^|\/)tools\/secret-scan\//,
];

const BINARY_EXTENSIONS = new Set([
  '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.pdf', '.zip', '.gz',
  '.jar', '.aar', '.apk', '.aab', '.so', '.dex', '.class', '.woff', '.woff2', '.ttf',
]);

const MAX_BYTES = 2 * 1024 * 1024;

/** Lines carrying this marker are exempt, so the scanner's own tests can exist. */
const ALLOW_MARKER = 'secret-scan:allow';

function git(args) {
  return execFileSync('git', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
}

export function isForbiddenFile(path) {
  return (
    FORBIDDEN_EXTENSIONS.has(extname(path).toLowerCase()) || FORBIDDEN_BASENAMES.has(basename(path))
  );
}

export function findCredentialsInText(text) {
  const findings = [];
  text.split(/\r?\n/).forEach((line, index) => {
    if (line.includes(ALLOW_MARKER)) return;
    for (const { name, pattern } of CREDENTIAL_PATTERNS) {
      if (pattern.test(line)) {
        findings.push({ line: index + 1, name });
      }
    }
  });
  return findings;
}

function shouldScanContent(path) {
  if (SKIPPED_PATH_PATTERNS.some((p) => p.test(path))) return false;
  if (BINARY_EXTENSIONS.has(extname(path).toLowerCase())) return false;
  try {
    if (statSync(path).size > MAX_BYTES) return false;
  } catch {
    return false;
  }
  return true;
}

function scanPaths(paths, label) {
  const problems = [];
  for (const path of paths) {
    if (!path) continue;

    if (isForbiddenFile(path)) {
      problems.push(`${label}: ${path}: key, certificate or credential file must not enter the tree`);
      continue;
    }

    if (!shouldScanContent(path)) continue;

    let text;
    try {
      text = readFileSync(path, 'utf8');
    } catch {
      continue;
    }
    // Skip files that are actually binary.
    if (text.includes('\u0000')) continue;

    for (const finding of findCredentialsInText(text)) {
      problems.push(`${label}: ${path}:${finding.line}: possible ${finding.name}`);
    }
  }
  return problems;
}

function main() {
  const baseIndex = process.argv.indexOf('--base');
  const base = baseIndex === -1 ? null : process.argv[baseIndex + 1];

  const tracked = git(['ls-files', '-z']).split('\u0000').filter(Boolean);
  const problems = scanPaths(tracked, 'tree');

  if (base) {
    let changed = [];
    try {
      changed = git(['diff', '--name-only', '--diff-filter=ACMR', '-z', `${base}...HEAD`])
        .split('\u0000')
        .filter(Boolean);
    } catch {
      // A missing base ref must not silently disable the diff scan.
      process.stderr.write(`secret-scan: could not diff against '${base}'\n`);
      process.exitCode = 1;
      return;
    }
    problems.push(...scanPaths(changed, 'diff'));
  }

  const unique = [...new Set(problems)].sort();
  if (unique.length > 0) {
    process.stderr.write(
      `secret-scan: FAILED — ${unique.length} finding(s).\n` +
        unique.map((p) => `  ${p}`).join('\n') +
        '\n\nNo long-lived credential, keystore or certificate may enter the tree ' +
        '(docs/architecture/release.md).\n',
    );
    process.exitCode = 1;
    return;
  }

  process.stdout.write(
    `secret-scan: OK — scanned ${tracked.length} tracked file(s)` +
      (base ? ` plus the diff against ${base}` : '') +
      '.\n',
  );
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}
