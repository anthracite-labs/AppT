# Diagnostics and privacy

V1 has an always-available, already-redacted local diagnostic path and **opt-in** cloud crash reporting. It has no behavioral analytics, no diagnostic upload service, and no diagnostic data on the entitlement backend.

Owning files elsewhere: consent UI in [presentation.md](presentation.md#diagnostics), redaction tests in [testing.md](testing.md), threat model in [security.md](security.md).

## Ownership

| Piece | Owner | On the command path | Default |
|---|---|---|---|
| Redacting ring buffer | `samsung`, exposed only as `redactedDiagnostics()` | No. Enqueue and drop if full | Always on, local only |
| Share sheet, preview, export builder | `app` | No | Always available |
| Crashlytics | `app` | No. Uncaught crashes, plus non-fatals whose message is an enum name | **Off** until the user opts in |
| Firebase Analytics, ads, attribution, install-referrer | Not in the dependency graph | — | Absent |

`samsung` does not depend on Crashlytics. `app` may record a non-fatal whose message is a `SessionState` or `TvFailure` name. It never records television names, `TvId` values, command text, account identifiers, or addresses.

## Crash reporting consent

Cloud crash reporting is a user decision, not a default.

| Aspect | Architecture |
|---|---|
| Initial state | Collection disabled. The Android manifest sets the Crashlytics meta-data flag (`firebase_crashlytics_collection_enabled`) to `false`, so a crash before any UI is shown is not uploaded |
| Enabling | Only from the Diagnostics surface, by explicit user action. The user sees what would be sent before confirming |
| Runtime override | The provider override is written only in response to that decision. Its persistence semantics are provider behaviour: see [README.md](README.md#needs-validation) |
| Reports stored while disabled | The provider may keep reports on the device while collection is disabled. The Diagnostics surface offers two explicit user actions: send stored reports, or delete stored reports |
| Disabling | Disables upload from the next launch, per provider semantics. The user may also delete locally stored reports immediately |
| Data already uploaded | Provider-side deletion of already-processed reports is an operator support path, not a user-facing control. Recorded as needing validation |
| Crash Insights aggregation | Disabled in the Firebase console, so no aggregated sharing occurs beyond the crash reports the user opted into |
| Sign-out, account deletion | Do not change the crash-reporting consent state. Consent is device-level and belongs to the person using the phone |
| Uninstall | Removes local reports with the app |

Rules that hold regardless of consent state:

- Never call `setUserId`.
- Never set a custom key for account, television, address, network, or install identity.
- Never attach a Username, email, uid, `TvId`, friendly name, model-plus-address combination, or a persistent installation identifier.
- Never add `firebase-analytics` or an advertising SDK. A manifest check fails if `AD_ID` appears.
- Crash reporting is not a diagnostic correctness requirement: an outage or a disabled SDK never affects control.

## Local diagnostic buffer

- In memory, capped at 200 events, oldest dropped. Optionally a rolling file under `noBackupFilesDir` so a crash dump of backups cannot export the buffer. The file content is redacted at write time.
- The event API inside `samsung` takes an event name and an allowlist of fields. It does not take a `TvCommand`, a URL, or a free-form message built from a payload. Free-form strings that must exist go through the redactor first.
- `command` never awaits the buffer. If the buffer is full, the event is dropped.
- Retention: the buffer is bounded, in memory, and never leaves the phone except through the explicit export path. There is no upload, no queue, and no backend.

## Export and Request Support

Request Support and the settings export use the same path. It is a diagnostic share, not an account export, and importing it restores nothing.

1. The user asks to export.
2. `app` builds a report from `redactedDiagnostics()` plus app version and Android API level.
3. A preview lists the fields that will leave the phone.
4. The user confirms; the share sheet sends the file. V1 does not post it to an AppT service.

Default export may include television model and firmware, because support needs them and they are not unique pairing material. It does not include friendly names, `TvId`, account identifiers, email, addresses, or an installation identifier. The user can describe the room themselves.

There is no automatic export on pairing failure. The unsupported card gains this action in the diagnostics slice; before that slice the card states that control is unavailable and shows no dead button.

## Redaction

Single redactor, used by the `samsung` log wrapper, the buffer, and the export builder. `app` log lines that might touch television or account data use the same function.

Apply in order:

1. Replace any URL with `[url]`.
2. Replace IPv4 and IPv6 with `[ip]`.
3. Replace MAC addresses with `[mac]`.
4. Replace email addresses with `[account]`.
5. Replace UUID-shaped strings with `[id]`.
6. Replace any currently stored token, pin, or entitlement proof value with `[redacted]`, via a supplier that is not itself logged.
7. Drop fields that are not on the export allowlist.

Allowlist for export and Crashlytics-adjacent logs: event name, elapsed time, `SessionState` name, `TvFailure` name, `RepairReason` name, capability flag names, `model`, `firmware`, app version, API level.

### Forbidden everywhere

These names must not appear as Room columns, DataStore keys, entitlement-cache fields, log fields, Crashlytics keys, export fields, or backend fields:

```text
token, pairingToken, secret, pin, password, certificate, spki, proof,
mac, wifiMac, ip, address, host, ssid, wifi, command, commandText, text
```

### Present on the phone, never in a crash report or export

```text
tvId, friendlyName, uid, email, username
```

`model` and `firmware` are the only television-identifying values that may leave the phone, and only through a user-confirmed export.

Tests lock the lists. A fixture token planted in a frame must be absent from `redactedDiagnostics()` and from any log line the test captures.

## Logcat

`samsung` production source does not call `android.util.Log` directly. CI fails the slice if it does. Release builds do not attach an OkHttp logging interceptor. Debug builds may log event names through the redactor; they still must not log URLs, and they must never upload crash reports unless the developer opts in.

## Latency and coupling

The control path's only contact with diagnostics is a non-suspending offer to a bounded queue. No disk flush, no Crashlytics call, and no share-sheet work run inside `command` or the socket read loop.

## Dependency and CI checks

| Check | Protects |
|---|---|
| `firebase-analytics` absent from the dependency graph | No behavioral analytics |
| `AD_ID` absent from the merged manifest | No advertising identifier |
| `setUserId` absent from production sources | No account identifier in crash data |
| `samsung` graph excludes Crashlytics | Diagnostics never on the control path |
| Fixture redaction grep | No secrets or addresses in committed fixtures |
| No `Log` in `samsung` | Tokens do not reach logcat by accident |
| Release variant cannot resolve development configuration | No accidental test data in production crash reports |
