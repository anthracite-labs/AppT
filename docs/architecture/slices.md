# Implementation slice map

Architecture output only. **Do not open implementation Issues from this file.** Accepted S01–S03 history comes from the earlier route; Issue #96 reconciled the future S04–S17 route against the full application lifecycle and evidence standard. ChatGPT compiles one authorized slice at a time into an Arena Issue, and each dispatch names this file, the slice, and that slice's architecture sources.

Every slice is vertical: a user or tester can observe the outcome, and CI proves it, without waiting for a later slice to make the earlier one real. Security, privacy, accessibility, and reliability constraints appear in the slice where they first become real, not in a final cleanup slice.

Not in this route: iOS, a second television ecosystem, IR, casting, mirroring, voice, advertising, a subscription, a diagnostic upload service, cloud crash reporting, a paid-device roster, a public compatibility page, or TV-personalization cloud sync of any kind.

External gates, not slices: the source-license decision and the Samsung vendor-terms review. Both gate public production promotion only; internal testing does not wait on them.

## Definition of V1-ready

V1-ready for public production means S01 through S17 are accepted, the two external gates are passed, at least one physical matrix row passes the TLS token path with a recorded wake attempt, and the recorded paid-path drill in S10 has run on production.

## Ordering rules

Four rules shape the future route while preserving accepted S01–S03 history:

1. The licensing gate is part of the spine, not an appendix. Account and trial (S08) land before any post-first-session route can open a television.
2. The ordinary daily-control lifecycle is completed before monetization deepening. Remembered televisions, remote-first launch, switching, rename, and the user-facing Forget transaction are S09; lifetime purchase/restore/refund binding work follows in S10. This keeps the product usable and recoverable before the most operationally complex backend path is added.
3. Diagnostics recording starts early. S05 lands the bounded redacted local recorder needed to diagnose control/reconnect/backend development; S13 later adds the user-facing preview/share/clear and Request Support surfaces.
4. Hardware uncertainty is retired where it becomes load-bearing. S04 physically re-exercises discovery → approval → first command → restart/token resume, S06 physically exercises reconnect/lifecycle, S12 records a wake attempt, and S17 consolidates release-candidate physical evidence. S03's accepted implementation history is not rewritten; the S04 checkpoint covers its real-TV path before saved pairing can be accepted.

Foundations still precede provider-backed verification: S07 creates environment/signing/backend/Play foundations before S08 and S10 need real infrastructure. S16 owns release/upgrade hardening once a candidate exists.

## Route

```mermaid
flowchart TD
  s01[S01 Skeleton and CI floor]
  s02[S02 Discovery]
  s03[S03 Pair and first command]
  s04[S04 Saved pairing and forget primitive]
  s05[S05 Remote surface, Settings, diagnostic recorder]
  s06[S06 Reconnect, lifecycle, recreation]
  s07[S07 Environments, signing, backend deployment]
  s08[S08 Account, trial, and the gate]
  s09[S09 Remembered televisions and launch]
  s10[S10 Purchase, restore, revocation]
  s11[S11 Apps, text, favourites]
  s12[S12 Wake and honest power]
  s13[S13 Diagnostics UI, export, Request Support]
  s14[S14 Accessibility and responsive]
  s15[S15 Reliability, performance, lifecycle E2E]
  s16[S16 Upgrade/release promotion hardening]
  s17[S17 Physical acceptance and gates]

  s01 --> s02 --> s03 --> s04 --> s05 --> s06
  s01 --> s07
  s06 --> s08
  s07 --> s08
  s08 --> s09 --> s10
  s10 --> s11
  s10 --> s12
  s05 --> s13
  s10 --> s14
  s11 --> s14
  s12 --> s14
  s13 --> s14
  s11 --> s15
  s13 --> s15
  s14 --> s16
  s15 --> s16
  s10 --> s17
  s12 --> s17
```

S07 may run in parallel with the control spine as soon as S01 lands, but accepted implementation remains one authorized slice at a time. S13's **recorder foundation** begins in S05; S13 itself owns the customer-facing diagnostics transaction. Public production promotion waits for S16, S17, and the two external gates.

## S01 — Walking skeleton and CI floor

Observable outcome: a debug build installs and shows Welcome. Pull-request CI is green with the repository, dependency, manifest, and telemetry guards in place, and secret scanning runs on every change. The design-token module exists with the categories from the presentation architecture, and the test-only benchmark module compiles.

Architecture sources: [presentation.md](presentation.md), [release.md](release.md), [modules.md](modules.md), [diagnostics.md](diagnostics.md).

Depends on: none.

Acceptance: Welcome route renders on a device; the navigation graph contains only `WelcomeRoute`; three Gradle modules exist (`app`, `samsung`, and test-only `:macrobenchmark`) and neither production module depends on the benchmark module (`noProductionModuleDependsOnBenchmark`); version catalog pins versions with no `+` ranges and no snapshot; repository mode fails on project repositories; lockfiles and verification metadata committed; manifest allowlist matches [release.md](release.md) exactly; actions pinned to commit SHAs; token module exposes color, type, space, size, and motion categories with the 48dp floor; `noTelemetryDependency` and `adIdAbsentFromManifest` pass; `:app` dependency insight shows no Firestore artifact; `backend/` exists with its lockfile, typecheck, lint, and test jobs wired into CI.

Verification: `./gradlew assembleDebug lintDebug testDebugUnitTest dependencyLockCheck`, `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency firestore` (no match), `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency crashlytics` (no match), `npm ci --prefix backend && npm test --prefix backend`, install the debug build, CI log shows pinned actions.

Not in this slice: Room, discovery, Firebase configuration, any backend endpoint, environment variants and guards (S07).

## S02 — Local-network explanation and bounded discovery

Observable outcome: after an ordinary-language explanation, the user sees friendly television cards and the scan ends. An unsupported Samsung fixture appears as not controllable. No address, port, or UUID is ever shown. No command is sent.

Architecture sources: [discovery.md](discovery.md), [samsung-interface.md](samsung-interface.md), [protocol.md](protocol.md), [presentation.md](presentation.md#discovery), [reliability.md](reliability.md), [flows.md — first run to first control](flows.md#first-run-to-first-control).

Depends on: S01.

Acceptance: explanation precedes any system prompt and precedes the first scan; the first scan starts as soon as the gate is granted with no separate setup step; a rescan starts a new `discover()`; target 36 probes request neither `NEARBY_WIFI_DEVICES` nor location and do not declare `ACCESS_LOCAL_NETWORK`; a system prompt appears only when a chosen API requires one; the scan finishes at 10 seconds; the soundbar fixture emits no card; the unsupported fixture emits `Unsupported` and writes no key frame; cards contain no IP text; the multicast lock is released on cancel; a card is rendered from live evidence with no parallel model table; every control this slice adds has a content description and at least 48dp touch bounds; the Discovery ViewModel depends on `SamsungTvs`, never on `samsung.internal`.

Verification: `./gradlew :samsung:test` for fixture contracts; `./gradlew :app:testDebugUnitTest` for ViewModel state; Compose test for the card and empty state; `./gradlew :app:lintDebug` for the manifest; one device walkthrough of the explanation.

Not in this slice: pairing, Room, Request Support export.

## S03 — Pair on the television and the first command

Observable outcome: the user selects a controllable card, allows AppT on the television, presses volume or a direction key, and the command is written on the already open session. The first `Accepted` records the first-control milestone that later decides the account exemption.

Architecture sources: [connection.md](connection.md), [protocol.md](protocol.md), [samsung-interface.md](samsung-interface.md), [account-entitlement.md](account-entitlement.md#remote-entry-gate), [presentation.md](presentation.md#pairing), [data.md](data.md), [flows.md — first run to first control](flows.md#first-run-to-first-control).

Depends on: S02.

Acceptance: fixture `tls-approval-then-volume` reaches `Ready` and records a volume write; `command` does not open a second socket; `:samsung` has no Firebase or Play dependency; `cloudAbsenceDoesNotBlockCommand` and `noBackendCallOnCommandPath` pass; a malformed fixture does not crash the caller; the Pairing surface shows the approval meaning and no port, certificate, or generation; selecting a controllable card inserts the `TvProfile` row; `firstControlAchieved` is written on the first `Accepted`; no account prompt appears during this session.

Verification: `./gradlew :samsung:test`, `./gradlew :app:testDebugUnitTest`, Gradle dependency check, Compose test from card to `Accepted` using the fake `SamsungTvs`.

Not in this slice: saved pairing, full remote layout, account, remembered television list.

## S04 — Saved pairing, fail-closed identity, and the safe forget primitive

Observable outcome: after approval, force-stopping and reopening the same television does not prompt again. A television presenting a different security identity never receives the token and asks for explicit re-pair. The safe forget primitive deletes the television's secret, is idempotent, and is verified at the `SamsungTvs` seam; the user-facing Forget transaction lands with the management surface in S09.

Architecture sources: [data.md](data.md), [connection.md](connection.md), [samsung-interface.md](samsung-interface.md).

Depends on: S03.

Acceptance: `token-resume` fixture passes; `identityMismatchDoesNotSendToken` passes and the recorded socket URL carries no token; `unauthorized-with-token` produces `TokenRejected` with no reconnect loop; instrumented Keystore round trip succeeds; the secret file exists only under `noBackupFilesDir/samsung-secrets/`; `forgetRemovesSecret` passes — after `Forgotten` the secret file is gone and a second `forget` still returns `Forgotten`; backup and device-transfer configuration cannot carry the secret or device directory; no plaintext token appears in Room, DataStore, or any log the test captures; `samsung` still has no Firebase dependency.

Verification: `./gradlew :samsung:test`, `./gradlew :app:testDebugUnitTest`, one instrumented Keystore test, a grep over captured logs for a planted token, and the S04 physical checkpoint: one real Samsung TLS-token path completes discovery → TV approval → first command → app restart → token resume without a second approval prompt. Record the observed persistent identity behavior without exposing token, address, MAC, or certificate material.

Not in this slice: `Favourite` and `PendingForget` schema, the user-facing Forget transaction and confirmation (S09), the television list surface, favourites UI, account. S03 already owns `TvProfile`; S04 does not pre-create tables for later behavior.

## S05 — Capability-driven Remote surface, Settings shell, and diagnostic recorder

Observable outcome: the Remote is recognizable and phone-native, and the bounded always-redacted local diagnostic recorder exists early enough to support the reconnect/account work that follows. Standard controls work, a rejected key disappears, haptics and phone volume buttons follow settings, and the D-pad/touchpad toggle appears only when the television demonstrates pointer support. The Settings screen exists with the Interaction and About sections functional, reachable from the Remote chrome.

Architecture sources: [commands.md](commands.md), [presentation.md](presentation.md) (Remote contract, Settings contract, thumb-first zones, tokens, accessibility), [lifecycle.md](lifecycle.md), [reliability.md](reliability.md), [diagnostics.md](diagnostics.md).

Depends on: S04.

Acceptance: the UI binds only to `capabilities.keys`; the four thumb-first zones are implemented as specified and power is spatially isolated; pointer toggle is absent until the fixture probe accepts; haptics default on and can be disabled; volume buttons send volume taps only while the remote is started and the setting is on; every control has a content description, a role, and at least 48dp touch bounds with primary controls at 56dp or more; no `KEY_*` string appears in the Compose tree; the Settings route renders the Interaction section (haptics, volume buttons, navigation mode) and About (app version), preference writes happen in the ViewModel and never in a composable effect, and a section whose destination does not exist yet renders no dead row — Account & License lands in S08, TVs in S09, Privacy & Diagnostics in S13; `commandLatencyBudget` p50 is inside the control-path target on the reference device; a baseline profile for Remote is committed; the app and Samsung redacted diagnostic buffers are bounded, always local, excluded from backup, and `commandDoesNotAwaitDiagnostics` proves a full buffer never delays a command. No preview/share UI exists yet.

Verification: Compose tests with the fake adapter; `./gradlew :app:testDebugUnitTest`; `./gradlew :macrobenchmark:connectedCheck` for the control-latency target; a unit test that a rejected key leaves the capability set; a Compose test for the Settings sections.

Not in this slice: reconnect behavior, the television list, favourites shelf, account, Diagnostics screen/export/Request Support.

## S06 — Reconnect, lifecycle, and Activity recreation

Observable outcome: a dropped socket shows a quiet inline status and recovers on its own; an address change recovers without the user doing anything; leaving the app and returning inside the grace window reuses the same session; rotating the phone keeps the session and the route.

Architecture sources: [connection.md](connection.md), [lifecycle.md](lifecycle.md), [presentation.md](presentation.md#rotation-window-size-foldables-and-tablets), [reliability.md](reliability.md), [flows.md — quiet reconnect and an address change](flows.md#quiet-reconnect-and-an-address-change).

Depends on: S05.

Acceptance: the backoff fixture ends in `Unreachable` and stops; `reconnectBackoff` and `rediscoverSameUuid` pass; `graceClosesAfterFifteenSeconds` and `backgroundReleasesRemote` pass; `networkLossShowsReconnectingNotDialog` and `vpnDoesNotBypass` pass; `rotationKeepsSession` passes with the Activity genuinely recreated (no `android:configChanges`, one session, no second `open`, no gate evaluation); `recreationRestoresRouteAndDrafts` and `recreationDropsTransientUiOnly` pass; commands issued while not `Ready` return `Rejected(Unavailable)` and nothing is replayed; reconnect status is never a dialog loop.

Verification: `./gradlew :samsung:test` with the fake transport, `./gradlew :app:testDebugUnitTest`, an instrumented rotation test that asserts Activity recreation, a scripted LAN-flap run, and one real-Samsung checkpoint that records a LAN drop/recovery plus leave/return or Activity recreation on the same candidate without exposing network/security identifiers.

Not in this slice: account, television list, TV switching.

## S07 — Environments, signing, and backend deployment

Observable outcome: three isolated environments exist and prove it. A debug build resolves only development identifiers; an internal tester build signed with the internal key installs outside Play and resolves only the internal environment; a production-flavoured candidate uploads to the Play internal testing track, installs against production, and is re-signed by Play App Signing. Backend rules and indexes deploy to all three projects through the CI pipeline, and the Play app carries the lifetime product with a licence tester configured.

Architecture sources: [release.md](release.md) (environments, release path and artifact identity, signing identity and distribution channel, backend deployment and rollback), [account-entitlement.md](account-entitlement.md#backend-source-architecture), [security.md](security.md#b7-ci-supply-chain-and-release).

Depends on: S01. May run in parallel with the control spine.

Acceptance: `dev`, `internal`, and `production` projects exist with separate Firebase, Firestore, functions, Secret Manager marker keys, and Cloud KMS keys; deny-all client Firestore rules and the declared indexes deploy to each environment as versioned artifacts; deployment workflows exist with Workload Identity Federation and no long-lived Google key in the repository; `debugVariantCannotReachProduction` and `releaseVariantCannotReachDevelopment` pass against the real environment configuration; the signing identities match the [release.md](release.md#signing-identity-and-distribution-channel) table — debug keystore per machine, an internal signing key whose certificate is registered only in `appt-internal`, an upload key held by CI, and Play App Signing holding the app-signing key; `signingIdentityMatchesChannel` passes; `appCheckRegistrationMatchesChannel` passes — the Play app-signing certificate fingerprint is registered in `appt-prod`, the internal certificate fingerprint in `appt-internal`, and no fingerprint appears in both; the candidate AAB installs from the Play internal testing track against production, and the tester build installs against internal outside Play, is never uploaded to Play, and needs an uninstall to coexist with a Play-signed build on the same device; the candidate-upload and tester-build workflows exist as manual dispatches, and the candidate workflow uploads the R8 mapping; the single Play catalogue contains the lifetime product with at least one licence tester, and the RTDN topic for one-time and voided events routes to the production Pub/Sub pipeline; `noCredentialFilesInRepo` passes.

Verification: CI logs, a Play internal testing install of the production candidate, an internal-environment install of the tester build, `apksigner verify --print-certs` on the tester artifact and certificate inspection of the uploaded bundle, the recorded App Check fingerprints per project, and the environment guard checks.

Not in this slice: account, trial, or purchase endpoints, RTDN handlers, the first function deployment and the rollback drill (S08), the promotion workflow (S16), public rollout.

## S08 — Account, server-authoritative trial, and the licensing gate

Observable outcome: the first successful local-control session is never blocked on sign-in. When that session ends, the next remote entry asks the user to continue, and the television is never opened while the gate denies. Google sign-in and email/password both work, an unverified email cannot start a trial, and an eligible account receives a seven-day trial with an exact remaining time in Account and Settings. Signing in on a second phone returns the same expiry and marks that phone as trial-consumed. Signing out keeps every television and the cached proof, blocks a new entry, and never interrupts an active session. With the backend unreachable, a valid trial still permits control. The account functions are the first real backend deployment, and the production rollback drill is recorded.

Architecture sources: [account-entitlement.md](account-entitlement.md) (authentication, topology, backend source architecture, API surface, record shapes, trial and attach, gate, local cache), [presentation.md](presentation.md#account), [lifecycle.md](lifecycle.md#workmanager), [release.md](release.md#environments), [flows.md](flows.md) — [first free control to the account requirement](flows.md#first-free-control-to-the-account-requirement), [account sign-in and trial activation](flows.md#account-sign-in-and-trial-activation), [trial expiry and the next remote entry](flows.md#trial-expiry-and-the-next-remote-entry), [sign-out](flows.md#sign-out), [account deletion](flows.md#account-deletion), [backend outage during control](flows.md#backend-outage-during-control).

Depends on: S06, S07.

Acceptance: `accountlessFirstSessionIsExemptOnce` and `firstSessionGateEndsWithActiveRemote` pass; `gatedLaunchNeverOpensSession` passes (`SamsungTvs.open` is not called while the gate denies); a blocked entry returns to its origin and is never resumed automatically; process death after the first success also requires sign-in; Google and email/password paths reach a signed-in state; `entitlementRequiresVerifiedEmail` passes; `trialStartsServerSideAndSevenDays` passes against the emulator suite; `trialAttachIsIdempotent` passes with the original expiry returned and exactly one device marker written; `deviceMarkerDeniesSecondTrial`, `trialMarkerKeyRotationResolvesOldMarkers`, and `trialFollowsAccountAcrossPhones` pass; `proofSignatureTamperDenied`, `proofForAnotherUidIgnored`, and `clockRollbackDoesNotExtendEntitlement` pass; `signOutKeepsLocalStateAndCachedProof` passes; `backendRejectsClientSuppliedUid`, `clientFirestoreAccessDenied`, and `backendStoresNoTelevisionField` pass; account deletion runs its account phases in order — mark `deleting`, delete the Firebase Auth user, finish — and is resumable: `deletionRetryConverges` passes for a deletion with no purchase binding present, a retry after any phase completes the deletion exactly once, only pseudonymous trial markers are retained, and the finished record drops Username, trial dates, and entitlement state; the purchase-binding freeze/release contracts and the scheduled reconciliation job land in S10 with the bindings; the Settings Account & License section is functional with the exact remaining trial time, sign-out, and account deletion behind explicit confirmation; Account surfaces never show a television error and Remote never shows a licensing prompt; `workManagerNeverTouchesSamsung` passes; the trial's remaining time is exposed to accessibility services as text, not color; production functions deploy with a traffic split from 0% and the rollback step is exercised once and recorded.

Verification: `./gradlew :app:testDebugUnitTest`; `npm ci --prefix backend && npm test --prefix backend` plus `npm run test:emulator --prefix backend` with the fake Play verifier and fake integrity decoder; one instrumented test for Credential Manager through the fake seam; one real Google sign-in and trial activation against the internal environment, proving the ID-token and App Check path end to end; Compose tests for the gated entry, the blocked-entry back path, and the backend-outage copy; the recorded rollback drill note.

Not in this slice: purchase, restore, revocation, remembered-television routing (S09), purchase/restore/revocation and purchase-binding deletion durability (S10).

## S09 — Remembered televisions, remote-first launch, TV switching, and the Forget transaction

Observable outcome: reopening AppT lands on the last-used television and starts connecting; the remembered list shows friendly names and ordinary-language state; Add TV returns to discovery; Forget from the list, behind an explicit confirmation, removes the television from this phone immediately, including its favourites, and finishes the local unpair even if `samsung` is temporarily unavailable; the switch sheet moves between remembered televisions without a swipe gesture; every one of those entries passes through the licensing gate first. The Settings TVs section opens the list.

Architecture sources: [data.md](data.md), [presentation.md](presentation.md) (launch routing, Remote, switch sheet, TvList), [lifecycle.md](lifecycle.md), [account-entitlement.md](account-entitlement.md#remote-entry-gate), [flows.md](flows.md) — [process death and reopen](flows.md#process-death-and-reopen), [switching televisions](flows.md#switching-televisions).

Depends on: S08.

Acceptance: `launchRoutingResolvesRemoteFirst` and `gatedLaunchNeverOpensSession` pass with a remembered television; `rememberedIds` behaves as specified; `forgetRemovesRowAndFavouritesInOneTransaction` passes from the list — the confirming tap removes the profile and its favourites in one transaction, leaves one `PendingForget` row, and the television is absent from every list; `forgetIsRetryable` passes — a failed `samsung.forget` keeps the row and the startup retry, and the television stays out of every list; `newPairingSupersedesPendingForget` passes; the forget confirmation states that the television is removed from this phone only; renaming writes one transaction with `nameSource = USER`, and `nameSourceUserIsNotOverwritten` passes; the switch sheet lists remembered televisions plus Add TV and contains no swipe gesture; `lastOpenedIsDeviceLocal` passes; a row whose television is offline shows an ordinary-language state and no address; backing out of a blocked entry returns to its origin (`EntryOrigin.TvList` or `Remote`); the Active Remote is released through the normal path when switching, never in parallel.

Verification: `./gradlew :app:testDebugUnitTest`, Room transaction tests, Compose tests for the list, the empty state, rename, forget confirmation, and the sheet, and one device walkthrough of reopen-to-Remote.

Not in this slice: lifetime purchase/restore/refund handling (S10), favourites UI, apps surface, wake.

## S10 — Lifetime purchase, restore, revocation, paid offline, and binding deletion durability

Observable outcome: Buy once completes a Play purchase and unlocks a lifetime entitlement; a reinstall plus sign-in restores it; a refund or chargeback blocks the next remote entry without interrupting an active one; a paid customer keeps local control while the backend is unreachable; if the validator is unavailable while Play reports a purchase, the user gets an honest temporary unlock that expires within 24 hours and cannot be renewed for that purchase. Account deletion freezes the purchase binding before the Auth user is deleted and releases it only after the removal is proven.

Architecture sources: [account-entitlement.md](account-entitlement.md) (purchase, foreground/resume reconciliation, binding, deletion durability, revocation, provisional, local cache, gate), [lifecycle.md](lifecycle.md#install-app-update-and-external-flow-return), [presentation.md](presentation.md#entitlement-and-purchase), [release.md](release.md#play-and-rtdn-are-shared-by-design), [security.md](security.md), [flows.md](flows.md) — [purchase](flows.md#purchase), [restore purchase including after account deletion](flows.md#restore-purchase-including-after-account-deletion), [restore on a second phone](flows.md#restore-on-a-second-phone), [refund or chargeback](flows.md#refund-or-chargeback), [account deletion](flows.md#account-deletion).

Depends on: S09.

Acceptance: `testPurchaseDoesNotGrantLifetime` passes for a `ProductPurchaseV2` licence-test response carrying `testPurchaseContext` in every environment; `onePurchaseBindsToOneLiveAccount` passes; a replay of the same token resolves to the existing binding with no duplicate grant; `restoreAfterAccountDeletionSucceeds` passes, and the purchase is re-bindable only after the Auth user is deleted; acknowledgement happens from the backend inside the three-day window; `revocationAppliesOnNextEntry` passes; duplicate and out-of-order RTDN delivery converge through the OIDC-authenticated subscription on the deployed handler; raw purchase tokens never appear in stored documents, logs, or the provisional record, and the provisional record uses the device-computed key (`provisionalUsesLocalKey`); `provisionalIsNonRenewable` and `provisionalExpiresWithin24Hours` pass and the Account surface labels it as temporary; `paidOfflineControlSurvivesOutage` passes; `PENDING` grants nothing and says so; `pendingPurchaseCompletesWhileProcessDead`, `billingReconnectRequeriesPurchases`, and `duplicatePurchaseObservationIsIdempotent` pass so listener/query/RTDN observations converge on one grant; account deletion with a real binding runs the full freeze-release sequence: `deletionFreezesBeforeAuthDelete`, `frozenBindingRetainsOwnerCorrelation`, `releaseRequiresAuthRemovalProof`, `reconciliationRequiresAuthRemoval`, and `reconciliationReleasesOrphanedBindings` pass, the daily reconciliation function is deployed and is the only automated `frozen → released` path, and a frozen binding denies use with `deletion_pending`; the Settings account summary deepens with purchase and provisional state; no device roster or device cap exists anywhere in the code or schema.

Verification: `./gradlew :app:testDebugUnitTest`; backend tests including the RTDN handlers, binding-freeze, and release paths; the internal testing track run with a licence tester that asserts withholding, executable because S07 established the track, the product, and the production environment; a Compose test for pending, revoked, and provisional surfaces; an application-lifecycle test that backgrounds/kills AppT during Play flow and proves foreground query reconciliation; **a recorded paid-path drill before public promotion** — one real purchase on the production environment, verifying grant and acknowledgement, then refunded, exercising RTDN void and revocation; the drill record states the outcome and leaves no entitlement behind.

Not in this slice: any television-related cloud data, any entitlement-based device registry.

## S11 — Apps, text, and favourites

Observable outcome: a supported television lists its launchable apps, text entry uses the phone keyboard when the television accepts it, and favourite apps or controls can be added, reordered, and removed.

Architecture sources: [commands.md](commands.md), [presentation.md](presentation.md#favourites-apps-and-edit-mode), [data.md](data.md).

Depends on: S10.

Acceptance: `app-list` and `text-rejected` fixtures behave as specified; `LaunchApp` with an unknown id returns `Rejected(Unavailable)` and sends nothing; `textInput` false hides the keyboard affordance; `favouriteReorderIsAtomic` passes; the shelf and the More sheet read one `FavouriteDao` source; edit mode is opt-in, cancellable, and never reorders core controls; a missing app degrades to a clear state rather than a dead control; nothing about a favourite leaves the phone.

Verification: fixtures `text-rejected` and `app-list`, Room tests, `./gradlew :app:testDebugUnitTest`, Compose tests for a missing app and for reordering.

Not in this slice: DIAL launch, a full layout designer.

## S12 — Wake and honest power

Observable outcome: with the television off and a stored MAC, the power control attempts a wake and then opens the session; when wake is unavailable or has failed repeatedly, the screen says so honestly instead of offering a button that cannot work.

Architecture sources: [commands.md](commands.md), [connection.md](connection.md), [discovery.md](discovery.md), [presentation.md](presentation.md#remote), [reliability.md](reliability.md).

Depends on: S10.

Acceptance: `powerOn` is `Attemptable` only with a stored MAC for the interface actually reached; wake sends a bounded burst and then opens; repeated failure moves to `Unavailable` with the explanatory copy; no SmartThings or consumer-IP-control fallback exists; a Mac that was never observed for this television never produces a wake attempt; the wake path never blocks the first frame or a command.

Verification: `./gradlew :samsung:test` with the recording wake sender, `./gradlew :app:testDebugUnitTest`, and one physical attempt recorded in the acceptance matrix (a recorded failure is a valid outcome).

Not in this slice: IR, HDMI discrete selection, a compatibility claim.

## S13 — Local diagnostics, redacted export, and Request Support

Observable outcome: building on the recorder foundation from S05, the Diagnostics surface shows what the local record holds and how old it is; building a report shows a preview of every field before anything leaves the phone; sharing happens only after explicit confirmation; Request Support on an unsupported card uses the same path; Clear local history empties the record. Nothing is uploaded anywhere, because V1 has no cloud crash reporting and no analytics. The Settings Privacy & Diagnostics section opens the surface.

Architecture sources: [diagnostics.md](diagnostics.md), [presentation.md](presentation.md#diagnostics), [security.md](security.md#b6-diagnostics), [release.md](release.md), [flows.md — diagnostics: local record and user-confirmed export](flows.md#diagnostics-local-record-and-user-confirmed-export).

Depends on: S05.

Acceptance: `noTelemetryDependency` and `adIdAbsentFromManifest` pass; `localRecordIsBoundedAndRedacted` passes for both sources and the rolling file; `diagnosticFileIsExcludedFromBackup` passes; `exportRequiresUserConfirmation` passes and the preview lists every field; `clearLocalHistoryDeletesRecordAndFile` passes with a confirmation step; `noDiagnosticsUploadPath` passes (no HTTP client in the package, no endpoint in the backend inventory); `redactedReportContainsNoFixtureSecret` passes with a planted token, pin, IP, MAC, email, Username, and text; `samsungHasNoLogCalls` passes; a full record never delays a command (`commandDoesNotAwaitDiagnostics`); the unsupported card offers Request Support through the same preview; the Settings Privacy & Diagnostics section deep-links into the surface.

Verification: unit redaction tests, `./gradlew :app:testDebugUnitTest`, manifest merge check, dependency check, `npm test --prefix backend` endpoint inventory test, and a Compose test for the preview, confirmation, and clear-history states.

Not in this slice: a diagnostic backend, crash reporting, analytics, an upload endpoint.

## S14 — Accessibility and responsive closure

Observable outcome: a screen-reader user completes Welcome, explanation, discovery, approval, one command, the account gate, trial and purchase surfaces, settings, and diagnostics. Text scales to 200% without clipping. Contrast and target sizes hold. Landscape, foldable, and tablet windows behave per the responsive rules.

Architecture sources: [presentation.md](presentation.md) (accessibility contracts, responsive rules, tokens), [ui-ux.md](ui-ux.md), [reliability.md](reliability.md).

Depends on: S10, S11, S12, S13. Every earlier control-introducing slice is a transitive ancestor of these four, so no slice can introduce a user-facing control or route after this closure.

Acceptance: every interactive control across the routes has a name, role, and state; `composeAccessibilityChecksPass` runs the API 34+ platform accessibility validator, including rendered contrast checks, across every shipped route; `everyControlMeetsTouchTarget` passes for every route; `fontScale200DoesNotClip` passes for every route; `statusIsNotColorOnly` passes; `reducedMotionSkipsTravel` passes; `gestureAlternativesExist` passes; `destructiveActionsConfirm` passes for forget, sign-out, account deletion, and clear-local-history; `statusAnnouncedOnce` passes; traversal order matches the visual task order in the Remote zones; a TalkBack walkthrough record is attached to the slice; each window size class renders per the responsive table with control ordering preserved.

Verification: Compose accessibility assertions with platform accessibility checks enabled on API 34+ AndroidComposeTestRule instrumentation, `./gradlew :app:connectedDebugAndroidTest` on an API 34+ phone and tablet-sized emulator, the existing API 29 installed-app acceptance as a separate compatibility floor, a manual TalkBack script, and a screenshot set at 100% and 200% font scale.

Not in this slice: a new visual brand, a separate tablet product.

## S15 — Reliability, performance, and lifecycle E2E verification harness

Observable outcome: launch, control latency, frame timing, memory, and battery results are recorded per release, and the complete customer lifecycle is exercised through journey-level E2E tests whose failure artifacts make cross-layer regressions diagnosable.

Architecture sources: [reliability.md](reliability.md), [testing.md](testing.md#whole-application-lifecycle-e2e), [testing.md](testing.md#performance-and-reliability), [modules.md](modules.md#shape), [lifecycle.md](lifecycle.md).

Depends on: S11, S13. S11 transitively covers every measured feature — launch routing, TvList, process-death reopen, the More sheet, Remote frame timing, control latency, discovery, and recreation — and S13 covers the diagnostics non-blocking contract.

Acceptance: the five required lifecycle journeys in [testing.md](testing.md#required-journeys) run against the exact candidate with step trace, failure screenshot, semantics hierarchy when available, and AppT logcat retained on failure; the `:macrobenchmark` module measures cold and warm start, Remote frame timing, control latency, discovery, reopen after process death, and recreation; `launchBudget`, `commandLatencyBudget`, `frameTimingBudget`, and `reconnectRecoveryRate` are enforced; baseline profiles for Remote, Discovery, and TvList are generated by the module and committed, refreshing the Remote profile first committed in S05; `commandDoesNotAwaitDiagnostics` passes; memory and battery budgets are recorded with a battery-historian run on the reference device; a >20% regression blocks promotion until explained; results are stored as release artifacts, never as analytics; `noProductionModuleDependsOnBenchmark` passes.

Verification: journey-level E2E on the candidate plus retained failure artifacts, `./gradlew :macrobenchmark:connectedCheck`, recorded benchmark JSON per release, the release checklist entry, and CI guards for the thresholds.

Not in this slice: public performance claims or SLAs.

## S16 — Upgrade, release promotion, and hardening

Observable outcome: the candidate proves install-over-install migration and the promotion machinery exists and stays off: a manual production-promotion workflow moves the tested candidate to a production track with staged rollout, the release checklist records the halt criteria and the required evidence, and the full pull-request check suite from the release architecture is green on `main`. The artifact that will be promoted is the artifact that was tested.

Architecture sources: [release.md](release.md) (upgrade-in-place release proof, release path and artifact identity, pull-request checks, two external gates), [data.md](data.md#migration-and-upgrade-architecture), [lifecycle.md](lifecycle.md#install-app-update-and-external-flow-return), [reliability.md](reliability.md#regression-policy), [security.md](security.md#b7-ci-supply-chain-and-release).

Depends on: S14, S15.

Acceptance: `upgradeFromPreviousReleasePreservesLocalState` passes on an install-over-install candidate using the source-artifact rule in release.md, with no destructive migration or data clear; `releaseArtifactsDifferOnlyByConfig` passes on the signature-stripped comparison — the internal and production release artifacts from one commit differ only in environment configuration, the production artifact carries none of the development or internal configuration, and no version-code exception exists in the check; `releaseVariantsShareVersionCode` passes — both variants from the same commit carry the same version code and name; the same release unit tests and accessibility checks run against both variants; the production-promotion workflow exists, is a manual dispatch, promotes that same uploaded bundle with no rebuild and no environment change, and is not run by this slice; staged rollout starts below 100 percent, and the halt criteria — a Play Console Android vitals crash or ANR rise, or an entitlement error-rate rise — are recorded in the release checklist; the release checklist names the required evidence: the recorded backend rollback drill (S08), the recorded paid-path drill (S10), and at least one physical matrix row (S17); every pull-request check in [release.md](release.md#pull-request-checks) runs, including the backend typecheck, lint, and emulator jobs, the accessibility assertions, and the benchmark guards; `main` is green.

Verification: CI logs, workflow definition review, and the completed release checklist note.

Not in this slice: running promotion, public rollout, the license decision, the vendor-terms decision.

## S17 — Physical acceptance and external gates

Observable outcome: the release candidate consolidates the staged physical evidence: one real Samsung television completes pair, command, reconnect, and process-death resume on the TLS token path. Wake is attempted and recorded, including an honest failure. The matrix row contains no address, MAC, token, or account email. Both external gates are recorded as passed before public promotion.

Architecture sources: [testing.md](testing.md#physical-acceptance-matrix), [release.md](release.md#two-external-gates).

Depends on: S10 (the recorded paid-path drill), S12 (the recorded wake attempt). May run in parallel with S14–S16. A debug or internal build is enough.

Acceptance: one matrix row with the required columns filled; pin-survived-reboot recorded as yes, no, or not tried; no year range added to the app as a support gate; honest wake failure recorded if wake does not work; the source-license decision and the Samsung vendor-terms review are recorded as human decisions before public promotion; the S10 paid-path drill is recorded as complete; production promotion is not started by this slice.

Verification: the matrix note in the slice record plus a rerun of the contract tests against the same build.

Not in this slice: a public compatibility page, a second ecosystem, a commitment that untested generations work.

## Coverage matrix

Every implementation-relevant architecture document has an owning slice. A slice owns a document when that slice is where the behavior first becomes real and is verified; later slices may deepen it, named in the slice text.

| Architecture area | Source | Owning slices |
| --- | --- | --- |
| Module seams and the two-adapter test pattern | [modules.md](modules.md) | S01 (skeleton seams), S02 (first production and test adapter) |
| Local-network discovery | [discovery.md](discovery.md) | S02 |
| Samsung interface: discovery, pairing, control, wake | [samsung-interface.md](samsung-interface.md) | S02, S03, S04, S12 |
| Command mapping | [commands.md](commands.md) | S03, S05, S11 |
| Wire protocol and quiet reconnect | [protocol.md](protocol.md) | S03, S06 |
| Connection lifecycle | [connection.md](connection.md) | S03, S06 |
| Local data and persistence | [data.md](data.md) | S03, S04, S09, S11, S13, S16 |
| Routes and screen-state contracts | [presentation.md](presentation.md) | S02, S03, S05, S08, S09, S10, S11, S13, S14 |
| UI/UX surface map | [ui-ux.md](ui-ux.md) | S02, S05, S08, S09, S10, S13, S14 |
| Account, trial, purchase, entitlement backend | [account-entitlement.md](account-entitlement.md) | S07, S08, S10 |
| Environments, signing, release, Play, App Check | [release.md](release.md) | S07, S08, S16 |
| App lifecycle, upgrade, system return, and WorkManager | [lifecycle.md](lifecycle.md) | S06, S09, S10, S16 |
| Local diagnostics and redacted export | [diagnostics.md](diagnostics.md) | S05 (recorder), S13 (surface/export) |
| Reliability and performance targets | [reliability.md](reliability.md) | S06, S15, S17 |
| Security invariants | [security.md](security.md) | S04, S08, S10, S13, S16 |
| Test strategy, lifecycle E2E, and physical acceptance | [testing.md](testing.md) | S01, S04, S06, S12, S15, S16, S17 |
| Cross-module lifecycle flows | [flows.md](flows.md) | S02, S03, S06, S08, S09, S10, S13, S16 |
| Product intent and canonical domain language | [PRODUCT.md](../PRODUCT.md), [CONTEXT.md](../../CONTEXT.md) | every slice, as the contract it implements |
| External evidence, implementation provenance, and reuse disposition | Owning architecture documents per [README.md](README.md#evidence-and-implementation-harvest) | Introduced where behavior becomes real; projected into each Arena Issue as the executable Implementation Reuse Plan; no separate harvest slice/register |

Cross-cutting invariants are not a single slice's work; each is introduced where it first becomes real and closed where stated.

| Invariant | Introduced / closed by |
| --- | --- |
| Accessibility floor (48dp targets, TalkBack semantics, scalable text, contrast, no color-only meaning, gesture alternatives, reduced motion) | introduced by every UI-adding slice; closed by S14 |
| Responsive rules (rotation, landscape, foldables, tablets) | introduced from S05; closed by S14 |
| Reliability, performance, and whole-app lifecycle measured | S15 harness; S04/S06/S12 stage physical evidence; S17 consolidates release evidence |
| Diagnostic and support data leave the phone only through an explicit user preview and confirmed share | S13 (confirmed export) |
| TV identity, pairing, personalization, preferences, diagnostics, and usage data are never uploaded to the account backend | S08 (`backendStoresNoTelevisionField`); kept device-local in S04 (pairing), S05 (preferences), S09 (personalization), S11 (favourites), S13 (diagnostics) |
| No TV-personalization sync, no cloud crash reporting, no analytics or ad SDK | S01 dependency guards; enforced across S04, S10, S13 |
| The licensing gate is active on every remote entry | S08 (the gate); S09 (launch routing passes through it) |
| TV/personalization/diagnostic state excluded from Android backup and device transfer | S04 (pairing), S09 (personalization), S13 (diagnostic file) |
| Backend source architecture (`npm <script> --prefix backend`, server-only Firestore) | S07, S08, S10 |
| Fail-closed identity and secret deletion | S04 |
| Raw purchase tokens never stored, logged, or exported | S10 |
| Diagnostics never blocks or delays a command | S05 foundation; S13 closes customer-facing diagnostics |
| CI supply-chain pinning and signing-identity checks | S16 |
| The two external gates (final source license; Samsung vendor terms) | human gates asserted at S17; never a slice |

## Slice count and dependency outline

Seventeen slices. Control spine: S01 → S02 → S03 → S04 → S05 → S06. Foundations: S07 (environments, signing, backend deployment, Play) from S01, available in parallel but activated only when authorized. Product/account spine: S08 (account, trial, gate) after S06 and S07 → S09 (remembered televisions, remote-first launch, switching, Forget) → S10 (purchase, restore, revocation, purchase-binding deletion durability). Feature branches: S11 (apps, text, favourites) and S12 (wake) after S10. Diagnostics recording begins in S05 and the complete customer-facing diagnostics transaction lands in S13. Closure: S14 accessibility/responsive after S10–S13; S15 reliability/performance/lifecycle-E2E after S11 and S13; S16 upgrade/release hardening after S14 and S15; S17 final physical/external gates after S10 and S12.

### What this route answers

- **Settings ownership.** S05 owns the Settings route/shell, Interaction/About sections, and the early local diagnostic recorder. Account & License becomes functional in S08 and deepens in S10 with purchase/provisional state; TVs becomes functional in S09; Privacy & Diagnostics becomes functional in S13. No dead destination row renders before its owner exists.
- **Account deletion versus purchase-binding deletion.** Account deletion starts in S08 with the no-binding phases. Purchase-binding freeze/release, deletion-scoped correlation, Auth-removal proof, and scheduled reconciliation first become real in S10, where purchase bindings exist.
- **Ordinary control before monetization depth.** S09 completes remembered-TV launch, switching, rename, and Forget behind the S08 gate before S10 adds purchase/refund/restoration complexity.
- **Environments early, promotion late.** S07 creates dev/internal/production Firebase, backend deployment, Play, signing, and App Check foundations. S08 proves account/trial against them; S10 proves purchase/RTDN against them. S16 owns upgrade-in-place candidate proof and production-promotion machinery.
- **Diagnostics early, UI later.** S05 introduces the bounded redacted recorder so S06–S12 failures are diagnosable. S13 adds preview/share/clear and Request Support without changing the command-path constraint.
- **Accessibility closure.** S14 is blocked by S10, S11, S12, and S13; every earlier user-facing route is a transitive ancestor, so no later slice adds a new control surface after accessibility closure.
- **Reliability and lifecycle closure.** S15 combines macrobenchmarks with the journey-level E2E contract from [testing.md](testing.md#whole-application-lifecycle-e2e), catching cross-layer failures that isolated unit/fixture/Compose tests cannot.
- **Physical evidence cadence.** S04 re-exercises the accepted S03 path on real Samsung hardware and proves restart/token resume; S06 proves a real reconnect/lifecycle path; S12 records wake behavior; S17 consolidates release-candidate physical evidence and the two external gates.
- **Forget.** The low-level idempotent `SamsungTvs.forget` primitive stays in S04. `PendingForget`, atomic row/favourite deletion, confirmation, and retry land in S09 with the management surface that exposes them.
- **Schema timing.** S04 does not pre-create `Favourite` or `PendingForget`. `TvProfile` is already real from S03; `PendingForget` lands in S09; `Favourite` lands in S11.
- **Cross-module lifecycle flows.** [flows.md](flows.md) is cited by the slices that implement each sequence: S02/S03 first use, S06 reconnect/lifecycle, S08 account/trial, S09 remembered-TV launch/switch/forget, S10 purchase/restore/refund/purchase-return, S13 diagnostics, and S16 upgrade-in-place.

This route preserves accepted S01–S03 history while replacing the future route from S04 onward where lifecycle evidence showed ordering or verification gaps. No television-sync slice exists because television/personalization state remains device-local. No separate harvest slice/register exists because external evidence belongs to the architecture owner that consumes it.
