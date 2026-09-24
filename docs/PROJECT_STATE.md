# Project State

<!--
Living snapshot only: replace stale state; do not append history.
Update only when the snapshot materially changes.
Keep decisions and references only while they affect current work.
Recent change: one entry. Next: one action. After that: at most three.
Verification is optional and appears only when materially relevant.
Phase values: setup | discovery | architecture | implementation | hardening | maintenance.
-->

Project: `AppT`

Purpose: `A universal TV remote for ordinary consumers who want private, dependable local television control, beginning with Samsung Smart TVs.`

Phase: `implementation`

## Current objective

Objective: `Resume S02 — Local-network explanation and bounded discovery — now that the pre-S02 security baseline in Issue #36 has been accepted and merged. S07 remains dependency-ready but is not active or authorized.`

Success condition: `S02 is implemented and accepted against the architecture/slice contract while preserving the merged security baseline, privacy constraints, and supply-chain controls.`

## Active work

Primary: `S01 remains the accepted product implementation baseline through PR #30, the pre-S02 security baseline is accepted through PR #38, dependency/toolchain modernization is accepted through PR #55, and the verification/AI-assurance redesign is accepted through merged PR #60. S02 is the next product slice to compile and dispatch.`

Secondary:

- CI verification architecture and the independent AI-code assurance stack are accepted through merged PR #60 / Issue #56: one-owner `verify.yml`, strict compiler/formatting, dead-code and coverage evidence, workflow security, SonarQube Cloud, CodeRabbit policy, temporary Advanced CodeQL for Kotlin 2.4.20, and independent post-Arena review.
- Provider facts recorded as needs validation in docs/architecture/README.md must be confirmed in the slice where they first become implementation-relevant.
- Final AppT source-license decision remains a pre-public-release gate.
- Focused Samsung vendor-terms/legal review remains a pre-public-release gate.

## Current decisions

- `Product discovery is complete; docs/PRODUCT.md owns product intent.`
- `CONTEXT.md now owns canonical domain language, including Television, Local Pairing, Customer Account, Trial, Lifetime Entitlement, and Active Remote.`
- `Android-native is first; Kotlin + Compose + ViewModel/Coroutines/StateFlow; iOS is deferred and not an Android architecture constraint.`
- `Start with lean app + samsung modules; Samsung is a deep module and a universal TV seam waits for ecosystem #2.`
- `The physical Television is the core domain concept; discovery, local pairing/trust, personalization, account identity, and active control are separate relationships/state around it.`
- `AppT is remote-first after setup: normally reopen the last-used TV; connection failure stays on an honest remote surface with retry/switch-TV recovery rather than forcing a dashboard.`
- `Production V1 has no consumer experimental functional-control mode for non-adopted protocols; internal/debug research may exercise bounded experimental implementations.`
- `Customer Account purpose is minimal: authentication, username, seven-day trial/anti-abuse state, and lifetime license entitlement only.`
- `TV identity, pairing, TV names, favourites, remote arrangement, preferences, last-used TV, diagnostics, and usage history are device-local and are not stored/synchronized in the account backend.`
- `Google sign-in through Credential Manager is primary; email/password is fallback. Google display name may seed an editable username, but unrelated profile data is not copied into AppT.`
- `First successful local-control session remains available before account creation; after that active session ends, account access is required.`
- `Eligible accounts receive a seven-day full-use trial from a server-authoritative activation timestamp.`
- `Email/password fallback accounts must verify their email before activating a free trial; Google identities use the provider-verified identity.`
- `Username is an editable, non-unique display name only; it is not a login identifier or public handle.`
- `One recognized Android device receives one AppT trial total unless support explicitly clears a pseudonymous abuse marker for a legitimate exceptional case; V1 does not depend on Play Integrity Device Recall.`
- `Trial abuse protection uses privacy-minimized pseudonymous eligibility signals derived from verified email/device context plus Play Integrity, not TV or behavioral data.`
- `Android lifetime unlock is a Google Play one-time non-consumable product; authoritative purchase validation grants an account-level lifetime entitlement.`
- `A validated lifetime entitlement keeps paid local control available offline indefinitely without periodic backend revalidation.`
- `Network failure never removes a validated entitlement; an authoritative refunded/revoked result applies on the next remote entry and never interrupts an already active remote.`
- `V1 has no paid-device roster or fixed device cap.`
- `Deleting an AppT account does not destroy the underlying Google Play purchase; a legitimate purchaser may later restore it through authoritative validation.`
- `Minimal pseudonymous trial-used markers may survive account deletion for as long as the trial program exists; they contain no username, raw email, TV data, personalization, diagnostics, or usage history.`
- `Trial expiry does not interrupt an active remote; after known expiry, the next remote entry requires online entitlement validation or purchase.`
- `Signing out, switching accounts, or deleting an account does not erase/merge/upload/replace local TV pairing or personalization.`
- `Forget this TV is the consumer action that removes this phone's local pairing and TV personalization only.`
- `Favourites/secondary-control order are per-TV and local; haptics, physical volume-button behavior, and preferred navigation mode are device-wide and local.`
- `The previous Firestore TV-personalization sync architecture is superseded and must not reappear in implementation.`
- `Explicit sign-out requires sign-in again before a new remote entry; active remote sessions are not interrupted.`
- `One Google Play lifetime purchase binds to one Customer Account; it is not freely transferable among unrelated AppT accounts.`
- `A genuine Play purchase may receive a short non-renewable provisional entitlement if AppT validation infrastructure is temporarily unavailable.`
- `Play Integrity is used proportionally for anti-abuse and authenticity, not as a blanket paid-entitlement confiscation mechanism.`
- `V1 has no cloud crash reporting and no crash-reporting, analytics, or advertising SDK; bounded redacted local diagnostics plus an explicit user-confirmed export are the whole diagnostic path.`
- `TV/personalization state is excluded from Android backup/device transfer; a new phone starts its remote state clean.`
- `The same seven-day trial follows the account across phones with the original expiry; participating devices are marked as trial-consumed.`
- `Trial/purchase UX is low-pressure and never interrupts an active remote session.`
- `UI/UX architecture is now a dedicated architecture track; docs/architecture/ui-ux.md owns the product-surface map.`
- `Visual direction combines a friendly consumer-facing mascot/brand layer with a premium restrained dark-first everyday remote theme.`
- `The everyday Remote has no persistent bottom navigation; the remote remains the dominant surface.`
- `Remote customization is fixed core + reorderable/favouritable secondary controls; core muscle-memory controls keep stable placement.`
- `When supported, D-pad and Touchpad switch in-place within the central navigation surface.`
- `TV switching from Remote uses the current-TV affordance and a remembered-TV bottom sheet with Add TV; swipe-between-TVs is rejected.`
- `Onboarding is one concise Welcome screen with brand/mascot personality, privacy/local-control reassurance, then local-network explanation and bounded discovery.`
- `Pairing is a dedicated focused state that directs the user to approve AppT on the television and transitions directly into Remote on success.`
- `Account/trial/purchase uses full-screen continuation/gate surfaces outside active Remote; during trial payment UX is low-pressure and does not dominate the remote.`
- `Settings is one conventional grouped screen; contextual actions may deep-link into it.`
- `Failure UX stays in context and presents the next useful recovery action; modal/full-screen interruption is reserved for genuinely blocking states.`
- `Remote layout is thumb-first: primary navigation/high-frequency controls occupy the central/lower reach zone; power/status/TV chrome live above, with power isolated against accidental activation.`
- `Remembered-TV cards remain consumer-minimal: friendly name + ordinary-language state; technical identifiers stay out of normal UI.`
- `A compact favourites shelf exposes a small set of favourite apps/secondary controls; More opens the complete supported secondary surface.`
- `Gestures accelerate visible actions but are never the sole way to perform them; no hidden TV-switch or secret gestures.`
- `Portrait phone is the primary reference, but V1 responds correctly to rotation, landscape, foldables and tablets without a separate tablet product.`
- `Accessibility floor includes 48dp minimum targets, scalable text, TalkBack semantics/status announcements, no color-only meaning, gesture alternatives, strong contrast, reduced-motion respect, and safe destructive-action treatment.`
- `The known human UI/UX decision frontier is closed; remaining frontend work is technical architecture synthesis unless a real contradiction surfaces.`
- `Architecture closure: the Entitlement Backend is Firebase Cloud Functions (2nd gen) with a server-only Firestore datastore, Secret Manager marker keys, a Cloud KMS proof-signing key, and Play RTDN over Pub/Sub; the Android client has no Firestore dependency.`
- `Development, internal, and production Firebase/Cloud/Play environments are separated; production credentials never enter the repository.`
- `Play carries one artifact: a production-flavoured release candidate is uploaded to the internal testing track and promoted from there, while internal-environment builds are distributed outside Play, so the promoted build is the build that was tested.`
- `Account deletion freezes the purchase binding before the Firebase Auth user is deleted and releases it only after, so the purchase is never re-bindable while the previous account can still authenticate; a scheduled job finishes deletions that stopped early.`
- Backend source is TypeScript on the Cloud Functions 2nd gen Node.js runtime, owned by backend/, with its own lockfile and emulator-suite tests, and every backend command is package-prefixed from the repository root as `npm <script> --prefix backend` (ci, typecheck, lint, test, test:emulator, deploy).
- `Production signing stays with Google-managed Play App Signing and CI holds only the upload key; the outside-Play internal tester build is signed with a separate internal signing key whose certificate is registered only in the internal Firebase project, so each App Check registration matches the certificate of the build that talks to it.`
- `The internal and production release artifacts are compared with signature material stripped, so a signing-certificate difference can never be mistaken for a configuration difference, and signing identity plus certificate registration are asserted by their own checks.`
- `Both release variants from one commit carry the same versionCode and versionName, because the version identifies a release rather than an environment, so the artifact comparison needs no version-code exception and allows environment configuration to differ and nothing else.`
- `A frozen purchase binding and its account record share one random deletion-scoped deletionId written at freeze, so an interrupted account deletion is always reconcilable and a frozen binding can never be permanently orphaned.`
- `An accountDeleted release of a purchase binding requires an Auth-removal proof timestamp: reconciliation releases only after Firebase Auth confirms the user is gone, and while the identity can still authenticate it leaves the binding frozen and raises an alert instead of releasing.`
- `AppT application data is excluded from Android backup and device transfer, so a new phone starts its remote state clean.`
- `The implementation route is the accepted seventeen-slice map in docs/architecture/slices.md, merged through PR #25; it replaces the sixteen-slice map and contains no television-sync slice.`
- `Architecture closure was explicitly accepted by the human on 2026-09-23; implementation is now authorized, one accepted slice at a time.`
- `S01 — Walking skeleton and CI floor — is the accepted implementation baseline, merged through PR #30 with green Android, runtime-emulator, backend, and secret-scanning checks.`
- `The pre-S02 security baseline is accepted through merged PR #38 / closed Issue #36: full standard-profile audit evidence was produced, and CodeQL for Java/Kotlin plus JavaScript/TypeScript, detekt, GitHub dependency review, Dependabot configuration, and the repository-owned security script are now part of the repository floor while preserving the existing secret scanner.`
- `Generic Semgrep is deferred; add it only for a later AppT-specific invariant that CodeQL, detekt, Android lint, existing Gradle guards, or simple repository checks cannot express cleanly.`
- `Verification architecture is one-owner by concern: GitHub owns repository-host security controls, Gradle owns Android verification, the backend package owns TypeScript verification, AppT guards own product-specific invariants, and repository workflow YAML exposes one stable verify/gate interface without a path classifier unless measured cost later justifies one.`
- `AI-authored changes receive no trust discount: native deterministic checks remain authoritative, SonarQube Cloud owns the cross-language maintainability/reliability/new-code coverage/duplication gate once compatibility is proven, CodeRabbit supplies independent issue/scope/custom pre-merge review, and Arena PRs still receive an independent repository code-review pass before the human merge decision.`
- `CodeQL on Kotlin 2.4.20: GitHub-managed Default Setup uses bundle 2.27.0 which does not support Kotlin 2.4.20 (supported starting in bundle 2.27.1). AppT temporarily operates an Advanced Setup workflow (.github/workflows/codeql.yml) pinned to CodeQL Action v4.38.2 (commit 2892aa5e19bbd11bc0cff5427e3b750a04d9e3c2) and bundle 2.27.1, analyzing java-kotlin via deterministic Gradle extraction under strict dependency verification, plus javascript-typescript and actions with security-extended query suite. Migration-back condition: retire .github/workflows/codeql.yml and re-enable Default Setup once GitHub's managed service reaches CodeQL >= 2.27.1 and passes.`

## Blockers / Unknowns

- Provider facts listed as needs validation in `docs/architecture/README.md` (Play RTDN shapes, Developer API method, `purchaseType`, Play Integrity verdicts, Android ID stability, Play vitals coverage without a crash SDK, KMS/JWKS rotation, email-alias normalization, contrast tooling) must be confirmed during implementation.
- Final AppT source-license decision remains required before public distribution.
- Focused Samsung vendor-terms/legal review must be completed before public release.
- Physical-device evidence is required to tune the reliability targets in `docs/architecture/reliability.md`.

## Recent change

- `PR #60 merged as commit 2898b2ace007c84b21a85eed95b496027ac4ef86, completing Issue #56's verification and AI-code assurance redesign: one-owner verify workflow, stable gate, strict Android/backend verification, Gradle Managed Devices, CI-based SonarQube Cloud, temporary Advanced CodeQL bundle 2.27.1 for Kotlin 2.4.20, version-controlled CodeRabbit policy, coordinated Dependabot updates, and retirement of the legacy CI/maintenance topology.`

## Relevant canonical references

- `docs/PRODUCT.md — approved product definition, including privacy-first account/trial/license behavior.`
- `CONTEXT.md — canonical AppT domain language, including Entitlement Backend, Provisional Entitlement, and Trial Eligibility Marker.`
- `docs/ARCHITECTURE.md — accepted technical baseline as revised by settled architecture decisions.`
- `docs/architecture/README.md — detailed architecture map, settled-decision ownership, invariant pointers, and the needs-validation register.`
- `docs/architecture/sync.md — account, trial, purchase, and entitlement architecture (file name is historical).`
- `docs/architecture/presentation.md — routes, screen state contracts, restoration, responsive rules, tokens, accessibility.`
- `docs/architecture/slices.md — the accepted S01–S17 implementation route merged through PR #25.`
- `docs/HARVEST.md — harvested research disposition: ADOPT / HARVEST / REJECT.`
- `GitHub Issue #23 — pre-S01 planning reconciliation work order that produced the revised slice route.`
- `GitHub Issue #27 — completed S01 Arena work order; closed by merged PR #30.`
- `GitHub Issue #36 — completed security-baseline Arena work order; closed by merged PR #38.`
- `GitHub Issue #54 / PR #55 — completed one-pass dependency and toolchain modernization.`
- `GitHub Issue #56 / PR #60 — completed verification and AI-code assurance redesign, merged through commit 2898b2ace007c84b21a85eed95b496027ac4ef86.`
- `.agents/CAPABILITIES.md — architecture/decision/review routing.`
- `.agents/ARENA-DISPATCH.md — Arena work-order compilation contract.`
- `AGENTS.md — repository operating entry point.`

## Next

`Compile and dispatch S02 — Local-network explanation and bounded discovery — as the next authorized product implementation slice.`

## After that

1. `Implement and review S02 against the accepted architecture/slice contract.`
2. `Observe the merged verification stack on the next ordinary PR, including CodeRabbit target-branch configuration and the stable gate.`
3. `Keep the confirmed security-audit follow-up findings, S07's explicit authorization gate, provider needs-validation facts, and the two external public-release gates visible; do not silently fold them into unrelated work.`
