# Local data and secrets

Three storage classes. They are different types so a pairing secret cannot be inserted into a sync record by accident.

| Class | Store | Examples | Sync |
|---|---|---|---|
| Secret | Keystore-backed file under `noBackupFilesDir` | Token, SPKI pin | Never |
| Samsung-private | File under `noBackupFilesDir`, not Room | UUID cache, MAC, last address, capability evidence, model, firmware | Never |
| Application | Room and DataStore | Friendly names, favourites, preferences, last-opened id | Explicit whitelist only |

Room is the local source of truth for structured application data. Screens read Room and DataStore. They do not read Firestore.

## Secret record

Location: `noBackupFilesDir/samsung-secrets/v1/<tvId>`.

`app` does not build this path and has no API that accepts the ciphertext type. The directory exists only under `noBackupFilesDir`. A debug assertion fails if a secret file is created under `filesDir`, `databases/`, or `cacheDir`.

One Android Keystore AES-256-GCM key, alias `appt.samsung.v1`, non-exportable. `setUserAuthenticationRequired(false)` so reopen does not demand a biometric prompt. Biometric gating is not a product requirement and is not added. StrongBox when the device has it, otherwise TEE. Randomized encryption required.

File layout:

```text
magic     4 bytes  APS1
version   uint16   1
iv        12 bytes
gcm body  ciphertext and tag
```

Write by encrypting, writing a temporary file in the same directory, fsync, and rename. A crash mid-write leaves the previous secret. Never stage plaintext in `cacheDir`.

Payload is the token and the SPKI pin only. MAC, address, and UUID do not share this type, so a serializer for "device record" cannot sweep the token along with them.

`forget` deletes the file. Deletion is idempotent. Undecryptable files are not replaced with an empty pairing. `open` surfaces `SecretsUnavailable` until the user pairs again, which writes a new file after approval.

Backup and device-to-device transfer: `noBackupFilesDir` is already excluded. Also exclude `samsung-secrets/` and `samsung-device/` in both `data_extraction_rules.xml` and `backup_rules.xml`, so a later move into `filesDir` still fails closed. `allowBackup` may stay true for non-secret application data.

Clear-data and uninstall remove secrets. They are not recoverable from AppT cloud, because they were never uploaded. The user pairs again. Synced names can return after sign-in. That split is the product rule.

## Samsung-private device record

Location: `noBackupFilesDir/samsung-device/v1/<tvId>.json`.

Contains protocol UUID, MAC, last address, capability evidence (including rejected keys and wake-failure count), model, firmware, and the candidate display name. No token and no pin. Same backup exclusion. Diagnostics must not dump the directory. Export reads model and firmware through an allowlist function, not by copying the file.

This record is how address changes are remembered without putting addresses in Room.

## Room

Database file: `appt.db` in the application database directory. Version 1. Export the schema. `fallbackToDestructiveMigration` is forbidden. A migration test is required for every later version.

### `TvProfile`

`TvProfile` and `Favourite` are account-scoped non-secret application state. They are not the source of pairing truth. A remembered Samsung pairing may exist without a live `TvProfile` after an account switch or a synchronized deletion.

| Column | Sync | Notes |
|---|---|---|
| `tvId` TEXT PK | yes, document id | Opaque correlation id |
| `friendlyName` TEXT | yes | 1..40 characters |
| `correlatable` INTEGER | yes | False records are not uploaded |
| `nameSource` TEXT | yes | `USER` or `TV` |
| `lastOpenedAt` INTEGER NULL | no | Device-local reopen |
| `updatedAt` INTEGER | yes | Client millis at the mutation |
| `revision` INTEGER | yes | Incremented on each local mutation |
| `deletedAt` INTEGER NULL | yes | Tombstone |
| `originDeviceId` TEXT | yes | Install id tie-break |
| `pendingSync` INTEGER | no | Outbox flag |
| `localUnpairPending` INTEGER | no | This phone's user asked to forget. Not set by a remote tombstone |

No column for token, pin, MAC, address, SSID, or command text. A unit test loads the exported schema and fails if a forbidden name from [diagnostics.md](diagnostics.md) appears.

### `Favourite`

| Column | Notes |
|---|---|
| `id` TEXT PK | Stable id of `tvId`, `kind`, and `target`, so two phones do not create duplicate Netflix rows |
| `tvId` | |
| `kind` | `APP` or `CONTROL` |
| `target` | Opaque `AppId` value or `RemoteKey` name |
| `sortOrder` | |
| sync metadata | Same `updatedAt`, `revision`, `deletedAt`, `originDeviceId`, `pendingSync` pattern as `TvProfile` |

Favourite identity is the triple, not a random UUID. Reorder updates `sortOrder` and bumps `updatedAt` / `revision` on each changed row in the same write as the new order.

A television the user has not selected is not inserted. An `Unsupported` scan hit is not inserted.

`nameSource` starts as `TV` when the row is created from `DiscoveredTv.name`. A user edit sets `USER` and bumps sync metadata in that same write. A later television-reported name must not overwrite `USER`.

`lastOpenedAt` is written when the remote surface reaches `Ready`. It is not a sync field. Last-used reopen is device-local, so one phone does not steal another's last television.

## DataStore

Preferences DataStore. Keys are typed wrappers. Call sites do not use raw key strings.

Synced whitelist:

| Key | Type | Default |
|---|---|---|
| `hapticsEnabled` | Boolean | true |
| `volumeButtonsControlTv` | Boolean | true |
| `navigationMode` | `Directional` or `Pointer` | `Directional` |

Device-local, never built into a `SyncRecord`:

| Key | Purpose |
|---|---|
| `permissionExplanationAcknowledged` | Gate has been shown |
| `firstControlAchieved` | Set when a command first returns `Accepted`. Socket-write proxy, not visible television action. See [sync.md](sync.md) |
| `lastOpenedTvId` | Quiet reopen |
| `lastSyncedUid` | Detect a different account. See [sync.md](sync.md) |
| `originDeviceId` | Canonical lowercase UUID, created once. Tie-break id |

Sync metadata for whitelist preferences lives in the same DataStore under a `prefmeta.` prefix: `updatedAt`, `revision`, `deletedAt`, `originDeviceId`, `pendingSync`.

A local preference change is one DataStore edit. That edit writes the new value and the `prefmeta.` tuple together. `updatedAt` is client millis at that edit. `revision` is the previous revision plus one, or 1 if none. `originDeviceId` is the install id. `pendingSync` is true. The UI calls this edit. It does not write the value alone and leave metadata for later.

The sync worker is not the writer of that original tuple. It must not stamp `updatedAt`, `revision`, or `originDeviceId` when it later uploads the preference. After a successful reconcile it may clear `pendingSync` if the stored tuple is still the one it wrote. When a remote tuple wins, it writes the remote value and the remote `prefmeta.` together, with `pendingSync` false, in one edit. If a newer local edit landed, it leaves `pendingSync` set and does not overwrite that edit. UI reads the preference values, not Firestore.

Room mutations follow the same rule. The DAO write that changes a name, a favourite, or a local tombstone also writes `updatedAt`, `revision`, `originDeviceId`, and `pendingSync` in that transaction. The worker does not invent them at sync time.

`lastSyncedUid` and `originDeviceId` are account-identifying. They stay out of Crashlytics and diagnostic export.

## Ownership rules

- `app` writes Room and DataStore first. UI collects those flows.
- `samsung` writes secrets and samsung-private files. `app` never reads pairing material.
- Remember: user selects a controllable card → `app` inserts `TvProfile` with its sync tuple → `open`. Secret appears only after approval.
- Local forget, requested on this phone: one Room transaction sets `localUnpairPending`, `deletedAt`, and the rest of the sync tuple together and tombstones related account-scoped favourites. Then `app` calls `forget` and retries on `Failed`. After `Forgotten`, clear `localUnpairPending`. The tombstone stays for sync. Startup calls `forget` only for ids that still have `localUnpairPending` and are still in `rememberedIds()`. A second `forget` is safe.
- A television tombstone that arrives from sync removes the live account-scoped `TvProfile` presentation and related favourites but does not call `forget`, set `localUnpairPending`, or delete pairing material.
- The remembered-TV list is the union of live account profiles and local remembered Samsung ids. If an id is locally paired but has no live account profile, it remains controllable and is presented with a neutral `Samsung TV` label or a freshly discovered television-reported name. A tombstoned account friendly name and its favourites are not shown.
- Confirmed account switch: clear the previous account's Room sync state, tombstones, favourites, and pending sync flags without creating new tombstones; reset the three synchronized preferences and their `prefmeta.` state to defaults without marking them pending; preserve all Samsung secret/private files and device-local DataStore keys; then load the new account. See [sync.md](sync.md).
- Non-correlatable televisions can be named locally and must not be uploaded.

## Secret lifecycle

```mermaid
sequenceDiagram
  actor User
  participant App
  participant Samsung as SamsungTvs
  participant Secret as KeystoreFile
  participant Room
  participant Cloud as Firestore
  User->>App: Choose television
  App->>Room: Insert name and version tuple
  App->>Samsung: open
  Note over Secret: Candidate pin stays in memory
  Samsung->>Secret: On approval, encrypt token and pin
  App->>Room: Enqueue name
  Note over Room,Cloud: Worker compares the stored tuple before writing
  User->>App: Forget television on this phone
  App->>Room: localUnpairPending and tombstone tuple
  App->>Samsung: forget
  Samsung->>Secret: Delete secret and device file
  Note over App,Cloud: A remote tombstone does not enter this path
```

Cloud failure on the sync steps does not roll back the secret and does not close an open session. The name remains local and `pendingSync` stays set until compare-before-write settles it.

## Structural barriers

These are the implementation rules that make leakage difficult rather than merely discouraged:

1. `PairingSecret` is a type the Room DAO and `SyncRecord` do not reference.
2. `SyncRecord` is a sealed type. The worker serializes that type only. No reflection over "all entities."
3. Secret files live in a directory the sync worker does not list.
4. The diagnostic logger has no parameter of type `TvCommand` or secret payload. Text is not passed in.
5. Schema and `SyncRecord` tests fail on forbidden field names.
6. Firestore rules reject forbidden field names even if a buggy client sends them. See [sync.md](sync.md).
