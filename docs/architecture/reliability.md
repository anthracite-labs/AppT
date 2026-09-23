# Reliability and performance engineering targets

Reliability is the primary product constraint, so the architecture fixes internal engineering targets for the critical paths. These are **internal engineering targets**, not public SLAs, and not marketing claims.

Two rules apply to every number here:

1. A target that needs physical-device or provider evidence before it can be trusted is marked **tune during implementation**. The number states intent and gives a regression gate; it is not presented as measured fact.
2. Every material target names its verification strategy. A target with no verification is deleted, not documented.

Reference device class for all targets: a mid-tier 2023-or-later Android phone (for example a Pixel 7a / Galaxy A5x class device), Android 10 minimum, one physical Samsung television on the same home network. Numbers are worst-case after warm-up across five runs, reported as p50 and p95.

## Launch and setup

The measurement harness lives in the **test-only `:macrobenchmark` module** described in [modules.md](modules.md#shape). Without that module the Macrobenchmark targets below cannot run, which is why it exists even though the production shape is two modules.

| Target | p50 | p95 | Notes | Verification |
|---|---|---|---|---|
| Cold start to first frame of the resolved start destination | 500 ms | 1200 ms | No backend wait before first frame; launch routing is synchronous over local state | Macrobenchmark `StartupTimingMetric` |
| Cold start to usable last-used Remote (chrome, controls enabled as capability evidence allows) | 900 ms | 2000 ms | Session may still be `Connecting`; the surface is usable regardless | Macrobenchmark plus a Compose test asserting controls render before `Ready` |
| Warm start from recents within grace to interactive Remote | 150 ms | 400 ms | Reuses the retained session | Macrobenchmark warm start |
| Time from local-network grant to first television card | 1500 ms | 4000 ms | Bounded scan is 10 s; a card is a `Found` event, not a scan end | Contract test with a scripted transport plus a physical note |
| Scan completion at the bound | 10 s | 10 s | Existing bound in [discovery.md](discovery.md); cancelling is faster | `discoveryEndsAtBound` |
| Card tap to Pairing surface visible | 150 ms | 350 ms | User-perceived responsiveness, not the TV-side approval | Compose test with a fake |
| TV approval to `Ready` | 400 ms | 1500 ms | Depends on the television; reported, not promised | Physical matrix row |
| Debug-build setup walkthrough (welcome to first command) | — | 60 s | Setup-time regression tripwire, no public claim | Manual scripted walkthrough in the release checklist |

## Control path

| Target | p50 | p95 | Notes | Verification |
|---|---|---|---|---|
| Key press to socket write issued for `Tap` while `Ready` | 20 ms | 60 ms | Excludes network and TV; measured from input event to write completion inside `samsung` | Contract test with a recording transport plus Macrobenchmark trace |
| Key press to haptic and press-state feedback | 1 frame | 2 frames | Feedback never waits for a round trip | Compose test with a frame counter |
| `Hold` press-to-release accuracy | ±30 ms | ±60 ms | Clamp at 10 s | Contract test with a fake clock |
| Command dispatch blocked by diagnostics | 0 ms | 0 ms | The diagnostic offer is non-blocking; a full buffer drops events | `commandDoesNotAwaitDiagnostics` |
| Command dispatch blocked by licensing or backend | 0 ms | 0 ms | No backend call exists on the command path | `cloudAbsenceDoesNotBlockCommand`, `noBackendCallOnCommandPath` |
| Commands queued during reconnect | 0 | 0 | Commands while not `Ready` return `Rejected(Unavailable)`; nothing is replayed | Contract test |

## Connection and recovery

| Target | Value | Notes | Verification |
|---|---|---|---|
| Socket loss to visible `Reconnecting` status | ≤ 300 ms | Quiet status, no dialog | Fake-transport test with injected close |
| Reconnect budget | 6 attempts or 45 s, whichever ends first | Existing budget in [connection.md](connection.md) | `reconnectBackoff` |
| Transient single-drop recovery rate on the same network | ≥ 90% within budget | Tune during implementation; reported per physical device | Physical matrix plus a scripted LAN flap run |
| Address-change recovery without user action | ≥ 90% within budget | Internal rediscovery of the saved identity | `rediscoverSameUuid` |
| Post-`Unreachable` user-initiated recovery | ≤ 5 s to `Reconnecting`, then normal budget | No hidden auto-retry loop | Lifecycle test |
| Process-death reopen to usable Remote | 2000 ms | Includes routing and session open attempt | Macrobenchmark plus a scripted process kill |
| First frame after rotation | ≤ 1 frame budget | No reconnect, no gate, no re-scan | `rotationKeepsSession` |

## Entitlement and backend

| Target | Value | Notes | Verification |
|---|---|---|---|
| Entitlement check at remote entry when a cached proof exists | ≤ 50 ms, no network on the decision path | Cached proof verification only | Unit test asserting no backend call on entry with a valid proof |
| Online entitlement refresh | ≤ 3000 ms budget | Async; never blocks a session or a command | Fake backend with artificial delay |
| Trial activation end-to-end (after sign-in) | ≤ 3000 ms p95 | Server-authoritative; a failure is a retryable surface, not a failed sign-in | Function test plus instrumented flow |
| Purchase verification end-to-end | ≤ 5000 ms p95 | Includes Google Play Developer API latency | Fake Play verifier in tests |
| Backend outage effect on an active remote | none | Structural: no backend call on the command path | `cloudAbsenceDoesNotBlockCommand` |
| Backend outage effect on a validated Lifetime Entitlement | none | No expiry, no periodic revalidation | `paidOfflineControlSurvivesOutage` |
| Provisional entitlement duration | ≤ 24 h, non-renewable | Architecture target; a validator rejection ends it at the next entry | `provisionalIsNonRenewable`, `provisionalExpiresWithin24Hours` |

## Frame timing, memory, and battery

| Target | Value | Notes | Verification |
|---|---|---|---|
| Remote rendering | ≥ 95% of frames within the display frame budget | Baseline profile shipped for Remote, Discovery, and TvList | Macrobenchmark `FrameTimingMetric` plus JankStats in internal builds |
| Interaction jank | ≤ 1% of frames > 32 ms; no frame > 100 ms | Includes scroll of the More sheet | Macrobenchmark |
| Idle memory (PSS, app in foreground on Welcome) | ≤ 140 MB | Tune during implementation | Memory profiler in CI-adjacent release check |
| Active Remote memory (PSS) | ≤ 200 MB | One socket, one session, no cached frames | Memory profiler |
| Background idle battery | ≤ 0.5% per hour | No wakelocks, no keepalive outside a retained session, no polling | Battery historian run on the reference device |
| Active-session network energy | keepalive 20 s while retained only | Budget reported per physical device rather than promised | Battery historian |
| Apk size contribution of the Firebase/Play stack | recorded per release, non-blocking | A large jump is a review trigger | Release artifact report |

`command` and the socket read loop allocate nothing in steady state beyond the command itself, and never perform disk I/O, diagnostic recording, or share-sheet work. A diagnostic offer is a non-suspending queue operation, and the rolling file is written on its own dispatcher.

## Startup work budget

| Work | Allowed on first frame? | Notes |
|---|---|---|
| Launch routing over Room/DataStore/Auth cache | Yes, synchronous and small | Must not open the database on the main thread for anything but the routing query; the query is single-row by primary key |
| Samsung session open | Started immediately, awaited off the first frame | The Remote surface renders `Connecting` |
| Discovery scan | Only when Discovery is the destination | Never started from Remote rendering |
| Entitlement refresh | Never on the launch path | Scheduled work or a result of an explicit user action |
| Local diagnostic record | A bounded, non-blocking open; nothing is written before the first frame | Recording is enqueue-only and never precedes rendering |
| Mapping or symbol upload | No | The R8 mapping file is uploaded to Play at build time, never at runtime |

## Regression policy

- Baseline profiles are committed for Remote, Discovery, and TvList, and refreshed when their composition changes materially.
- Macrobenchmark results are recorded per release on the reference device; a >20% regression on a control-path or launch target blocks promotion until explained.
- Physical matrix rows record reconnect, wake, and resume outcomes per television, including honest failures.
- Performance work never changes a product decision in [ui-ux.md](ui-ux.md) or a security control in [security.md](security.md). If a budget conflicts with a control, the control wins and the budget is renegotiated.
