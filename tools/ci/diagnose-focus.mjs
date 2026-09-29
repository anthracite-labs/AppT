#!/usr/bin/env node
/**
 * AppT focused-diagnostic focus resolution (Issue #88, human revision).
 *
 * Every `diagnose.yml` mode accepts an optional `focus` input that narrows that
 * mode without widening it. This module owns what "narrow" means for each mode,
 * so the narrowing is reviewable in one file and provable without running a
 * diagnostic: it returns the exact argument vector the mode should execute, and
 * it throws on anything that would select work outside that mode.
 *
 * The contract this module enforces
 * --------------------------------
 *   * an empty `focus` reproduces the mode's current, unfocused command
 *     exactly — a focus is an addition, never a replacement of the mode;
 *   * a non-empty `focus` narrows only within its mode, either through the
 *     underlying tool's native selector (`--tests`, Jest patterns,
 *     instrumentation runner arguments) or through a finite allowlist of
 *     sub-responsibilities that mode already owns;
 *   * the value is data, never code. It is returned as a single argv entry, so
 *     no amount of shell metacharacter in it can become a command, a flag, or
 *     an unrelated task. It may contain spaces — a Jest test name legitimately
 *     does — but never a control character, and never a leading `-`;
 *   * a value that starts with `-` is rejected outright, because every
 *     underlying tool would read it as a flag rather than as a selector;
 *   * anything else — a wrong shape, a foreign prefix, a sub-responsibility the
 *     mode does not own — fails closed with a non-zero exit.
 *
 * The commands below were verified against the pinned toolchain before being
 * committed: the Gradle task paths are the ones `build.gradle.kts` declares for
 * each failure domain, the npm script names are the ones
 * `backend/package.json` declares, and the device task is the Gradle Managed
 * Device `pixel2api29` configured in `app/build.gradle.kts`.
 *
 * Deliberately zero dependencies: its provenance is the repository itself.
 *
 * Usage:
 *   node tools/ci/diagnose-focus.mjs --mode app-unit --focus com.example.FooTest
 */

/** Every diagnose mode, in the order `diagnose.yml` declares them. */
export const DIAGNOSE_MODES = Object.freeze([
  'app-unit',
  'samsung-unit',
  'android-static',
  'android-build',
  'backend-static',
  'backend-test',
  'backend',
  'device',
]);

/**
 * The Gradle invocation prefix each Android mode uses. Reproduced exactly so an
 * empty focus yields byte-for-byte the command the mode ran before focus
 * existed.
 */
const GRADLE_CONTINUE = [
  './gradlew',
  '--no-daemon',
  '--dependency-verification=strict',
  '--continue',
];

/** The device mode deliberately does not use `--continue`. */
const GRADLE_DEVICE = [
  './gradlew',
  '--no-daemon',
  '--dependency-verification=strict',
  '-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect',
];

/** npm script invocation prefix. */
const npm = (script) => ['npm', 'run', script, '--prefix', 'backend'];

/**
 * Android static sub-checks, as owned tasks. `androidStatic` in
 * `build.gradle.kts` depends on exactly these, so focusing here selects a
 * strict subset of the same responsibility.
 */
export const ANDROID_STATIC_SUBCHECKS = Object.freeze({
  lint: [':app:lintDebug', ':samsung:lintDebug'],
  detekt: [':app:detekt', ':samsung:detekt'],
  guards: ['appTGuards'],
  'dependency-lock': ['dependencyLockCheck'],
});

/**
 * Android build sub-targets, as owned tasks. `androidBuild` depends on exactly
 * these.
 */
export const ANDROID_BUILD_SUBTARGETS = Object.freeze({
  app: [':app:assembleDebug'],
  macrobenchmark: [':macrobenchmark:assembleBenchmark'],
});

/**
 * Backend static sub-checks, as npm script names. `verify:static` in
 * `backend/package.json` runs exactly these.
 */
export const BACKEND_STATIC_SUBCHECKS = Object.freeze({
  typecheck: 'typecheck',
  lint: 'lint',
  format: 'format:check',
  knip: 'knip',
});

/** The two sub-responsibilities the whole-backend mode is made of. */
export const BACKEND_RESPONSIBILITIES = Object.freeze(['static', 'test']);

/**
 * True for C0 controls and DEL. Written as a code-point scan rather than a
 * character class so the rule is unambiguous in the source: a focus may not
 * contain a newline, because the CLI encodes one argument per line, and may not
 * contain any other control character either.
 */
function hasControlCharacter(value) {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.codePointAt(index);
    if (code <= 0x1f || code === 0x7f) return true;
  }
  return false;
}

/**
 * A Gradle `--tests` pattern: a qualified class name with an optional
 * `#method`, where every segment may use `*` wildcards. It cannot contain a
 * task path, because `--tests` takes a test-name pattern, not a task.
 */
const GRADLE_TEST_PATTERN =
  /^[A-Za-z0-9_$*]+(\.[A-Za-z0-9_$*]+)*(#[A-Za-z0-9_$*]+)?$/;

/** A fully qualified Java/Kotlin class or package name. */
const FQCN = /^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$/;

/** A fully qualified class name plus a `#method` name. */
const METHOD_REF = /^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*#[A-Za-z_$][A-Za-z0-9_$]*$/;

/**
 * Validate and normalise the focus value.
 *
 * Returns `''` for "no focus", which every mode treats as its unfocused self.
 * Throws for anything that is not a safe single argv entry.
 */
export function normalizeFocus(focus) {
  if (focus === undefined || focus === null || focus === '') return '';
  if (typeof focus !== 'string') {
    throw new Error(`focus must be a string, got ${typeof focus}`);
  }
  // A newline would break the one-argument-per-line CLI encoding, and the other
  // control characters have no legitimate use in a selector. Ordinary spaces are
  // fine: they stay inside a single argv entry.
  if (hasControlCharacter(focus)) {
    throw new Error(
      `focus must not contain control characters (got ${JSON.stringify(focus)}); ` +
        'a focus is a single argv entry'
    );
  }
  // A leading dash would be read as a flag by every underlying tool, which is
  // how a focus could select work other than its own selector.
  if (focus.startsWith('-')) {
    throw new Error(
      `focus must not start with '-' (got ${JSON.stringify(focus)}); ` +
        'that would be parsed as a flag, not as a selector'
    );
  }
  return focus;
}

/**
 * A value beginning with `-` would be read as a flag by the underlying tool
 * rather than as a selector, which is how a focus could select work other than
 * its own. Rejected for the extracted value as well as for the whole focus,
 * because `file:--coverage` starts with `f` but its value is a flag.
 */
function rejectLeadingDash(value, mode) {
  if (value.startsWith('-')) {
    throw new Error(
      `focus value for ${mode} must not start with '-' (got ${JSON.stringify(value)}); ` +
        'that would be parsed as a flag, not as a selector'
    );
  }
}

function requireMatch(pattern, value, description) {
  if (!pattern.test(value)) {
    throw new Error(`focus is not a valid ${description}: ${JSON.stringify(value)}`);
  }
  return value;
}

/**
 * A Jest pattern (a positional file pattern, or a `--testNamePattern` value) is
 * a regular expression over test names and paths. Spaces are legitimate there,
 * and the global rules already reject a leading `-` and every control
 * character, so the only thing left to enforce is that a value exists.
 */
function requireJestPattern(value, description) {
  rejectLeadingDash(value, description);
  if (value.length === 0) {
    throw new Error(`focus needs a ${description}`);
  }
  return value;
}

/**
 * Split a `kind:value` focus, rejecting a focus whose kind is wrong for the
 * mode that received it.
 */
function splitKind(focus, allowedKinds, mode) {
  const separator = focus.indexOf(':');
  if (separator < 0) {
    throw new Error(
      `focus for ${mode} must be one of ${allowedKinds.map((kind) => `${kind}:<value>`).join(', ')}; ` +
        `got ${JSON.stringify(focus)}`
    );
  }
  const kind = focus.slice(0, separator);
  const value = focus.slice(separator + 1);
  rejectLeadingDash(value, mode);
  if (!allowedKinds.includes(kind)) {
    throw new Error(
      `focus kind '${kind}' is not valid for ${mode}. Valid kinds: ${allowedKinds.join(', ')}`
    );
  }
  if (value === '') {
    throw new Error(`focus '${kind}:' for ${mode} needs a value after the colon`);
  }
  return { kind, value };
}

/**
 * Resolve a mode and an optional focus into the exact command to run.
 *
 * @returns {{mode: string, focus: string, kind: string, command: string[], summary: string}}
 * @throws on an unknown mode, or any focus that is not a narrowing of that mode.
 */
export function resolveFocus({ mode, focus } = {}) {
  if (!DIAGNOSE_MODES.includes(mode)) {
    throw new Error(
      `unknown diagnose mode: ${String(mode)}. Valid modes: ${DIAGNOSE_MODES.join(', ')}`
    );
  }

  const normalized = normalizeFocus(focus);

  let kind = 'full-mode';
  let command;

  switch (mode) {
    // Gradle test-class / test-method targeting through `--tests`.
    case 'app-unit':
    case 'samsung-unit': {
      kind = 'gradle-test-pattern';
      const task = mode === 'app-unit' ? ':app:testDebugUnitTest' : ':samsung:test';
      command = [...GRADLE_CONTINUE, task];
      if (normalized !== '') {
        requireMatch(GRADLE_TEST_PATTERN, normalized, 'Gradle test class or method pattern');
        command.push('--tests', normalized);
      }
      break;
    }

    // A finite allowlist of sub-checks this mode already owns.
    case 'android-static': {
      kind = 'allowlisted-subcheck';
      command = [...GRADLE_CONTINUE, 'androidStatic'];
      if (normalized !== '') {
        if (!Object.hasOwn(ANDROID_STATIC_SUBCHECKS, normalized)) {
          throw new Error(
            `focus for android-static must be one of ${Object.keys(ANDROID_STATIC_SUBCHECKS).join(', ')}; ` +
              `got ${JSON.stringify(normalized)}`
          );
        }
        command = [...GRADLE_CONTINUE, ...ANDROID_STATIC_SUBCHECKS[normalized]];
      }
      break;
    }

    // A finite allowlist of build sub-targets this mode already owns.
    case 'android-build': {
      kind = 'allowlisted-subtarget';
      command = [...GRADLE_CONTINUE, 'androidBuild'];
      if (normalized !== '') {
        if (!Object.hasOwn(ANDROID_BUILD_SUBTARGETS, normalized)) {
          throw new Error(
            `focus for android-build must be one of ${Object.keys(ANDROID_BUILD_SUBTARGETS).join(', ')}; ` +
              `got ${JSON.stringify(normalized)}`
          );
        }
        command = [...GRADLE_CONTINUE, ...ANDROID_BUILD_SUBTARGETS[normalized]];
      }
      break;
    }

    // A finite allowlist of the backend's static sub-checks.
    case 'backend-static': {
      kind = 'allowlisted-subcheck';
      command = npm('verify:static');
      if (normalized !== '') {
        if (!Object.hasOwn(BACKEND_STATIC_SUBCHECKS, normalized)) {
          throw new Error(
            `focus for backend-static must be one of ${Object.keys(BACKEND_STATIC_SUBCHECKS).join(', ')}; ` +
              `got ${JSON.stringify(normalized)}`
          );
        }
        command = npm(BACKEND_STATIC_SUBCHECKS[normalized]);
      }
      break;
    }

    // Jest file targeting (a positional pattern) or name targeting
    // (`--testNamePattern`), passed through the mode's own npm script so the
    // unfocused command is unchanged.
    case 'backend-test': {
      kind = 'jest-targeting';
      command = npm('verify:test');
      if (normalized !== '') {
        const { kind: jestKind, value } = splitKind(normalized, ['file', 'name'], mode);
        if (jestKind === 'name') {
          requireJestPattern(value, 'Jest test name pattern');
          command = [...npm('verify:test'), '--', '--testNamePattern', value];
        } else {
          requireJestPattern(value, 'Jest file pattern');
          command = [...npm('verify:test'), '--', value];
        }
      }
      break;
    }

    // The whole backend, narrowable to its own static or test responsibility,
    // and from there to that responsibility's own child selector. A bare
    // `static` / `test` is valid; a colon with nothing after it is not.
    case 'backend': {
      kind = 'sub-responsibility';
      command = npm('verify');
      if (normalized !== '') {
        const separator = normalized.indexOf(':');
        const responsibility = separator < 0 ? normalized : normalized.slice(0, separator);
        const child = separator < 0 ? null : normalized.slice(separator + 1);

        if (!BACKEND_RESPONSIBILITIES.includes(responsibility)) {
          throw new Error(
            `focus for backend must be 'static', 'test', 'static:<sub>' where sub is one of ` +
              `${Object.keys(BACKEND_STATIC_SUBCHECKS).join(', ')}, or 'test:<pattern>'; ` +
              `got ${JSON.stringify(normalized)}`
          );
        }
        if (child === '') {
          throw new Error(
            `focus '${normalized}' for backend needs a value after the colon`
          );
        }

        if (child === null) {
          command = npm(responsibility === 'static' ? 'verify:static' : 'verify:test');
        } else if (responsibility === 'static') {
          if (!Object.hasOwn(BACKEND_STATIC_SUBCHECKS, child)) {
            throw new Error(
              `focus for backend (static) must narrow to one of ` +
                `${Object.keys(BACKEND_STATIC_SUBCHECKS).join(', ')}; ` +
                `got ${JSON.stringify(child)}`
            );
          }
          command = npm(BACKEND_STATIC_SUBCHECKS[child]);
        } else {
          requireJestPattern(child, 'Jest file pattern');
          command = [...npm('verify:test'), '--', child];
        }
      }
      break;
    }

    // Instrumentation targeting through AGP's testInstrumentationRunnerArguments
    // on the pinned managed device.
    case 'device': {
      kind = 'instrumentation-targeting';
      // Both modules' instrumented suites run on the pinned device: :app's acceptance
      // suite and :samsung's Android-runtime contracts (Issue #91: regexes the JVM
      // accepts but Android's engine rejects must fail on the device route).
      command = [
        ...GRADLE_DEVICE,
        ':app:pixel2api29DebugAndroidTest',
        ':samsung:pixel2api29DebugAndroidTest',
      ];
      if (normalized !== '') {
        const { kind: target, value } = splitKind(
          normalized,
          ['class', 'package', 'method'],
          mode
        );
        if (target === 'class') {
          requireMatch(FQCN, value, 'instrumentation test class name');
          command.push(`-Pandroid.testInstrumentationRunnerArguments.class=${value}`);
        } else if (target === 'package') {
          requireMatch(FQCN, value, 'instrumentation test package name');
          command.push(`-Pandroid.testInstrumentationRunnerArguments.package=${value}`);
        } else {
          requireMatch(METHOD_REF, value, 'instrumentation test method reference');
          command.push(`-Pandroid.testInstrumentationRunnerArguments.method=${value}`);
        }
      }
      break;
    }

    default:
      throw new Error(`unhandled diagnose mode: ${mode}`);
  }

  // No focus means the mode's whole self, whatever selector kind it otherwise
  // uses, so report it that way rather than as a kind that was never applied.
  if (normalized === '') kind = 'full-mode';

  const summary =
    normalized === ''
      ? `${mode}: full mode, no focus`
      : `${mode}: focus ${normalized} -> ${command.slice(1).join(' ')}`;

  return { mode, focus: normalized, kind, command, summary };
}

function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const next = () => {
      i += 1;
      if (i >= argv.length) throw new Error(`missing value for ${arg}`);
      return argv[i];
    };
    switch (arg) {
      case '--mode':
        options.mode = next();
        break;
      case '--focus':
        options.focus = next();
        break;
      case '--help':
      case '-h':
        options.help = true;
        break;
      default:
        throw new Error(`unknown argument: ${arg}`);
    }
  }
  return options;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log(
      'Usage: node tools/ci/diagnose-focus.mjs --mode <mode> [--focus <focus>]\n' +
        `Modes: ${DIAGNOSE_MODES.join(', ')}\n\n` +
        'Prints the resolved command, one argument per line, on stdout. Exits\n' +
        'non-zero — before running anything — if the focus does not narrow that\n' +
        'mode.'
    );
    return;
  }

  try {
    const resolved = resolveFocus({
      mode: options.mode ?? process.env.DIAGNOSE_MODE,
      focus: options.focus ?? process.env.DIAGNOSE_FOCUS ?? '',
    });
    // One argument per line. A focus value can never contain a newline, so the
    // encoding is unambiguous, and every token stays a single argv entry.
    process.stdout.write(`${resolved.command.join('\n')}\n`);
  } catch (error) {
    console.error(`diagnose-focus: ${error.message}`);
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === `file://${process.argv[1]}`) {
  main();
}
