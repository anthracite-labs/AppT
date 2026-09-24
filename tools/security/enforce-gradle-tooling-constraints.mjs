#!/usr/bin/env node
/**
 * AppT Gradle build-time tooling constraint floor (Issue #68).
 *
 * The bug this guard locks down
 * -----------------------------
 * AppT's `Automatic Dependency Submission (Gradle)` workflow runs the Gradle
 * build, resolves the relevant Gradle graph including the *plugin/buildscript*
 * classpath, and submits that resolved snapshot to GitHub's dependency graph.
 * That snapshot therefore contains vulnerable transitive libraries that no
 * Gradle module declares, and the alerts raised from it are real.
 *
 * Dependabot's Gradle *updater* works from a separate and narrower view: it
 * parses the declared Gradle dependency files (`dependabot/gradle/file_parser.rb`
 * harvests literal `group:name:version` declarations from the build files, the
 * version catalog, the script plugins and the wrapper properties) and cannot
 * mutate an undeclared transitive coordinate merely because it appears in the
 * submitted snapshot. When a security alert targets one of those
 * undeclared-but-submitted coordinates, the updater cannot find it in
 * `dependency_snapshot.all_dependencies` and the security-update job dies with
 *
 *     dependency_not_found
 *     Job dependencies not found in the dependency snapshot: <coordinates>
 *
 * while the alert itself stays open. The alert is real, the red job is real,
 * and neither is fixed by dismissing anything.
 *
 * What the guard asserts
 * ----------------------
 * For every build-time tooling coordinate with a known advisory:
 *
 *  1. MUTABLE — it is declared as a literal `group:name:version` **in code**
 *     somewhere in the Gradle declaration surface Dependabot's Gradle file
 *     parser reads, so the coordinate exists in the updater's dependency
 *     snapshot and a future security update for it can produce a pull request
 *     instead of `dependency_not_found`. Comments are removed first, exactly as
 *     the parser does, so a commented-out declaration never counts.
 *  2. PATCHED — no version of it recorded in the committed supply-chain
 *     metadata (`gradle/verification-metadata.xml`) is below the first patched
 *     version of the advisory that applies to the previously resolved version.
 *
 * Check 1 alone would be satisfiable by a fake direct dependency, and check 2
 * alone would be satisfiable by a version bump nobody can maintain. Both
 * together are the actual invariant: the coordinate is remediated *and* owned
 * at a seam Dependabot can act on.
 *
 * The declaration seam is the root `buildscript { dependencies { constraints {
 * classpath(...) } } }` block in `build.gradle.kts`. Constraints are a floor in
 * Gradle conflict resolution, never a downgrade, so they stay correct when a
 * later plugin bump moves the same transitive further forward.
 *
 * Deliberately zero dependencies: its provenance is the repository itself and
 * its behaviour is reviewable in one file and covered by tests, matching
 * `tools/secret-scan/secret-scan.mjs` and `tools/security/enforce-dependency-policy.mjs`.
 *
 * Usage:
 *   node tools/security/enforce-gradle-tooling-constraints.mjs
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

/**
 * Build-file basenames Dependabot's Gradle file parser accepts
 * (`Dependabot::Gradle::FileParser::SUPPORTED_BUILD_FILE_NAMES`).
 */
const SUPPORTED_BUILD_FILE_NAMES = new Set([
  'build.gradle',
  'build.gradle.kts',
  'settings.gradle',
  'settings.gradle.kts',
]);

/** Directories that never contain a declaration surface. */
const IGNORED_DIRECTORIES = new Set([
  'node_modules',
  'build',
  '.git',
  '.gradle',
  '.arena',
  'coverage',
]);

/**
 * Dependabot's `DEPENDENCY_DECLARATION_REGEX`, transcribed. It matches any
 * quoted `group:name:version` literal in a build file regardless of the
 * enclosing method name, which is precisely why a `classpath("g:a:v")`
 * constraint is visible to the Gradle updater.
 */
const PART = String.raw`[^\s,@'":/\\]+`;
const VERSION_PART = String.raw`[^\s,'":/\\]+`;
const DEPENDENCY_DECLARATION_REGEX = new RegExp(
  String.raw`(?:\(|\s)\s*['"](?<declaration>${PART}:${PART}:${VERSION_PART})['"]`,
  'g',
);

/**
 * Comment stripping, transcribed from `Dependabot::Gradle::FileParser#prepared_content`,
 * which removes both comment forms before the declaration scan runs. (The Ruby
 * block-comment delimiter is escaped below because the literal pair would close
 * this comment.)
 *
 *   prepared_content.gsub(%r{(?<=^|\s)//.*$}, "\n")
 *                   .gsub(%r{(?<=^|\s)/\*.*?\*\/}m, "")
 *
 * This matters for the guard's meaning, not just its tidiness. A commented-out
 * constraint looks like a declaration to a naive scan but is invisible to the
 * updater, so without this a `// classpath("org.jdom:jdom2:2.0.6.1")` would
 * satisfy the mutability check while the coordinate stayed unmutable and the
 * `dependency_not_found` failure stayed live.
 *
 * The `(?<=^|\s)` lookbehind is part of the transcription and is load-bearing:
 * it stops a `//` inside a string, such as the
 * `"https://repo1.maven.org/maven2"` in a repository URL, from being read as a
 * comment. Groovy and Kotlin both honour the same two forms, and the block form
 * closes at the first closing delimiter because the transcription is
 * non-greedy, exactly like Dependabot's.
 */
const LINE_COMMENT_REGEX = /(?<=^|\s)\/\/[^\n]*/gm;
const BLOCK_COMMENT_REGEX = /(?<=^|\s)\/\*[\s\S]*?\*\//g;

/**
 * Removes Gradle comments from a declaration surface, so that only code can
 * satisfy the mutability check.
 */
export function stripComments(surfaceText) {
  return String(surfaceText)
    .replace(LINE_COMMENT_REGEX, '')
    .replace(BLOCK_COMMENT_REGEX, '');
}

const COMPONENT_REGEX = /<component\s+group="(?<group>[^"]+)"\s+name="(?<name>[^"]+)"\s+version="(?<version>[^"]+)"/g;

const APPLIED_SCRIPT_PLUGIN_REGEX = /apply\s*\(\s*from\s*=\s*rootProject\.file\s*\(\s*["'](?<path>[^"']+)["']\s*\)\s*\)/g;

/**
 * Known build-time tooling advisories whose coordinate is not declared by any
 * AppT module and therefore has to be owned by a plugin-classpath constraint.
 *
 * `owner` records the declared Gradle plugin/tooling dependency that pulls the
 * coordinate, so a reviewer can see why the seam is the buildscript classpath
 * rather than `gradle/libs.versions.toml`.
 *
 * Evidence for each chain is the repository dependency graph itself
 * (`GET /repos/:owner/:repo/dependency-graph/sbom`), which records exactly
 * these `DEPENDS_ON` edges.
 */
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

/**
 * Maven/Gradle-ish version ordering, limited to the shapes this guard has to
 * compare: dot-separated numeric parts with an optional trailing qualifier
 * (`0.9.6`, `2.0.6.1`, `6.10.1.202505221210-r`, `1.86`).
 *
 * Returns a negative number when `a < b`, 0 when they order equal, and a
 * positive number when `a > b`.
 */
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

/** Maven ordering ranks for the qualifiers that actually appear in this graph. */
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
  // A missing trailing token reads as `0`, so `2.0.6.1` orders above `2.0.6`.
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

  // Maven orders any release (numeric) token above a qualifier token.
  if (aNumeric !== bNumeric) {
    const numeric = aNumeric ? a : b;
    const sign = aNumeric ? 1 : -1;
    return Number(numeric) > 0 ? sign : -sign;
  }

  const aRank = QUALIFIER_RANKS[a];
  const bRank = QUALIFIER_RANKS[b];
  if (aRank !== undefined || bRank !== undefined) {
    // Unknown qualifiers order below the known release qualifiers.
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

/**
 * Collects the Gradle declaration surface Dependabot's Gradle file parser
 * reads: supported build files, script plugins applied from them, the version
 * catalog and the wrapper properties.
 *
 * Returns `{ files, text }` so diagnostics can name the file a coordinate was
 * (not) found in.
 */
export function readDeclarationSurface(repoRoot = REPO_ROOT) {
  const files = [];

  for (const entry of walk(repoRoot, repoRoot)) {
    if (SUPPORTED_BUILD_FILE_NAMES.has(basename(entry))) {
      files.push(entry);
    }
  }

  // Script plugins (`apply(from = rootProject.file("..."))`) are parsed by
  // Dependabot exactly like build files.
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

/**
 * Mirrors `Dependabot::Gradle::FileParser#shortform_buildfile_dependencies`:
 * after comments are removed, every literal `group:name:version` in the surface
 * becomes an entry of the updater's `dependency_snapshot.all_dependencies`.
 *
 * @returns {Map<string, Set<string>>} coordinate -> declared versions
 */
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

/**
 * Reads the resolved component versions out of the committed Gradle
 * verification metadata, which records every artifact the build resolves.
 *
 * @returns {Map<string, Set<string>>} coordinate -> resolved versions
 */
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

/**
 * @param {object} input
 * @param {string} input.declarationSurface concatenated Gradle declaration surface
 * @param {string} input.verificationMetadataXml committed verification metadata
 * @param {Array} [input.policy] advisory table (defaults to the exported one)
 * @returns {{passed: boolean, errors: Array, remediated: Array}}
 */
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
