# Product

**Status: approved product definition.**

AppT is a **Universal TV Remote** for ordinary consumers who want to control a television using the phone already in their hand.

The immediate problem is simple: the physical remote may be lost, broken, inconvenient, unavailable, or simply less useful than the phone.

AppT should make television control feel immediate, dependable, familiar, and effortless. The physical remote is the reliability baseline.

“Universal” means AppT is designed to support multiple television ecosystems behind one consistent experience. It does not mean every television is guaranteed to work.

V1 begins with **Samsung Smart TVs**. Other televisions may be discovered and experimentally probed, but Samsung is the first ecosystem intentionally targeted.

## Product priorities

Reliability is the primary product constraint.

Premium visual quality, accessibility, and a polished phone-native experience are major priorities, but they must not compromise dependable control. AppT has a friendly consumer-facing brand personality and may use a mascot in onboarding, support, and other low-frequency moments; the everyday remote surface itself is premium, restrained, dark-first, and utility-focused.

Broad compatibility and feature count come after reliable everyday operation.

The connected television is the source of truth. AppT exposes controls from demonstrated capabilities rather than assuming every model in a family behaves identically.

Technical networking and protocol details stay out of the normal user experience.

## Platforms

AppT targets **Android and iPhone for V1** when the selected architecture can support both without materially compromising reliability.


If simultaneous cross-platform delivery would compromise core product quality or require unreasonable platform compromises, AppT may fall back to an Android-first launch.

Architecture and application-stack selection occur after product definition and are not predetermined by the product requirements.

## Local-first control

Normal TV control is local-first.

Before an account exists, AppT lets the user reach one successful local-control session so compatibility is demonstrated before sign-in or payment.

After account creation, a seven-day trial provides full use. The trial has a server-authoritative expiry, but an active remote session is not interrupted when that time passes. Once the known trial expiry has passed, the next remote entry requires an online entitlement check or purchase.

After a lifetime entitlement has been validated, paired-TV control remains available offline indefinitely. AppT does not require periodic backend contact to keep a paid customer's local remote working.

Outside the licensing gate above, previously working local control continues during AppT service or public-internet outages.

## Accounts

An AppT customer account exists for a deliberately narrow purpose:

- authenticate the customer;
- store the customer's chosen username;
- track seven-day trial eligibility and expiry;
- hold the customer's lifetime AppT license entitlement.

The account is **not** a television or personalization profile.

AppT does not sync or store television identities, friendly television names, favourites, remote layouts, preferences, last-used television, pairing state, pairing credentials, diagnostic history, or remote-use history in the customer account/backend.

TV pairing, remembered televisions, television names, favourites, remote arrangement, and settings remain device-local. They are excluded from Android cloud backup and device-to-device restoration so a new phone starts its remote state clean. A second phone signed into the same customer account receives the customer's username and license entitlement, but it pairs and configures televisions independently.

Google sign-in is the primary account path. A verified Google identity creates or signs into the AppT customer account without a separate registration form. Email/password remains a fallback, but its email address must be verified before that account can activate a free trial. A Google display name may be suggested as the initial username, but the user may edit it. The username is a non-unique display name, not a login identifier or public handle. AppT does not copy unrelated Google profile data into the customer account.

First-run prioritizes demonstrating product value before account creation:

**Welcome → discovery → pairing/control → account creation → seven-day trial.**

Account requirements must not prevent the user from reaching the first successful local-control experience. The first successful command does not interrupt that active remote session. After that active remote session ends, the next remote entry requires an AppT account.

The seven-day trial begins from a server timestamp when the account first activates its trial. The same trial follows that account to another phone with the original expiry; signing in on another phone does not create a new seven-day window. A participating phone is also marked as having consumed a trial so it cannot later be used to create a second trial through another account. Trial abuse prevention may use privacy-minimized, pseudonymous eligibility signals derived from the verified email identity and this Android device, plus Play Integrity for authenticity checks. Raw television or behavioral data is never part of trial eligibility.

Creating another account or reinstalling AppT does not grant another trial when the same verified email identity or recognized Android device has already consumed one. One Android device receives one AppT trial total unless support explicitly clears an abuse marker for a legitimate exceptional case such as a second-hand device. V1 does not depend on Play Integrity Device Recall. A customer who is not trial-eligible may still sign in, restore a lifetime entitlement, or purchase one.

Deleting an AppT account permanently deletes the account and ordinary account-held username/trial/license profile records subject to required transaction/legal retention. Minimal pseudonymous "trial already used" eligibility markers may remain for as long as AppT operates the free-trial program so account deletion cannot be used to reset trial eligibility. Those markers contain no username, raw email, TV data, personalization, or usage history. Device-local television pairing and personalization remain on the phone because they do not belong to the account. The user may separately forget televisions or clear app data.

Signing out or switching accounts does not erase, merge, upload, or replace local television/personalization data. Account identity and license state are separate from the phone's local remote state. Explicit sign-out ends licensed access for new remote entries until the customer signs in again, but it does not delete the locally cached entitlement proof, TVs, pairing, or personalization, and it never interrupts an already active remote session.

## Samsung-first strategy

AppT begins with Samsung Smart TVs.

V1 does not predeclare a fixed supported Samsung year range. Discovery may identify Samsung televisions across generations and determine what each device can actually do.

Compatibility is capability-driven rather than based purely on model assumptions.

Where AppT encounters an unfamiliar or unsupported television, production V1 may identify and safely probe enough to classify support, but it does not offer consumer experimental functional control on a non-adopted protocol.

Internal/debug engineering builds may exercise bounded experimental protocol implementations for research. Those experiments are not a production user mode and do not weaken production trust or safety rules.

An unsupported production television can offer a **Request Support** flow. Sending device or protocol diagnostics is an explicit user choice, and submitted information is appropriately redacted.

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

Remembered-TV surfaces use friendly names and ordinary-language states such as Ready, Connecting, Offline, or Needs pairing. Technical network/protocol identifiers are never part of the ordinary TV list.

## Remote experience

The remote should be immediately recognizable but designed for a phone rather than visually copying a physical remote. After setup AppT is remote-first: normal launch attempts to reopen the last-used television rather than landing on a dashboard.

Controls are capability-driven.

Where supported, AppT provides both directional-button navigation and touchpad/swipe navigation and lets the user choose.

Phone-native behavior includes:

- subtle haptic feedback by default, with an option to disable it;
- physical phone volume buttons controlling TV volume while the remote is active, with an option to disable this behavior;
- the normal phone keyboard for supported TV text input.

Everyday controls remain prominent and keep stable positions for muscle memory. Less frequently used capabilities can live behind secondary surfaces and may be favourited/reordered. The core remote is not a fully free-form layout designer.

Simple favourites and control rearrangement are appropriate for V1. Favourites and secondary-control order are per television and device-local. Interaction preferences such as haptics, physical volume-button behavior, and preferred navigation mode are device-wide and device-local. A full remote-layout designer is not required.

One-handed use is a first-class design requirement. The everyday Remote does not carry persistent bottom navigation; television switching and secondary destinations use compact, contextual affordances so the control surface retains the screen. High-frequency controls occupy the thumb-friendly central/lower region, while power is deliberately isolated toward the top to reduce accidental activation.

Accessibility is first-class, including screen-reader support, scalable text without clipping/overlap, strong contrast, interactive targets of at least 48dp, clear labels/roles/state announcements, no color-only meaning, alternatives for gesture interactions, and respect for reduced-motion preferences.

## Applications and shortcuts

When the television exposes installed or launchable applications, AppT discovers and uses that information.

AppT may also provide curated familiar shortcuts such as major streaming services, but only when it has confirmed that the corresponding application is actually launchable on the connected television.

Users can favourite or rearrange these shortcuts. Remote exposes only a compact favourite subset; a fuller secondary surface contains the rest of the confirmed launchable apps and secondary controls.

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

Cloud crash reporting is opt-in rather than automatic. AppT always keeps a bounded, already-redacted local diagnostic buffer. A customer may explicitly enable anonymous crash reporting in Settings or explicitly share a redacted Request Support report. Crash data must not be tied to username, account id, email, television id, network identifiers, command/text content, or another persistent user identifier.

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

V1 uses a **seven-day free trial per eligible customer/device**, followed by a **one-time lifetime AppT license fee** for continued use.

There is no advertising and no subscription.

Trial/purchase UX is low-pressure: show the exact remaining trial time in account/settings surfaces, do not interrupt remote control with purchase prompts, allow at most a lightweight reminder near expiry, and after expiry gate the next remote entry with clear **Buy once** and **Restore purchase** actions. No artificial urgency, repeated nags, or subscription-style dark patterns.

On Android, the lifetime unlock is purchased as a Google Play one-time non-consumable product. After AppT validates the purchase, the lifetime entitlement is associated with one AppT Customer Account so it can be restored on another supported Android device after sign-in. A purchase is not freely movable between unrelated AppT accounts. V1 has no paid-device roster or fixed device cap.

The entitlement model should remain conceptually vendor-neutral for a future iPhone implementation, but V1 does not promise that an Android purchase unlocks a future iOS version.

A customer with a validated lifetime entitlement keeps local remote control offline indefinitely; licensing infrastructure must not become a recurring dependency for paid local control. If Google Play reports a genuine purchase but AppT's authoritative validator is temporarily unavailable, AppT may grant a short non-renewable provisional entitlement while retrying validation so a paying customer is not blocked by AppT infrastructure. Network failure never removes a previously validated entitlement. If AppT later receives an authoritative refunded/revoked result while online, that revocation applies on the next remote entry and does not interrupt an already active remote session. Deleting an AppT account does not destroy the underlying Google Play purchase: a legitimate purchaser may use Restore Purchase after recreating/signing into the appropriate AppT identity.

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
