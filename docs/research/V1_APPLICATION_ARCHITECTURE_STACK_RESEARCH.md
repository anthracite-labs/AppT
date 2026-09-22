# AppT V1 application architecture and technology-stack research

**Status:** non-binding discovery report; no architecture or stack selected  
**Prepared:** 2026-09-22 (UTC)  
**Scope:** Android and iPhone V1, Samsung-first local TV control, future TV adapters  
**Authoritative inputs:** [`docs/PRODUCT.md`](../PRODUCT.md) and [`docs/PROJECT_STATE.md`](../PROJECT_STATE.md)

## Executive answer

AppT V1 is genuinely viable with more than one application-stack family, but it is **not** viable as a package-only, WebView-only, or “Expo Go” application with no platform integration. The decisive capability is a small, testable control seam that can perform LAN discovery, direct TCP/WebSocket/TLS work, Samsung pairing and token persistence, lifecycle-aware reconnect, Wake-on-LAN where supported, and secure storage without sending TV commands through a backend. The control seam may be shared-runtime, native, shared-core/native-transport, or hybrid; the evidence has not selected its owner.

The viable families are therefore conditional:

- **Fully native Swift plus Kotlin/Jetpack** can reach every required OS API directly, at the cost of duplicated UI/domain integration and two native codebases.
- **Kotlin Multiplatform with native iOS and Android integration** can share protocol/domain/data logic while retaining native escape hatches. Compose Multiplatform can share UI, but native UI remains an equally valid KMP shape and keeps more phone-specific behavior direct.
- **React Native in a native-capable application** can work with direct JavaScript networking where the runtime supports it, a native module/plugin, a shared core, or a hybrid. Current React Native has a production New Architecture and first-party WebSocket support, but the bridge/runtime lifecycle, build, and device-test surface remains material. The evidence does not establish who should own a long-lived control session.
- **Expo** is viable only as a custom development-build/CNG/native-project setup. Expo Go and a managed-only interpretation are not sufficient for AppT’s multicast entitlements, platform network controls, secure storage policy, Samsung-specific transport, and lifecycle work; the required native configuration does not by itself decide session ownership.
- **Flutter** can use Dart sockets/WebSockets, platform plugins, a shared core, or a hybrid. Permission prompts, multicast behavior, secure storage, lifecycle, and unsolicited background events still require platform-specific handling. A native plugin is a platform seam, not proof that the protocol session must live there.
- **.NET MAUI** is technically viable through shared .NET code plus platform code, handlers, and native APIs, but this report found less AppT-specific evidence around the unusual Samsung/iOS LAN and lifecycle seam than for the other candidates. It should remain a conditional candidate, not be discarded or assumed.
- **Capacitor/Ionic or another WebView-first stack** is technically possible with platform plugins or another native integration for OS-required seams. A WebView-only control path has the weakest fit for deterministic foreground lifecycle, low-level LAN discovery, secure transport identity, and native phone behavior; whether a plugin, shared runtime, or hybrid should own the control session remains unverified.
- **A shared Rust/C++ protocol core with native UIs** remains a research hypothesis, not a supported recommendation. It could reduce duplicated wire-protocol logic while leaving discovery, secure storage, lifecycle, haptics, accessibility, keyboard, and buttons in platform integration, but this report has not established equivalent evidence for FFI, TLS/trust callbacks, secure-storage boundaries, lifecycle, testing, packaging, or UI/accessibility integration.

No family can remove the largest unresolved risks:

1. Samsung’s current generic phone-remote behavior is not established by a current public Samsung protocol specification. The commonly used Tizen WebSocket remote is convergent community evidence, not a vendor compatibility guarantee.
2. Samsung generations and firmware differ. Pairing, ports, token behavior, encrypted/legacy flows, available commands, application discovery, text input, and wake behavior must be capability-detected and tested on real devices.
3. Samsung’s secure WebSocket path may expose a local certificate/trust problem. Product requirements prohibit a global certificate-verification bypass. Scoped identity verification or an explicit release decision is still required.
4. iOS multicast/broadcast discovery has privacy controls and a restricted entitlement, while iOS does not provide a general-purpose persistent background LAN session for a normal consumer remote.
5. The requirement that iPhone physical volume buttons control TV volume is a product/platform release risk. Apple documents that only the user can directly set system volume, and App Store guideline 2.5.9 says apps that alter standard Volume Up/Down switch behavior will be rejected. A framework choice cannot make that requirement safe; it needs a narrowly scoped native experiment and, if confirmed, a product decision or Apple-approved interpretation.

**Non-binding conclusion:** the next discovery step should test the Samsung and phone-platform seams first, then compare shared-runtime, native, shared-core/native-transport, and hybrid session placements within each credible candidate. This report deliberately does not choose a winner, create an ADR, add dependencies, or prescribe implementation.

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

### Platform seams versus session ownership

The report distinguishes two questions that were previously too easy to conflate:

1. **Platform-required work:** OS permission prompts and declarations, iOS multicast entitlements, Android local-network controls, lifecycle notifications, secure-storage APIs, haptics, accessibility, keyboard/input integration, hardware-button policy, and platform-specific TLS configuration. These require platform integration even when most application code is shared.
2. **Session placement:** discovery orchestration, transport objects, pairing/token state, command serialization, reconnect state, and event/state-machine ownership. These may technically live in a shared runtime, a native implementation, a shared Rust/C++/other core, or a hybrid arrangement that combines them. The current evidence does **not** establish which placement is most reliable for AppT.

A native escape hatch proves that a candidate can reach a platform API; it does not prove that a complete Samsung session must run in native code. The comparison matrix and Experiment 9 therefore treat session placement as an empirical architecture question. Lifecycle and security warnings remain real: whichever layer owns a session must tolerate suspension, cancellation, identity changes, secret isolation, and event loss.

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

- **mDNS:** Multicast DNS is specified by [RFC 6762](https://www.rfc-editor.org/rfc/rfc6762.html). It uses link-local multicast and is subject to network filtering and mobile-OS privacy/power rules. [S08]
- **DNS-SD/Bonjour:** DNS-Based Service Discovery is specified by [RFC 6763](https://www.rfc-editor.org/rfc/rfc6763.html). It can advertise a service while allowing address/TXT records to change; it is not an identity proof by itself. [S09]
- **SSDP/UPnP:** SSDP is part of the UPnP Device Architecture family. It is a UDP multicast/broadcast discovery mechanism and is commonly deployed by consumer devices, but it can be disabled or filtered by access points, guest networks, and VLANs. AppT should treat discovered metadata as a candidate, not an authenticated TV identity. [S10]
- **WebSocket:** The wire protocol is standardized by [RFC 6455](https://www.rfc-editor.org/rfc/rfc6455.html). A compliant WebSocket client can still fail on a TV-specific handshake, endpoint, message schema, or authentication behavior. [S11]
- **TLS:** TLS 1.3 is specified by [RFC 8446](https://www.rfc-editor.org/rfc/rfc8446.html). TLS encryption and certificate identity verification are separate questions; accepting an encrypted connection while skipping identity verification is not equivalent to secure peer authentication. [S12]
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
6. **Expect foreground recovery.** The control implementation must close/recreate or otherwise recover sessions after lifecycle transitions and re-resolve the TV after network changes. Persistent background control is not a baseline assumption on iOS and is battery/policy-sensitive on Android. Whether recovery is implemented in shared code, native code, or a hybrid is an experiment question.
7. **Keep Samsung protocol code behind one adapter boundary.** This contains undocumented changes and makes later ecosystems less likely to leak Samsung-specific names into the product/UI model; it does not dictate where the adapter session is hosted.

## 4. Multi-ecosystem architecture stress test

The Samsung-first findings were checked against Roku, LG webOS, and Android/Google TV before treating any seam as general. The table uses **F** for current first-party documentation, **A** for an official source that is old or not a current public third-party contract, **E** for maintained implementation/empirical evidence, and **U** for unresolved or not established. A protocol being observable is not permission to ship it.

| Dimension | Samsung Smart TV | Roku TV / player | LG webOS TV | Android / Google TV |
|---|---|---|---|---|
| **Discovery** | mDNS/SSDP, cached identity, and device-info/HTTP probes appear across official historical material and maintained integrations; exact current cohort behavior is mixed. **A/E** [S03] [S06] [U03] | SSDP discovery is part of the current External Control Protocol (ECP) documentation. **F** [V01] | SSDP discovery is documented empirically by Home Assistant and maintained clients; LG’s current developer material points external-device developers to Connect SDK rather than publishing a current SSAP remote reference. **F/E** [V02] [V03] [V04] | Service discovery and local endpoints are implemented by maintained Android TV Remote Protocol clients/integrations; current public third-party API status remains uncertain. **E** [V07] [V08] |
| **Pairing / authentication** | TV approval/token flows vary; legacy/encrypted/PIN/session alternatives are reported for some cohorts. **E/U** [U01] [U03] [U04] | Current ECP documentation describes an on-device “Control by mobile apps” setting and command gating, not a general cryptographic pairing ceremony. **F** [V01] | TV prompt/client-key pairing is reported by maintained clients; official LG material does not establish SSAP as a current generic public remote API. **E/U** [V02] [V04] [V05] | AOSP contains an older Polo pairing flow; maintained current clients report PIN pairing followed by certificate-based TLS. **A/E** [V06] [V07] |
| **Transport** | JSON WebSocket/WSS families commonly reported on 8001/8002, plus legacy paths and cohort-specific behavior. **E/U** [U01] [U03] | HTTP ECP on port 8060 with REST-like query, keypress, launch, and text endpoints. **F** [V01] | SSAP WebSocket/WSS; maintained implementations report WS 3000 and WSS 3001/fallback variation. **E/U** [V04] [V05] | TLS/protobuf Remote Protocol v2; maintained implementation reports pairing/control ports 6467/6466. **E** [V07] |
| **Secret shape** | Opaque approval token for modern flows; some cohorts use session/PIN/encrypted material. Treat as phone-local opaque secret. **E/U** [U01] [U04] | No ECP credential shape is established by the current page; the documented gate is device policy/configuration. Do not invent a token model. **F/U** [V01] | Client key/secret returned or stored after prompt pairing. **E** [V03] [V04] | Client certificate/private key plus pairing secret/material in maintained clients; exact current platform contract remains uncertain. **A/E** [V06] [V07] |
| **Capability discovery** | Device info, advertised key/application support, and observed responses vary by model/firmware; capability detection is mandatory. **A/E** [S01] [S02] [U03] | `/query/device-info`, `/query/apps`, active-app and feature fields expose device/app capabilities. **F** [V01] | SSAP responses, endpoint results, subscriptions, and device UUID/client behavior provide capability evidence; optional features vary. **E** [V03] [V04] | Feature flags/current app/volume and protocol messages are exposed by maintained implementations; do not equate feature flags with every model’s behavior. **E** [V07] [V08] |
| **App launch** | Community/maintainer evidence reports application/channel operations, but support varies and is not a current Samsung generic contract. **E/U** [U01] [U03] | ECP documents app listing and launch by app ID, subject to current policy/device settings. **F** [V01] | Community SSAP clients implement launcher/app operations; official current support is not established. **E/U** [V04] [V05] | Maintained Remote Protocol clients implement app links/launch identifiers; public current API status is uncertain. **E/U** [V07] [V08] |
| **Text / pointer input** | Remote keys are better evidenced; text, touchpad, and hold semantics vary by TV/app/context. **E/U** [S01] [S02] [U01] | ECP documents keypress and `Lit_` text input; no general pointer protocol is established in the cited current page. **F/U** [V01] | Maintained clients report text/IME and a specialized pointer socket, with endpoint/firmware differences. **E** [V04] [V05] | Maintained clients report IME/text and voice features; no general pointer/touchpad primitive is established. **E/U** [V07] |
| **Power / wake** | Power-off and historical WoW/WoWLAN are reported, but wake depends on cohort, settings, network, and identity. **A/E/U** [S04] [U03] | ECP power keys/flags are documented; reliable wake from sleep is not a universal ECP guarantee in the cited page. **F/U** [V01] | Power operations and WoL are implemented by community clients; Home Assistant treats wake as separate and documents limitations. **E** [V03] [V04] | Power/volume operations are implemented while reachable; a universal Wake-on-LAN guarantee is not established. **E/U** [V07] [V08] |
| **Identity / identity change** | Model/device metadata, MACs, tokens, and any TLS identity do not form one proven current identity policy across cohorts. **U** [U01] [U04] | Serial/UDN/device metadata are available, but current ECP HTTP documentation does not establish cryptographic peer identity or fail-closed identity-change behavior. **F/U** [V01] | Home Assistant uses device UUID for unique identity; community certificate/TOFU behavior is not equivalent to per-TV authenticated identity. **E/U** [V03] [V04] | Pairing/server certificates provide a stronger identity candidate, but current protocol/client validation and a product pinning policy remain unresolved. **A/E/U** [V06] [V07] |
| **State / events** | WebSocket events and integration polling/feedback are reported, but event coverage is cohort/app-specific. **E/U** [U01] [U03] | Current ECP is query/command HTTP; no generic persistent event stream is established in the cited page. **F/U** [V01] | SSAP subscriptions/pushed state and coordinator polling/reconnect are observed. **E** [V03] [V04] | Remote Protocol callbacks/state messages are implemented by maintained clients; coverage is protocol/device dependent. **E** [V07] [V08] |

### 4.1 What generalizes, and what does not

The stress test supports a small set of **architecture questions**, not a universal wire interface:

- Each ecosystem needs an adapter boundary with discovery evidence, pairing state, transport/session behavior, capability evidence, command mapping, lifecycle/reconnect, diagnostics, and an explicit wake result.
- A product-level capability model can expose only demonstrated features such as `keyPress`, `textInput`, `pointer`, `appLaunch`, `power`, `wake`, `stateEvents`, and `identityProtected`. Each capability needs an evidence/status value; transport connectivity alone is not capability evidence.
- Pairing, secret shape, transport, app IDs/links, pointer semantics, power/wake, and event delivery are **not** safely universalized. A normalized intent such as “send key” can exist above adapters, but the protocol details and availability remain ecosystem-specific.
- “TV identity” is not one portable type. A serial/UDN, UUID, client key, opaque token, and TLS/server certificate answer different questions. The product must not claim identity-change protection unless the adapter has a persistent identity that can be checked and a fail-closed behavior.
- A shared host can still be useful, but the host must tolerate request/response HTTP, long-lived WebSocket, TLS/protobuf, vendor prompts, push subscriptions, polling, and no-event transports. The table does not select whether that host lives in shared runtime, native code, a shared core, or a hybrid.

**Architecture hypothesis result:** Samsung-specific seams still make sense as research boundaries after removing Samsung assumptions, but a concrete platform-hosted session service does not generalize. Roku’s current policy is especially important: ECP is technically rich but the current official documentation says commands from third-party/mobile platforms are not permitted and many devices require the user setting. Roku is therefore a vendor-policy/release gate, not a ready universal-adapter target. [V01]

### 4.2 Empirical architecture evidence from Home Assistant

Home Assistant is evidence about how a maintained multi-vendor integration handles heterogeneity, not an AppT design prescription. Its current `samsungtv`, `roku`, `webostv`, and `androidtv_remote` directories are separate integrations with vendor-specific config flows, transports/coordinators, diagnostics, and capability-specific entities/services. [V03] [V08] [V09] [U05]

Observed patterns worth carrying into research:

- **LG webOS:** SSDP/config-flow discovery, a stored `client_secret`, a device UUID used as a stable integration identity, reconnect/polling through a coordinator, pushed state where available, optional media/switch/notification entities, and Wake-on-LAN as a separate best-effort action. [V03]
- **Roku:** ECP HTTP discovery/queries, device/app capability reads, coordinator-style refresh, and command/media entities that are bounded by the device’s available endpoints and settings. [V09]
- **Android/Google TV:** a separate integration and config flow around the Android TV Remote Service/maintained protocol client, with app/deep-link launch, remote controls, optional IME, and explicit limitations. [V08]
- **Samsung:** a separate integration with Samsung-specific pairing/transport/diagnostics rather than pretending that all TVs share the same session behavior. [U05] [V10]

This is useful empirical decomposition: a normalized product host can coexist with vendor-specific pairing, transports, coordinators, diagnostics, and optional features. It does **not** prove that Home Assistant’s coordinator, its server-side lifetime, or its entity model is appropriate for a foreground phone app, and it does not settle coordinator/session placement for AppT.

KDE Connect is an optional comparison rather than a TV dependency: its protocol separates discovery, capability advertisement, explicit pairing, TLS/device identity, and restrictions on packets before pairing. It reinforces the value of an explicit trust/capability state machine, but its implementation license is not permission to copy code and its daemon/device model differs from AppT. [V11]

### 4.3 Credential shape, identity, and feature honesty

| Ecosystem | Observed credential shape | Persistent identity evidence | Fail-closed conclusion for AppT research |
|---|---|---|---|
| Samsung | Modern opaque approval/token material; legacy cohorts may use encrypted/PIN/session material. | Uneven device metadata, MAC, token, and TV certificate evidence; no single current cross-cohort identity contract. | Require fail-closed behavior where a persistent identity can be established; otherwise mark identity protection unresolved and do not imply it. |
| Roku | Current ECP source does not establish a cryptographic pairing secret. | Serial/UDN/device metadata are useful matching hints, not documented authenticated peer identity. | Do not claim identity-change protection from ECP metadata; vendor-policy eligibility is a separate release gate. |
| LG webOS | Prompt/client key or `client_secret` stored by maintained integrations. | Device UUID is a useful integration identity; community certificate/TOFU behavior does not prove per-TV authenticity. | Bind and reject changes only where the evidence supports it; otherwise surface identity protection as unresolved. |
| Android/Google TV | Client certificate/private key plus pairing secret/material in maintained protocol clients. | Server certificate and pairing identity are promising, but current public third-party status and product pinning policy remain unresolved. | Preserve explicit identity-change fail-closed behavior as a requirement; validate the certificate/pairing binding before claiming it. |

Feature honesty rules for every adapter:

1. Do not show a control because a protocol library has a method. Show it only after the current device/session provides evidence that the command or event is supported.
2. `connected`, `paired`, `identityProtected`, `textInput`, `pointer`, `appLaunch`, `wake`, and `stateEvents` are separate states. `connected` must not imply the others.
3. Treat “unknown,” “unsupported,” “temporarily unavailable,” “permission denied,” “vendor policy blocked,” and “identity changed” as distinct outcomes where the adapter can observe them.
4. Diagnostics should record protocol family/version, discovery source, permission state, pairing method, secret **type** (never value), identity evidence/fingerprint status, capability evidence, last successful event/command class, and network/lifecycle transitions. Redact IP/MAC/TV identifiers from ordinary logs unless an explicit support export requires them.
5. If a protocol supplies only a weak/non-specific identity, the UI and diagnostics must say that identity protection is unresolved rather than silently applying a TOFU or serial-number guarantee.

## 5. Mobile operating-system constraints independent of frameworks
### 5.1 iPhone / iOS
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

**Inference:** all candidate frameworks can invoke these capabilities through their platform integration, but direct native controls provide a useful baseline for exact behavior. A shared UI must be tested with VoiceOver, Dynamic Type, system text input, haptics disabled/enabled, and one-handed layout on physical devices; this does not decide where the TV session belongs.

### 5.2 Android
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

**Inference:** Android foreground handling of volume keys is technically plausible, but consuming or suppressing system volume behavior, Samsung One UI behavior, media sessions, accessibility services, and lock-screen/background cases requires device testing. It should not be generalized from an emulator or one Pixel device. Android text fields/IME, haptic feedback, TalkBack semantics, and large touch targets have direct platform support; Compose/View choices affect polish but not feasibility. [S22]

## 6. Logical seams to preserve regardless of stack
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

## 7. Candidate families
The families below are compared as ways to deliver the product and its platform seams. None is assigned a session owner in advance. A native API, plugin, or source set may implement an OS-required seam while discovery, transport, pairing, and state remain shared; conversely, a shared runtime may prove sufficient for a given protocol. That distinction must be measured on devices.

### Native-development evidence baseline

Native development receives the same evidence treatment as cross-platform candidates. Apple’s current app-development material documents Xcode, SwiftUI/UIKit, accessibility, state/lifecycle, persistence, and error handling; XCTest documents unit, performance, UI, asynchronous, and device-oriented testing. Android’s current Compose course documents Android Studio, Kotlin, device/emulator execution, state, and unit testing; Android testing guidance documents local and instrumented tests. The NDK documents C/C++ through CMake/Gradle/JNI. These sources establish available toolchains and test surfaces, not that native code is a product fit, that native sessions are more reliable, or that Samsung’s undocumented protocol is supported. [S60] [S61] [S62] [S63] [S64] [S65]

### A. Fully native Swift + Kotlin/Jetpack

Two platform applications share product vocabulary, protocol specifications, fixtures, and capability rules, while implementing UI and OS integrations natively. Samsung control can use Swift Network.framework/URLSession or carefully scoped lower-level APIs on iOS and Kotlin/Java networking/Android APIs on Android. Secure storage, lifecycle, keyboard, accessibility, haptics, and volume behavior are directly available.

**Strength:** the most direct platform-development baseline and the clearest way to isolate OS policy from product code.
**Cost:** duplicated UI/domain glue, two release toolchains, and a risk that adapter behavior diverges unless protocol/capability contracts and traces are shared. Direct platform access does not prove that a platform-owned session is more reliable or that the resulting product is a better fit.

### B. Kotlin Multiplatform; native UI or Compose Multiplatform UI

KMP can share domain, serialization, state machines, protocol logic, and parts of networking while compiling shared code to Kotlin/JVM on Android and Kotlin/Native on iOS. JetBrains documents Android/iOS core KMP as stable and Compose Multiplatform Android/iOS UI as stable; KMP can also leave UI and platform code native. [S49] [S50] [S51]

Ktor supplies multiplatform clients with platform-specific engines; the engine table shows Android/OkHttp or Android options and Darwin/NSURLSession on Apple platforms, with different WebSocket/TLS capabilities. [S52]

**Strength:** a credible shared-domain/protocol option without a JavaScript bridge, while allowing platform-specific seams or a native UI.
**Cost:** Gradle/Xcode/Swift boundary, Kotlin-to-Swift API ergonomics, platform source sets, Compose iOS accessibility/interop validation, and build/test complexity. KMP’s common/platform test model is available, but it does not replace XCTest/JUnit and physical-device validation. Shared code does not remove platform-specific discovery, entitlement, secure storage, or lifecycle work, and the report has not established whether the session belongs in common or platform code. [S53]

### C. React Native (native-capable/bare)

React Native shares UI and JavaScript/TypeScript application logic while exposing native modules/components. Current React Native’s New Architecture is the default direction; React Native 0.82 became New-Architecture-only, and 0.87 (released 2026-08-11) makes the strict TypeScript API default while still requiring native iOS/Android toolchains. [S38] [S40]

React Native documents WebSocket support and `AppState` foreground/background notifications. [S37] [S39]

**Strength:** broad product/UI ecosystem, fast iteration, and several possible control placements: direct JS networking, native modules/JSI, a shared core, or a hybrid.
**Cost:** the control seam crosses JS/native/runtime boundaries; tests must show whether a JS-owned session survives the required lifecycle or whether another placement is safer. Native modules, entitlements, secure storage semantics, and Android/iOS lifecycle still require maintenance and device validation. The New Architecture reduces old bridge concerns but does not make OS behavior cross-platform.

### D. Expo (React Native distribution/tooling family)

Expo SDK 57 is documented as the latest stable reference and pins a specific React Native version (the observed table pairs SDK 57 with RN 0.86), while RN itself has moved to 0.87. Expo releases three times a year and aligns to one RN version per SDK. [S41]

Expo says Expo Go cannot use third-party libraries requiring custom native code and recommends development builds for production-grade work. CNG/prebuild/config plugins can add native code and configuration, but direct native changes can be overwritten unless represented through the supported module/config mechanism. [S42] [S43]

**Strength:** useful React Native workflow, native module API, development builds, config plugins, and release tooling.
**Cost:** version pinning and config generation are additional maintenance seams. AppT cannot use an Expo Go-only workflow. The Samsung control module, iOS multicast entitlement, local-network usage description, Android local-network permission, certificate configuration, and lifecycle integration must be represented in native modules/config plugins and validated in custom builds; this requirement does not predetermine session ownership.

### E. Flutter

Flutter renders its own UI and uses Dart for application logic. It supports sockets/WebSockets through Dart packages and platform channels/plugins for native APIs. Flutter documents asynchronous MethodChannel/Pigeon boundaries, platform-side background task queues, and serialized platform-channel messages. [S44] [S45]

Flutter’s current documentation includes an Android local-network-permission guide warning that Dart sockets cannot display the Android runtime permission prompt and that permission must be requested before opening a local socket. It also documents that background isolates cannot receive unsolicited host-platform messages in the general case. [S46] [S47]

Flutter 3.47 is a current 2026 stable release and has ongoing iOS lifecycle/toolchain changes; its release notes/blog emphasize native integration and platform migration work. [S48]

**Strength:** high UI consistency, widget testing, direct Dart sockets for some work, and a clear plugin/native escape hatch.
**Cost:** platform channels/serialization and isolate/lifecycle behavior can become part of the control path; iOS permission/entitlement and certificate handling still need platform integration; text input, VoiceOver/TalkBack, haptics, and OS-specific controls need real-device validation. Dart-owned, plugin-owned, shared-core, and hybrid session models all remain candidates.

### F. .NET MAUI

Microsoft documents .NET MAUI as a C#/XAML framework for Android, iOS, macOS, and Windows with direct access to platform APIs, handlers, platform source, and native app packaging. It provides cross-platform connectivity and secure-storage abstractions, while still requiring platform-specific setup and handling differences such as Android backup behavior. [S55] [S56]

**Strength:** native-capable C# stack, shared business logic/UI, platform access, and mature .NET testing/tooling.
**Cost:** AppT-specific evidence for Samsung LAN discovery, local-network entitlement/permission details, WSS trust pinning, and lifecycle behavior is thinner in this research. The abstraction must be checked rather than assumed to cover SSDP/mDNS, raw sockets, WoL, and hardware buttons. A team without current .NET/iOS expertise would carry an additional staffing risk; neither this gap nor the native APIs selects session ownership.

### G. Capacitor/Ionic or WebView-first hybrid

Capacitor builds a web UI into native iOS/Android projects and exposes native functionality through plugins. Its documentation says developers are encouraged to write Swift/Java/Kotlin custom native code and compile through Xcode/Android Studio; current v8 is active with iOS 15+/Android 7+ minimums. [S57] [S58] [S59]

**Strength:** web UI ecosystem, fast iteration, open native projects, and straightforward custom plugin escape hatches.
**Cost:** a WebView adds a process/runtime boundary for UI and event flow. Local LAN permissions, low-level discovery, TLS identity, background/lifecycle, keyboard, haptics, and secure storage must cross plugins. A plugin may own the session, or a WebView/shared runtime may be sufficient for some adapters; the latter is higher risk and must be tested rather than assumed impossible.

### H. Shared Rust/C++ protocol core with native UI shells

A portable protocol/state library could share parsing, command serialization, adapter state machines, redaction rules, and test fixtures. Swift/Kotlin/native shells could expose discovery APIs, secure storage, permissions, lifecycle, haptics, accessibility, text input, and OS-specific volume behavior.

**Status:** research hypothesis only. This report has not established equivalent evidence for FFI, memory/concurrency ownership, TLS/trust callbacks, secure-storage boundaries, lifecycle, testing, packaging, debugging, or UI/accessibility integration. Android’s NDK documents C/C++ packaging through CMake/Gradle/JNI, and Rust’s official material documents WebAssembly interoperation, but neither establishes that a shared native control core is the right AppT architecture. [S60] [S61]

**Potential strength:** protocol logic could be centralized if the interoperability and operational costs are justified.
**Potential cost:** specialized toolchains, symbol packaging, FFI failure modes, debugging across languages, and another boundary for secrets/events. It is not assigned a higher confidence than native or shared-runtime alternatives.

## 8. Common comparison matrix
This matrix separates **OS-required platform work** from the unresolved question of **who owns a TV session**. `Shared-runtime feasible` means the work is technically plausible in the candidate’s shared language/runtime; it does not mean that lifecycle, permission, or reliability has been demonstrated. `Evidence quality` describes this research, not production readiness.

### 8.1 Platform work, shared-runtime feasibility, and session ownership
| Candidate family | OS-required platform work | Shared-runtime feasibility | Native escape-hatch availability | Session ownership status | Evidence quality | Real-device validation status |
|---|---|---|---|---|---|---|
| **Swift + Kotlin/Jetpack** | Direct access to iOS/Android permission, multicast/NSD, lifecycle, secure storage, haptics, accessibility, keyboard, and packaging APIs. | Shared protocol fixtures/specifications are possible; UI/domain sharing is optional rather than inherent. | Native implementation is the baseline. | **Unresolved.** Native, shared service/library, or hybrid ownership can still be compared; direct APIs do not prove native ownership is best. | High for platform APIs; low for AppT/Samsung outcome until tested. | No AppT TV/phone cohort has been run. |
| **Kotlin Multiplatform** | iOS/Android source sets or shells handle entitlements, permissions, lifecycle, secure storage, and OS UI. | Strong candidate for common domain, protocol, capability, and possibly transport/session code; engine/TLS differences require tests. | Objective-C/Swift interop, `expect`/`actual`, UIKit/SwiftUI and Android interop. | **Unresolved.** Common session, platform transport, or hybrid remain plausible. | High framework/platform documentation; medium AppT integration evidence. | No AppT device validation. |
| **React Native** | Native project/modules handle entitlements, manifests, permission prompts, lifecycle, secure storage, and any raw discovery API. | JS WebSocket/HTTP and state coordination are technically plausible; multicast/UDP/library and suspension behavior need tests. | Native modules, TurboModules/JSI, native components, custom iOS/Android projects. | **Unresolved.** JS-owned, native-module-owned, shared-core, and hybrid models are all open. | High framework API evidence; low AppT reliability evidence. | No AppT device validation. |
| **Expo custom build** | Same as React Native plus CNG/prebuild/config-plugin and SDK/RN pinning discipline; Expo Go is insufficient. | Shared JS work remains plausible for supported transports; custom-build lifecycle and networking need tests. | Development builds, Expo Modules API, config plugins, generated native projects. | **Unresolved.** Custom native configuration is required, but it does not choose session owner. | High for workflow limits; low AppT outcome evidence. | No AppT device validation. |
| **Flutter** | Runner/plugins handle Android permission prompts, iOS entitlements, lifecycle, secure storage, haptics, accessibility, and native packaging. | Dart sockets/WebSockets and common state are plausible; unsolicited events/isolate behavior and multicast need tests. | Platform channels, Pigeon, FFI, platform views, open native projects. | **Unresolved.** Dart, plugin, shared-core, or hybrid ownership remain candidates. | High framework evidence; medium platform-seam evidence; low AppT outcome evidence. | No AppT device validation. |
| **.NET MAUI** | `Platforms/*`, handlers, manifests, entitlements, lifecycle, secure storage, and native packaging remain required. | Shared C# domain/network code is plausible; raw discovery, WSS identity, and lifecycle need a focused check. | Handlers, partial classes, platform APIs, native projects. | **Unresolved.** Shared C#, native, or hybrid session placement is open. | High general framework evidence; thinner AppT-specific evidence. | No AppT device validation. |
| **Capacitor/WebView** | Native iOS/Android projects/plugins handle permissions, discovery, lifecycle, secure storage, haptics, keyboard, and packaging. | HTTP/WebSocket-like work in the WebView is technically possible; raw LAN, trust identity, suspension, and event delivery are higher-risk. | Swift/Java/Kotlin plugins and open native projects. | **Unresolved.** Plugin-owned, WebView/shared-runtime, or hybrid models require comparison; WebView-only is not a credible baseline. | High plugin/native-project evidence; low AppT control evidence. | No AppT device validation. |
| **Rust/C++ shared core** | Native shells handle all OS policy and user-facing integration; FFI packaging and callbacks are additional platform work. | Common parsing/state/transport logic is technically plausible, but this report has not established equivalent operational evidence. | FFI/JNI/Objective-C or C++ interop and native shells. | **Unresolved.** Shared core with native transport, core-owned transport, or hybrid are hypotheses. | **Low for AppT:** research hypothesis; no evidence parity with native/shared-runtime alternatives. | No AppT device validation. |

**Reading rule:** “native escape hatch available” does not determine where a session should run. Conversely, a shared-runtime row is not a promise that the runtime survives every lifecycle state. The missing evidence is exactly what Experiment 9 measures.

### 8.2 Reliability-critical concern comparison
| Concern | Native baseline | Shared-runtime possibility | Native/plugin/core hybrid possibility | Current research judgment |
|---|---|---|---|---|
| **LAN discovery** | First-party Network.framework/Bonjour, Android NSD/raw sockets, and platform permission APIs are directly available. | JS/Dart/Kotlin/C# can coordinate or perform supported sockets; multicast/broadcast, permission prompts, and address changes still need platform integration. | Native layer can discover and pass candidates/permission state to common code; a shared core should not assume raw multicast. | Keep discovery as an explicit coordinator. Compare where it runs rather than assigning it to native by default. |
| **SSDP/mDNS/multicast/broadcast** | Direct but entitlement/permission-sensitive. | Runtime libraries may support some sockets; they cannot bypass iOS multicast entitlement or Android local-network policy. | Platform transport/discovery wrapper with common candidate model is plausible. | OS seam is known; session/discovery ownership remains empirical. |
| **Direct TCP/WebSocket/TLS** | Direct APIs and native trust callbacks. | WebSocket/HTTP are technically available in RN, Dart, KMP/Ktor, .NET, and WebView contexts, subject to runtime limits. | Shared serializer/state plus native socket/trust callback is plausible; native TLS does not imply native state-machine ownership. | Test system trust, scoped identity/pinning, cancellation, and event delivery for each placement. |
| **Samsung pairing/tokens** | State machine and Keychain/Keystore can be implemented directly. | Shared code can represent approval/token transitions; secrets must never enter ordinary runtime persistence/logs. | Native secure store plus shared adapter state is plausible; a native module may expose opaque handles only. | Pairing shape is Samsung/protocol evidence, not a framework decision. |
| **Reconnect/IP changes** | Scene/activity/network callbacks are direct. | Shared state machines can be idempotent; JS/AppState, Dart isolates, Kotlin coroutines, and .NET lifecycle callbacks need device tests. | Native lifecycle notification can drive common reconnect logic; persistent native session is not proven necessary. | Require explicit Reconnecting/IdentityChanged/PairingRequired states. |
| **Wake / Wake-on-LAN** | Raw packet/interface and platform networking are available, subject to network behavior. | Dart/ Kotlin/.NET or libraries may send packets; RN/WebView support is runtime/library-dependent. | Native interface selection plus common capability result is plausible. | Treat wake as adapter capability and best effort; never advertise from transport availability alone. |
| **Secure storage** | Keychain/Keystore are direct. | Shared code can request an abstraction but should not implement the OS vault or place secrets in JS/Dart/Web storage. | Native vault with opaque secret handle/bridge is plausible; audit backup/migration semantics. | This is an OS-required seam, not evidence for session ownership. |
| **iOS/Android lifecycle** | Direct scene/activity callbacks and policy controls. | Runtime lifecycle notifications are useful but may arrive late or not preserve a socket/isolate. | Native callback can trigger a common state machine or native transport; both require tests. | No persistent background control assumption. |
| **Text/pointer/keyboard** | Native phone text/IME/accessibility plus adapter-specific TV wire format. | Shared UI and event models are possible; TV pointer/text semantics remain ecosystem-specific. | Native input collection plus shared command mapping is plausible. | Capability-gate `textInput`/`pointer`; do not infer from phone keyboard availability. |
| **Accessibility/haptics** | Direct platform semantics/effects. | Cross-platform widgets can provide semantics but custom remote controls need audits. | Native components can be wrapped while state remains shared. | Native UX evidence and control-session evidence are separate acceptance gates. |
| **Account/local boundary** | Easy to separate secure vault, local DB, and account DTOs, but still requires discipline. | Any runtime can leak secrets through persistence, debugging, or sync if types are not separated. | Common non-secret profile plus native secure store is plausible. | Test redaction, independent pairing, backup/restore, and backend outage; no secrets sync. |

## 9. Candidate-family observations by required concern

### 9.1 LAN discovery is the main cross-platform trap
The existence of a socket API in every candidate is not the same as equivalent discovery behavior:

- iOS custom multicast/broadcast may require a restricted entitlement that needs Apple approval; Bonjour declarations and a user-facing local-network reason are still required. [S23] [S24]
- Android is moving from implicit LAN access toward explicit local-network permission, with raw TCP/UDP and SSDP/mDNS included. [S13] [S14]
- A framework library can open a socket, but cannot itself grant an OS entitlement, explain a runtime prompt, guarantee multicast reception during power saving, or make guest-network multicast work.
- Direct unicast `/24` probing is an empirical fallback used by community remote apps, but AppT must measure whether it is acceptable for ordinary consumers and whether a privacy review considers the breadth of the scan appropriate. This is not a universal substitute for service discovery.

**Implication:** discovery should be an explicit platform seam in every candidate. It should report permission state, network scope, candidate source, confidence, and cancellation, not just return a list of IP addresses.

### 9.2 Samsung WSS security can dominate the stack decision
The community’s ability to connect with `wss://` is not proof that a platform’s default TLS verifier can authenticate the TV. Some community clients disable verification, while AppT explicitly prohibits a global bypass. The following must be answered experimentally:

- What certificate chain/identity does each Samsung cohort present?
- Is the certificate stable across a reboot, IP change, router change, TV firmware update, and re-pair?
- Can the iOS and Android transport verify a narrowly scoped identity without accepting arbitrary local certificates?
- Does the TV expose a trustworthy identity in device info that can be bound to the TLS identity, or would this only be a TOFU/pinning decision?
- What should happen when an identity changes: explicit re-pair, warning, or unsupported?

A framework that offers a “trust all certificates” flag is not a solution; it is a red flag for AppT’s security requirements.

### 9.3 Local-first and account sync are easy to violate accidentally
All families can implement local-first behavior, but shared JavaScript/Dart/C#/Kotlin state stores make it easy to pass one large “TV profile” object to sync. The profile must be divided explicitly:

- **Synchronizable:** friendly name, favourites, non-secret preferences, control layout/rearrangement, and other approved non-secret data.
- **Phone-local:** pairing approval state, Samsung token, certificate/public-key pin or identity evidence, MAC/wake hints, IP history, network/diagnostic details, and sensitive failure evidence.
- **Never ordinary logs/analytics:** token, private key, raw command text, Wi-Fi name, local IP, directly identifying TV data, or complete device-info payload.

Secure storage wrappers are not automatically equivalent. Apple Keychain accessibility/migration and Android Keystore/backup behavior must be chosen and tested. [S20] [S29] [S30] [S56]

### 9.4 Platform-native controls matter even when UI is shared
A remote is an interaction-heavy application with custom directional/touchpad controls, one-handed layout, haptic feedback, keyboard input, screen-reader state, scalable text, and possibly hardware button integration. Shared UI can reduce duplication, but it must not hide:

- whether a custom control exposes correct accessibility role/name/value/actions;
- whether gesture and touchpad modes remain usable with VoiceOver/TalkBack;
- whether text input and cancellation behave like a native form;
- whether haptics respect system state and the product setting;
- whether the physical volume-button requirement is legal/possible on iPhone.

The last point is a platform/product gate, not a framework feature comparison.

## 10. Viability assessment without selecting a stack
### 10.1 Unconditional vs conditional viability
No candidate is unconditionally accepted because Samsung behavior and the iPhone volume requirement are not resolved. The following is the useful discovery classification:

| Family | Current viability posture | Conditions that must be demonstrated |
|---|---|---|
| Fully native Swift + Kotlin | **Viable candidate / baseline** | Samsung adapter and TLS identity work on target cohorts; duplicate platform code remains maintainable; iPhone volume requirement is resolved or removed/changed. Direct APIs are a baseline advantage, not proof of native session ownership. |
| KMP with native UI | **Viable candidate** | Shared protocol/state code and any selected session placement work on real devices; Swift interop and secure storage/lifecycle boundaries remain understandable to both teams. |
| KMP with Compose UI | **Viable candidate with UI validation** | VoiceOver/TalkBack, text input, haptics, touchpad gestures, and scene/activity lifecycle pass physical-device tests; native interop remains available. |
| React Native bare/native-capable | **Viable candidate with control-placement experiment** | The team compares JS/shared, native-module, shared-core, and hybrid session placements; secure storage, discovery, permissions, lifecycle, and New Architecture modules are stable on devices. |
| Expo custom development build/CNG | **Viable candidate with higher configuration burden** | No reliance on Expo Go; entitlements/manifests/config plugins are deterministic; at least one session placement works in custom builds; SDK/RN cadence is acceptable. |
| Flutter | **Viable candidate with control-placement experiment** | Dart/plugin/shared-core/hybrid placements are compared; local-network permission precedes sockets; accessibility, keyboard, lifecycle, and trust policy pass device tests. |
| .NET MAUI | **Conditional viable candidate** | A focused check confirms SSDP/mDNS/raw sockets, WSS identity handling, secure storage, lifecycle, and device packaging on the target iPhone/Android range. |
| Capacitor/WebView-first | **Conditional viable candidate** | A native plugin or another explicit integration handles OS-required work; WebView/shared-runtime and plugin/hybrid session behavior is measured; reload/suspension/permission behavior is acceptable; no token enters Web storage. |
| Rust/C++ shared core plus native UI | **Research hypothesis, not yet a viability classification** | Equivalent evidence is needed for FFI, TLS/trust callbacks, secret boundaries, lifecycle, testing, packaging, debugging, and UI/accessibility integration. It must compete fairly with native and shared-runtime alternatives. |

This is not a ranking. It says which candidates deserve a focused validation path and which assumptions would make them nonviable.

### 10.2 What would make a family nonviable
Regardless of label, a candidate should be rejected for V1 if its normal shape requires any of the following:

- backend/public internet in the ordinary TV command path;
- pairing tokens in account sync, JavaScript/Web storage, ordinary preferences, or ordinary logs;
- global TLS certificate-verification bypass;
- a background service/connection that cannot meet iOS or Android policy expectations;
- a UI/runtime arrangement that cannot expose or reliably test the platform-required discovery, secure-storage, lifecycle, and Samsung-transport seams;
- an Expo Go/WebView-only path for a capability that requires custom native integration;
- a hard-coded Samsung model/year matrix without observed capability detection;
- an untested assumption that physical iPhone volume buttons can be repurposed for TV commands;
- an inability to run real iPhone + Android + Samsung TV tests in CI/lab or a disciplined manual matrix.

## 11. Reliability and maintainability risks by family
### Native

The central risk is duplication: two adapter implementations can disagree on command timing, key names, token refresh, or capability mapping. Mitigations are platform-independent fixtures, a shared protocol specification, recorded/redacted device traces, and contract tests. The benefit is that failures are close to the platform API and easier to reason about.

### React Native / Expo

The central risks are session-placement evidence and build surface: how a shared-runtime, native-module, shared-core, or hybrid session behaves after JS reload/suspension; whether every module supports the current New Architecture; and whether generated/native configuration remains deterministic. React Native’s architecture and native module systems are credible, but they do not answer those questions. Expo makes custom native work possible, but the correct mental model is “React Native app with generated/custom native projects,” not “managed JavaScript with no platform maintenance.” [S38] [S42] [S43]

### Flutter

The central risks are channel boundaries, background/isolate semantics, and custom-platform UI behavior. A socket held in Dart may be technically correct in the foreground and still fail when host lifecycle, permission, or background event delivery changes; a plugin or shared core may have a different trade-off. No placement is promoted until the same failure/recovery tests are run for each candidate. [S44] [S46]

### KMP / Compose

The central risks are version/build coordination and Swift-facing APIs. KMP’s native binary model is attractive for shared protocol/domain/session experiments, but teams must understand Objective-C-mediated interoperability, suspend/Flow exposure, Kotlin/Native compilation, and platform-specific source sets. Compose Multiplatform’s iOS stability is a positive signal, not proof that every AppT custom control is as accessible or platform-native as UIKit/SwiftUI. [S49] [S50] [S51] [S54]

### .NET MAUI

The central risk is evidence gap rather than a known hard blocker: AppT needs raw local discovery, WSS identity handling, Samsung protocol-specific behavior, and high-quality custom remote controls. MAUI’s platform access means these may be implementable, but the project should not treat its abstractions or a particular session placement as proven until a focused integration check passes. [S55] [S56]

### Capacitor

The central risk is false simplicity. Building the remote UI is easy; deciding whether WebView/shared-runtime, plugin, or hybrid code can deliver reliable control and safe secret/event boundaries is the real application. Plugins may reduce OS-seam uncertainty but add another lifecycle/reload boundary and do not automatically prove that a plugin should own every session. [S57] [S58]

### Shared native core

The central risk is specialized complexity. Rust/C++ could make protocol state and fixtures consistent, but the current report has not substantiated FFI, trust callbacks, secret boundaries, lifecycle, testing, packaging, or UI/accessibility integration at parity with the other choices. It cannot abstract away iOS local-network entitlements, Android local-network permissions, secure storage, haptics, accessibility, scene/activity lifecycle, or App Review. It remains a hypothesis to test, not an implied reliability solution.

## 12. Unresolved questions
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

## 13. Weak, stale, or contradictory evidence
1. **Official vs current:** Samsung’s strongest official sender material is historical Smart View SDK documentation. The current download page lists recent package artifacts, but the conceptual sender docs still focus on TV applications and do not publish a generic current remote contract. This is useful evidence with low guarantee strength for AppT’s exact use case. [S03] [S04] [S05]
2. **Community port disagreement:** community code reports both 8001 and 8002, different token/popup behavior, and insecure-certificate workarounds. The convergence proves a useful family of observed behavior, not a stable protocol contract. [U01] [U03] [U06] [U07]
3. **Generational contradictions:** OpenHAB’s broad “pre-2016 legacy / post-2016 WebSocket” guidance is contradicted by current Home Assistant reports of a 2016 K-series encrypted fallback and current authorization timeouts. Both should be retained as evidence that model/firmware capability detection is required, not as a single truth table. [U03] [U04]
4. **Certificate evidence:** community libraries that set `CERT_NONE` demonstrate an interoperability problem but do not establish what certificate Samsung intends or whether a safe pinning scheme is possible. The security conclusion is unresolved, not “disable verification.” [U01] [U05]
5. **Android release timing:** Android’s local-network docs describe API 36 opt-in and API 37 enforcement. This is a current official planning input, but target SDK/device release timing should be revalidated immediately before implementation. [S13] [S14]
6. **Framework vendor claims:** React Native, Flutter, KMP, Expo, MAUI, and Capacitor documentation establishes platform access and supported mechanisms. None of it proves AppT’s Samsung reliability, real-device latency, App Store approval, or protocol compatibility. Those are experiment results.
7. **Volume-button sources:** Apple’s public documentation and App Review text are stronger than community workarounds. Private API, hidden view hierarchy, silent-audio, or system-volume tricks are not acceptable evidence for a public V1. [S32] [S33] [S34]
8. **Historical repositories:** Some Samsung repositories are old and tested on one device. They are useful reverse-engineering records, not current support matrices. [U06] [U07]
9. **Roku policy versus protocol:** The current official ECP page documents a technically rich interface but also says third-party/mobile-platform commands are not permitted and that device settings gate control. The scope, eligibility, and release interpretation need vendor/legal confirmation; this is not merely an adapter coding gap. [V01]
10. **LG public-contract gap:** Current LG developer pages/forum material did not provide a formal public SSAP remote reference; port, certificate, pointer, and client-key details come from maintained community implementations and Home Assistant. These are useful empirical evidence but not LG support guarantees. [V02] [V03] [V04] [V05]
11. **Android/Google TV age/status gap:** AOSP’s accessible Polo repository is an older implementation/message source, while current Remote Protocol v2 behavior comes from maintained reverse-engineered code and integrations. The attempted AOSP README path was unavailable, no current public third-party remote contract was established, and no AppT device validation exists. [V06] [V07] [V08]
12. **Home Assistant transfer gap:** Its vendor-specific coordinators/config flows demonstrate empirical decomposition, but Home Assistant is a long-running server/integration host rather than a foreground phone app. It does not settle AppT session placement or lifecycle behavior. [V03] [V08] [V09]
13. **Source-register provenance:** Current pages were rechecked on 2026-09-22; no unverified commit/hash is asserted for maintained implementations. The Compose Multiplatform page currently reports 1.12.1, so an older 1.12.0 snapshot should not be carried forward. [S54]

## 14. Proposed risk-reducing experiments
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

### Experiment 9 — Session-placement comparison

**Question:** For the same adapter, device cohort, UI workload, and failure conditions, which session placement gives AppT the best measured reliability and maintainability without assuming that a platform escape hatch must own the session?

**Models to compare:**

1. **Native-owned:** platform code owns discovery orchestration, pairing/token state, transport, command serialization, reconnect state, and event/state machine. Shared code receives product-level state/events only.
2. **Shared-runtime-owned:** the selected shared runtime owns discovery/session state, transport, serialization, reconnect, and events where its APIs permit it. Native code supplies OS-required permission, entitlement, secure-storage, lifecycle, and other seams.
3. **Shared-core/native-transport:** a shared core owns protocol/state/serialization; native platform code owns sockets/TLS/trust callbacks, OS lifecycle hooks, and secure storage, with a narrow FFI boundary.
4. **Hybrid:** ownership is split intentionally—for example, a shared domain/adapter state machine with platform transport and secure storage, or a shared transport with platform lifecycle/recovery. The boundary, event loss behavior, and secret flow must be explicit rather than accidental.

These are comparison models, not four recommendations. “Native transport” in model 3 is not the same claim as “native-owned session” in model 1.

**Scope:** After the Samsung cohort and discovery/TLS experiments identify a reproducible target, exercise equivalent minimal flows on physical iPhone and Android devices: discovery and permission, pairing/token storage, one key command, one unsupported capability, reconnect after resume/network/IP change, identity change, cancellation, redacted diagnostics, and (where supported) an unsolicited state event. Use the same adapter fixtures and UI workload. A deterministic simulated TV may precede physical testing, but cannot be the pass gate.

**Measure for every model:**

- pairing completion and re-pair rate by cohort;
- command latency/jitter, ordering, cancellation, duplicate/lost-command rate, and behavior after TV/network failure;
- lifecycle recovery after background, lock, suspension, force-quit, and process/runtime reload;
- event delivery/reconciliation and explicit `Unknown`/`Reconnecting`/`IdentityChanged` states;
- TLS identity verification, fail-closed behavior, secret exposure across FFI/bridges/logs/backups, and account-boundary tests;
- discovery time, permission-denied/revoked behavior, IP-change recovery, battery/memory impact, crash/error observability, test isolation, build reproducibility, and upgrade friction;
- amount and location of platform code, but never treat more or less native code as a reliability result by itself.

**Pass evidence:** a model completes the same test matrix with no global TLS bypass, no secret leakage, no silent identity change, bounded recovery, honest capability presentation, and diagnostics that distinguish unsupported, unavailable, permission-denied, and identity-changed outcomes. The result must include physical-device traces/metrics and the reason a boundary was selected.

**Fail evidence:** a model loses or duplicates commands without reconciliation, requires the UI/runtime to remain alive contrary to platform behavior, cannot observe a necessary lifecycle/permission event, leaks secret material, silently accepts identity changes, or makes a capability appear supported without evidence. A failed model is not proof that another model owns all sessions; it narrows the evidence.

This experiment is a later validation plan, not authorization to add a prototype, native module, dependency, or application code in this discovery change.

## 15. Provisional discovery criteria for a later architecture decision
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

## 16. Current non-binding findings

- **Finding A — the hard part is not generic UI.** Samsung capability discovery, local transport, pairing, identity, reconnect, wake, lifecycle, secure storage, and native phone behavior are the architecture drivers.
- **Finding B — every credible cross-platform option needs platform integration, not a predetermined session owner.** React Native/Expo, Flutter, .NET MAUI, and Capacitor remain possible if they can expose and test the required OS seams. Shared-runtime, native, shared-core/native-transport, and hybrid session placements remain open.
- **Finding C — KMP is a distinct option, not the same trade as a UI bridge.** KMP can share protocol/domain code and possibly session code while retaining native UI and OS integrations; Compose Multiplatform adds UI sharing but also adds an accessibility/interop validation burden.
- **Finding D — native development is the baseline, not automatically the winner.** It provides direct platform APIs and a useful control comparison, but duplicates platform logic and does not prove native session ownership, cross-platform product fit, or lower total reliability risk.
- **Finding E — Expo is a workflow choice, not permission to avoid platform work.** A custom development build/CNG path can be viable; an Expo Go-only interpretation cannot.
- **Finding F — Samsung support must be capability-led.** Official Samsung key guidance, historical SDK material, and community failures all point away from a fixed year/model list.
- **Finding G — no backend in the command path is compatible with every candidate.** It must be enforced by module boundaries and tests, not only by product prose.
- **Finding H — iPhone volume buttons are a product release gate.** There is no architecture selection that makes Apple’s public API and App Review guidance disappear.
- **Finding I — multi-ecosystem evidence validates adapter boundaries, not a universal protocol.** Roku’s vendor-policy restriction, LG’s pairing/push/pointer differences, Android TV’s certificate/protobuf family, and Samsung’s cohort variation require feature honesty and ecosystem-specific evidence.
- **Finding J — Rust/C++ is a research hypothesis.** It should not outrank native or shared-runtime options until FFI, TLS/trust, secret, lifecycle, packaging, testing, and accessibility evidence is comparable.

These findings do not select a stack, a session owner, or a final universal interface, and do not move the repository out of discovery.

## 17. Source register
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
- **[U01]** xchwarze, `samsung-tv-ws-api`, current repository README/structure and maintained implementation, version 3.0.6 observed during the September 2026 review. The report does not assert an unverified commit hash; this remains unofficial evidence. <https://github.com/xchwarze/samsung-tv-ws-api>
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

### Multi-ecosystem vendor and implementation sources

- **[V01]** Roku Developer, “External Control Protocol,” current ECP documentation observed 2026-09-22: SSDP discovery, HTTP/8060, device/app/capability queries, keypress/text/app launch, power fields, the “Control by mobile apps” setting, and the current third-party/mobile command restriction. First-party but vendor-policy-sensitive. <https://developer.roku.com/dev/docs/external-control-api>
- **[V02]** LG webOS TV Developer, home/references pages and official developer forum response observed 2026-09-22; current forum guidance directs external-device connection to Connect SDK, while no formal public SSAP remote reference was located. First-party/current context. <https://webostv.developer.lge.com/>; <https://webostv.developer.lge.com/develop/references>; <https://forum.webostv.developer.lge.com/t/connecting-mobile-apps-to-lg-webos-tv/3082>
- **[V03]** Home Assistant, “LG webOS TV,” current integration documentation observed 2026-09-22: Connect Apps, SSDP/config-flow pairing, client secret, UUID identity, pushed state, optional features, coordinator behavior, and Wake-on-LAN limitations. Empirical maintained integration. <https://www.home-assistant.io/integrations/webostv/>
- **[V04]** hobbyquaker, `lgtv2`, maintained community implementation README observed 2026-09-22: SSAP, WS/WSS 3000/3001 variation, prompt/client-key pairing, subscriptions, pointer socket, app/power operations, WoL, and certificate/TOFU considerations. Reverse-engineered/community evidence. <https://github.com/hobbyquaker/lgtv2/blob/master/README.md>
- **[V05]** griches, `lgtvremote-cli`, and chros73, `bscpylgtv`, maintained community implementations observed 2026-09-22: LG pairing, SSAP operations, pointer/input and adapter capability evidence. Not vendor-supported API evidence. <https://github.com/griches/lgtvremote-cli>; <https://github.com/chros73/bscpylgtv>
- **[V06]** Android Open Source Project, `google-tv-pairing-protocol` repository and `proto/polo.proto`, repository state on `refs/heads/main` observed 2026-09-22. Official AOSP source for an older Polo pairing implementation/message definitions; it does not establish a current public third-party remote API. <https://android.googlesource.com/platform/external/google-tv-pairing-protocol/+/refs/heads/main/>; <https://android.googlesource.com/platform/external/google-tv-pairing-protocol/+/refs/heads/main/proto/polo.proto>
- **[V07]** tronikos, `androidtvremote2`, maintained community implementation `pairing.py`/`remote.py` observed 2026-09-22: certificate/PIN pairing, protobuf Remote Protocol v2, ports 6466/6467, feature flags, app links, IME/voice, power/volume, and state callbacks. Reverse-engineered/community evidence. <https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/pairing.py>; <https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remote.py>
- **[V08]** Home Assistant, “Android TV Remote,” current docs and `androidtv_remote` integration tree observed 2026-09-22: Android TV Remote Service dependency, discovery/config flow, app/deep-link launch, optional IME, diagnostics, and capability limitations. Empirical maintained integration. <https://www.home-assistant.io/integrations/androidtv_remote/>; <https://github.com/home-assistant/core/tree/dev/homeassistant/components/androidtv_remote>
- **[V09]** Home Assistant, current `roku`, `webostv`, and related vendor integration directories observed 2026-09-22: separate config flows, coordinators/transports, diagnostics, and capability-specific entities. Empirical architecture evidence, not an AppT prescription. <https://github.com/home-assistant/core/tree/dev/homeassistant/components/roku>; <https://github.com/home-assistant/core/tree/dev/homeassistant/components/webostv>
- **[V10]** Home Assistant, `samsungtv` integration directory and maintained vendor-specific code observed 2026-09-22. Empirical Samsung pairing/transport/diagnostics evidence; not a current Samsung contract. <https://github.com/home-assistant/core/tree/dev/homeassistant/components/samsungtv>
- **[V11]** KDE Connect protocol reference and device implementation observed 2026-09-22: discovery, capabilities, explicit pairing, TLS/device identity, and pre-pairing packet restrictions. Optional comparative evidence; do not copy implementation code or treat its license as permission. <https://invent.kde.org/network/kdeconnect-meta/-/blob/master/protocol.md>; <https://invent.kde.org/network/kdeconnect-kde/-/blob/master/core/device.cpp>

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
- **[S54]** Kotlin Multiplatform, “Compatibility and versions,” live recheck reports Compose Multiplatform **1.12.1** (not the earlier review snapshot’s 1.12.0), with platform/compiler/version information; last modified and observed 2026-09-22. No unverified commit/hash is asserted. <https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html>
- **[S55]** Microsoft Learn, “What is .NET MAUI?,” native-capable C#/XAML cross-platform model, .NET MAUI 10 docs observed 2026-09-22. <https://learn.microsoft.com/en-us/dotnet/maui/what-is-maui?view=net-maui-10.0>
- **[S56]** Microsoft Learn, “Secure storage,” `ISecureStorage`, Android backup and iOS keychain platform differences, .NET MAUI 10 docs observed 2026-09-22. <https://learn.microsoft.com/en-us/dotnet/maui/platform-integration/storage/secure-storage?view=net-maui-10.0&tabs=android>
- **[S57]** Capacitor, “Development Workflow,” native project/build/plugin model, current v8 docs observed 2026-09-22. <https://capacitorjs.com/docs/basics/workflow>
- **[S58]** Capacitor, “Custom Native Android Code” and “Custom Native iOS Code,” plugin escape hatches, current v8 docs observed 2026-09-22. <https://capacitorjs.com/docs/android/custom-code> and <https://capacitorjs.com/docs/ios/custom-code>
- **[S59]** Capacitor, “App Development Support Policy,” v8 active support and minimum platform/toolchain table, observed 2026-09-22. <https://capacitorjs.com/docs/main/reference/support-policy>
- **[S60]** Android Developers, “Get started with the NDK,” C/C++ with Android Studio, CMake/Gradle, JNI, packaging, and native debugging, current page observed 2026-09-22. <https://developer.android.com/ndk/guides>
- **[S61]** Rust, “WebAssembly,” official Rust/Wasm interoperation and binding overview, current page observed 2026-09-22. This is general interoperation evidence, not proof of AppT FFI suitability. <https://rust-lang.org/what/wasm/>
- **[S62]** Apple Developer, “App Dev Tutorials,” current Xcode/SwiftUI/UIKit, accessibility, state/lifecycle, persistence, and error-handling training observed 2026-09-22. <https://developer.apple.com/tutorials/app-dev-training>
- **[S63]** Apple Developer, “XCTest,” unit, performance, UI, asynchronous, and accessibility-oriented test framework documentation observed 2026-09-22. <https://developer.apple.com/documentation/xctest>
- **[S64]** Android Developers, “Android Basics with Compose,” current Android Studio/Kotlin/Compose, device/emulator, state, and unit-test training observed 2026-09-22. <https://developer.android.com/courses/android-basics-compose/course>
- **[S65]** Android Developers, “Test apps on Android,” local/instrumented/UI testing guidance observed 2026-09-22. <https://developer.android.com/training/testing>

## 18. Harvest Matrix provenance boundary

Harvest Matrix history is treated as a stale, different product concept. Only the following leads were independently rechecked for this report and retained as evidence, with their limits stated above:

- Samsung official key/capability and historical Smart View material, plus current maintained Samsung integrations and reverse-engineered clients.
- Roku’s current official ECP documentation: technically useful discovery/capability evidence, but its current third-party/mobile command restriction makes vendor eligibility a release gate.
- LG’s current developer pages/forum context plus maintained `lgtv2`, other community clients, and Home Assistant evidence for SSDP, client-key pairing, SSAP push, pointer input, app operations, and wake limitations.
- AOSP’s accessible older Google TV pairing repository/protobuf definitions plus maintained `androidtvremote2` and Home Assistant evidence for current-looking certificate/protobuf behavior; current public third-party API status remains unresolved.
- Home Assistant’s separate vendor integrations as empirical evidence for adapter-specific config flows, coordinators/transports, diagnostics, and capability-specific features—not as an AppT architecture decision.
- The live Compose Multiplatform compatibility page, rechecked 2026-09-22, which reports 1.12.1.

The following Harvest assumptions are explicitly rejected and were not imported into this report or the canonical product/project-state files:

- Android-only delivery or Android-first as a settled architecture rather than the approved reliability fallback;
- Kotlin/Compose preselection, a settled ADR, or any predetermined application stack;
- IR, `ConsumerIrManager`, dongles, learned codes, or mirroring as assumed control architecture;
- no account/backend, F-Droid, paid monetization, ads, or any other product/business constraint not present in the approved AppT sources;
- predetermined licensing, crash-reporter/ACRA, telemetry, or analytics decisions;
- a predetermined “no listening socket” rule, native session authority, or any other implementation decision presented as settled;
- treating a third-party license as permission to copy implementation code, or treating community code as vendor authorization.

## 19. Completion state
This report records research and bounded next investigations only. It does not claim an architecture decision, stack selection, implementation plan, or production readiness. The project remains in the **discovery** phase. The next durable decision should be made only after Samsung protocol/TLS, iOS discovery/volume, lifecycle/session-placement, multi-ecosystem eligibility, and real-device experiments produce evidence strong enough to compare the candidates against AppT’s reliability requirement.
