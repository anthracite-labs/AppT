// Tests for the Gradle build-time tooling constraint floor (Issue #68).
//
// The two fixture states below are the exact before/after of the Dependabot
// `dependency_not_found` mismatch: the vulnerable tooling transitive is present
// in the resolved graph but undeclared anywhere Dependabot's Gradle file parser
// reads, versus constrained at the buildscript classpath seam.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

import {
  TOOLING_ADVISORY_CONSTRAINTS,
  compareVersions,
  declaredCoordinates,
  evaluate,
  formatDiagnostic,
  readDeclarationSurface,
  resolvedVersions,
  stripComments,
} from '../enforce-gradle-tooling-constraints.mjs';

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

// --- Fixtures -------------------------------------------------------------

// The canonical base state (commit 8aa9db4d): the three coordinates are in the
// resolved graph at vulnerable versions and declared nowhere.
const VULNERABLE_SURFACE = `
buildscript {
    repositories { mavenCentral() }
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
        }
    }
}

plugins {
    alias(libs.plugins.spotless)
}
`;

const VULNERABLE_METADATA = `<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">
   <components>
      <component group="org.bitbucket.b_c" name="jose4j" version="0.9.5">
         <artifact name="jose4j-0.9.5.jar"/>
      </component>
      <component group="org.eclipse.jgit" name="org.eclipse.jgit" version="6.10.0.202406032230-r">
         <artifact name="org.eclipse.jgit-6.10.0.202406032230-r.jar"/>
      </component>
      <component group="org.jdom" name="jdom2" version="2.0.6">
         <artifact name="jdom2-2.0.6.jar"/>
      </component>
   </components>
</verification-metadata>
`;

// The remediated state: patched versions constrained at the buildscript
// classpath and recorded as the resolved versions.
const PATCHED_SURFACE = `
buildscript {
    dependencies {
        constraints {
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            classpath("org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r")
            classpath("org.jdom:jdom2:2.0.6.1")
        }
    }
}
`;

const PATCHED_METADATA = `<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">
   <components>
      <component group="org.bitbucket.b_c" name="jose4j" version="0.9.6">
         <artifact name="jose4j-0.9.6.jar"/>
      </component>
      <component group="org.eclipse.jgit" name="org.eclipse.jgit" version="6.10.1.202505221210-r">
         <artifact name="org.eclipse.jgit-6.10.1.202505221210-r.jar"/>
      </component>
      <component group="org.jdom" name="jdom2" version="2.0.6.1">
         <artifact name="jdom2-2.0.6.1.jar"/>
      </component>
   </components>
</verification-metadata>
`;

// --- Version ordering -----------------------------------------------------

test('orders the three advisory version pairs correctly', () => {
  assert.ok(compareVersions('0.9.5', '0.9.6') < 0);
  assert.ok(compareVersions('2.0.6', '2.0.6.1') < 0);
  assert.ok(compareVersions('6.10.0.202406032230-r', '6.10.1.202505221210-r') < 0);
  assert.equal(compareVersions('0.9.6', '0.9.6'), 0);
  assert.ok(compareVersions('1.80.2', '1.86') < 0);
  assert.ok(compareVersions('3.20.0', '3.16.0') > 0);
});

// --- Red state ------------------------------------------------------------

test('goes red on the exact Dependabot dependency_not_found mismatch', () => {
  const result = evaluate({
    declarationSurface: VULNERABLE_SURFACE,
    verificationMetadataXml: VULNERABLE_METADATA,
  });

  assert.equal(result.passed, false);
  assert.equal(result.remediated.length, 0);

  // One mutability finding and one resolved-version finding per coordinate.
  assert.equal(result.errors.length, 6);

  const codes = result.errors.map((e) => e.code).sort();
  assert.deepEqual(codes, [
    'DEPENDENCY_NOT_MUTABLE',
    'DEPENDENCY_NOT_MUTABLE',
    'DEPENDENCY_NOT_MUTABLE',
    'VULNERABLE_RESOLVED_VERSION',
    'VULNERABLE_RESOLVED_VERSION',
    'VULNERABLE_RESOLVED_VERSION',
  ]);

  const coordinates = [...new Set(result.errors.map((e) => e.coordinate))].sort();
  assert.deepEqual(coordinates, [
    'org.bitbucket.b_c:jose4j',
    'org.eclipse.jgit:org.eclipse.jgit',
    'org.jdom:jdom2',
  ]);

  const notMutable = result.errors.find((e) => e.code === 'DEPENDENCY_NOT_MUTABLE');
  assert.match(notMutable.detail, /dependency_not_found/);
  assert.match(formatDiagnostic(notMutable), /^::error title=Gradle Tooling Constraint Failure::/);
});

// --- Green state ----------------------------------------------------------

test('goes green once the coordinates are constrained and resolved patched', () => {
  const result = evaluate({
    declarationSurface: PATCHED_SURFACE,
    verificationMetadataXml: PATCHED_METADATA,
  });

  assert.deepEqual(result.errors, []);
  assert.equal(result.passed, true);
  assert.equal(result.remediated.length, 3);
});

test('stays red when only the declaration is added but metadata is stale', () => {
  const result = evaluate({
    declarationSurface: PATCHED_SURFACE,
    verificationMetadataXml: VULNERABLE_METADATA,
  });

  assert.equal(result.passed, false);
  assert.deepEqual(
    result.errors.map((e) => e.code),
    [
      'VULNERABLE_RESOLVED_VERSION',
      'VULNERABLE_RESOLVED_VERSION',
      'VULNERABLE_RESOLVED_VERSION',
    ],
  );
});

test('stays red when a resolved vulnerable version survives beside a patched one', () => {
  const mixed = PATCHED_METADATA.replace(
    '<component group="org.jdom" name="jdom2" version="2.0.6.1">',
    '<component group="org.jdom" name="jdom2" version="2.0.6">\n         <artifact name="jdom2-2.0.6.jar"/>\n      </component>\n      <component group="org.jdom" name="jdom2" version="2.0.6.1">',
  );

  const result = evaluate({
    declarationSurface: PATCHED_SURFACE,
    verificationMetadataXml: mixed,
  });

  assert.equal(result.passed, false);
  assert.equal(result.errors.length, 1);
  assert.equal(result.errors[0].coordinate, 'org.jdom:jdom2');
  assert.equal(result.errors[0].code, 'VULNERABLE_RESOLVED_VERSION');
});

test('rejects a constraint declared below the patched version', () => {
  const tooLow = PATCHED_SURFACE.replace('jose4j:0.9.6', 'jose4j:0.9.5');

  const result = evaluate({
    declarationSurface: tooLow,
    verificationMetadataXml: PATCHED_METADATA,
  });

  assert.equal(result.passed, false);
  assert.equal(result.errors.length, 1);
  assert.equal(result.errors[0].code, 'DEPENDENCY_NOT_MUTABLE');
  assert.equal(result.errors[0].coordinate, 'org.bitbucket.b_c:jose4j');
});

// --- Parsers --------------------------------------------------------------

test('harvests literal declarations the way the Gradle updater does', () => {
  const declared = declaredCoordinates(PATCHED_SURFACE);

  assert.deepEqual(declared.get('org.jdom:jdom2'), new Set(['2.0.6.1']));
  assert.deepEqual(declared.get('org.eclipse.jgit:org.eclipse.jgit'), new Set([
    '6.10.1.202505221210-r',
  ]));
  // A packaging suffix must not leak into the version.
  assert.deepEqual(declaredCoordinates('classpath("org.jdom:jdom2:2.0.6.1@jar")').get('org.jdom:jdom2'), new Set(['2.0.6.1']));
});

// --- Comments are not declarations ---------------------------------------
//
// Dependabot strips comments before it scans for declarations, so a
// commented-out constraint is invisible to the updater. The guard must agree,
// or it would report a coordinate as mutable while `dependency_not_found` was
// still live.

test('a line-commented declaration is not a declaration', () => {
  const surface = `
buildscript {
    dependencies {
        constraints {
            // classpath("org.jdom:jdom2:2.0.6.1")
        }
    }
}
`;

  assert.equal(declaredCoordinates(surface).has('org.jdom:jdom2'), false);
});

test('a block-commented declaration is not a declaration', () => {
  const surface = `
buildscript {
    dependencies {
        constraints {
            /* classpath("org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r") */
        }
    }
}
`;

  assert.equal(declaredCoordinates(surface).has('org.eclipse.jgit:org.eclipse.jgit'), false);
});

test('a multi-line block comment containing a declaration is not a declaration', () => {
  const surface = `
buildscript {
    dependencies {
        constraints {
            /*
             * Disabled while AGP catches up:
             * classpath("org.bitbucket.b_c:jose4j:0.9.6")
             */
        }
    }
}
`;

  assert.equal(declaredCoordinates(surface).has('org.bitbucket.b_c:jose4j'), false);
});

test('a comment-only patched declaration does not satisfy the guard', () => {
  const result = evaluate({
    // The exact shape CodeRabbit reported: the patched declaration exists only
    // inside a comment.
    declarationSurface: `
buildscript {
    dependencies {
        constraints {
            // classpath("org.jdom:jdom2:2.0.6.1")
            /* classpath("org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r") */
            // classpath("org.bitbucket.b_c:jose4j:0.9.6")
        }
    }
}
`,
    verificationMetadataXml: PATCHED_METADATA,
  });

  assert.equal(result.passed, false);
  assert.deepEqual(
    result.errors.map((e) => `${e.code} ${e.coordinate}`),
    [
      'DEPENDENCY_NOT_MUTABLE org.bitbucket.b_c:jose4j',
      'DEPENDENCY_NOT_MUTABLE org.eclipse.jgit:org.eclipse.jgit',
      'DEPENDENCY_NOT_MUTABLE org.jdom:jdom2',
    ],
  );
  assert.match(result.errors[0].detail, /dependency_not_found/);
});

test('a real declaration beside a comment still counts', () => {
  const surface = `
buildscript {
    dependencies {
        constraints {
            // classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.jdom:jdom2:2.0.6.1")
        }
    }
}
`;

  assert.deepEqual(declaredCoordinates(surface).get('org.jdom:jdom2'), new Set(['2.0.6.1']));
});

test('a trailing comment does not hide the declaration it follows', () => {
  const surface = `
buildscript {
    dependencies {
        constraints {
            classpath("org.jdom:jdom2:2.0.6.1") // pinned by Issue #68
        }
    }
}
`;

  assert.deepEqual(declaredCoordinates(surface).get('org.jdom:jdom2'), new Set(['2.0.6.1']));
});

test('a comment on a declaration-free line is removed, not merged into code', () => {
  const surface = `
// classpath("org.jdom:jdom2:2.0.6.1")
`;
  const stripped = stripComments(surface);

  assert.doesNotMatch(stripped, /jdom2/);
  assert.equal(declaredCoordinates(surface).size, 0);
});

test('a repository URL comment marker is not treated as a comment', () => {
  // `(?<=^|\s)` is load-bearing: the `//` in a URL follows `:`, not whitespace,
  // so the string must survive comment stripping intact.
  const surface = `
repositories { maven { url = uri("https://repo1.maven.org/maven2") } }
classpath("org.jdom:jdom2:2.0.6.1")
`;

  assert.match(stripComments(surface), /https:\/\/repo1\.maven\.org\/maven2/);
  assert.deepEqual(declaredCoordinates(surface).get('org.jdom:jdom2'), new Set(['2.0.6.1']));
});

test('a block-comment marker inside a string cannot swallow a later declaration', () => {
  // Discriminating case for the block-comment lookbehind: without it, the `/*`
  // inside the string would pair with the `*/` of the real trailing comment and
  // delete the declaration in between.
  const surface = [
    'ext.odd = "a/*b"',
    'classpath("org.jdom:jdom2:2.0.6.1")',
    '/* a real trailing comment */',
    '',
  ].join('\n');

  assert.match(stripComments(surface), /jdom2:2\.0\.6\.1/);
  assert.doesNotMatch(stripComments(surface), /a real trailing comment/);
  assert.deepEqual(declaredCoordinates(surface).get('org.jdom:jdom2'), new Set(['2.0.6.1']));
});

test('reads resolved versions out of verification metadata', () => {
  const resolved = resolvedVersions(VULNERABLE_METADATA);

  assert.deepEqual(resolved.get('org.jdom:jdom2'), new Set(['2.0.6']));
  assert.deepEqual(resolved.get('org.eclipse.jgit:org.eclipse.jgit'), new Set([
    '6.10.0.202406032230-r',
  ]));
});

// --- Live repository ------------------------------------------------------

test('the committed repository satisfies the floor', () => {
  const surface = readDeclarationSurface(REPO_ROOT);
  const result = evaluate({
    declarationSurface: surface.text,
    verificationMetadataXml: readFileSync(
      join(REPO_ROOT, 'gradle', 'verification-metadata.xml'),
      'utf8',
    ),
  });

  assert.deepEqual(
    result.errors.map((e) => `${e.code} ${e.coordinate}`),
    [],
  );
  assert.equal(result.passed, true);
  assert.equal(result.remediated.length, TOOLING_ADVISORY_CONSTRAINTS.length);
});
