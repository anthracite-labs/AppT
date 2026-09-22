# Discovery and identity

Caller-facing scan behavior is `SamsungTvs.discover()` in [samsung-interface.md](samsung-interface.md). This file is the behavior behind that operation, plus the application permission gate.

## Permission gate

One gate in `app` owns local-network permission policy. `samsung` does not launch permission UI.

The gate explains, in ordinary language, that the phone needs to reach the television on the home network. The explanation is shown immediately before any system permission prompt, and before the first scan even when the platform shows no prompt. Primary copy does not say SSDP, mDNS, multicast, or port.

V1 stays at `minSdk` 29 and `targetSdk` / `compileSdk` 36. Do not bump the target in order to adopt a newer permission.

| Situation | Gate behavior |
|---|---|
| `targetSdk` 36, V1 probes | After the explanation, the gate is granted and discovery may start. V1 probes are SSDP, raw sockets, `NsdManager`, and a multicast lock. Those probes do not require `NEARBY_WIFI_DEVICES` or a location permission. Do not request either. Do not declare `NEARBY_WIFI_DEVICES` for them. |
| A chosen Android Wi-Fi API that requires a runtime permission | Request that permission after the same explanation, and only then. Request `NEARBY_WIFI_DEVICES` only in that case, with `neverForLocation`. V1 discovery does not choose such an API. |
| `targetSdk` 37 or newer | Not V1. Before that bump, this same gate must request `ACCESS_LOCAL_NETWORK` for broad LAN access. Do not use the system service picker. It cannot serve a multi-television scan plus quiet reconnect to remembered televisions. Do not declare or request `ACCESS_LOCAL_NETWORK` while `targetSdk` is 36. |

Android 16 local-network protection is transitional and opt-in at target 36. It is not a reason to require `NEARBY_WIFI_DEVICES` on API 33–36, and it is not a reason to request `ACCESS_LOCAL_NETWORK` early. If an opted-in device blocks the V1 probes, `discover` emits `Failed(LocalNetworkDenied)` once. The gate returns to the explanation. It does not add a permission the chosen probes do not require.

Install-time permissions used by discovery: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`.

Do not declare `ACCESS_FINE_LOCATION` unless an instrumented test proves a chosen probe cannot run without it. The default is not to declare it. Do not declare `NEARBY_WIFI_DEVICES` in the V1 manifest. Do not declare `ACCESS_LOCAL_NETWORK` while `targetSdk` is 36.

Gate states: explanation required, requesting, granted, denied. `requesting` is used only when a chosen API actually shows a system prompt. At target 36, Continue on the explanation grants the gate for the V1 probes without a system prompt. Denial returns to the explanation with a way to retry. A revoke observed at the next scan returns to the explanation. Denied is not a protocol error and is not retried in a loop by `samsung`.

If sockets fail in a way that matches missing LAN permission, `discover` emits `Failed(LocalNetworkDenied)` once and stops.

## What a scan is

A scan is a foreground, bounded, user-visible probe. It is not continuous, not a background job, and not telemetry.

| Bound | Value | Owner |
|---|---|---|
| Explicit scan (`discover`) | 10 seconds from start, then `Finished` | this file |
| Internal rediscovery during reconnect | 5 seconds, no UI | [connection.md](connection.md) |

`discover` runs the full 10 seconds unless the collector is cancelled, so a late television can still appear. The user may select a card before `Finished`. Selection cancels the scan.

No scan runs in the background. No WorkManager discovery. No foreground service. The multicast lock is acquired at scan start and released in `finally`, including cancellation and failure. Internal rediscovery may hold the same lock; scans and rediscovery share one lock owner inside the module.

V1 binds discovery to the active non-VPN Wi-Fi or Ethernet network. If no such network is up, emit `Failed(LocalNetworkDenied)` or `Failed(Unreachable)` once. Do not add a VPN bypass.

## Probes

Probes are client traffic. The phone does not bind UDP 1900 as a resident listener, does not join SSDP permanently, and does not open a TCP server.

Order, all inside the bound:

1. SSDP `M-SEARCH` for `urn:samsung.com:device:RemoteControlReceiver:1`.
2. SSDP `M-SEARCH` for `urn:dial-multiscreen-org:service:dial:1`, kept only when the response already identifies Samsung.
3. `NsdManager` browse for `_airplay._tcp`, kept only when the service identifies manufacturer Samsung.

Do not send `ssdp:all`. That census collects printers, routers, and unrelated devices and is not required for the Samsung-first product. Other brands are out of V1; their protocols stay HARVEST and are not probed.

A response is a candidate, not a card. Confirm with device-info before emitting `Found`. Device-info is fetched only for candidates, on the advertised host, ports 8001 and 8002 only. No LAN port scan.

Confirmation rules:

- Device-info `type` must indicate a Samsung Smart TV. Soundbars and other Samsung speakers that share `_airplay._tcp` are dropped, not shown.
- A Blu-ray or other device that answers `RemoteControlReceiver` is dropped unless device-info confirms a television.
- If device-info cannot be read within the scan, do not emit a card. Do not substitute an address as the label.
- `Unsupported` is emitted only when identity confirms a Samsung television and the adopted WebSocket control path is not available. See [protocol.md](protocol.md).

DIAL is a discovery hint only. V1 does not launch apps through DIAL.

On API 29 through the T-extension where the platform does not manage multicast for the app, acquire `WifiManager.MulticastLock` for the scan. Release it when the scan ends. Do not hold it for the life of the process.

## Identity

`TvId` is assigned inside `samsung`.

| Source | Trust | Use |
|---|---|---|
| Protocol UUID from device-info `id` / `duid` / `udn`, after normalizing to a bare lowercase UUID | Stable correlation key when present and unchanged | `TvId` for televisions with a stable identity. The same television keeps the same `TvId` across scans, process death, and a repaired data store, because the id comes from the television rather than from this phone. |
| TLS SPKI pin | Security identity for 8002, not a scan key | Fail-closed compare at connect. See [connection.md](connection.md). |
| MAC | Wake only, and only when it is a real MAC for the interface we reached | Never a `TvId`. Wifi and Ethernet MACs may differ. |
| IP address | Untrusted. Changes. | Last-address cache inside `samsung` only. Refreshed when a confirmed identity is seen at a new address. |
| Television name | Display default only | Not an identity. User rename wins. See [data.md](data.md). |
| Model, year, firmware | Not an identity and not a capability table | Model and firmware may appear in a user-confirmed export. They never unlock controls. |

Normalization: strip a leading `uuid:`, lowercase, require a canonical UUID. That bare UUID is the `TvId` value when the television supplies one. `DiscoveredTv.stableIdentity` is true only then.

If a confirmed television exposes no stable UUID, mint a device-local `TvId` and set `stableIdentity` false. Nothing about that television leaves the phone in any case, because no television data leaves the phone, but the caller is told the id is this phone's invention: after a data repair or a reinstall the same television cannot be recognized again, so the user may need to add it once more. Do not fall back to using the MAC as an identity to make correlation look better.

`Found.remembered` is true when a samsung-private record exists for the `TvId`.

## Dedup and rediscovery

Within one scan, candidates that normalize to the same UUID are one television. Merge their addresses; keep a single card.

Across scans and process death, a television-supplied UUID maps to the same `TvId`. Two televisions that share a display name and have different UUIDs stay two cards. Do not merge on name. Do not disambiguate cards with IP addresses. Renaming after the user selects a television is the remedy for identical names.

What a later scan may update internally: last address, MAC for the interface just seen, display name proposal. What it must not update: a user-edited friendly name, a saved token, a saved SPKI pin.

If the UUID at a last-known address differs from the saved UUID, do not retarget the old `TvId` to the new television during scan. Emit the new identity as its own card if it confirms. The old television remains remembered and unreachable until reconnect policy says otherwise.

Internal rediscovery, used only by the session machine, is a unicast device-info to the last address, then a 5-second probe filtered to the saved UUID. It does not emit `DiscoveryEvent`. It does not show a scan UI.

## Wake identity

Wake-on-LAN is specified in [connection.md](connection.md) and [commands.md](commands.md). Discovery's only job is to retain a MAC when device-info or the description document provides a valid one for the interface used, and to store it in the samsung-private device record. Absence of a MAC means `powerOn` is `Unavailable` rather than a guessed broadcast.

## Trust limits

A scan does not establish security identity. A card means "this looks like that television," not "this socket is trusted." Trust for reconnect is the saved SPKI pin when TLS is used, or the saved protocol UUID when only plaintext is available. Those checks happen at `open`, not at `Found`.

Device-info is unauthenticated on port 8001. Treat it as a label and a correlation hint. Do not send a token during discovery. Do not log the raw device-info document. Field handling is in [protocol.md](protocol.md).

## Caller obligations

After the gate is granted, `app` calls `discover` immediately. Later visits offer an explicit rescan. That matches the baseline: discovery starts once local-network access is available, and the explanation sits immediately before that first scan. The product list names discovery before the permission sentence; the baseline orders explanation, then access, then the scan.

`app` shows `DiscoveredTv.name`, never an address or protocol generation. `Unsupported` cards have no command controls. They may later offer Request Support. V1 does not send experimental key commands on a non-adopted handshake. A later handshake can be added inside `samsung` and flip the card to `NeedsPairing` without an interface change.

`app` creates the Room row when the user selects a `NeedsPairing` or `ReadyToOpen` card, not for every `Found`, and not for `Unsupported`. Selection of `NeedsPairing` then calls `open`.
