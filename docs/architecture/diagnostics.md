# Diagnostics and privacy

V1 has a **bounded, always-redacted local diagnostic record** and an **explicit, user-confirmed diagnostic export**. It has no cloud crash reporting, no crash-reporting SDK, no behavioral analytics, no diagnostic upload service, and no diagnostic data on the Entitlement Backend.

**Decision recorded this round:** cloud crash reporting (Firebase Crashlytics) was in the earlier draft of this map and has been **removed from V1 entirely**. It conflicted with the settled rule that nothing leaves the phone without an explicit user action, and with the rule that no persistent installation or user identifier is created. The replacement is local-only: the record stays on the phone, and a report leaves only when the customer builds a preview and shares it.

Owning files elsewhere: the Diagnostics surface contract is in [presentation.md](presentation.md#diagnostics), redaction and privacy tests are in [testing.md](testing.md), the threat-model view is in [security.md](security.md), and the dependency rules are in [release.md](release.md) and [modules.md](modules.md).

## Ownership

| Piece | Owner | On the command path | Default |
|---|---|---|---|
| Redacting event buffer | `samsung`, exposed only as `redactedDiagnostics()` | No. Enqueue and drop if full | Always on, local only |
| App-level event record | `app` (`diagnostics`) | No | Always on, local only |
| Preview, export builder, share sheet | `app` (`diagnostics`) | No | Available on explicit user action |
| Any crash-reporting, analytics, ads, or attribution SDK | **Not in the dependency graph** | — | Absent |

`samsung` does not depend on any telemetry SDK, and `app` contains none. There is no seam here: diagnostics has one implementation and a test double rather than a production/test adapter pair, so it is not a seam in the [modules.md](modules.md#external-seams) sense.

## What the local record contains

Two bounded sources, merged chronologically at export time.

| Source | Contents | Bound |
|---|---|---|
| `samsung` (`redactedDiagnostics()`) | Event name, elapsed time, `SessionState` name, `TvFailure` name, `RepairReason` name, capability flag names, `model`, `firmware` | 200 events, oldest dropped |
| `app` (`diagnostics`) | Event name, elapsed time, enum names for gate outcome, backend status transition, licensing outcome, route name, export action | 200 events, oldest dropped |

Neither source accepts free text, a `TvCommand`, a URL, a name, a token, or a payload. An event is written through the redactor before it is stored, so the record is redacted **at write time**, not at export time.

The record is persisted to a rolling file under `noBackupFilesDir/diagnostics/v1/` so that events from a run that ended in an uncaught crash are still available to the customer afterwards. The file is bounded (200 events or 64 KB, whichever comes first) and is excluded from backup and device transfer by the same rules as every other AppT path ([data.md](data.md)).

Accepted consequence of removing cloud crash reporting: an uncaught crash is **not** reported anywhere automatically. If the customer shares a diagnostic export afterwards, that export contains only events that were recorded before the crash and redacted at write time. The human decision accepts this in exchange for never uploading anything without an explicit action.

## Latency and coupling

The control path's only contact with diagnostics is a non-suspending offer to a bounded queue:

- `command` never awaits the record, a disk flush, a share sheet, or a network client.
- A full queue drops the event. Dropping is preferred to delay.
- File writes happen on a dedicated dispatcher, never on the socket read loop.
- No diagnostic work happens inside `samsung.session`, `open`, `wake`, or `command`.

## Export and Request Support

Request Support and the Settings export use the same path. It is a diagnostic share, not an account export, and importing it restores nothing.

1. The user asks to export or to request support.
2. `app` builds a report from the two local sources plus app version and Android API level.
3. A preview lists every field that will leave the phone, including `model` and `firmware`.
4. The user confirms; the system share sheet sends the file. **V1 does not post it to an AppT service**, and the backend has no endpoint that could receive it.

Default export may include television model and firmware, because support needs them and they are not unique pairing material. It does not include friendly names, `TvId`, account identifiers, email, Username, addresses, tokens, or any installation identifier. The user can describe the room themselves.

There is no automatic export on pairing failure, and no export prompt ever appears without the user asking. The unsupported card gains the Request Support action in the diagnostics slice; before that slice the card states that control is unavailable and shows no dead button.

The Diagnostics surface also offers **Clear local history**, which deletes the buffer and the rolling file. It is the only retention control a customer needs, because nothing was ever uploaded.

## Redaction

Single redactor, used by the `samsung` log wrapper, both record sources, and the export builder.

Apply in order:

1. Replace any URL with `[url]`.
2. Replace IPv4 and IPv6 with `[ip]`.
3. Replace MAC addresses with `[mac]`.
4. Replace email addresses with `[account]`.
5. Replace UUID-shaped strings with `[id]`.
6. Replace any currently stored token, pin, or entitlement proof value with `[redacted]`, via a supplier that is not itself logged.
7. Drop fields that are not on the export allowlist.

Export allowlist: event name, elapsed time, `SessionState` name, `TvFailure` name, `RepairReason` name, capability flag names, `model`, `firmware`, app version, API level.

### Forbidden everywhere

These names must not appear as Room columns, DataStore keys, entitlement-cache fields, diagnostic-record fields, export fields, or backend fields:

```text
token, pairingToken, secret, pin, password, certificate, spki, proof,
mac, wifiMac, ip, address, host, ssid, wifi, command, commandText, text
```

### Present on the phone, never in a diagnostic record or export

```text
tvId, friendlyName, uid, email, username
```

`model` and `firmware` are the only television-identifying values that may leave the phone, and only through a user-confirmed export.

Tests lock the lists. A fixture token planted in a frame must be absent from `redactedDiagnostics()`, from the app-level record, from the rolling file, and from any log line a test captures.

## Logcat

`samsung` production source does not call `android.util.Log` directly; the app's own diagnostic calls go through the redactor. CI fails if a direct `Log` call appears in `samsung` or in the diagnostics package. Release builds do not attach an OkHttp logging interceptor. Debug builds may log event names through the redactor and still must not log URLs, payloads, names, or tokens.

## Dependency and CI checks

| Check | Protects |
|---|---|
| No crash-reporting, analytics, advertising, or attribution SDK in the merged dependency graph | Nothing is uploaded automatically, and no provider installs an identifier |
| `AD_ID` absent from the merged manifest | No advertising identifier |
| No HTTP client in the `diagnostics` package | No hidden upload path |
| Backend endpoint inventory contains no diagnostics endpoint | No server that could receive a report |
| `samsung` graph excludes every telemetry SDK | Diagnostics never on the control path |
| Fixture and recorded-trace redaction grep | No secrets or addresses in committed fixtures |
| No `Log` in `samsung` or in `diagnostics` | Tokens do not reach logcat by accident |
| Release variant cannot resolve development configuration | No accidental test data in a shared report |

## Tests this file implies

| Test | Assertion |
|---|---|
| `noTelemetryDependency` | No crash-reporting, analytics, advertising, or attribution artifact appears in the merged graph |
| `adIdAbsentFromManifest` | `AD_ID` does not appear in the merged manifest |
| `redactedReportContainsNoFixtureSecret` | A planted token, pin, IP, MAC, email, Username, and text are absent from the record, the file, and the export |
| `localRecordIsBoundedAndRedacted` | Both sources cap at 200 events and the rolling file at its bound, with every stored field on the allowlist |
| `diagnosticFileIsExcludedFromBackup` | Backup and device-transfer rules exclude the diagnostics path |
| `exportRequiresUserConfirmation` | No report leaves the phone before the preview is shown and the user confirms |
| `clearLocalHistoryDeletesRecordAndFile` | Both sources and the rolling file are empty afterwards |
| `noDiagnosticsUploadPath` | The `diagnostics` package contains no HTTP client, and no upload endpoint exists in the backend surface |
| `samsungHasNoLogCalls` | `samsung` production sources do not call `android.util.Log` |
| `commandDoesNotAwaitDiagnostics` | A full record never delays a command |
