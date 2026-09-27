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
  DEFAULT_POLL_INTERVAL_SECONDS,
  DEFAULT_SETTLE_TIMEOUT_MINUTES,
  createDryRunGh,
  evaluatePurgeState,
  listCaches,
  listRuns,
  partitionRuns,
  purgeActions,
  resolveTiming,
} from '../purge-actions.mjs';

const CURRENT_RUN_ID = 900;

function run(id, status, name = 'verify') {
  return { id, status, name, head_sha: `sha-${id}` };
}

/**
 * A fake `gh api` implementation with mutable repository state, so the purge
 * code under test sees the consequences of its own calls.
 *
 * The fake is deliberately strict about *shape*, not just about outcome. GitHub
 * has no "delete every cache" endpoint: `DELETE .../actions/caches` is
 * delete-by-key and requires a `key`, and the only exact purge is
 * `DELETE .../actions/caches/{cache_id}` per cache. A bare
 * `DELETE .../actions/caches` therefore throws here, so a regression to that
 * invalid request cannot pass by silently succeeding.
 */
function createFakeApi({
  runs = [],
  caches = [],
  cancelStopsRuns = true,
  lateRun = null,
  lateActiveRun = null,
  // Number of run listings after which a `lateRun` stops on its own, without
  // being cancelled. Used to land a settle exactly on the deadline.
  lateRunStopsAfter = null,
} = {}) {
  const state = {
    runs: runs.map((entry) => ({ ...entry })),
    caches: caches.map((entry) => ({ ...entry })),
  };
  const calls = [];
  let lateRunPushed = false;
  let lateActiveRunPushed = false;
  let runListings = 0;

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

      // The provider-valid cache deletion is by cache ID.
      const cacheDeletion = path.match(/\/actions\/caches\/(\d+)$/);
      if (method === 'DELETE' && cacheDeletion) {
        const id = Number(cacheDeletion[1]);
        state.caches = state.caches.filter((entry) => entry.id !== id);
        return null;
      }

      // A bare `DELETE .../actions/caches` is not a provider-valid request:
      // that endpoint is delete-by-key and requires `key`. Refuse it so the
      // tests cannot be satisfied by an invalid endpoint shape.
      if (method === 'DELETE' && /\/actions\/caches$/.test(path)) {
        throw new Error(
          'invalid request: DELETE .../actions/caches requires a key; ' +
            'an exact purge must delete each cache by cache ID'
        );
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
      // `lateRun` models a run that appeared after the purge's initial
      // snapshot — queued while the purge was waiting for others to settle, or
      // started by some other trigger entirely. It is injected on any listing
      // after the first, and at most once: a run either appeared during the
      // purge or it did not.
      if (lateRun && !lateRunPushed && runListings >= 1) {
        lateRunPushed = true;
        state.runs.push({ ...lateRun });
      }
      // A late run that stops on its own rather than on cancel, so the settle
      // can be made to land on the final poll rather than after it.
      if (lateRunStopsAfter !== null && runListings >= lateRunStopsAfter) {
        for (const entry of state.runs) {
          if (entry.id === lateRun.id) entry.status = 'completed';
        }
      }
      runListings += 1;
      return { total_count: state.runs.length, workflow_runs: state.runs };
    }
    if (/\/actions\/caches\?/.test(args[0])) {
      // `lateActiveRun` models a run that is *queued while the purge is
      // deleting caches* -- after the first settlement phase has finished and
      // before the final pre-deletion run listing. That is the exact window the
      // final-listing phase has to handle, so it is injected on the first cache
      // listing and at most once.
      if (lateActiveRun && !lateActiveRunPushed) {
        lateActiveRunPushed = true;
        state.runs.push({ ...lateActiveRun });
      }
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
    assert.equal(result.settled, true);
    assert.deepEqual(result.cancelled, ['1', '2']);
    // Every other run is deleted, including the two this purge just cancelled.
    assert.deepEqual(result.deleted, ['1', '2', '3', '999']);
    // Every cache is deleted, by cache ID.
    assert.deepEqual(result.deletedCaches, ['10', '11']);

    // Ordering: cancel first, then delete caches, then delete runs.
    const mutations = api.calls.filter((call) => call.startsWith('--method'));
    const firstCancel = mutations.findIndex((call) => call.includes('/cancel'));
    const firstCacheDelete = mutations.findIndex((call) => /\/actions\/caches\/\d+$/.test(call));
    const firstRunDelete = mutations.findIndex((call) => /\/actions\/runs\/\d+$/.test(call));
    assert.ok(firstCancel >= 0 && firstCacheDelete > firstCancel, 'caches deleted after cancelling');
    assert.ok(firstRunDelete > firstCacheDelete, 'runs deleted after caches');

    // Caches are deleted by cache ID, and never with the invalid bare
    // `DELETE .../actions/caches` request shape.
    for (const call of mutations) {
      assert.ok(
        !/--method DELETE repos\/\S+\/actions\/caches$/.test(call),
        `invalid bare cache deletion: ${call}`
      );
    }

    // The purge never acts on its own run.
    for (const call of mutations) {
      assert.ok(
        !call.includes(`/actions/runs/${CURRENT_RUN_ID}`),
        `the purge touched its own run: ${call}`
      );
    }
  });

  it('deletes every cache by cache ID, not with a bare cache-deletion request', async () => {
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance')],
      caches: [
        { id: 10, key: 'gradle-a' },
        { id: 11, key: 'gradle-b' },
        { id: 12, key: 'gradle-c' },
      ],
    });

    const result = await purgeActions({
      repo: 'owner/name',
      runId: CURRENT_RUN_ID,
      gh: api.gh,
      wait: async () => {},
      log: () => {},
    });

    assert.equal(result.state.ok, true, result.state.problems.join('; '));
    assert.deepEqual(result.deletedCaches, ['10', '11', '12']);

    const cacheDeletes = api.calls.filter((call) =>
      /--method DELETE repos\/\S+\/actions\/caches\/\d+$/.test(call)
    );
    assert.equal(cacheDeletes.length, 3, `expected one delete per cache: ${cacheDeletes}`);
    for (const id of ['10', '11', '12']) {
      assert.ok(
        cacheDeletes.some((call) => call.endsWith(`/actions/caches/${id}`)),
        `cache ${id} was not deleted by ID`
      );
    }

    // The end-state verification re-lists caches, so it must still see none.
    assert.deepEqual(api.state.caches, []);
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

  it('cancels and settles a run that appeared during the cache phase, then deletes it', async () => {
    // The race the review found: the cache phase takes real time, and a run
    // queued during it is still *active* when the final pre-deletion listing is
    // taken. GitHub refuses to delete an unfinished run, so deleting it blind
    // would leave it behind and make a purge that did not do its job look like
    // a failure. The final listing must therefore cancel it, settle it, and
    // only then delete it.
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance'), run(3, 'completed')],
      caches: [{ id: 10, key: 'gradle-a' }, { id: 11, key: 'gradle-b' }],
      lateActiveRun: run(777, 'queued', 'queued-during-cache-phase'),
    });

    const result = await purgeActions({
      repo: 'owner/name',
      runId: CURRENT_RUN_ID,
      gh: api.gh,
      wait: async () => {},
      log: () => {},
    });

    assert.equal(result.state.ok, true, result.state.problems.join('; '));
    // The initial snapshot had nothing active, so nothing was cancelled there.
    // The late run is cancelled -- and recorded -- by the final phase.
    assert.deepEqual(result.cancelled, []);
    assert.deepEqual(result.lateCancelled, ['777']);
    assert.deepEqual(result.deleted, ['3', '777']);
    assert.deepEqual(result.deletedCaches, ['10', '11']);

    // Ordering: the caches are deleted first, the late run is cancelled after
    // that, and it is deleted only once it has settled. Cancellation before
    // deletion is the whole point -- a run cannot be deleted while active.
    const mutations = api.calls.filter((call) => call.startsWith('--method'));
    const lastCacheDelete = mutations.map(
      (call, index) => (/\/actions\/caches\/\d+$/.test(call) ? index : -1)
    ).reduce((a, b) => Math.max(a, b), -1);
    const lateCancel = mutations.findIndex((call) => call.includes('/actions/runs/777/cancel'));
    const lateDelete = mutations.findIndex((call) => /\/actions\/runs\/777$/.test(call));

    assert.ok(lastCacheDelete >= 0, 'the caches were deleted');
    assert.ok(lateCancel > lastCacheDelete, 'the late run was cancelled after the cache phase');
    assert.ok(lateDelete > lateCancel, 'the late run was deleted after it was cancelled');
  });

  it('exits cleanly when a late run settles on the very last poll', async () => {
    // The late-run loop re-lists before it checks the deadline, so a run that
    // stops on the final poll is a success rather than a timeout. Checking the
    // deadline first turns a settled end state into a spurious failure: the
    // settle wait returns success at exactly the deadline, and re-checking the
    // deadline before the next listing then throws.
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance'), run(3, 'completed')],
      caches: [{ id: 10, key: 'gradle-a' }],
      lateRun: run(999, 'in_progress', 'stops-on-its-own'),
      // Cancel does not stop it, so only the passage of time does.
      cancelStopsRuns: false,
      // 60s / 15s = 4 polls, and the run stops on the fifth listing -- the one
      // that reports it settled at clock 60000, the deadline itself.
      lateRunStopsAfter: 5,
    });

    let clock = 0;
    const result = await purgeActions({
      repo: 'owner/name',
      runId: CURRENT_RUN_ID,
      gh: api.gh,
      wait: async () => {
        clock += 15 * 1000;
      },
      log: () => {},
      now: () => clock,
      settleTimeoutMs: 60 * 1000,
      pollIntervalMs: 15 * 1000,
    });

    // The settle window was used to the last poll, and that is still a success.
    assert.equal(result.state.ok, true, result.state.problems.join('; '));
    assert.equal(result.settled, true);
    assert.equal(clock, 60 * 1000, 'the settle ran to its deadline and still succeeded');
    // It was cancelled while active, waited out, and only then deleted -- never
    // deleted while active, and never left behind.
    assert.deepEqual(result.lateCancelled, ['999']);
    assert.deepEqual(result.deleted, ['3', '999']);
    const mutations = api.calls.filter((call) => call.startsWith('--method'));
    const cancelAt = mutations.findIndex((call) => call.includes('/actions/runs/999/cancel'));
    const deleteAt = mutations.findIndex((call) => /\/actions\/runs\/999$/.test(call));
    assert.ok(cancelAt !== -1, 'the late run was cancelled');
    assert.ok(deleteAt > cancelAt, 'the late run was deleted only after it was cancelled');
  });

  it('deletes a late active run only after it has settled', async () => {
    // A late run that does not stop on the first cancel must still be waited
    // out under the same bounded rules, and nothing may be deleted until it
    // has settled.
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance'), run(3, 'completed')],
      caches: [{ id: 10, key: 'gradle-a' }],
      lateActiveRun: run(777, 'in_progress', 'stuck-late-run'),
      cancelStopsRuns: false,
    });

    // The run never stops on cancel, so the settle loop must run to its
    // deadline. A real clock would make this test slow, so the injected clock
    // advances exactly as far as the injected wait does.
    let clock = 0;
    const pushClock = async () => {
      clock += 15 * 1000;
    };
    await assert.rejects(
      purgeActions({
        repo: 'owner/name',
        runId: CURRENT_RUN_ID,
        gh: api.gh,
        wait: pushClock,
        log: () => {},
        now: () => clock,
        settleTimeoutMs: 60 * 1000,
        pollIntervalMs: 15 * 1000,
      }),
      /Timed out after 1 minute\(s\) waiting for a run that appeared during the purge/
    );

    // The late run was cancelled, but it never settled -- so the purge fails
    // closed instead of deleting an active run and calling the end state clean.
    const mutations = api.calls.filter((call) => call.startsWith('--method'));
    assert.ok(
      mutations.some((call) => call.includes('/actions/runs/777/cancel')),
      'the late run was cancelled'
    );
    assert.ok(
      !mutations.some((call) => /\/actions\/runs\/777$/.test(call)),
      'an unsettled run was never deleted'
    );
    assert.ok(
      !mutations.some((call) => /\/actions\/runs\/3$/.test(call)),
      'a failed purge deletes no run at all'
    );
  });

  it('never deletes an active run introduced after the final settled snapshot', async () => {
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance'), run(3, 'completed')],
    });
    let listings = 0;
    const gh = async (args) => {
      if (/\/actions\/runs\?/.test(args[0]) && ++listings === 3) {
        // First listing: initial state. Second: final settled snapshot.
        // The next listing must verify the result, not add unchecked runs to
        // the deletion set.
        api.state.runs.push(run(777, 'in_progress', 'arrived-after-settlement'));
      }
      return api.gh(args);
    };
    const result = await purgeActions({
      repo: 'owner/name',
      runId: CURRENT_RUN_ID,
      gh,
      wait: async () => {},
      log: () => {},
    });
    assert.equal(result.state.ok, false, 'concurrent arrivals prevent a clean-purge claim');
    assert.deepEqual(result.deleted, ['3']);
    assert.ok(!api.calls.some((call) => call.includes('/actions/runs/777')));
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

describe('resolveTiming', () => {
  it('uses the documented defaults when no flags are supplied', () => {
    const timing = resolveTiming({});
    assert.equal(timing.settleTimeoutMs, DEFAULT_SETTLE_TIMEOUT_MINUTES * 60 * 1000);
    assert.equal(timing.pollIntervalMs, DEFAULT_POLL_INTERVAL_SECONDS * 1000);
  });

  it('treats an absent flag as the default rather than as an error', () => {
    // `null` and `undefined` both mean "not supplied": the nullish coalescing in
    // resolveTiming must pick the default, and only a *present but invalid*
    // value is an error.
    assert.deepEqual(resolveTiming({ settleTimeoutMinutes: null }), resolveTiming({}));
    assert.deepEqual(resolveTiming({ pollIntervalSeconds: undefined }), resolveTiming({}));
    assert.deepEqual(resolveTiming(), resolveTiming({}));
  });

  it('converts the CLI units into the units the purge loop uses', () => {
    const timing = resolveTiming({ settleTimeoutMinutes: 3, pollIntervalSeconds: 7 });
    assert.equal(timing.settleTimeoutMs, 3 * 60 * 1000);
    assert.equal(timing.pollIntervalMs, 7 * 1000);
  });

  it('rejects invalid, non-finite and non-positive values instead of forwarding NaN', () => {
    for (const settleTimeoutMinutes of [0, -1, 1.5, NaN, Infinity, '20']) {
      assert.throws(
        () => resolveTiming({ settleTimeoutMinutes }),
        /--settle-timeout-minutes must be a positive integer/,
        String(settleTimeoutMinutes)
      );
    }
    for (const pollIntervalSeconds of [0, -5, 2.5, NaN, Infinity, '15']) {
      assert.throws(
        () => resolveTiming({ pollIntervalSeconds }),
        /--poll-interval-seconds must be a positive integer/,
        String(pollIntervalSeconds)
      );
    }
  });
});

describe('purge timing reaches the purge loop', () => {
  it('polls at the supplied interval and gives up at the supplied timeout', async () => {
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance'), run(1, 'in_progress', 'stuck')],
      caches: [],
      cancelStopsRuns: false,
    });

    const { settleTimeoutMs, pollIntervalMs } = resolveTiming({
      settleTimeoutMinutes: 2,
      pollIntervalSeconds: 20,
    });

    const waited = [];
    let clock = 0;
    await assert.rejects(
      purgeActions({
        repo: 'owner/name',
        runId: CURRENT_RUN_ID,
        gh: api.gh,
        wait: async () => {
          waited.push(pollIntervalMs);
          clock += pollIntervalMs;
        },
        log: () => {},
        now: () => clock,
        settleTimeoutMs,
        pollIntervalMs,
      }),
      /Timed out after 2 minute/
    );

    // The loop really used the supplied interval, and really stopped at the
    // supplied timeout rather than the module default.
    assert.ok(waited.length > 0, 'the loop must poll at least once');
    for (const interval of waited) assert.equal(interval, pollIntervalMs);
    assert.equal(waited.length, Math.floor(settleTimeoutMs / pollIntervalMs));
  });

  it('accepts different supplied values and drives the loop differently', async () => {
    const api = createFakeApi({
      runs: [run(CURRENT_RUN_ID, 'in_progress', 'maintenance'), run(1, 'in_progress', 'stuck')],
      caches: [],
      cancelStopsRuns: false,
    });

    const { settleTimeoutMs, pollIntervalMs } = resolveTiming({
      settleTimeoutMinutes: 1,
      pollIntervalSeconds: 10,
    });

    const waited = [];
    let clock = 0;
    await assert.rejects(
      purgeActions({
        repo: 'owner/name',
        runId: CURRENT_RUN_ID,
        gh: api.gh,
        wait: async () => {
          waited.push(pollIntervalMs);
          clock += pollIntervalMs;
        },
        log: () => {},
        now: () => clock,
        settleTimeoutMs,
        pollIntervalMs,
      }),
      /Timed out after 1 minute/
    );

    assert.equal(pollIntervalMs, 10 * 1000);
    assert.equal(waited.length, 6);
  });
});

describe('createDryRunGh', () => {
  it('passes read-only calls through to the real API so the preview is real', async () => {
    const reads = [];
    const mutations = [];
    const fakeGh = async (args) => {
      reads.push(args.join(' '));
      return { total_count: 0, workflow_runs: [], actions_caches: [] };
    };
    const gh = createDryRunGh({ gh: fakeGh, log: () => {} });

    const listed = await gh(['repos/owner/name/actions/runs?per_page=100&page=1']);
    assert.equal(reads.length, 1, 'the read reached the real API');
    assert.ok(listed, 'the real response is returned, not null');
    assert.equal(mutations.length, 0);
  });

  it('suppresses and logs mutations instead of performing them', async () => {
    const calls = [];
    const logged = [];
    const fakeGh = async (args) => {
      calls.push(args.join(' '));
      return null;
    };
    const gh = createDryRunGh({ gh: fakeGh, log: (m) => logged.push(m) });

    const cancel = await gh(['--method', 'POST', 'repos/owner/name/actions/runs/1/cancel']);
    const del = await gh(['--method', 'DELETE', 'repos/owner/name/actions/caches/10']);

    assert.equal(cancel, null);
    assert.equal(del, null);
    assert.equal(calls.length, 0, 'no mutation reached the real API');
    assert.equal(logged.length, 2);
    assert.match(logged.join('\n'), /would run: gh api --method POST/);
    assert.match(logged.join('\n'), /would run: gh api --method DELETE/);
  });

  it('reports nonzero repository state while performing zero mutations', async () => {
    const api = createFakeApi({
      runs: [
        run(CURRENT_RUN_ID, 'in_progress', 'maintenance'),
        run(1, 'in_progress'),
        run(2, 'completed'),
      ],
      caches: [{ id: 10, key: 'gradle-a' }, { id: 11, key: 'gradle-b' }],
    });
    const gh = createDryRunGh({ gh: api.gh, log: () => {} });

    const runs = await listRuns(gh, 'owner/name');
    const caches = await listCaches(gh, 'owner/name');
    const { other, active } = partitionRuns(runs, CURRENT_RUN_ID);

    // The preview reports the real, nonzero state...
    assert.equal(other.length, 2);
    assert.equal(active.length, 1);
    assert.equal(caches.total_count, 2);

    // ...and nothing was mutated.
    const mutations = api.calls.filter((call) => call.startsWith('--method'));
    assert.deepEqual(mutations, []);
    assert.equal(api.state.runs.length, 3, 'no run was cancelled or deleted');
    assert.equal(api.state.caches.length, 2, 'no cache was deleted');
  });
});
