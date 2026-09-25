# `:samsung`

The deep production module that owns Samsung television control: discovery,
identity correlation, pairing, security identity, session, reconnect,
capability evidence, command translation, wake, and secret storage
(`docs/architecture/modules.md#samsung-control-samsung`).

## State in S02

S02 (Issue #73) makes discovery real. The public surface is the `SamsungTvs`
seam in `dev.anthracite.appt.samsung` with exactly one operation so far,
`discover()`, plus the value types it emits (`TvId`, `DiscoveredTv`,
`ControlAvailability`, `DiscoveryEvent`, `TvFailure`). `SamsungModule` hands
`:app` the production adapter; everything else lives in
`dev.anthracite.appt.samsung.internal`, which `:app` never imports.

| Arrives in | What |
|---|---|
| S02 | `SamsungTvs.discover()`, discovery transport, device-info confirmation |
| S03 | `open`, `command`, the session transport and protocol (incl. port 8002/TLS) |
| S04 | Secret store, identity checking, `forget` |
| S12 | Wake |

### Discovery, in one paragraph

Collecting `discover()` starts one scan bounded at 10 seconds, which ends with
`Finished`. A second collection cancels the first. The scan binds to the active
non-VPN Wi-Fi or Ethernet network, holds a Wi-Fi multicast lock only while it
runs, and sends exactly three probes: an SSDP M-SEARCH for
`urn:samsung.com:device:RemoteControlReceiver:1`, an SSDP M-SEARCH for
`urn:dial-multiscreen-org:service:dial:1` (kept only when the reply identifies
Samsung), and an `NsdManager` browse of `_airplay._tcp` (kept only when the
manufacturer is Samsung). Each candidate is confirmed by reading bounded
(64 KiB) device-info from `/api/v2/` on port 8001 of the advertised host before
it is `Found`; non-TVs, unreadable device-info, and anything off the LAN are
dropped. Identity is the normalized device UUID, or a device-local minted id
marked `stableIdentity = false`. No token, key, text, or launch frame is ever
sent during discovery.

### Seams inside the module

`DiscoveryScan` holds the policy (bound, dedup, filtering, identity) and talks to
`DiscoveryTransport`. `AndroidDiscoveryTransport` is the production adapter
(raw UDP sockets, `NsdManager`, `ConnectivityManager`, `WifiManager`);
`FixtureTransport` in the tests replays the committed fixtures under
`src/test/resources/samsung/fixtures/<case-id>/`, each with a
`provenance.json`. The S02 fixtures are synthetic (see each `provenance.json`).

## Boundary

Enforced by `samsungDependencyBoundary` and `noLogInSamsungSource` (see
`gradle/guards.gradle.kts`):

- no Firebase, Play services, Play Billing, Play Integrity or telemetry SDK;
- no dependency on `:app`;
- no direct `android.util.Log` call in production source, so a token can never
  reach logcat by accident. Raw device-info is never logged or persisted.
