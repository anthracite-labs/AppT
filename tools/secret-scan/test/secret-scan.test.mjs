import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  CREDENTIAL_PATTERNS,
  PROVIDER_OWNED_SAMPLES,
  findCredentialsInText,
  isForbiddenFile,
} from '../secret-scan.mjs';

test('flags a PEM private key block', () => {
  const findings = findCredentialsInText('-----BEGIN RSA PRIVATE KEY-----');
  assert.equal(findings.length, 1);
  assert.equal(findings[0].name, 'PEM private key block');
});

test('flags a service-account key document', () => {
  assert.ok(findCredentialsInText('{ "type": "service_account" }').length > 0);
});

test('flags a hardcoded credential assignment', () => {
  assert.ok(findCredentialsInText('password = "hunter2hunter2hunter2"').length > 0);
});

test('does not flag ordinary source', () => {
  const source = [
    'val surface: Color = Color(0xFF101418)',
    'const val STATE_CHANGE_MILLIS: Int = 120',
    'password = System.getenv("APPT_PASSWORD")',
  ].join('\n');
  assert.deepEqual(findCredentialsInText(source), []);
});

test('leaves provider token formats to GitHub Secret Scanning and Push Protection', () => {
  for (const { name, sample } of PROVIDER_OWNED_SAMPLES) {
    assert.deepEqual(findCredentialsInText(sample), [], `${name} must not be detected here`);
  }
});

test('still flags a provider key used as a hardcoded assignment', () => {
  const key = ['AIza', 'B'.repeat(35)].join('');
  const findings = findCredentialsInText(`api_key = "${key}"`);
  assert.ok(findings.length > 0);
  assert.equal(findings[0].name, 'hardcoded credential assignment');
});

test('keeps every documented credential-material pattern', () => {
  const names = CREDENTIAL_PATTERNS.map((entry) => entry.name);
  for (const required of [
    'PEM private key block',
    'Google Cloud service-account key',
    'JSON Web Token',
    'Firebase Cloud Messaging server key',
    'private key assignment',
    'hardcoded credential assignment',
  ]) {
    assert.ok(names.includes(required), `${required} must remain a repository guard`);
  }
});

test('honours the allow marker so this test file can exist', () => {
  assert.deepEqual(
    findCredentialsInText('-----BEGIN PRIVATE KEY----- // secret-scan:allow'),
    [],
  );
});

test('rejects key, certificate and keystore files by path', () => {
  for (const path of [
    'release.jks',
    'upload.keystore',
    'cert.p12',
    'app/google-services.json',
    'ci/service.pem',
  ]) {
    assert.ok(isForbiddenFile(path), `${path} should be forbidden`);
  }
});

test('allows ordinary repository files by path', () => {
  for (const path of ['app/build.gradle.kts', 'docs/BUILD.md', 'backend/package.json']) {
    assert.ok(!isForbiddenFile(path), `${path} should be allowed`);
  }
});
