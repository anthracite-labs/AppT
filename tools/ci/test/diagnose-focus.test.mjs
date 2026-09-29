/**
 * Tests for the focused-diagnostic focus contract (Issue #88, human revision).
 *
 * Every `diagnose.yml` mode accepts an optional `focus`. These tests are what
 * makes that safe to expose: they prove that an empty focus reproduces the
 * mode's existing command exactly, that a valid focus narrows within the mode,
 * that anything else fails closed, and that a focus value can never become a
 * command, a flag, or a task the mode does not own.
 */

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import {
  ANDROID_BUILD_SUBTARGETS,
  ANDROID_STATIC_SUBCHECKS,
  BACKEND_STATIC_SUBCHECKS,
  DIAGNOSE_MODES,
  normalizeFocus,
  resolveFocus,
} from '../diagnose-focus.mjs';

const MODULE = fileURLToPath(new URL('../diagnose-focus.mjs', import.meta.url));

/**
 * The command each mode ran before `focus` existed, transcribed from
 * `diagnose.yml`. An empty focus must reproduce these exactly, so a focus can
 * only ever narrow a mode — never silently replace it.
 */
const UNFOCUSED_COMMANDS = {
  'app-unit': [
    './gradlew',
    '--no-daemon',
    '--dependency-verification=strict',
    '--continue',
    ':app:testDebugUnitTest',
  ],
  'samsung-unit': [
    './gradlew',
    '--no-daemon',
    '--dependency-verification=strict',
    '--continue',
    ':samsung:test',
  ],
  'android-static': [
    './gradlew',
    '--no-daemon',
    '--dependency-verification=strict',
    '--continue',
    'androidStatic',
  ],
  'android-build': [
    './gradlew',
    '--no-daemon',
    '--dependency-verification=strict',
    '--continue',
    'androidBuild',
  ],
  'backend-static': ['npm', 'run', 'verify:static', '--prefix', 'backend'],
  'backend-test': ['npm', 'run', 'verify:test', '--prefix', 'backend'],
  backend: ['npm', 'run', 'verify', '--prefix', 'backend'],
  device: [
    './gradlew',
    '--no-daemon',
    '--dependency-verification=strict',
    '-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect',
    ':app:pixel2api29DebugAndroidTest',
    ':samsung:pixel2api29DebugAndroidTest',
  ],
};

/**
 * The invocation prefix each mode uses, independent of any focus. A focus may
 * change what is invoked; it may never change how.
 */
const INVOCATION_PREFIX = {
  'app-unit': ['./gradlew', '--no-daemon', '--dependency-verification=strict', '--continue'],
  'samsung-unit': ['./gradlew', '--no-daemon', '--dependency-verification=strict', '--continue'],
  'android-static': ['./gradlew', '--no-daemon', '--dependency-verification=strict', '--continue'],
  'android-build': ['./gradlew', '--no-daemon', '--dependency-verification=strict', '--continue'],
  'backend-static': ['npm', 'run'],
  'backend-test': ['npm', 'run'],
  backend: ['npm', 'run'],
  device: [
    './gradlew',
    '--no-daemon',
    '--dependency-verification=strict',
    '-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect',
  ],
};

/**
 * The work each mode already owns. A focus may only ever select from here, so a
 * focused command naming anything outside this set is a widening, not a
 * narrowing — which is what the "cannot select unrelated tasks" rule means.
 */
const OWNED_GRADLE_TASKS = {
  'app-unit': [':app:testDebugUnitTest'],
  'samsung-unit': [':samsung:test'],
  'android-static': [
    ':app:lintDebug',
    ':samsung:lintDebug',
    ':app:detekt',
    ':samsung:detekt',
    'appTGuards',
    'dependencyLockCheck',
  ],
  'android-build': [':app:assembleDebug', ':macrobenchmark:assembleBenchmark'],
  device: [':app:pixel2api29DebugAndroidTest', ':samsung:pixel2api29DebugAndroidTest'],
};

const OWNED_NPM_SCRIPTS = {
  'backend-static': ['verify:static', 'typecheck', 'lint', 'format:check', 'knip'],
  'backend-test': ['verify:test'],
  backend: ['verify', 'verify:static', 'verify:test', 'typecheck', 'lint', 'format:check', 'knip'],
};

/** Every task or script a focused command names must be one the mode owns. */
function assertNarrowsOnly(mode, command) {
  const ownedTasks = OWNED_GRADLE_TASKS[mode];
  if (ownedTasks) {
    const tasks = command.filter((token) => token.startsWith(':') || token === 'androidStatic');
    for (const task of tasks) {
      assert.ok(
        ownedTasks.includes(task),
        `${mode} focused to ${task}, which it does not own (owns: ${ownedTasks.join(', ')})`
      );
    }
  }
  const ownedScripts = OWNED_NPM_SCRIPTS[mode];
  if (ownedScripts) {
    const script = command[2];
    assert.ok(
      ownedScripts.includes(script),
      `${mode} focused to npm script ${script}, which it does not own`
    );
  }
}

/** Run the module exactly as the workflow does, and capture stdout and status. */
function runCli(mode, focus) {
  const args = ['--mode', mode];
  if (focus !== undefined) args.push('--focus', focus);
  try {
    const stdout = execFileSync(process.execPath, [MODULE, ...args], {
      encoding: 'utf8',
    });
    return { ok: true, stdout };
  } catch (error) {
    return { ok: false, stderr: error.stderr ?? '', status: error.status };
  }
}

describe('empty focus preserves the current command', () => {
  for (const mode of DIAGNOSE_MODES) {
    it(`${mode} with no focus runs exactly its existing command`, () => {
      // Every spelling of "no focus" must agree, including the one the workflow
      // produces when the input is left at its empty default.
      for (const focus of [undefined, '', null]) {
        const resolved = resolveFocus({ mode, focus });
        assert.deepEqual(
          resolved.command,
          UNFOCUSED_COMMANDS[mode],
          `${mode} with focus ${JSON.stringify(focus)}`
        );
        assert.equal(resolved.focus, '');
        assert.equal(resolved.kind, 'full-mode');
      }
    });
  }

  it('the workflow sees the same command through the CLI', () => {
    for (const mode of DIAGNOSE_MODES) {
      const result = runCli(mode);
      assert.equal(result.ok, true, `${mode}: ${result.stderr}`);
      assert.deepEqual(
        result.stdout.split('\n').filter((line) => line !== ''),
        UNFOCUSED_COMMANDS[mode],
        mode
      );
    }
  });
});

describe('a valid focus narrows within its mode', () => {
  const cases = [
    // Gradle test class / method patterns through `--tests`, appended to the
    // mode's own test task.
    ['app-unit', 'com.example.FooTest', [':app:testDebugUnitTest', '--tests', 'com.example.FooTest']],
    ['app-unit', 'com.example.FooTest.testBar', [
      ':app:testDebugUnitTest',
      '--tests',
      'com.example.FooTest.testBar',
    ]],
    ['app-unit', '*Smoke*', [':app:testDebugUnitTest', '--tests', '*Smoke*']],
    ['samsung-unit', 'dev.anthracite.appt.samsung.DiscoveryTest', [
      ':samsung:test',
      '--tests',
      'dev.anthracite.appt.samsung.DiscoveryTest',
    ]],

    // Allowlisted Android static sub-checks, as the tasks the domain owns.
    ...Object.entries(ANDROID_STATIC_SUBCHECKS).map(([focus, tasks]) => [
      'android-static',
      focus,
      tasks,
    ]),

    // Allowlisted Android build sub-targets.
    ...Object.entries(ANDROID_BUILD_SUBTARGETS).map(([focus, tasks]) => [
      'android-build',
      focus,
      tasks,
    ]),

    // Allowlisted backend static sub-checks, as npm script names.
    ...Object.entries(BACKEND_STATIC_SUBCHECKS).map(([focus, script]) => [
      'backend-static',
      focus,
      [script, '--prefix', 'backend'],
    ]),

    // Jest file and name targeting, through the mode's own npm script.
    ['backend-test', 'file:src/foo.test.ts', ['verify:test', '--prefix', 'backend', '--', 'src/foo.test.ts']],
    ['backend-test', 'name:handles a retry', [
      'verify:test',
      '--prefix',
      'backend',
      '--',
      '--testNamePattern',
      'handles a retry',
    ]],

    // The whole backend narrowing to its own responsibilities.
    ['backend', 'static', ['verify:static', '--prefix', 'backend']],
    ['backend', 'test', ['verify:test', '--prefix', 'backend']],
    ['backend', 'static:knip', ['knip', '--prefix', 'backend']],
    ['backend', 'test:src/foo.test.ts', ['verify:test', '--prefix', 'backend', '--', 'src/foo.test.ts']],

    // Instrumentation targeting on the pinned managed device.
    ['device', 'class:dev.anthracite.appt.SmokeTest', [
      ':app:pixel2api29DebugAndroidTest',
    ':samsung:pixel2api29DebugAndroidTest',
      '-Pandroid.testInstrumentationRunnerArguments.class=dev.anthracite.appt.SmokeTest',
    ]],
    ['device', 'package:dev.anthracite.appt', [
      ':app:pixel2api29DebugAndroidTest',
    ':samsung:pixel2api29DebugAndroidTest',
      '-Pandroid.testInstrumentationRunnerArguments.package=dev.anthracite.appt',
    ]],
    ['device', 'method:dev.anthracite.appt.SmokeTest#launches', [
      ':app:pixel2api29DebugAndroidTest',
    ':samsung:pixel2api29DebugAndroidTest',
      '-Pandroid.testInstrumentationRunnerArguments.method=dev.anthracite.appt.SmokeTest#launches',
    ]],
  ];

  for (const [mode, focus, expectedTail] of cases) {
    it(`${mode} focused on ${focus}`, () => {
      const resolved = resolveFocus({ mode, focus });
      // The invocation prefix is untouched: a focus may change what is invoked,
      // never how it is invoked.
      assert.deepEqual(
        resolved.command.slice(0, INVOCATION_PREFIX[mode].length),
        INVOCATION_PREFIX[mode],
        'a focus must not change how the mode is invoked'
      );
      // Everything after that prefix is exactly the expected selector.
      assert.deepEqual(resolved.command.slice(INVOCATION_PREFIX[mode].length), expectedTail);
      assert.equal(resolved.focus, focus);
      // And it selects only from work the mode already owns.
      assertNarrowsOnly(mode, resolved.command);
    });
  }

  it('a focus never adds a Gradle task the mode does not own', () => {
    // The gradle-test modes narrow with `--tests`, which is a test-name filter.
    // Even a value that names another task stays a filter, so it can select no
    // task other than the mode's own.
    for (const task of ['androidStatic', 'clean', 'ciCheck', 'lint']) {
      const resolved = resolveFocus({ mode: 'app-unit', focus: task });
      const tasks = resolved.command.filter((token) => token.startsWith(':'));
      assert.deepEqual(tasks, [':app:testDebugUnitTest'], task);
      assert.deepEqual(resolved.command.slice(-2), ['--tests', task], task);
    }

    // A task *path* is not even a valid test pattern, so it is refused outright
    // rather than being passed through as a filter.
    for (const taskPath of [':app:assembleDebug', ':samsung:test', ':app:lintDebug']) {
      assert.throws(
        () => resolveFocus({ mode: 'app-unit', focus: taskPath }),
        /not a valid Gradle test class or method pattern/,
        taskPath
      );
    }
  });
});

describe('invalid and cross-mode focus fails closed', () => {
  const rejections = [
    // A wrong shape for the mode.
    ['app-unit', 'name:Foo'],
    ['samsung-unit', 'class:x'],
    ['app-unit', 'lint && echo pwned'],
    ['app-unit', ':app:assembleDebug'],
    ['app-unit', 'com..example'],
    ['app-unit', 'Foo#'],
    ['app-unit', 'Foo bar'],

    // A sub-responsibility the mode does not own — including one that belongs
    // to a different mode, which is what "cross-mode" means here.
    ['android-static', 'typecheck'],
    ['android-static', 'knip'],
    ['android-static', 'nope'],
    ['android-build', 'lint'],
    ['android-build', 'nope'],
    ['backend-static', 'detekt'],
    ['backend-static', 'nope'],

    // A kind the mode does not accept.
    ['backend-test', 'lint'],
    ['backend-test', 'class:x'],
    ['backend-test', 'file:'],
    ['backend-test', 'name:'],
    ['backend-test', 'file:--coverage'],
    ['backend-test', 'name:--testNamePattern'],
    ['backend', 'lint'],
    ['backend', 'static:'],
    ['backend', 'test:'],
    ['backend', 'static:nope'],
    ['backend', 'test:--coverage'],
    ['device', 'lint'],
    ['device', 'class:9bad'],
    ['device', 'method:Foo'],
    ['device', 'package:a..b'],
    ['device', 'class:-x'],

    // A value that would be read as a flag rather than as a selector.
    ['app-unit', '--tests'],
    ['app-unit', '-x'],
    ['backend-test', '-x'],
    ['device', '-x'],
  ];

  for (const [mode, focus] of rejections) {
    it(`${mode} rejects ${JSON.stringify(focus)}`, () => {
      assert.throws(() => resolveFocus({ mode, focus }), Error, `${mode} / ${focus}`);
      // The CLI fails before running anything, which is what the workflow's
      // `set -e` relies on.
      const result = runCli(mode, focus);
      assert.equal(result.ok, false, `${mode} / ${focus} must fail`);
      assert.notEqual(result.status, 0);
    });
  }

  it('rejects an unknown mode', () => {
    assert.throws(() => resolveFocus({ mode: 'not-a-mode' }), /unknown diagnose mode/);
    const result = runCli('not-a-mode');
    assert.equal(result.ok, false);
  });

  it('rejects a non-string focus', () => {
    for (const focus of [42, {}, [], true]) {
      assert.throws(() => resolveFocus({ mode: 'app-unit', focus }), /focus must be a string/);
    }
  });
});

describe('special characters stay data', () => {
  const hostile = [
    'Foo;rm -rf /',
    'Foo$(whoami)',
    'Foo`whoami`',
    'Foo&&echo pwned',
    'Foo|cat',
    'Foo>out',
    "Foo'quote'",
    'Foo"dquote"',
    'Foo\nBar',
    'Foo\tBar',
    'Foo\u0000Bar',
  ];

  for (const value of hostile) {
    it(`${JSON.stringify(value)} is never a command`, () => {
      // Either it is rejected outright, or it survives as exactly one argv
      // entry. There is no third outcome in which it becomes a command, a flag
      // or a second argument.
      let resolved;
      try {
        resolved = resolveFocus({ mode: 'app-unit', focus: value });
      } catch {
        return;
      }
      assert.equal(resolved.focus, value);
      const occurrences = resolved.command.filter((token) => token === value);
      assert.equal(occurrences.length, 1, 'the value is exactly one argv entry');
      assert.equal(resolved.command.at(-1), value, 'the value is the last argument');
    });
  }

  it('a shell metacharacter is refused as a Gradle test pattern', () => {
    // `--tests` takes a test-name pattern, so anything that is not one is
    // rejected before it can reach the command line at all.
    for (const value of ['Foo;rm -rf /', 'Foo$(whoami)', 'Foo`whoami`', 'Foo&&x', 'Foo|x']) {
      assert.throws(
        () => resolveFocus({ mode: 'app-unit', focus: value }),
        /not a valid Gradle test class or method pattern/,
        value
      );
    }
  });

  it('a shell metacharacter survives as a single Jest pattern argument', () => {
    // The Jest modes take a pattern, which is legitimately free-form, so the
    // metacharacters have to survive — as exactly one argv entry.
    for (const value of ['src/foo$(whoami).test.ts', 'src/foo`id`.test.ts', 'src/a&&b.test.ts']) {
      const resolved = resolveFocus({ mode: 'backend-test', focus: `file:${value}` });
      assert.equal(resolved.command.at(-1), value, value);
      assert.equal(resolved.command.at(-2), '--', value);
    }
  });

  it('a space survives inside a single argv entry', () => {
    const resolved = resolveFocus({ mode: 'backend-test', focus: 'name:handles a retry' });
    assert.equal(resolved.command.at(-1), 'handles a retry');
  });
});

describe('normalizeFocus', () => {
  it('treats absent and empty as no focus', () => {
    for (const focus of [undefined, null, '']) {
      assert.equal(normalizeFocus(focus), '');
    }
  });

  it('rejects control characters, including the newline that would break the encoding', () => {
    for (const focus of ['a\nb', 'a\tb', 'a\rb', 'a\u0000b', 'a\u007fb']) {
      assert.throws(() => normalizeFocus(focus), /control characters/, JSON.stringify(focus));
    }
  });

  it('rejects a leading dash', () => {
    assert.throws(() => normalizeFocus('--tests'), /must not start with '-'/);
    assert.throws(() => normalizeFocus('-x'), /must not start with '-'/);
  });

  it('accepts a value containing spaces', () => {
    assert.equal(normalizeFocus('handles a retry'), 'handles a retry');
  });
});

describe('the CLI encoding round-trips', () => {
  it('one argument per line, with no trailing blank argument', () => {
    const result = runCli('app-unit', 'com.example.FooTest');
    assert.equal(result.ok, true, result.stderr);
    const lines = result.stdout.split('\n');
    assert.equal(lines.at(-1), '', 'the output ends with a newline');
    const args = lines.slice(0, -1);
    assert.deepEqual(args, resolveFocus({ mode: 'app-unit', focus: 'com.example.FooTest' }).command);
  });

  it('an argument containing a space stays on one line', () => {
    const result = runCli('backend-test', 'name:handles a retry');
    assert.equal(result.ok, true, result.stderr);
    const args = result.stdout.split('\n').filter((line) => line !== '');
    assert.equal(args.at(-1), 'handles a retry');
  });

  it('records the mode and the effective focus in its summary', () => {
    assert.match(resolveFocus({ mode: 'app-unit' }).summary, /app-unit: full mode, no focus/);
    assert.match(
      resolveFocus({ mode: 'app-unit', focus: 'com.example.FooTest' }).summary,
      /app-unit: focus com\.example\.FooTest/
    );
  });
});
