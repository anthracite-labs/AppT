# Contract Exception — Issue #145 (S06→S17 contiguous program)

Date: 2026-10-02
Branch: `arena/01a0fd0b-appt`
Base: `adf18d6` (accepted `main` after PR #147)
Contract: Issue #145 Contract Revision 1, `.project-ai/execution/arena-dispatch.md`
§2A/§4

## Outcome

The contiguous S06→S17 program was stopped at its first checkpoint and control
is returned to the control plane / human owner. Per Issue #145's Contract
Exceptions section, the following condition is met:

> a physical/provider capability required for a dependency cannot be exercised
> through any safe route.

No implementation edits were made. The branch is clean at `adf18d6`, so no
partial candidate exists and nothing accepted is invalidated. No provider,
physical, legal or human evidence is claimed as passed.

## Blocked requirements

### S06 — verification clause cannot be reached

`slices.md` S06 requires, in one verification clause:

- an instrumented rotation test that asserts Activity recreation;
- a scripted LAN-flap run;
- one real-Samsung checkpoint recording a LAN drop/recovery plus leave/return or
  Activity recreation on the same candidate.

This environment has no Android device, no emulator, no adb, no Android SDK and
no JDK, and the repository currently exposes **no connected/instrumented
execution route at all**: every job in `verify.yml` and `diagnose.yml` runs on
`ubuntu-24.04` with no device or emulator step, and accepted state records the
S05 retirement of hosted Gradle Managed Device and `ci:device`
(`.project-ai/PROJECT_STATE.md`). The real-Samsung checkpoint additionally needs
a physical phone and television, which Arena does not have. Those evidence modes
cannot be exercised through any safe route, so S06 cannot reach acceptance
evidence and the ordered contract cannot move to S07.

### S07–S17 — provider and human gates are unreachable by Arena

| Checkpoint | Blocked requirement | Why Arena cannot exercise it |
| --- | --- | --- |
| S07 | `dev`/`internal`/`production` projects exist (Firebase, Firestore, functions, Secret Manager marker keys, Cloud KMS); App Check certificate fingerprints registered per project; Play catalogue holds the lifetime product with a licence tester; RTDN topic; WIF deployment; Play internal-track install; `apksigner`/bundle certificate inspection | Requires Google Cloud / Firebase / Play Console owner authority and secrets. The contract forbids Arena holding a long-lived Google credential, and this environment has no provider credentials or CLIs, no `google-services.json`, no `.firebaserc`, and no deployment workflow. `appt-dev` / `appt-internal` / `appt-prod` appear only in `docs/architecture/release.md` |
| S08 | First real backend deployment with traffic split plus a recorded production rollback drill; one real Google sign-in and trial activation against `appt-internal` proving the ID-token and App Check path | Same provider gate |
| S10 | Recorded paid-path production drill: one real purchase on production, grant and acknowledgement, then refund, exercising RTDN void and revocation | Same provider gate; requires a live Play product and real payment |
| S17 | Source-license decision and Samsung vendor-terms review recorded as **human** decisions; final promotion path | Issue #145: "Arena may prepare the record/checklist but cannot decide or self-approve either gate"; production promotion is owner-only |

Local Android/JVM verification is itself blocked, so even code-level checkpoint
work has no local build/test loop. The repository's documented fallback — a
`diagnose.yml` dispatch — is also unavailable to this integration: direct
dispatch returns `HTTP 403 Resource not accessible by integration`, and hosted
runs are reachable only through the `agent-control` label bridge
(`ci:app-unit`, `ci:samsung-unit`, `ci:android-static`, `ci:android-build`,
`ci:backend`, `ci:full`) on an **open pull request**. `ci:full` is the only route
to a terminal `verify` run.

## Evidence

Probes were run on the dispatch branch before any edit was attempted:

| Probe | Result |
| --- | --- |
| `java -version`, `ls /usr/lib/jvm`, `command -v adb emulator sdkmanager`, `$ANDROID_HOME`/`$ANDROID_SDK_ROOT`, `~/.gradle` | absent — no JDK, no Android SDK, no adb, no emulator, no Gradle cache |
| `apt-get install -s openjdk-17-jdk-headless` | `E: Unable to locate package` |
| `curl https://services.gradle.org/distributions/` | `000` — Gradle distribution unreachable |
| `curl https://repo1.maven.org/maven2/` | `000` — Maven Central unreachable |
| `curl https://dl.google.com/android/repository/repository2-1.xml` | `000` — Android SDK and Google Maven unreachable |
| `curl https://storage.googleapis.com/` | `000` — unreachable |
| `curl https://api.github.com/`, `https://registry.npmjs.org/`, `https://pypi.org/` | `200` — only the GitHub API and package registries are reachable; none provides a verified Android build |
| `ls /dev/bus/usb`, `lsusb` | absent — no USB or device bus |
| `gh workflow run diagnose.yml --ref arena/01a0fd0b-appt -f mode=samsung-unit` | `HTTP 403: Resource not accessible by integration` |
| `gh api -X POST repos/anthracite-labs/AppT/issues/145/comments` | `HTTP 403: Resource not accessible by integration` (this record therefore travels as a draft pull request) |
| `command -v gcloud firebase docker`, `ls app/google-services.json .firebaserc firebase.json backend/.env*` | absent — no provider CLI, no credentials, no provisioned environment |
| `ls .github/workflows` | only `agent-control`, `codeql`, `diagnose`, `maintenance`, `verify` — no deployment/WIF workflow exists |
| `grep -rn 'rotationKeepsSession\|rediscoverSameUuid\|reconnectBackoff'` over `app` and `samsung` | none of the S06 named tests exist yet; `SessionState.Reconnecting` exists in the model while `LiveSession.kt` documents "It never reconnects automatically. Supervised reconnect is S06." |

No local Android compile was attempted, because the toolchain and every artifact
host are unreachable. Under `docs/BUILD.md` ("Where the Android verification
runs") the narrowest remaining route is the hosted diagnostic, which requires
the decision below before implementation work could be verified at all.

## Smallest decisions needed

1. **S06 evidence route (contract revision).** `slices.md` S06 names an
   instrumented rotation test and a scripted LAN-flap run, but the accepted
   route has no instrumented/connected execution path and Arena has no device.
   Choose either:
   (a) an S05-style revision: an exact-head, debug-only in-app S06 evidence
   surface built from the trusted `android-build` diagnostic artifact, plus the
   human-run physical checkpoint, so the tester needs no JDK, Gradle or adb
   (the pattern PR #118 used for the S05 p50 measurement); or
   (b) name who executes `connectedAndroidTest` on which device and toolchain
   and record that as the instrumented-evidence route, accepting that Arena
   cannot run it.
2. **S07 provider provisioning.** The owner/admin creates the three
   environments (projects, Firestore rules and index targets, functions,
   Secret Manager marker keys, KMS keys, App Check certificate registrations,
   Play product and licence tester, RTDN topic, signing keys, WIF bindings) and
   records the resulting fingerprint and ID set. Arena can then implement the
   deployment and release workflows inside that boundary without holding
   long-lived credentials.
3. **S08/S10/S17 owner-run steps.** Confirm the provider and human steps (first
   backend deployment and rollback drill; paid-path production drill;
   source-license and vendor-terms decisions) so those checkpoints have a
   reachable evidence route.
4. **Program packaging.** Decide whether the contiguous S06→S17 package stays
   as-is (it cannot complete in an environment without device or provider
   access) or is re-scoped so Arena executes only checkpoints whose verification
   is reachable, with owner gates as separate milestone-bound checkpoints.

Once (1) and (2) are decided, Arena can resume on this branch immediately:
implement S06, obtain hosted `samsung-unit` / `app-unit` / `android-static` /
`android-build` feedback through the label bridge, and leave only the authorized
human, physical and provider steps outstanding.

## Work already completed

Repository, contract and architecture reading plus route probing only. No
repository source change was made and no candidate was produced; the branch
contains only this exception record. Nothing is reported as verified, and no
physical, provider, legal or licensing evidence is claimed.

## Current verification state

Accepted baseline: S05 (`bd0474ac`), control-plane merge `adf18d6`. No candidate
exists on this branch, so no `verify / gate` run exists and none was requested.
The S06 acceptance tests (`reconnectBackoff`, `rediscoverSameUuid`,
`graceClosesAfterFifteenSeconds`, `backgroundReleasesRemote`,
`networkLossShowsReconnectingNotDialog`, `vpnDoesNotBypass`,
`rotationKeepsSession`, `recreationRestoresRouteAndDrafts`,
`recreationDropsTransientUiOnly`) do not exist and are not claimed.
