# AppT V1 architecture decision gap analysis

**Status:** independently verified discovery synthesis; non-binding; no architecture or stack selected  
**Prepared:** 2026-09-22  
**Inputs:** `docs/PRODUCT.md`, `docs/PROJECT_STATE.md`, and `docs/research/V1_APPLICATION_ARCHITECTURE_STACK_RESEARCH.md`

## Purpose

This pass independently checks the architecture-critical claims in the merged V1 stack research and reduces its proposed experiment set to the minimum evidence gates needed before AppT selects a V1 architecture and stack.

It does not select a framework, runtime, UI toolkit, session owner, Samsung support range, or release policy.

## Verified conclusions

### 1. Local-network platform integration is unavoidable

Apple's current local-network privacy guidance says outgoing TCP connections, UDP unicast, Bonjour operations, multicast, and broadcast to the local network are subject to local-network permission. iOS additionally requires the restricted `com.apple.developer.networking.multicast` entitlement for IP multicast/broadcast and arbitrary Bonjour service types.

Sources:
- Apple TN3179: <https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy>
- Apple multicast entitlement: <https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.networking.multicast>

Android 17 / API 37 introduces the `ACCESS_LOCAL_NETWORK` runtime permission for apps targeting API 37 or higher, with Android 16 offering opt-in testing.

Source:
- Android 17 behavior changes: <https://developer.android.com/about/versions/17/behavior-changes-17>

**Architecture consequence:** no credible cross-platform framework removes the need for explicit iOS/Android permission, entitlement, and network-lifecycle seams.

### 2. A persistent general-purpose iOS LAN session is not a valid baseline assumption

Apple documents that normal foreground apps move briefly through background execution and are then suspended, and advises apps to quiet work and release resources as they transition to the background.

Source:
- Apple, Preparing your UI to run in the background: <https://developer.apple.com/documentation/uikit/preparing-your-ui-to-run-in-the-background>

**Architecture consequence:** AppT should be designed around resumable/reconstructable local sessions and explicit reconnect state, not around a socket being continuously alive while the app is backgrounded.

### 3. The iPhone physical-volume requirement is a product/App Review gate, not an architecture discriminator

Apple App Review guideline 2.5.9 says apps that alter or disable the functions of standard switches, including Volume Up/Down, will be rejected. Apple's public audio API documentation also states that only the user can directly set system volume.

Sources:
- App Review Guidelines 2.5.9: <https://developer.apple.com/app-store/review/guidelines/>
- `AVAudioSession.outputVolume`: <https://developer.apple.com/documentation/avfaudio/avaudiosession/outputvolume>

**Architecture consequence:** do not spend architecture prototype budget trying to prove that a framework can make hardware-volume repurposing acceptable. Unless Apple provides an approved interpretation, the product requirement should be handled as a separate product/release decision.

### 4. Samsung's official public material remains useful but does not establish a current generic remote-control contract

Samsung still hosts Smart View sender/receiver material, current package downloads, TLS-capable historical releases, and the `http://TV_IP:8001/api/v2/` debugging endpoint. The material is centered on Smart View sender/receiver applications and does not establish a current vendor-supported generic phone-remote API for arbitrary Samsung TVs.

Sources:
- Smart View SDK overview: <https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/introduction.html>
- iOS Sender App: <https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/ios-sender-app.html>
- Smart View download/release notes: <https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/download.html>
- Smart View debugging endpoint: <https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/receiver-apps/debugging.html>

Samsung also documents model/platform variation in TV-side key handling and recommends key-name based handling where possible.

Sources:
- Remote Control: <https://developer.samsung.com/smarttv/develop/guides/user-interaction/remote-control.html>
- User Interaction Q&A: <https://developer.samsung.com/smarttv/develop/faq/user-interaction.html>

**Architecture consequence:** capability-driven Samsung behavior remains the correct assumption. Real-device protocol evidence is still required.

### 5. Framework documentation proves reachability, not reliability

Current first-party documentation confirms that the major candidate families can reach the kinds of seams AppT needs:

- React Native supports WebSockets and lifecycle state, while native modules remain available.
- Expo development builds permit custom native code; Expo Go is not a production-grade substitute for a custom native runtime.
- Flutter supports WebSockets and platform-specific Swift/Kotlin code through platform channels/Pigeon.
- Kotlin Multiplatform supports stable Android/iOS targets and platform-specific integration; Ktor exposes platform-specific engines including Darwin and Android/OkHttp WebSocket-capable engines.
- .NET MAUI exposes platform APIs and secure storage.
- Capacitor exposes native plugin code from a web-first runtime.

Representative sources:
- React Native networking: <https://reactnative.dev/docs/network>
- React Native AppState: <https://reactnative.dev/docs/appstate>
- Expo custom native code: <https://docs.expo.dev/workflow/customizing/>
- Flutter platform channels: <https://docs.flutter.dev/platform-integration/platform-channels>
- Kotlin Multiplatform supported platforms: <https://kotlinlang.org/docs/multiplatform/supported-platforms.html>
- Ktor client engines: <https://ktor.io/docs/client-engines.html>
- .NET MAUI platform integration: <https://learn.microsoft.com/en-us/dotnet/maui/platform-integration/?view=net-maui-10.0>
- Capacitor docs: <https://capacitorjs.com/docs>

**Architecture consequence:** documentation alone does not justify selecting a session owner or application stack. The differentiator is measured behavior at AppT's Samsung/security/lifecycle seams.

## What is already settled enough for architecture work

The next architecture decision can treat the following as constraints rather than open research questions:

- TV commands remain phone-local; AppT's backend is not in the ordinary control path.
- Pairing secrets remain phone-local and use platform-appropriate secure storage.
- Discovery is an explicit subsystem with permission/error/cancellation state rather than a helper returning IP addresses.
- Samsung support is capability-driven rather than a hard-coded year/model matrix.
- IP address is a locator, not device identity.
- A global TLS-verification bypass is unacceptable.
- Local sessions must tolerate suspension, process loss, IP change, and reconstruction.
- Native/platform seams are required even if substantial domain, protocol, or UI code is shared.
- The iPhone hardware-volume requirement does not decide the application stack.

## Minimum unresolved architecture gates

The nine experiments in the baseline research are useful as a total validation backlog, but only three gates materially block the V1 architecture decision.

### Gate A — Samsung transport, pairing, and security identity

**Question:** On representative physical Samsung TVs, what local protocol path actually works, and can AppT establish a safe persistent TV identity without a global trust bypass?

Minimum evidence should include:

- device-info response and stable identifiers;
- observed 8001/8002/legacy behavior;
- approval/PIN/token path;
- one harmless remote command;
- presented TLS certificate/chain where WSS is used;
- certificate/identity behavior across reconnect and TV reboot;
- explicit result for system trust, scoped pinning/TOFU, or another fail-closed policy;
- a redacted trace showing unsupported versus temporarily unavailable behavior.

A broad pre-Tizen/Frame/model-year compatibility matrix is not required to make the first architecture decision. Start with the smallest representative set available, then expand compatibility after the control seam is understood.

### Gate B — Physical iOS/Android discovery and network-lifecycle baseline

**Question:** Which discovery/reconnect combination works on real phones under current OS privacy rules, and which platform events must the application architecture expose?

Minimum evidence should include:

- iOS Local Network allow/deny/revoke behavior;
- known-service Bonjour if Samsung advertises a usable service;
- SSDP/custom multicast only when the multicast entitlement is available;
- bounded direct-unicast fallback where appropriate;
- Android local-network permission behavior using the current API 37 model or Android 16 opt-in testing;
- cached-address reconnect followed by identity verification;
- network/IP change;
- background/foreground and lock/unlock;
- cancellation and clear permission-denied/unavailable states.

The semantics of iOS local-network permission itself no longer require research; Apple documents them. The experiment is about AppT's actual Samsung discovery path, timing, UX, and recovery.

### Gate C — Session-placement comparison after Gates A and B

**Question:** Once a reproducible Samsung path exists, which ownership boundary gives the best reliability and maintainability on physical iPhone and Android devices?

Do not compare every framework.

Compare a native baseline against only the smallest credible shared alternatives needed to answer the ownership question. Measure the same minimal flow and failure matrix for each candidate:

- pair;
- store/retrieve secret through the platform vault seam;
- send one command;
- handle one unsupported capability;
- resume after suspension;
- recover after network/IP change;
- reject an unexpected identity change;
- cancel an in-flight operation;
- produce redacted diagnostics.

The architecture decision should use physical-device results, not code-sharing percentage or the existence of a native escape hatch.

## Deferred validation, not first architecture gates

The following remain important but should not block the first stack/session decision unless Gate A or B exposes a dependency on them:

- broad Samsung cohort/support matrix beyond the initial representative TVs;
- Wake-on-LAN/WoW repeatability;
- full text input, pointer/touchpad, app-launch, and shortcut breadth;
- account backup/restore and migration behavior after the secret/non-secret model is implemented;
- future-ecosystem implementation details beyond confirming adapter isolation;
- full accessibility/polish acceptance for the chosen UI stack;
- vendor/legal release review;
- iPhone physical-volume-button product/App Review resolution.

## Prototype prioritization

For the first physical probe, use thin platform-native diagnostic shells or equivalent minimal harnesses so the experiment exposes raw iOS/Android/Samsung behavior instead of cross-platform runtime behavior. This is a measurement baseline, not a native-stack selection.

Only after that baseline exists should shared-session candidates be introduced. A cross-platform prototype should be chosen to answer a specific ownership question, not to build a miniature product.

Cloud/Arena work can prepare harnesses, fixtures, instructions, parsers, and redaction. It cannot by itself satisfy Gates A-C unless the resulting evidence includes traces from actual Samsung TVs and physical iPhone/Android devices.

## Candidate posture after verification

Documentation review does not eliminate fully native, Kotlin Multiplatform, React Native, or Flutter as credible V1 candidates.

For prototype budgeting:

- treat Expo as a React Native workflow/runtime configuration, not a separate architecture family;
- keep .NET MAUI and Capacitor as reserve candidates unless project/team constraints give them a concrete advantage;
- keep Rust/C++ shared-core work deferred until a specific measured problem justifies the extra FFI/toolchain boundary.

This is prototype prioritization only. It is not a stack ranking or architecture decision.

## Next decision point

The repository should remain in **discovery**.

The next material evidence should be Gate A plus Gate B baseline results. Once those exist, AppT can select the minimum session-placement comparison in Gate C. The V1 architecture/stack decision should follow that comparison, with explicit trade-offs and a recorded rationale.
