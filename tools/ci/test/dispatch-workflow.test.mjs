/**
 * Tests for the AppT trusted agent-control dispatch bridge (Issue #88).
 *
 * These tests are the safe way to prove the bridge: they drive the real control
 * flow against a fake `gh` API, so the label allowlist, the provider-valid
 * dispatch request shape and the fork refusal are all exercised without
 * dispatching a single real workflow run.
 *
 * The centrepiece is the dispatch-ref contract. GitHub's Create Workflow
 * Dispatch endpoint takes `ref` as a **branch or tag name**, not a commit SHA.
 * The Issue #88 contract review found a candidate that passed a SHA, which the
 * provider rejects. These tests make that shape a test failure, so it cannot
 * come back.
 */

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  COMMAND_LABELS,
  COMMAND_LABEL_NAMES,
  buildDispatchBody,
  dispatchCommand,
  resolveDispatch,
  resolvePullRequestHead,
} from '../dispatch-workflow.mjs';

const REPO = 'anthracite-labs/AppT';
const PR_NUMBER = 89;
const HEAD_SHA = '10525a115869dcf3e9d49cf2053c03d284200249';
const HEAD_REF = 'arena/01a0e487-appt';

/** A fake `gh api` implementation that records every call, including stdin. */
function createFakeApi({
  pr = { head: { sha: HEAD_SHA, ref: HEAD_REF, repo: { full_name: REPO } } },
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

describe('buildDispatchBody', () => {
  it('uses the branch name as ref and always carries expected_sha', () => {
    const body = buildDispatchBody({ ref: HEAD_REF, mode: 'app-unit', expectedSha: HEAD_SHA });
    assert.equal(body.ref, HEAD_REF);
    assert.equal(body.inputs.expected_sha, HEAD_SHA);
    assert.equal(body.inputs.mode, 'app-unit');
    assert.equal(body.return_run_details, true);
  });

  it('omits mode for a verify dispatch but keeps expected_sha', () => {
    const body = buildDispatchBody({ ref: HEAD_REF, mode: '', expectedSha: HEAD_SHA });
    assert.equal(body.ref, HEAD_REF);
    assert.equal(body.inputs.expected_sha, HEAD_SHA);
    assert.equal('mode' in body.inputs, false);
  });

  // The contract the review corrected: `ref` is a branch or tag name, never a
  // commit SHA. A SHA used directly as `workflow_dispatch.ref` must fail here.
  it('refuses a commit SHA as the dispatch ref', () => {
    for (const sha of [HEAD_SHA, HEAD_SHA.toLowerCase(), 'a'.repeat(40)]) {
      assert.throws(
        () => buildDispatchBody({ ref: sha, mode: 'app-unit', expectedSha: HEAD_SHA }),
        /must be a branch or tag name, not a commit SHA/,
        sha
      );
    }
  });

  it('refuses an empty or non-SHA expected_sha', () => {
    for (const expectedSha of ['', undefined, null, 'not-a-sha', HEAD_SHA.slice(0, 39)]) {
      assert.throws(
        () => buildDispatchBody({ ref: HEAD_REF, mode: 'app-unit', expectedSha }),
        /expectedSha/,
        String(expectedSha)
      );
    }
  });

  it('refuses an empty ref', () => {
    assert.throws(
      () => buildDispatchBody({ ref: '', mode: 'app-unit', expectedSha: HEAD_SHA }),
      /ref is required/
    );
  });

  it('produces a JSON-serialisable body for every allowlisted label', () => {
    for (const label of COMMAND_LABEL_NAMES) {
      const { mode } = resolveDispatch(label);
      const body = buildDispatchBody({ ref: HEAD_REF, mode, expectedSha: HEAD_SHA });
      const round = JSON.parse(JSON.stringify(body));
      assert.equal(round.ref, HEAD_REF, label);
      assert.equal(round.inputs.expected_sha, HEAD_SHA, label);
    }
  });
});

describe('resolvePullRequestHead', () => {
  it('resolves the exact head SHA and branch name', async () => {
    const api = createFakeApi();
    const head = await resolvePullRequestHead({ gh: api.gh, repo: REPO, prNumber: PR_NUMBER });
    assert.equal(head.sha, HEAD_SHA);
    assert.equal(head.ref, HEAD_REF);
    assert.equal(head.headRepo, REPO);
  });

  it('refuses an unusable head SHA', async () => {
    for (const sha of ['', undefined, 'short', HEAD_SHA.toUpperCase()]) {
      const api = createFakeApi({ pr: { head: { sha, ref: HEAD_REF, repo: { full_name: REPO } } } });
      await assert.rejects(
        resolvePullRequestHead({ gh: api.gh, repo: REPO, prNumber: PR_NUMBER }),
        /could not resolve a pull-request head SHA/,
        String(sha)
      );
    }
  });

  it('refuses a missing head branch', async () => {
    const api = createFakeApi({ pr: { head: { sha: HEAD_SHA, ref: '', repo: { full_name: REPO } } } });
    await assert.rejects(
      resolvePullRequestHead({ gh: api.gh, repo: REPO, prNumber: PR_NUMBER }),
      /could not resolve a pull-request head branch/
    );
  });
});

describe('dispatchCommand', () => {
  it('dispatches every allowlisted label against the branch ref and expected SHA', async () => {
    for (const label of COMMAND_LABEL_NAMES) {
      const api = createFakeApi();
      const result = await dispatchCommand({
        label,
        gh: api.gh,
        repo: REPO,
        prNumber: PR_NUMBER,
        log: () => {},
      });

      assert.equal(result.ref, HEAD_REF, `${label} ref`);
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
        HEAD_REF,
        `${label}: workflow_dispatch.ref must be the branch name, not a commit SHA`
      );
      assert.notEqual(
        body.ref,
        HEAD_SHA,
        `${label}: workflow_dispatch.ref must never be the resolved commit SHA`
      );
      assert.equal(body.inputs.expected_sha, HEAD_SHA, `${label}: expected_sha is the head SHA`);
      assert.equal(body.return_run_details, true, `${label}: return_run_details`);

      const mode = resolveDispatch(label).mode;
      if (mode) assert.equal(body.inputs.mode, mode, `${label}: mode`);
      else assert.equal('mode' in body.inputs, false, `${label}: no mode`);
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
      pr: {
        head: {
          sha: HEAD_SHA,
          ref: HEAD_REF,
          repo: { full_name: 'some-fork/AppT' },
        },
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
      /cross-repository \(fork\) head cannot be dispatched/
    );

    // No dispatch was attempted.
    assert.ok(!api.calls.some((call) => call.includes('/dispatches')));
    assert.deepEqual(api.bodies, []);
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
