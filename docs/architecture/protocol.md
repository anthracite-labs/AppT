# Samsung protocol knowledge

This file is for implementers inside `samsung`. `app` does not import these names, ports, or payloads. The external **interface** is [samsung-interface.md](samsung-interface.md).

Reference libraries, including `samsung-tv-ws-api` (LGPL-3.0) and Home Assistant's Samsung integration, are knowledge sources only. Do not copy their source. Reimplement from this specification and from redacted fixtures. That clean-room rule is the retained harvest screener applied to an incompatible-license reference. It is not a decision about AppT's license.

## Adopted generation

V1 commands only the Tizen remote-control WebSocket channel.

| Generation | What V1 does |
|---|---|
| WebSocket TLS, port 8002, token when required | Adopted control path. Preferred when the television demonstrates it. |
| WebSocket plaintext, port 8001 | Adopted when 8002 does not speak this channel. |
| Legacy encrypted TCP, commonly port 55000 | Identify only. No functional commands. Card may be `Unsupported`. |
| Encrypted PIN / session handshake, commonly port 8000 | Identify only. No functional commands. Not silently promoted from research. |
| Consumer IP Control, ports 1515/1516 | Not probed for control. Not a power path. |
| SmartThings cloud | Not used. It would put control or power on a cloud account. |
| Art mode, voice, DIAL launch, casting | Out of V1. |

Generation choice uses evidence from the television: which of ports 8001 and 8002 speak device-info or the remote channel, and whether token auth is required. It does not use a model-year table. A year label may be written later on a physical-matrix row after a television is observed. The label does not select the handshake.

`Unsupported` means identity confirmed a Samsung television and neither adopted channel completed a handshake. Probes may open a short connection to 8001/8002. They must not send key, text, or launch frames to a non-adopted generation.

## Endpoints

Device-info, tried for candidates only:

```text
http://<host>:8001/api/v2/
https://<host>:8002/api/v2/
```

Remote channel:

```text
wss://<host>:8002/api/v2/channels/samsung.remote.control?name=<base64>&token=<token>
ws://<host>:8001/api/v2/channels/samsung.remote.control?name=<base64>
```

`name` is standard Base64, no newlines, of the UTF-8 string `AppT`. Omit `token` until a pin check has passed and a token exists. URL-encode query values. Never log the URL.

One `OkHttpClient`. No body or header logging interceptor. Connect timeout 5 seconds. Read limits below.

## Device-info handling

The raw document is not logged and not stored. Parse with a bounded reader (64 KiB). Tolerate `isSupport` as either an object or a stringified JSON object. Ignore unknown fields.

Retain internally, in the samsung-private device record:

| Field | Use |
|---|---|
| `id`, `duid`, `udn` | UUID normalization for `TvId` |
| `name` | Display proposal |
| `type` | Television versus soundbar filter |
| `OS` | Non-Tizen explicit OS supports `Unsupported` |
| `TokenAuthSupport` | Handshake preference, not a button |
| `modelName`, `firmwareVersion` | Export only, never capability inference |
| `wifiMac` | Wake, only if it parses as a MAC. The value `none` is absent. |
| `remote_available`, `remote_touchPad`, `ImeSyncedSupport` | Probe order only |

Discard immediately: `ip`, `ssid`, `developerIP`, `countryCode`, and any field not in the retain list. The `ssid` field is not trustworthy even as a Wi-Fi name; observed devices have put a MAC there. Do not special-case it into storage.

## Session frames

Outbound key tap:

```json
{"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"<internal-key>","Option":"false","TypeOfRemote":"SendRemoteKey"}}
```

`Hold` sends `Press`, waits the clamped duration, then `Release`, including when the command coroutine is cancelled. `DataOfCmd` is the internal key name. The map from `RemoteKey` to that name stays in one table inside `samsung`. `Power` tries the power key the fixture marks as primary for that channel and may try one alternate power key once if the first is rejected. Callers still send `RemoteKey.Power`.

Text:

```json
{"method":"ms.remote.control","params":{"Cmd":"<base64-text>","DataOfCmd":"base64","Option":"false","TypeOfRemote":"SendInputString"}}
```

Pointer shapes commonly observed use `TypeOfRemote` `ProcessMouseDevice` and `Cmd` `Move` or `Click`. Lock the exact body to a redacted fixture before enabling `pointer`. If the fixture is absent, the probe does not run and `pointer` stays false. Do not invent a second pointer encoding in `app`.

App list request:

```json
{"method":"ms.channel.emit","params":{"event":"ed.installedApp.get","to":"host"}}
```

Launch prefers the WebSocket launch method the fixture shows the television accepts, with `ed.apps.launch` as the documented fallback. Do not use `POST /api/v2/applications/{id}` as the V1 launch path; on many later Tizen sets it is a no-op that still looks successful. Parser for the list is fixture-locked, ignores unknown fields, and caps the list at 200. A missing or malformed list means `apps` is false. It is not a crash.

Inbound events that drive the session machine:

| Event | Effect |
|---|---|
| `ms.channel.connect` | Trusted session. Read token from `data.token`, else from the client attributes. Persist if new. |
| `ms.channel.unauthorized` | State-dependent. See [connection.md](connection.md). |
| `ms.channel.timeOut` | Approval timeout if waiting for approval. Connection loss if `Ready`. |
| `ms.channel.clientDisconnect` | Connection loss. |

Other events are ignored unless a fixture assigns them a role. IME focus event names are not hardcoded until a fixture names them.

## Parser limits

| Limit | Value |
|---|---|
| Frame size | 64 KiB |
| Device-info body | 64 KiB |
| App list | 200 |
| Text | 256 characters |
| Friendly name retained from the television | 40 characters after trim |
| JSON depth | 8 |

Over-limit input is a malformed frame, not a crash. Strings are not concatenated unbounded. Unknown methods are ignored. The parser does not evaluate URLs from the television, does not follow redirects off the candidate host, and does not accept a device-info host that does not match the candidate it probed.

## TLS

Custom trust check as specified in [connection.md](connection.md): SPKI SHA-256, candidate pin only before the first successful approval, saved pin compared before the token is attached. No global certificate bypass. TLS 1.2 minimum.

Keepalive ping interval on the live socket is 20 seconds. A failed keepalive is connection loss, then supervised reconnect.

## Wake packet

Standard magic packet for the stored MAC, three transmissions, UDP port 9, subnet broadcast. No cloud wake. No SmartThings power-on.

## Logging from this layer

Log event names and failure enums through the internal diagnostic log. Do not log URLs, frames, device-info bodies, tokens, or text. The diagnostic log redacts anyway. Structural avoidance is the primary rule; redaction is the backstop. See [diagnostics.md](diagnostics.md).
