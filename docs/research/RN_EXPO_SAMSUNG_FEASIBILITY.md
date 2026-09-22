# React Native / Expo feasibility for AppT V1 Samsung control

**Status:** research evidence only. This document does not select an architecture, reinterpret product requirements, or change `docs/PRODUCT.md` or `docs/PROJECT_STATE.md`.

**Date of research:** 2026-09-22.

**Question researched:** Can React Native / Expo support a dependable Android + iPhone V1 for AppT without compromising reliable Samsung Smart TV control, local-first operation, security, or the required phone-native experience?

**Answer this report is allowed to give:** evidence and trade-offs for five variants. Not a winner.

The five variants are evaluated separately throughout:

1. Expo managed-style development using standard Expo-supported APIs, including what Expo Go can run.
2. Expo with development builds, prebuild, config plugins, and custom native modules where required.
3. React Native without relying on Expo-managed constraints (bare React Native, with or without Expo modules added later).
4. Native Android + native iOS, as the reliability/control baseline.
5. Android-first, as a fallback if simultaneous Android/iPhone delivery introduces material reliability compromises.

## How to read evidence

Claims are labeled:

- **Official.** Vendor or platform documentation, or a first-party changelog.
- **Community / reverse-engineered.** Maintained third-party implementations, integrator docs, or developer-forum reports. Not Samsung protocol specifications.
- **Inference.** A conclusion drawn from the sources above. Marked as such.

Confidence is **high**, **medium**, or **low**. "Hardware validation required" means a real phone and, where stated, a real Samsung television are needed before the claim can settle an architecture decision.

Every source cited here was retrieved on **2026-09-22** unless a source's own publication date is given. The source register at the end is the verification list.

Greenfield4 material was not used as an AppT requirement. Product constraints below are taken only from `docs/PRODUCT.md`.

## Product constraints this research is not allowed to relax

These are inputs, not findings:

- Reliability is the primary constraint. The physical remote is the reliability baseline.
- V1 targets Android and iPhone when the architecture can support both without materially compromising reliability. Android-first is an acceptable fallback, not a default.
- React Native and Expo are candidates, not an accepted stack.
- Normal control is local-first. After pairing, AppT's backend and the public internet must not be required for that television to keep working as a remote.
- Pairing secrets stay on the phone. They are password-equivalent, use platform secure storage, and are excluded from ordinary logs and analytics.
- Where a persistent television security identity can be established, an unexpected identity change fails closed and requires explicit re-pairing. There is no global certificate-verification bypass.
- Discovery is automatic. Manual networking configuration is not the normal consumer experience. IP changes are handled by rediscovery.
- Controls are capability-driven. Directional buttons and touchpad/swipe are both required where the television supports them. The phone keyboard is required for supported TV text input.
- Physical phone volume buttons control TV volume while the remote is active, with an option to disable that behavior.
- Haptics, one-handed use, screen-reader support, scalable text, strong contrast, large touch targets, and clear labels are first-class.
- Connection drops use quiet automatic reconnection, not repeated modal errors.
- Wake may be attempted, including Wake-on-LAN where appropriate. If a television cannot be awakened, AppT explains the limitation rather than presenting a permanently ineffective power control.
- V1 does not predeclare a Samsung year range. Compatibility is capability-driven.
- Exact Samsung protocol and vendor-terms position is a pre-release legal/vendor review. That review does not block architecture research. This report does not perform that legal review.
- Casting and voice are not launch blockers.

## What "Expo" means in this report

"Expo" is not one runtime. As of the official SDK 57 changelog (30 June 2026, with an update on 27 August 2026), the current stable Expo SDK is **SDK 57**, which includes **React Native 0.86** (0.86.3 as of `expo@57.0.17`) and **React 19.2**. [S1]

| Term | Meaning for AppT | Official basis |
| --- | --- | --- |
| Expo Go | A prebuilt store app with a fixed native module set. It cannot gain AppT-specific entitlements, config-plugin permissions, or custom native modules. | Expo development-build introduction: a development build is "your own version of Expo Go where you are free to use any native libraries and change any native configuration." [S2] |
| Expo SDK APIs | First-party libraries such as SecureStore, Haptics, KeepAwake, and Network, documented under SDK 57. | [S3] [S4] [S5] [S6] |
| Development build | An application binary you own, including `expo-dev-client`, built locally or with EAS. JavaScript reloads without rebuilding; native changes require a new binary. | [S2] |
| Prebuild / Continuous Native Generation | `npx expo prebuild` generates `android/` and `ios/` from the app config, the SDK template, autolinking, and config plugins. SDK 57 clears and regenerates those directories by default. | [S1] [S7] |
| Config plugins | Functions that modify native project files during prebuild (Info.plist, entitlements, Android manifest, Gradle). They do not add runtime capability by themselves; they configure the binary. | [S8] |
| Expo Modules API | Swift/Kotlin native modules with New Architecture support and performance characteristics Expo describes as comparable to Turbo Modules. Usable from an Expo app and from an existing React Native app that installs `expo`. | [S9] |
| EAS Build | Cloud or local compilation. If `android/` and `ios/` are absent, EAS runs prebuild. It does not remove Apple entitlement approval or Play target-SDK rules. | [S2] [S7] |
| New Architecture | Expo's SDK 54 changelog states that SDK 54 is the final SDK with Legacy Architecture support, and that React Native 0.82 removes the ability to opt out. SDK 57 ships React Native 0.86. | [S1] [S10] |

A feature missing from Expo Go is not missing from an Expo production application. Variant 1 in this report means standard SDK APIs and Expo Go constraints. Variant 2 means an Expo production application that may contain custom native code.

This research did not re-read the SDK 55 changelog page to confirm the exact removal of `newArchEnabled`. The combination of the SDK 54 forward statement and SDK 57 shipping React Native 0.86 is sufficient to treat Legacy Architecture as unavailable for a new AppT app. Confidence: high. Hardware validation: not required.

## 1. Samsung Smart TV local control

### 1.1 Official mechanisms, and what they are not

Samsung publishes several relevant surfaces. None of them, in the documents retrieved, is a public specification of the local phone-remote protocol that consumer mobile apps use to emulate a physical remote.

**Smart View SDK (official, and a different product).** Samsung's Smart View SDK is a multiscreen SDK. The sender discovers compatible televisions, launches a receiver application, and communicates with that application over a channel. The getting-started page says a Smart View app implements both a sender and a receiver. [S11] The published feature matrix on that page covers 2014–2017 televisions and lists BLE discovery, web casting, Wake on Wireless LAN, multitasking, and TLS. Wake on Wireless LAN is marked supported on 2016 and 2017 televisions, not on 2015, with a note of a compatibility issue on 2015 sets. TLS is documented for Android/iOS sender apps at SDK 2.3.7 / 2.3.8 and higher, not for the JavaScript sender. [S11] The iOS release note dates SDK 2.3.8 to 14 December 2016. [S12] The separate "Supported TVs" page lists 2014–2017 model floors and says supported models may vary by region. [S13]

That SDK can discover a television and launch an application that the SDK knows about. It does not, in the retrieved pages, document D-pad emulation of the television's own UI, a pairing token for `samsung.remote.control`, or ports 8001/8002. Treating Smart View as the V1 remote protocol would also imply a TV-side receiver application. That is a different setup from the television's own connection-approval prompt, and it is not what `docs/PRODUCT.md` describes. Whether a current Tizen television still answers the 2016 SDK was not established. The published matrix stops at 2017 even though Samsung's model-group table continues through 2026. [S14] Staleness is the honest reading. Confidence: high that the published SDK is not a current full-remote specification. Hardware validation: required before anyone treats the SDK as a viable control path on a 2020s television.

**On-television remote key codes (official, wrong side of the glass).** Samsung's Smart TV "Remote Control" guide lists key names and numeric key codes for applications running on the television. [S15] That page documents how a TV app sees remote input. It does not document a phone-to-TV transport. It should not be cited as the local control protocol.

**Television model groups (official platform map, not a control map).** Samsung's TV Model Groups table, retrieved 2026-09-22, maps product years through **2026** to Tizen versions through **Tizen 10.0**, and earlier years to Tizen or the Samsung Legacy Platform. [S14] This proves Samsung was still shipping Tizen televisions in the 2026 lineup at the time of the page retrieval. It does not prove that any of those models expose a particular local remote API. AppT's product rule against a predeclared year range is consistent with this: model year is not a capability proof.

**SmartThings cloud API (official, not local-first).** SmartThings documents a cloud API protected by OAuth 2.0 bearer tokens. Personal access tokens and service integrations are Samsung-account credentials for that API. [S16] A cloud command path can turn a registered television on or poll status only while Samsung's cloud can reach the television. That fails the local-first rule if it is the normal control path: AppT's backend or the public internet must not be required after pairing. SmartThings may remain relevant later as an optional enrichment or wake fallback. It is not a substitute for local control. Confidence: high for the cloud and account requirement. Hardware validation: not required to reject it as the primary path.

**Commercial display protocols are a different device class.** Samsung professional and hospitality displays have historically used documented serial/MDC and IP-control interfaces. Those documents were not treated as evidence about consumer Smart TVs. A community Frame-TV integration describes an additional local JSON-RPC "IP Control" channel on ports 1515/1516. [S17] That is community evidence about some consumer sets, not an official consumer specification. It is recorded as an optional later probe, not as a V1 assumption.

**No public protocol grant was found.** This research did not find a Samsung developer document that specifies, versions, or licenses third-party use of the local WebSocket remote channel described below. Samsung developer-forum threads show independent developers attempting that channel. The retrieved excerpts do not contain a vendor endorsement or a vendor protocol document. [S18] [S19] Anecdotal integrator comments that an NDA is required were not treated as proof. The narrower, supportable statement is: public documentation retrieved for this research does not provide a stable, licensed local-remote specification. `docs/PRODUCT.md` already assigns vendor-terms review to a pre-release legal gate. That unknown is identical for every phone stack. It is not an Expo-specific finding.

### 1.2 Community protocol families

The following is reverse-engineered knowledge. The most concrete current source is `xchwarze/samsung-tv-ws-api` at commit `e48d6377faede37db1f034d726a079b9d8034fac` (11 September 2026, version bump to 3.0.6). [S20] Home Assistant's Samsung TV integration depends on that library line (`samsungtvws[async,encrypted]==3.0.6` in the manifest retrieved from `home-assistant/core` commit `3bc9cf09eb8d2079635d7a294e0f9176bc420f93`, 22 September 2026) and classifies the integration as local push. [S21] [S22] Home Assistant's user documentation describes local REST plus a WebSocket channel for Tizen-era sets, and a legacy bridge for older sets. [S23]

These sources disagree on year labels. This report therefore uses protocol families, not a supported year range.

**Family W: Tizen WebSocket v2.** Community clients open:

- `ws://<tv>:8001/api/v2/channels/samsung.remote.control`
- `wss://<tv>:8002/api/v2/channels/samsung.remote.control`

The client name is a query parameter, base64-encoded. On the TLS port, a previously issued token is also a query parameter. After the socket opens, the television emits events including `ms.channel.connect` and `ms.channel.unauthorized`. A token, when present, is read from the connect event payload. Commands observed by the maintained library use method `ms.remote.control` with a remote type such as `SendRemoteKey` and a key name such as `KEY_VOLUP`. [S20] Home Assistant's constants name port 8002 as the SSL WebSocket port and 8001 as the non-SSL port, and try the SSL port first. [S21]

This family is the one current maintained integrations use for Tizen televisions. The library README says it is designed for Tizen sets from 2016 onward. [S20] That year boundary is the maintainer's claim, not Samsung's. Confidence that the channel exists on many Tizen sets: high, because it is implemented by a gold-tier Home Assistant integration and a library updated in September 2026. Confidence that every current Samsung Smart TV exposes it, or will keep exposing it after a firmware update: low. Hardware validation: required on the actual set AppT will treat as the V1 reference, and again after a firmware update.

**Family E: encrypted PIN / Orsay-era channel.** The same library has an encrypted extra. Its WebSocket helper defaults to port 8000 and a socket.io-style path. Its pairing helper defaults to HTTP port 8080 and describes an on-screen PIN flow. [S20] Home Assistant names an encrypted method and port 8000 separately from legacy port 55000. [S21] Older community write-ups sometimes collapse 2014–2015 sets onto port 8080 only. openHAB's Samsung binding documentation, last substantively describing this split in its retrieved page, says H and J series sets use a PIN and encryption and are not supported by that binding, while later sets use 8001 or 8002. [S24] These accounts conflict on support and on which port is the control port versus the pairing port. Confidence: medium that a distinct encrypted pre-Tizen family exists; low that any single port map covers it. Hardware validation: required before AppT promises control of a pre-Tizen set. Product does not require a year cutoff, but it also does not require V1 to succeed on every generation. Capability probing is the product rule.

**Family L: legacy TCP.** Home Assistant still names port 55000 as the legacy port. [S21] The historical `samsungctl` tool documents a TCP remote on that port. [S25] This is older community knowledge. It is not needed to decide whether React Native can speak a socket. It is needed to avoid assuming one WebSocket client covers every Samsung television a discovery pass might find.

**Family D: discovery advertisements.** Home Assistant discovers Samsung televisions by SSDP search targets including `urn:samsung.com:device:RemoteControlReceiver:1` and `urn:samsung.com:service:MainTVAgent2:1`, plus UPnP RenderingControl from Samsung, and by mDNS/Bonjour type `_airplay._tcp.local.` when the manufacturer property matches Samsung. [S21] SSDP is UDP multicast to 239.255.255.250 port 1900. That is the standard SSDP group, confirmed by the advertisements integrators match, not by a Samsung discovery specification retrieved here. AirPlay's `_airplay._tcp` service is a Bonjour type an app can declare. Whether every Samsung television advertises it, and whether the TXT records are stable enough to identify a previously paired set, is not official. Confidence: high that SSDP advertisements of those Samsung URN types are what a major integration searches for. Medium that AirPlay Bonjour is a useful partial signal. Hardware validation: required.

**Family R: local REST on the same ports.** The maintained library issues HTTP(S) against `/api/v2/` on port 8001 or 8002 for device info, and against `/api/v2/applications/<id>` to query, run, close, or install an application. A separate text path posts to `/api/v2/remoteControl/imeInput/<base64>?token=...` and the library requires a token for that call. [S20] Home Assistant's documentation says the integration uses a local REST API plus a WebSocket notification channel, and that installed-application exposure varies. [S23] Device-info field names (MAC, model, a stable id, token-auth support) are commonly consumed by these clients. This research did not retrieve an official schema. Confidence: medium. Hardware validation: required before those fields are used as a persistent identity.

### 1.3 Pairing, tokens, and identity

Community behavior, consistently enough to plan a prototype, not consistently enough to specify V1:

- First connection without a token raises an on-screen allow/deny (or, on the encrypted family, a PIN). The connect event can carry a token. Later connections send that token to avoid a repeated prompt. [S20] [S26]
- The maintained library appends the token only when it considers the connection to be the TLS port. That is library policy. It is not proof that port 8001 never uses a token. [S20]
- The library README says newer televisions ask for permission on every connection unless the television setting **Device Connection Manager → Access Notification → First Time Only** is selected, and that stale entries in the device list should be removed. [S20] That setting name is community knowledge of the television UI. If the default is "ask every time," quiet reconnection is impossible until the user changes a television setting. That is a product-reliability hazard independent of phone stack.
- Token lifetime is disputed. Some reports treat the token as stable. A commercial-control plugin documents token rotation on each connection and persists the latest value. [S27] The maintained library overwrites its stored token whenever a connect event includes one, which is compatible with rotation but does not prove rotation. [S20] If the token rotates, a client that logs or races an old token will look unpaired. Hardware validation: required.
- The token is a bearer secret in the WebSocket URL. The maintained library logs the new token at info level and logs the WebSocket URL. [S20] That is evidence of a logging hazard, not a pattern to copy. AppT's rule against secrets in logs applies to this URL on every stack.

**Persistent security identity.** Port 8002 is TLS to a certificate that community clients do not treat as publicly trusted. The maintained library disables certificate verification (`CERT_NONE` / `verify=False`). [S20] That is exactly the global verification bypass `docs/PRODUCT.md` forbids. It is cited only as evidence that the television certificate is not in the public web trust model.

A persistent identity is still technically plausible: after the user approves the television prompt, store the certificate's public-key hash (SPKI) with the token, and fail closed if a later handshake presents a different key. That is trust-on-first-use bound to the on-screen approval, not a disabled verifier. It can be implemented in a native TLS stack. It cannot be implemented with the stock React Native `WebSocket` constructor, which documents no certificate callback. [S28]

What documentation cannot settle:

- Whether the television certificate is stable across reboot, network change, and firmware update.
- Whether the REST device id or MAC is stable, and whether Wi-Fi and Ethernet MACs differ.
- Whether a television that only offers port 8001 can present any cryptographic identity. Cleartext cannot. The product rule is conditional: fail closed where an identity can be established. A cleartext-only set is a capability gap, not an excuse for a global bypass.

Confidence that TOFU pinning is the security shape that matches the product text: high as a design constraint. Confidence that real televisions keep a stable certificate: low. Hardware validation: required, and it can change how painful fail-closed re-pairing is.

### 1.4 Commands, applications, text, and touch

Community clients send key names, not the numeric codes from the on-TV developer page. Press, click, and release are distinct commands. The maintained library inserts a delay between keys; its CLI default cited in the commands document is 1.0 second, and the connection object defaults to a 1 second key-press delay. [S20] Home Assistant documents that supported keys vary by model, and lists power, volume, navigation, and media names. [S23] A Samsung forum report from December 2024 said `KEY_POWER` toggle worked while `KEY_POWERON` and `KEY_POWEROFF` did not. [S19] That is one user's report, not a specification.

Application launch is a different message (`ed.apps.launch` / REST `POST /api/v2/applications/<id>`). The installed-app list is explicitly commented as not available on all televisions. [S20] [S23] Curated shortcuts are therefore only as good as a successful list or a successful probe. The product already forbids dead buttons. The stack can hide unconfirmed apps. The unknown is whether the television answers the list call.

Text input has two community paths: a WebSocket `SendInputString` with base64 text, and a token-gated REST IME call. [S20] openHAB's binding also documents text entry and mouse events. [S24] Mouse/touch is a `ProcessMouseDevice` move command in the maintained library. [S20] None of this is official. A phone keyboard in React Native does not imply the television will accept a Unicode string. Non-Latin input, password fields, and composition are hardware-validation items.

Touchpad support is capability-driven in the product. A stack that can send the mouse command is not a stack that can assume every television has a pointer. Directional keys remain the accessible and broadly reported path.

### 1.5 Power, standby, and wake

Several different "off" states are conflated in community reports:

- The WebSocket is reachable and `KEY_POWER` toggles power. The television is not fully off.
- The WebSocket is unreachable. A network wake might still work.
- The television is electrically off or on a switched strip. No packet works.

Home Assistant's retrieved user documentation says that if discovery yielded a MAC, turn-on attempts Wake-on-LAN, and that Wake-on-LAN must be enabled on the television. [S23] The `dev` branch commit retrieved the same day is titled "Cleanup deprecated wake-on-lan for SamsungTV". [S22] A March 2026 Home Assistant issue describes implicit Wake-on-LAN as deprecated in favor of an explicit magic-packet automation. [S29] Read together: Home Assistant has been sending magic packets, and is moving that send out of the Samsung integration. That is not evidence that wake works, and not evidence that it does not.

The official Smart View SDK documents a `WakeOnWirelessLan(mac)` call, with the published matrix covering 2016–2017 and noting 2015 trouble. [S11] [S30] The SDK does not publish the packet format. Community tools send the ordinary Wake-on-LAN magic packet (six `0xFF` bytes plus sixteen repetitions of the MAC). Whether Samsung's WoW is that packet, a directed broadcast, or something else on current firmware is not settled by the SDK page.

Community restrictions conflict:

- The samsung-tizen adapter README says Wake-on-LAN works for a wired television and not for wireless, except short standby. [S31]
- openHAB's retrieved binding page says the opposite in one troubleshooting note: Wake-on-LAN did not work on wired sets with an ARC/eARC soundbar, and did work on Wi-Fi. [S24]
- Home Assistant documents a subnet/VLAN restriction on the WebSocket itself. The library README repeats it. [S20] [S23]

The product already allows an attempt and requires an explanation when wake fails. The architecture question is whether the phone stack can send the packet, not whether every Samsung television will wake. Packet send is a stack question. Wake success is a television question. Both need hardware validation, on both Wi-Fi and Ethernet if the reference set can use both.

### 1.6 What is stack-independent

Reliable Samsung control is limited first by an unofficial, firmware-sensitive protocol, not by React Native. Native Android and native iOS do not get a vendor SDK that the retrieved public documents show as a current full remote. They would implement the same community protocol, or wrap the old Smart View SDK, or depend on SmartThings cloud. Those choices have the same protocol risk on every variant.

The stack question is narrower: can the phone runtime discover the television, hold the socket, verify the certificate it chooses to trust, store the token, send a wake packet, and keep doing that through ordinary phone lifecycle events, without a verification bypass.

## 2. Local-network discovery in React Native / Expo

Discovery AppT requires, given the evidence above, is at least:

- SSDP multicast listen/search for the Samsung URNs Home Assistant matches, if that remains the strongest Samsung-specific signal.
- Optionally, a declared Bonjour browse of `_airplay._tcp` as a partial signal.
- A follow-up unicast HTTP(S) read of device info, then a WebSocket, on the same LAN.
- Rediscovery when the television's address changes, without asking the user to type an address.

| Need | Expo standard APIs | Development build / config plugin | Custom native module | Problematic side |
| --- | --- | --- | --- | --- |
| Phone IPv4 address | `expo-network` `getIpAddressAsync` on Android, iOS, and tvOS. Returns `0.0.0.0` if unavailable. No subnet mask, no SSID. Included in Expo Go. [S6] | Not required for this call. | Not required for the address alone. | iOS may return an unusable address on some interfaces. No official guarantee it is the Wi-Fi address on a multi-homed phone. |
| Connectivity changes | `expo-network` network-state listener. On iOS, `isInternetReachable` is documented as always equal to `isConnected`, so it cannot mean "the television is reachable." [S6] | Same. | `NWPathMonitor` / `ConnectivityManager` if finer path identity is required. | A Wi-Fi change does not identify the television's new address. Rediscovery is still required. |
| SSDP multicast | No Expo SDK UDP or multicast API was found in the SDK 57 network reference. [S6] | A config plugin can add permissions and the iOS entitlement key. It cannot receive packets. | Required. | iOS physical devices require the restricted multicast entitlement to send or receive broadcast/multicast. [S32] [S33] Android receive path needs a multicast lock. [S34] |
| Bonjour browse of one declared service | Not in Expo Network. | Config plugin can set `NSBonjourServices` and `NSLocalNetworkUsageDescription`. | A native browser, or a maintained Bonjour library in a dev build. | Declared-service browse is the case Apple tells developers to prefer, and it does not by itself require the multicast entitlement. [S33] [S35] Arbitrary service enumeration does. |
| Unicast TCP probe of port 8001/8002 | `fetch` can attempt it once an address is known. Cleartext local HTTP may need an ATS / cleartext exception. [S28] | Config plugin can set `NSAllowsLocalNetworking` and an Android cleartext policy. | Required if certificate checks or dynamic per-television policy must be enforced. | Scanning a subnet from JavaScript is possible only after the phone IP is known, and it is a poor substitute for discovery on non-/24 and IPv6-only networks. |
| SSDP in Expo Go | Unavailable. Expo Go cannot add the multicast entitlement or an arbitrary native socket module. [S2] | Development build. | Yes. | — |

**iOS permission versus entitlement.** These are different gates.

- Local-network privacy permission is an ordinary user prompt. Apple's documentation says any app that uses the local network, directly or indirectly, including Bonjour and direct unicast or multicast to local hosts, should include `NSLocalNetworkUsageDescription`. [S36] Outgoing TCP and UDP to local addresses trigger it. [S37] The user can deny or later revoke it. There is no supported API to force the prompt again. This applies to native and React Native equally. It is ordinary configuration plus a user decision, not a restricted entitlement.
- The multicast entitlement `com.apple.developer.networking.multicast` is restricted. Apple's entitlement reference says the app must have it to send or receive IP multicast or broadcast on iOS, and that Apple's permission is required before use. [S32] Apple's 22 June 2020 developer note says the simulator does not require the entitlement and a physical device does. [S33] Apple's local-network FAQ says sending a UDP broadcast requires the entitlement, with incomplete enforcement on iOS 14 and 15 and expected enforcement from iOS 16. [S35] A config plugin can write the entitlement file. The provisioning profile will not contain it until Apple grants it to the team. That is a schedule and approval risk, not an Expo bug. Native iOS has the same gate.

Bonjour browse of `_airplay._tcp`, if declared in `NSBonjourServices`, is the discovery path most likely to avoid the restricted entitlement. It is not evidence that it finds the televisions AppT must find. SSDP remains the Samsung-specific signal in the Home Assistant manifest. [S21] A V1 that depends only on AirPlay advertisements would miss sets that do not advertise AirPlay. That is a hardware-validation question, and it is material to whether iOS discovery requires the entitlement.

**Android.** Sending and receiving local SSDP is not an Expo API. `CHANGE_WIFI_MULTICAST_STATE` is a normal permission. `WifiManager.MulticastLock` exists so the Wi-Fi stack delivers multicast not addressed to the phone; the lock should be held only during discovery, because it costs power. [S34] Connecting to a discovered address is ordinary local IP traffic. From Android 17, apps targeting API 37 must hold the dangerous runtime permission `ACCESS_LOCAL_NETWORK` for local TCP and UDP, including SSDP and mDNS, unless they use a system picker. Android's local-network permission guide says home-automation and IoT management that need broad persistent access should request that permission, and that the system picker is the alternative when a single user-selected device is enough. [S38] The picker does not match AppT's automatic multi-television discovery. As of 31 August 2026, Google Play requires new apps and updates to target API 36, not 37. [S39] So a V1 targeting API 36 is not yet forced to ship the Android 17 prompt. The permission is documented future enforcement, and discovery code written now should be ready for it. Confidence: high on the documented rule. Hardware validation: required on an Android 17 device before relying on target-37 behavior, not required to know the rule exists.

**Expo Go is not the production ceiling.** Variant 1 cannot do Samsung-specific discovery. Variant 2 can, through a native module, subject to the iOS entitlement grant. Variant 3 can, through the same kind of module. Variant 4 can, subject to the same entitlement. Variant 5 avoids the iOS entitlement if Android ships alone. It does not avoid Android multicast-lock and future local-network permission work.

No current, New-Architecture-ready SSDP library with credible maintenance was found. `react-native-udp` was last pushed on 5 March 2023. [S40] React Native SSDP packages found in search are forks of that stack and show years-old publish dates. Depending on them for a 2026 new-architecture app is an abandonment risk, not a shortcut. See section 10.

## 3. Persistent connections and React Native suitability

### 3.1 What the JavaScript WebSocket can and cannot do

React Native documents a browser-style `WebSocket`. The documented constructor takes a URL. There is no documented option for a custom trust evaluation, a pinned public key, or a per-connection certificate exception. [S28] Fetch and XHR are likewise documented without a television-identity callback. iOS App Transport Security applies to the URL loading stack React Native describes; cleartext requires an exception. Android blocks cleartext by default from API 28, overridable in the manifest. [S28]

That is enough to reject variant 1 for the secure control path:

- `wss://` to a self-signed television certificate fails normal trust evaluation.
- Making it succeed by disabling verification globally conflicts with the product.
- Pinning the key observed at pairing requires a TLS callback the documented JavaScript client does not expose.
- `ws://` to port 8001 may work after a local-cleartext exception, and then provides no cryptographic identity.

Older React Native issues and an Apple Developer Forums thread describe the same gap for self-signed WebSockets, including that the iOS implementation's trust handling is not a JavaScript option. [S41] Those are supporting, older reports. The current documentation is the stronger proof that the public API still has no pinning hook. Confidence: high. Hardware validation: still required to confirm that a native client, not the stock client, can complete the Samsung handshake on a current television.

`react-native-tcp-socket` documents TLS and a `ca` option, but the documented client example loads the CA with `require()`, as a bundled file. [S42] A television certificate is not known at bundle time. That library is therefore not, on its documented API, a trust-on-first-use pin for an arbitrary local television. It also speaks TCP/TLS, not WebSocket. Building WebSocket framing on top of it is possible and is more native-module-shaped work than using `URLSession` or OkHttp's WebSocket with a custom trust manager. Confidence: high that the documented `ca: require(...)` shape does not solve runtime pinning. A runtime string API, if one exists beyond the README example, was not verified.

Community packages whose purpose is to accept any self-signed certificate were found and are not candidates. They implement the bypass the product forbids. They are omitted except as evidence that the ecosystem's usual answer is the forbidden one.

### 3.2 Lifecycle, not API existence

The reliability hazards are:

- **Foreground command path.** A warm socket and a small JSON send are not bridge-bound in any way that should miss a physical remote's feel. Expo's own module guidance says native method-call overhead is rarely the bottleneck versus the work itself. [S9] The dominant delays reported by community clients are television-side: dropped commands if keys are sent too quickly, and multi-hundred-millisecond spacing in client defaults. [S20] That delay is stack-independent. It may still make the remote feel worse than the physical remote. Hardware validation: measure click-to-on-screen latency with a warm connection, and the minimum spacing that does not drop keys.
- **JavaScript suspension.** React Native `AppState` reports `active`, `background`, and on iOS `inactive` during transitions, incoming calls, and Notification Center. Android also has `blur` when the notification shade opens without a full background transition. [S43] Timers and JS reconnect logic do not run while iOS has suspended the process. Apple DTS has repeatedly stated that a suspended app's network connections can be closed, that a `UIApplication` background task only delays suspension briefly, and that indefinite background networking is not available unless the app is performing a user-visible background mode for its intended purpose. [S44] [S45]
- **The product does not require an always-on background remote.** It requires quiet reconnection after drops, and volume-button behavior while the remote is active. The realistic reading is: the control session is a foreground session; lock, call, and app-switch are interruptions followed by reconnect. A stack that cannot keep a socket open in the background can still meet that reading. A stack that reconnects slowly, or that re-prompts pairing on every resume, cannot.
- **iOS cannot be kept connected through suspension by moving the socket to native code.** Native code in a suspended process does not run. A native module helps for certificate handling, for not losing a token update that arrives as the app backgrounds, and for reconnecting quickly on `active`. It does not defeat process suspension. Full native iOS has the same limit. [S44]
- **Android Doze and background restriction** matter if the app tries to hold the socket after the user leaves it. A foreground activity using the network is not the Doze case. Doze is documented for idle devices and background work. [S46] For a foreground remote, Android lifecycle risk is lower than iOS, but OEM task killers and Wi-Fi power save still drop sockets. Hardware validation: required on at least one non-Pixel device.
- **Wi-Fi changes.** `expo-network` can report that the interface changed. It cannot report the television's new address. Rediscovery is an application behavior on top of working discovery. All variants that can discover can implement this. Variants that cannot discover cannot.
- **Calls and notifications.** iOS `inactive` and Android `blur` are visible to JavaScript. [S43] The session should treat them as "do not send keys," then reconnect if the socket died. That logic can live in JavaScript if the socket implementation reports close reliably. It should not depend on JS timers firing during the interruption.

**Where native code is actually justified.** Certificate identity, UDP discovery, and wake packets need native code because the JavaScript APIs do not expose them. The reconnect state machine can live in JavaScript for a foreground remote if the native socket delivers open, close, token, and identity-mismatch events onto the JS thread when the app is active. Putting the entire state machine in native code is a reliability choice, not a demonstrated requirement. The hazard to test is a token that rotates in the same moment the app is suspended. If that race is real on hardware, the native module should persist the new token before returning to JavaScript. That is a small native responsibility, not a reason to abandon React Native.

Confidence: high on the API and OS limits. Medium on whether JS-owned reconnect is reliable enough once the socket is native. Hardware validation: required.

## 4. Security against the approved requirements

| Requirement | Variant 1: Expo standard | Variant 2: Expo + native module | Variant 3: bare React Native | Variant 4: native apps |
| --- | --- | --- | --- | --- |
| Store pairing token in Keychain / Keystore-backed storage | `expo-secure-store` does this. Included in Expo Go. [S3] | Same, or the native module can write Keychain/Keystore directly. | `react-native-keychain`, or Expo modules installed into the bare app. [S47] | Direct. Equivalent protection, two implementations. |
| Exclude secrets from logs | Not solved by the storage API. Any client that puts the token in a URL must not log that URL. Expo's dev-client network inspector is a development leak if it records the control socket. | A native module can keep the token out of JS logs if the URL is built on the native side. JS must still not receive it for logging. | Same discipline. | Same discipline. |
| No global certificate bypass | Stock WebSocket cannot both connect to the usual self-signed television certificate and verify it. | Practical: pin the paired SPKI; reject mismatches; do not install a global trust-all manager. | Same native work. | Practical. |
| Fail closed on identity change | Cannot see the certificate. | Native TLS callback can. | Same. | Same. |
| Quiet reconnect without a biometric prompt | SecureStore `requireAuthentication` prompts on read on iOS and on all operations on Android. That fights quiet reconnect. Do not enable it for the token. [S3] | Same. | Same. | Same if biometric is bound to every read. |

**SecureStore limits that matter, and ones that do not.**

- Android values are SharedPreferences encrypted with the Android Keystore. iOS values are Keychain generic passwords. [S3] That matches the product's "platform-appropriate secure storage" for a short token and a short public-key hash.
- Expo documents that some iOS releases historically refused values above roughly 2048 bytes, and that Expo does not enforce a limit. [S3] Tokens observed in community examples are short. An SPKI hash is 32 bytes. A full PEM certificate might approach or exceed historical Keychain item limits. Store the hash, not the PEM, unless hardware shows a reason to store more.
- iOS Keychain items can survive uninstall and reinstall of the same bundle id. Android SecureStore data does not survive uninstall. Expo documents both, and says the iOS behavior must not be relied on. [S3] A reinstall might still hold a token the television also remembers, or a token the television has forgotten. Either outcome is a re-pair case, not a cross-phone sync case. The product already forbids syncing pairing secrets.
- Android Auto Backup cannot decrypt SecureStore data after reinstall because Keystore keys are deleted. Expo's config plugin excludes SecureStore from backup by default when the app has no custom backup rules. [S3] A custom backup config must keep that exclusion. This is a config-plugin item, not a reason to avoid SecureStore.
- `WHEN_UNLOCKED` is the appropriate accessibility if the remote is a foreground, unlocked-phone activity. `AFTER_FIRST_UNLOCK` would matter only if a background native module had to read the token before first unlock. The product does not require that.
- SecureStore does not log values. Application code, crash reporters, and URL logs do. The token-in-query-string design means crash reporting must redact request URLs. That is true in native code too.

**Pinning practicality.** Public-key pinning libraries aimed at a known hostname and a build-time pin list do not match a television whose address changes and whose certificate is learned at pairing. The practical control is a native trust callback:

1. First approved connection: record SPKI hash and token. The on-screen approval is the authentication.
2. Later connection: continue the handshake only if the presented key matches the stored hash.
3. Mismatch: close, do not send commands, require explicit re-pair.
4. Cleartext-only television: do not pretend a TLS identity exists. Record that the set has no cryptographic identity. Do not disable verification for televisions that do.

This is not a global bypass. It is also not "verification" in the public-CA sense. The product text allows a persistent identity where one can be established, and forbids a global ignore-errors mode. The TOFU design is the one that fits. Its weakness is certificate rotation. If televisions rotate certificates often, fail-closed becomes frequent re-pairing and hurts the reliability priority. That conflict cannot be resolved from documentation.

Custom native modules do not weaken Keychain or Keystore if they use those APIs. They weaken security if they copy the community client's `CERT_NONE` setting, log the tokenized URL, or export a JavaScript "ignore TLS" flag. The module boundary should not expose that flag.

Confidence: high that SecureStore meets the storage requirement for short secrets, and that pinning requires native TLS. Low on certificate stability. Hardware validation: required for certificate lifetime and for confirming the native callback can complete Samsung's handshake.

## 5. Wake-on-LAN and other wake sends

The packet AppT may need to send is a UDP broadcast or subnet-directed broadcast, typically to port 9 or 7, containing the magic packet. That is the packet community integrations send. It may or may not be what Samsung's older `WakeOnWirelessLan` SDK call sends. [S11] [S23]

| Question | Finding |
| --- | --- |
| Expo standard API | No UDP API in `expo-network`. Variant 1 cannot send the packet. [S6] |
| Expo development build | A native module can send it. A config plugin must declare iOS local-network usage and, for broadcast, the multicast entitlement. Android needs `INTERNET` and, when targeting API 37, `ACCESS_LOCAL_NETWORK`. [S32] [S38] |
| Android multicast lock | Needed to receive multicast, not obviously needed to send one broadcast. Hold it for discovery, not for the life of the remote. [S34] |
| iOS broadcast | Physical device requires the multicast entitlement. Simulator exemption does not help, because the simulator cannot wake the television. [S33] |
| Wi-Fi limitations | Client isolation, guest networks, and AP broadcast filtering drop the packet on every stack. iOS Low Data Mode and VPNs are additional reasons a send can fail without an error the television will confirm. Wake-on-LAN has no acknowledgement. |
| Doze | Irrelevant for a send from the foreground. Relevant only if wake is attempted from a background task. |
| Reliability | Stack can deliver the packet and still fail the user-visible test. Product already requires explaining that failure. |

Variant 5 does not remove wake uncertainty. It removes the iOS entitlement from the wake path. Android can send the broadcast with less process friction. Whether the television wakes is still unproven.

Confidence: high that variant 1 cannot send the packet, and that variants 2–4 can send it if the iOS entitlement is granted. Low that a magic packet wakes a current Samsung television on Wi-Fi. Hardware validation: required.

## 6. Phone-native V1 experience

| Requirement | Feasibility | Stack note |
| --- | --- | --- |
| Subtle haptics, user-disableable | Supported by `expo-haptics` on Android and iOS, included in Expo Go. iOS does nothing in Low Power Mode, if the user disabled the Taptic Engine, or during camera/dictation. Android `VIBRATE` is added by the library. [S4] | Not a differentiator. A user setting to disable haptics is application code. Respecting iOS Low Power Mode is already platform behavior. |
| Volume buttons control TV volume while the remote is active, and can be disabled | **Android: feasible in native code while the activity is focused. iOS: no public API found that consumes the buttons without changing system volume, and App Store guideline 2.5.9 forbids altering volume-switch function.** | See below. This is the sharpest phone-native platform split. |
| Screen reader | React Native `accessibilityLabel`, `accessibilityRole`, `accessibilityHint`, and `accessibilityState` are the supported path. A touchpad surface needs accessibility actions or, as the product already requires, a directional-button alternative. | Equivalent in Expo and bare React Native. Native apps can do it and must do it twice. |
| Dynamic / scalable text | `Text` and `TextInput` `allowFontScaling` defaults to true. `maxFontSizeMultiplier` exists. [S48] | Layout must be tested at large sizes. That is a UI test, not a stack blocker. |
| Large touch targets, contrast, labels, one-handed layout | Application layout. React Native can do it. No platform API gap found. | One-handed reach is design. It does not favor native over React Native. |
| Phone keyboard for TV text | `TextInput` raises the system keyboard, including password and locale keyboards. Delivering the string to the television is the community text command, not the keyboard. [S20] [S48] | Keyboard UI is a point in favor of one shared React Native UI. The send path belongs with the control module. |
| Keep screen awake while the remote is in use | `expo-keep-awake` prevents idle sleep on Android and iOS while activated. Included in Expo Go. [S5] | It does not stop the user locking the phone, an incoming call, or iOS suspension after backgrounding. Activate only on the remote screen. |
| Calls, notifications, app switch, lock | Observable through `AppState`. Reconnect on return is the product behavior. [S43] | See section 3. Not a reason to leave React Native if reconnect is fast and does not re-prompt. |

### Volume buttons

**Android.** `KeyEvent` defines `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN` as public key codes. [S49] A focused foreground activity can receive them. `react-native-volume-manager`, maintained as of a 4 September 2026 push, documents that with the native volume UI suppressed it intercepts hardware volume keys while the app is foregrounded, and that Expo Go is not supported. [S50] That matches "while the remote is active." When the activity is not focused, the keys should remain phone volume. The library also supports reading and setting system volume, which is a different behavior from consuming the key. OEM skins can swallow keys before the activity. Hardware validation: required on at least one Samsung, one Pixel, and one other OEM phone if Android volume is a launch promise.

This is not available in Expo standard APIs. It is a small native integration, either the maintained library or a few lines in the activity dispatched through an Expo module. It does not require a fully native app.

**iOS.** Apple's current App Store Review Guidelines, as quoted from the official guidelines page in search retrieval on 2026-09-22, say at **2.5.9** that apps that alter or disable the functions of standard switches, such as the Volume Up/Down and Ring/Silent switches, will be rejected. A guidelines PDF with the same 2.5.9 sentence is dated 6 February 2026. [S51] [S52] Apple staff, in an older developer-forum answer, said system output volume is a user preference and there is no API for it; `MPVolumeView` is the user-facing volume control. [S53]

`react-native-volume-manager` can observe volume changes, set system volume, and hide the system volume HUD on a physical device. Its README describes control and observation of system volume. It does not document a supported way to make the buttons stop changing system volume and control a television instead. [S50] Techniques that play silent audio or reset system volume after observing a press are attempts to alter the volume switch. This report does not describe those techniques. They are the behavior guideline 2.5.9 names.

Consequence, without making the product decision: the volume-button requirement as written is a public-API fit on Android and an App Store risk on iPhone, on React Native and on fully native iOS alike. Native iOS does not remove 2.5.9. Android-first removes the conflict from V1 only by not shipping the iPhone client yet. An iPhone V1 can still ship the rest of the remote if the product later accepts that iOS volume buttons are not captured. That acceptance would be a product change. This research does not make it.

Confidence: high on the guideline text and on the absence of a documented public consume API. Medium that App Review would reject a specific implementation, because review outcomes are not fully determined by one sentence. Hardware validation: can show what a public API does on device. It cannot grant an App Store exception.

## 7. iOS-specific constraints

| Item | Class | Effect on AppT |
| --- | --- | --- |
| `NSLocalNetworkUsageDescription` | Ordinary Info.plist string. Required for local unicast, Bonjour, and multicast. [S36] | Config plugin. User can deny. Explain in ordinary language before the first local send, matching the product. The system, not the app, shows the prompt and decides timing. |
| `NSBonjourServices` | Ordinary Info.plist array of service types. Required if Bonjour is used. [S33] | Needed if `_airplay._tcp` or any other declared browse is used. Not needed for raw SSDP, which instead needs the entitlement. |
| Multicast entitlement | Restricted. Apple approval, then provisioning profile, then a physical device. Simulator is exempt and useless for a real television. [S32] [S33] | Material schedule risk for SSDP and for UDP broadcast wake. Not caused by Expo. A development build is how an Expo app carries the entitlement; Expo Go cannot. |
| App Store 2.5.9 | Review rule. [S51] | Material product risk for captured volume buttons. |
| Background modes | Must be used for their intended purpose. Apple DTS quotes guideline 2.5.4 to that effect and says suspension closes connections. [S44] [S45] | Do not use audio, VoIP, or location modes to keep a remote socket alive. Brief `beginBackgroundTask` can cover an in-flight token save. It is not a persistent remote. |
| ATS / `NSAllowsLocalNetworking` | Ordinary ATS exception for local networking, narrower than disabling ATS globally. Archived Apple ATS documentation says local networking can be allowed without disabling ATS for the rest of the app. A 2024 Apple DTS post says ATS applies to URLSession, not to Network framework or BSD sockets, and discusses IP-literal HTTP restrictions. [S54] [S55] | Cleartext device-info fetches may need this exception if they use React Native `fetch`. Identity-verified `wss` should use a native socket, where ATS is not the control point. Do not set `NSAllowsArbitraryLoads` as a substitute for pinning. |
| Wi-Fi information entitlement | Restricted capability historically required to read SSID. | AppT should not need the SSID. The product forbids logging Wi-Fi names. `getifaddrs`-style local IP via `expo-network` is the documented Expo path and does not require that entitlement. [S6] |
| Local-network permission revocation | User setting. No in-app re-prompt API found in the retrieved docs. | Same on native iOS. The UI must explain how to re-enable it in Settings. |
| Keychain survival across reinstall | Platform behavior, documented by Expo. [S3] | Do not treat uninstall as a secure wipe of the token. |

None of the restricted items are created by choosing Expo. They are created by SSDP, broadcast wake, and the volume requirement. Unicast control of an already known address needs the local-network usage string and a native TLS socket, not the multicast entitlement. That split is the most important iOS prototype cut: control can be tested before the entitlement is granted; discovery and broadcast wake cannot, on a physical device.

## 8. Android-specific constraints

| Item | Current rule | AppT effect |
| --- | --- | --- |
| `INTERNET` | Normal permission. | Required. Not a user prompt. |
| Cleartext | Blocked by default for app targets of API 28+, as React Native documents. [S28] | A global `usesCleartextTraffic=true` is broader than AppT needs. Prefer TLS. A cleartext-only television needs an explicit per-connection choice in native code, not an app-wide bypass. |
| `CHANGE_WIFI_MULTICAST_STATE` and `MulticastLock` | Normal permission. Lock causes the Wi-Fi stack to deliver multicast. Released on process death. [S34] | Hold during SSDP discovery only. |
| `ACCESS_LOCAL_NETWORK` | Dangerous runtime permission added in API 37. Enforced for target SDK 37. Local TCP and UDP, including SSDP, require it. Android 16 used an opt-in and a temporary `NEARBY_WIFI_DEVICES` mapping. [S38] | Not forced by the 31 August 2026 Play target of API 36. [S39] Plan the prompt and the denial path now. The system device picker is not a fit for automatic multi-TV discovery. |
| `NEARBY_WIFI_DEVICES` | Dangerous, API 33, for Wi-Fi advertise/connect and the Android 16 local-network opt-in. [S38] | Do not confuse it with SSDP on current target-36 behavior. Wi-Fi scanning is a different, location-sensitive API and is not required for SSDP. |
| Foreground service / Doze | Doze restricts idle background work. [S46] | A foreground remote does not need a foreground service to send keys. Adding one to hold a socket in the background would need a user-visible reason and still would not match iOS. |
| Volume keys | Public `KeyEvent` codes. Interception while focused is what the maintained volume library implements. [S49] [S50] | Feasible. Validate on OEM skins. |
| Secure storage | Keystore-backed encryption via SecureStore. Backup exclusion is a plugin default. [S3] | Fits the product if the backup exclusion is preserved. |
| Wi-Fi changes | `ConnectivityManager` callbacks, surfaced coarsely by Expo Network. | Rediscover the television. Same as iOS. |
| Play target SDK | New apps and updates must target API 36 as of 31 August 2026. [S39] | A new Expo SDK 57 app must be checked for its default `targetSdkVersion` at project creation. This research did not retrieve the SDK 57 template's default. `expo-build-properties` can set it. [S56] |

Android is the less constrained phone for this product: local broadcast is not behind a vendor approval queue, and volume keys have a public foreground path. That is the factual content of the Android-first fallback. It is not a recommendation.

## 9. Where Expo stops being an advantage

Expo remains an advantage for the phone UI and the surrounding product even if the control plane is native:

- One accessibility, keyboard, haptic, and layout implementation.
- SecureStore, KeepAwake, and Haptics without writing those modules.
- Config plugins for usage strings, backup exclusion, and entitlement placeholders.
- EAS or local development builds, and JavaScript updates that do not touch the native control module.
- Expo Modules as the native boundary, with New Architecture support documented by Expo. [S9]

Expo stops being sufficient, and a bounded native module starts, at:

- SSDP and any other multicast or broadcast.
- Wake-on-LAN send.
- TLS identity for a self-signed television certificate.
- Android volume-key consumption.
- Any television protocol detail that must not appear in JavaScript logs.

Expo Go stops being relevant as soon as the first of those is required. That is early. It should not be mistaken for Expo being unable to ship the app.

Bare React Native does not remove that native module. It removes Continuous Native Generation unless the project also uses prebuild. Expo documents that prebuild is optional and that Expo modules can be installed in an existing React Native app. [S7] [S9] Variant 3 and variant 2 therefore converge once custom native code is accepted. The remaining difference is who generates and upgrades the native projects, and whether first-party Expo libraries are used. That is a maintenance difference, not a Samsung-control capability difference.

Full native development does not gain a Samsung SDK the public documents show as a current remote. It pays for two UI codebases to reach the same protocol uncertainty and the same iOS entitlement and review rules.

## 10. Dependency risk

Selection below is by fit and maintenance evidence, not download counts. Versions and New Architecture support still need a lockfile check at implementation time. This research did not run `npm view`.

| Candidate | Purpose | Evidence reviewed | Risk |
| --- | --- | --- | --- |
| `expo-secure-store` ~57.0.4 | Keychain / Keystore-backed secrets. Android, iOS, tvOS. In Expo Go except biometric prompt. [S3] | First-party, current SDK. | Low. Do not enable `requireAuthentication` for the reconnect token. Keep backup exclusion. |
| `expo-haptics` ~57.0.3 | Haptics. [S4] | First-party. | Low. |
| `expo-keep-awake` ~57.0.2 | Idle sleep. [S5] | First-party. In Expo Go. | Low. |
| `expo-network` ~57.0.2 | IPv4 and coarse connectivity. [S6] | First-party. No sockets. | Low for what it claims. Insufficient for discovery. |
| `expo-dev-client` | Development builds. [S2] | First-party. | Low. Network inspector must not become a production token log. |
| Expo Modules API | The custom control/discovery/wake module. [S9] | First-party. New Architecture supported. | Low as a bridge. The Samsung code inside it is the project's own maintenance. |
| `react-native-volume-manager` | Android volume-key interception while foregrounded; iOS volume observation; native UI suppression. Expo dev build required. RN 0.85+ recommended. New Architecture is the supported path. Pushed 4 September 2026. MIT. [S50] | Maintainer README and GitHub metadata. | Medium. Active, but a single-maintainer native module on a sensitive iOS surface. Android use is plausible. iOS use does not clear guideline 2.5.9. |
| `react-native-keychain` | Alternative secure storage. Pushed 29 April 2026. MIT. 3477 stars. [S47] | GitHub metadata. API not re-audited here. | Medium-low if SecureStore is missing a flag. Prefer SecureStore first. Confirm iCloud sync stays off. |
| `@react-native-community/netinfo` | Richer path updates. Pushed 15 February 2026. MIT. [S57] | GitHub metadata. | Medium-low. Overlaps Expo Network. Not a discovery library. |
| `react-native-tcp-socket` | TCP/TLS sockets. Pushed 10 September 2026. MIT. Documents TLS `ca` as a bundled import. [S42] | README plus GitHub metadata. | Medium for generic TCP. Poor fit for runtime television pinning and for WebSocket framing. Not a reason to avoid a small purpose-built module. |
| `react-native-udp` | UDP. Last push 5 March 2023. [S40] | GitHub metadata. | High abandonment risk on mandatory New Architecture. Do not base discovery or wake on it without a current fork audit. None was established here. |
| `react-native-zeroconf` | mDNS browse. Pushed 30 December 2025. MIT. [S58] | GitHub metadata. New Architecture status not verified. | Medium. Useful only if the Bonjour path is chosen. Does not implement SSDP. |
| `samsungtvws` and Node `samsung-tv-control` packages | Protocol evidence. Python or Node, not React Native. `samsungtvws` is LGPL-3.0. [S20] | Source retrieved. | Do not depend on them inside the app. Porting LGPL code is a license decision this research does not make. Their TLS verification disablement is not reusable. |
| React Native SSDP packages found via search | SSDP. | Publish dates in search results are 2017–2019, built on `react-native-udp`. [S59] | High. Treat as abandoned. |

The dependency conclusion is not "avoid native modules." It is that the Samsung-specific I/O should be a small project-owned module rather than an unmaintained SSDP package or a TLS-bypass WebSocket package. First-party Expo libraries cover the phone-chrome requirements.

## 11. Native implementation baseline

This section distinguishes "React Native cannot do this" from "React Native can do this through a bounded native module."

**Cannot, on documented APIs, in JavaScript alone:**

- Receive SSDP multicast.
- Send a Wake-on-LAN broadcast on a physical iPhone without the entitlement, and not at all without a UDP socket.
- Evaluate a television TLS certificate and pin it.
- Consume Android volume keys.

**Can, through one bounded Expo module (Swift and Kotlin), without a second UI codebase:**

- **Discovery.** Kotlin: `MulticastLock`, a UDP socket joined to 239.255.255.250:1900, SSDP M-SEARCH, parse of the Samsung URNs, then release the lock. Swift: `NWConnectionGroup` / `NWMulticastGroup` for SSDP if the entitlement is present; `NWBrowser` for a declared Bonjour type if that path is used. Both: emit a candidate address to JavaScript. No command is sent from the discovery socket.
- **Control socket.** Kotlin: OkHttp WebSocket or the platform WebSocket with a trust manager that compares SPKI to a value passed in from secure storage. Swift: `URLSession` WebSocket, or Network framework, with a session authentication-challenge delegate that performs the same comparison. The module returns connection state, unauthorized, token-rotated, and identity-mismatch. It does not return the raw certificate to logs. JavaScript owns the remote layout and decides which key name to send.
- **Wake.** One UDP send of a caller-supplied datagram to a caller-supplied broadcast address. No retry policy hidden in the module until hardware shows what retry is useful.
- **Android volume.** Forward `KEYCODE_VOLUME_UP` / `DOWN` to JavaScript only while the remote screen has requested capture, and consume them so the system volume does not move. Release capture when the screen blurs or the user disables the setting.
- **Config plugin.** `NSLocalNetworkUsageDescription`, `NSBonjourServices` if used, multicast entitlement placeholder, Android permissions, SecureStore backup exclusion, local ATS exception only if a cleartext probe is still required.

That module is on the order of a focused networking component, not an application. Expo documents this as the intended use of the Modules API when no library exists. [S9] Turbo Modules are the alternative if the project is bare React Native and does not want the `expo` package. Expo's stated guidance, paraphrasing the React Native team, is to prefer Turbo Modules when the module is C++, and the Expo Modules API when developer experience matters and the `expo` dependency is acceptable. [S9] AppT's module would be Swift and Kotlin, not a large C++ core. Either bridge is adequate. JSI performance is not the constraint.

**Still cannot, even with that module:**

- Keep the iOS process running as a background remote without misusing a background mode.
- Make iOS volume buttons control the television without altering system volume behavior, inside the public SDK and guideline 2.5.9.
- Force Apple to grant the multicast entitlement, or force a user to accept the local-network prompt.
- Make an undocumented television protocol stable.

**Full native baseline.** The same Swift and Kotlin networking code would be written. The difference is that screens, accessibility, keyboard, haptics, account UI, and navigation would also be written twice. No retrieved source shows a native-only Samsung control API that a React Native module cannot call. Confidence: high. Hardware validation: the module still needs a television. The boundary claim does not.

## 12. Architecture comparison

Cells are factual, not scores. "Uncertain" means hardware or vendor validation is still required.

| Area | Expo standard APIs | Expo + native modules | Bare React Native | Native Android / iOS |
| --- | --- | --- | --- | --- |
| Samsung discovery | Not supported for SSDP or broadcast. Phone IP and coarse connectivity only. Unicast probe only if an address is already known. Fails automatic setup. | Requires a native module and config plugin. iOS multicast entitlement is an Apple approval gate if SSDP or broadcast is required. Bonjour-only discovery is uncertain. | Same native module. No Expo Go limit, and no less native code. | Supported directly. Same iOS entitlement and Android permission gates. |
| Samsung pairing / control | Stock WebSocket may open cleartext if exceptions allow. Cannot meet the identity rule on the TLS port. Protocol itself is unofficial and uncertain on every stack. | Requires a native socket for a dependable TLS session. Protocol success still requires hardware validation. | Same. | Same protocol implementation per platform. No additional official API found. |
| Secure WebSockets / TLS | Public API has no trust callback. A global bypass would violate the product and is not a solution. | Practical in the native module: pin paired SPKI, fail closed on change. Certificate stability is uncertain. | Same native requirement. | Practical. Same uncertainty about the television certificate. |
| Credential storage | Supported directly by SecureStore (Keychain / Keystore). Suitable for short tokens and hashes. Biometric-on-read fights quiet reconnect. | Same SecureStore, or native secure storage if a flag is missing. | Supported via Expo modules or `react-native-keychain`. More assembly if Expo is excluded entirely. | Supported directly. Duplicated. iOS Keychain uninstall behavior is the same. |
| Wake-on-LAN | Not supported. No UDP API. | Native UDP send. iOS broadcast needs the multicast entitlement. Whether the television wakes is uncertain. | Same. | Same packet and same television limits. |
| App lifecycle reliability | JS can observe foreground/background. Cannot keep a socket across iOS suspension. Reconnect-on-foreground is possible only if the socket can be reopened, which the TLS case cannot do correctly in JS. | Foreground remote plus quiet reconnect is realistic. Native code does not defeat iOS suspension. Token-rotation-during-suspend is uncertain. | Same OS limits. | Same OS limits. More direct hooks, no demonstrated advantage once a native module exists. |
| Volume-button handling | Not supported. | Android: native integration, foreground only. iOS: public consume-and-redirect path not found; guideline 2.5.9 applies on every stack. | Same. | Same platform split. Native iOS does not remove 2.5.9. |
| Keyboard / text input | System keyboard supported. Television text command is a separate, uncertain protocol. | Keyboard in JS; send path in the control module. | Same. | Keyboard implemented twice; same uncertain send path. |
| Accessibility | Supported directly if labels, roles, and font scaling are used. Touchpad still needs the button alternative the product already requires. | Same. A native gesture surface must expose accessibility actions. | Same. | Supported, duplicated, easier to drift between platforms. |
| Cross-platform reuse | High for UI, insufficient for control. | High for UI, account sync, and remote layout. Control I/O remains per phone platform behind one JS boundary. | Similar if the JS UI is kept. | Low, unless a shared non-UI core is added, which recreates the module boundary. |
| Native complexity | Lowest, and not adequate for discovery, TLS identity, wake, or Android volume. | Bounded: one Swift/Kotlin control module plus config plugins. UI stays shared. | Similar native surface, plus native-project upkeep unless prebuild is adopted. | Highest: two applications, same protocol risk. |
| Long-term multi-TV extensibility | Poor. Each new local protocol will need sockets this variant does not have. | Plausible: per-ecosystem native adapters behind one phone UI. Samsung uncertainty does not disappear, but it stays behind the adapter. | Same shape. | Each ecosystem is implemented twice unless the project invents a shared core anyway. |

Account sync of non-secret data over HTTPS is supported by `fetch` in every React Native variant and by URLSession / OkHttp natively. It is not a differentiator and was not a pressure point in the sources reviewed. Pairing secrets staying local is a storage rule, already covered.

## Hardware-validation questions

Documentation cannot settle these. A later prototype should be the smallest experiment that answers one question, on a physical phone. A simulator or emulator is not a success criterion for LAN control.

1. **Which protocol family does the reference television actually speak?**
   - Why docs are insufficient: no public Samsung specification; community port maps conflict.
   - Smallest experiment: from a laptop on the same LAN, probe 8002, 8001, 8000, 8080, 55000, and 1515/1516 while the television is on; record which accept a connection and whether an on-screen prompt appears. Do not log tokens.
   - Platforms: neither phone required for this cut. Then repeat the successful port from Android and iOS.
   - Real Samsung television: yes.
   - Success: one port completes pairing and a volume key changes television volume. Failure: no port does, or only a cloud path does.

2. **Does the TLS certificate and the token survive reboot, DHCP renewal, and a cold reconnect?**
   - Why: community reports conflict on token rotation; certificate lifetime is unpublished.
   - Smallest experiment: after approval, store SPKI hash and token; reconnect 20 times; reboot the television; reconnect; note prompt, token change, and hash change.
   - Platforms: one is enough for the television behavior.
   - Real Samsung television: yes.
   - Success: hash stable and token either stable or updated without a new prompt. Failure: hash changes, or every reconnect prompts. Either failure makes fail-closed re-pairing a reliability problem.

3. **Can a native TLS callback complete the Samsung handshake without disabling verification?**
   - Why: community clients disable verification, so their success does not prove pinning works.
   - Smallest experiment: development-build or native harness; pin the hash from question 2; send one key; then present a mismatched pin and confirm refusal.
   - Platforms: both, because OkHttp and URLSession differ.
   - Real Samsung television: yes for the success path. The mismatch path can use a test double plus one real handshake.
   - Success: paired hash works; mismatched hash fails closed and does not send a key.

4. **Is SSDP required on iOS, or does declared AirPlay Bonjour find the television?**
   - Why: entitlement necessity depends on which advertisement the television actually emits.
   - Smallest experiment: packet capture of SSDP and mDNS while the television is on; then an iOS device browse of `_airplay._tcp` only, with local-network permission and without the multicast entitlement.
   - Platforms: iOS device for the browse; capture can be a laptop.
   - Real Samsung television: yes.
   - Success: Bonjour browse returns this television with a stable identity and no entitlement. Failure: only SSDP sees it, so the entitlement is required for iOS discovery.

5. **Does a standard magic packet wake this television?**
   - Why: SDK wake is undocumented at packet level past the 2016–2017 matrix; community Wi-Fi versus Ethernet reports conflict.
   - Smallest experiment: television in the off state the user actually uses; send one magic packet to the MAC from device info; wait a defined interval; try Wi-Fi and Ethernet if both exist.
   - Platforms: Android first, then iOS if the entitlement exists. The television result should be the same if the packet leaves the phone.
   - Real Samsung television: yes.
   - Success: television reaches a state where the control socket connects, within the interval, from the connection type the household uses. Failure: packet is sent and the television stays off. The product already requires explaining that failure.

6. **What is click-to-screen latency and the minimum reliable key spacing?**
   - Why: community clients insert long delays; the physical remote is the baseline; bridge overhead is unlikely to be the cause, but that is a hypothesis.
   - Smallest experiment: warm connection; 30 single volume clicks with timing; then a burst at human repeat rate.
   - Platforms: both, after the native socket exists. A laptop client is a useful control to separate television delay from phone delay.
   - Real Samsung television: yes.
   - Success: missed keys and visible latency are no worse from the phone than from the laptop client. Failure: the phone path drops keys the laptop does not, which would implicate the phone stack.

7. **Does reconnect stay quiet across lock, unlock, app switch, notification shade, and an incoming call?**
   - Why: OS suspension behavior is documented; Samsung re-prompt behavior is not.
   - Smallest experiment: paired session; perform each interruption; return; send one key.
   - Platforms: both.
   - Real Samsung television: yes.
   - Success: no on-screen approval prompt, and the key works within a few seconds of return, with a non-modal reconnecting state. Failure: prompt, or reconnect longer than ordinary remote use tolerates.

8. **Android volume keys.**
   - Why: public key codes exist; OEM behavior varies.
   - Smallest experiment: remote screen focused; volume keys change television volume and do not change phone media volume; leave the app; volume keys change phone volume again; setting off restores phone volume immediately.
   - Platforms: Android, more than one OEM if this is a promise.
   - Real Samsung television: yes.
   - Success: those three observations. Failure: OEM delivers the key to the system anyway.

9. **iOS volume buttons, observation only.**
   - Why: guideline 2.5.9. The experiment is to record what public APIs do, not to ship a bypass.
   - Smallest experiment: on a physical iPhone, observe whether a volume press changes system volume while a remote screen is visible, using only documented volume APIs.
   - Platforms: iOS.
   - Real Samsung television: not required to see system-volume behavior. Required only if the test also sends a television command.
   - Success criterion for the architecture question: a written result of what the public API did. Absence of a consume-without-system-change result is a finding, not a prompt to try private APIs.

10. **Text entry and application list.**
    - Why: both are commented as model-dependent.
    - Smallest experiment: focus a television text field; send a short Latin string and a non-Latin string from the phone keyboard; request the installed-app list; launch one returned id.
    - Platforms: either, once the socket works.
    - Real Samsung television: yes.
    - Success: text appears, list returns, launch opens that app. Partial success is a capability flag, which the product allows. Total failure of text on a set that clearly has a text field is a protocol gap.

11. **Address change.**
    - Why: product requires rediscovery rather than manual addresses.
    - Smallest experiment: pair; change the television DHCP reservation or reconnect it so the address changes; reopen the app.
    - Platforms: both, if discovery differs.
    - Real Samsung television: yes.
    - Success: the same paired television is selected without the user typing an address, and identity still matches. Failure: it is treated as a new television or the identity check false-fails.

## Required conclusions

### Demonstrated facts

- Expo SDK 57, current as of 30 June 2026 and updated 27 August 2026, is React Native 0.86 on React 19.2. Expo Go cannot host arbitrary native modules or AppT-specific entitlements. Development builds and prebuild can. [S1] [S2]
- Expo's SDK 54 changelog states that SDK 54 is the last release with Legacy Architecture support, and SDK 57 ships a React Native version past the stated opt-out removal. [S1] [S10]
- `expo-secure-store` stores short secrets in the iOS Keychain and in Android Keystore-backed encrypted preferences, and documents backup exclusion, uninstall differences, and a historical iOS size sensitivity around 2048 bytes. [S3]
- `expo-haptics` and `expo-keep-awake` cover the haptic and idle-sleep requirements inside Expo standard APIs, with the iOS haptic exceptions Expo documents. [S4] [S5]
- React Native's documented WebSocket has no certificate-pinning callback. Android cleartext and iOS ATS are real constraints for `ws://` and for `fetch` of `http://` television URLs. [S28]
- iOS requires `NSLocalNetworkUsageDescription` for local unicast and multicast. Sending or receiving multicast or broadcast on a physical iPhone requires the restricted multicast entitlement and Apple's approval. The simulator exemption does not test a television. [S32] [S33] [S36]
- Android 17 documents `ACCESS_LOCAL_NETWORK` as a runtime permission for local TCP/UDP, including SSDP, for apps targeting API 37. Google Play's requirement as of 31 August 2026 is target API 36, so that prompt is not yet a Play submission gate. [S38] [S39]
- Android exposes volume key codes publicly. iOS App Store guideline 2.5.9 rejects apps that alter or disable Volume Up/Down switch function. [S49] [S51]
- iOS suspends backgrounded apps and can close their sockets. A background mode cannot be borrowed to keep a remote alive. That limit is the operating system, not React Native. [S44] [S45]
- Samsung's published Smart View SDK is a sender/receiver multiscreen SDK whose retrieved feature matrix stops at 2017 and whose iOS library note is dated 2016. It is not a current public full-remote specification. [S11] [S12]
- SmartThings' documented API is an OAuth-protected cloud API. It does not satisfy local-first control. [S16]
- Samsung's model-group table runs through 2026 Tizen 10.0. Model year is not a control-protocol proof. [S14]
- Maintained community integrations implement a local WebSocket on ports 8001 and 8002, disable TLS verification, and discover sets with SSDP Samsung URNs plus optional AirPlay Bonjour. That is reverse-engineered evidence of a widely used channel, not a vendor contract. [S20] [S21] [S23]
- No maintained New-Architecture-ready SSDP library was established. `react-native-udp`'s last push is 5 March 2023. [S40]
- The phone UI requirements other than volume-button capture are inside React Native's accessibility and text APIs plus Expo Haptics and KeepAwake. [S4] [S5] [S48]

### Likely findings requiring validation

- A bounded Swift/Kotlin module inside an Expo development build can implement discovery, pinned TLS, token handling, wake send, and Android volume capture without a second UI codebase. This is likely because each of those platform APIs exists and Expo documents native modules as the extension mechanism. It is not demonstrated on a Samsung television.
- For a foreground remote, JavaScript can own screen state and key choice if the native socket reports identity, token, and close events reliably. Latency is likely dominated by the television, not the bridge. Both parts need measurement.
- Quiet reconnect is likely on sets whose connection-manager setting is "first time only" and whose token and certificate stay stable. The setting and the stability are the unproven parts.
- AirPlay Bonjour may reduce iOS discovery's dependence on the multicast entitlement for some sets, and will miss others. SSDP is the safer Samsung-specific signal and the one that triggers the entitlement.
- Android-first removes the iOS entitlement queue and the iOS volume-guideline conflict from the first release. It does not remove protocol, pinning, or wake uncertainty.
- Bare React Native and Expo-plus-native-modules are likely capability-equivalent for Samsung control once a custom module is allowed. The difference is native-project workflow and first-party UI libraries.
- Full native iOS and Android are unlikely to be more able to speak Samsung's local protocol than a bounded module, because no public current remote SDK was found that only a fully native app can call.

### Material unknowns

- Whether the reference television, and televisions AppT will actually encounter, still speak the community WebSocket channel after current firmware.
- Token rotation frequency, certificate rotation frequency, and which REST fields are stable identities.
- Default television UI for connection approval, and whether quiet reconnect requires the user to change a television setting.
- Whether Wake-on-LAN or Samsung WoW works on the connection type households use, and what packet current firmware expects.
- Minimum reliable key spacing, text-input coverage, mouse/touch coverage, and installed-app list coverage.
- Whether Apple will grant the multicast entitlement to this use, and how long that takes. The use is within the published purpose of the entitlement. Approval is not guaranteed.
- Whether App Review would accept any iOS volume behavior that changes what the buttons do. The guideline is clear enough that this should be treated as a product risk, not as an implementation task.
- Vendor terms for third-party use of the unpublished local channel. Out of architecture scope, still unresolved, and stack-independent.
- Expo SDK 57's default Android `targetSdkVersion` in a newly generated template. Not retrieved. Play's API 36 rule is retrieved.

### React Native / Expo pressure points

- Variant 1 cannot meet discovery, TLS identity, wake, or Android volume capture. Choosing it would compromise the product.
- Variant 2's pressure is concentrated in one native module and in iOS process limits, not in the UI toolkit. The module is justified. Pretending Expo Go represents Expo is not.
- Stock and community WebSocket clients either cannot pin a television certificate or disable verification. The latter is incompatible with the security requirement. The native module must not expose a bypass flag.
- Token-in-URL is a logging hazard on every JavaScript networking debug surface, including development-build network inspection.
- Unmaintained UDP/SSDP packages are a supply risk if used instead of a small owned module.
- iOS suspension means "always connected" is the wrong reliability target. The testable target is fast, non-prompting reconnect when the remote becomes active again.
- New Architecture is mandatory for a new Expo app. Libraries that are not New-Architecture-ready are not drop-in dependencies.

### Native-development pressure points

- Two complete phone applications would duplicate accessibility, dynamic type, keyboard, haptics, one-handed layout, account UI, and reconnect UX without a retrieved Samsung capability gain.
- The same iOS entitlement, local-network prompt, background-suspension rule, and volume-switch guideline apply to a fully native iPhone app.
- The same unofficial protocol and firmware risk apply. Native code does not make the television documented.
- A shared native core plus two UIs converges toward the bounded-module shape, with more UI cost.
- Android-first native still has to solve SSDP, pinning, and wake. It avoids iOS review and entitlement delay. It delays the iPhone product rather than proving the television.

### Recommended prototype questions

These are the smallest experiments that would change the architecture evidence. They are not an implementation of AppT.

1. On the reference Samsung television, which ports and which pairing prompt actually work, and do the certificate hash and token survive reboot and reconnect?
2. Can a minimal Expo development build, with a tiny native TLS WebSocket and no discovery, send a volume key to a known address on Android and on a physical iPhone, pin the paired key, and refuse a mismatched key?
3. After lock, app switch, and a call, does that session reconnect without a new television prompt, and is click latency comparable to a laptop client on the same socket?
4. Does the television emit SSDP, AirPlay Bonjour, both, or neither? That single capture decides whether iOS discovery requires the multicast entitlement.
5. Does one magic packet wake the television from the off state the household uses, on the network type it uses?
6. On Android, can foreground volume keys move television volume without moving phone volume, and revert when the remote is not active?
7. On iOS, what do documented volume APIs actually do while a remote screen is visible? Record the result against guideline 2.5.9. Do not prototype a bypass.
8. Do text entry and the installed-app list work on that television, as capability flags rather than as assumptions?

Questions 2 and 3 separate "React Native cannot hold a Samsung socket" from "the television protocol is awkward." Questions 4 and 5 separate iOS platform gates from television behavior. Question 6 is the Android volume proof. Question 7 is the iPhone volume finding the architecture decision needs and does not yet have. None of these require choosing the production stack first.

## Boundaries observed

This research did not modify `docs/PRODUCT.md` or `docs/PROJECT_STATE.md`, did not select a stack, did not treat Expo Go as the production ceiling, did not treat community protocol code as Samsung documentation, and did not implement the application. It did not recommend disabling TLS verification.

## Source register

Accessed 2026-09-22 unless noted. "HV" means hardware validation is still required for the product claim, even when the source itself is solid.

| ID | Title | URL | Date | What it proves | Confidence | HV |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | Expo SDK 57 changelog | https://expo.dev/changelog/sdk-57 | Published 30 June 2026; update 27 August 2026 | SDK 57 includes React Native 0.86 / 0.86.3 and React 19.2. Prebuild cleans native directories by default. | High | No |
| S2 | Introduction to development builds | https://docs.expo.dev/develop/development-builds/introduction/ | Current docs, SDK 57 site | Development builds are a custom Expo Go that can include any native library and native configuration. Expo Go is the fixed sandbox a new project starts in. | High | No |
| S3 | Expo SecureStore | https://docs.expo.dev/versions/latest/sdk/securestore/ | Current SDK 57 docs; recommended ~57.0.4 | Keychain on iOS, Keystore-encrypted preferences on Android, backup exclusion, uninstall differences, historical ~2048-byte iOS sensitivity, biometric option behavior. | High | No, except biometric-on-device behavior if used |
| S4 | Expo Haptics | https://docs.expo.dev/versions/latest/sdk/haptics/ | Current SDK 57 docs; ~57.0.3 | Haptics on Android and iOS, with documented iOS conditions where the Taptic Engine does nothing. | High | Only to confirm feel |
| S5 | Expo KeepAwake | https://docs.expo.dev/versions/latest/sdk/keep-awake/ | Current SDK 57 docs; ~57.0.2 | Idle-sleep prevention on Android and iOS, including Expo Go. | High | No |
| S6 | Expo Network | https://docs.expo.dev/versions/latest/sdk/network/ | Current SDK 57 docs; ~57.0.2 | IPv4 and coarse network state only. No UDP. iOS internet-reachable flag equals connected. | High | Phone IP usability on real Wi-Fi |
| S7 | Continuous Native Generation | https://docs.expo.dev/workflow/continuous-native-generation/ | Current docs | Prebuild generates native projects from config and plugins. EAS runs prebuild when native directories are absent. Prebuild is optional. | High | No |
| S8 | Config plugin mods | https://docs.expo.dev/config-plugins/mods/ | Current docs | Plugins modify native project files at prebuild, not at runtime. | High | No |
| S9 | Expo Modules API overview | https://docs.expo.dev/modules/overview/ | Current docs | Swift/Kotlin modules, New Architecture support, performance comparable to Turbo Modules, intended for missing platform features. | High | No |
| S10 | Expo SDK 54 changelog | https://expo.dev/changelog/sdk-54 | 10 September 2025 page age on the retrieved changelog | SDK 54 is the final SDK with Legacy Architecture support; RN 0.82 removes the opt-out; SDK 55 expected to be New Architecture only. | High for the statement; the SDK 55 page itself was not re-fetched | No |
| S11 | Smart View SDK getting started | https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/getting-started.html | Retrieved 2026-09-22; feature matrix content is older | Official multiscreen SDK. Sender plus receiver. Feature matrix 2014–2017. WoW marked for 2016–2017, not 2015. TLS for native senders at stated SDK versions. | High that this is what the page says; low that the matrix describes 2026 TVs | Yes, if anyone proposes this SDK as the control path |
| S12 | Smart View SDK download / release notes | https://developer.samsung.com/tv/develop/extension-libraries/smart-view-sdk/download | iOS 2.3.8 dated 14 December 2016; Android 2.3.7 dated 22 December 2016 | Official SDK binaries documented there are from 2016, including the TLS and WoW notes. | High | Yes for current-TV compatibility |
| S13 | Smart View supported TVs | https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/supported-device/supported-tvs.html | Retrieved 2026-09-22; listed years are 2014–2017 | Official support list for that SDK, region-variable, not a V1 year range for AppT. | High that the page is limited to those years | No |
| S14 | TV Model Groups | https://developer.samsung.com/smarttv/develop/specifications/tv-model-groups.html | Retrieved 2026-09-22; table includes 2026 / Tizen 10.0 | Official map of lineup year to Tizen or legacy platform. Not a remote-protocol map. | High | No |
| S15 | Remote Control key codes (on-TV) | https://developer.samsung.com/tv/develop/guides/user-interaction/remote-control | Retrieved 2026-09-22 | Official numeric key codes for TV applications, not a phone transport. | High | No |
| S16 | SmartThings authorization and permissions | https://developer.smartthings.com/docs/getting-started/authorization-and-permissions | Retrieved 2026-09-22 | Cloud API uses OAuth 2.0 bearer tokens and Samsung-account credentials. | High | No |
| S17 | ha-samsungtv-smart README | https://github.com/TheFab21/ha-samsungtv-smart | Pushed 17 August 2026 per search snippet; page retrieved via search | Community: local WebSocket 8001/8002 as primary control; SmartThings cloud as status; optional JSON-RPC on 1515/1516. | Medium, single integration | Yes |
| S18 | Samsung developer forum, socket client connect | https://forum.developer.samsung.com/t/socket-client-connect/29410 | Thread activity cited 8 February 2024 | Developers use the community WebSocket against a television. Retrieved excerpt has no vendor protocol specification. | Low as vendor evidence | No |
| S19 | Samsung developer forum, power keys | https://forum.developer.samsung.com/t/power-remote-control-keys-via-websocket-interface/36813 | 2 December 2024 | One report that `KEY_POWER` worked and `KEY_POWERON` / `KEY_POWEROFF` did not. | Low | Yes |
| S20 | samsung-tv-ws-api | https://github.com/xchwarze/samsung-tv-ws-api | Commit `e48d6377faede37db1f034d726a079b9d8034fac`, 11 September 2026 | Community protocol implementation: ports, token, events, key and text and mouse commands, REST apps, TLS verification disabled, encrypted extra, Device Connection Manager note, subnet note, LGPL-3.0. | High as a description of that code; medium as a prediction of television behavior | Yes |
| S21 | Home Assistant samsungtv constants and manifest | https://github.com/home-assistant/core/blob/3bc9cf09eb8d2079635d7a294e0f9176bc420f93/homeassistant/components/samsungtv/const.py and `manifest.json` | Commit `3bc9cf09`, 22 September 2026 | Ports 55000 / 8000 / 8001 / 8002. SSDP URNs. AirPlay zeroconf. Dependency on samsungtvws 3.0.6. Local-push classification. | High for what HA searches and links | Yes |
| S22 | home-assistant/core dev commit | https://github.com/home-assistant/core/commit/3bc9cf09eb8d2079635d7a294e0f9176bc420f93 | 22 September 2026 | Commit message records cleanup of deprecated Samsung TV Wake-on-LAN. | High that the commit exists | No |
| S23 | Home Assistant Samsung Smart TV docs | https://www.home-assistant.io/integrations/samsungtv/ | Retrieved 2026-09-22 | User-facing local REST plus WebSocket description, key-name list, Wake-on-LAN attempt, subnet limitation, variable app list. May lag the 22 September code cleanup. | Medium where it conflicts with S22 | Yes |
| S24 | openHAB Samsung TV binding | https://www.openhab.org/addons/bindings/samsungtv/ | Page content describes 2021-era behavior; retrieved 2026-09-22 | Community binding: legacy vs websocket vs secure websocket; H/J called unsupported; mouse and text claimed; conflicting WoL note. | Low to medium; possibly stale | Yes |
| S25 | samsungctl | https://github.com/Ape/samsungctl | Repository page retrieved via search | Older community TCP remote, legacy versus websocket methods. | Low; historical | Yes if pre-Tizen sets matter |
| S26 | Stack Overflow, Samsung token query parameter | https://stackoverflow.com/questions/61886062/samsung-tv-duplicates-permission-websocket | 19 May 2020 | Community report that a token stops repeated permission prompts. | Low | Yes |
| S27 | PepperDash Samsung Tizen WebSocket plugin | https://github.com/PepperDash/epi-samsung-tizenWebsocket | Retrieved via search 2026-09-22 | Community: token persistence and rotation; pairing events; self-signed acceptance. One tested model named by the README. | Medium for that plugin's behavior | Yes |
| S28 | React Native networking | https://reactnative.dev/docs/network | Current docs | Fetch, XHR, and WebSocket. No TLS callback. ATS and Android cleartext documented. | High | Handshake behavior on a television |
| S29 | Home Assistant issue, implicit WoL deprecated | https://github.com/home-assistant/core/issues/166226 | 22 March 2026 | Users told implicit Wake-on-LAN would be removed in favor of an explicit magic-packet automation. | Medium | Yes |
| S30 | Smart View enhanced features, Android | https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/android-sender-app/enhanced-features.html | Retrieved 2026-09-22 | Official `WakeOnWirelessLan(mac)` and `setSecurityMode` for the Smart View channel, not for the community remote channel. | High for that SDK | Yes |
| S31 | iobroker.samsung_tizen README | https://github.com/iobroker-community-adapters/ioBroker.samsung_tizen | Retrieved via search; page age cited 22 September 2023 | Community television setting names and a claim that WoL is wired-only. Conflicts with S24. | Low | Yes |
| S32 | com.apple.developer.networking.multicast | https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.networking.multicast | Current docs; entitlement since iOS 14 | Restricted entitlement required to send or receive IP multicast or broadcast on iOS. Apple approval required. | High | Device test after grant |
| S33 | How to use multicast networking in your app | https://developer.apple.com/news/?id=0oi77447 | 22 June 2020 | Physical hardware requires the entitlement; simulator does not. Bonjour plus usage string is the non-entitled path. Broadcast/multicast is the entitled path. | High; old but the entitlement page still matches | Yes |
| S34 | WifiManager.MulticastLock | https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock | Current reference | Acquire to receive Wi-Fi multicast; release to restore filtering. | High | Behavior on OEM Wi-Fi |
| S35 | Local Network Privacy FAQ-3 | https://developer.apple.com/forums/thread/663875 | Forum post dated 19 September 2022 in retrieved metadata | Apple: UDP multicast and broadcast send/receive require the multicast entitlement; enforcement expected from iOS 16. Declared Bonjour types are the exception class. | High for Apple's stated rule; forum is not a versioned doc page | Yes on current iOS |
| S36 | NSLocalNetworkUsageDescription | https://developer.apple.com/documentation/bundleresources/information-property-list/nslocalnetworkusagedescription | Current docs; iOS 14+ | Usage string for any direct or indirect local-network use, including Bonjour and unicast. | High | Prompt copy and denial UX |
| S37 | Local Network Privacy FAQ-2 | https://developer.apple.com/forums/thread/663874 | Forum metadata 16 October 2024 | Apple: outgoing TCP and UDP unicast, multicast, and broadcast to the local network require local-network permission. | High | Prompt timing |
| S38 | Local network permission | https://developer.android.com/privacy-and-security/local-network-permission | Retrieved 2026-09-22; page age in search 13 July 2026 | API 37 enforcement, `ACCESS_LOCAL_NETWORK`, Android 16 opt-in, IoT as a broad-access case, picker alternative. | High | Android 17 device when targeting 37 |
| S39 | Meet Google Play's target API level requirement | https://developer.android.com/google/play/requirements/target-sdk | Retrieved 2026-09-22 | From 31 August 2026, new apps and updates must target API 36, with stated exceptions and an extension window to 1 November 2026. | High | No |
| S40 | react-native-udp | https://github.com/tradle/react-native-udp | Last push 5 March 2023 | UDP library exists and is stale relative to mandatory New Architecture. | High for staleness | No |
| S41 | React Native self-signed WebSocket issue; Apple forum thread | https://github.com/facebook/react-native/issues/18920 ; https://developer.apple.com/forums/thread/710434 | 2018; July 2022 | Supporting evidence that stock RN WebSocket trust handling is not a JS option. Superseded in part by current docs (S28). | Medium | Yes |
| S42 | react-native-tcp-socket | https://github.com/Rapsssito/react-native-tcp-socket | Pushed 10 September 2026 | Maintained TCP/TLS module. Documented CA example is a bundled `require()`. Not a runtime television pin. | High for the README example | If someone still proposes it |
| S43 | React Native AppState | https://reactnative.dev/docs/appstate | Current docs | active, background, iOS inactive, Android blur/focus. | High | Interruption behavior with a live TV |
| S44 | Apple DTS, Network.framework background operation | https://developer.apple.com/forums/thread/116799 | 26 May 2019, DTS | Suspended apps can have connections closed. Background task only covers a short period. Indefinite background networking is not available. | High for the rule; old post | Reconnect timing |
| S45 | Apple DTS, iOS background execution limits | https://developer.apple.com/forums/thread/685525 | Thread updated through references to iOS 26; original 2021 | No general background execution. Background modes are purpose-limited. Quotes guideline 2.5.4 via DTS. | High | No |
| S46 | Optimize for Doze and app standby | https://developer.android.com/training/monitoring-device-state/doze-standby | Current guide; not fully re-quoted in this session | Doze restricts background work on idle devices. Used here only for that well-documented scope. | Medium, because the page body was not re-fetched in this session | OEM idle behavior if background control is ever proposed |
| S47 | react-native-keychain | https://github.com/oblador/react-native-keychain | Pushed 29 April 2026 | Active Keychain/Keystore library. API details not re-audited. | Medium | iCloud-sync flag if selected |
| S48 | React Native Text and TextInput | https://reactnative.dev/docs/text ; https://reactnative.dev/docs/textinput | Current docs | `allowFontScaling` defaults true. System keyboard via TextInput. | High | Large-type layout |
| S49 | Android KeyEvent | https://developer.android.com/reference/android/view/KeyEvent | Current reference | `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN` are public key codes. | High | OEM delivery to the activity |
| S50 | react-native-volume-manager | https://github.com/hirbod/react-native-volume-manager | Pushed 4 September 2026 | Android foreground volume-key interception documented. iOS observes and sets system volume. Expo Go unsupported. New Architecture supported. Does not remove 2.5.9. | High for the README | Android OEM; iOS public-API observation |
| S51 | App Store Review Guidelines | https://developer.apple.com/app-store/review/guidelines/ | Official page; search retrieval 2026-09-22 quoted 2.5.9; page age in search 8 June 2026 | Guideline 2.5.9: apps that alter or disable Volume Up/Down or Ring/Silent switches will be rejected. | High | Review outcome is still case-specific |
| S52 | App Review Guidelines PDF | https://developer.apple.com/support/downloads/terms/app-review-guidelines/App-Review-Guidelines-English-UK.pdf | 6 February 2026 in search metadata | Same 2.5.9 sentence in a downloadable guidelines text. | High | No |
| S53 | Apple developer forum, system volume | https://developer.apple.com/forums/thread/51980 | July 2016, marked accepted | Apple staff: system volume is a user preference; no API; MPVolumeView is the user-facing control. Old, consistent with 2.5.9. | Medium because of age | Public-API observation |
| S54 | Archived Cocoa Keys, NSAllowsLocalNetworking | https://developer.apple.com/library/archive/documentation/General/Reference/InfoPlistKeyReference/Articles/CocoaKeys.html | Archived; retrieved via search | `NSAllowsLocalNetworking` allows local resources without disabling ATS globally. Also says ATS does not apply to IP addresses, unqualified names, and `.local`. | Medium; archived, and S55 conflicts on IP literals | Cleartext probe on current iOS |
| S55 | Apple DTS, ATS changes around iOS 17 | https://developer.apple.com/forums/thread/747421 | February 2024 | ATS applies to URLSession, not Network framework or BSD sockets. IP-literal HTTP via URLSession is restricted. | High for the DTS distinction | RN `fetch` to a television IP |
| S56 | Expo build properties, referenced from current docs index | https://docs.expo.dev/versions/latest/sdk/build-properties/ | Current SDK docs index; page body not fully quoted | Mechanism to set Android target SDK and iOS deployment target in a config plugin. Default SDK 57 target was not retrieved. | Medium | No |
| S57 | react-native-netinfo | https://github.com/react-native-netinfo/react-native-netinfo | Pushed 15 February 2026 | Maintained connectivity library. Not discovery. | Medium | No |
| S58 | react-native-zeroconf | https://github.com/balthazar/react-native-zeroconf | Pushed 30 December 2025 | mDNS library, MIT, maintenance more recent than react-native-udp. New Architecture not verified. | Medium | Bonjour results on a television |
| S59 | react-native-ssdp and forks | https://github.com/netbeast/react-native-ssdp and npm pages retrieved via search | Package publish dates cited 2017–2019 | Abandoned SSDP-on-react-native-udp stack. | High for abandonment | No |

### Conflicts recorded

- **Wake-on-LAN medium.** S31 says wired works and Wi-Fi does not, except short standby. S24 says the opposite in the presence of an ARC soundbar. S23 still describes an integration attempt. S22 and S29 say Home Assistant is removing implicit Wake-on-LAN from the Samsung integration. None of these is a Samsung specification.
- **Encrypted-family ports.** S21 uses 8000 for encrypted WebSocket and 55000 for legacy. S20's encrypted pairing helper defaults to 8080 and its encrypted WebSocket helper defaults to 8000. Older write-ups treat 8080 as the 2014–2015 control port. These can be different legs of one flow, or real disagreements. Unresolved.
- **H/J support.** S24 says those series are unsupported because of PIN and encryption. S20 claims encrypted support for H series and part of J series. Unresolved.
- **Token stability.** S26 and common practice say a stored token suppresses prompts. S27 says tokens rotate. S20 updates the token if one arrives, which fits both. Unresolved.
- **ATS and IP literals.** Archived S54 says ATS does not apply to IP addresses. S55 says URLSession HTTP to IP literals became restricted and that lower-level APIs are outside ATS. For a native Network-framework or OkHttp client, ATS is the wrong control. For React Native `fetch`, the conflict remains a device test.
- **Smart View supported years versus model groups.** S13 stops in 2017. S14 continues through 2026. The SDK page is stale or the SDK's supported set was never updated. It must not be read as AppT's supported television range.
- **Home Assistant docs versus same-day code.** S23 describes built-in Wake-on-LAN. S22 cleans up deprecated Wake-on-LAN. Prefer the commit as the newer code fact, and the docs as the user-facing description that may lag.

### Evidence gaps that are not hardware tests

- Expo SDK 57 default `targetSdkVersion` and `minSdkVersion` in the blank template were not retrieved.
- The live App Store guidelines HTML section 2.5 was not fully re-rendered in this session beyond the official search excerpt and the February 2026 PDF. 2.5.9 is quoted from those. 2.5.4 is relied on through Apple DTS quotations in S45.
- SmartThings command schemas for televisions were not enumerated. The authorization model was enough to exclude the cloud API as the primary local-first path.
- No Samsung employee statement endorsing or forbidding third-party use of `samsung.remote.control` was retrieved. Absence of a public specification is the finding, not a proof of prohibition.
- New Architecture status of `react-native-zeroconf` and the full TLS option matrix of `react-native-tcp-socket` beyond the README example were not source-audited.
