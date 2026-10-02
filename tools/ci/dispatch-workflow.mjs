#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { appendFileSync } from 'node:fs';

import { resolveFocus as resolveDiagnoseFocus } from './diagnose-focus.mjs';

export const COMMAND_LABELS = {
  'ci:full': { workflow: 'verify.yml', mode: '' },
  'ci:app-unit': { workflow: 'diagnose.yml', mode: 'app-unit' },
  'ci:samsung-unit': { workflow: 'diagnose.yml', mode: 'samsung-unit' },
  'ci:android-static': { workflow: 'diagnose.yml', mode: 'android-static' },
  'ci:android-build': { workflow: 'diagnose.yml', mode: 'android-build' },
  'ci:backend': { workflow: 'diagnose.yml', mode: 'backend' },
};

export const COMMAND_LABEL_NAMES = Object.freeze(Object.keys(COMMAND_LABELS));

const COMMIT_SHA = /^[0-9a-f]{40}$/;

export function resolveDispatch(label) {
  const target = COMMAND_LABELS[label];
  if (!target) {
    throw new Error(
      `unsupported command label: ${label}. Allowed: ${COMMAND_LABEL_NAMES.join(', ')}`
    );
  }
  return { ...target };
}

export function resolvePullRequestFocus({ body, mode }) {
  if (!mode) return '';

  const text = typeof body === 'string' ? body : '';
  const marker = /^<!-- appt-ci-focus ([a-z0-9-]+): ([^\r\n]+) -->$/gm;
  const matches = [];

  for (const match of text.matchAll(marker)) {
    if (match[1] === mode) matches.push(match[2].trim());
  }

  if (matches.length > 1) {
    throw new Error(`multiple appt-ci-focus markers found for mode ${mode}`);
  }
  if (matches.length === 0) return '';

  const focus = matches[0];
  if (focus.length === 0) {
    throw new Error(`appt-ci-focus marker for mode ${mode} must not be empty`);
  }
  if (focus.length > 512) {
    throw new Error(`appt-ci-focus marker for mode ${mode} exceeds 512 characters`);
  }
  return focus;
}

export function buildDispatchBody({ ref, targetRef, mode = '', focus = '', expectedSha }) {
  if (typeof ref !== 'string' || ref.length === 0) {
    throw new Error('buildDispatchBody: ref is required');
  }
  if (COMMIT_SHA.test(ref)) {
    throw new Error(
      'buildDispatchBody: ref must be a branch or tag name, not a commit SHA. ' +
        'Pass the resolved head SHA as expectedSha instead.'
    );
  }
  if (typeof targetRef !== 'string' || targetRef.length === 0) {
    throw new Error(
      'buildDispatchBody: targetRef is required. The dispatch ref is the trusted ' +
        'anchor and the requested branch travels as the target_ref input.'
    );
  }
  if (COMMIT_SHA.test(targetRef)) {
    throw new Error(
      'buildDispatchBody: targetRef must be a branch or tag name, not a commit SHA. ' +
        'Pass the resolved head SHA as expectedSha instead.'
    );
  }
  if (ref === targetRef) {
    throw new Error(
      `buildDispatchBody: ref and targetRef are both '${ref}'. The dispatch ref must be ` +
        'the trusted default branch, never the pull-request branch being verified.'
    );
  }
  if (typeof expectedSha !== 'string' || !COMMIT_SHA.test(expectedSha)) {
    throw new Error('buildDispatchBody: expectedSha must be a 40-character commit SHA');
  }
  if (typeof focus !== 'string') {
    throw new Error('buildDispatchBody: focus must be a string');
  }
  if (focus && !mode) {
    throw new Error('buildDispatchBody: focus is only valid for a diagnose mode');
  }

  return {
    ref,
    inputs: {
      target_ref: targetRef,
      expected_sha: expectedSha,
      ...(mode ? { mode } : {}),
      ...(focus ? { focus } : {}),
    },
    return_run_details: true,
  };
}

export async function resolveDefaultBranch({ gh, repo }) {
  const body = await gh([`repos/${repo}`]);
  const branch = body?.default_branch;
  if (typeof branch !== 'string' || branch.length === 0) {
    throw new Error(`could not resolve the default branch for ${repo}`);
  }
  return branch;
}

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
  const body = typeof pr?.body === 'string' ? pr.body : '';
  return { sha, ref, headRepo, body };
}

export async function dispatchCommand({
  label,
  gh,
  repo,
  prNumber,
  log = (message) => console.log(message),
} = {}) {
  const { workflow, mode } = resolveDispatch(label);

  const head = await resolvePullRequestHead({ gh, repo, prNumber });

  const focus = mode ? resolvePullRequestFocus({ body: head.body, mode }) : '';
  if (mode) resolveDiagnoseFocus({ mode, focus });

  if (head.headRepo !== repo) {
    throw new Error(
      `pull request #${prNumber} heads from ${head.headRepo || '<unavailable repository>'}, not ${repo}. ` +
        'The target branch must resolve within this repository, ' +
        'so an unavailable repository or cross-repository (fork) head cannot be dispatched.'
    );
  }

  const anchor = await resolveDefaultBranch({ gh, repo });
  if (anchor === head.ref) {
    throw new Error(
      `pull request #${prNumber} heads from ${anchor}, which is the default branch. ` +
        'A dispatch runs the workflow from the default branch, so there is no trusted ' +
        'anchor distinct from the target; verify it with an ordinary run instead.'
    );
  }

  const body = buildDispatchBody({
    ref: anchor,
    targetRef: head.ref,
    mode,
    focus,
    expectedSha: head.sha,
  });

  log(
    `Dispatching ${workflow} from the trusted default branch ${anchor} for target ` +
      `ref ${head.ref} (expected ${head.sha})${mode ? ` with mode=${mode}` : ''}` +
      `${focus ? ` focus=${JSON.stringify(focus)}` : ''}.`
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
    focus,
    ref: anchor,
    targetRef: head.ref,
    expectedSha: head.sha,
    runUrl: typeof response === 'string' ? response : '',
  };
}

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

    const outputPath = process.env.GITHUB_OUTPUT;
    if (outputPath) {
      appendFileSync(
        outputPath,
        [
          `sha=${result.expectedSha}`,
          `ref=${result.ref}`,
          `target-ref=${result.targetRef}`,
          `workflow=${result.workflow}`,
          `focused=${result.focus ? 'true' : 'false'}`,
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
