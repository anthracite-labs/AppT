# `:samsung`

The deep production module that owns Samsung television control: discovery,
identity correlation, pairing, security identity, session, reconnect,
capability evidence, command translation, wake, and secret storage
(`docs/architecture/modules.md#samsung-control-samsung`).

## State in S01

S01 is the walking skeleton. This module exists so the dependency boundary is
real and enforced from the first slice; it deliberately contains **no**
production source yet. `SamsungTvs` and its internal seams arrive in the slices
that make them observable:

| Arrives in | What |
|---|---|
| S02 | `SamsungTvs.discover()`, discovery transport, capability evidence |
| S03 | `open`, `command`, the session transport and protocol |
| S04 | Secret store, identity checking, `forget` |
| S12 | Wake |

Creating the interface earlier would be a hypothetical seam: it would have one
adapter and no variation behind it, which
`.agents/skills/codebase-design/SKILL.md` rejects.

## Boundary

Enforced by `samsungDependencyBoundary` and `noLogInSamsungSource` (see
`gradle/guards.gradle.kts`):

- no Firebase, Play services, Play Billing, Play Integrity or telemetry SDK;
- no dependency on `:app`;
- no direct `android.util.Log` call in production source, so a token can never
  reach logcat by accident.
