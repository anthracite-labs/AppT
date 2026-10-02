
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
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

function createSandbox({
  refSha = HEAD_SHA,
  openPulls = [{ number: 89, sha: HEAD_SHA }],
  refUnresolvable = false,
  pullsError = false,
} = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'assert-dispatch-target-'));
  const log = join(dir, 'gh.log');

  const gh = `#!/usr/bin/env bash
echo "$*" >> '${log}'
case "$2" in
  */git/ref/heads/*)
    [ "$3" = "--jq" ] && [ "$4" = ".object.sha" ] || exit 1
    if [ "\${REF_UNRESOLVABLE:-0}" = "1" ]; then exit 1; fi
    printf '%s\\n' "\${REF_SHA}"
    ;;
  */pulls*)
    [ "$3" = "--jq" ] && [ "$4" = ".[] | [.number, .head.sha] | @tsv" ] || exit 1
    if [ "\${PULLS_ERROR:-0}" = "1" ]; then exit 1; fi
    printf '%b' "\${OPEN_PULLS}"
    ;;
  *) echo "unexpected gh call: $*" >&2; exit 1 ;;
esac
`;

  writeFileSync(join(dir, 'gh'), gh, { mode: 0o755 });
  writeFileSync(
    join(dir, 'env.sh'),
    [
      `export REF_SHA='${refSha}'`,
      `export OPEN_PULLS='${openPulls.map(({ number, sha }) => `${number}\\t${sha}\\n`).join('')}'`,
      `export REF_UNRESOLVABLE='${refUnresolvable ? 1 : 0}'`,
      `export PULLS_ERROR='${pullsError ? 1 : 0}'`,
      '',
    ].join('\n')
  );
  return { dir, log };
}

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

function assertRequestTransport(calls, targetRef) {
  assert.equal(calls.length, 2);
  const endpoints = calls.map((call, index) => {
    const projection = index === 0 ? '.object.sha' : '.[] | [.number, .head.sha] | @tsv';
    const suffix = ` --jq ${projection}`;
    assert.ok(call.startsWith('api '));
    assert.ok(call.endsWith(suffix));
    return call.slice(4, -suffix.length);
  });
  const [refUrl, pullsUrl] = endpoints.map((endpoint) => new URL(`https://api.github.com/${endpoint}`));
  const prefix = `/repos/${REPO}/git/ref/heads/`;
  assert.ok(refUrl.pathname.startsWith(prefix));
  assert.equal(refUrl.search, '', 'branch characters cannot introduce a query');
  assert.equal(refUrl.hash, '', 'branch characters cannot introduce a fragment');
  assert.deepEqual(
    refUrl.pathname.slice(prefix.length).split('/').map(decodeURIComponent),
    targetRef.split('/'),
    'each branch path segment round-trips exactly, preserving slash separators'
  );
  assert.equal(pullsUrl.pathname, `/repos/${REPO}/pulls`);
  assert.equal(pullsUrl.hash, '');
  assert.deepEqual([...pullsUrl.searchParams], [
    ['head', `${OWNER}:${targetRef}`],
    ['state', 'open'],
  ]);
  for (const [character, escaped] of [['#', '%23'], ['&', '%26']]) {
    if (!targetRef.includes(character)) continue;
    assert.ok(refUrl.pathname.includes(escaped));
    assert.ok(!refUrl.pathname.includes(character));
    assert.ok(pullsUrl.search.includes(escaped));
  }
}

describe('transport assertions are independent of optional URI escaping', () => {
  const targetRef = "feature/#frag&state=closed!$({IFS})'";
  const callsWith = (encode) => [
    `api repos/${REPO}/git/ref/heads/${targetRef.split('/').map(encode).join('/')} --jq .object.sha`,
    `api repos/${REPO}/pulls?head=${encode(`${OWNER}:${targetRef}`)}&state=open --jq .[] | [.number, .head.sha] | @tsv`,
  ];

  it('accepts both literal and escaped parentheses/quotes while preserving the same data', () => {
    assertRequestTransport(callsWith(encodeURIComponent), targetRef);
    const strictEncode = (value) => encodeURIComponent(value).replace(
      /[!'()*]/g, (character) => `%${character.charCodeAt(0).toString(16).toUpperCase()}`
    );
    assertRequestTransport(callsWith(strictEncode), targetRef);
  });

  it('rejects raw delimiters that change the path or query', () => {
    const calls = callsWith(encodeURIComponent);
    for (const index of [0, 1]) {
      for (const [escaped, raw] of [['%23', '#'], ['%26', '&']]) {
        const broken = [...calls];
        broken[index] = broken[index].replaceAll(escaped, raw);
        assert.throws(() => assertRequestTransport(broken, targetRef));
      }
    }
  });
});

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
      actualSha: OTHER_SHA,
    });
    assert.match(result.stdout, /open pull request #89 resolving to/);
    assert.equal(result.output.trim(), `sha=${HEAD_SHA}\npr-number=89`);
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

  it('refuses a commit that is not the head of any open pull request', () => {
    const sandbox = createSandbox({ refSha: HEAD_SHA, openPulls: [{ number: 89, sha: OTHER_SHA }] });
    const { stderr } = runAssertionExpectingFailure(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
    });
    assert.match(stderr, /not the head of any open pull request/);
  });

  it('refuses a commit that is the head of a closed pull request', () => {
    const sandbox = createSandbox({ refSha: HEAD_SHA, openPulls: [] });
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
    const sandbox = createSandbox({
      refSha: HEAD_SHA,
      openPulls: [
        { number: 90, sha: OTHER_SHA },
        { number: 91, sha: HEAD_SHA },
        { number: 89, sha: HEAD_SHA },
      ],
    });
    const result = runAssertion(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
    });
    assert.equal(result.output.trim(), `sha=${HEAD_SHA}\npr-number=89`);
  });

  it('refuses Git-forbidden branch names before making any API call', () => {
    const invalid = [
      'feature?x=1',
      'feature space',
      'feature\ttab',
      'feature\nnewline',
      'feature\x7f',
      'fea..ture',
      '/leading',
      'trailing/',
      'double//slash',
      'trailing.',
      '.hidden',
      'feature/.hidden',
      'feature.lock',
      'feature.lock/child',
      'feature@{1}',
      '@{-1}',
      '-option',
      'feature:bad',
      'feature*',
      'feature[bad',
      'feature\\bad',
      'feature~1',
      'feature^',
    ];
    for (const targetRef of invalid) {
      const sandbox = createSandbox();
      const { stderr } = runAssertionExpectingFailure(sandbox, {
        expectedSha: HEAD_SHA,
        targetRef,
      });
      assert.match(stderr, /Invalid dispatch target ref/, targetRef);
      assert.equal(readFileSync(join(sandbox.dir, 'github-output'), 'utf8'), '');
      assert.equal(existsSync(sandbox.log), false, 'invalid names never reach the API');
    }
  });

  it('encodes legal reserved characters as data in both API requests', () => {
    const legal = [
      'feature/#frag&state=closed',
      'feature/%23literal',
      'feature+plus=equals',
      'feature$({IFS})',
      'feature;rm',
      "feature/'quoted'",
      'feature/日本語',
    ];
    for (const targetRef of legal) {
      const sandbox = createSandbox();
      const result = runAssertion(sandbox, { expectedSha: HEAD_SHA, targetRef });
      assert.equal(result.output.trim(), `sha=${HEAD_SHA}\npr-number=89`, targetRef);
      const calls = readFileSync(sandbox.log, 'utf8').trim().split('\n');
      assertRequestTransport(calls, targetRef);
    }
  });

  it('accepts the branch-name characters that are legal', () => {
    for (const targetRef of ['main', 'arena/01a0e487-appt', 'feature_x.y-1', 'release/1.2']) {
      const sandbox = createSandbox();
      const result = runAssertion(sandbox, { expectedSha: HEAD_SHA, targetRef });
      assert.equal(result.output.trim(), `sha=${HEAD_SHA}\npr-number=89`, targetRef);
    }
  });

  it('publishes the proven commit, so callers check that out rather than the input', () => {
    const sandbox = createSandbox();
    const result = runAssertion(sandbox, {
      expectedSha: HEAD_SHA,
      targetRef: HEAD_REF,
      actualSha: OTHER_SHA,
    });
    assert.equal(result.output.trim(), `sha=${HEAD_SHA}\npr-number=89`);
  });
});
