#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

const SUPPORTED_BUILD_FILE_NAMES = new Set([
  'build.gradle',
  'build.gradle.kts',
  'settings.gradle',
  'settings.gradle.kts',
]);

const IGNORED_DIRECTORIES = new Set([
  'node_modules',
  'build',
  '.git',
  '.gradle',
  '.arena',
  'coverage',
]);

const PART = String.raw`[^\s,@'":/\\]+`;
const VERSION_PART = String.raw`[^\s,'":/\\]+`;
const DEPENDENCY_DECLARATION_REGEX = new RegExp(
  String.raw`(?:\(|\s)\s*['"](?<declaration>${PART}:${PART}:${VERSION_PART})['"]`,
  'g',
);

const LINE_COMMENT_REGEX = /(?<=^|\s)\/\/[^\n]*/gm;
const BLOCK_COMMENT_REGEX = /(?<=^|\s)\/\*[\s\S]*?\*\//g;

export function stripComments(surfaceText) {
  return String(surfaceText)
    .replace(LINE_COMMENT_REGEX, '')
    .replace(BLOCK_COMMENT_REGEX, '');
}

const COMPONENT_REGEX = /<component\s+group="(?<group>[^"]+)"\s+name="(?<name>[^"]+)"\s+version="(?<version>[^"]+)"/g;

const APPLIED_SCRIPT_PLUGIN_REGEX = /apply\s*\(\s*from\s*=\s*rootProject\.file\s*\(\s*["'](?<path>[^"']+)["']\s*\)\s*\)/g;

export const TOOLING_ADVISORY_CONSTRAINTS = [
  {
    coordinate: 'org.bitbucket.b_c:jose4j',
    owner: 'com.android.tools.build:bundletool (Android Gradle Plugin 9.4.1)',
    vulnerableVersion: '0.9.5',
    patchedVersion: '0.9.6',
    advisoryId: 'GHSA-3677-xxcr-wjqv',
    cve: 'CVE-2024-29371',
    summary: 'jose4j is vulnerable to DoS via compressed JWE content',
  },
  {
    coordinate: 'org.eclipse.jgit:org.eclipse.jgit',
    owner: 'com.diffplug.spotless:spotless-lib-extra (Spotless Gradle plugin)',
    vulnerableVersion: '6.10.0.202406032230-r',
    patchedVersion: '6.10.1.202505221210-r',
    advisoryId: 'GHSA-vrpq-qp53-qv56',
    cve: 'CVE-2025-4949',
    summary: 'Eclipse JGit XML External Entity (XXE) Vulnerability',
  },
  {
    coordinate: 'org.jdom:jdom2',
    owner:
      'com.android.tools.build.jetifier:jetifier-processor (Android Gradle Plugin 9.4.1)',
    vulnerableVersion: '2.0.6',
    patchedVersion: '2.0.6.1',
    advisoryId: 'GHSA-2363-cqg2-863c',
    cve: 'CVE-2021-33813',
    summary: 'XML External Entity (XXE) Injection in JDOM',
  },
];

export function compareVersions(a, b) {
  const left = tokenize(a);
  const right = tokenize(b);
  const length = Math.max(left.length, right.length);

  for (let index = 0; index < length; index += 1) {
    const comparison = compareTokens(left[index], right[index]);
    if (comparison !== 0) {
      return comparison;
    }
  }

  return 0;
}

const QUALIFIER_RANKS = {
  dev: 0,
  a: 1,
  alpha: 1,
  experimental: 1,
  unstable: 1,
  b: 2,
  beta: 2,
  m: 3,
  milestone: 3,
  rc: 4,
  cr: 4,
  pr: 4,
  pre: 4,
  preview: 4,
  snapshot: 5,
  '': 6,
  ga: 6,
  final: 6,
  release: 6,
  sp: 7,
};

function tokenize(version) {
  return String(version)
    .toLowerCase()
    .split(/[.\-_+]/)
    .filter((token) => token !== '');
}

function isNumeric(token) {
  return /^[0-9]+$/.test(token);
}

function compareTokens(a, b) {
  if (a === undefined && b === undefined) {
    return 0;
  }
  if (a === undefined) {
    return compareTokens('0', b);
  }
  if (b === undefined) {
    return compareTokens(a, '0');
  }

  const aNumeric = isNumeric(a);
  const bNumeric = isNumeric(b);

  if (aNumeric && bNumeric) {
    return Number(a) - Number(b);
  }

  if (aNumeric !== bNumeric) {
    const numeric = aNumeric ? a : b;
    const sign = aNumeric ? 1 : -1;
    return Number(numeric) > 0 ? sign : -sign;
  }

  const aRank = QUALIFIER_RANKS[a];
  const bRank = QUALIFIER_RANKS[b];
  if (aRank !== undefined || bRank !== undefined) {
    const left = aRank ?? -1;
    const right = bRank ?? -1;
    if (left !== right) {
      return left - right;
    }
  }

  if (a === b) {
    return 0;
  }
  return a < b ? -1 : 1;
}

export function readDeclarationSurface(repoRoot = REPO_ROOT) {
  const files = [];

  for (const entry of walk(repoRoot, repoRoot)) {
    if (SUPPORTED_BUILD_FILE_NAMES.has(basename(entry))) {
      files.push(entry);
    }
  }

  for (const file of [...files]) {
    const content = readFileSync(file, 'utf8');
    for (const match of content.matchAll(APPLIED_SCRIPT_PLUGIN_REGEX)) {
      const script = resolve(repoRoot, match.groups.path);
      if (existsSync(script) && !files.includes(script)) {
        files.push(script);
      }
    }
  }

  for (const extra of [
    join('gradle', 'libs.versions.toml'),
    join('gradle', 'wrapper', 'gradle-wrapper.properties'),
  ]) {
    const path = join(repoRoot, extra);
    if (existsSync(path) && !files.includes(path)) {
      files.push(path);
    }
  }

  files.sort();

  return {
    files: files.map((file) => file.slice(repoRoot.length + 1)),
    text: files.map((file) => readFileSync(file, 'utf8')).join('\n'),
  };
}

function* walk(root, directory) {
  for (const entry of readdirSync(directory)) {
    if (IGNORED_DIRECTORIES.has(entry)) {
      continue;
    }
    const path = join(directory, entry);
    if (statSync(path).isDirectory()) {
      yield* walk(root, path);
    } else {
      yield path;
    }
  }
}

export function declaredCoordinates(surfaceText) {
  const declared = new Map();

  for (const match of stripComments(surfaceText).matchAll(DEPENDENCY_DECLARATION_REGEX)) {
    const [group, name, rawVersion] = match.groups.declaration.split(':');
    const version = rawVersion.split('@')[0];
    const coordinate = `${group}:${name}`;

    if (!declared.has(coordinate)) {
      declared.set(coordinate, new Set());
    }
    declared.get(coordinate).add(version);
  }

  return declared;
}

export function resolvedVersions(verificationMetadataXml) {
  const resolved = new Map();

  for (const match of String(verificationMetadataXml).matchAll(COMPONENT_REGEX)) {
    const coordinate = `${match.groups.group}:${match.groups.name}`;
    if (!resolved.has(coordinate)) {
      resolved.set(coordinate, new Set());
    }
    resolved.get(coordinate).add(match.groups.version);
  }

  return resolved;
}

export function evaluate({ declarationSurface, verificationMetadataXml, policy }) {
  const entries = policy ?? TOOLING_ADVISORY_CONSTRAINTS;
  const declared = declaredCoordinates(declarationSurface);
  const resolved = resolvedVersions(verificationMetadataXml);

  const errors = [];
  const remediated = [];

  for (const entry of entries) {
    const declaredVersions = [...(declared.get(entry.coordinate) ?? [])];
    const resolvedForCoordinate = [...(resolved.get(entry.coordinate) ?? [])];

    const declaredPatched = declaredVersions.filter(
      (version) => compareVersions(version, entry.patchedVersion) >= 0,
    );

    if (declaredPatched.length === 0) {
      errors.push({
        code: 'DEPENDENCY_NOT_MUTABLE',
        coordinate: entry.coordinate,
        owner: entry.owner,
        advisoryId: entry.advisoryId,
        cve: entry.cve,
        detail:
          `${entry.coordinate} is not declared at >= ${entry.patchedVersion} anywhere in the ` +
          'Gradle declaration surface Dependabot reads. It reaches the dependency graph only as a ' +
          `transitive of ${entry.owner}, so a Dependabot security update for it fails with ` +
          'dependency_not_found instead of opening a pull request. Declare it as a ' +
          'buildscript classpath constraint in build.gradle.kts.',
      });
    }

    const vulnerableResolved = resolvedForCoordinate.filter(
      (version) => compareVersions(version, entry.patchedVersion) < 0,
    );

    if (vulnerableResolved.length > 0) {
      errors.push({
        code: 'VULNERABLE_RESOLVED_VERSION',
        coordinate: entry.coordinate,
        owner: entry.owner,
        advisoryId: entry.advisoryId,
        cve: entry.cve,
        detail:
          `${entry.coordinate} still resolves to ${vulnerableResolved.join(', ')} in ` +
          `gradle/verification-metadata.xml, which is below ${entry.patchedVersion} ` +
          `(${entry.advisoryId} / ${entry.cve}: ${entry.summary}).`,
      });
    }

    if (declaredPatched.length > 0 && vulnerableResolved.length === 0) {
      remediated.push({
        coordinate: entry.coordinate,
        owner: entry.owner,
        advisoryId: entry.advisoryId,
        declared: declaredPatched.sort(compareVersions),
        resolved: resolvedForCoordinate.sort(compareVersions),
      });
    }
  }

  return { passed: errors.length === 0, errors, remediated };
}

export function formatDiagnostic(error) {
  return (
    `::error title=Gradle Tooling Constraint Failure::${error.coordinate} [${error.code}] ` +
    `${error.advisoryId}${error.cve ? ` / ${error.cve}` : ''} — ${error.detail}`
  );
}

const isMain = process.argv[1] === fileURLToPath(import.meta.url);
if (isMain) {
  const surface = readDeclarationSurface();
  const metadataPath = join(REPO_ROOT, 'gradle', 'verification-metadata.xml');

  if (!existsSync(metadataPath)) {
    console.error(`::error::gradle/verification-metadata.xml is missing at ${metadataPath}`);
    process.exit(1);
  }

  const result = evaluate({
    declarationSurface: surface.text,
    verificationMetadataXml: readFileSync(metadataPath, 'utf8'),
  });

  console.log(`Gradle declaration surface: ${surface.files.join(', ')}`);

  for (const item of result.remediated) {
    console.log(
      `Remediated: ${item.coordinate} declared at ${item.declared.join(', ')} ` +
        `(resolved ${item.resolved.join(', ') || 'nothing'}), owner ${item.owner}, ${item.advisoryId}`,
    );
  }

  for (const error of result.errors) {
    console.error(formatDiagnostic(error));
  }

  if (!result.passed) {
    console.error(
      `Gradle tooling constraint floor failed: ${result.errors.length} finding(s). ` +
        'See Issue #68 and docs/BUILD.md.',
    );
    process.exit(1);
  }

  console.log('Gradle build-time tooling constraint floor passed.');
}
