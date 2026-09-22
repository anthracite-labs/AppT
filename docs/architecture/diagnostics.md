# Diagnostics and privacy

V1 has crash reporting and an explicit redacted export. It has no behavioral analytics. It has no diagnostic upload service.

## Ownership

| Piece | Owner | On the command path |
|---|---|---|
| Redacting ring buffer | `samsung`, exposed only as `redactedDiagnostics()` | No. Enqueue and drop if full. |
| Share sheet and preview | `app` | No |
| Crashlytics | `app` | No. Uncaught crashes only, plus non-fatals that are enum names. |
| Firebase Analytics, ads, attribution | Not in the dependency graph | |

`samsung` does not depend on Crashlytics. `app` may record a non-fatal whose message is a `SessionState` or `TvFailure` name. It does not record television names, `TvId` values, or command text.

## Crashlytics

- Dependency: Firebase Crashlytics via the Firebase BOM.
- Do not add `firebase-analytics`.
- Do not add `com.google.android.gms.permission.AD_ID`. The manifest merger check fails if `AD_ID` appears.
- Do not call `setUserId`. Do not set custom keys for account, television, address, or Wi-Fi.
- Collection enabled in release. Debug builds do not upload.
- Upload mapping files for release builds so crashes are readable. Mapping upload is not a command-path dependency.

A Crashlytics outage does not affect `command`.

## Local buffer

In memory, capped at 200 events, oldest dropped. Optional rolling file in `noBackupFilesDir` so a crash dump of backups does not export the buffer. The file is already redacted at write time.

Event API inside `samsung` takes an event name and an allowlist of fields. It does not take a `TvCommand`, a URL, or a free-form message built from a payload. Free-form strings that must exist go through the redactor first.

`command` never awaits the buffer. If the buffer is full, the event is dropped.

## Export

Request Support and the settings export use the same path. The file is a diagnostic share. It is not an account-sync export, and importing it does not restore televisions or replace Firestore. That rejected file-sync idea stays rejected.

1. User asks to export.
2. `app` builds a report from `redactedDiagnostics()` plus app version and Android API level.
3. A preview lists the fields that will leave the phone.
4. The user confirms. The share sheet sends the file. V1 does not POST it to an AppT service.

Default export may include television model and firmware, because support needs them and they are not unique pairing material. It does not include friendly names, `TvId`, account ids, or install ids. The user can describe the room name themselves.

There is no automatic export on pairing failure. The unsupported card gains this action in the diagnostics slice. Until that slice, the card states that control is unavailable and does not show a dead button.

## Redaction

Single redactor, used by the `samsung` log wrapper, the buffer, and the export builder. `app` log lines that might touch television data use the same function.

Apply in order:

1. Replace any URL with `[url]`.
2. Replace IPv4 and IPv6 with `[ip]`.
3. Replace MAC addresses with `[mac]`.
4. Replace email addresses with `[account]`.
5. Replace UUID-shaped strings with `[id]`.
6. Replace any currently stored token or pin value with `[redacted]`, via a supplier that is not itself logged.
7. Drop fields that are not on the export allowlist.

Allowlist for export and Crashlytics-adjacent logs: event name, elapsed time, `SessionState` name, `TvFailure` name, `RepairReason` name, capability flag names, `model`, `firmware`, app version, API level.

### Forbidden everywhere

These names must not appear as Room sync columns, `SyncRecord` properties, Firestore fields, log fields, Crashlytics keys, or export fields:

```text
token, pairingToken, secret, pin, password, certificate, spki,
mac, wifiMac, ip, address, host, ssid, wifi, command, commandText, text
```

### Allowed in the user's Firestore, forbidden in Crashlytics and export

```text
tvId, friendlyName, originDeviceId, lastSyncedUid, email, uid
```

Tests lock both lists. A fixture token planted in a frame must be absent from `redactedDiagnostics()` and from any log line the test captures.

## Logcat

`samsung` production source does not call `android.util.Log` directly. CI fails the slice if it does. Release builds do not attach an OkHttp logging interceptor. Debug builds may log event names through the redactor. They still must not log URLs.

## Latency and coupling

The control path's only contact with diagnostics is a non-suspending offer to a bounded queue. No disk flush, no Crashlytics call, and no share-sheet work run inside `command` or the socket read loop.

A deep security audit of the TLS pin and the ruleset is a recommended follow-up before public release. It is not part of this map and is not a reason to add analytics or a diagnostic backend.
