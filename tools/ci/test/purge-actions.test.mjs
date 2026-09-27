/**
 * Tests for the AppT Actions maintenance purge (Issue #88).
 *
 * These tests are the safe way to prove the destructive sequence in
 * `maintenance.yml`: they drive the real control flow against a fake `gh` API,
 * so the ordering, the "never touch the current run" rule and the end-state
 * verification are all exercised without deleting a single real workflow run or
 * cache.
 */

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  ACTIVE_RUN_STATUSES,
  evaluatePurgeState,
  listCaches,
  listRuns,
  partitionRuns,
  purgeActions,
} from '../purge-actions.mjs';

const CURRENT_RUN_ID = 900;

function run(id, status, name = 'verify') {
  return { id, status, name, head_sha: `sha-${id}` };
}

/**
 * A fake `gh api` implementation with mutable repository state, so the purge
 * code under test sees the consequences of its own calls.
 */
function createFakeApi({ runs = [], caches = [], cancelStopsRuns = true, lateRun = null } = {}) {
  const state = {
    runs: runs.map((entry) => ({ ...entry })),
    caches: caches.map((entry) => ({ ...entry })),
  };
  const calls = [];

  const gh = async (args) => {
    calls.push(args.join(' '));

    if (args[0] === '--method') {
      const [, method, path] = args;

      const cancel = path.match(/\/actions\/runs\/(\d+)\/cancel$/);
      if (method === 'POST' && cancel) {
        const id = Number(cancel[1]);
        const target = state.runs.find((entry) => entry.id === id);
        // A well-behaved run stops when cancelled. `cancelStopsRuns: false`
        // models the stuck run the settle timeout exists for.
        if (target && cancelStopsRuns) target.status = 'cancelled';
        return null;
      }

      if (method === 'DELETE' && /\/actions\/caches$/.test(path)) {
        // Optionally models a run that was queued while the purge was waiting.
        state.caches = [];
        if (lateRun) state.runs.push({ ...lateRun });
        return null;
      }

      const deletion = path.match(/\/actions\/runs\/(\d+)$/);
      if (method === 'DELETE' && deletion) {
        const id = Number(deletion[1]);
        state.runs = state.runs.filter((entry) => entry.id !== id);
        return null;
      }

      throw new Error(`unexpected mutation: ${args.join(' ')}`);
    }

    if (/\/actions\/runs\?/.test(args[0])) {
      return { total_count: state.runs.length, workflow_runs: state.runs };
    }
    if (/\/actions\/caches\?/.test(args[0])) {
      return { total_count: state.caches.length, actions_caches: state.caches };
    }

    throw new Error(`unexpected call: ${args.join(' ')}`);
  };

  return { gh, calls, state };
}

describe('partitionRuns', () => {
  it('excludes the run executing the purge', () => {
    const { other } = partitionRuns(
      [run(CURRENT_RUN_ID, 'in_progress', 'maintenance'), run(1, 'completed')],
      CURRENT_RUN_ID
    );
    assert.deepEqual(
      other.map((entry) => entry.id),
      [1]
    );
  });

  it('separates active runs from settled runs', () => {
    const runs = [
      run(1, 'in_progress'),
      run(2, 'queued'),
      run(3, 'completed'),
      run(4, 'cancelled'),
      run(5, 'failure'),
    ];
    const { active, settled } = partitionRuns(runs, CURRENT_RUN_ID);
    assert.deepEqual(
      active.map((entry) => entry.id),
      [1, 2]
    );
    assert.deepEqual(
      settled.map((entry) => entry.id),
      [3, 4, 5]
    );
  });

  it('treats every documented status as active or settled', () => {
    for (const status of ACTIVE_RUN_STATUSES) {
      assert.equal(partitionRuns([run(1, status)], CURRENT_RUN_ID).active.length, 1, status);
    }
    for (const status of ['completed', 'cancelled', 'failure', 'success', 'skipped', 'stale']) {
      assert.equal(partitionRuns([run(1, status)], CURRENT_RUN_ID).settled.length, 1, status);
    }
  });
});

describe('evaluatePurgeState', () => {
  it('passes when no cache and no other run remains', () => {
    const state = evaluatePurgeState({
      caches: { total_count: 0, actions_caches: [] },
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance')],
      currentRunId: CURRENT_RUN_ID,
    });
    assert.equal(state.ok, true);
    assert.deepEqual(state.problems, []);
  });

  it('reports remaining caches', () => {
    const state = evaluatePurgeState({
      caches: { total_count: 1, actions_caches: [{ id: 7, key: 'gradle-1' }] },
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance')],
      currentRunId: CURRENT_RUN_ID,
    });
    assert.equal(state.ok, false);
    assert.match(state.problems.join(' '), /1 Actions cache/);
  });

  it('reports remaining other runs and ignores the current run', () => {
    const state = evaluatePurgeState({
      caches: { total_count: 0, actions_caches: [] },
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance'), run(12, 'completed')],
      currentRunId: CURRENT_RUN_ID,
    });
    assert.equal(state.ok, false);
    assert.match(state.problems.join(' '), /12\/completed/);
  });
});

describe('purgeActions', () => {
  it('cancels, waits, deletes caches, deletes other runs, then verifies', async () => {
    const api = createFakeApi({
      runs: [
        run(CURRENT_RUN_ID, 'in_progress', 'maintenance'),
        run(1, 'in_progress'),
        run(2, 'queued'),
        run(3, 'completed'),
      ],
      caches: [{ id: 10, key: 'gradle-a' }, { id: 11, key: 'gradle-b' }],
      lateRun: run(999, 'completed', 'late-run'),
    });

    const result = await purgeActions({
      repo: 'owner/name',
      runId: CURRENT_RUN_ID,
      gh: api.gh,
      wait: async () => {},
      log: () => {},
    });

    assert.equal(result.state.ok, true, result.state.problems.join('; '));
    assert.deepEqual(result.cancelled, ['1', '2']);
    // Every other run is deleted, including the two this purge just cancelled.
    assert.deepEqual(result.deleted, ['1', '2', '3', '999']);

    // Ordering: cancel first, then delete caches, then delete runs.
    const mutations = api.calls.filter((call) => call.startsWith('--method'));
    const firstCancel = mutations.findIndex((call) => call.includes('/cancel'));
    const cacheDelete = mutations.findIndex((call) => call.endsWith('/actions/caches'));
    const firstRunDelete = mutations.findIndex((call) => /\/actions\/runs\/\d+$/.test(call));
    assert.ok(firstCancel >= 0 && cacheDelete > firstCancel, 'caches deleted after cancelling');
    assert.ok(firstRunDelete > cacheDelete, 'runs deleted after caches');

    // The purge never acts on its own run.
    for (const call of mutations) {
      assert.ok(
        !call.includes(`/actions/runs/${CURRENT_RUN_ID}`),
        `the purge touched its own run: ${call}`
      );
    }
  });

  it('deletes a run that appeared while the purge was waiting', async () => {
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance'), run(3, 'completed')],
      caches: [],
      lateRun: run(999, 'completed', 'late-run'),
    });

    const result = await purgeActions({
      repo: 'owner/name',
      runId: CURRENT_RUN_ID,
      gh: api.gh,
      wait: async () => {},
      log: () => {},
    });

    assert.equal(result.state.ok, true);
    assert.deepEqual(result.deleted, ['3', '999']);
  });

  it('needs no cancellation and no deletion when the repository is already clean', async () => {
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance')],
      caches: [],
    });

    const result = await purgeActions({
      repo: 'owner/name',
      runId: CURRENT_RUN_ID,
      gh: api.gh,
      wait: async () => {},
      log: () => {},
    });

    assert.deepEqual(result.cancelled, []);
    assert.deepEqual(result.deleted, []);
    assert.equal(result.state.ok, true);
  });

  it('fails loudly when other runs never settle', async () => {
    const api = createFakeApi({
      runs: [
        run(CURRENT_RUN_ID, 'in_progress', 'maintenance'),
        run(1, 'in_progress', 'stuck'),
      ],
      caches: [],
      cancelStopsRuns: false,
    });

    let clock = 0;
    await assert.rejects(
      purgeActions({
        repo: 'owner/name',
        runId: CURRENT_RUN_ID,
        gh: api.gh,
        wait: async () => {
          clock += 15 * 1000;
        },
        log: () => {},
        now: () => clock,
        settleTimeoutMs: 20 * 60 * 1000,
        pollIntervalMs: 15 * 1000,
      }),
      /Timed out/
    );

    // Nothing destructive happened while waiting.
    assert.ok(!api.calls.some((call) => call.includes('/actions/caches')));
    assert.ok(!api.calls.some((call) => /--method DELETE/.test(call)));
  });

  it('reports an incomplete end state instead of claiming success', async () => {
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance')],
      caches: [],
    });
    const realGh = api.gh;
    const gh = async (args) => {
      const result = await realGh(args);
      // A cache that reappears after the purge must be reported, not ignored.
      if (/\/actions\/caches\?/.test(args[0])) {
        api.state.caches.push({ id: 42, key: 'reappeared' });
      }
      return result;
    };

    const result = await purgeActions({
      repo: 'owner/name',
      runId: CURRENT_RUN_ID,
      gh,
      wait: async () => {},
      log: () => {},
    });

    assert.equal(result.state.ok, false);
    assert.match(result.state.problems.join(' '), /1 Actions cache/);
  });

  it('paginates run and cache listings', async () => {
    const page = (offset) => Array.from({ length: 100 }, (_, index) => run(offset + index, 'completed'));
    let runPage = 0;
    let cachePage = 0;
    const gh = async (args) => {
      if (/\/actions\/runs\?/.test(args[0])) {
        runPage += 1;
        const runs = runPage === 1 ? page(0) : [run(500, 'completed')];
        return { total_count: runs.length, workflow_runs: runs };
      }
      if (/\/actions\/caches\?/.test(args[0])) {
        cachePage += 1;
        const caches = cachePage === 1 ? Array.from({ length: 100 }, (_, i) => ({ id: i })) : [];
        return { total_count: caches.length, actions_caches: caches };
      }
      if (args[0] === '--method') return null;
      throw new Error(`unexpected call: ${args.join(' ')}`);
    };

    const runs = await listRuns(gh, 'owner/name');
    const caches = await listCaches(gh, 'owner/name');
    assert.equal(runs.length, 101);
    assert.equal(caches.total_count, 100);
  });
});
