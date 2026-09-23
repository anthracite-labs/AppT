# Local data, secrets, and migration

This file owns what AppT stores on this phone, where it lives, how it survives upgrades, and how it recovers from corruption. Everything here is **device-local**. There is no AppT cloud synchronization of any television, personalization, or preference data, and no sync metadata, tombstones, version tuples, or outbox flags exist.

Account, trial, and entitlement storage is separate and is owned by [sync.md](sync.md). Entitlement material never shares a file, directory, or type with television secrets.

## Storage classes

Four storage classes, deliberately distinct types so one class cannot be written by another's code path:

| Class | Store | Examples | Leaves the phone? |
|---|---|---|---|
| Secret | Keystore-backed file under `noBackupFilesDir/samsung-secrets/v1/` | Samsung token, TLS SPKI pin | Never |
| Samsung-private | File under `noBackupFilesDir/samsung-device/v1/` | Protocol UUID cache, MAC, last address, capability evidence, model, firmware, candidate display name | Never |
| Entitlement | Keystore-backed file under `noBackupFilesDir/entitlement/v1/` | Signed proof, key set, time model, provisional record | Never (it is a cache of server-issued material) |
| Application | Room and DataStore | Friendly names, favourites, interaction preferences, last-used television, local app flags | Never |

Room and DataStore are the local source of truth for structured application data and preferences. Screens read Room and DataStore, never a network source.

## Room

Database file: `appt.db` in the application database directory. Version 1. Schema exported to the test resources. `fallbackToDestructiveMigration` is forbidden; a migration test is required for every later version.

### `TvProfile`

One row per television this phone remembers.

| Column | Type | Notes |
|---|---|---|
| `tvId` | TEXT PK | Opaque identity assigned inside `samsung` |
| `friendlyName` | TEXT NULL | 1–40 characters after trim; null until a name exists |
| `nameSource` | TEXT | `USER` or `TV` |
| `stableIdentity` | INTEGER | True when the television supplied the identity (see [discovery.md](discovery.md)) |
| `createdAt` | INTEGER | Device millis |
| `lastOpenedAt` | INTEGER NULL | Written when the session reaches `Ready`; drives quiet last-used reopen |

Forbidden columns: any column named like a token, pin, secret, certificate, MAC, address, host, SSID, Wi-Fi name, command, or text. A test loads the exported schema and fails if a forbidden name appears (the list is owned by [diagnostics.md](diagnostics.md)).

A forgotten television has no row here at all. Forget deletes the profile, so nothing can re-expose it; the retry that may still be outstanding lives in `PendingForget` below.

### `Favourite`

One row per favourited app or secondary control.

| Column | Notes |
|---|---|
| `tvId` | Part of the composite primary key |
| `kind` | `APP` or `CONTROL` |
| `target` | Opaque `AppId` value or `RemoteKey` name |
| `sortOrder` | Order within the shelf and the More sheet |

Primary key: `(tvId, kind, target)`. Composite identity makes favourite toggles and reorders idempotent without a synthetic id. A reorder is one transaction that rewrites `sortOrder` for the affected rows.

Rules:

- A television the user has not selected is not inserted.
- An `Unsupported` scan hit is not inserted.
- `nameSource` starts as `TV` when the row is created from `DiscoveredTv.name`. A user edit sets `USER`; a later television-reported name never overwrites `USER`.
- `lastOpenedAt` is device-local and never leaves the phone, so one phone never changes another phone's last-used television.
- There is no account column, no account-scoped ownership, and no shared-metadata concept anywhere in the schema.

### `PendingForget`

One row per local unpair that has been requested but not yet confirmed by `samsung`.

| Column | Notes |
|---|---|
| `tvId` | Primary key |
| `requestedAt` | Device millis, for diagnostics and ordering only |

Forget is the only writer. The row exists because `samsung.forget` can fail while the telephone, television, or network is temporarily unavailable, and a retry needs durable state after the profile row is already gone. It holds no name, no favourite, no secret, and no sync metadata, and it is a **local lifecycle marker, not a tombstone**: it never leaves the phone, nothing else reads it, and no server or account concept can create one.

### Ownership rules

- Remember: the user selects a controllable card → `app` inserts `TvProfile` → `ActiveRemoteHost.enter` → `samsung.open`. The secret appears only after TV-side approval.
- Rename: one Room transaction updates `friendlyName`, `nameSource = USER`. Write happens in the ViewModel, never in a composable effect.
- Favourite or reorder: one Room transaction per user action (see [presentation.md](presentation.md#favourites-apps-and-edit-mode)).
- Forget this TV, requested on this phone: **one Room transaction** deletes the `TvProfile` row and every `Favourite` row for that `tvId`, and inserts a `PendingForget` row. The television disappears from every list immediately, and no state that could re-expose it survives the transaction.
- `app` then calls `samsung.forget(tvId)`. On `Forgotten`, a second transaction deletes the `PendingForget` row. A failed `forget` leaves that row in place; startup retries every pending id that still has a samsung-private record for it. A second `forget` is safe because `forget` is idempotent.
- A `PendingForget` row is also cleared when a session for the same `tvId` reaches `Ready` again: the user has re-added the television, the new approval replaced the old pairing material, and a stale retry must never delete it. Without that rule, a stuck pending forget could unpair a freshly paired television at startup.
- Forget removes this phone's Local Pairing and Television Personalization. It never touches account state, entitlement state, another television, or anything outside the phone.
- Removing a profile row never touches another television's row, and never touches account state.

## DataStore

Preferences DataStore with typed keys. Call sites never use raw key strings.

| Key | Type | Default | Purpose |
|---|---|---|---|
| `hapticsEnabled` | Boolean | true | Interaction Preference |
| `volumeButtonsControlTv` | Boolean | true | Interaction Preference |
| `navigationMode` | `Directional` \| `Pointer` | `Directional` | Preferred navigation mode |
| `permissionExplanationAcknowledged` | Boolean | false | The local-network explanation has been shown and accepted |
| `firstControlAchieved` | Boolean | false | Set when a command first returns `Accepted`. A socket-write proxy, not visible television action. See [sync.md](sync.md) |
| `lastOpenedTvId` | String? | null | Quiet reopen target |

Explicitly absent: `lastSyncedUid`, `originDeviceId`, any `prefmeta.*` tuple, install identifiers, and any `updatedAt`/`revision`/`deletedAt` preference metadata. Those belonged to the removed sync design.

Interaction Preferences are device-wide and device-local. Favourites and secondary-control order are per television and device-local. Neither is account data.

## Samsung secret record

Location: `noBackupFilesDir/samsung-secrets/v1/<tvId>`.

`app` does not build this path and has no API that accepts the ciphertext type.

- Keystore key: alias `appt.samsung.v1`, AES-GCM, non-exportable, `setUserAuthenticationRequired(false)` so reopening does not demand a biometric prompt. StrongBox where available, otherwise TEE. Randomized encryption required.
- File layout: magic `APS1`, version, 12-byte IV, GCM body.
- Payload: token and SPKI pin only. MAC, address, and UUID do not share this type, so a serializer for "device record" cannot sweep the token along with them.
- Write: encrypt, write a temporary file in the same directory, fsync, rename. A crash mid-write leaves the previous secret. Plaintext is never staged in `cacheDir`.
- `forget` deletes the file. Deletion is idempotent. An undecryptable file is never replaced with an empty pairing: `open` surfaces `SecretsUnavailable` until the user pairs again.

## Samsung-private device record

Location: `noBackupFilesDir/samsung-device/v1/<tvId>.json`.

Contains the protocol UUID, MAC, last address, capability evidence (including rejected keys and wake-failure count), model, firmware, and the candidate display name. No token, no pin. Diagnostics never dump the directory; export reads model and firmware through an allowlist function rather than copying the file.

This record is how address changes are remembered without putting addresses in Room.

## Entitlement cache

Owned by [sync.md](sync.md). It sits in its own directory with its own Keystore alias (`appt.entitlement.v1`) and its own type, so:

- television secrets cannot be read by licensing code;
- entitlement proofs cannot be written into a Room entity, DataStore key, or Samsung file;
- deleting or corrupting one class cannot silently destroy the other.

The provisional record inside this file uses the device-computed `provisionalKey` described in [sync.md](sync.md#provisional-entitlement); the server-keyed purchase fingerprint cannot be computed offline and is never stored here. Raw purchase tokens are never stored anywhere.

## Backup and device transfer

AppT's data is device-local **by product rule**: a new phone starts its remote state clean, and signing in restores only account identity, Username, trial state, and Lifetime Entitlement.

The architecture enforces that structurally rather than by listing exclusions:

- `android:allowBackup="false"` for V1, so neither cloud backup nor device-to-device transfer carries AppT application data.
- `dataExtractionRules` and `backupRules` additionally exclude the secret, device, entitlement, diagnostics, and datastore paths, so a future change to `allowBackup` still fails closed.
- Uninstall and clear-data remove all local television state. Pairing is re-established by pairing again; remembered televisions that expose a stable identity return as the same `TvId` after rediscovery, and device-local names and favourites do not return. That is the accepted product behavior, not a defect.

Consequences documented for support: after a reinstall a customer signs in to restore their entitlement, and re-pairs their televisions.

## Migration and upgrade architecture

### Room schema migration

- Every version bump ships an explicit `Migration`; destructive fallback is a test failure if present.
- Migration tests run against the previous exported schema, in an instrumented or Robolectric test, using realistic rows that include a Chinese-language friendly name, an emoji name, a 40-character name, a null name, and a `PendingForget` row.
- A migration that would drop a column holding a user-visible choice (name, favourite order) is rejected in review unless the product decision is explicit.

### DataStore migration

- Key changes use a migration map applied once, guarded by a `dataStoreSchemaVersion` key, with the old key deleted only after the new key reads back successfully.
- The preferences file is never deleted as a repair step; a corrupt preferences file is replaced with defaults, and the user's television state in Room is unaffected.

### Samsung-private file migration

- Files carry a version in the directory name (`v1`) and a version field in the JSON.
- A format change adds `v2` and reads `v1` during a migration window: parse `v1` → write `v2` → verify read-back → delete `v1`. A failed migration keeps `v1` and continues with it.
- Unknown fields are ignored on read and preserved when the writer does not own them; a writer never silently drops capability evidence it does not understand.
- Missing required fields fall back to safe defaults (`MAC` absent → `powerOn` `Unavailable`; UUID absent → a device-minted, non-stable `TvId`).

### Entitlement cache migration

- The cache carries a format version and a magic header. An unknown or older format is discarded rather than guessed, because it can always be re-fetched while online and never contains television state.
- Discarding the cache never deletes television state and never interrupts an active remote session.

### Keystore key invalidation, rotation, and recovery

| Situation | Behavior |
|---|---|
| Keystore key invalidated (device lock change, restore, OEM wipe) | Samsung secret decrypt fails → `SecretsUnavailable` → the television shows "pair again" and re-pairing writes a fresh secret. Entitlement cache decrypt fails → treated as no cached proof → online check required |
| Planned rotation for the Samsung secret | Read-decrypt under `appt.samsung.v1`, encrypt under the new alias, verify read-back, then delete the old alias. Never delete before verification. A failed rotation keeps the old alias |
| Planned rotation for the entitlement cache | Same pattern; the cache is disposable, so failure simply discards it |
| StrongBox available on some devices only | Key generation prefers StrongBox and falls back to TEE; the alias records which was used so a device that later loses StrongBox still decrypts through the normal path or reports `SecretsUnavailable` |

There is no silent plaintext fallback anywhere, and no "ignore security errors" mode.

### Corruption recovery

| Corrupt artifact | Recovery | User-visible effect |
|---|---|---|
| Samsung secret file | `SecretsUnavailable`; pair again writes a new file | "Saved connection needs pairing again" |
| Samsung-private device record | Re-created from the next discovery or device-info read; a missing MAC downgrades power-on to `Unavailable` | Power control may disappear honestly |
| Room database that fails to open | A repair path offers to reset local television data after explicit confirmation. Stable-identity televisions return as the same `TvId` after rediscovery because identity comes from the television, and their pairing secrets are unaffected | A one-time recovery screen; favourited order and names are lost |
| DataStore preferences file | Replaced with defaults | Preferences reset to defaults |
| Entitlement cache | Discarded; refreshed online | A remote entry may require an online check |

Room repair is never automatic and never silent, because losing a user's names is a visible loss. It is offered only when the database cannot be opened at all.

### Atomicity and crash consistency

- Secret, device, and entitlement files: temporary file in the same directory, fsync, rename.
- Multi-row changes (forget, favourite reorder, television switch) are single Room transactions.
- No write path performs a partial update followed by a deferred "fix it later" pass; there is no outbox, no queue, and no pending-upload state anywhere in local storage.
- An interrupted process leaves either the previous or the new state, never a merged hybrid.

## Structural barriers

Rules that make leakage and re-introduction of cloud state difficult rather than merely discouraged:

1. `PairingSecret` is a type the Room DAOs, DataStore keys, and entitlement cache do not reference.
2. The entitlement proof type is not importable from `samsung`, and no Samsung type is importable from licensing code.
3. Secret, device, and entitlement directories are never listed or scanned generically; each owner reads only its own typed records.
4. The diagnostic logger has no parameter of type `TvCommand`, `PairingSecret`, or proof payload.
5. A schema test fails on forbidden column names, and the manifest-level backup configuration fails closed.
6. There is no sync entity, no mutation log, no version tuple, and no tombstone type in the codebase. A test asserts that those names cannot be found in production sources.

## Tests this file implies

| Test | Assertion |
|---|---|
| `schemaContainsNoForbiddenColumn` | The exported schema has no forbidden column name |
| `roomMigrationEveryVersion` | A migration test exists for every schema version after 1 |
| `forgetRemovesRowAndFavouritesInOneTransaction` | After the confirming tap, the profile and its favourites are gone and a `PendingForget` row exists, with no intermediate state that re-exposes the television |
| `forgetIsRetryable` | A failed `samsung.forget` leaves the `PendingForget` row in place and startup retries it |
| `newPairingSupersedesPendingForget` | A session reaching `Ready` for a pending id clears the marker and never deletes the new pairing |
| `forgetRemovesSecret` | After `Forgotten` the secret file and the marker are gone, and a second `forget` still returns `Forgotten` |
| `favouriteReorderIsAtomic` | A reorder commits in one transaction and survives process death |
| `dataStoreMigrationKeepsValues` | Renamed keys keep their values and delete the old key only after a successful read-back |
| `corruptSecretDoesNotResetPairing` | An undecryptable secret yields `SecretsUnavailable`, not an empty pairing |
| `corruptEntitlementCacheDoesNotDeleteTvState` | Discarding the cache leaves Room and Samsung files untouched |
| `backupExcludesAllTvState` | Backup and device-transfer configuration cannot carry television or entitlement state |
| `noSyncTypesExist` | Production sources contain no sync-record, tombstone, or version-tuple type |
