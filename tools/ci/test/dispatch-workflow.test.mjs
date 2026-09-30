/**
 * Tests for the AppT trusted agent-control dispatch bridge (Issue #88).
 *
 * These tests are the safe way to prove the bridge: they drive the real control
 * flow against a fake `gh` API, so the label allowlist, the provider-valid
 * dispatch request shape and the fork refusal are all exercised without
 * dispatching a single real workflow run.
 *
 * The centrepiece is the dispatch-ref contract, which has two halves.
 *
 * GitHub's Create Workflow Dispatch endpoint takes `ref` as *the git reference
 * for the workflow* — a **branch or tag name**, never a commit SHA. The first
 * review round found a candidate that passed a SHA, which the provider rejects.
 *
 * The second round found the deeper defect: the bridge dispatched the pull
 * request's own mutable head branch, so the provider ran the workflow *as that
 * branch defined it* — including the local composite action that asserts the
 * dispatch target. A pull request could therefore rewrite its own assertion and
 * pass it. The fix is that the dispatch ref is the repository's default branch
 * (trusted content) and the requested target travels as the `target_ref` and
 * `expected_sha` inputs.
 *
 * The tests below make the earlier shape a test failure, so it cannot come back:
 * `targetRef` is required, `ref` may never equal it, and no dispatch body this
 * bridge can produce names the pull-request branch as its ref.
 */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  COMMAND_LABELS,
  COMMAND_LABEL_NAMES,
  buildDispatchBody,
  dispatchCommand,
  resolveDefaultBranch,
  resolveDispatch,
  resolvePullRequestFocus,
  resolvePullRequestHead,
} from '../dispatch-workflow.mjs';

const REPO = 'anthracite-labs/AppT';
const PR_NUMBER = 89;
const HEAD_SHA = '10525a115869dcf3e9d49cf2053c03d284200249';
const HEAD_REF = 'arena/01a0e487-appt';
/** The trusted anchor a dispatch actually runs from. */
const DEFAULT_BRANCH = 'main';

const WORKFLOW_DIR = fileURLToPath(new URL('../../../.github/workflows', import.meta.url));

/** Read a workflow file as text, for repository-level contract assertions. */
function readWorkflow(name) {
  return readFileSync(`${WORKFLOW_DIR}/${name}`, 'utf8');
}

/** A fake `gh api` implementation that records every call, including stdin. */
function createFakeApi({
  pr = { head: { sha: HEAD_SHA, ref: HEAD_REF, repo: { full_name: REPO } }, body: '' },
  defaultBranch = DEFAULT_BRANCH,
  dispatchResponse = `https://github.com/${REPO}/actions/runs/1`,
} = {}) {
  const calls = [];
  const bodies = [];

  const gh = async (args, stdin = '') => {
    calls.push(args.join(' '));
    if (stdin) bodies.push(stdin);

    if (args[0] === '--method' && args[1] === 'POST') {
      const path = args[2];
      if (/\/actions\/workflows\/[^/]+\/dispatches$/.test(path)) {
        return dispatchResponse;
      }
      throw new Error(`unexpected mutation: ${args.join(' ')}`);
    }

    const pulls = args[0].match(/^repos\/[^/]+\/[^/]+\/pulls\/(\d+)$/);
    if (pulls) {
      if (Number(pulls[1]) !== PR_NUMBER) {
        throw new Error(`unexpected pull request: ${args[0]}`);
      }
      return pr;
    }

    // The repository record, which is where the trusted anchor comes from.
    if (/^repos\/[^/]+\/[^/]+$/.test(args[0])) {
      return { full_name: REPO, default_branch: defaultBranch };
    }

    throw new Error(`unexpected call: ${args.join(' ')}`);
  };

  return { gh, calls, bodies };
}

describe('resolveDispatch', () => {
  it('maps every allowlisted label to a workflow and mode', () => {
    for (const label of COMMAND_LABEL_NAMES) {
      const { workflow, mode } = resolveDispatch(label);
      assert.ok(workflow.endsWith('.yml'), `${label} -> ${workflow}`);
      assert.equal(typeof mode, 'string', `${label} mode`);
    }
  });

  it('dispatches verify with no mode, and every other label to diagnose', () => {
    assert.deepEqual(resolveDispatch('ci:full'), { workflow: 'verify.yml', mode: '' });
    for (const label of COMMAND_LABEL_NAMES.filter((name) => name !== 'ci:full')) {
      const { workflow, mode } = resolveDispatch(label);
      assert.equal(workflow, 'diagnose.yml', label);
      assert.ok(mode.length > 0, `${label} must name a mode`);
    }
  });

  it('refuses any label outside the allowlist without dispatching', async () => {
    const api = createFakeApi();
    for (const label of ['ci:bogus', 'bug', 'ci:full; rm -rf /', '', 'CI:FULL']) {
      assert.throws(() => resolveDispatch(label), /unsupported command label/, label);
      await assert.rejects(
        dispatchCommand({ label, gh: api.gh, repo: REPO, prNumber: PR_NUMBER, log: () => {} }),
        /unsupported command label/,
        label
      );
    }
    assert.deepEqual(api.calls, [], 'a refused label must not call the API at all');
  });
});



describe('resolvePullRequestFocus', () => {
  it('returns the exact focus bound to the requested mode', () => {
    const body = [
      '<!-- appt-ci-focus samsung-unit: SamsungStoreTest -->',
      '<!-- appt-ci-focus app-unit: PairingToFirstControlFlowTest -->',
    ].join('\n');
    assert.equal(
      resolvePullRequestFocus({ body, mode: 'app-unit' }),
      'PairingToFirstControlFlowTest'
    );
  });

  it('ignores markers for another mode and preserves ordinary full-mode behavior', () => {
    const body = '<!-- appt-ci-focus samsung-unit: SamsungStoreTest -->';
    assert.equal(resolvePullRequestFocus({ body, mode: 'app-unit' }), '');
    assert.equal(resolvePullRequestFocus({ body, mode: '' }), '');
  });

  it('fails closed on duplicate markers for the same mode', () => {
    const body = [
      '<!-- appt-ci-focus app-unit: FirstTest -->',
      '<!-- appt-ci-focus app-unit: SecondTest -->',
    ].join('\n');
    assert.throws(
      () => resolvePullRequestFocus({ body, mode: 'app-unit' }),
      /multiple appt-ci-focus markers/
    );
  });

  it('does not interpret malformed marker text', () => {
    for (const body of [
      'appt-ci-focus app-unit: FooTest',
      '<!-- appt-ci-focus app-unit FooTest -->',
      ' <!-- appt-ci-focus app-unit: FooTest -->',
    ]) {
      assert.equal(resolvePullRequestFocus({ body, mode: 'app-unit' }), '');
    }
  });
});

describe('buildDispatchBody', () => {
  it('dispatches from the trusted anchor and carries the target as inputs', () => {
    const body = buildDispatchBody({
      ref: DEFAULT_BRANCH,
      targetRef: HEAD_REF,
      mode: 'app-unit',
      expectedSha: HEAD_SHA,
    });
    assert.equal(body.ref, DEFAULT_BRANCH);
    assert.equal(body.inputs.target_ref, HEAD_REF);
    assert.equal(body.inputs.expected_sha, HEAD_SHA);
    assert.equal(body.inputs.mode, 'app-unit');
    assert.equal('focus' in body.inputs, false);
    assert.equal(body.return_run_details, true);
  });


  it('carries a diagnose focus as data and refuses focus on verify', () => {
    const body = buildDispatchBody({
      ref: DEFAULT_BRANCH,
      targetRef: HEAD_REF,
      mode: 'app-unit',
      focus: 'PairingToFirstControlFlowTest',
      expectedSha: HEAD_SHA,
    });
    assert.equal(body.inputs.focus, 'PairingToFirstControlFlowTest');

    assert.throws(
      () =>
        buildDispatchBody({
          ref: DEFAULT_BRANCH,
          targetRef: HEAD_REF,
          mode: '',
          focus: 'PairingToFirstControlFlowTest',
          expectedSha: HEAD_SHA,
        }),
      /focus is only valid for a diagnose mode/
    );
  });

  it('omits mode for a verify dispatch but keeps the target inputs', () => {
    const body = buildDispatchBody({
      ref: DEFAULT_BRANCH,
      targetRef: HEAD_REF,
      mode: '',
      expectedSha: HEAD_SHA,
    });
    assert.equal(body.ref, DEFAULT_BRANCH);
    assert.equal(body.inputs.target_ref, HEAD_REF);
    assert.equal(body.inputs.expected_sha, HEAD_SHA);
    assert.equal('mode' in body.inputs, false);
  });

  // The contract the first review round corrected: `ref` is a branch or tag
  // name, never a commit SHA. A SHA used directly as `workflow_dispatch.ref`
  // must fail here.
  it('refuses a commit SHA as the dispatch ref', () => {
    for (const sha of [HEAD_SHA, HEAD_SHA.toLowerCase(), 'a'.repeat(40)]) {
      assert.throws(
        () =>
          buildDispatchBody({
            ref: sha,
            targetRef: HEAD_REF,
            mode: 'app-unit',
            expectedSha: HEAD_SHA,
          }),
        /must be a branch or tag name, not a commit SHA/,
        sha
      );
    }
  });

  it('refuses an empty or non-SHA expected_sha', () => {
    for (const expectedSha of ['', undefined, null, 'not-a-sha', HEAD_SHA.slice(0, 39)]) {
      assert.throws(
        () =>
          buildDispatchBody({
            ref: DEFAULT_BRANCH,
            targetRef: HEAD_REF,
            mode: 'app-unit',
            expectedSha,
          }),
        /expectedSha/,
        String(expectedSha)
      );
    }
  });

  it('refuses an empty ref', () => {
    assert.throws(
      () => buildDispatchBody({ ref: '', targetRef: HEAD_REF, mode: 'app-unit', expectedSha: HEAD_SHA }),
      /ref is required/
    );
  });

  it('produces a JSON-serialisable body for every allowlisted label', () => {
    for (const label of COMMAND_LABEL_NAMES) {
      const { mode } = resolveDispatch(label);
      const body = buildDispatchBody({
        ref: DEFAULT_BRANCH,
        targetRef: HEAD_REF,
        mode,
        expectedSha: HEAD_SHA,
      });
      const round = JSON.parse(JSON.stringify(body));
      assert.equal(round.ref, DEFAULT_BRANCH, label);
      assert.equal(round.inputs.target_ref, HEAD_REF, label);
      assert.equal(round.inputs.expected_sha, HEAD_SHA, label);
    }
  });
});

describe('resolveDefaultBranch', () => {
  it('reads the trusted anchor from the provider', async () => {
    const api = createFakeApi();
    assert.equal(await resolveDefaultBranch({ gh: api.gh, repo: REPO }), DEFAULT_BRANCH);
  });

  it('refuses a repository record with no usable default branch', async () => {
    for (const defaultBranch of ['', null, 42, {}]) {
      const api = createFakeApi({ defaultBranch });
      await assert.rejects(
        resolveDefaultBranch({ gh: api.gh, repo: REPO }),
        /could not resolve the default branch/,
        String(defaultBranch)
      );
    }
    // An absent key is the same failure, and is not silently defaulted.
    await assert.rejects(
      resolveDefaultBranch({ gh: async () => ({ full_name: REPO }), repo: REPO }),
      /could not resolve the default branch/,
      'absent'
    );
  });
});

describe('resolvePullRequestHead', () => {
  it('resolves the exact head SHA and branch name', async () => {
    const api = createFakeApi();
    const head = await resolvePullRequestHead({ gh: api.gh, repo: REPO, prNumber: PR_NUMBER });
    assert.equal(head.sha, HEAD_SHA);
    assert.equal(head.ref, HEAD_REF);
    assert.equal(head.headRepo, REPO);
    assert.equal(head.body, '');
  });

  it('refuses an unusable head SHA', async () => {
    for (const sha of ['', undefined, 'short', HEAD_SHA.toUpperCase()]) {
      const api = createFakeApi({
        pr: { head: { sha, ref: HEAD_REF, repo: { full_name: REPO } } },
      });
      await assert.rejects(
        resolvePullRequestHead({ gh: api.gh, repo: REPO, prNumber: PR_NUMBER }),
        /could not resolve a pull-request head SHA/,
        String(sha)
      );
    }
  });

  it('refuses a missing head branch', async () => {
    const api = createFakeApi({
      pr: { head: { sha: HEAD_SHA, ref: '', repo: { full_name: REPO } } },
    });
    await assert.rejects(
      resolvePullRequestHead({ gh: api.gh, repo: REPO, prNumber: PR_NUMBER }),
      /could not resolve a pull-request head branch/
    );
  });
});

describe('dispatchCommand', () => {

  it('transports a valid mode-bound focus from PR metadata', async () => {
    const api = createFakeApi({
      pr: {
        head: { sha: HEAD_SHA, ref: HEAD_REF, repo: { full_name: REPO } },
        body: '<!-- appt-ci-focus app-unit: PairingToFirstControlFlowTest -->',
      },
    });
    const result = await dispatchCommand({
      label: 'ci:app-unit',
      gh: api.gh,
      repo: REPO,
      prNumber: PR_NUMBER,
      log: () => {},
    });

    assert.equal(result.focus, 'PairingToFirstControlFlowTest');
    const body = JSON.parse(api.bodies[0]);
    assert.equal(body.inputs.mode, 'app-unit');
    assert.equal(body.inputs.focus, 'PairingToFirstControlFlowTest');
  });

  it('ignores a focus marker for another mode', async () => {
    const api = createFakeApi({
      pr: {
        head: { sha: HEAD_SHA, ref: HEAD_REF, repo: { full_name: REPO } },
        body: '<!-- appt-ci-focus samsung-unit: SamsungStoreTest -->',
      },
    });
    const result = await dispatchCommand({
      label: 'ci:app-unit',
      gh: api.gh,
      repo: REPO,
      prNumber: PR_NUMBER,
      log: () => {},
    });

    assert.equal(result.focus, '');
    const body = JSON.parse(api.bodies[0]);
    assert.equal('focus' in body.inputs, false);
  });

  it('rejects invalid focus grammar before dispatch', async () => {
    const api = createFakeApi({
      pr: {
        head: { sha: HEAD_SHA, ref: HEAD_REF, repo: { full_name: REPO } },
        body: '<!-- appt-ci-focus app-unit: --not-a-test -->',
      },
    });

    await assert.rejects(
      dispatchCommand({
        label: 'ci:app-unit',
        gh: api.gh,
        repo: REPO,
        prNumber: PR_NUMBER,
        log: () => {},
      }),
      /focus must not start with/
    );
    assert.ok(!api.calls.some((call) => call.includes('/dispatches')));
  });

  it('never forwards focus through ci:full', async () => {
    const api = createFakeApi({
      pr: {
        head: { sha: HEAD_SHA, ref: HEAD_REF, repo: { full_name: REPO } },
        body: '<!-- appt-ci-focus app-unit: PairingToFirstControlFlowTest -->',
      },
    });
    const result = await dispatchCommand({
      label: 'ci:full',
      gh: api.gh,
      repo: REPO,
      prNumber: PR_NUMBER,
      log: () => {},
    });

    assert.equal(result.focus, '');
    const body = JSON.parse(api.bodies[0]);
    assert.equal('mode' in body.inputs, false);
    assert.equal('focus' in body.inputs, false);
  });

  it('dispatches every allowlisted label from the trusted anchor', async () => {
    for (const label of COMMAND_LABEL_NAMES) {
      const api = createFakeApi();
      const result = await dispatchCommand({
        label,
        gh: api.gh,
        repo: REPO,
        prNumber: PR_NUMBER,
        log: () => {},
      });

      // The trusted anchor is the dispatch ref; the target travels as inputs.
      assert.equal(result.ref, DEFAULT_BRANCH, `${label} ref`);
      assert.equal(result.targetRef, HEAD_REF, `${label} targetRef`);
      assert.equal(result.expectedSha, HEAD_SHA, `${label} expectedSha`);
      assert.equal(result.workflow, resolveDispatch(label).workflow, `${label} workflow`);
      assert.equal(result.runUrl, `https://github.com/${REPO}/actions/runs/1`, `${label} url`);

      // Exactly one dispatch call, carrying exactly one body.
      const dispatches = api.calls.filter((call) => call.includes('/dispatches'));
      assert.equal(dispatches.length, 1, `${label} dispatched once`);
      assert.equal(api.bodies.length, 1, `${label} sent one body`);

      const body = JSON.parse(api.bodies[0]);
      assert.equal(
        body.ref,
        DEFAULT_BRANCH,
        `${label}: workflow_dispatch.ref must be the trusted default branch`
      );
      assert.equal(body.inputs.target_ref, HEAD_REF, `${label}: target_ref is the head branch`);
      assert.equal(body.inputs.expected_sha, HEAD_SHA, `${label}: expected_sha is the head SHA`);
      assert.equal(body.return_run_details, true, `${label}: return_run_details`);

      const mode = resolveDispatch(label).mode;
      if (mode) assert.equal(body.inputs.mode, mode, `${label}: mode`);
      else assert.equal('mode' in body.inputs, false, `${label}: no mode`);
      assert.equal('focus' in body.inputs, false, `${label}: no focus without marker`);
    }
  });

  it('never dispatches a ref that looks like a commit SHA, for any label', async () => {
    // Belt and braces over the per-label assertion above: scan every dispatch
    // body this bridge can produce and reject any 40-hex `ref`.
    for (const label of COMMAND_LABEL_NAMES) {
      const api = createFakeApi();
      await dispatchCommand({ label, gh: api.gh, repo: REPO, prNumber: PR_NUMBER, log: () => {} });
      const body = JSON.parse(api.bodies[0]);
      assert.ok(
        !/^[0-9a-f]{40}$/.test(body.ref),
        `${label} dispatched a commit SHA as ref: ${body.ref}`
      );
    }
  });

  it('refuses a fork pull request instead of dispatching it', async () => {
    const api = createFakeApi({
      pr: { head: { sha: HEAD_SHA, ref: HEAD_REF, repo: { full_name: 'some-fork/AppT' } } },
    });

    await assert.rejects(
      dispatchCommand({
        label: 'ci:app-unit',
        gh: api.gh,
        repo: REPO,
        prNumber: PR_NUMBER,
        log: () => {},
      }),
      /cross-repository \(fork\) head cannot be dispatched/
    );

    // No dispatch was attempted.
    assert.ok(!api.calls.some((call) => call.includes('/dispatches')));
    assert.deepEqual(api.bodies, []);
  });

  for (const headRepo of [null, undefined, {}, { full_name: '' }]) {
    it(`refuses an unavailable head repository (${JSON.stringify(headRepo)}) before dispatch`, async () => {
      const api = createFakeApi({
        pr: { head: { sha: HEAD_SHA, ref: HEAD_REF, repo: headRepo } },
      });
      await assert.rejects(
        dispatchCommand({
          label: 'ci:app-unit',
          gh: api.gh,
          repo: REPO,
          prNumber: PR_NUMBER,
          log: () => {},
        }),
        /heads from <unavailable repository>/
      );
      assert.ok(!api.calls.some((call) => call.includes('/dispatches')));
      assert.deepEqual(api.bodies, []);
      assert.equal(api.calls.length, 1, 'only the PR lookup is allowed');
    });
  }

  it('refuses a pull request that heads from the default branch itself', async () => {
    // There would be no trusted anchor distinct from the target, so the bridge
    // fails closed rather than dispatching the branch it is meant to verify.
    const api = createFakeApi({
      pr: { head: { sha: HEAD_SHA, ref: DEFAULT_BRANCH, repo: { full_name: REPO } } },
    });

    await assert.rejects(
      dispatchCommand({
        label: 'ci:app-unit',
        gh: api.gh,
        repo: REPO,
        prNumber: PR_NUMBER,
        log: () => {},
      }),
      /no trusted anchor distinct from the target/
    );
    assert.ok(!api.calls.some((call) => call.includes('/dispatches')));
  });

  it('keeps working when the provider reports no run details', async () => {
    const api = createFakeApi({ dispatchResponse: '' });
    const result = await dispatchCommand({
      label: 'ci:device',
      gh: api.gh,
      repo: REPO,
      prNumber: PR_NUMBER,
      log: () => {},
    });
    // A dispatch that is accepted but not yet listed is still a success.
    assert.equal(result.runUrl, '');
    assert.equal(result.expectedSha, HEAD_SHA);
    assert.equal(result.ref, DEFAULT_BRANCH);
  });

  it('exposes an allowlist that matches the documented command labels', () => {
    assert.deepEqual([...COMMAND_LABEL_NAMES].sort(), [
      'ci:android-build',
      'ci:android-static',
      'ci:app-unit',
      'ci:backend',
      'ci:backend-static',
      'ci:backend-test',
      'ci:device',
      'ci:full',
      'ci:samsung-unit',
    ]);
    for (const label of COMMAND_LABEL_NAMES) {
      assert.equal(label.startsWith('ci:'), true, label);
    }
  });
});

/**
 * The earlier design cannot satisfy the contract. These tests exist so that a
 * regression to "dispatch the pull request's mutable head branch and load the
 * assertion from that same branch" is a test failure rather than a silent
 * reintroduction of the defect the review found.
 */
describe('the earlier mutable-ref design cannot satisfy the contract', () => {
  it('refuses a body with no target ref — the old shape', () => {
    // The old design had no `targetRef` at all: `ref` was the pull request's
    // branch and `expected_sha` was the only thing carried alongside it.
    for (const targetRef of [undefined, null, '']) {
      assert.throws(
        () => buildDispatchBody({ ref: HEAD_REF, mode: 'app-unit', expectedSha: HEAD_SHA, targetRef }),
        /targetRef is required/,
        String(targetRef)
      );
    }
  });

  it('refuses a dispatch ref that is the target being verified', () => {
    assert.throws(
      () =>
        buildDispatchBody({
          ref: HEAD_REF,
          targetRef: HEAD_REF,
          mode: 'app-unit',
          expectedSha: HEAD_SHA,
        }),
        /must be\s+the trusted default branch, never the pull-request branch/s,
      'dispatching the pull-request branch'
    );
  });

  it('refuses a commit SHA as the target ref too', () => {
    assert.throws(
      () =>
        buildDispatchBody({
          ref: DEFAULT_BRANCH,
          targetRef: HEAD_SHA,
          mode: 'app-unit',
          expectedSha: HEAD_SHA,
        }),
      /targetRef must be a branch or tag name/
    );
  });

  it('no dispatch this bridge can produce names the pull-request branch as ref', async () => {
    // The end-to-end statement of the rule: whatever the label, whatever the
    // provider says, the dispatch ref is the trusted anchor and never the
    // mutable branch the command was issued against.
    for (const label of COMMAND_LABEL_NAMES) {
      for (const defaultBranch of [DEFAULT_BRANCH, 'develop', 'trunk']) {
        const api = createFakeApi({ defaultBranch });
        const result = await dispatchCommand({
          label,
          gh: api.gh,
          repo: REPO,
          prNumber: PR_NUMBER,
          log: () => {},
        });
        const body = JSON.parse(api.bodies[0]);
        assert.equal(body.ref, defaultBranch, `${label} / ${defaultBranch}`);
        assert.notEqual(body.ref, HEAD_REF, `${label} / ${defaultBranch}`);
        assert.equal(result.ref, defaultBranch);
        assert.equal(result.targetRef, HEAD_REF);
      }
    }
  });

  it('the repository workflows validate the target from trusted content', () => {
    // The dispatch-side half of the contract is enforced in the workflow files:
    // every use of the assertion action passes the target ref, and no checkout
    // ever checks out the mutable branch — only the validated exact SHA.
    for (const name of ['verify.yml', 'diagnose.yml']) {
      const workflow = readWorkflow(name);

      // Both the `./` and the `$/` spellings of a repository-local action are
      // accepted, so the count does not depend on which one the file uses.
      const assertions =
        workflow.match(/uses: [.$]\/\.github\/actions\/assert-dispatch-target/g) ?? [];
      assert.ok(assertions.length > 0, `${name} must use the assertion action`);
      assert.equal(
        assertions.length,
        (workflow.match(/target-ref: \$\{\{ inputs\.target_ref \}\}/g) ?? []).length,
        `${name}: every dispatch-target assertion must receive the target ref`
      );

      // Matched as whole lines, so the `target-ref:` assertion input is not
      // mistaken for a checkout ref.
      const checkoutRefs = workflow
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.startsWith('ref: '));

      // Never the mutable branch, and never a raw dispatch input: a
      // `workflow_dispatch` input is attacker-reachable, so the commit that gets
      // built has to be one trusted logic has proven.
      assert.ok(
        !checkoutRefs.includes('ref: ${{ inputs.target_ref }}'),
        `${name} must never check out the mutable target branch`
      );
      assert.ok(
        !checkoutRefs.some((ref) => ref.startsWith('ref: ${{ inputs.')),
        `${name} must not check out a raw workflow_dispatch input: ${checkoutRefs.join(', ')}`
      );
      assert.ok(
        checkoutRefs.includes('ref: ${{ steps.assert-target.outputs.sha }}'),
        `${name} must check out the commit the assertion proved`
      );

      // Every checkout of the target is guarded by the assertion having run, and
      // the assertion itself only runs for a dispatch.
      const assertionSteps = workflow
        .split('\n')
        .filter((line) => line.includes('id: assert-target'));
      assert.equal(
        assertionSteps.length,
        checkoutRefs.filter((ref) => ref.includes('assert-target')).length,
        `${name}: every validated checkout has an assertion to produce it`
      );
    }
  });
});
