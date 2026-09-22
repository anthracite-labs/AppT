# Harvest / Adopt / Reject Register

**Status: architecture input.**

This register translates the attached `harvest-matrix-remote` research package into current AppT architecture decisions.

The harvested package is evidence and pattern input, not current product truth. Where it conflicts with `docs/PRODUCT.md` or `docs/ARCHITECTURE.md`, the repository documents win.

## Status meanings

- **ADOPT** — binding for the current AppT V1 architecture.
- **HARVEST** — retain as a useful pattern, protocol reference, test source, or future option; it is not a current V1 commitment.
- **REJECT** — deliberately excluded or superseded by an AppT decision.

## Register

| Harvested idea | AppT status | Current disposition |
|---|---|---|
| Samsung Tizen WebSocket control with TV-side approval/token | **ADOPT** | Samsung is the first intentionally targeted ecosystem. Reimplement protocol behavior inside the deep `samsung` module; persist secrets with Keystore-backed protection. |
| SSDP / mDNS / native LAN discovery | **ADOPT** | Use bounded discovery behind the application local-network permission gate. Probe set and identity rules are in `docs/architecture/discovery.md`. |
| Wake-on-LAN for TVs where appropriate | **ADOPT** | Generic Samsung network wake may be attempted where appropriate; capability/result determines UX. |
| Bounded, visible discovery rather than continuous background scanning | **ADOPT** | First-run auto-discovery plus explicit rescans/rediscovery; no continuous discovery telemetry. |
| Capability-driven remote UI | **ADOPT** | Connected TV is the source of truth; knowingly unavailable controls are not shown. |
| Typed/canonical commands instead of raw wire payloads in UI | **ADOPT** | App code uses typed Samsung commands. Raw Samsung keys/payloads remain internal. |
| Deep per-protocol implementation with recorded protocol fixtures | **ADOPT** | Samsung protocol complexity stays local and is contract-tested with recorded/redacted fixtures. |
| Keystore-backed storage for TV pairing material | **ADOPT** | Pairing credentials/tokens/private keys remain device-local and protected. |
| TOFU/visible pairing confirmation and fail-closed identity change | **ADOPT** | Use TV-side confirmation where available; persistent identity changes require re-pairing where identity can be established. |
| No unnecessary listening server ports in V1 | **ADOPT** | AppT's remote-control path uses client-side networking. |
| Defensive protocol parsing and supervised failure handling | **ADOPT** | Malformed/failed TV traffic must not crash callers or UI. |
| No ads / no behavioral analytics | **ADOPT** | Matches `docs/PRODUCT.md`. |
| Redacted diagnostics and explicit local diagnostic export | **ADOPT** | Retained, but remote crash implementation differs from the harvested package. |
| Reproducible/locked dependency and provenance discipline | **ADOPT** | Dependency locking, verification, controlled repositories, reviewed updates, and immutable CI action pins where practical. |
| Screen mirroring omitted from core V1 | **ADOPT** | Mirroring/casting must not distort or delay the core remote. If later added, it requires an authenticated/secured design. |
| IR capability honesty: never expose functionality hardware cannot perform | **HARVEST** | Strong future principle if IR enters product scope. IR is not part of the current Samsung-first V1 architecture. |
| `ConsumerIrManager`, Flipper-IRDB, IR codecs/import/learn ecosystem | **HARVEST** | Retain as future universal-remote research, not current V1 scope. |
| Roku ECP reference | **HARVEST** | Candidate ecosystem/reference for later expansion; not an initial implementation target. |
| LG webOS SSAP reference | **HARVEST** | Candidate ecosystem/reference for later expansion. |
| Android/Google TV Remote v2 reference | **HARVEST** | Candidate ecosystem/reference for later expansion. |
| Sony IRCC reference | **HARVEST** | Candidate ecosystem/reference for later expansion. |
| Fire TV via network ADB | **HARVEST** | Potential advanced/future path; user-friction/security implications require a later product decision. |
| Chromecast / CASTV2 / DLNA casting | **HARVEST** | Casting may ship later but is not a launch blocker. |
| ScreenStream-style authenticated mirroring | **HARVEST** | Retain only as a future secure pattern if mirroring is deliberately added. |
| Public device-support matrix and adapter health/status views | **HARVEST** | Not a V1 product surface. Internal physical-matrix notes live in `docs/architecture/testing.md` and do not promote this idea. |
| Apache-2.0 clean-room reimplementation of protocol knowledge | **HARVEST** | Sensible licensing/provenance pattern, but AppT's final source license is not decided here. Do not copy incompatible reference code. |
| One universal `TvAdapter` interface now | **REJECT** | Premature with one ecosystem. Build Samsung cleanly; design the universal seam when ecosystem #2 exists. |
| One Gradle module per future TV brand from day one | **REJECT** | Start with lean `app` + `samsung`; add modules when real implementations exist. |
| Full Clean Architecture layer/module tree from day one | **REJECT** | Avoid speculative `domain/data/usecase/repository` layering without demonstrated pressure. |
| Android + iOS simultaneous V1 implementation | **REJECT** | Current delivery decision is native Android first; iOS is deferred and will be designed natively later. |
| Fully local app with no backend/account/cloud sync | **REJECT** | Superseded by `docs/PRODUCT.md`: AppT has an account and non-secret sync, while TV control remains local-first. |
| Cloud sync of TV pairing credentials | **REJECT** | Pairing secrets remain local to each phone. |
| File export/import as the replacement for account sync | **REJECT** | May be useful later, but it does not replace the accepted account/sync product model. |
| One-time premium IAP + donations as V1 monetization | **REJECT** | Superseded by product decision: V1 launches free with no required paid tier/subscription. |
| Advertising SDKs | **REJECT** | Explicitly outside V1 product model. |
| ACRA + self-hosted opt-in crash reporting as the chosen implementation | **REJECT** | Current architecture chooses Crashlytics without behavioral Analytics plus redacted local diagnostics/export. The harvested privacy/redaction principles still apply. |
| Firebase Crashlytics categorically disallowed | **REJECT** | Superseded by the accepted diagnostics decision; Crashlytics is permitted under strict data-minimization/redaction rules. |
| F-Droid/GitHub distribution as a committed V1 channel | **HARVEST** | Reproducibility is adopted; additional distribution channels are not committed by current product/architecture decisions. |
| Proprietary control-path blobs such as Whisperlink | **REJECT** | Avoid opaque/proprietary dependencies in the TV-control path when an open/documented implementation path is available. |
| Open/unauthenticated MJPEG server or equivalent mirroring listener | **REJECT** | Security anti-pattern; not part of V1. |
| Per-brand UI reskins as the primary architecture | **REJECT** | Prefer capability-driven phone-native UI. Brand-specific UX is only justified by real protocol/product needs. |

## Translation of the harvested ADRs

The attached package included nine ADRs written for an earlier product concept. Their current AppT disposition is:

| Harvested ADR | AppT disposition |
|---|---|
| ADR-0001 no backend/cloud/accounts | **REJECT**, while **ADOPTING** its local-control independence principle |
| ADR-0002 Apache-2.0 core / clean-room protocol ports | **HARVEST** pending a separate AppT licensing decision |
| ADR-0003 universal `TvAdapter` now | **REJECT** as premature; **ADOPT** a deep Samsung module now |
| ADR-0004 IR honesty | **HARVEST** for future IR scope |
| ADR-0005 Flipper-IRDB/import/learn | **HARVEST** for future IR scope |
| ADR-0006 pairing/secrets/Keystore | **ADOPT** |
| ADR-0007 one-time premium monetization | **REJECT** for V1 |
| ADR-0008 omit mirroring from V1; secure it if later | **ADOPT** |
| ADR-0009 ACRA/self-hosted crash reporting | **REJECT** as implementation choice; retain its data-minimization intent |

## Screening principles retained

The research package's screener is retained as a dependency/protocol evaluation mindset:

- prefer maintained, auditable implementations and documented/open protocols;
- treat incompatible-license projects as knowledge/reference sources unless compliance is deliberately designed;
- reject embedded credentials, tracker/ad SDKs, unnecessary listeners, and opaque proprietary control-path blobs;
- prefer dependencies with low exit cost and narrow privilege/network behavior;
- verify manifest permissions and dependency changes in CI;
- make product claims match demonstrated runtime capability.

These principles inform architecture and dependency review; they do not override explicit AppT product or architecture decisions.
