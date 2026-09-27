/**
 * Tests for the dispatch-target assertion's shell logic (Issue #88).
 *
 * `.github/actions/assert-dispatch-target` is the enforcement point that decides
 * whether a dispatched run verifies the commit the command was issued against,
 * so its behaviour has to be proven rather than assumed. Its `run:` block is
 * extracted from the action definition and executed for real, against a fake
 * `gh` on `PATH`, so every branch of the decision is exercised.
 *
 * The case that matters most is the one the CodeQL finding forced: a
 * `workflow_dispatch` input is settable by anyone who can dispatch, so an
 * arbitrary commit SHA must not be able to pass merely by naming a branch that
 * happens to resolve to it. It also has to be the head of an open pull request.
 */

import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

const ACTION = fileURLToPath(
  new URL('../../../.github/actions/assert-dispatch-target/action.yml', import.meta.url)
);

const REPO = 'anthracite-labs/AppT';
const OWNER = 'anthracite-labs';
const HEAD_SHA = '10525a115869dcf3e9d49cf2053c03d284200249';
const OTHER_SHA = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
const HEAD_REF = 'arena/01a0e487-appt';

/**
 * Pull the `run:` script out of the composite action. The action has exactly one
 * shell step, so the block is unambiguous; the indentation is stripped the same
 * way the workflow engine strips it.
 */
function extractRunScript() {
  const action = readFileSync(ACTION, 'utf8');
  const marker = '      run: |\n';
  const start = action.indexOf(marker);
  assert.notEqual(start, -1, 'the action must have a run block');
  const body = action.slice(start + marker.length);
  const lines = [];
  for (const line of body.split('\n')) {
    if (line.trim() === '') {
      lines.push('');
      continue;
    }
    if (!line.startsWith('        ')) break;
    lines.push(line.slice(8));
  }
  const script = lines.join('\n').trimEnd();
  assert.ok(script.includes('EXPECTED_SHA'), 'the extracted script must be the assertion');
  return script;
}

const RUN_SCRIPT = extractRunScript();

/**
 * Build a sandbox with a fake `gh` that answers the two read-only calls the
 * assertion makes: resolving a ref to a commit, and listing open pull requests
 * by head ref.
 */
function createSandbox({
  refSha = HEAD_SHA,
  openHeads = [HEAD_SHA],
  refUnresolvable = false,
  pullsError = false,
} = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'assert-dispatch-target-'));
  const log = join(dir, 'gh.log');

  // The assertion calls `gh api <path> --jq <projection>`, so `$2` is the path
  // and the fake answers the *projected* value, exactly as the real CLI would.
  const gh = `#!/usr/bin/env bash
echo "$*" >> '${log}'
case "$2" in
  */commits/*)
    if [ "\${REF_UNRESOLVABLE:-0}" = "1" ]; then exit 1; fi
    printf '%s\\n' "\${REF_SHA}"
    ;;
  */pulls*)
    if [ "\${PULLS_ERROR:-0}" = "1" ]; then exit 1; fi
    for sha in \${OPEN_HEADS}; do printf '%s\\n' "$sha"; done
    ;;
  *) echo "unexpected gh call: $*" >&2; exit 1 ;;
esac
`;

  writeFileSync(join(dir, 'gh'), gh, { mode: 0o755 });
  writeFileSync(
    join(dir, 'env.sh'),
    [
      `export REF_SHA='${refSha}'`,
      `export OPEN_HEADS='${openHeads.join(' ')}'`,
      `export REF_UNRESOLVABLE='${refUnresolvable ? 1 : 0}'`,
      `export PULLS_ERROR='${pullsError ? 1 : 0}'`,
      '',
    ].join('\n')
  );
  return { dir, log };
}

/** Run the assertion with the given inputs; return status, stdout and output. */
function runAssertion(sandbox, { expectedSha, targetRef, actualSha, eventName = 'workflow_dispatch' }) {
  const outputFile = join(sandbox.dir, 'github-output');
  writeFileSync(outputFile, '');
  const env = {
    ...process.env,
    PATH: `${sandbox.dir}:${process.env.PATH}`,
    EXPECTED_SHA: expectedSha ?? '',
    TARGET_REF: targetRef ?? '',
    ACTUAL_SHA: actualSha ?? HEAD_SHA,
    EVENT_NAME: eventName,
    REPOSITORY: REPO,
    REPOSITORY_OWNER: OWNER,
    GH_TOKEN: 'fake-token',
    GITHUB_OUTPUT: outputFile,
  };
  const stdout = execFileSync('bash', ['-c', `. '${join(sandbox.dir, 'env.sh')}'; bash -s`], {
    env,
    input: RUN_SCRIPT,
    encoding: 'utf8',
    stdio: ['pipe', 'pipe', 'pipe'],
  }).toString();
  return { stdout, output: readFileSync(outputFile, 'utf8') };
}

/** Run the assertion expecting a non-zero exit; return the captured stderr. */
function runAssertionExpectingFailure(sandbox, inputs) {
  const outputFile = join(sandbox.dir, 'github-output');
  writeFileSync(outputFile, '');
  const env = {
    ...process.env,
    PATH: `${sandbox.dir}:${process.env.PATH}`,
    EXPECTED_SHA: inputs.expectedSha ?? '',
    TARGET_REF: inputs.targetRef ?? '',
    ACTUAL_SHA: inputs.actualSha ?? HEAD_SHA,
    EVENT_NAME: inputs.eventName ?? 'workflow_dispatch',
    REPOSITORY: REPO,
    REPOSITORY_OWNER: OWNER,
    GH_TOKEN: 'fake-token',
    GITHUB_OUTPUT: outputFile,
  };
  try {
    execFileSync('bash', ['-c', `. '${join(sandbox.dir, 'env.sh')}'; bash -s`], {
      env,
      input: RUN_SCRIPT,
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'pipe'],
    });
  } catch (error) {
    return { status: error.status, stderr: (error.stderr ?? '').toString() };
  }
  throw new Error('the assertion unexpectedly succeeded');
}

describe('the assertion is a no-op when there is nothing to assert', () => {
  it('skips for a non-dispatch event and publishes nothing', () => {
    const sandbox = createSandbox();
    const result = runAssertion(sandbox, {
      expectedSha: '',
      targetRef: '',
      actualSha: HEAD_SHA,
      eventName: 'pull_request',
    });
    assert.match(result.stdout, /skipping the dispatch-target assertion/);
    assert.equal(result.output.trim(), '');
  });
});

describe('an ordinary manual dispatch', () => {
  it('accepts a run whose own commit is the expected one', () => {
    const sandbox = createSandbox();
    const result = runAssertion(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: '',
      actualSha: HEAD_SHA,
    });
    assert.match(result.stdout, /Dispatch target confirmed/);
    assert.equal(result.output.trim(), `sha=${HEAD_SHA}`);
  });

  it('refuses a run whose own commit moved', () => {
    const sandbox = createSandbox();
    const { stderr } = runAssertionExpectingFailure(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: '',
      actualSha: OTHER_SHA,
    });
    assert.match(stderr, /Dispatch target moved/);
    assert.match(stderr, new RegExp(HEAD_SHA));
    assert.match(stderr, new RegExp(OTHER_SHA));
  });
});

describe('a bridge dispatch', () => {
  it('accepts a target that resolves to the expected commit and is an open pull-request head', () => {
    const sandbox = createSandbox();
    const result = runAssertion(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
      // The run's own commit is the default branch, not the target.
      actualSha: OTHER_SHA,
    });
    assert.match(result.stdout, /open pull-request head resolving to/);
    assert.equal(result.output.trim(), `sha=${HEAD_SHA}`);
  });

  it('refuses a target ref that moved', () => {
    const sandbox = createSandbox({ refSha: OTHER_SHA });
    const { stderr } = runAssertionExpectingFailure(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
      actualSha: OTHER_SHA,
    });
    assert.match(stderr, /Dispatch target moved/);
    assert.match(stderr, /Re-issue the command label/);
  });

  it('refuses an unresolvable target ref', () => {
    const sandbox = createSandbox({ refUnresolvable: true });
    const { stderr } = runAssertionExpectingFailure(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
    });
    assert.match(stderr, /Dispatch target unresolvable/);
  });

  // The case the CodeQL finding forced into the open: a `workflow_dispatch` input
  // is settable by anyone who can dispatch, so an arbitrary commit must not pass
  // just because some branch resolves to it.
  it('refuses a commit that is not the head of any open pull request', () => {
    const sandbox = createSandbox({ refSha: HEAD_SHA, openHeads: [OTHER_SHA] });
    const { stderr } = runAssertionExpectingFailure(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
    });
    assert.match(stderr, /not the head of any open pull request/);
  });

  it('refuses a commit that is the head of a closed pull request', () => {
    const sandbox = createSandbox({ refSha: HEAD_SHA, openHeads: [] });
    const { stderr } = runAssertionExpectingFailure(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
    });
    assert.match(stderr, /not the head of any open pull request/);
  });

  it('refuses when the open-pull-request lookup fails', () => {
    const sandbox = createSandbox({ pullsError: true });
    const { stderr } = runAssertionExpectingFailure(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
    });
    assert.match(stderr, /not the head of any open pull request/);
  });

  it('accepts when several open pull requests share the head ref', () => {
    const sandbox = createSandbox({ refSha: HEAD_SHA, openHeads: [OTHER_SHA, HEAD_SHA] });
    const result = runAssertion(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
    });
    assert.equal(result.output.trim(), `sha=${HEAD_SHA}`);
  });

  it('publishes the proven commit, so callers check that out rather than the input', () => {
    const sandbox = createSandbox();
    const result = runAssertion(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
      actualSha: OTHER_SHA,
    });
    // The published value is the proven commit. It is deliberately the same
    // string as the input here -- what matters is that it is only ever published
    // after the checks above have passed.
    assert.equal(result.output.trim(), `sha=${HEAD_SHA}`);
  });
});
