#!/usr/bin/env node
// Enforces AppT dependency review security policy on detected vulnerable packages.
//
// Policy:
// - All production and runtime dependencies must have ZERO vulnerabilities (low, moderate, high, critical).
// - GHSA-vrpq-qp53-qv56 (Eclipse JGit XXE in AmazonS3/repo) is permitted ONLY as a build-time tooling
//   transitive introduced by Spotless Gradle plugin under manifest settings.gradle.kts.
// - Any appearance of GHSA-vrpq-qp53-qv56 or any other advisory in app/, samsung/, backend/, or any
//   other manifest is strictly forbidden and fails the gate.

import { fileURLToPath } from 'node:url';

export function evaluateVulnerabilities(vulnerableChangesJson) {
  if (!vulnerableChangesJson || vulnerableChangesJson.trim() === '') {
    return { passed: true, errors: [], permitted: [] };
  }

  let changes;
  try {
    changes = JSON.parse(vulnerableChangesJson);
  } catch (err) {
    return {
      passed: false,
      errors: [
        {
          advisoryId: 'PARSE_ERROR',
          manifest: 'dependency-review',
          name: 'invalid-json',
          version: 'unknown',
          severity: 'critical',
          summary: `Failed to parse vulnerable changes JSON: ${err.message}`,
        },
      ],
      permitted: [],
    };
  }

  const errors = [];
  const permitted = [];

  for (const change of changes) {
    for (const vuln of change.vulnerabilities || []) {
      const advisoryId = vuln.advisory_ghsa_id;
      const manifest = change.manifest;
      const name = change.name;
      const version = change.version;
      const severity = vuln.severity;
      const summary = vuln.advisory_summary;

      // Specifically allow GHSA-vrpq-qp53-qv56 ONLY if it is scoped to the build-tooling manifest (settings.gradle.kts)
      if (advisoryId === 'GHSA-vrpq-qp53-qv56' && manifest === 'settings.gradle.kts') {
        permitted.push({ advisoryId, manifest, name, version, summary });
        continue;
      }

      errors.push({ advisoryId, manifest, name, version, severity, summary });
    }
  }

  return {
    passed: errors.length === 0,
    errors,
    permitted,
  };
}

export function formatErrorDiagnostic(e) {
  return `::error title=Vulnerable Dependency Detected::${e.name}@${e.version} (${e.advisoryId} - ${e.severity}) in manifest ${e.manifest}: ${e.summary}`;
}

const isMain = process.argv[1] === fileURLToPath(import.meta.url);
if (isMain) {
  const result = evaluateVulnerabilities(process.env.VULNERABLE_CHANGES);

  for (const p of result.permitted) {
    console.log(`Permitted build-time tooling advisory: ${p.advisoryId} (${p.summary}) in ${p.manifest} (${p.name}@${p.version})`);
  }

  for (const e of result.errors) {
    console.error(formatErrorDiagnostic(e));
  }

  if (!result.passed) {
    console.error(`Dependency review failed: ${result.errors.length} unpermitted vulnerable dependenc(ies) detected.`);
    process.exit(1);
  }

  console.log('Strict dependency security policy passed.');
}
