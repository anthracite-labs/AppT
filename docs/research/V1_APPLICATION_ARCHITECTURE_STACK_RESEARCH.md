# AppT V1 application architecture and technology-stack research

**Status:** non-binding discovery report; no architecture or stack selected  
**Prepared:** 2026-09-22 (UTC)  
**Scope:** Android and iPhone V1, Samsung-first local TV control, future TV adapters  
**Authoritative inputs:** [`docs/PRODUCT.md`](../PRODUCT.md) and [`docs/PROJECT_STATE.md`](../PROJECT_STATE.md)

## Executive answer

AppT V1 is genuinely viable with more than one application-stack family, but it is **not** viable as a purely framework-managed, web-only, or “Expo Go” application. The decisive capability is a small, testable, platform-aware control seam that can perform LAN discovery, direct TCP/WebSocket/TLS work, Samsung pairing and token persistence, lifecycle-aware reconnect, Wake-on-LAN where supported, and secure storage without sending TV commands through a backend.

The viable families are therefore conditional:

- **Fully native Swift plus Kotlin/Jetpack** can reach every required OS API directly, at the cost of duplicated UI/domain integration and two native codebases.
- **Kotlin Multiplatform with native iOS and Android integration** can share protocol/domain/data logic while retaining native escape hatches. Compose Multiplatform can share UI, but native UI remains an equally valid KMP shape and keeps more phone-specific behavior direct.
- **React Native in a native-capable application** can work if the Samsung control path is implemented and tested as native modules or a native/shared core, not assumed to be JavaScript-only. Current React Native has a production New Architecture and first-party WebSocket support, but the native module, lifecycle, build, and device-test surface remains material.
- **Expo** is viable only as a custom development-build/CNG/native-project setup. Expo Go and a managed-only interpretation are not sufficient for AppT’s multicast entitlements, platform network controls, secure storage policy, Samsung-specific transport, and lifecycle work.
- **Flutter** can work with a native plugin boundary or a shared native protocol core. Dart sockets and WebSockets are useful, but platform permission prompts, multicast behavior, secure storage, lifecycle, and unsolicited background events still require platform-specific handling.
- **.NET MAUI** is technically viable through platform code, handlers, and native APIs, but this report found less AppT-specific evidence around the unusual Samsung/iOS LAN and lifecycle seam than for the other candidates. It should remain a conditional candidate, not be discarded or assumed.
- **Capacitor/Ionic or another WebView-first stack** is technically possible only when the critical control implementation is native. A WebView-only control path has the weakest fit for deterministic foreground lifecycle, low-level LAN discovery, secure transport identity, and native phone behavior.
- **A shared Rust/C++ protocol core with native UIs** is a serious architecture family rather than a UI framework. It can reduce duplicated wire-protocol logic while leaving discovery, secure storage, lifecycle, haptics, accessibility, keyboard, and buttons native. It adds FFI, toolchain, and debugging complexity.

No family can remove the largest unresolved risks:

1. Samsung’s current generic phone-remote behavior is not established by a current public Samsung protocol specification. The commonly used Tizen WebSocket remote is convergent community evidence, not a vendor compatibility guarantee.
2. Samsung generations and firmware differ. Pairing, ports, token behavior, encrypted/legacy flows, available commands, application discovery, text input, and wake behavior must be capability-detected and tested on real devices.
3. Samsung’s secure WebSocket path may expose a local certificate/trust problem. Product requirements prohibit a global certificate-verification bypass. Scoped identity verification or an explicit release decision is still required.
4. iOS multicast/broadcast discovery has privacy controls and a restricted entitlement, while iOS does not provide a general-purpose persistent background LAN session for a normal consumer remote.
5. The requirement that iPhone physical volume buttons control TV volume is a product/platform release risk. Apple documents that only the user can directly set system volume, and App Store guideline 2.5.9 says apps that alter standard Volume Up/Down switch behavior will be rejected. A framework choice cannot make that requirement safe; it needs a narrowly scoped native experiment and, if confirmed, a product decision or Apple-approved interpretation.

**Non-binding conclusion:** the next discovery step should test the Samsung and phone-platform seams first, then compare the cost of placing those seams behind each candidate’s native escape hatch. This report deliberately does not choose a winner, create an ADR, add dependencies, or prescribe implementation.

## 1. How to read this report

### Evidence labels

Claims are marked or described using the following categories:

- **Verified fact** — directly stated in an official platform/vendor document or a named project artifact that can be inspected.
- **Standards-based** — grounded in an open protocol or standards document, but not proof that a Samsung model implements it correctly.
- **Empirical unofficial** — repeated operational evidence from maintained open-source integrations, real-device reports, or community libraries; useful but not vendor-guaranteed.
- **Reverse-engineered** — a community reconstruction of an undocumented Samsung behavior or message format.
- **Engineering inference** — a reasoned implication for AppT architecture, not a platform promise.
- **Unverified / experiment required** — a claim that needs real TVs, physical iPhones/Android devices, entitlement approval, or release testing.
- **Product decision required** — a constraint or risk that technology cannot resolve on its own.

The report distinguishes a protocol being technically observable from it being appropriate to ship. Samsung’s terms/vendor position, App Store suitability, certificate policy, and user-facing permissions remain separate gates.

### Scope boundary

This is architecture/stack research, not an implementation plan. It does not:

- select a framework or runtime;
- define a binding ADR;
- add production code, a framework project, dependencies, or a disposable prototype;
- modify [`docs/PRODUCT.md`](../PRODUCT.md);
- claim that any Samsung model range is supported;
- claim that current Samsung Smart View SDK materials constitute a current generic remote-control contract.

## 2. Product and project constraints that drive the comparison

The approved product definition makes the control seam more important than the amount of generic UI code shared.

| Product constraint | Architecture consequence |
|---|---|
| Reliability is the primary constraint and the physical remote is the baseline. | The control path must be deterministic, observable, cancellable, and testable on real device/TV pairs. A framework’s UI productivity cannot compensate for an opaque or fragile transport seam. |
| Android and iPhone are desired when reliability is not materially compromised; Android-first is acceptable fallback. | Cross-platform delivery is a goal, not a reason to weaken the Samsung and iOS constraints. A credible Android+iPhone path must be demonstrated, not assumed from code-sharing percentages. |
| Normal control is local-first/offline after pairing; backend/public internet is not in the ordinary command path. | TV adapters, discovery, transport, pairing, capability state, and command dispatch live on each phone. Account APIs are not allowed to become a hidden command proxy. |
| Pairing secrets and device credentials remain on each phone; account sync is non-secret data only; phones pair independently. | Secret-bearing state must be separated from synchronizable profiles. Secure storage and backup/restore behavior need explicit tests. |
| Samsung first; future ecosystems behind adapters; connected TV is capability source of truth. | A platform-neutral TV capability model and adapter seam are useful hypotheses, but each adapter must own protocol quirks and capability evidence. |
| Discovery and setup should be automatic and nontechnical. | Discovery may need multiple mechanisms: Bonjour/mDNS, SSDP/UPnP, direct device-info probes, cached identity, and user-assisted fallback. The UI must not expose IP/port/protocol details in the ordinary path. |
| IP changes should be recovered through rediscovery; multiple TVs and friendly names are required. | Persist a stable device identity and last-known hints, not an IP as identity. Re-resolve addresses after network changes and verify the returned TV identity before sending commands. |
| WoL/WoW may be attempted, but ineffective power controls must be explained. | Wake is a best-effort capability, not proof that the TV is reachable. MAC capture, standby behavior, subnet/broadcast policy, and TV settings need per-model evidence. |
| Haptics, physical volume buttons, normal phone keyboard, one-handed use, and accessibility are product requirements. | Native OS integration and App Store suitability are first-class stack criteria. The iPhone volume-button requirement is especially consequential and unresolved. |
| No behavioral analytics in V1; diagnostics are explicit and redacted. | Avoid framework services that introduce telemetry by default. Logging must be structured around redaction and never treat raw local-network or token data as ordinary debug context. |
| Samsung protocol/legal review is required before public release. | An open-source implementation can accelerate research but does not by itself establish permission to ship or long-term compatibility. |

## 3. Samsung-first research, independently of application frameworks

### 3.1 Evidence tiers

| Tier | What is supported | What it does **not** prove |
|---|---|---|
| Samsung-documented | Samsung’s historical Smart View sender materials document same-Wi-Fi discovery, selecting a service, application launch/communication, WoW/WoWLAN behavior after a previous connection, MAC retention, reconnect/error states, TLS-capable Smart View SDK versions, and TV-side model-dependent key capability behavior. | They do not establish a current, generic, vendor-supported phone remote API for every Samsung television, nor a current guarantee for a third-party app that only sends remote keys. |
| Standards-based | mDNS/DNS-SD, SSDP/UPnP discovery, TCP/UDP, WebSocket, and TLS provide interoperable building blocks. | A Samsung TV may omit, alter, filter, or version these services. Standards compliance does not imply a supported Samsung command set. |
| Empirical unofficial | OpenHAB, Home Assistant, maintained community libraries, and real-device reports converge on several local Samsung/Tizen behaviors and expose model-specific failures. | Community convergence is not a Samsung compatibility or legal guarantee. The code may accept unsafe TLS, rely on old firmware, or implement only a subset of models. |
| Reverse-engineered | The `/api/v2/channels/samsung.remote.control` channel, 8001/8002 WebSocket patterns, base64 client names, approval/token events, and `ms.remote.control` JSON command shape are reconstructed from observed behavior and community code. | Exact version support, certificate identity semantics, command completeness, token lifetime, app/text/touch behavior, and future firmware compatibility remain unknown until tested. |

### 3.2 Samsung-documented findings

**Verified fact — historical Smart View sender workflow.** Samsung’s iOS Sender App page describes three core sender operations: discover a compatible Smart TV on the same Wi-Fi network, launch a TV application, and communicate with a TV application. It describes starting discovery, receiving service-added/service-removed events, showing a device list, selecting a service, and then connecting or retrieving application information. [S03]

**Verified fact — Samsung’s official material is receiver/app oriented.** The same page describes communication with a TV application and application IDs, not a current universal phone remote command contract. The page is therefore useful evidence for discovery, local communication, and lifecycle concepts, but not proof that a third-party AppT can use a supported generic remote API across current TVs. [S03]

**Verified fact — WoW/WoWLAN is documented in the Smart View material.** Samsung’s Enhanced Features page says a previously discovered/connected Tizen TV can be shown as a standby device, that the mobile device stores TV MAC information after a successful connection, and that a subsequent connect attempt can wake the TV and wait for an updated service. It describes different timing/error behavior, including an approximately one-minute discovery/reconnect window in the historical SDK workflow. It also notes older model variations, including LAN-cable WoL for some 2016 TVs. [S04]

**Verified fact — official Smart View TLS support exists historically.** Samsung’s Enhanced Features page documents secure sender/receiver connections through WSS and HTTP when using versions of its mobile/TV libraries that support the feature. The download page records Android mobile package 2.5.34 dated 2024-07-25 and iOS mobile package 3.1.1 dated 2023-04-05, with older release notes for TLS and iOS MDNS crash fixes. This is evidence that Samsung shipped TLS-capable Smart View components, not evidence that AppT can reuse them or that their current remote coverage is sufficient. [S04] [S05]

**Verified fact — official debugging endpoint.** Samsung’s debugging page documents querying `http://TV_IP:8001/api/v2/` from a browser on the same network and says that 404/500 responses can indicate that the Smart View SDK server is not working. It is a useful research probe for compatible generations, but the page is receiver/SDK debugging material and should not be treated as a universal public device contract. [S06]

**Verified fact — TV-side key capabilities vary.** Samsung’s Remote Control page says supported keys can be queried on the TV side, documents key names/codes, and notes long-press variations. Samsung’s User Interaction Q&A explicitly warns that key-code values can differ by TV model and platform version and recommends key names where possible. This supports capability-driven behavior and argues against hard-coding a model-year feature matrix. [S01] [S02]

**Verified fact — official material records historical generation failures.** Samsung’s download/release notes include a 2015 TV firmware issue where iOS and JavaScript APIs could not discover/connect until updated libraries were used. The existence of an official historical compatibility fix is evidence that firmware/model cohorts matter; it is not a current support promise. [S05]

**Verified fact — legacy API surface exists.** Samsung’s legacy API reference archive lists older UPnP/Convergence/remote-related classes and describes older Smart TV application APIs. This is useful when investigating pre-Tizen/legacy cohorts, but the archive is explicitly historical and does not establish universal support for current consumer TVs. [S07]

### 3.3 Standards-based building blocks

- **mDNS:** Multicast DNS is specified by [RFC 6762](https://www.rfc-editor.org/rfc/rfc6762.html). It uses link-local multicast and is subject to network filtering and mobile-OS privacy/power rules.
- **DNS-SD/Bonjour:** DNS-Based Service Discovery is specified by [RFC 6763](https://www.rfc-editor.org/rfc/rfc6763.html). It can advertise a service while allowing address/TXT records to change; it is not an identity proof by itself.
- **SSDP/UPnP:** SSDP is part of the UPnP Device Architecture family. It is a UDP multicast/broadcast discovery mechanism and is commonly deployed by consumer devices, but it can be disabled or filtered by access points, guest networks, and VLANs. AppT should treat discovered metadata as a candidate, not an authenticated TV identity.
- **WebSocket:** The wire protocol is standardized by [RFC 6455](https://www.rfc-editor.org/rfc/rfc6455.html). A compliant WebSocket client can still fail on a TV-specific handshake, endpoint, message schema, or authentication behavior.
- **TLS:** TLS 1.3 is specified by [RFC 8446](https://www.rfc-editor.org/rfc/rfc8446.html). TLS encryption and certificate identity verification are separate questions; accepting an encrypted connection while skipping identity verification is not equivalent to secure peer authentication.
- **Wake-on-LAN:** The Ethernet “magic packet” convention is widely implemented but is not a guarantee that a sleeping TV, Wi-Fi chipset, access point, or subnet will wake. Samsung’s own WoW material confirms that wake depends on prior discovery/connection and TV behavior for the documented SDK path. [S04]

### 3.4 Unofficial and reverse-engineered Samsung evidence

**Empirical unofficial — modern Tizen WebSocket control.** The actively maintained [`samsung-tv-ws-api`](https://github.com/xchwarze/samsung-tv-ws-api) project describes support for post-2016 Tizen TVs and, at the observed repository state reported as version 3.0.6 on 2026-09-11, contains current code, tests, encrypted/legacy support, and command/application documentation. Its README and command docs report WebSocket ports 8001/8002, token handling, remote keys, and application operations. This is valuable empirical evidence, but the project is not Samsung and its LGPL license/implementation details would require separate review before reuse. [U01] [U02]

**Empirical unofficial — operational integrations.** OpenHAB documents automatic discovery through UPnP, legacy interfaces for older TVs, WebSocket on 8001 or secure WebSocket on 8002 for newer TVs, user approval, persisted tokens, MAC addresses, and model-dependent channel failures. Home Assistant maintains a Samsung TV integration and exposes a directory with bridge, remote, diagnostics, and coordinator code. These projects demonstrate that local control is operationally possible, while also demonstrating the need for fallbacks and device-specific handling. [U03] [U04]

**Reverse-engineered — common control shape.** Community clients such as `vrachieru/samsung-tv-api` and `stefanoj3/samsungremote` describe a URL shaped like `/api/v2/channels/samsung.remote.control`, base64-encoded client name, a first connection requiring TV approval, a returned token for later calls, and JSON remote-key messages using the `ms.remote.control` method. The Go project explicitly reports testing only one TV model; the Python project’s last commit is from 2018. This makes them useful protocol archaeology, not support evidence. [U06] [U07]

**Reverse-engineered — port/security variation.** Community projects and OpenHAB commonly report `ws://` on 8001 and `wss://` on 8002, but they disagree on which models require which path, whether an approval popup appears, and whether token authentication is available. Some clients disable certificate verification to work around local TV certificates. That workaround is specifically incompatible with AppT’s “no global certificate-verification bypass” requirement and must not be copied as an architecture assumption. [U01] [U03] [U06]

**Empirical unofficial — legacy/encrypted fallback.** Recent Home Assistant issue and pull-request reports describe a 2016 K-series TV whose device information did not advertise `TokenAuthSupport`, whose attempts on ports 8001/8002 timed out or returned unauthorized, and whose community implementation succeeded through an encrypted CloudPINPage flow with a session identifier and control on another port. These are user/maintainer reports rather than vendor specifications, but they are strong warning evidence against a “modern token WebSocket for every post-2016 TV” assumption. [U04]

### 3.5 Samsung generation and capability model

The following is a **research cohort model**, not a support promise:

| Research cohort | Evidence and likely investigation | AppT implication |
|---|---|---|
| Older/legacy Samsung Smart TV interfaces, often pre-Tizen | Samsung’s legacy archive and OpenHAB report older UPnP/legacy control, with different ports and feature surfaces. [S07] [U03] | Keep a legacy adapter candidate separate. Do not let a legacy probe send functional commands without explicit user consent if it is unfamiliar. |
| Early Tizen/2015–2017 cohorts | Samsung records historical 2015 discovery/connect breakage; community projects report model-specific 8001/8002 and authentication differences. [S05] [U04] [U06] | Test at least one 2015/2016 set and a K-series set. Expect approval, encrypted fallback, and command differences. |
| Modern token-capable Tizen cohorts | Convergent community evidence reports device info, secure WebSocket, approval, reusable token, remote keys, and application channels. [U01] [U02] [U05] | Candidate first adapter, but capability-detect `TokenAuthSupport`, remote availability, application support, text support, and security identity. |
| Newer/firmware-divergent cohorts | Current community issue reports include connection failures, silent authorization failures, and behavior that differs from model expectations. [U04] | Firmware version and observed behavior belong in diagnostics and test fixtures. Avoid year-only support claims. |
| Frame/art/special-purpose models | OpenHAB lists art-mode channels and notes that many channels do not work on all TVs. [U03] | Treat app launch, art mode, channel state, and power-on as optional capabilities, not baseline remote controls. |

The adapter should expose demonstrated capabilities such as:

- discovery identity and confidence;
- pairing state and supported pairing flow;
- secure/insecure transport availability;
- directional/remote key set;
- press/hold/repeat semantics;
- application discovery and launch;
- text-input mode and character support;
- touchpad/swipe support;
- state/feedback channels;
- wake and power-off behavior;
- reconnect and re-pair requirements.

That model is an architectural hypothesis for research, not a selected implementation shape.

### 3.6 Samsung implications for AppT

1. **Probe before command.** A device-info response, discovery advertisement, model name, or open port is not enough to expose a working remote. The adapter should establish what it can prove and keep experimental probing separate from functional commands.
2. **Use capability detection rather than model assumptions.** Samsung’s own key documentation and community integration failures support this directly. [S01] [S02] [U03] [U04]
3. **Persist a local TV identity separately from the IP.** A last-known IP is a hint. A MAC, device identifier, certificate/public-key fingerprint, and observed model/firmware tuple may contribute to identity, but each is subject to availability and change. Identity verification must fail closed where possible.
4. **Treat tokens as password-equivalent local secrets.** Store per-phone, per-TV, never in account-sync payloads or ordinary logs. Rotate/update only after a verified pairing response.
5. **Do not copy unsafe TLS workarounds.** The community’s `CERT_NONE`/`rejectUnauthorized: false` patterns are evidence of a practical problem, not an acceptable AppT security policy. The exact TV certificate and a scoped pinning strategy are experiment gates.
6. **Expect foreground recovery.** The phone should close/recreate sockets after lifecycle transitions and re-resolve the TV after network changes. Persistent background control is not a baseline assumption on iOS and is battery/policy-sensitive on Android.
7. **Keep Samsung protocol code behind one adapter boundary.** This contains undocumented changes and makes later ecosystems less likely to leak Samsung-specific names into the product/UI model.

## 4. Mobile operating-system constraints independent of frameworks

### 4.1 iPhone / iOS

#### Local network permission and discovery

Apple describes iOS local-network privacy as a user-controlled permission for discovering and communicating with devices on the immediate network. Apple recommends Bonjour/DNS-SD through Network.framework for normal service discovery, requires declared Bonjour service types and a reason string, and says custom multicast/broadcast protocols on physical devices require the restricted `com.apple.developer.networking.multicast` entitlement and Apple approval. [S23] [S24]

Apple’s local-network FAQ also states that outgoing local TCP/UDP and Bonjour operations are covered by local-network permission, while the multicast entitlement and user consent are distinct controls. This means an AppT design that scans an entire subnet or uses SSDP cannot assume that “ordinary internet networking permission” is enough on iOS. [S28]

**Inference:** direct unicast probes can reduce dependence on the restricted multicast entitlement, but they still require a transparent local-network permission flow and have trade-offs: scan size/timing, privacy perception, battery, subnet/VLAN reachability, IPv6 support, and false positives. A Bonjour-only strategy may not discover Samsung TVs that do not advertise the required service type. A production discovery plan may need multiple paths and an explicit permission rationale.

#### Local sockets, WebSocket, and TLS

Network.framework supplies `NWConnection` for bidirectional endpoint connections and `NWProtocolWebSocket` for WebSocket protocol configuration. [S25] [S26] This is a direct native escape hatch available to every candidate stack through Swift/Objective-C modules.

**Unresolved security issue:** Samsung’s secure port behavior must be tested with normal certificate validation. AppT must determine whether the TV presents a stable identity that can be scoped/pinned safely, whether the certificate changes after reboot/firmware/IP changes, and whether a public CA or local trust anchor is involved. A framework that makes WSS easy does not resolve trust semantics.

#### Lifecycle/background

Apple’s current UIKit guidance says apps transition to background and are then suspended; background apps should do as little as possible, release shared resources, close listening sockets/Bonjour registrations, and resume appropriate work when foregrounded. [S31] Apple’s archived networking technote is unusually explicit: a suspended app executes no code, sockets may be reclaimed, and a simple foreground app should generally close/reopen sockets around lifecycle transitions. [S27]

**Inference:** AppT’s ordinary command path should be foreground-first. A remote connection can be kept alive while the remote is visible if testing proves it useful, but the design must tolerate suspension, socket loss, process termination, and reconnection on resume. A long-lived background listener or “keep the TV session alive forever” is not a valid baseline for App Store suitability. Apple App Review guideline 2.5.4 limits background services to intended categories such as audio, location, task completion, and similar purposes. [S32]

#### Secure storage

Apple Keychain Services provides encrypted secret storage; accessibility classes control whether an item is available while locked and whether it can migrate in backup. Apple recommends choosing the most restrictive class that fits the use case; `ThisDeviceOnly` prevents migration to another device. [S29] [S30]

**Inference:** the Samsung pairing token should be in a Keychain item with a deliberate accessibility/migration policy. The choice must balance “control after first unlock” against fail-closed device-local credentials and user expectations after restore. A cross-platform wrapper is acceptable only if it exposes these semantics or the native layer owns the secret.

#### Physical volume buttons

Apple’s `AVAudioSession.outputVolume` documentation says only the user can directly set system volume and recommends `MPVolumeView` for a system-volume interface. `MPVolumeView` reflects device-volume buttons while sound is playing; it is not a public API for rerouting those buttons to a third-party TV. [S33] [S34]

Apple App Review guideline 2.5.9 states that apps altering or disabling standard switch behavior, including Volume Up/Down, will be rejected. [S32]

**Product decision required:** “physical iPhone volume buttons control TV volume while the remote is active” is not a normal native integration equivalent to Android foreground key handling. It is a probable App Store conflict if it suppresses or repurposes the system volume behavior. A native experiment using only public APIs and a direct App Review/Apple platform interpretation is needed before this requirement can remain in the iPhone V1 acceptance boundary.

#### Keyboard, haptics, accessibility

UIKit provides native text fields and input methods; Core Haptics provides custom transient/continuous patterns; Apple’s VoiceOver guidance requires labels, hints, grouping, state descriptions, and actual VoiceOver audits. [S35] [S36]

**Inference:** all candidate frameworks can invoke these capabilities, but native Swift provides the shortest path to exact behavior. A shared UI must be tested with VoiceOver, Dynamic Type, system text input, haptics disabled/enabled, and one-handed layout on physical devices.

### 4.2 Android

#### Local network access and future permission enforcement

Android’s current local-network protection documentation says raw sockets, mDNS, SSDP, `NsdManager`, TCP, UDP unicast, multicast, and broadcast to/from local addresses are all in scope. It describes Android 16 as an opt-in preparation phase and Android 17 (API 37) as mandatory for apps targeting API 37 or higher, with a new `ACCESS_LOCAL_NETWORK` runtime permission. It also describes a system-mediated `NsdManager` picker for a specific service, while complex IoT/home-automation use cases generally need broad local-network permission. [S13]

Android’s Android 16 behavior-change page gives the same direction and explicitly includes raw sockets and networking libraries such as OkHttp/Cronet in the restriction boundary. The exact enforcement schedule and device behavior must be rechecked against the target release when AppT sets its minimum/target SDK. [S14]

Android 13+ Wi-Fi APIs also use `NEARBY_WIFI_DEVICES`, while older scan APIs can require location permission and Wi-Fi scan throttling. `NsdManager` implements DNS-SD over mDNS and is asynchronous. [S15] [S16]

**Inference:** Android is not “free LAN access” as a durable assumption. A candidate stack must expose permission declaration/request timing and handle denial/revocation. A Samsung remote needs broad/direct access more often than a single system-selected cast target, so the AppT UX cannot assume the privacy-preserving picker will cover every flow.

#### Multicast, power, and lifecycle

Android documents that `NsdManager` mDNS operations historically required a `WifiManager.MulticastLock`; recent SDK extensions automatically manage multicast reception for foreground discovery, while background apps should avoid holding the lock unless necessary. The `MulticastLock` itself only affects receipt of multicast packets and can cause noticeable battery drain. [S16] [S17]

Android Doze suspends network access and defers ordinary jobs/alarms while the device is idle; Android recommends adapting persistent real-time connections and testing Doze/App Standby. [S18]

Android activity lifecycle guidance explicitly uses closing a network connection on leaving an activity and reconnecting on return as an example pattern. [S19]

**Inference:** AppT should discover/connect while the remote is active, release multicast resources promptly, and make reconnect idempotent. A foreground service might keep a connection alive for a user-visible, ongoing operation, but it adds a notification, policy, power, and product burden; it should not be the default answer for a remote that is merely in the background.

#### Secure storage

Android Keystore keeps key material difficult to extract, can use secure hardware, and supports restrictions such as requiring user authentication for cryptographic use. [S20]

**Inference:** use Keystore-backed encryption or a platform secure credential design for Samsung tokens; do not equate ordinary preferences/database encryption with a Keystore-backed secret. Backup/restore and device migration need explicit policy tests.

#### Hardware volume buttons, keyboard, haptics, accessibility

Android’s `KeyEvent` API models hardware key/button events and includes volume key codes; Android’s keyboard guidance says hardware key events can be handled in an Activity, while soft IMEs are not guaranteed to produce key events. [S21A] [S21B]

**Inference:** Android foreground handling of volume keys is technically plausible, but consuming or suppressing system volume behavior, Samsung One UI behavior, media sessions, accessibility services, and lock-screen/background cases requires device testing. It should not be generalized from an emulator or one Pixel device. Android text fields/IME, haptic feedback, TalkBack semantics, and large touch targets have direct native support; Compose/View choices affect polish but not feasibility.

## 5. Logical seams to preserve regardless of stack

The following seams are research hypotheses that let a chosen UI/runtime vary without moving secrets or Samsung quirks into the wrong layer:

1. **Product/UI layer** — onboarding, friendly device cards, remote modes, favourites, capability-based presentation, accessibility, haptics preference, volume-button preference, and account screens.
2. **TV domain model** — TV profile, stable identity confidence, friendly name, observed capabilities, connection state, last-known hints, and redacted diagnostic summary.
3. **Discovery coordinator** — mDNS/DNS-SD, SSDP/UPnP, direct unicast probes, cached identity, network path changes, scan cancellation, permission state, and result deduplication.
4. **Ecosystem adapter** — Samsung first; later adapters implement their own discovery, pairing, capability mapping, command vocabulary, state feedback, app launch, text, and wake semantics.
5. **Pairing/token state** — explicit state machine for unpaired, approval pending, paired, token rejected, identity changed, and re-pair required. Secrets are handles/opaque values to higher layers.
6. **Transport** — TCP/WebSocket/TLS framing, certificate/identity verification, serialized writes, ping/timeout, cancellation, reconnect backoff, and redacted errors.
7. **Secure local credential store** — Keychain/Keystore-backed storage with backup/migration policy and atomic update/delete behavior.
8. **Capability model** — an evidence-bearing set of supported controls; absence/unknown is different from unsupported and from temporarily unavailable.
9. **Local-first state store** — local TV profiles, preferences, favourites, and command/session state; no network dependency for ordinary control after pairing.
10. **Account sync boundary** — non-secret friendly names, favourites, preferences, and settings only. It must reject pairing tokens, private keys, local IPs, SSIDs, raw command contents, and sensitive TV identifiers.
11. **Native phone integration** — lifecycle, permission prompts, secure storage, haptics, keyboard/text input, accessibility, OS volume behavior, and any background capability.
12. **Diagnostics** — redaction at the boundary, explicit export/request-support action, no behavioral analytics, and testable guarantees that tokens and local network details cannot enter ordinary logs.

These seams are not an architecture decision. They identify what a candidate must be able to represent and test.

## 6. Candidate families

### A. Fully native Swift + Kotlin/Jetpack

Two platform applications share product vocabulary and protocol specifications, but implement OS integrations natively. Samsung control can use Swift Network.framework/URLSession or carefully scoped lower-level APIs on iOS and Kotlin/Java networking/Android APIs on Android. Secure storage, lifecycle, keyboard, accessibility, haptics, and volume behavior are direct.

**Strength:** least abstraction at the reliability-critical seam.  
**Cost:** duplicated UI/domain glue, two release toolchains, and a higher risk that adapter behavior diverges unless the protocol/capability contract and fixtures are shared.

### B. Kotlin Multiplatform; native UI or Compose Multiplatform UI

KMP can share domain, serialization, state machines, protocol logic, and parts of networking while compiling shared code to Kotlin/JVM on Android and Kotlin/Native on iOS. JetBrains documents Android/iOS core KMP as stable and Compose Multiplatform Android/iOS UI as stable; KMP can also leave UI and platform code native. [S49] [S50] [S51]

Ktor supplies multiplatform clients with platform-specific engines; the engine table shows Android/OkHttp or Android options and Darwin/NSURLSession on Apple platforms, with different WebSocket/TLS capabilities. [S52]

**Strength:** a natural shared-core/native-shell split without a JavaScript bridge; strong fit if the team can operate Kotlin/Native and Swift interop.  
**Cost:** Gradle/Xcode/Swift boundary, Kotlin-to-Swift API ergonomics, native platform source sets, Compose iOS accessibility/interop validation, and build/test complexity. Shared code does not remove platform-specific discovery, entitlement, secure storage, or lifecycle work.

### C. React Native (native-capable/bare)

React Native shares UI and JavaScript/TypeScript application logic while exposing native modules/components. Current React Native’s New Architecture is the default direction; React Native 0.82 became New-Architecture-only, and 0.87 (released 2026-08-11) makes the strict TypeScript API default while still requiring native iOS/Android toolchains. [S38] [S40]

React Native documents WebSocket support and `AppState` foreground/background notifications. [S37] [S39]

**Strength:** broad product/UI ecosystem, fast iteration, native modules/JSI/TurboModules, and a mature cross-platform model.  
**Cost:** the reliability seam crosses JS/native/runtime boundaries; protocol work must not depend on JS being alive while suspended; native modules, entitlements, secure storage semantics, and Android/iOS lifecycle must be maintained and device-tested. The New Architecture reduces old bridge concerns but does not make native OS behavior cross-platform.

### D. Expo (React Native distribution/tooling family)

Expo SDK 57 is documented as the latest stable reference and pins a specific React Native version (the observed table pairs SDK 57 with RN 0.86), while RN itself has moved to 0.87. Expo releases three times a year and aligns to one RN version per SDK. [S41]

Expo explicitly says Expo Go cannot use third-party libraries requiring custom native code and recommends development builds for production-grade work. CNG/prebuild/config plugins can add native code and configuration, but direct native changes can be overwritten unless represented through the supported module/config mechanism. [S42] [S43]

**Strength:** useful React Native workflow, native module API, development builds, config plugins, and release tooling.  
**Cost:** version pinning and config-generation surface are additional maintenance seams. AppT cannot use an Expo Go-only workflow. The Samsung control module, iOS multicast entitlement, local-network usage description, Android local-network permission, certificate configuration, and any lifecycle integration must be represented in native modules/config plugins and validated in custom builds.

### E. Flutter

Flutter renders its own UI and uses Dart for application logic. It supports sockets/WebSockets through Dart packages and platform channels/plugins for native APIs. Flutter documents asynchronous MethodChannel/Pigeon boundaries, platform-side background task queues, and the fact that platform channel calls are serialized messages. [S44] [S45]

Flutter’s current documentation includes an Android local-network-permission guide specifically warning that Dart sockets cannot display the Android runtime permission prompt and that permission must be requested before opening a local socket. It also documents that background isolates cannot receive unsolicited host-platform messages in the general case. [S46] [S47]

Flutter 3.47 is a current 2026 stable release and has ongoing iOS lifecycle/toolchain changes; its release notes/blog emphasize native integration and platform migration work. [S48]

**Strength:** high UI consistency, strong widget testing, direct Dart sockets for some work, and a clear plugin/native escape hatch.  
**Cost:** platform channels/serialization and isolate/lifecycle behavior can become part of the control path; iOS native permission/entitlement and certificate handling still need plugins; text input, VoiceOver/TalkBack, haptics, and OS-specific controls require real-device validation. A native plugin or shared native core should own the Samsung session if control reliability is primary.

### F. .NET MAUI

Microsoft documents .NET MAUI as a C#/XAML framework for Android, iOS, macOS, and Windows with direct access to platform APIs, handlers, platform source, and native app packaging. It provides cross-platform connectivity and secure-storage abstractions, while still requiring platform-specific setup and handling platform differences such as Android backup behavior. [S55] [S56]

**Strength:** native-capable C# stack, shared business logic/UI, platform access, and mature .NET testing/tooling.  
**Cost:** AppT-specific evidence for Samsung LAN discovery, local-network entitlement/permission details, WSS trust pinning, and lifecycle behavior is thinner in this research. The abstraction must be checked rather than assumed to cover SSDP/mDNS, raw sockets, WoL, and hardware buttons. A team without current .NET/iOS expertise would carry an additional staffing risk.

### G. Capacitor/Ionic or WebView-first hybrid

Capacitor builds a web UI into native iOS/Android projects and exposes native functionality through plugins. Its documentation says developers are encouraged to write Swift/Java/Kotlin custom native code and compile through Xcode/Android Studio; current v8 is active with iOS 15+/Android 7+ minimums. [S57] [S58] [S59]

**Strength:** web UI ecosystem, fast iteration, fully open native project, and straightforward custom plugin escape hatches.  
**Cost:** a WebView is another process/runtime boundary for the primary remote UI and event flow. Local LAN permissions, low-level discovery, TLS identity, background/lifecycle, keyboard, haptics, and secure storage must cross plugins. A native plugin can make it technically viable, but then the core reliability case is native while the WebView mainly supplies UI.

### H. Shared Rust/C++ protocol core with native UI shells

A portable protocol/state library can share parsing, command serialization, adapter state machines, redaction rules, and test fixtures. Swift/Kotlin/native shells own discovery APIs, secure storage, permissions, lifecycle, haptics, accessibility, text input, and OS-specific volume behavior.

**Strength:** isolates the most deterministic, cross-platform logic without forcing UI or lifecycle through a cross-platform runtime.  
**Cost:** FFI bindings, memory/concurrency ownership, build and symbol packaging, debugging across languages, and native platform duplication. It is a candidate architecture pattern that can complement native, React Native, Flutter, or .NET UI rather than a replacement for them.

## 7. Common comparison matrix

The matrix uses `Direct` for a platform capability available without a framework-specific native escape hatch, `Native seam` when the family can support it but the implementation belongs in Swift/Kotlin/native code, `Conditional` when feasibility depends on a custom plugin/core and more testing, and `Risk` for a material unresolved burden. The labels are comparative observations, not weighted scores or a ranking.

| Criterion | Native Swift + Kotlin | React Native | Expo | Flutter | KMP / Compose Multiplatform | .NET MAUI | Capacitor/WebView | Shared Rust/C++ core + native UI |
|---|---|---|---|---|---|---|---|---|
| **LAN discovery** | **Direct.** Network.framework, Bonjour, BSD/UDP/TCP, Android NSD/raw sockets can be selected per platform. | **Native seam.** JS can coordinate results, but discovery should live in native modules with explicit permission/lifecycle events. | **Native seam + config.** Custom module and config plugin/development build required; Expo Go is insufficient. | **Native seam.** Dart can perform some sockets, but native permission and discovery plugins are still needed. | **Native seam.** Common discovery state/DTOs; actual iOS/Android mechanisms in source sets/native shells. | **Native seam.** Platform code/handlers can expose discovery; verify APIs and packaging. | **Native plugin.** WebView cannot be trusted as the discovery authority; native plugin required. | **Best split.** Native shells discover; core consumes normalized candidates. |
| **SSDP / mDNS / multicast / broadcast** | **Direct but permission-sensitive.** iOS restricted multicast entitlement for custom multicast/broadcast; Android current/future local-network rules and multicast lock/NSD behavior. | **Native seam.** JS libraries do not bypass OS entitlements or permissions. | **Native seam/config plugin.** Entitlements and manifests must be generated and audited. | **Native seam.** Dart multicast may work only after native permission/entitlement setup; Samsung SSDP needs real-device tests. | **Native seam.** Common interfaces, platform implementations. | **Native seam.** Abstractions do not remove iOS/Android permission differences. | **Native plugin.** Browser/WebView multicast is not a viable baseline. | **Do not put raw multicast in the shared core.** Pass sockets/candidates across a narrow interface. |
| **Direct TCP/WebSocket/TLS** | **Direct.** First-party APIs and native trust configuration. | **JS WebSocket exists; native module recommended for TLS/cert identity and long-lived session.** | **Same as RN, plus custom native build/config.** | **Dart WebSocket exists; native plugin for trust policy and lifecycle-sensitive session is prudent.** | **Ktor/common or native engines; platform engine differences must be tested.** | **.NET sockets/WebSockets plus native handlers; certificate policy needs explicit audit.** | **Native plugin should own it; browser WebSocket semantics are a poor control authority.** | **Strong for framing/parser, but socket/TLS object and trust callback should remain platform-owned.** |
| **Samsung pairing / tokens / WSS** | **Direct control of state machine and secure store.** | **Native TurboModule/JSI or native service should own token and session; never ordinary JS logs/storage.** | **Custom Expo module and build; no Expo Go path.** | **Native plugin or carefully isolated Dart service; bridge event semantics need testing.** | **Good shared state machine; platform secure storage and trust callbacks remain actual implementations.** | **Shared service possible; Android/iOS secure storage and TLS callbacks require platform code.** | **Plugin owns token/session; WebView must receive only redacted state.** | **Good shared protocol/token state if secret never crosses logs/unsafe FFI; store remains native.** |
| **Reconnection / IP changes** | **Direct; explicit scene/activity/network callbacks and address re-resolution.** | **Native session emits state to AppState/UI; JS must tolerate suspension/recreation.** | **Same plus CNG/module lifecycle configuration.** | **App lifecycle plus plugin callbacks; isolates/event streams need care.** | **Shared state machine with native lifecycle adapters; KMP coroutine cancellation/Swift exposure need tests.** | **Lifecycle services/handlers; verify background and process restoration.** | **WebView lifecycle adds another suspension/reload failure mode; native plugin should reconnect.** | **Core can define idempotent state transitions; native shells trigger them.** |
| **WoL / WoW / MAC handling** | **Direct UDP/native packet path and secure local persistence.** | **Native module for broadcast/unicast and Android/iOS permissions; JS only requests wake.** | **Custom native module/config; no managed-only assumption.** | **Dart UDP can send packets, but TV/network/permission behavior is native-tested.** | **Common packet/decision logic; platform interface for interface/broadcast policy.** | **C# UDP plus platform-specific network details.** | **Native plugin required.** | **Core can create magic packet; native shell chooses interface, destination, and permission policy.** |
| **iOS lifecycle / local-network permission** | **Direct and clearest.** | **Native modules/AppState; exact scene/permission behavior must be owned/tested natively.** | **Config/plugin correctness adds risk; custom dev build required.** | **Plugin/runner integration; Dart lifecycle is not a substitute for scene delegate behavior.** | **Native iOS shell/source set; shared code must not assume background execution.** | **Platform-specific lifecycle and entitlements; verify current iOS templates.** | **WebView/bridge lifecycle adds complexity; native plugin must survive reload and foreground.** | **Native shell owns all iOS policy; core is agnostic.** |
| **Android lifecycle / local-network permission** | **Direct.** NSD, multicast locks, API 36 opt-in/API 37 permission, Doze, foreground-service choices. | **Native module/activity/service; JS AppState is notification, not OS authority.** | **Same plus manifest/config plugin and SDK pinning.** | **Native permission plugin before Dart sockets; background isolate limits matter.** | **Android source set/native shell; common code receives capability/permission results.** | **Android platform code/permissions; validate MAUI lifecycle callbacks.** | **Native plugin/manifest; WebView inherits host permission state.** | **Native shell owns permission and sockets; core receives events.** |
| **Secure storage** | **Direct Keychain/Keystore.** | **Native module or audited secure-storage library; module API must preserve accessibility/backup semantics.** | **Expo package/custom module may help, but audit implementation and config.** | **Plugin backed by Keychain/Keystore; audit package and backup behavior.** | **expect/actual/native storage; good separation but more APIs to maintain.** | **ISecureStorage exists; inspect Android backup and iOS keychain policy.** | **Native plugin, not Web Storage.** | **Native store only; never put secret material in the shared core’s ordinary state.** |
| **Local-first / account-sync boundary** | **Direct and explicit.** Separate local DB/secure store from account DTOs. | **Good if state/store layers are disciplined; JS persistence can accidentally leak secrets.** | **Same; Expo services must not become hidden command path.** | **Good state architecture; keep token in plugin/native store.** | **Natural shared domain/data layer; expose non-secret sync models separately from secure actuals.** | **Good shared C# domain; platform storage boundary still required.** | **Web storage is unsuitable for tokens; native plugin boundary mandatory.** | **Excellent for pure non-secret domain/sync models; native app owns secret storage.** |
| **Native OS integrations** | **Direct.** | **Native modules/components; more surface area and bridge/event testing.** | **Custom modules/config plugins; strongest native access only outside Expo Go.** | **Platform plugins/channels; bridge serialization and lifecycle.** | **Direct iOS/Android interop in native source sets; Compose interop available.** | **Handlers/platform code; direct APIs available.** | **Plugins; very explicit bridge boundary.** | **Native shell by design.** |
| **Haptics** | **Direct Core Haptics/UIKit/Android haptic APIs.** | **Mature native modules; exact behavior still device-tested.** | **Expo haptics plus native escape hatch; custom native may be needed for policy.** | **Plugins/system feedback; device variance.** | **Native actuals or Compose APIs; test iOS interop.** | **Essentials/platform APIs; exact effect needs device testing.** | **Plugin; Web Haptics is not enough for native reliability.** | **Native UI owns feedback.** |
| **Accessibility** | **Direct VoiceOver/TalkBack semantics and platform UI controls.** | **Good cross-platform semantics, but custom remote/touchpad controls need native audits.** | **Same RN semantics plus custom native components.** | **Flutter semantics are strong but must audit VoiceOver/TalkBack and custom gesture controls.** | **Native UI has strongest baseline; Compose iOS semantics are stable but still need audits and interop tests.** | **Native-backed controls/handlers; test both screen readers.** | **Web accessibility plus WebView/native accessibility boundaries; highest custom-control audit burden.** | **Native shells own semantics.** |
| **Physical volume buttons** | **Android foreground path plausible; iOS is a public-API/App Review risk.** | **Android native module; iOS cannot be made safe by RN.** | **Same, with custom native code; Expo does not change Apple policy.** | **Platform plugin; same iOS product blocker.** | **Platform-specific; same iOS product blocker.** | **Platform-specific; same iOS product blocker.** | **Plugin; same iOS product blocker.** | **Native shell; same iOS product blocker.** |
| **Keyboard / Samsung text input** | **Direct native input and Samsung-specific command adapter.** | **TextInput is good, but Samsung command encoding/session stays native.** | **Same with custom module.** | **TextField/IME good, but platform plugin for TV text wire format.** | **Native UI or Compose text fields; shared state mapping possible.** | **Native-backed Entry/Editor; adapter still platform-aware.** | **Web input works for phone UI, but TV text command must cross plugin.** | **Core handles wire encoding; native UI/IME owns text collection.** |
| **Reliability / determinism** | **Fewest runtime boundaries; duplicated logic can drift.** | **Viable only when native control service is authoritative; JS UI cannot be session authority.** | **Viable custom build; extra release/config layer.** | **Viable with native plugin; channel/isolate boundaries need failure tests.** | **Strong shared state/protocol potential; Kotlin/Native/Swift boundary is a reliability surface.** | **Viable with platform code; less AppT evidence.** | **Weakest if WebView owns control; acceptable if native plugin owns all critical path.** | **Strong deterministic core plus native policy; FFI itself must be tested.** |
| **Testing model** | **XCTest + Android unit/instrumentation/UI tests; real iPhone/Android/TV matrix.** | **JS unit/component/E2E plus native XCTest/instrumentation and real device tests.** | **Same plus custom dev/release builds, prebuild/config tests.** | **Dart unit/widget/integration plus native plugin tests and real device tests.** | **commonTest plus platform tests, XCTest/JUnit, and native UI/device tests. [S53]** | **.NET unit/UI plus platform tests; native device matrix.** | **Web tests plus plugin native tests and Xcode/Android device tests.** | **Core protocol/property/fuzz tests plus native integration/TV tests.** |
| **Build/release complexity** | **Two native toolchains and duplicated app setup.** | **Node/Metro/Hermes + CocoaPods/Gradle/Xcode/Android; native modules increase complexity.** | **SDK/RN pinning, CNG/prebuild/config plugins, EAS or native builds; Expo Go cannot represent release.** | **Flutter/Dart SDK + generated native projects + Xcode/Gradle plugins.** | **Gradle/Kotlin/Native + Xcode + Swift interop; Compose version matrix if shared UI.** | **.NET SDK + Android/Xcode/Apple signing; AOT and native bindings.** | **Web bundler + Capacitor sync + Xcode/Android Studio + plugin versions.** | **Native builds plus Rust/C++ toolchains/FFI packaging.** |
| **Native escape hatches** | **The baseline, not an escape hatch.** | **TurboModules/Fabric/JSI and native projects.** | **Expo Modules API, config plugins, prebuild, custom dev builds.** | **Platform channels, FFI, platform views/native projects.** | **expect/actual, Objective-C interop, UIKit/SwiftUI/Android interop.** | **Handlers, partial classes, `Platforms/*`, direct APIs.** | **Swift/Java/Kotlin plugins and open native projects.** | **Native shell is the primary surface.** |
| **Maintainability** | **Protocol correctness duplicated unless fixtures/specs are shared.** | **Large ecosystem, but native module/New Architecture compatibility and JS/native ownership must be managed.** | **Expo cadence can simplify common packages but pins RN and adds CNG/config maintenance.** | **Single UI/runtime but plugin ecosystem and native integration versions need stewardship.** | **Shared logic reduces drift; KMP/Compose/Gradle/Xcode version coordination is nontrivial.** | **Single language/domain possible; cross-platform ecosystem and Apple edge cases need team expertise.** | **Web ecosystem fast; native plugins become a parallel mobile platform codebase.** | **Protocol logic centralization reduces drift; FFI/build ownership is specialized.** |
| **Future TV adapters** | **Strong boundary if adapter contracts/specs are shared; adapter code duplicated by platform.** | **Good if adapters are native/domain modules, not UI-specific JS.** | **Same as RN; module publishing/config overhead.** | **Good if adapter service is behind a Dart/native interface.** | **Strong shared adapter/state model with platform actuals.** | **Good shared C# adapter abstractions; platform implementations.** | **Good only with native plugin adapter layer.** | **Strong common adapter/protocol core, native discovery hooks.** |
| **App Store / permission risk** | **Lowest framework-specific risk; still subject to iOS volume, multicast, background, and Samsung terms.** | **Framework does not change Apple/Android policy; native modules must declare honestly.** | **Expo Go/custom build distinction and generated entitlements must be documented/audited.** | **Same platform policy; plugin manifests and privacy declarations need review.** | **Shared/native code still ships as an ordinary iOS app; no policy exemption.** | **Same; inspect generated app metadata.** | **WebView does not avoid native review; custom plugins make behavior explicit.** | **Native shells carry the policy burden directly.** |

## 8. Candidate-family observations by required concern

### 8.1 LAN discovery is the main cross-platform trap

The existence of a socket API in every candidate is not the same as equivalent discovery behavior:

- iOS custom multicast/broadcast may require a restricted entitlement that needs Apple approval; Bonjour declarations and a user-facing local-network reason are still required. [S23] [S24]
- Android is moving from implicit LAN access toward explicit local-network permission, with raw TCP/UDP and SSDP/mDNS included. [S13] [S14]
- A framework library can open a socket, but cannot itself grant an OS entitlement, explain a runtime prompt, guarantee multicast reception during power saving, or make guest-network multicast work.
- Direct unicast `/24` probing is an empirical fallback used by community remote apps, but AppT must measure whether it is acceptable for ordinary consumers and whether a privacy review considers the breadth of the scan appropriate. This is not a universal substitute for service discovery.

**Implication:** discovery should be an explicit platform seam in every candidate. It should report permission state, network scope, candidate source, confidence, and cancellation, not just return a list of IP addresses.

### 8.2 Samsung WSS security can dominate the stack decision

The community’s ability to connect with `wss://` is not proof that a platform’s default TLS verifier can authenticate the TV. Some community clients disable verification, while AppT explicitly prohibits a global bypass. The following must be answered experimentally:

- What certificate chain/identity does each Samsung cohort present?
- Is the certificate stable across a reboot, IP change, router change, TV firmware update, and re-pair?
- Can the iOS and Android transport verify a narrowly scoped identity without accepting arbitrary local certificates?
- Does the TV expose a trustworthy identity in device info that can be bound to the TLS identity, or would this only be a TOFU/pinning decision?
- What should happen when an identity changes: explicit re-pair, warning, or unsupported?

A framework that offers a “trust all certificates” flag is not a solution; it is a red flag for AppT’s security requirements.

### 8.3 Local-first and account sync are easy to violate accidentally

All families can implement local-first behavior, but shared JavaScript/Dart/C#/Kotlin state stores make it easy to pass one large “TV profile” object to sync. The profile must be divided explicitly:

- **Synchronizable:** friendly name, favourites, non-secret preferences, control layout/rearrangement, and other approved non-secret data.
- **Phone-local:** pairing approval state, Samsung token, certificate/public-key pin or identity evidence, MAC/wake hints, IP history, network/diagnostic details, and sensitive failure evidence.
- **Never ordinary logs/analytics:** token, private key, raw command text, Wi-Fi name, local IP, directly identifying TV data, or complete device-info payload.

Secure storage wrappers are not automatically equivalent. Apple Keychain accessibility/migration and Android Keystore/backup behavior must be chosen and tested. [S20] [S29] [S30] [S56]

### 8.4 Native controls matter even when UI is shared

A remote is an interaction-heavy application with custom directional/touchpad controls, one-handed layout, haptic feedback, keyboard input, screen-reader state, scalable text, and possibly hardware button integration. Shared UI can reduce duplication, but it must not hide:

- whether a custom control exposes correct accessibility role/name/value/actions;
- whether gesture and touchpad modes remain usable with VoiceOver/TalkBack;
- whether text input and cancellation behave like a native form;
- whether haptics respect system state and the product setting;
- whether the physical volume-button requirement is legal/possible on iPhone.

The last point is a platform/product gate, not a framework feature comparison.

## 9. Viability assessment without selecting a stack

### 9.1 Unconditional vs conditional viability

No candidate is unconditionally accepted because Samsung behavior and the iPhone volume requirement are not resolved. The following is the useful discovery classification:

| Family | Current viability posture | Conditions that must be demonstrated |
|---|---|---|
| Fully native Swift + Kotlin | **Viable candidate** | Samsung adapter and TLS identity work on target cohorts; duplicate platform code remains maintainable; iPhone volume requirement is resolved or removed/changed. |
| KMP with native UI | **Viable candidate** | Shared protocol/state code works on real devices; Swift interop and secure storage/lifecycle boundaries remain understandable to both teams; Compose is optional, not required. |
| KMP with Compose UI | **Viable candidate with UI validation** | VoiceOver/TalkBack, text input, haptics, touchpad gestures, and scene/activity lifecycle pass physical-device tests; native interop remains available. |
| React Native bare/native-capable | **Viable candidate with native control seam** | Samsung session, secure storage, discovery, permissions, and lifecycle are native-authoritative; New Architecture modules and build/test process are stable. |
| Expo custom development build/CNG | **Viable candidate with higher configuration burden** | No reliance on Expo Go; entitlements/manifests/config plugins are deterministic; native module can own the Samsung seam; SDK/RN cadence is acceptable. |
| Flutter with native plugin | **Viable candidate with plugin boundary** | Local-network prompts occur before Dart sockets; plugin owns trust/lifecycle/unsolicited events; accessibility and keyboard pass device tests. |
| .NET MAUI | **Conditional viable candidate** | A focused spike confirms SSDP/mDNS/raw sockets, WSS trust, secure storage, lifecycle, and device packaging on the target iPhone/Android range. |
| Capacitor/WebView-first | **Conditional viable candidate** | Native plugin owns all reliability-critical work; WebView is only a UI client; reload/suspension/permission behavior is acceptable; no token enters Web storage. |
| Rust/C++ shared core plus native UI | **Viable architecture family** | FFI/build/debugging cost is justified; core/native ownership and secret boundaries are explicit; all OS behavior remains native. |

This is not a ranking. It says which candidates deserve a focused validation path and which assumptions would make them nonviable.

### 9.2 What would make a family nonviable

Regardless of label, a candidate should be rejected for V1 if its normal shape requires any of the following:

- backend/public internet in the ordinary TV command path;
- pairing tokens in account sync, JavaScript/Web storage, ordinary preferences, or ordinary logs;
- global TLS certificate-verification bypass;
- a background service/connection that cannot meet iOS or Android policy expectations;
- UI/runtime code that cannot expose a reliable native escape hatch for discovery, secure storage, lifecycle, and Samsung transport;
- an Expo Go/WebView-only path for a capability that requires custom native code;
- a hard-coded Samsung model/year matrix without observed capability detection;
- an untested assumption that physical iPhone volume buttons can be repurposed for TV commands;
- an inability to run real iPhone + Android + Samsung TV tests in CI/lab or a disciplined manual matrix.

## 10. Reliability and maintainability risks by family

### Native

The central risk is duplication: two adapter implementations can disagree on command timing, key names, token refresh, or capability mapping. Mitigations are platform-independent fixtures, a shared protocol specification, recorded/redacted device traces, and contract tests. The benefit is that failures are close to the platform API and easier to reason about.

### React Native / Expo

The central risks are ownership and build surface: which side owns the session, how native event streams are delivered after JS reload/suspension, whether every module supports the current New Architecture, and whether generated/native configuration remains deterministic. React Native’s current architecture and native module systems are credible, but they do not eliminate these responsibilities. Expo makes custom native work possible, but the correct mental model is “React Native app with generated/custom native projects,” not “managed JavaScript with no native maintenance.” [S38] [S42] [S43]

### Flutter

The central risks are channel boundaries, background/isolate semantics, and custom-platform UI behavior. A socket held in Dart may be technically correct in the foreground and still fail when the host lifecycle, permission, or background event delivery changes. A plugin can solve this, but then plugin/native code becomes the reliability authority. [S44] [S46]

### KMP / Compose

The central risks are version/build coordination and Swift-facing APIs. KMP’s native binary model is attractive for a shared protocol/domain core, but teams must understand Objective-C-mediated interoperability, suspend/Flow exposure, Kotlin/Native compilation, and platform-specific source sets. Compose Multiplatform’s iOS stability is a positive signal, not proof that every AppT custom control is as accessible or platform-native as UIKit/SwiftUI. [S49] [S50] [S51] [S54]

### .NET MAUI

The central risk is evidence gap rather than a known hard blocker: AppT needs raw local discovery, WSS identity handling, Samsung protocol-specific behavior, and high-quality custom remote controls. MAUI’s direct platform access means these may be implementable, but the project should not treat its cross-platform abstractions as proof until a focused integration check passes. [S55] [S56]

### Capacitor

The central risk is false simplicity. Building the remote UI is easy; building a reliable native control service and then making a WebView a safe client of it is the real application. If the team already owns Swift/Kotlin plugins, the WebView saves less in the highest-risk area and adds another lifecycle/reload boundary. [S57] [S58]

### Shared native core

The central risk is specialized complexity. Rust/C++ can make the protocol state machine and fixtures consistent, but it cannot abstract away iOS local-network entitlements, Android local-network permissions, secure storage, haptics, accessibility, scene/activity lifecycle, or App Review. It is most useful when the wire protocol and state machine are the duplication hotspot, not when the primary problem is UI.

## 11. Unresolved questions

### Samsung and protocol

1. Which Samsung cohorts are within the intended V1 test/support envelope, and what minimum set of real TV models is representative?
2. Is a generic third-party remote path acceptable under Samsung’s current terms and store/vendor requirements, separately from technical feasibility?
3. Which TVs expose `TokenAuthSupport`, which require encrypted/PIN/session flows, and which only support legacy/receiver-oriented protocols?
4. Which controls are actually available per cohort: power, volume, input, directional keys, hold/repeat, app launch, text, touchpad, state feedback, and wake?
5. What does the secure TV certificate represent, and can AppT implement scoped identity verification without a global trust bypass?
6. Are tokens stable, rotatable, revocable, per-phone, per-TV, and invalidated by firmware/setting changes? What is the exact re-pair UX?
7. Does Samsung’s current official SDK have a supported generic remote path, or is the V1 adapter necessarily based on an undocumented interface?
8. What diagnostics can be collected and redacted while still helping support a failed pairing?

### iOS and Android

9. Can AppT obtain the iOS multicast entitlement if SSDP/custom discovery is necessary, or can a combination of Bonjour/direct probes cover target TVs without it?
10. What exactly does iOS local-network permission do for unicast probes and WSS to a private IP on the target OS versions?
11. What Android target SDK/minimum SDK and local-network permission strategy will be appropriate when API 37 enforcement reaches the release channel?
12. Does the Samsung TV/phone Wi-Fi topology allow discovery across common mesh, guest, AP-isolation, IPv6, and VLAN configurations?
13. What wake behavior works with TV wired Ethernet, TV Wi-Fi standby, mesh APs, and subnet broadcast policies?
14. Can ordinary foreground Android activities reliably consume volume keys while preserving expected system behavior on Samsung and other major OEMs?
15. Is the iPhone physical-volume requirement acceptable under App Review guideline 2.5.9, or must the product requirement be changed for iPhone?
16. What is the desired behavior if the user backgrounds, locks, kills, or force-stops AppT while a remote session is active?
17. Which OS versions and accessibility technologies are in the V1 acceptance matrix?

### Architecture and operations

18. What is the acceptable shared-code boundary: UI, domain/state, protocol, adapter, or only test fixtures?
19. What level of native build expertise and real-device lab access is available for Swift/Xcode, Kotlin/Gradle, and any chosen cross-platform toolchain?
20. What account provider and sync conflict model will be used for non-secret data, and how will the server reject accidental secret-bearing payloads?
21. How will redacted traces and deterministic simulated-TV fixtures be versioned without collecting behavioral analytics or raw household data?
22. What is the minimum reliability bar for pairing success, command latency, reconnect recovery, and wake success, given that the product has no public setup-time SLA yet?

## 12. Weak, stale, or contradictory evidence

1. **Official vs current:** Samsung’s strongest official sender material is historical Smart View SDK documentation. The current download page lists recent package artifacts, but the conceptual sender docs still focus on TV applications and do not publish a generic current remote contract. This is useful evidence with low guarantee strength for AppT’s exact use case. [S03] [S04] [S05]
2. **Community port disagreement:** community code reports both 8001 and 8002, different token/popup behavior, and insecure-certificate workarounds. The convergence proves a useful family of observed behavior, not a stable protocol contract. [U01] [U03] [U06] [U07]
3. **Generational contradictions:** OpenHAB’s broad “pre-2016 legacy / post-2016 WebSocket” guidance is contradicted by current Home Assistant reports of a 2016 K-series encrypted fallback and current authorization timeouts. Both should be retained as evidence that model/firmware capability detection is required, not as a single truth table. [U03] [U04]
4. **Certificate evidence:** community libraries that set `CERT_NONE` demonstrate an interoperability problem but do not establish what certificate Samsung intends or whether a safe pinning scheme is possible. The security conclusion is unresolved, not “disable verification.” [U01] [U05]
5. **Android release timing:** Android’s local-network docs describe API 36 opt-in and API 37 enforcement. This is a current official planning input, but target SDK/device release timing should be revalidated immediately before implementation. [S13] [S14]
6. **Framework vendor claims:** React Native, Flutter, KMP, Expo, MAUI, and Capacitor documentation establishes platform access and supported mechanisms. None of it proves AppT’s Samsung reliability, real-device latency, App Store approval, or protocol compatibility. Those are experiment results.
7. **Volume-button sources:** Apple’s public documentation and App Review text are stronger than community workarounds. Private API, hidden view hierarchy, silent-audio, or system-volume tricks are not acceptable evidence for a public V1. [S32] [S33] [S34]
8. **Historical repositories:** Some Samsung repositories are old and tested on one device. They are useful reverse-engineering records, not current support matrices. [U06] [U07]

## 13. Proposed risk-reducing experiments

These are deliberately narrow investigations for a later prototype/validation phase. They are not authorization to add a disposable prototype in this discovery change.

### Experiment 1 — Samsung cohort/protocol matrix

**Question:** Can the intended Samsung V1 cohort perform a safe, repeatable local pairing and basic remote session?

**Scope:** A small physical set including at least one legacy/pre-Tizen candidate, one 2015/2016 or K-series candidate, one modern token-capable Tizen candidate, one recent model, and (if relevant) one Frame/art model. Test Ethernet and Wi-Fi where available.

**Observe:** device info, model/firmware/network type, `TokenAuthSupport`/equivalent, discovery source, 8001/8002/legacy candidates, approval/PIN flow, token/session behavior, key command acknowledgement, app launch, text, touchpad, power-off, reconnect, and wake.

**Pass evidence:** each result is recorded as a capability matrix with protocol version, redacted trace, explicit security identity result, and repeatability after reboot/firmware/network changes.  
**Fail evidence:** any flow that requires a global TLS bypass, silently accepts a changed identity, or cannot distinguish unsupported from temporarily unreachable.

### Experiment 2 — Samsung TLS identity and fail-closed behavior

**Question:** Can iOS and Android establish Samsung secure transport without disabling certificate verification globally?

**Scope:** For at least two modern TVs, inspect the presented certificate/chain and identity over repeated connections, IP changes, reboot, re-pair, and firmware variation where available. Test only scoped verification options: system trust, explicit local identity/pinning, or an equivalent documented policy.

**Pass evidence:** a stable, user-understandable identity policy that rejects unexpected identity changes and supports explicit re-pair.  
**Fail evidence:** only `trust all`, unstable identity with no safe binding, or platform divergence that cannot be explained to the user.

### Experiment 3 — Discovery and permission feasibility

**Question:** Which discovery combination works on real iPhone and Android devices without unacceptable entitlement, permission, timing, or privacy cost?

**Scope:** Compare Bonjour/DNS-SD, SSDP, direct unicast device-info probes, cached-address reconnect, and user-assisted manual selection. Test iOS physical devices with and without the multicast entitlement, iOS local-network denial/revocation, Android API 36 restriction opt-in, Android API 37 permission/picker behavior, AP isolation, guest Wi-Fi, mesh, IPv6, and VLAN boundaries.

**Pass evidence:** a bounded discovery strategy with user rationale, cancellation/timeouts, redacted telemetry-free diagnostics, and acceptable setup latency across target networks.  
**Fail evidence:** required entitlement cannot be obtained, direct scans are too broad/slow/privacy-invasive, or common Samsung TVs are not discoverable without manual technical configuration.

### Experiment 4 — iPhone physical volume-button release gate

**Question:** Can the product requirement be met on iPhone using public APIs while preserving App Store suitability?

**Scope:** A native-only test on physical iPhones using documented volume/audio APIs, with no private APIs, hidden view hierarchy, silent-audio workaround, or global button interception. Test foreground remote active/inactive, audio playing/not playing, locked/background, App Review guideline 2.5.9 interpretation, and visible system behavior.

**Pass evidence:** a documented public API path and an Apple/App Review interpretation that does not alter expected volume-switch behavior.  
**Fail evidence:** the only working path repurposes/suppresses system volume, requires private APIs, or creates a misleading system-volume experience. A fail is a **product decision**, not a reason to pick a different framework.

### Experiment 5 — Foreground/background/reconnect matrix

**Question:** Can each candidate keep normal foreground control dependable across common lifecycle transitions?

**Scope:** With a real paired TV, hold a session while switching apps, opening Notification Center, locking/unlocking, losing Wi-Fi, roaming to a new AP/IP, returning from suspension, force-quitting, and relaunching. Compare native, one cross-platform candidate, and any proposed shared core only after Samsung transport is known.

**Pass evidence:** idempotent reconnect, no duplicate commands, no stale-token use, explicit Reconnecting state, and no need for prohibited/general-purpose background execution.  
**Fail evidence:** commands are lost/duplicated without a recoverable state, sockets are assumed to survive suspension, or the framework hides the lifecycle event needed to repair state.

### Experiment 6 — WoL/WoW repeatability

**Question:** When can AppT honestly show a working wake control?

**Scope:** After a successful pair, record MAC/identity, put TVs into supported standby states, test unicast and subnet-directed/broadcast magic packets from Wi-Fi and Ethernet phone paths, mesh/guest networks, several delays, and TV settings. Do not rely on internet/cloud wake.

**Pass evidence:** per-TV capability evidence and a bounded retry/status UX; otherwise power-on is shown as unavailable/experimental.  
**Fail evidence:** wake is dependent on undocumented router behavior or succeeds only intermittently without a user-understandable state.

### Experiment 7 — Text input and capability probing

**Question:** Can ordinary phone keyboard input be delivered safely and predictably to supported Samsung TV contexts?

**Scope:** Test focus acquisition, Latin/Unicode/punctuation, spaces, deletion, submit/cancel, password fields, IME behavior, and TV-side app/context differences. Compare native text-field handling with cross-platform UI, while keeping TV wire encoding in the adapter.

**Pass evidence:** explicit `textInput` capability and reliable cancellation/error handling; unsupported contexts do not show a dead keyboard button.  
**Fail evidence:** only a brittle sequence of remote keys works, or text can leak into logs/diagnostics.

### Experiment 8 — Secure local/account boundary and restore behavior

**Question:** Do all candidate storage/sync shapes preserve secrets on one phone and non-secret data across phones?

**Scope:** Pair two phones independently to one TV; sync friendly name/favourites/preferences; confirm tokens do not sync. Test backup/restore, uninstall/reinstall, device migration, Keychain/Keystore lock state, token corruption, account logout, and explicit TV forget.

**Pass evidence:** local control continues during backend outage; server rejects secret-shaped fields; restore behavior follows a documented policy.  
**Fail evidence:** token appears in account payloads, backups, normal logs, analytics, or a shared JS/Dart/Web storage area.

### Experiment 9 — Focused candidate integration comparison

**Question:** Does a chosen cross-platform candidate reduce product work without weakening the control seam?

**Scope:** After Experiments 1–4 identify real native requirements, build the smallest non-production integration for two candidates plus a native baseline. It should exercise discovery permission, secure pairing storage, WSS identity policy, one key command, reconnect on resume, accessibility label/state, haptic toggle, and redacted failure reporting. It must use custom native builds and physical devices.

**Measure:** build/rebuild time, number of native files/configuration points, crash/error observability, command latency/jitter, reconnect correctness, test isolation, and upgrade friction.  
**Pass evidence:** a candidate can expose the native seam without hidden global state or unsafe TLS.  
**Fail evidence:** control depends on a UI runtime being alive, native configuration is not reproducible, or the team cannot test the candidate on both platforms.

## 14. Provisional discovery criteria for a later architecture decision

A later architecture decision should not be based on “most code shared.” It should record evidence for:

1. **Samsung reality:** tested model/firmware cohorts, supported capabilities, legacy fallback, pairing/token state, and current vendor/terms review status.
2. **Secure transport:** certificate identity behavior and fail-closed re-pair policy without a global bypass.
3. **Discovery:** iOS entitlement/permission results, Android current/future local-network permission plan, network topology limits, and fallback UX.
4. **Lifecycle:** foreground/background/kill/reconnect behavior on physical devices.
5. **Wake:** measured WoL/WoW support and honest capability presentation.
6. **Phone-native behavior:** accessibility, haptics, keyboard/text input, one-handed use, and a product decision on iPhone volume buttons.
7. **Local-first security:** clear secret/non-secret model, secure storage semantics, backup/restore, account boundary, and redaction tests.
8. **Future adapters:** adapter isolation, capability model, test fixture strategy, and ability to add a second ecosystem without rewriting the UI/control state model.
9. **Engineering economics:** native expertise, build/release complexity, real-device lab access, upgrade cadence, and expected maintenance over Samsung firmware changes.

## 15. Current non-binding findings

- **Finding A — the hard part is not generic UI.** Samsung capability discovery, local transport, pairing, identity, reconnect, wake, lifecycle, secure storage, and native phone behavior are the architecture drivers.
- **Finding B — every credible cross-platform option needs a native seam.** React Native/Expo, Flutter, .NET MAUI, and Capacitor remain possible, but only if the critical control path is explicitly native or a carefully bounded shared core.
- **Finding C — KMP is a distinct option, not the same trade as a UI bridge.** KMP can share protocol/domain code while retaining native UI and OS integrations; Compose Multiplatform adds UI sharing but also adds an accessibility/interop validation burden.
- **Finding D — native is the baseline, not automatically the winner.** It minimizes abstraction at the reliability seam but duplicates platform logic and can allow adapter behavior to drift.
- **Finding E — Expo is a workflow choice, not permission to avoid native work.** A custom development build/CNG path can be viable; an Expo Go-only interpretation cannot.
- **Finding F — Samsung support must be capability-led.** Official Samsung key guidance, historical SDK material, and community failures all point away from a fixed year/model list.
- **Finding G — no backend in the command path is compatible with every candidate.** It must be enforced by module boundaries and tests, not only by product prose.
- **Finding H — iPhone volume buttons are a product release gate.** There is no architecture selection that makes Apple’s public API and App Review guidance disappear.
- **Finding I — a second ecosystem should be an adapter test, not a reason to abstract every detail now.** The useful early seam is a small capability/command contract plus a Samsung adapter, not a speculative universal protocol.

These findings do not select a stack and do not move the repository out of discovery.

## 16. Source register

**Access context:** web sources below were consulted on 2026-09-22 UTC. “Current” means the page content observed on that date; historical/archive sources are labeled as such. GitHub source claims should be checked against the cited commit/branch before reuse.

### Product and project sources

- **[P]** AppT, “Product,” repository `docs/PRODUCT.md`, approved product definition, accessed 2026-09-22. <https://github.com/anthracite-labs/AppT/blob/main/docs/PRODUCT.md>
- **[PS]** AppT, “Project State,” repository `docs/PROJECT_STATE.md`, discovery position, accessed 2026-09-22. <https://github.com/anthracite-labs/AppT/blob/main/docs/PROJECT_STATE.md>

### Samsung and TV sources

- **[S01]** Samsung Developer, “Remote Control,” TV-side key names/codes, model/platform variation, current page observed 2026-09-22. <https://developer.samsung.com/smarttv/develop/guides/user-interaction/remote-control.html>
- **[S02]** Samsung Developer, “User Interaction Q&A,” key-name recommendation, keyboard/IME and model limitations, current page observed 2026-09-22. <https://developer.samsung.com/smarttv/develop/faq/user-interaction.html>
- **[S03]** Samsung Developer, “iOS Sender App,” historical Smart View sender discovery/launch/communication workflow, current page observed 2026-09-22. <https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/ios-sender-app.html>
- **[S04]** Samsung Developer, “Enhanced Features,” historical Smart View WoW/WoWLAN, MAC persistence, reconnect/error, multitasking, and TLS material, current page observed 2026-09-22. <https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/ios-sender-app/enhanced-features.html>
- **[S05]** Samsung Developer, “Download,” Smart View package versions/release notes and historical 2015 discovery issue, current page observed 2026-09-22. <https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/download.html>
- **[S06]** Samsung Developer, “Debugging,” local TV device-info/debug endpoint, current page observed 2026-09-22. <https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/receiver-apps/debugging.html>
- **[S07]** Samsung Developer, “API References,” legacy platform/API archive, historical page observed 2026-09-22. <https://developer.samsung.com/smarttv/legacy/api-references.html>
- **[U01]** xchwarze, `samsung-tv-ws-api`, current repository README/structure; latest observed commit `e48d6377faede37db1f034d726a079b9d8034fac`, version 3.0.6, 2026-09-11. Unofficial maintained implementation. <https://github.com/xchwarze/samsung-tv-ws-api>
- **[U02]** xchwarze, `samsung-tv-ws-api` command documentation, ports/token/remote workflow, repository document observed 2026-09-22. Unofficial. <https://github.com/xchwarze/samsung-tv-ws-api/blob/master/COMMANDS.md>
- **[U03]** openHAB, “Samsung TV Binding,” UPnP discovery, legacy/WebSocket protocols, ports, approval/token/MAC and model-dependent channel behavior, current docs observed 2026-09-22. Community integration. <https://www.openhab.org/addons/bindings/samsungtv/>
- **[U04]** Home Assistant, Samsung TV issue/pull-request reports, K-series encrypted fallback and recent authorization/timeout behavior, issue/P.R. reports observed 2026-09-22. Community real-device evidence; not vendor documentation. <https://github.com/home-assistant/core/issues/177252> and <https://github.com/home-assistant/core/pull/180619>
- **[U05]** Home Assistant, `homeassistant/components/samsungtv`, maintained integration directory, observed 2026-09-22. Community integration. <https://github.com/home-assistant/core/tree/dev/homeassistant/components/samsungtv>
- **[U06]** vrachieru, `samsung-tv-api`, version 1.0.0 released 2018-10-20, reverse-engineered Python wrapper observed 2026-09-22. <https://github.com/vrachieru/samsung-tv-api>
- **[U07]** stefanoj3, `samsungremote`, one-model-tested Go wrapper; repository/readme evidence observed 2026-09-22. <https://github.com/stefanoj3/samsungremote>

### Standards and protocol sources

- **[S08]** IETF, RFC 6762, “Multicast DNS,” standards document. <https://www.rfc-editor.org/rfc/rfc6762.html>
- **[S09]** IETF, RFC 6763, “DNS-Based Service Discovery,” standards document. <https://www.rfc-editor.org/rfc/rfc6763.html>
- **[S10]** Open Connectivity Foundation, UPnP Device Architecture resources, SSDP/UPnP specification family, accessed 2026-09-22. <https://openconnectivity.org/developer/specifications/upnp-resources/upnp-device-architecture-documents>
- **[S11]** IETF, RFC 6455, “The WebSocket Protocol,” standards document. <https://www.rfc-editor.org/rfc/rfc6455.html>
- **[S12]** IETF, RFC 8446, “The Transport Layer Security (TLS) Protocol Version 1.3,” standards document. <https://www.rfc-editor.org/rfc/rfc8446.html>

### Android sources

- **[S13]** Android Developers, “Local network permission,” Android 16 preparation and Android 17/API 37 enforcement guidance, last updated 2026-07-13, observed 2026-09-22. <https://developer.android.com/privacy-and-security/local-network-permission>
- **[S14]** Android Developers, “Behavior changes: Apps targeting Android 16 or higher,” raw socket/mDNS/SSDP/local-network guidance, last updated 2026-09-16, observed 2026-09-22. <https://developer.android.com/about/versions/16/behavior-changes-16>
- **[S15]** Android Developers, “Request permission to access nearby Wi-Fi devices,” `NEARBY_WIFI_DEVICES` and scan permission guidance, observed 2026-09-22. <https://developer.android.com/develop/connectivity/wifi/wifi-permissions>
- **[S16]** Android Developers, `NsdManager` API reference, DNS-SD/mDNS, multicast lock, service picker, address-change guidance, observed 2026-09-22. <https://developer.android.com/reference/android/net/nsd/NsdManager>
- **[S17]** Android Developers, `WifiManager.MulticastLock` API reference, multicast reception and battery implications, observed 2026-09-22. <https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock>
- **[S18]** Android Developers, “Optimize for Doze and App Standby,” network suspension and persistent-connection guidance, observed 2026-09-22. <https://developer.android.com/training/monitoring-device-state/doze-standby>
- **[S19]** Android Developers, “The activity lifecycle,” stop/reconnect example and lifecycle callbacks, observed 2026-09-22. <https://developer.android.com/guide/components/activities/activity-lifecycle>
- **[S20]** Android Developers, “Android Keystore system,” non-exportable/hardware-backed key and authorization guidance, observed 2026-09-22. <https://developer.android.com/privacy-and-security/keystore>
- **[S21A]** Android Developers, `KeyEvent` API reference, hardware key/button event model, observed 2026-09-22. <https://developer.android.com/reference/android/view/KeyEvent>
- **[S21B]** Android Developers, “Handle keyboard actions,” hardware vs soft IME key-event guidance, last updated 2026-05-28, observed 2026-09-22. <https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/commands>
- **[S22]** Android Developers, “Make apps more accessible,” touch targets/content descriptions/Compose accessibility guidance, observed 2026-09-22. <https://developer.android.com/guide/topics/ui/accessibility/apps>

### Apple sources

- **[S23]** Apple Developer, “How to use multicast networking in your app,” iOS 14 local-network privacy, Bonjour declarations, and restricted multicast entitlement, 2020-06-22, observed 2026-09-22. <https://developer.apple.com/news/?id=0oi77447>
- **[S24]** Apple Developer, `com.apple.developer.networking.multicast`, restricted entitlement for IP multicast/broadcast and arbitrary Bonjour, observed 2026-09-22. <https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.networking.multicast>
- **[S25]** Apple Developer, `NWConnection`, native endpoint connection API, observed 2026-09-22. <https://developer.apple.com/documentation/network/nwconnection>
- **[S26]** Apple Developer, `NWProtocolWebSocket`, native WebSocket protocol API, observed 2026-09-22. <https://developer.apple.com/documentation/network/nwprotocolwebsocket>
- **[S27]** Apple Developer, Technical Note TN2277, “Networking and Multitasking,” suspension/socket reclaim/reopen guidance, archived, observed 2026-09-22. <https://developer.apple.com/library/archive/technotes/tn2277/_index.html>
- **[S28]** Apple Developer Forums, Local Network Privacy FAQ-2, outgoing local TCP/UDP and Bonjour permission guidance, observed 2026-09-22. <https://developer.apple.com/forums/thread/663874>
- **[S29]** Apple Developer, “Using the keychain to manage user secrets,” Keychain secret storage, observed 2026-09-22. <https://developer.apple.com/documentation/security/using-the-keychain-to-manage-user-secrets>
- **[S30]** Apple Developer, “Restricting keychain item accessibility,” lock-state, `ThisDeviceOnly`, backup/migration guidance, observed 2026-09-22. <https://developer.apple.com/documentation/security/restricting-keychain-item-accessibility>
- **[S31]** Apple Developer, “Preparing your UI to run in the background,” scene lifecycle, suspension, resource and socket/Bonjour guidance, observed 2026-09-22. <https://developer.apple.com/documentation/uikit/app_and_environment/scenes/preparing_your_ui_to_run_in_the_background>
- **[S32]** Apple Developer, “App Review Guidelines,” sections 2.5.4, 2.5.9, background services and standard volume-switch behavior, observed 2026-09-22. <https://developer.apple.com/app-store/review/guidelines/>
- **[S33]** Apple Developer, `MPVolumeView`, public system-volume and route-control behavior, observed 2026-09-22. <https://developer.apple.com/documentation/mediaplayer/mpvolumeview>
- **[S34]** Apple Developer, `AVAudioSession.outputVolume`, only-user-directly-sets-system-volume guidance, observed 2026-09-22. <https://developer.apple.com/documentation/avfaudio/avaudiosession/outputvolume>
- **[S35]** Apple Developer, “Core Haptics,” custom haptic patterns, observed 2026-09-22. <https://developer.apple.com/documentation/corehaptics>
- **[S36]** Apple Developer, “Supporting VoiceOver in your app,” labels, hints, grouping, state, and audit guidance, observed 2026-09-22. <https://developer.apple.com/documentation/uikit/accessibility/supporting-voiceover-in-your-app>

### Framework sources

- **[S37]** React Native, “Networking,” Fetch and WebSocket support, current docs observed 2026-09-22. <https://reactnative.dev/docs/network>
- **[S38]** React Native, “New Architecture is here,” Turbo Native Modules/Fabric/JSI and production status, 2024-10-23, observed 2026-09-22. <https://reactnative.dev/blog/2024/10/23/the-new-architecture-is-here>
- **[S39]** React Native, “AppState,” foreground/background/inactive states, last updated 2026-09-02, observed 2026-09-22. <https://reactnative.dev/docs/appstate>
- **[S40]** React Native, “React Native 0.87,” current release/toolchain/Strict TypeScript/SwiftPM information, 2026-08-11, observed 2026-09-22. <https://reactnative.dev/blog/2026/08/11/react-native-0.87>
- **[S41]** Expo, “Expo SDK reference,” SDK 57 latest reference, RN pinning/support table, observed 2026-09-22. <https://docs.expo.dev/versions/latest/>
- **[S42]** Expo, “Add custom native code” and CNG/customization guidance, observed 2026-09-22. <https://docs.expo.dev/workflow/customizing/>
- **[S43]** Expo, “Configure a development build in cloud,” Expo Go vs custom development build, observed 2026-09-22. <https://docs.expo.dev/tutorial/eas/configure-development-build/>
- **[S44]** Flutter, “Writing custom platform-specific code,” channels/Pigeon/threads, observed 2026-09-22. <https://docs.flutter.dev/platform-integration/platform-channels>
- **[S45]** Flutter, “Communicate with WebSockets,” Dart WebSocket channel workflow, observed 2026-09-22. <https://docs.flutter.dev/cookbook/networking/web-sockets>
- **[S46]** Flutter, “Concurrency and isolates,” platform channel/background-isolate limitations, observed 2026-09-22. <https://docs.flutter.dev/perf/isolates>
- **[S47]** Flutter, “Request local network permissions on Android,” Dart socket permission boundary, observed 2026-09-22. <https://docs.flutter.dev/platform-integration/android/local-network-permission>
- **[S48]** Flutter, “Flutter 3.47,” current release/toolchain/iOS lifecycle direction, 2026-08-12, observed 2026-09-22. <https://flutter.dev/blog/whats-new-in-flutter-3-47>
- **[S49]** Kotlin Multiplatform, “FAQ,” KMP/Compose UI sharing, iOS/Android stability and native integration, last modified 2026-05-15, observed 2026-09-22. <https://kotlinlang.org/docs/multiplatform/faq.html>
- **[S50]** Kotlin Multiplatform, “Stability of supported platforms,” core KMP and Compose platform stability, last modified 2025-09-10, observed 2026-09-22. <https://kotlinlang.org/docs/multiplatform/supported-platforms.html>
- **[S51]** Kotlin Multiplatform, “KMP for iOS,” native/shared-code model and Swift integration, last modified 2026-08-05, observed 2026-09-22. <https://kotlinlang.org/docs/multiplatform/kmp-for-ios.html>
- **[S52]** Ktor, “Client engines,” multiplatform Android/Darwin/CIO/WebSocket capability matrix, Ktor 3.6.0 docs observed 2026-09-22. <https://ktor.io/docs/client-engines.html>
- **[S53]** Kotlin Multiplatform, “Test your multiplatform app,” common/platform test model, observed 2026-09-22. <https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html>
- **[S54]** Kotlin Multiplatform, “Compatibility and versions,” Compose Multiplatform 1.12.1 platform/compiler/version information, last modified 2026-09-22, observed 2026-09-22. <https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html>
- **[S55]** Microsoft Learn, “What is .NET MAUI?,” native-capable C#/XAML cross-platform model, .NET MAUI 10 docs observed 2026-09-22. <https://learn.microsoft.com/en-us/dotnet/maui/what-is-maui?view=net-maui-10.0>
- **[S56]** Microsoft Learn, “Secure storage,” `ISecureStorage`, Android backup and iOS keychain platform differences, .NET MAUI 10 docs observed 2026-09-22. <https://learn.microsoft.com/en-us/dotnet/maui/platform-integration/storage/secure-storage?view=net-maui-10.0&tabs=android>
- **[S57]** Capacitor, “Development Workflow,” native project/build/plugin model, current v8 docs observed 2026-09-22. <https://capacitorjs.com/docs/basics/workflow>
- **[S58]** Capacitor, “Custom Native Android Code” and “Custom Native iOS Code,” plugin escape hatches, current v8 docs observed 2026-09-22. <https://capacitorjs.com/docs/android/custom-code> and <https://capacitorjs.com/docs/ios/custom-code>
- **[S59]** Capacitor, “App Development Support Policy,” v8 active support and minimum platform/toolchain table, observed 2026-09-22. <https://capacitorjs.com/docs/main/reference/support-policy>

## 17. Completion state

This report records research and bounded next investigations only. It does not claim an architecture decision, stack selection, implementation plan, or production readiness. The project remains in the **discovery** phase. The next durable decision should be made only after the Samsung protocol/TLS, iOS discovery/volume, and lifecycle experiments produce evidence strong enough to compare the candidates against AppT’s reliability requirement.
