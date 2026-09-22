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

Purpose: `A universal TV remote for ordinary consumers, beginning with Samsung Smart TVs and expanding across television ecosystems behind one consistent phone-native experience.`

Phase: `discovery`

## Current objective

Objective: `Determine the V1 application architecture and stack, beginning with React Native / Expo feasibility against AppT's Samsung-control, local-first, security, account-sync, and cross-platform requirements.`

Success condition: `An evidence-backed architecture decision identifies whether React Native / Expo can deliver the required Android and iPhone experience without compromising reliable Samsung control; if not, an alternative direction is selected with the trade-offs recorded.`

## Active work

Primary: `Application architecture and stack discovery`

Secondary:

- Samsung control integration constraints relevant to stack selection.
- Account/sync boundary design, preserving device-local pairing credentials and offline local control.
- Focused Samsung vendor-terms/legal review remains a pre-release gate, not an architecture blocker.

## Current decisions

- `AppT is a Universal TV Remote for ordinary consumers — docs/PRODUCT.md`
- `Samsung Smart TVs are the first intentionally targeted ecosystem; other TVs may be experimentally probed with explicit user consent before functional commands — docs/PRODUCT.md`
- `Android and iPhone are V1 targets when architecture can support both without compromising reliability; Android-first is an acceptable fallback — docs/PRODUCT.md`
- `React Native / Expo is a candidate to research, not an accepted stack — docs/PRODUCT.md`
- `TV control is local-first and continues when AppT services or internet access are unavailable — docs/PRODUCT.md`
- `An account is part of the product, but first successful local control is not gated by sign-in; account sync excludes pairing secrets — docs/PRODUCT.md`
- `V1 is free, with no advertising, behavioral usage analytics, or required paid tier/subscription — docs/PRODUCT.md`

## Blockers / Unknowns

- Whether React Native / Expo can satisfy Samsung discovery/control, TLS/security, secure credential storage, app lifecycle, and cross-platform requirements without unacceptable native complexity.
- Exact Samsung protocol/device-generation behavior that AppT will support in V1.
- Final account/backend architecture and synchronization model.
- Focused Samsung vendor-terms/legal review must be completed before public release.

## Recent change

- `Product definition approved and recorded in docs/PRODUCT.md; project advanced from setup to discovery.`

## Relevant canonical references

- `docs/PRODUCT.md — approved AppT product definition and product constraints.`
- `.agents/CAPABILITIES.md — routing for planning, architecture, implementation, and review work.`
- `AGENTS.md — repository operating entry point.`
- `anthracite-labs/Greenfield4/docs/PRODUCT.md and docs/research/ — reference evidence only; not AppT requirements unless adopted in docs/PRODUCT.md.`

## Next

`Research the V1 stack, starting with React Native / Expo feasibility against the approved product constraints and Samsung-first control requirements.`

## After that

1. `Record the architecture/stack decision and move the project into architecture when the discovery evidence is sufficient.`
2. `Design the Samsung integration boundary and account/local-data seams from the accepted product and architecture decisions.`
3. `Formalize decided implementation work for dispatch only after those boundaries are settled.`
