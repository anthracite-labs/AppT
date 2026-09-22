# Architecture

**Status: accepted V1 architecture baseline.**

This document records the architecture decisions already made for AppT. It is the canonical input for detailed architecture mapping during the architecture phase.

`docs/PRODUCT.md` owns product intent. This file owns accepted technical direction. `docs/HARVEST.md` records which ideas from the harvested remote-control research are adopted, retained as references, or rejected.

## Delivery strategy

- V1 implementation starts **Android-native**.
- iOS is deliberately deferred. The Android architecture does not carry portability constraints for a future iOS implementation.
- Android implementation language: **Kotlin**.
- UI: **Jetpack Compose**.
- Screen/application state: **ViewModel + Kotlin Coroutines + StateFlow**.
- Navigation: **Navigation Compose with type-safe Kotlin-serialization routes**.

## Android platform baseline

- `minSdk`: **29 (Android 10)**.
- Current Play target baseline: **API 36**.
- Local-network permission policy is owned by one application-level access gate. Samsung protocol code does not launch permission UI.
- Permission UX explains why LAN access is required immediately before first discovery.
- Platform-specific networking, lifecycle, secure-storage, and permission details stay native to Android.

## Project shape

Start lean:

- `app` — Compose UI, navigation, product flows, account/sync orchestration, permission UX, application wiring.
- `samsung` — Samsung discovery, pairing, identity, capabilities, connection/reconnection, protocol parsing, and command translation.

Do not create a universal TV abstraction before a second TV ecosystem exists. Samsung is implemented cleanly first; the universal seam is designed from two real implementations later.

Do not pre-create a forest of `domain`, `data`, `usecase`, `repository`, or per-feature Gradle modules without demonstrated pressure.

## Samsung module

The Samsung implementation is a **deep module**: a small interface hides protocol complexity and concentrates Samsung knowledge.

The application may ask the Samsung module to perform operations such as discovery, pairing, connection, command execution, capability inspection, and disconnection, and may observe device/session state. The caller-facing types are `SamsungTvs` and `RemoteSession`, specified in `docs/architecture/samsung-interface.md`.

The module owns:

- SSDP/mDNS/native LAN discovery mechanics relevant to Samsung;
- pairing and TV-side approval flows;
- persistent Samsung identity where the protocol exposes one;
- token/credential handling;
- HTTP/WebSocket/TLS protocol details;
- command-to-wire translation;
- connection state and supervised reconnection;
- defensive protocol parsing;
- Samsung capability detection;
- protocol-specific failure normalization.

The `app` module does not construct raw Samsung WebSocket payloads, raw `KEY_*` strings, retry loops, or protocol parsers.

## Commands and capabilities

- Application code uses **typed Samsung commands** rather than wire-format strings.
- Controls are capability-driven.
- A connected TV is the source of truth for demonstrated capabilities.
- Do not assume capabilities solely from model/year tables.
- Raw protocol payloads and Samsung-specific key strings remain internal to `samsung`.
- A cross-brand canonical command interface is deferred until ecosystem #2 creates a real seam.

## Discovery and connection lifecycle

- First-run discovery starts automatically after local-network access is available.
- Discovery is bounded rather than continuous.
- Users can explicitly rescan/add TVs.
- Known TVs reconnect quietly.
- Rediscovery is used when stored network addressing becomes stale.
- A Samsung session is persistent while the remote is active so button presses remain immediate.
- Connection loss moves through supervised reconnect state with bounded backoff.
- The connection may be released when active control is no longer useful.
- Background continuous discovery/control is not a V1 architectural assumption.

## Networking

- **OkHttp** is the preferred HTTP/WebSocket/TLS client stack.
- Android/native networking primitives are used where LAN discovery, multicast, NSD/mDNS, SSDP, Wake-on-LAN, or platform integration require them.
- No unnecessary listening server is opened on the phone for V1 control flows.
- Protocol payloads are parsed defensively with bounded lengths and schema/state validation.
- Samsung failures surface as state/results rather than crashing UI callers.

## Dependency injection

- **Hilt** wires the Android application graph.
- Ordinary Kotlin classes still receive dependencies through constructors and remain directly constructible where practical.
- Hilt is composition/wiring, not the architecture itself.

## Local persistence and secrets

- **Room** stores structured durable application data.
- **DataStore** stores preferences and small configuration state.
- TV pairing credentials/tokens/private keys are password-equivalent data and are protected using **Android Keystore-backed** storage.
- Pairing secrets are not stored as ordinary Room/DataStore plaintext.
- Friendly names, favourites, and syncable preferences are a different storage class from TV pairing secrets.
- Pairing secrets never enter account synchronization.
- Where a persistent TV security identity can be established, an unexpected identity change fails closed and requires explicit re-pairing.

## Account and backend

- Backend: **Firebase Authentication + Cloud Firestore**.
- V1 sign-in methods: **Google sign-in + email/password fallback**.
- First successful local control is not gated by account creation.
- The TV-control path does not depend on Firebase, AppT backend availability, or public internet availability.

## Sync model

- **Room is the application-local source of truth.**
- Screens/ViewModels read local data rather than switching between local and cloud sources.
- Local mutations are persisted locally first.
- A dedicated sync implementation exchanges an explicit whitelist of non-secret data with Firestore.
- Deferred/retryable synchronization work uses WorkManager where appropriate.
- Firestore is not a competing local application datastore.
- Conflict policy is **last committed write wins per small record**, with deletion handling designed so deletions synchronize rather than resurrect stale data.
- TV pairing credentials and device secrets are never synchronized.

## Diagnostics and privacy

- V1 has no behavioral analytics.
- Crash reporting: **Firebase Crashlytics**, configured without behavioral Analytics, plus a bounded/redacted local diagnostic log/export path.
- Logs and reports must exclude pairing credentials, Wi-Fi names, local IP addresses, directly identifying TV data, sensitive command/text contents, and other secrets.
- More detailed diagnostics require explicit user action.
- Diagnostics must not sit in the TV-control critical path.

## Testing architecture

Use the highest useful seam and test external behavior rather than internal implementation detail.

- JVM unit tests for ViewModels/state and pure logic.
- Samsung protocol contract tests using recorded/redacted fixtures.
- Fake transports for connection, WebSocket, discovery, timeout, and failure scenarios.
- Room migration/data tests.
- Instrumented tests where Android behavior is materially involved, including Keystore, permissions, lifecycle, and platform networking integration.
- Compose UI tests for critical user flows.
- A small physical Samsung-TV acceptance matrix before release.
- The Samsung module interface is the primary high-value test surface. Its concrete shape is `docs/architecture/samsung-interface.md`.

## CI, release, and supply chain

- CI/release: **GitHub Actions + Gradle + Google Play testing tracks**.
- Pull requests compile and run relevant static checks/tests.
- `main` stays releasable.
- Release artifact: Android App Bundle.
- Production signing uses Google Play App Signing.
- Releases move through Internal testing before deliberate promotion; production rollout is staged.
- Use a Gradle version catalog.
- Pin dependency versions; no production `+` ranges or snapshots.
- Enable dependency locking and dependency verification where practical.
- Restrict dependency repositories.
- Dependency updates arrive as reviewed pull requests rather than silent upgrades.
- Pin GitHub Actions to immutable revisions where practical.
- Review license/provenance for significant dependencies.
- Keep the dependency surface small, especially inside `samsung`.

## Architecture invariants

These are binding unless deliberately changed by a later architecture decision:

1. **Local control remains independent of cloud availability.**
2. **Samsung protocol complexity stays inside the Samsung module.**
3. **Pairing secrets remain device-local and Keystore-backed.**
4. **UI is capability-driven and does not knowingly expose dead controls.**
5. **Discovery is bounded and user-visible rather than continuous telemetry.**
6. **No unnecessary listening server is part of V1 control.**
7. **The Android implementation is optimized for Android rather than a hypothetical shared iOS runtime.**
8. **Universal TV abstractions wait for a second real ecosystem.**
9. **Room is local truth; cloud sync is secondary and non-secret.**
10. **No behavioral analytics.**

## Elaboration

Implementation-ready detail lives in `docs/architecture/`. Start at `docs/architecture/README.md`. That directory elaborates this baseline. It does not replace it. If an elaboration conflicts with this file, this file wins until a later architecture decision changes it.

A missing decision that could materially change product or architecture intent is surfaced, not chosen. The open case is different-account sign-in, recorded in `docs/architecture/sync.md`.
