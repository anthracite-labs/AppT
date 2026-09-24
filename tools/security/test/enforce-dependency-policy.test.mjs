// Tests for the dependency security policy evaluator.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { evaluateVulnerabilities, formatErrorDiagnostic } from '../enforce-dependency-policy.mjs';

test('passes when no vulnerabilities are detected', () => {
  const result = evaluateVulnerabilities('[]');
  assert.equal(result.passed, true);
  assert.equal(result.errors.length, 0);
});

test('passes when empty or undefined', () => {
  assert.equal(evaluateVulnerabilities('').passed, true);
  assert.equal(evaluateVulnerabilities(undefined).passed, true);
});

test('permits GHSA-vrpq-qp53-qv56 in settings.gradle.kts', () => {
  const input = JSON.stringify([
    {
      manifest: 'settings.gradle.kts',
      name: 'org.eclipse.jgit:org.eclipse.jgit',
      version: '6.10.0.202406032230-r',
      vulnerabilities: [
        {
          advisory_ghsa_id: 'GHSA-vrpq-qp53-qv56',
          severity: 'moderate',
          advisory_summary: 'Eclipse JGit XML External Entity (XXE) Vulnerability',
        },
      ],
    },
  ]);

  const result = evaluateVulnerabilities(input);
  assert.equal(result.passed, true);
  assert.equal(result.errors.length, 0);
  assert.equal(result.permitted.length, 1);
  assert.equal(result.permitted[0].advisoryId, 'GHSA-vrpq-qp53-qv56');
});

test('rejects GHSA-vrpq-qp53-qv56 if introduced in app module', () => {
  const input = JSON.stringify([
    {
      manifest: 'app/build.gradle.kts',
      name: 'org.eclipse.jgit:org.eclipse.jgit',
      version: '6.10.0.202406032230-r',
      vulnerabilities: [
        {
          advisory_ghsa_id: 'GHSA-vrpq-qp53-qv56',
          severity: 'moderate',
          advisory_summary: 'Eclipse JGit XML External Entity (XXE) Vulnerability',
        },
      ],
    },
  ]);

  const result = evaluateVulnerabilities(input);
  assert.equal(result.passed, false);
  assert.equal(result.errors.length, 1);
});

test('rejects arbitrary vulnerable package in any manifest', () => {
  const input = JSON.stringify([
    {
      manifest: 'backend/package.json',
      name: 'some-bad-package',
      version: '1.0.0',
      vulnerabilities: [
        {
          advisory_ghsa_id: 'GHSA-1234-5678-9012',
          severity: 'low',
          advisory_summary: 'Some vulnerability',
        },
      ],
    },
  ]);

  const result = evaluateVulnerabilities(input);
  assert.equal(result.passed, false);
  assert.equal(result.errors.length, 1);
});

test('handles JSON parse error with structured diagnostic format', () => {
  const result = evaluateVulnerabilities('{ invalid json');
  assert.equal(result.passed, false);
  assert.equal(result.errors.length, 1);
  const err = result.errors[0];
  assert.equal(typeof err, 'object');
  assert.equal(err.advisoryId, 'PARSE_ERROR');
  assert.equal(err.manifest, 'dependency-review');
  assert.equal(err.name, 'invalid-json');
  assert.equal(err.version, 'unknown');
  assert.equal(err.severity, 'critical');
  assert.match(err.summary, /Failed to parse vulnerable changes JSON/);

  const formatted = formatErrorDiagnostic(err);
  assert.match(formatted, /^::error title=Vulnerable Dependency Detected::invalid-json@unknown/);
  assert.doesNotMatch(formatted, /undefined/);
  assert.match(formatted, /Failed to parse vulnerable changes JSON/);
});
