# Product

**Status: approved product definition.**

AppT is a **Universal TV Remote** for ordinary consumers who want to control a television using the phone already in their hand.

The immediate problem is simple: the physical remote may be lost, broken, inconvenient, unavailable, or simply less useful than the phone.

AppT should make television control feel immediate, dependable, familiar, and effortless. The physical remote is the reliability baseline.

“Universal” means AppT is designed to support multiple television ecosystems behind one consistent experience. It does not mean every television is guaranteed to work.

V1 begins with **Samsung Smart TVs**. Other televisions may be discovered and experimentally probed, but Samsung is the first ecosystem intentionally targeted.

## Product priorities

Reliability is the primary product constraint.

Premium visual quality, accessibility, and a polished phone-native experience are major priorities, but they must not compromise dependable control.

Broad compatibility and feature count come after reliable everyday operation.

The connected television is the source of truth. AppT exposes controls from demonstrated capabilities rather than assuming every model in a family behaves identically.

Technical networking and protocol details stay out of the normal user experience.

## Platforms

AppT targets **Android and iPhone for V1** when the selected architecture can support both without materially compromising reliability.

React Native and Expo are candidates for investigation, not accepted architecture decisions.

If simultaneous cross-platform delivery would compromise core product quality or require unreasonable platform compromises, AppT may fall back to an Android-first launch.

Architecture and application-stack selection occur after product definition.

## Local-first control

Normal TV control is local-first.

Once a television is paired, AppT's backend or the public internet must not be required for that television to continue functioning as a remote.

Previously working local control continues during AppT service or internet outages.

## Accounts

An AppT account is part of the product.

The account synchronizes non-secret user data such as:

- friendly television names;
- favourites;
- preferences and settings.

TV pairing secrets and device credentials remain local to each phone.

Different household phones pair with televisions independently rather than sharing pairing credentials through AppT's backend.

First-run prioritizes demonstrating product value before account creation:

**Welcome → discovery → pairing/control → account creation.**

Account requirements must not prevent the user from reaching the first successful local-control experience. Sign-in is required for continued/full product use and synchronization features, not as a prerequisite for the first successful remote session.

## Samsung-first strategy

AppT begins with Samsung Smart TVs.

V1 does not predeclare a fixed supported Samsung year range. Discovery may identify Samsung televisions across generations and determine what each device can actually do.

Compatibility is capability-driven rather than based purely on model assumptions.

Where AppT encounters an unfamiliar or unsupported television, it may automatically identify and probe likely protocols, but it does not send functional remote-control commands until the user explicitly chooses to try experimental control.

If experimental control fails, AppT offers a **Request Support** flow. Sending device or protocol diagnostics is an explicit user choice, and submitted information is appropriately redacted.

## Discovery and setup

First-run should:

1. Give a concise welcome and explanation.
2. Begin automatic television discovery.
3. Explain operating-system permissions in ordinary language before requesting them.
4. Show friendly discovered-device cards rather than IP addresses and protocol details.
5. Guide the user through any television-side approval or pairing flow.
6. Enter the working remote as quickly and simply as practical.
7. Introduce account creation after the user has experienced successful control.

There is no public numeric setup-time SLA.

Setup must minimize unnecessary steps, avoid technical configuration, and be benchmarked during product testing so regressions are visible.

Manual networking configuration is not the normal consumer experience.

## Multiple televisions

AppT remembers multiple televisions.

Users can assign friendly names such as Lounge, Bedroom, or Office.

The last-used television normally reopens automatically, with reconnection occurring quietly.

Changes to local IP addresses are handled through rediscovery rather than requiring users to manage addresses manually.

## Remote experience

The remote should be immediately recognizable but designed for a phone rather than visually copying a physical remote.

Controls are capability-driven.

Where supported, AppT provides both directional-button navigation and touchpad/swipe navigation and lets the user choose.

Phone-native behavior includes:

- subtle haptic feedback by default, with an option to disable it;
- physical phone volume buttons controlling TV volume while the remote is active, with an option to disable this behavior;
- the normal phone keyboard for supported TV text input.

Everyday controls remain prominent. Less frequently used capabilities can live behind secondary surfaces.

Simple favourites and control rearrangement are appropriate for V1. A full remote-layout designer is not required.

One-handed use is a first-class design requirement.

Accessibility is first-class, including screen-reader support, scalable text, strong contrast, large touch targets, and clear labels.

## Applications and shortcuts

When the television exposes installed or launchable applications, AppT discovers and uses that information.

AppT may also provide curated familiar shortcuts such as major streaming services, but only when it has confirmed that the corresponding application is actually launchable on the connected television.

Users can favourite or rearrange these shortcuts.

Dead or knowingly unavailable app buttons are not presented.

## Power and recovery

AppT may attempt generic Samsung network wake behavior, including Wake-on-LAN where appropriate, even before model-specific wake support has been verified.

If a television cannot be awakened remotely, AppT explains the limitation rather than presenting a permanently ineffective power control.

Connection drops trigger quiet automatic reconnection and a lightweight status such as **Reconnecting**, rather than repeated modal errors.

## Security

Pairing credentials, tokens, private keys, and equivalent secrets are password-equivalent data.

They use platform-appropriate secure storage and are excluded from ordinary logs and analytics.

Where a persistent television security identity can be established, reconnect behavior fails closed if that identity changes unexpectedly and requires explicit re-pairing rather than silently trusting a replacement.

AppT does not provide a global certificate-verification bypass or generic “ignore security errors” mode.

The exact Samsung protocol and vendor-terms position receives focused legal/vendor review before public release. This review does not block early product or architecture work.

## Privacy and diagnostics

V1 has **no behavioral usage analytics**.

Privacy-safe crash and error reporting is acceptable when it excludes sensitive information such as:

- pairing credentials;
- sensitive remote-control command contents;
- Wi-Fi names;
- local IP addresses;
- directly identifiable television information.

More detailed diagnostic information may be exported only through an explicit user action.

## Casting and voice

Casting or mirroring may ship where it can be supported cleanly, but it is not a launch blocker.

Voice control may ship where a television ecosystem exposes it cleanly and safely without delaying launch.

Neither feature should distort the core remote-control product.

## Business model

V1 launches free.

There is no advertising and no required paid tier or subscription at launch.

Long-term monetization can be reconsidered after the product demonstrates real value.

## Product name

**AppT** remains the working and V1 name.

It may be renamed later.

## Product success

AppT succeeds when an ordinary user can discover and control their television with minimal thought, and then depend on AppT in everyday use as they would depend on a physical remote.

The defining qualities are:

- dependable local control;
- effortless setup and recovery;
- a polished phone-native experience;
- capability-aware behavior;
- strong accessibility;
- sensible security and privacy;
- a foundation capable of expanding beyond the first Samsung implementation into a genuinely universal television remote.

## Reference provenance

Greenfield4's `docs/PRODUCT.md` and `docs/research/` were used as reference material during AppT product-definition discovery. They are evidence and precedent only. AppT inherits no Greenfield4 requirement unless it is explicitly recorded in this document.
