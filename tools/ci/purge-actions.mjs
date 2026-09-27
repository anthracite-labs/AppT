#!/usr/bin/env node
/**
 * AppT GitHub Actions maintenance purge (Issue #88).
 *
 * `maintenance.yml` is the only destructive workflow in this repository. This
 * module owns its logic so the destructive sequence is reviewable in one file
 * and provable without touching live Actions history: every GitHub interaction
 * goes through an injectable `gh` runner, so the tests drive the real control
 * flow against a fake API.
 *
 * What a purge does, in order:
 *
 *   1. cancel every other active workflow run in the repository;
 *   2. wait for those runs to settle far enough to be deletable;
 *   3. delete every GitHub Actions cache in the repository;
 *   4. delete every workflow run except the maintenance run executing now;
 *   5. verify and report that no cache and no other run remains.
 *
 * The order is deliberate: caches are deleted only after the runs that could
 * still write them are stopped, and runs are deleted only once they have
 * settled, because GitHub refuses to delete a run that is not finished. Doing
 * it the other way round would let an in-flight run repopulate a cache between
 * deletion and verification and make a successful purge look like a failure.
 *
 * Nothing here is scheduled or automatic. It runs only when a human dispatches
 * `maintenance.yml` and types the confirmation input, and no other workflow in
 * this repository holds deletion privileges.
 *
 * Deliberately zero dependencies: its provenance is the repository itself.
 *
 * Usage:
 *   node tools/ci/purge-actions.mjs [--repo owner/name] [--run-id 123]
 *                                  [--settle-timeout-minutes 20]
 *                                  [--poll-interval-seconds 15]
 *                                  [--dry-run]
 */

import { execFile } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

/** How long to wait for cancelled runs to settle before giving up. */
export const DEFAULT_SETTLE_TIMEOUT_MINUTES = 20;

/** How often to re-read the run list while waiting. */
export const DEFAULT_POLL_INTERVAL_SECONDS = 15;

/** Run statuses that still occupy a worker and therefore cannot be deleted. */
export const ACTIVE_RUN_STATUSES = new Set([
  'queued',
  'in_progress',
  'waiting',
  'requested',
  'pending',
]);

/**
 * Split a run list into "everything except the run doing the purge", the still
 * active part of it, and the settled part of it.
 */
export function partitionRuns(runs, currentRunId) {
  const other = [];
  for (const run of runs ?? []) {
    if (run.id !== currentRunId) other.push(run);
  }
  return {
    other,
    active: other.filter((run) => ACTIVE_RUN_STATUSES.has(run.status)),
    settled: other.filter((run) => !ACTIVE_RUN_STATUSES.has(run.status)),
  };
}

/**
 * The post-purge invariant. Returns the concrete problems so a caller can
 * report them instead of a bare boolean.
 */
export function evaluatePurgeState({ caches, runs, currentRunId }) {
  const problems = [];

  const cacheList = caches?.actions_caches ?? [];
  if (cacheList.length > 0 || (caches?.total_count ?? 0) > 0) {
    problems.push(`${cacheList.length} Actions cache(s) still present`);
  }

  const { other } = partitionRuns(runs, currentRunId);
  if (other.length > 0) {
    problems.push(
      `${other.length} other workflow run(s) still present: ` +
        other.map((run) => `${run.id}/${run.status}`).join(', ')
    );
  }

  return { ok: problems.length === 0, problems };
}

/** Default GitHub CLI runner: `gh api <args>` returning parsed JSON or null. */
export async function defaultGh(args) {
  const { stdout } = await execFileAsync('gh', ['api', ...args], {
    maxBuffer: 64 * 1024 * 1024,
  });
  const text = stdout.trim();
  return text.length > 0 ? JSON.parse(text) : null;
}

export async function listRuns(gh, repo) {
  const runs = [];
  for (let page = 1; ; page += 1) {
    const body = await gh([`repos/${repo}/actions/runs?per_page=100&page=${page}`]);
    runs.push(...(body?.workflow_runs ?? []));
    if (!body?.workflow_runs || body.workflow_runs.length < 100) break;
  }
  return runs;
}

export async function listCaches(gh, repo) {
  const caches = [];
  for (let page = 1; ; page += 1) {
    const body = await gh([`repos/${repo}/actions/caches?per_page=100&page=${page}`]);
    caches.push(...(body?.actions_caches ?? []));
    if (!body?.actions_caches || body.actions_caches.length < 100) break;
  }
  return { total_count: caches.length, actions_caches: caches };
}

/**
 * Run the purge. Every GitHub call is `gh` so the sequence is testable.
 *
 * @returns {Promise<{cancelled: string[], deleted: string[], deletedCaches: string[], settled: boolean, state: object}>}
 */
export async function purgeActions({
  repo,
  runId,
  gh = defaultGh,
  wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  log = (message) => console.log(message),
  settleTimeoutMs = DEFAULT_SETTLE_TIMEOUT_MINUTES * 60 * 1000,
  pollIntervalMs = DEFAULT_POLL_INTERVAL_SECONDS * 1000,
  now = () => Date.now(),
} = {}) {
  if (!repo) throw new Error('purgeActions: repo is required');
  if (!Number.isInteger(runId)) throw new Error('purgeActions: runId must be an integer');

  const summary = [];
  const note = (line) => {
    summary.push(line);
    log(line);
  };

  // 1. Snapshot and cancel every other active run.
  let runs = await listRuns(gh, repo);
  let { other, active } = partitionRuns(runs, runId);
  note(`Found ${runs.length} workflow run(s); ${other.length} belong to other runs.`);

  const cancelled = [];
  for (const run of active) {
    note(`Cancelling run ${run.id} (${run.status}).`);
    await gh(['--method', 'POST', `repos/${repo}/actions/runs/${run.id}/cancel`]);
    cancelled.push(String(run.id));
  }
  if (cancelled.length === 0) {
    note('No other active workflow run needed cancelling.');
  }

  // 2. Wait for the cancelled runs to settle. A run that is still active
  //    cannot be deleted, so this is a real precondition, not a courtesy.
  let settled = active.length === 0;
  if (!settled) {
    const deadline = now() + settleTimeoutMs;
    for (;;) {
      await wait(pollIntervalMs);
      runs = await listRuns(gh, repo);
      ({ active } = partitionRuns(runs, runId));
      if (active.length === 0) {
        settled = true;
        note('All other active workflow runs have settled.');
        break;
      }
      if (now() >= deadline) {
        break;
      }
      note(`Waiting for ${active.length} run(s) to settle: ${active.map((r) => r.id).join(', ')}`);
    }
    if (!settled) {
      throw new Error(
        `Timed out after ${Math.round(settleTimeoutMs / 60000)} minute(s) waiting for other runs to settle.`
      );
    }
  }

  // 3. Delete every cache in the repository.
  //
  //    GitHub has no "delete every cache" endpoint. `DELETE .../actions/caches`
  //    is delete-by-key and *requires* a `key` query parameter; the only way to
  //    express an exact purge is to list the caches and delete each one by its
  //    cache ID. `listCaches` already paginates, so a repository with more than
  //    one page of caches is covered.
  note('Deleting every GitHub Actions cache in the repository.');
  const cachesBefore = await listCaches(gh, repo);
  note(`Found ${cachesBefore.total_count} Actions cache(s) to delete.`);
  const deletedCaches = [];
  for (const cache of cachesBefore.actions_caches) {
    note(`Deleting Actions cache ${cache.id} (${cache.key ?? 'no key'}).`);
    await gh(['--method', 'DELETE', `repos/${repo}/actions/caches/${cache.id}`]);
    deletedCaches.push(String(cache.id));
  }
  if (deletedCaches.length === 0) {
    note('No Actions cache needed deleting.');
  }

  // 4. Delete every other workflow run, re-reading the list so runs created
  //    during the wait are covered too.
  runs = await listRuns(gh, repo);
  ({ other } = partitionRuns(runs, runId));
  const deleted = [];
  for (const run of other) {
    note(`Deleting workflow run ${run.id} (${run.status}).`);
    await gh(['--method', 'DELETE', `repos/${repo}/actions/runs/${run.id}`]);
    deleted.push(String(run.id));
  }
  if (deleted.length === 0) {
    note('No other workflow run needed deleting.');
  }

  // 5. Verify the end state rather than assuming it.
  const caches = await listCaches(gh, repo);
  runs = await listRuns(gh, repo);
  const state = evaluatePurgeState({ caches, runs, currentRunId: runId });

  note(
    `Cancelled ${cancelled.length} run(s), deleted ${deletedCaches.length} cache(s), ` +
      `deleted ${deleted.length} run(s).`
  );
  if (state.ok) {
    note('Verified: no Actions cache and no other workflow run remains.');
  } else {
    for (const problem of state.problems) note(`Remaining: ${problem}`);
  }

  const summaryPath = process.env.GITHUB_STEP_SUMMARY;
  if (summaryPath) {
    appendFileSync(
      summaryPath,
      `### Actions maintenance purge\n\n\`\`\`\n${summary.join('\n')}\n\`\`\`\n\n` +
        `Result: ${state.ok ? 'clean' : 'incomplete'}\n`
    );
  }

  return { cancelled, deleted, deletedCaches, settled, state };
}

/**
 * Resolve the CLI timing flags into the values the purge loop actually uses.
 *
 * The flags exist so the workflow can control the settle wait, so they have to
 * reach `purgeActions`: an earlier revision parsed them and then called
 * `purgeActions({ repo, runId })`, which silently used the defaults and made the
 * declared timing a lie.
 *
 * Invalid values are rejected here rather than being forwarded, because a
 * non-finite or non-positive value would otherwise become `NaN` and the loop
 * would either give up immediately or spin.
 */
export function resolveTiming({ settleTimeoutMinutes, pollIntervalSeconds } = {}) {
  const minutes = settleTimeoutMinutes ?? DEFAULT_SETTLE_TIMEOUT_MINUTES;
  const seconds = pollIntervalSeconds ?? DEFAULT_POLL_INTERVAL_SECONDS;

  for (const [flag, value] of [
    ['--settle-timeout-minutes', minutes],
    ['--poll-interval-seconds', seconds],
  ]) {
    if (!Number.isInteger(value) || value <= 0) {
      throw new Error(`${flag} must be a positive integer, got: ${value}`);
    }
  }

  return {
    settleTimeoutMs: minutes * 60 * 1000,
    pollIntervalMs: seconds * 1000,
  };
}

/**
 * Build the read-only `gh` runner a dry run uses.
 *
 * A dry run has to report the repository's real state to be worth anything as a
 * pre-destructive preview, so read-only calls reach the real API and only
 * mutations are logged and suppressed. Returning `null` for every call — as an
 * earlier revision did — made the preview always report zero runs and zero
 * caches, which is worse than no preview.
 */
export function createDryRunGh({ gh = defaultGh, log = (message) => console.log(message) } = {}) {
  return async (args, stdin = '') => {
    if (args[0] === '--method') {
      log(`[dry-run] would run: gh api ${args.join(' ')}`);
      return null;
    }
    log(`[dry-run] gh api ${args.join(' ')}`);
    return gh(args, stdin);
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
      case '--repo':
        options.repo = next();
        break;
      case '--run-id':
        options.runId = Number.parseInt(next(), 10);
        break;
      case '--settle-timeout-minutes':
        options.settleTimeoutMinutes = Number.parseInt(next(), 10);
        break;
      case '--poll-interval-seconds':
        options.pollIntervalSeconds = Number.parseInt(next(), 10);
        break;
      case '--dry-run':
        options.dryRun = true;
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
    console.log('Usage: node tools/ci/purge-actions.mjs [--repo owner/name] [--run-id 123]');
    return;
  }

  const repo = options.repo ?? process.env.GITHUB_REPOSITORY;
  const runId = options.runId ?? Number.parseInt(process.env.GITHUB_RUN_ID ?? '', 10);

  if (!repo || !Number.isInteger(runId)) {
    console.error(
      'purge-actions: GITHUB_REPOSITORY and GITHUB_RUN_ID (or --repo/--run-id) are required.'
    );
    process.exitCode = 1;
    return;
  }

  // The timing flags are forwarded, not merely parsed: the workflow declares
  // them, so the purge loop has to actually use them.
  const { settleTimeoutMs, pollIntervalMs } = resolveTiming(options);

  if (options.dryRun) {
    const gh = createDryRunGh();
    let runs;
    let caches;
    try {
      runs = await listRuns(gh, repo);
      caches = await listCaches(gh, repo);
    } catch (error) {
      console.error(`purge-actions: dry run could not read the repository state: ${error.message}`);
      process.exitCode = 1;
      return;
    }
    const { other, active } = partitionRuns(runs, runId);
    console.log(`[dry-run] repository: ${repo}`);
    console.log(`[dry-run] current run: ${runId}`);
    console.log(`[dry-run] other runs: ${other.length} (active: ${active.length})`);
    console.log(`[dry-run] caches: ${caches.total_count}`);
    console.log('[dry-run] nothing was cancelled, deleted, or modified.');
    return;
  }

  const result = await purgeActions({ repo, runId, settleTimeoutMs, pollIntervalMs });
  if (!result.state.ok) {
    console.error('purge-actions: the purge did not reach a clean end state.');
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(`purge-actions: ${error.message}`);
    process.exitCode = 1;
  });
}
