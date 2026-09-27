#!/usr/bin/env node
/**
 * AppT trusted agent-control dispatch bridge (Issue #88).
 *
 * `agent-control.yml` is the only privileged control path in this repository.
 * It translates a `ci:*` command label on a pull request into an ordinary
 * workflow dispatch. This module owns that translation so it is reviewable in
 * one file and provable without touching a real repository: every GitHub
 * interaction goes through an injectable `gh` runner, so the tests drive the
 * real control flow against a fake API.
 *
 * The provider contract this module exists to get right
 * ---------------------------------------------------
 * GitHub's Create Workflow Dispatch endpoint takes `ref` as *the git reference
 * for the workflow* — a **branch or tag name**. It is not a commit SHA. Passing
 * a SHA is rejected by the provider.
 *
 * That is inconvenient, because the whole point of the bridge is to verify the
 * exact pull-request head the command was issued against, and a branch name is
 * mutable: the branch can move between resolving the head and the dispatched
 * run starting.
 *
 * The resolution is to dispatch against the provider-valid branch name and to
 * carry the resolved head SHA alongside it as an input, `expected_sha`. The
 * dispatched workflows (`verify.yml`, `diagnose.yml`) assert that
 * `github.sha` still equals `expected_sha` **before doing any target-controlled
 * work** — before Gradle, before npm, before the managed device — and hard-fail
 * otherwise. So the exact-PR-head guarantee is preserved without asking the
 * provider to accept a request shape it does not support.
 *
 * The privileged boundary is unchanged: this module never checks out or
 * executes pull-request-controlled code. It talks to the GitHub API and nothing
 * else.
 *
 * Fork pull requests cannot be dispatched this way at all: the provider
 * dispatches a ref that must exist in *this* repository, and a fork's head
 * branch does not. That is a hard failure, not a silent fallback.
 *
 * Deliberately zero dependencies: its provenance is the repository itself.
 *
 * Usage:
 *   node tools/ci/dispatch-workflow.mjs --repo owner/name --pr 123 --label ci:app-unit
 */

import { spawn } from 'node:child_process';
import { appendFileSync } from 'node:fs';

/**
 * The command-label allowlist. Only these labels do anything; anything else is
 * a hard failure with no API call, so an unrecognised label produces no run.
 *
 * Each entry names the workflow file and the `workflow_dispatch` mode to pass.
 * A label that dispatches `verify.yml` passes no mode: a dispatched `verify`
 * run is the full suite by design (there is no `mode` input on `verify`).
 */
export const COMMAND_LABELS = {
  'ci:full': { workflow: 'verify.yml', mode: '' },
  'ci:app-unit': { workflow: 'diagnose.yml', mode: 'app-unit' },
  'ci:samsung-unit': { workflow: 'diagnose.yml', mode: 'samsung-unit' },
  'ci:android-static': { workflow: 'diagnose.yml', mode: 'android-static' },
  'ci:android-build': { workflow: 'diagnose.yml', mode: 'android-build' },
  'ci:backend': { workflow: 'diagnose.yml', mode: 'backend' },
  'ci:backend-static': { workflow: 'diagnose.yml', mode: 'backend-static' },
  'ci:backend-test': { workflow: 'diagnose.yml', mode: 'backend-test' },
  'ci:device': { workflow: 'diagnose.yml', mode: 'device' },
};

/** Every allowlisted label, in a stable order for reporting and tests. */
export const COMMAND_LABEL_NAMES = Object.freeze(Object.keys(COMMAND_LABELS));

/**
 * A full 40-character lowercase hex commit SHA. Used to *reject* a SHA as a
 * dispatch ref, never to accept one.
 */
const COMMIT_SHA = /^[0-9a-f]{40}$/;

/**
 * Resolve a command label to the workflow and mode it dispatches.
 *
 * @throws if the label is not in the allowlist.
 */
export function resolveDispatch(label) {
  const target = COMMAND_LABELS[label];
  if (!target) {
    throw new Error(
      `unsupported command label: ${label}. Allowed: ${COMMAND_LABEL_NAMES.join(', ')}`
    );
  }
  return { ...target };
}

/**
 * Build the provider-valid dispatch request body.
 *
 * `ref` is the pull request's head *branch name*. It is asserted not to be a
 * commit SHA, because the provider would reject that: this is the defect the
 * Issue #88 contract review found, and the assertion is what keeps it from
 * coming back.
 *
 * `expected_sha` is always present. It is how the exact-PR-head guarantee
 * survives dispatching a mutable branch ref.
 */
export function buildDispatchBody({ ref, mode = '', expectedSha }) {
  if (typeof ref !== 'string' || ref.length === 0) {
    throw new Error('buildDispatchBody: ref is required');
  }
  if (COMMIT_SHA.test(ref)) {
    throw new Error(
      'buildDispatchBody: ref must be a branch or tag name, not a commit SHA. ' +
        'Pass the resolved head SHA as expectedSha instead.'
    );
  }
  if (typeof expectedSha !== 'string' || !COMMIT_SHA.test(expectedSha)) {
    throw new Error('buildDispatchBody: expectedSha must be a 40-character commit SHA');
  }

  const inputs = { expected_sha: expectedSha };
  if (mode) inputs.mode = mode;

  return {
    ref,
    inputs,
    // Ask the provider for the run details so the audit-trail comment can link
    // the dispatched run without polling for it.
    return_run_details: true,
  };
}

/**
 * Read the pull-request head: its exact SHA, its branch name, and the
 * repository that branch lives in.
 */
export async function resolvePullRequestHead({ gh, repo, prNumber }) {
  if (!Number.isInteger(prNumber)) {
    throw new Error('resolvePullRequestHead: prNumber must be an integer');
  }
  const pr = await gh([`repos/${repo}/pulls/${prNumber}`]);
  const sha = pr?.head?.sha;
  const ref = pr?.head?.ref;
  const headRepo = pr?.head?.repo?.full_name;
  if (typeof sha !== 'string' || !COMMIT_SHA.test(sha)) {
    throw new Error(`could not resolve a pull-request head SHA for #${prNumber}`);
  }
  if (typeof ref !== 'string' || ref.length === 0) {
    throw new Error(`could not resolve a pull-request head branch for #${prNumber}`);
  }
  return { sha, ref, headRepo };
}

/**
 * Run the whole bridge: resolve the label, resolve the head, build a
 * provider-valid body, dispatch it, and report what was dispatched.
 *
 * Every GitHub call is `gh(args, stdin)`, so the sequence is testable against
 * a fake API. `stdin` is how the dispatch body reaches `gh api --input -`.
 */
export async function dispatchCommand({
  label,
  gh,
  repo,
  prNumber,
  log = (message) => console.log(message),
} = {}) {
  const { workflow, mode } = resolveDispatch(label);

  const head = await resolvePullRequestHead({ gh, repo, prNumber });

  // The provider dispatches a ref that must exist in this repository. A fork's
  // head branch does not, so there is no provider-valid way to dispatch it.
  // Fail closed rather than dispatching something else.
  if (head.headRepo && head.headRepo !== repo) {
    throw new Error(
      `pull request #${prNumber} heads from ${head.headRepo}, not ${repo}. ` +
        'The provider can only dispatch a ref that exists in this repository, ' +
        'so a cross-repository (fork) head cannot be dispatched.'
    );
  }

  const body = buildDispatchBody({ ref: head.ref, mode, expectedSha: head.sha });

  log(
    `Dispatching ${workflow} against ref ${head.ref} (expected ${head.sha})` +
      `${mode ? ` with mode=${mode}` : ''}.`
  );

  const response = await gh(
    [
      '--method',
      'POST',
      `repos/${repo}/actions/workflows/${workflow}/dispatches`,
      '--input',
      '-',
      '--jq',
      '.html_url // .run_url // empty',
    ],
    JSON.stringify(body)
  );

  return {
    workflow,
    mode,
    ref: head.ref,
    expectedSha: head.sha,
    runUrl: typeof response === 'string' ? response : '',
  };
}

/**
 * Default GitHub CLI runner: `gh api <args>` with an optional stdin body,
 * returning parsed JSON, a bare `--jq` string, or null for an empty response.
 */
export async function defaultGh(args, stdin = '') {
  return new Promise((resolve, reject) => {
    const child = spawn('gh', ['api', ...args], {
      stdio: ['pipe', 'pipe', 'inherit'],
      maxBuffer: 64 * 1024 * 1024,
    });
    let stdout = '';
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
    });
    child.on('error', reject);
    child.on('close', (code) => {
      if (code !== 0) {
        reject(new Error(`gh api exited with code ${code}`));
        return;
      }
      const text = stdout.trim();
      if (text.length === 0) {
        resolve(null);
        return;
      }
      try {
        resolve(JSON.parse(text));
      } catch {
        // A `--jq` projection can legitimately yield a bare string, which is
        // not JSON.
        resolve(text);
      }
    });
    child.stdin.end(stdin);
  });
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
      case '--pr':
        options.prNumber = Number.parseInt(next(), 10);
        break;
      case '--label':
        options.label = next();
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
      'Usage: node tools/ci/dispatch-workflow.mjs --repo owner/name --pr 123 --label ci:app-unit'
    );
    return;
  }

  const repo = options.repo ?? process.env.GITHUB_REPOSITORY;
  const prNumber = options.prNumber ?? Number.parseInt(process.env.PR_NUMBER ?? '', 10);
  const label = options.label ?? process.env.LABEL;

  if (!repo || !Number.isInteger(prNumber) || !label) {
    console.error(
      'dispatch-workflow: --repo, --pr and --label ' +
        '(or GITHUB_REPOSITORY, PR_NUMBER, LABEL) are required.'
    );
    process.exitCode = 1;
    return;
  }

  try {
    const result = await dispatchCommand({
      label,
      gh: defaultGh,
      repo,
      prNumber,
      log: (message) => console.log(message),
    });

    // Publish the outcome so the calling workflow can record the audit trail.
    const outputPath = process.env.GITHUB_OUTPUT;
    if (outputPath) {
      appendFileSync(
        outputPath,
        [
          `sha=${result.expectedSha}`,
          `ref=${result.ref}`,
          `workflow=${result.workflow}`,
          `run-url=${result.runUrl}`,
          '',
        ].join('\n')
      );
    }

    console.log(`Dispatched run: ${result.runUrl || '<not reported by the provider>'}`);
  } catch (error) {
    console.error(`dispatch-workflow: ${error.message}`);
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === `file://${process.argv[1]}`) {
  main();
}
