# Commands and capabilities

`app` sends `TvCommand` values and reads `TvCapabilities` from the session snapshot. It does not send wire strings. Translation lives in [protocol.md](protocol.md).

The connected television is the source of truth. Model and year tables do not unlock controls. Device-info flags order internal probes. They do not, by themselves, show a button.

## Evidence

| Control | Shown when | Hidden when |
|---|---|---|
| Standard keys on the remote channel: direction, enter, back, home, volume, mute | Session has reached `Ready` on the adopted channel at least once for this pairing. `Ready` is evidence the channel accepts remote commands. | A later `Rejected` for that key removes it from `capabilities.keys` and it stays removed until a later accepted use. |
| Power off | `Power` remains in `keys` under the same channel rule. | Rejected power-off removes it. |
| Power on | `powerOn` is `Attemptable` when a MAC is stored and repeated wake failure has not been recorded. Product allows a wake attempt before model-specific proof. | After 3 distinct failed wake attempts, `powerOn` becomes `Unavailable`. A later `Sent` followed by a session that reaches `Ready` clears the failure count. |
| Pointer | A quiet internal probe was accepted, or a user `PointerMove` / `PointerClick` was accepted. | Probe rejection or command rejection. Not shown while the probe result is unknown. |
| Text | `textInput` becomes true when `Ready` first lands, because text rides the same adopted channel. | An `InsertText` rejection sets `textInput` false for this pairing. |
| Apps | The television returned a list on this pairing. | Empty or malformed list. `launchableApps` contains only returned apps. |
| Channel, source, media, color, digits | Same channel evidence as standard keys, but `app` places them on a secondary surface. | Per-key rejection removes that key. |

Unknown is not the same as knowingly dead. The first `Ready` may show the standard key channel. A control that has been rejected is knowingly unavailable and is not shown.

Device-info `remote_touchPad`, `ImeSyncedSupport`, and `remote_available` may choose probe order. They must not set `pointer`, `textInput`, or `keys` on their own.

`app` renders only keys present in `capabilities.keys`, pointer only when `pointer` is true, text only when `textInput` is true, and apps only from `launchableApps`. It does not keep a parallel model table.

## Power honesty

When the session is `Unreachable` and `powerOn` is `Attemptable`, the power control calls `wake` and then `open`. It does not send `Tap(Power)` into a dead session.

When `powerOn` is `Unavailable`, there is no power-on control. The screen explains that this television cannot be turned on from the phone and the physical power control is required. That explanation is not a button that still sends a packet.

When `Ready`, power uses `Tap(Power)` or `Hold` as the UI defines. Discrete HDMI selection is not a V1 command. `Source` opens the television's own source UI when that key is in the set.

## Typed commands

`RemoteKey` is the closed V1 set in [samsung-interface.md](samsung-interface.md). Factory, service, and panel-test keys are not in the set and are not sent.

`WellKnownApp` is a display hint. `samsung` sets it only when the live app list contains an id the internal table maps to that hint. A missing table entry still shows the television's app name with `wellKnown = null`. A table entry that is not in the live list is not a shortcut. Curated Netflix, YouTube, and similar shortcuts therefore appear only when that television returned the app.

The id table is an internal artifact seeded from redacted fixtures and extended by evidence. It is not a support matrix and not a year range.

`LaunchApp` with an id not in the current `launchableApps` returns `Rejected(Unavailable)` and sends nothing.

## Text

`InsertText` sends the whole string or nothing. Limit is 256 characters. User text is not logged, not snapshotted, and not copied into diagnostics.

Text entry uses the phone keyboard. The affordance is shown only when `textInput` is true. Streaming-app screens that draw their own keyboards may ignore inserted text. If the television rejects the command, hide the affordance. Do not invent a second text protocol in `app`.

Focus events from the television are an internal optimization. `samsung` may use a fixture-confirmed focus event to raise the keyboard without a extra tap. Until a redacted fixture names that event, do not hardcode an event name. The capability rule above is sufficient for V1.

## Pointer and navigation choice

When `pointer` is true, `app` offers both directional keys and touchpad. The choice is the device-local Interaction Preference `NavigationMode`, default `Directional`. See [data.md](data.md) and [presentation.md](presentation.md). When `pointer` is false, the toggle is absent and directional keys are used.

`app` samples the touchpad and sends `PointerMove` and `PointerClick`. Gesture recognition stays in `app`. Wire mouse frames stay in `samsung`.

## Phone volume buttons and haptics

These are `app` behavior. `samsung` does not read phone keys.

While the remote surface is started, `volumeButtonsControlTv` is true (the default), and volume keys are in `capabilities.keys`, `app` consumes the phone volume keys and sends `Tap(VolumeUp)` or `Tap(VolumeDown)`. Otherwise the phone handles its own volume.

Haptics default on, subtle, and can be disabled. The preference is `hapticsEnabled`. Haptics fire on accepted local press, not after a round trip, so a cloud or television delay cannot lag the tactile response. Haptics are skipped when the user setting is off or the system asks for reduced touch feedback.

Both preferences are device-wide and device-local. They are never account data and never leave the phone. See [data.md](data.md).

## What V1 does not command

Voice, casting, mirroring, art mode, discrete HDMI, IR, and channel-entry numeric sequences beyond individual digit keys are not V1 commands. Digit keys exist so a secondary surface can type a channel number as separate taps. There is no `TuneToChannel` operation. Adding one is an interface change and waits for a product need.

## UI obligations

- Everyday controls stay prominent: direction, enter, back, home, volume, mute, and power when honest.
- Secondary keys stay behind a secondary surface.
- One-handed use, screen-reader labels, scalable text, strong contrast, and at least 48dp targets are `app` requirements on every control this map shows. See [slices.md](slices.md).
- Do not show a control merely because a sibling model had it.
- Do not show IP addresses, port numbers, or key strings on the remote.
