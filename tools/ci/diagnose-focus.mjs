#!/usr/bin/env node

import { appendFileSync } from 'node:fs';

export const DIAGNOSE_MODES = Object.freeze([
  'app-unit',
  'samsung-unit',
  'android-static',
  'android-build',
  'backend',
]);

const GRADLE_CONTINUE = [
  './gradlew',
  '--no-daemon',
  '--dependency-verification=strict',
  '--continue',
];

const npm = (script) => ['npm', 'run', script, '--prefix', 'backend'];

export const ANDROID_STATIC_SUBCHECKS = Object.freeze({
  lint: [':app:lintDebug', ':samsung:lintDebug'],
  detekt: [':app:detekt', ':samsung:detekt'],
  guards: ['appTGuards'],
});

export const ANDROID_BUILD_SUBTARGETS = Object.freeze({
  app: [':app:assembleDebug'],
  'release-guard': [':app:verifyReleaseS05Boundaries'],
});

export const BACKEND_STATIC_SUBCHECKS = Object.freeze({
  typecheck: 'typecheck',
  lint: 'lint',
  format: 'format:check',
  knip: 'knip',
});

export const BACKEND_RESPONSIBILITIES = Object.freeze(['static', 'test']);

function hasControlCharacter(value) {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.codePointAt(index);
    if (code <= 0x1f || code === 0x7f) return true;
  }
  return false;
}

const GRADLE_TEST_PATTERN =
  /^[A-Za-z0-9_$*]+(\.[A-Za-z0-9_$*]+)*(#[A-Za-z0-9_$*]+)?$/;

export function normalizeFocus(focus) {
  if (focus === undefined || focus === null || focus === '') return '';
  if (typeof focus !== 'string') {
    throw new Error(`focus must be a string, got ${typeof focus}`);
  }
  if (hasControlCharacter(focus)) {
    throw new Error(
      `focus must not contain control characters (got ${JSON.stringify(focus)}); ` +
        'a focus is a single argv entry'
    );
  }
  if (focus.startsWith('-')) {
    throw new Error(
      `focus must not start with '-' (got ${JSON.stringify(focus)}); ` +
        'that would be parsed as a flag, not as a selector'
    );
  }
  return focus;
}

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

function requireJestPattern(value, description) {
  rejectLeadingDash(value, description);
  if (value.length === 0) {
    throw new Error(`focus needs a ${description}`);
  }
  return value;
}

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

    default:
      throw new Error(`unhandled diagnose mode: ${mode}`);
  }

  if (normalized === '') kind = 'full-mode';

  const summary =
    normalized === ''
      ? `${mode}: full mode, no focus`
      : `${mode}: focus ${normalized} -> ${command.slice(1).join(' ')}`;

  return { mode, focus: normalized, kind, command, summary };
}


export function resolveModeSelection({ mode, focus } = {}) {
  if (typeof mode !== 'string' || mode.length === 0) {
    throw new Error('diagnose mode selection must be a non-empty string');
  }
  if (/\s/.test(mode)) {
    throw new Error(
      `diagnose mode selection must not contain whitespace: ${JSON.stringify(mode)}`
    );
  }

  const modes = mode.split(',');
  if (modes.some((candidate) => candidate.length === 0)) {
    throw new Error(
      `diagnose mode selection contains an empty mode: ${JSON.stringify(mode)}`
    );
  }

  const unknown = modes.filter((candidate) => !DIAGNOSE_MODES.includes(candidate));
  if (unknown.length > 0) {
    throw new Error(
      `unknown diagnose mode(s): ${unknown.join(', ')}. Valid modes: ${DIAGNOSE_MODES.join(', ')}`
    );
  }

  if (new Set(modes).size !== modes.length) {
    throw new Error(
      `diagnose mode selection contains duplicate modes: ${JSON.stringify(mode)}`
    );
  }

  const normalizedFocus = normalizeFocus(focus);
  if (modes.length > 1 && normalizedFocus !== '') {
    throw new Error('focus is only valid when exactly one diagnose mode is selected');
  }

  if (modes.length === 1) {
    resolveFocus({ mode: modes[0], focus: normalizedFocus });
  }

  return {
    modes,
    canonical: modes.join(','),
    focus: normalizedFocus,
    kind: modes.length === 1 ? 'single' : 'multi',
  };
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
      case '--selection-output':
        options.selectionOutput = next();
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
      'Usage: node tools/ci/diagnose-focus.mjs --mode <mode[,mode...]> [--focus <focus>] ' +
        '[--selection-output <path>]\n' +
        `Modes: ${DIAGNOSE_MODES.join(', ')}\n\n` +
        'Without --selection-output, prints one single-mode command argument per line.\n' +
        'With --selection-output, validates the mode set and writes named GitHub outputs.\n' +
        'Multi-mode selections reject focus and run each selected mode whole.'
    );
    return;
  }

  try {
    const mode = options.mode ?? process.env.DIAGNOSE_MODE;
    const requestedFocus = options.focus ?? process.env.DIAGNOSE_FOCUS ?? '';

    if (options.selectionOutput) {
      const selection = resolveModeSelection({ mode, focus: requestedFocus });
      const selected = new Set(selection.modes);
      const outputLines = [
        `modes=${selection.canonical}`,
        `selection_kind=${selection.kind}`,
        ...DIAGNOSE_MODES.map(
          (candidate) => `${candidate.replaceAll('-', '_')}=${selected.has(candidate)}`
        ),
      ];
      appendFileSync(options.selectionOutput, `${outputLines.join('\n')}\n`);
      return;
    }

    const resolved = resolveFocus({ mode, focus: requestedFocus });
    process.stdout.write(`${resolved.command.join('\n')}\n`);
  } catch (error) {
    console.error(`diagnose-focus: ${error.message}`);
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === `file://${process.argv[1]}`) {
  main();
}
