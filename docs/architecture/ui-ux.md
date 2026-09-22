# UI/UX architecture

This file owns AppT's product-surface architecture: information architecture, screen/state behavior, interaction patterns, visual-system rules, and accessibility constraints.

It elaborates `docs/PRODUCT.md`. It does not change control, account, licensing, or Samsung protocol semantics.

## Experience principles

1. **Consumer personality first, utility underneath.** AppT may use a friendly mascot and brighter consumer-facing personality in onboarding, empty states, support, and brand moments. The everyday remote itself uses a premium, restrained, dark-first utility theme with high contrast, subtle depth, and minimal decoration.
2. **The remote is the product.** After setup, AppT is remote-first rather than dashboard-first.
3. **Stay in context.** Temporary failures are shown where they happen with obvious recovery actions instead of generic error pages or repeated modal dialogs.
4. **Muscle memory matters.** Core controls keep stable positions; customization applies to secondary controls and favourites.
5. **One-handed and accessible by construction.** Reach, target size, semantics, scale, contrast, and status announcements are architectural constraints, not a final polish pass.
6. **Licensing stays out of control.** Trial/purchase/account surfaces are explicit and clear but do not contaminate an active remote session.

## Brand and visual character

AppT has two visual layers:

- **Brand layer:** friendly, approachable consumer character; a mascot may appear in Welcome, discovery empty states, support, account/trial education, and other low-frequency moments.
- **Remote utility layer:** premium, restrained, dark-first surface optimized for a dim living room and frequent use.

The mascot does not occupy persistent remote-control space, obscure controls, or become required to understand status/errors.

Use Material foundations where useful, but AppT should not look like a generic Material sample or a skeuomorphic plastic remote.

Support system/light appearance where required by accessibility and platform expectations, while treating the dark remote surface as the primary design reference.

## Information architecture

Primary routes remain:

- `Welcome`
- `LocalNetwork`
- `Discovery`
- `Pairing` / pairing state
- `Remote(tvId)`
- `TvList`
- `Account`
- `Settings`
- `ExportDiagnostics`

After setup, normal launch attempts to reopen the last-used television into `Remote`.

There is no persistent bottom navigation on the everyday remote.

The Remote top area contains:

- current friendly TV name;
- lightweight connection state;
- an obvious TV-switch affordance;
- compact access to secondary app actions such as Settings.

The remote control surface receives the majority of the screen.

## Remote composition

Use **fixed core + customizable secondary controls**.

Core controls remain predictably placed and are not freely rearranged:

- power when genuinely available;
- navigation;
- volume;
- mute;
- back/home;
- other universally prominent everyday controls demonstrated by the television.

Secondary controls and launchable-app shortcuts may be favourited and reordered per television.

Do not provide a full arbitrary layout designer in V1.

### Directional and touchpad navigation

When both are demonstrated by the television, expose a visible D-pad / Touchpad toggle in the central navigation area.

Switching changes that navigation surface, not the entire screen.

Remember the last selected navigation mode as a device-local Interaction Preference.

Do not require a Settings round-trip for ordinary switching.

## Television switching

Tapping the television name/switch affordance on Remote opens a bottom sheet containing:

- remembered televisions;
- simple reachability/connection status in ordinary language;
- an `Add TV` action.

Selecting another television gracefully releases or transitions the prior Active Remote according to the existing session rules, then opens the selected television.

Do not use horizontal swipe between TVs; it conflicts with touchpad gestures and is too easy to trigger accidentally.

A full TV list remains available for rename/forget/management, but is not required for ordinary switching.

## Onboarding

Use one concise Welcome screen before discovery.

Welcome contains:

- the core value proposition;
- a short privacy/local-control reassurance;
- mascot/brand personality where useful;
- one primary action such as **Find my TV**.

Do not use a multi-page feature carousel.

The local-network explanation follows immediately before the first bounded discovery, consistent with the permission architecture.

## Pairing

Pairing is a dedicated focused state, not a spinner embedded in the discovery list and not a modal over it.

It:

- identifies the chosen TV by friendly name;
- tells the user in ordinary language to look at the TV and choose **Allow**;
- shows calm progress;
- offers Cancel;
- exposes no protocol, port, certificate, token, or generation terminology.

Successful approval transitions directly into Remote.

Pairing failure remains in this context with retry/repair actions appropriate to the actual failure.

## Account, trial, and purchase surfaces

After the first exempt Remote session ends, a full-screen continuation surface explains:

- sign in to keep using AppT;
- Google sign-in as the primary action;
- email/password as a secondary option;
- **7 days free**;
- **one-time purchase after the trial**;
- **no subscription**.

Do not show the account gate as a modal over an active remote.

During Trial:

- exact remaining time is visible in Account/Settings;
- purchase is available but not dominant;
- no recurring pay banners on Remote;
- at most one lightweight reminder near expiry;
- no artificial countdown urgency.

After Trial expiry, entering Remote routes to a clean entitlement surface with:

- **Buy once**;
- **Restore purchase**;
- account identity/details;
- honest backend-error states when validation cannot complete.

An active remote is never interrupted by trial expiry, purchase processing, revocation, or sign-out.

## Settings

Use one conventional Settings screen with compact sections:

- Interaction
- TVs
- Account & License
- Privacy & Diagnostics
- About

Contextual actions may deep-link into the relevant Settings section.

Do not scatter configuration across unrelated screens or create multiple competing settings hubs.

## Failure and recovery

Failure UX follows one rule: **stay in context and show the next useful action**.

Examples:

- reconnecting TV → lightweight Remote status;
- exhausted reconnect → Remote unavailable state with **Try again** and **Switch TV**;
- discovery found nothing → Discovery empty state with **Scan again**;
- pairing timeout/denial → Pairing state with retry/cancel guidance;
- permission unavailable → LocalNetwork explanation with retry/settings path;
- backend/license outage → Account/entitlement failure state, never a TV error;
- unsupported television → honest unsupported card with Request Support when available.

Use a full-screen blocking state only when continuing the current flow is genuinely impossible.

## Control zoning and one-handed reach

Portrait phone is the primary reference.

The everyday control surface is **thumb-first**:

- current TV, lightweight status, TV switch, Settings, and power occupy the upper region;
- power is spatially isolated from high-frequency controls to reduce accidental activation;
- the primary navigation surface occupies the central/lower thumb zone;
- high-frequency Back, Home, Mute, Volume, and Channel controls remain comfortably reachable around or below that zone;
- favourite shortcuts must not push the navigation surface out of comfortable one-handed reach.

Do not preserve a traditional physical-remote ordering when doing so harms phone ergonomics.

Main remote controls should generally exceed the minimum touch-target size where screen space allows.

## Remembered television presentation

Ordinary TV cards/list rows show only consumer-relevant information:

- friendly name as the dominant label;
- ordinary-language state such as **Ready**, **Connecting**, **Offline**, or **Needs pairing**;
- subtle current/last-used indication where useful;
- optional television/manufacturer icon.

Model/firmware details may exist in a secondary management/details surface when useful for support.

Never show IP address, port, UUID, MAC, protocol generation, certificate information, or pairing token in ordinary TV lists.

## Favourites, apps, and secondary controls

Remote may show a compact favourite shelf close to, but not at the expense of, the primary navigation zone.

The shelf contains a deliberately small immediately visible set of:

- favourite launchable apps;
- favourite secondary controls.

A **More** action opens the fuller supported apps/controls surface.

Applications appear only when live capability evidence says they are launchable.

Reordering is an explicit editing interaction: long-press or an **Edit** action may enter reorder mode, then drag changes order. Ordinary Remote interaction does not treat accidental drags as customization.

Installed/supported but non-favourited applications remain available from the fuller secondary surface rather than all being placed on Remote.

## Gestures

Gestures are accelerators for visible capabilities, never the sole route to an action.

Allowed examples:

- tap/swipe inside explicit Touchpad mode;
- long-press when a control visibly supports a hold command;
- drag while explicit Edit mode is active;
- normal Android system back gesture.

Do not use:

- horizontal swipe to switch televisions;
- secret edge gestures;
- shake actions;
- gesture-only settings or destructive actions.

Accessibility alternatives must exist for every gesture-based operation.

## Responsive behavior

Portrait phone is the primary design reference, but V1 must behave correctly in:

- phone landscape;
- foldable/windowed layouts;
- tablets and other wider Android windows.

Do not lock the Remote to portrait.

Do not create a separate tablet product architecture for V1.

On wider layouts:

- preserve sensible control dimensions rather than stretching controls to fill width;
- use added width for spacing or secondary two-column content when helpful;
- keep primary navigation and high-frequency controls visually dominant and reachable;
- preserve the same semantic control ordering across size classes where practical.

Rotation/window-size changes preserve the current Active Remote, selected television, navigation mode, edit state where safe, and other transient interaction state rather than reopening/pairing the TV.

## Accessibility and visual-system floor

These are architecture requirements for every shipped product surface:

- interactive targets are at least **48dp**; primary Remote controls should be larger where practical;
- Android font scaling must not clip, overlap, hide, or make essential controls unreachable;
- every interactive element has meaningful TalkBack naming, role, state, and action semantics;
- connection, pairing, and recovery status changes are announced without creating repeated speech interruption;
- color is never the only carrier of state or meaning;
- text/control contrast remains strong in dark and light/system appearances;
- haptics supplement visible/semantic feedback and are never the only confirmation;
- motion respects the relevant Android/system reduced-motion setting or removes nonessential motion when such a preference is active;
- mascot/decorative animation is never required to understand a state or complete a flow and is exposed appropriately as decorative or meaningfully described;
- destructive actions such as **Forget this TV**, sign out, and account deletion are visually distinct and receive confirmation appropriate to their consequence;
- Power remains spatially isolated from dense/high-frequency controls;
- focus/semantic traversal follows the visual/task order rather than layout implementation accidents.

## UI/UX decision status

The known human UI/UX decision frontier is closed.

Technical architecture still needs to turn these decisions into:

- explicit screen and UI-state contracts;
- navigation/back-stack/process-restoration behavior;
- design tokens and responsive layout rules;
- Compose state ownership and ViewModel seams;
- accessibility test contracts;
- end-to-end interaction/state diagrams.

Those are technical architecture tasks, not invitations to reopen the product decisions above unless a real contradiction is discovered.

Implementation remains unauthorized until the remaining technical architecture is completed, reviewed, and the human explicitly exits architecture.

