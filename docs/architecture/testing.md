# Testing architecture

Test external behavior at the highest useful seam. The two primary seams are `SamsungTvs` for television control and `Licensing` for account, trial, and entitlement behavior. Tests that only survive by reading private internals are in the wrong place.

## Responsibilities

| Layer | Proves | Does not prove |
|---|---|---|
| JVM / Robolectric | ViewModel state, gate decisions, proof verification, time model, redactor, session machine through `SamsungTvsImpl` with fake transports, Room/DataStore migration, Compose state/semantics/layout contracts | Real television/provider behavior or exact-device Android lifecycle behavior |
| Protocol contract | Fixture frames produce the specified snapshot and, inside `samsung` tests, the specified outbound frames | That every television generation works |
| Backend function tests | Endpoint authorization, marker logic, purchase verification against a fake Play verifier, RTDN idempotency, retention rules, deny-all client access | Google's actual API behaviour |
| Stock debug verifier | The exact debug APK exercises/guides Android lifecycle, network transitions, real Keystore, permissions, Credential Manager, physical Samsung behavior, accessibility walkthroughs, upgrade state, and other on-device evidence required by S06–S17 | Provider-console configuration, Play signing/production purchase truth, or human/legal decisions |
| Compose | Critical flows under JVM/Robolectric against fake `SamsungTvs` and fake licensing: first control, exemption, reconnect status, hidden dead controls, no address on cards, gate surfaces | Wire format or exact-device rendering |
| Accessibility | JVM/Robolectric semantics/layout contracts plus the stock-debug guided TalkBack, 200% font, rotation/window-size and screenshot walkthrough | Visual taste |
| Performance | Focused code-path tests plus the stock-debug verifier on the exact candidate when device timing matters | Product desirability or synthetic hosted-emulator timing |
| Physical matrix | One real television per generation actually claimed, recorded by the stock-debug verifier | A predeclared year range |

`app` tests replace `SamsungModule` and the licensing seams with fakes. They assert on snapshots and results. They do not assert `KEY_*` or ports.

`samsung` contract tests construct `SamsungTvsImpl` with a fake session transport that replays a fixture and records writes. Asserting outbound frames there is correct. Those tests are not on the `app` classpath.

## One stock debug verification surface

AppT has **no Android instrumentation acceptance tier** for the remaining V1 lifecycle. The production modules do not keep `src/androidTest/**` suites, an instrumentation runner, connected-test acceptance tasks, a Gradle Managed Device, or a second test/benchmark application.

The only on-device engineering verifier is the normal stock debug APK produced by the existing trusted `android-build` route. Extend the existing S05 debug verifier into one **AppT Verification** surface under `app/src/debug/**`; do not create per-slice verifier apps, activities, modules, workflows, or APKs.

The verifier is an engineering surface, not a V1 customer feature:

- it is compiled only into the debug source graph and an executable release-exclusion guard proves that no verifier route, type, string, scenario driver, or entry point reaches internal/production release artifacts;
- physical checks use the normal production `app -> samsung` path and the real production adapters. The verifier may observe bounded debug counters/events but may not own a second `RemoteSession`, call a second `SamsungTvs.open()`, bypass the licensing gate, or create a production fake-TV mode;
- deterministic app/provider lifecycle scenarios that cannot be forced safely on a real provider may use debug-only scripted seam controls, and every such result is labelled `scripted`. A scripted result never substitutes for a required real Samsung, Play, Firebase, signing, or provider-console gate;
- one exact-build report covers S06–S17 device evidence. It may contain the candidate SHA, app version/build type, phone model, Android/API version, non-sensitive environment label, relative timings, per-checkpoint PASS/FAIL/PENDING status, and the physical-matrix fields already allowed below. It must never contain an address, MAC, token, pin, certificate material, SSID, account email/uid/Username, purchase token, entered text, or arbitrary payload;
- the report is local and explicit-copy/share only. There is no upload endpoint, analytics path, background exporter, or provider credential in the verifier;
- provider-console, Play-signing, live production-purchase, source-license, and Samsung vendor-terms evidence remains owner/provider/human evidence. The verifier records those items only as `PENDING_EXTERNAL` or as a safe app-side observation; it never self-certifies them.

This is the single physical/device evidence route for S06–S17. JVM/Robolectric, protocol, backend, repository-policy, security, and terminal CI checks remain separate because they prove deterministic code and supply-chain properties more cheaply and precisely than a phone walkthrough.

## Fake adapters

Internal, `samsung` test source set:

- Discovery transport emits scripted candidates, including a soundbar and an unsupported handshake.
- Session transport scripts TLS identity, inbound frames, delays, and closes.
- Secret store is in-memory, with a mode that fails decryption.
- Wake sender records bursts and transmits nothing.
- Clock advances backoff without sleeping.

`app` fakes:

- `SamsungTvs` fake: scripts discovery cards, session states, capability sets, command results, and remember/forget outcomes. Has no WebSocket type.
- `AccountAuth` fake: scripts Google and email sign-in, verification state, token refresh failure, and sign-out.
- `EntitlementBackend` fake: scripts entitlement snapshots, trial eligibility, purchase verdicts (`granted`, `pending`, `bound_elsewhere`, `rejected`), key sets, outages, and latency.
- `PlayBilling` fake: scripts purchased, pending, acknowledged, replayed-token, and restored purchases.
- `IntegrityProvider` fake: scripts strong, basic, failed, and unavailable verdicts.
- Clock and storage fakes for the entitlement cache.

## Fixtures

Location: `samsung/src/test/resources/samsung/fixtures/<case-id>/`.

`provenance.json`:

```json
{
  "caseId": "tls-approval-then-volume",
  "captured": "YYYY-MM-DD",
  "generation": "tizen-websocket-tls",
  "source": "lab-tv",
  "redaction": "complete",
  "notes": "what the case proves"
}
```

No host, MAC, token, serial, account, or personal email. `source` is a role (`lab-tv`, `contributed`), not a person. `generation` is a label assigned after capture, not a year-range claim.

`trace.jsonl` lines:

```json
{"tMs":0,"dir":"in","kind":"ws-text","body":{"event":"ms.channel.connect","data":{"token":"[fixture-token]"}}}
```

Directions are `in` and `out`. Times are relative milliseconds. Tokens in committed files are the placeholder `[fixture-token]`. The test injects a known token when it needs to prove the token was or was not written to the socket.

Certificate fixtures are generated in-repo test certificates. Do not commit a captured television certificate; a captured certificate is a device identifier. CI rejects a fixture file containing an IPv4 address, a MAC, or a raw `"token": "<not a placeholder>"` pattern.

Minimum fixture set before the matching slice can be called done:

| Case | Proves |
|---|---|
| `tls-approval-then-volume` | Approval, the approval token received as transient live-session evidence, the volume frame written, state `Ready` |
| `token-resume` | Second connection sends the token and does not require approval |
| `identity-mismatch` | Different SPKI, token absent from the recorded URL, state `NeedsRepair` |
| `unauthorized-with-token` | `TokenRejected`, no reconnect loop |
| `approval-timeout` | `ApprovalTimedOut` at 45 seconds of fake clock |
| `malformed-frame` | Oversize or broken JSON does not throw to the caller |
| `reconnect-backoff` | Delays follow the schedule, then `Unreachable` |
| `rediscover-same-uuid` | New address accepted only when UUID matches and pin matches |
| `unsupported-no-keys` | Non-adopted handshake emits `Unsupported` and writes no key frame |
| `app-list` | Only returned apps become `LaunchableApp` |
| `text-rejected` | `textInput` becomes false and the text is absent from diagnostics |

A slice that needs a missing fixture captures a redacted trace or generates a synthetic trace whose provenance says `synthetic`. Synthetic traces are valid for unit contracts. They do not count as a physical-matrix row.

Backend fixtures are JSON request/response pairs under `backend/test/fixtures/`, including a `ProductPurchaseV2` standard purchase, a `ProductPurchaseV2` licence-test response carrying `testPurchaseContext`, a pending purchase, a revoked purchase, RTDN one-time-purchased, one-time-canceled, and voided-purchase messages, and a duplicate-delivery pair.

Backend checks run from the repository root and always name the package: `npm ci --prefix backend`, `npm run typecheck --prefix backend`, `npm run lint --prefix backend`, `npm test --prefix backend`, and `npm run test:emulator --prefix backend`. No documented backend command requires the caller to change directory first, and no command is written without the `--prefix backend` target.

## Required behavioral contracts

These names are the contract. Implementation may split them; it may not drop the assertion.

### Television control

| Test | Assertion |
|---|---|
| `cloudAbsenceDoesNotBlockCommand` | `Ready` fixture accepts a command with Firebase classes absent from the `samsung` runtime |
| `samsungGraphExcludesFirebase` | Gradle dependencies of `:samsung` exclude Firebase, Play services, and every telemetry SDK |
| `identityMismatchDoesNotSendToken` | Recorded socket URL carries no token |
| `discoveryEndsAtBound` | Fake clock at 10 seconds yields `Finished` and the multicast lock is released |
| `v1ProbesDoNotRequestNearbyWifiDevices` | Target 36 discovery requests neither `NEARBY_WIFI_DEVICES` nor location for SSDP, raw sockets, or `NsdManager` |
| `secondDiscoverCancelsFirst` | One scan owner at a time |
| `malformedFrameDoesNotEscapeSession` | No exception reaches the caller; the session continues or reconnects |
| `stableIdentitySurvivesRediscovery` | A television with a stable identity returns with the same `TvId` after an address change |
| `mintedIdentityIsLocal` | A television without a stable identity keeps a device-local `TvId` and never claims otherwise |
| `forgetRemovesSecret` | After `Forgotten` the secret file is gone and a second `forget` still returns `Forgotten` |
| `noPlaintextTokenAfterTlsPairing` | A television that completed TLS pairing never receives its saved token over the plaintext channel |
| `holdCancellationReleases` | A cancelled `Hold` still writes release |
| `noBackendCallOnCommandPath` | No entitlement, Auth, or diagnostic-recording call occurs between input and socket write |

### Licensing and the account gate

| Test | Assertion |
|---|---|
| `accountlessFirstSessionIsExemptOnce` | The first session opens with no account; after it ends, the next entry requires sign-in |
| `firstSessionGateEndsWithActiveRemote` | The first `Accepted` does not interrupt the exempt session; after that holder closes, the next entry does not call `SamsungTvs.open` without a user |
| `gateRunsOnlyOnEntry` | Rotation, resume, reconnect, and sheet dismissal never re-evaluate the gate |
| `entitlementRequiresVerifiedEmail` | An unverified email/password account cannot activate a trial |
| `integrityRequestHashBindsDecision` | A Standard Integrity token for different canonical request fields is rejected before trial or purchase state changes |
| `trialStartsServerSideAndSevenDays` | Activation writes `expiresAt` from the server timestamp and grants exactly seven days |
| `trialFollowsAccountAcrossPhones` | A second device receives the original expiry and creates no new window |
| `deviceMarkerDeniesSecondTrial` | A second account on the same device signal is not trial-eligible |
| `trialMarkerKeyRotationResolvesOldMarkers` | A marker written under a previous key version still denies a new trial with no raw value stored |
| `testPurchaseDoesNotGrantLifetime` | A `ProductPurchaseV2` licence-test response carrying `testPurchaseContext` never grants a durable Lifetime Entitlement in any environment; promo/rewarded acquisition is not enabled in V1 |
| `trialAttachIsIdempotent` | Refresh from a second phone returns the original expiry, creates the device marker once, and never extends the window |
| `deletionFreezesBeforeAuthDelete` | A binding is `frozen` before the Auth user is removed, and `deletion_pending` denies use in that state |
| `deletionRetryConverges` | A retry after any phase completes the deletion exactly once, and the binding becomes re-bindable only after Auth deletion |
| `frozenBindingRetainsOwnerCorrelation` | Freezing drops `boundUid` and writes the same random `deletionId` on the binding and the account record in one transaction, so the reconciliation lookup is defined without retaining an identifier |
| `reconciliationRequiresAuthRemoval` | A frozen binding whose account still has an Auth user is not released: the account stays `deleting`, the binding stays `frozen`, the correlation is kept, and the stuck-deletion alert fires |
| `releaseRequiresAuthRemovalProof` | No release with `releaseReason = "accountDeleted"` succeeds while `authRemovalConfirmedAt` is null, in the delete endpoint and in the reconciliation job |
| `reconciliationReleasesOrphanedBindings` | A frozen binding whose `deletionId` names a `deleting` account whose Auth user is gone is released by the scheduled job alone, and the release records the proof |
| `provisionalUsesLocalKey` | The provisional record holds the device-computed key, never the server fingerprint and never a raw token |
| `onePurchaseBindsToOneLiveAccount` | Verification from a different live account returns `bound_elsewhere` |
| `restoreAfterAccountDeletionSucceeds` | After deletion the same Play purchase binds to the recreated account |
| `revocationAppliesOnNextEntry` | A revoked binding denies the next entry and leaves an active session untouched |
| `provisionalIsNonRenewable` and `provisionalExpiresWithin24Hours` | A provisional record cannot be re-granted for the same token and expires at or before 24 hours |
| `proofSignatureTamperDenied` | A modified proof payload is rejected |
| `proofForAnotherUidIgnored` | A valid proof for a different uid grants nothing |
| `clockRollbackDoesNotExtendEntitlement` | Adjusted time never moves backwards and no window extends |
| `paidOfflineControlSurvivesOutage` | A cached lifetime proof permits entries while the backend is unreachable |
| `trialExpiryDoesNotInterruptSession` | Expiry during an active session changes nothing until the next entry |
| `signOutKeepsLocalStateAndCachedProof` | Sign-out blocks new entries and clears neither television state nor the cached proof |
| `backendRejectsClientSuppliedUid` | A request body containing a uid field is rejected |
| `clientFirestoreAccessDenied` | Rules deny client reads and writes to every backend collection |

### Local data and persistence

| Test | Assertion |
|---|---|
| `schemaContainsNoForbiddenColumn` | The exported schema has no forbidden column name |
| `roomMigrationEveryVersion` | A migration test exists for every schema version after 1 |
| `forgetRemovesRowAndFavouritesInOneTransaction` | The confirming tap removes the profile and its favourites together and leaves one `PendingForget` row; no intermediate state re-exposes the television |
| `forgetIsRetryable` | A failed `samsung.forget` keeps the `PendingForget` row and the retry, and the television stays out of every list |
| `newPairingSupersedesPendingForget` | A session reaching `Ready` for a pending id clears the marker and never deletes the fresh pairing |
| `favouriteReorderIsAtomic` | A reorder commits in one transaction and survives process death |
| `dataStoreMigrationKeepsValues` | Renamed keys keep values and delete the old key only after a successful read-back |
| `corruptSecretDoesNotResetPairing` | An undecryptable secret yields `SecretsUnavailable`, not an empty pairing |
| `corruptEntitlementCacheDoesNotDeleteTvState` | Discarding the cache leaves Room and Samsung files untouched |
| `backupExcludesAllTvState` | Backup and device-transfer configuration cannot carry television or entitlement state |
| `noSyncTypesExist` | Production sources contain no sync record, tombstone, outbox, or version-tuple type |
| `nameSourceUserIsNotOverwritten` | A television-reported name never overwrites a user edit |
| `lastOpenedIsDeviceLocal` | Last-used reopen reads only device-local state |

### Lifecycle and network

| Test | Assertion |
|---|---|
| `rotationKeepsSession` | A rotation recreates the Activity; the session survives, no reconnect or gate evaluation happens, and no second `open` is issued |
| `recreationRestoresRouteAndDrafts` | Route, `tvId`, list scroll, and a rename draft survive recreation |
| `recreationDropsTransientUiOnly` | An open sheet, edit mode, and an in-flight gesture reset on recreation, and nothing else does |
| `graceClosesAfterFifteenSeconds` | With a fake clock, the session closes at 15 seconds and not before |
| `backgroundReleasesRemote` | `onStop` starts grace; returning inside grace reuses the same session |
| `processDeathRequiresNewEntryWhenFirstControlDone` | After a first `Accepted`, process death leads to the account gate on the next entry |
| `processDeathDoesNotForceRepair` | A remembered television reopens with its saved secret and no re-pair prompt |
| `networkLossShowsReconnectingNotDialog` | Socket loss produces `Reconnecting` and no modal |
| `reconnectBackoff` | A dropped socket produces the documented attempt count within the 45-second budget and stops at `Unreachable` |
| `rediscoverSameUuid` | An address change is resolved by internal rediscovery of the saved identity with no scan UI |
| `vpnDoesNotBypass` | With a VPN-only route, results are `Unreachable` or `LocalNetworkDenied` and no bypass path exists |
| `workManagerNeverTouchesSamsung` | The refresh worker holds no reference to `SamsungTvs`, `RemoteSession`, or a discovery flow |

### Privacy, security, and diagnostics

| Test | Assertion |
|---|---|
| `redactedReportContainsNoFixtureSecret` | A planted token, pin, IP, MAC, email, Username, and text are absent from the record, the rolling file, and the export |
| `noTelemetryDependency` | No crash-reporting, analytics, advertising, or attribution artifact appears in the merged graph |
| `localRecordIsBoundedAndRedacted` | Both sources cap at 200 events, the rolling file caps at its bound, and every stored field is on the allowlist |
| `diagnosticFileIsExcludedFromBackup` | Backup and device-transfer rules exclude the diagnostics path |
| `exportRequiresUserConfirmation` | No report leaves the phone before the preview is shown and the user confirms |
| `clearLocalHistoryDeletesRecordAndFile` | Both sources and the rolling file are empty afterwards |
| `noDiagnosticsUploadPath` | The diagnostics package contains no HTTP client and the backend endpoint inventory contains no diagnostics endpoint |
| `adIdAbsentFromManifest` | `AD_ID` does not appear in the merged manifest |
| `samsungHasNoLogCalls` | `samsung` production sources do not call `android.util.Log` |
| `backendStoresNoTelevisionField` | Backend sources and rules contain no forbidden field name |
| `supportResetIsAuditable` | A marker clear writes a `supportActions` entry and no raw value |

### Presentation and accessibility

AppT does not use the platform instrumentation-only accessibility validator as an acceptance dependency. JVM/Robolectric Compose tests own semantics, labels, state, touch-target, scaling, reduced-motion and traversal contracts; the stock debug verifier owns the real-device TalkBack, 200% font-scale, contrast/screenshot and responsive-window walkthrough on the exact candidate.

| Test | Assertion |
|---|---|
| `accessibilityContractsPass` | JVM/Robolectric Compose coverage exercises the explicit accessibility contracts on every shipped route, and the exact-build debug-verifier walkthrough records the real-device TalkBack/rendering checks required by S14 |
| `launchRoutingResolvesRemoteFirst` | With a remembered television and allowed access, launch resolves to `Remote` and no dashboard exists |
| `gatedLaunchNeverOpensSession` | A blocked launch destination is Account or Entitlement and `SamsungTvs.open` is not called |
| `remoteShowsNoLicensingPrompts` | No purchase, trial countdown, or account prompt composable appears on the Remote surface |
| `unsupportedCardHasNoControlAffordance` | An `Unsupported` card exposes no command control |
| `everyControlMeetsTouchTarget` | Each control a slice adds asserts at least 48dp touch bounds |
| `fontScale200DoesNotClip` | Each route renders without clipping or overlap at 200% font scale |
| `statusIsNotColorOnly` | Every status composable exposes text plus icon |
| `reducedMotionSkipsTravel` | With animations disabled, no travel animation runs |
| `gestureAlternativesExist` | Reordering has Move earlier / Move later actions; touchpad has key navigation |
| `destructiveActionsConfirm` | Forget, sign-out, and account deletion each require an explicit confirmation |
| `statusAnnouncedOnce` | A status change produces one polite announcement, not a repeated one |

### Performance and reliability

| Test | Assertion |
|---|---|
| `launchPathRemainsNonBlocking` | Launch routing and first usable UI do not wait on backend/network work; exact-device timing is recorded only when a slice needs it |
| `s05PhysicalLatencyVerifier` | Five warmed input-to-local-socket-write samples on the exact candidate satisfy the S05 p50 target and report p95 |
| `interactiveSurfaceRemainsResponsive` | Compose/state tests prove immediate local feedback; device frame timing is a slice-owned diagnostic when required |
| `commandDoesNotAwaitDiagnostics` | A full diagnostic buffer never delays a command |
| `reconnectRecoveryRate` | ≥ 90% of scripted single drops recover inside the budget |

## Whole-application lifecycle E2E

JVM/fixture/Compose, backend, stock-debug, performance, provider, and physical evidence remain necessary, but they do not by themselves prove that navigation, persistence, process recreation, account gating, billing reconciliation, and recovery compose into one usable application.

### IMPLEMENTATION EVIDENCE

[Home Assistant Android](https://github.com/home-assistant/android) at [`d120c7dc`](https://github.com/home-assistant/android/commit/d120c7dcd0683e163a2379883ba9299af071e1cb), Apache-2.0, is the complete-app reference for journey-level Android E2E. Its `.github/workflows/e2e.yml` drives `.maestro/onboarding.yaml` across emulators and captures device logs/artifacts on failure. Method: **BEHAVIORAL REFERENCE**.

AppT harvests the journey structure and failure-artifact discipline, not the emulator/Maestro runner. S15 executes the required journeys through the same stock debug verifier used by the other device checkpoints; there is no second E2E app, connected-test runner, or emulator acceptance pipeline.

### Required journeys

1. **First use:** fresh local state → Welcome → LocalNetwork → discovery fake/fixture → pairing → first `Accepted` → leave Remote → next entry reaches Account.
2. **Daily control:** remembered TV → gate allows → remote-first launch → command → network drop → reconnect → switch TV → return.
3. **Forget safety:** remembered TV with favourites → confirm Forget → TV disappears atomically → failed Samsung forget leaves retry marker → re-pair supersedes stale pending forget.
4. **Purchase interruption:** start purchase → observe `PENDING` or background the process → transaction becomes `PURCHASED` while AppT is inactive → reopen → BillingClient reconnect/query → one authoritative grant.
5. **Upgrade in place:** install source artifact with representative local TV/account/entitlement state → install candidate over it → migrate → launch → pairing/entitlement remain correct or fail through the documented recovery state.

For a journey failure, the debug verifier retains at minimum: ordered step trace, bounded typed checkpoint state, candidate SHA/build identity, and the scripted/provider/physical environment label. A tester may attach a screenshot when the failure is visual. No adb, instrumentation runner, or exported logcat is required. These artifacts are local engineering diagnostics, not product analytics.

### Cross-contract regression names

| Test | Assertion |
|---|---|
| `ipv4MappedIpv6DedupsSameCandidate` | IPv4 and its IPv4-mapped-IPv6 representation cannot create two discovery identities/cards |
| `supersededConnectCannotResurrectSession` | A late result from an invalidated connection generation cannot publish Ready or persist older evidence |
| `pendingPurchaseCompletesWhileProcessDead` | Foreground purchase query recovers a transaction that completed while AppT was dead |
| `billingReconnectRequeriesPurchases` | Re-establishing BillingClient with a pending/unknown purchase triggers current-purchase reconciliation |
| `duplicatePurchaseObservationIsIdempotent` | Listener, query, and backend/RTDN convergence cannot create two grants |
| `upgradeFromPreviousReleasePreservesLocalState` | Supported install-over-install migration preserves valid TV/pairing/preferences and entitlement behavior |

## Physical evidence cadence

Physical evidence is staged where hardware uncertainty becomes load-bearing rather than postponed to release closure:

- **S04 acceptance:** one real Samsung TLS-token path covers discovery → TV approval → command → app restart → token resume without re-prompt; the recorded row also confirms the persistent security identity used by the candidate or records the hardware limitation honestly.
- **S06 acceptance:** on the same or another documented Samsung set, the stock-debug S06 verifier records one real LAN drop/recovery plus the guided process/Activity lifecycle walk and proves the fake transport did not hide a reconnect/lifecycle incompatibility.
- **S12 acceptance:** the stock-debug verifier records one real wake attempt; failure is acceptable and becomes honest capability evidence.
- **S17:** the stock-debug verifier emits the consolidated physical-matrix row and repeats the critical TLS-token path on the final candidate where the canonical release contract requires it; provider/human external gates remain separately recorded.

The already accepted S03 implementation is not retroactively invalidated. The S04 physical checkpoint re-exercises S03's discovery/pair/first-command path before saved pairing can be accepted.

## Room migrations

Once a second schema version exists, migrations run as Robolectric/JVM tests against the previous exported schema. Destructive fallback is a test failure if it appears in production source; upgrade-in-place behavior on a real phone is recorded by the stock-debug S16 verifier.

## Compose coverage

Welcome to explanation to cards; card has no IP text; approval state; one command; reconnecting status is not a dialog; rejected key disappears; the account requirement is not applied before the first `Accepted`; after the exempt session closes, a new entry with no signed-in user routes to Account before `SamsungTvs.open`; the entitlement surface shows Buy once, Restore purchase, and an honest backend-error state that is explicitly not a television error.

## Backend tests

| Area | Tests |
|---|---|
| Authorization | Missing or invalid ID token rejected; a uid in the body is rejected; App Check required |
| Rules | Client reads and writes denied for every collection, including a hard delete |
| Trial | Eligible activation; email-marker denial; device-marker denial; idempotent re-activation; attach on an active trial creates one device marker and returns the original expiry; attach is idempotent; attach never extends the window; unverified email denial; unverified-email normalization |
| Markers | No raw value stored; key rotation lookup across versions; support clear writes an audit record |
| Purchase | Standard `ProductPurchaseV2` granted and acknowledged; pending grants nothing; `testPurchaseContext` grants nothing; promo/rewarded acquisition is not enabled in V1; package/product mismatch rejected; second live account `bound_elsewhere`; replay resolves to the existing binding; raw token absent from all stored documents and logs |
| Revocation | Voided-purchase and one-time-canceled handlers revoke; duplicate delivery is idempotent; out-of-order delivery converges |
| Deletion | Phase order is mark, freeze, delete Auth, release, finish; the binding is frozen before the Auth user is removed; freezing writes one `deletionId` across the binding and the account; a frozen binding denies with `deletion_pending`; an `accountDeleted` release cannot happen without `authRemovalConfirmedAt`; a retry after any phase converges; the reconciliation job releases only frozen bindings whose Auth user is confirmed gone and alerts instead of releasing while the user still exists; a frozen binding naming no account is alerted, not released; markers retained; no endpoint accepts a request from an account in `deleting` state |
| Retention | Stored fields are limited to the documented shapes; a schema test fails on a forbidden field name |

Backend tests run against the Firebase emulator suite with a fake Play verifier and a fake integrity decoder. No live Google API is called in CI.

## Environment tests

| Test | Assertion |
|---|---|
| `debugVariantCannotReachProduction` | A debug build cannot resolve production backend or Firebase identifiers |
| `releaseVariantCannotReachDevelopment` | A release build cannot resolve development identifiers |
| `noCredentialFilesInRepo` | Secret scanning finds no service-account key, signing key, upload credential, keystore, or certificate file in the tree |
| `releaseArtifactsDifferOnlyByConfig` | The internal and production release artifacts from one commit are compared after signature material is stripped: the only entries that differ are the environment configuration files, in either direction, neither contains the other's configuration, and any other difference — a version code included — fails the comparison |
| `releaseVariantsShareVersionCode` | The internal and production release artifacts from one commit carry the same `versionCode` and `versionName`, and both are higher than the previous release's |
| `signingIdentityMatchesChannel` | The tester artifact carries the internal certificate, the bundle uploaded to Play carries the upload certificate, the two differ, and neither is a debug certificate |
| `appCheckRegistrationMatchesChannel` | The App Check registration fingerprint recorded for `appt-prod` is the Play app-signing certificate, the one recorded for `appt-internal` is the internal certificate, and no fingerprint appears in more than one environment |

## Physical acceptance matrix

Not a fixed year list. A row is added when a television is actually observed. Absence of a row means "not claimed", not "unsupported by year".

Columns: generation label assigned after the session, TLS or plaintext, token required, pair result, command result, reconnect result, process-death resume, wake result, text result, apps result, pointer result, pin survived reboot (yes/no/not tried), date, pass/fail.

Before a public production rollout, the matrix needs at least one passing row for the TLS token path covering pair, command, reconnect, and process-death resume, plus a recorded wake attempt (pass or honest failure). Further rows are added as televisions are available. They do not block the architecture map.

The matrix is an internal release artifact. The stock-debug verifier may emit the row as engineering evidence, but no release/customer build contains an adapter-health or compatibility screen. A public device-support page remains out of V1.

Do not write raw addresses, MACs, tokens, or account emails into the matrix. Model and firmware are allowed.
