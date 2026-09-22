# Testing architecture

Test external behavior at the highest useful seam. The primary seam is `SamsungTvs`. Tests that only survive by reading private protocol fields are in the wrong place.

## Responsibilities

| Layer | Proves | Does not prove |
|---|---|---|
| JVM unit | ViewModel state, merge function, redactor, session machine through `SamsungTvsImpl` with fake transports | Real Keystore, real multicast |
| Protocol contract | Fixture frames produce the specified snapshot and, inside `samsung` tests, the specified outbound frames | That every television generation works |
| Instrumented | Keystore round trip, backup exclusion, permission gate, Room migration, process death restore | Layout polish |
| Compose | Critical flows against a fake `SamsungTvs`: first control, reconnect status, hidden dead controls, no address on cards | Wire format |
| Rules tests | Firestore emulator or rules unit harness: cross-user deny, secret field deny, hard-delete deny | Television behavior |
| Physical matrix | One real television for each generation actually claimed | A predeclared year range |

`app` tests replace `SamsungModule` with a fake `SamsungTvs`. They assert on snapshots and results. They do not assert `KEY_*` or ports.

`samsung` contract tests construct `SamsungTvsImpl` with a fake session transport that replays a fixture and records writes. Asserting outbound frames there is correct. Those tests are not on the `app` classpath.

## Fake adapters

Internal, `samsung` test source set:

- Discovery transport emits scripted candidates, including a soundbar and an unsupported handshake.
- Session transport scripts TLS identity, inbound frames, delays, and closes.
- Secret store is in-memory, with a mode that fails decryption.
- Wake sender records bursts and transmits nothing.
- Clock advances backoff without sleeping.

`app` fake:

- Implements `SamsungTvs` only.
- Can script discovery cards, session states, capability sets, and command results.
- Has no WebSocket type.

## Fixtures

Location: `samsung/src/test/resources/samsung/fixtures/<case-id>/`.

`provenance.json`:

```json
{
  "caseId": "tls-approval-then-volume",
  "captured": "YYYY-MM-DD",
  "generation": "tizen-websocket-tls",
  "source": "lab-tv",
  "redaction": "complete",
  "notes": "what the case proves"
}
```

No host, MAC, token, serial, account, or personal email. `source` is a role (`lab-tv`, `contributed`), not a person. `generation` is a label assigned after capture, not a year-range claim.

`trace.jsonl` lines:

```json
{"tMs":0,"dir":"in","kind":"ws-text","body":{"event":"ms.channel.connect","data":{"token":"[fixture-token]"}}}
```

Directions are `in` and `out`. Times are relative milliseconds. Tokens in committed files are the placeholder `[fixture-token]`. The test injects a known token when it needs to prove the token was or was not written to the socket.

Certificate fixtures are generated in-repo test certificates. Do not commit a captured television certificate. A captured certificate is a device identifier.

CI rejects a fixture file that contains an IPv4 address, a MAC, or a raw `"token": "<not a placeholder>"` pattern.

Minimum fixture set before the matching slice can be called done:

| Case | Proves |
|---|---|
| `tls-approval-then-volume` | Approval, token persisted, volume frame written, state `Ready` |
| `token-resume` | Second connection sends the token and does not require approval |
| `identity-mismatch` | Different SPKI, token absent from the recorded URL, state `NeedsRepair` |
| `unauthorized-with-token` | `TokenRejected`, no reconnect loop |
| `approval-timeout` | `ApprovalTimedOut` at 45 seconds of fake clock |
| `malformed-frame` | Oversize or broken JSON does not throw to the caller |
| `reconnect-backoff` | Delays follow the schedule, then `Unreachable` |
| `rediscover-same-uuid` | New address accepted only when UUID matches and pin matches |
| `unsupported-no-keys` | Non-adopted handshake emits `Unsupported` and writes no key frame |
| `app-list` | Only returned apps become `LaunchableApp` |
| `text-rejected` | `textInput` becomes false and the text is absent from diagnostics |

A slice that needs a missing fixture captures a redacted trace or generates a synthetic trace whose provenance says `synthetic`. Synthetic traces are valid for unit contracts. They do not count as a physical-matrix row.

## Required behavioral tests

These names are the contract. Implementation may split them, not drop the assertion.

| Test | Assertion |
|---|---|
| `cloudAbsenceDoesNotBlockCommand` | `Ready` fixture accepts a command with Firebase classes absent from the `samsung` runtime |
| `samsungGraphExcludesFirebase` | Gradle dependencies of `:samsung` do not include Firebase, Play services, or Crashlytics |
| `secretRecordIsNotASyncRecord` | Schema and `SyncRecord` reflection contain none of the forbidden names |
| `identityMismatchDoesNotSendToken` | Recorded socket URL has no token |
| `discoveryEndsAtBound` | Fake clock at 10 seconds yields `Finished` and the multicast lock is released |
| `v1ProbesDoNotRequestNearbyWifiDevices` | Target 36 discovery does not request `NEARBY_WIFI_DEVICES` or location for SSDP, raw sockets, or `NsdManager` |
| `secondDiscoverCancelsFirst` | One scan owner |
| `malformedFrameDoesNotEscapeSession` | No exception on the caller; session continues or reconnects |
| `redactedReportContainsNoFixtureSecret` | Planted token, IP, MAC, and text are absent |
| `tombstoneDoesNotResurrect` | An older live tuple is denied. It does not replace a newer tombstone |
| `staleWriteDoesNotOverwrite` | An older tuple does not replace a newer live record. Rules deny the update |
| `workerSubmitsStoredTuple` | The worker writes the persisted `updatedAt`, `revision`, and `originDeviceId`. It does not stamp a new tuple at push time |
| `preferenceMutationWritesMetadata` | The preference value and `prefmeta` commit in one edit. The worker does not create that tuple later |
| `firstSessionGateEndsWithActiveRemote` | First `Accepted` does not interrupt the exempt active remote; after that holder closes, the next remote entry without a user does not call `SamsungTvs.open` |
| `accountSwitchDoesNotLeakOldMetadata` | Confirmed different-uid switch clears prior account names/favourites/synced preferences without uploading them to the new uid |
| `accountSwitchPreservesPairing` | Confirmed different-uid switch leaves Samsung secret/private records intact and a remembered TV remains locally controllable |
| `remoteTombstoneDoesNotForget` | A winning remote television tombstone removes account-scoped TV metadata/favourites, does not call `forget`, and leaves the secret file intact |
| `freshInstallDoesNotWipeCloud` | Empty local store pulls and pushes no tombstones |
| `forgetRemovesSecret` | After `Forgotten`, the secret file is gone and a second `forget` still returns `Forgotten` |
| `holdCancellationReleases` | Cancelled `Hold` still writes release |

Room migrations, once a second schema version exists, run as instrumented or Robolectric migration tests. Destructive fallback is a test failure if it is present in production source.

Compose coverage for V1 flows: welcome to explanation to cards; card has no IP text; approval state; one command; reconnecting status is not a dialog; rejected key disappears; account requirement is not applied before the first `Accepted`. That `Accepted` is a socket write, not visible television action. The same exempt `ActiveRemote` remains usable until it closes; the next remote entry without a Firebase user routes to Account before `SamsungTvs.open`.

## Physical acceptance matrix

Not a fixed year list. A row is added when a television is actually observed. Absence of a row means "not claimed," not "unsupported by year."

Columns: generation label assigned after the session, TLS or plaintext, token required, pair result, command result, reconnect result, process-death resume, wake result, text result, apps result, pointer result, pin survived reboot (yes/no/not tried), date, pass/fail.

Before a public production rollout, the matrix needs at least one passing row for the TLS token path covering pair, command, reconnect, and process-death resume, plus a recorded wake attempt (pass or honest failure). Further rows are added as televisions are available. They do not block the architecture map.

The matrix is an internal release artifact. It is not the harvested public device-support page, and it is not an in-app adapter-health screen. Those remain HARVEST and are not V1 product surfaces.

Do not write raw addresses, MACs, tokens, or account emails into the matrix. Model and firmware are allowed.
