# Account and synchronization

Firebase Authentication and Cloud Firestore sync non-secret application data. They are not on the television-control path. Room and DataStore remain the stores screens read. Firestore persistence is disabled so the Firestore SDK cannot become a second local source of truth. V1 uses no Firestore snapshot listeners.

The sync worker reconciles in the background. Each record is compared with the current remote version before any write. There is no unconditional push, and no push-then-merge pass.

## Unresolved decision — different account

This is a material product decision. This map does not choose it.

**Decision needed:** When this phone already has a `lastSyncedUid`, and the user authenticates a Firebase uid that is different, what should happen to local television names, favourites, preferences, and device-local pairing material?

Do not assume merge into the new account, replace local data with the new account's cloud data, two profiles on one phone, wipe of non-secret data, or wipe of pairing secrets.

**Holding behavior until that decision exists:**

- Do not upload local records to the new uid.
- Do not download that uid's records over local data.
- Do not delete pairing secrets.
- Do not offer a merge or replace action.
- A session already open may finish. Do not drop it because a conflicting sign-in started.
- If the user is at the account gate, show a holding screen whose meaning is that switching accounts is not available and nothing was changed. The only account action on that screen is sign-out, so the user is not trapped.
- Stop further account-switch work.

Same-uid sign-in and first sign-in (`lastSyncedUid` null) are specified below and are not blocked.

## Unresolved decision — cross-device unpair

This is a material product decision. This map does not choose it.

**Decision needed:** When a signed-in user deletes a television on one phone, should that deletion also unpair the television on the user's other phones, or should pairing stay strictly local while only non-secret synced state is removed?

Do not assume that other phones call `forget`, and do not assume that a remote tombstone is only a cloud name with no effect on the local list. Those are the two answers. Neither is selected here.

**Holding behavior until that decision exists:**

- Synchronize the non-secret television deletion record with the compare-before-write rule below. The deletion fact must not be lost, and an older live record must not resurrect it.
- Do not call `forget`.
- Do not delete pairing secrets or samsung-private device records.
- Do not set `localUnpairPending`.
- Do not hide, disable, or drop favourites for a television this phone has already paired, solely because the winning synced television record is a tombstone.
- Do not show a flow that says the other phone unpaired this one.
- A local forget, requested by the user on this phone, still calls `forget`. That path is specified in [data.md](data.md). It is not this decision.
- This continuation of local control is holding behavior. It is not the chosen product meaning of delete.

## Account gate

Sign-in methods: Google, via Android Credential Manager and Firebase `GoogleAuthProvider`, and email/password, including create, sign-in, and password-reset email. No other providers. No anonymous auth. No phone auth. Email verification is not an extra gate.

The gate reads the local Firebase user cache only. It does not wait for a network round trip before allowing control. Token refresh failure must not clear `currentUser` and must not sign the user out.

The accepted product rule is the whole gate. Sign-in is required for continued/full product use and for synchronization. It is not a prerequisite for reaching or completing the first successful local-control session. This map does not add a trigger the product text does not state. It does not define the requirement as the next cold start, the next navigation, or an interrupt of the remote at the instant the first command is accepted. There is no control that permanently dismisses the account requirement.

| Condition | Effect |
|---|---|
| `firstControlAchieved` is false | First local-control session is allowed without a Firebase user. |
| `firstControlAchieved` is true, `currentUser` null | Continued/full product use and sync require sign-in. The session that already reached first success is not retroactively blocked. No permanent dismiss. |
| `currentUser` non-null, cloud unreachable | Allowed. Sync may fail. An open session does not wait on the cloud. |
| Explicit sign-out | Continued/full use requires sign-in again. Secrets and Room remain so a later same-uid sign-in restores control without re-pairing. |
| Different uid from `lastSyncedUid` | Holding behavior for different-account sign-in. |

The gate lives in `app`. `SamsungTvs.open` does not check Auth. That split is what keeps a Firebase outage from being enforceable inside the television module. `app` does not call `open` for continued/full use when sign-in is required and `currentUser` is null. It may call `open` for the first session.

`firstControlAchieved` becomes true when a command first returns `Accepted`. `Accepted` means the command was written to the live session. It does not mean the television visibly acted. The adopted channel does not acknowledge keys. This flag is the architecture's observable proxy for the product's first successful local-control session. Do not invent an acknowledgement the protocol does not provide. See [samsung-interface.md](samsung-interface.md).

## Sync whitelist

The worker serializes `SyncRecord` only.

```kotlin
sealed interface SyncRecord {
    data class Tv(
        val tvId: String,
        val friendlyName: String,
        val correlatable: Boolean,
        val nameSource: String,
        val updatedAt: Long,
        val revision: Long,
        val deletedAt: Long?,
        val originDeviceId: String,
        val schema: Int,
    ) : SyncRecord

    data class Favourite(
        val id: String,
        val tvId: String,
        val kind: String,
        val target: String,
        val sortOrder: Int,
        val updatedAt: Long,
        val revision: Long,
        val deletedAt: Long?,
        val originDeviceId: String,
        val schema: Int,
    ) : SyncRecord

    data class Preference(
        val key: PreferenceKey,
        val value: PreferenceValue,
        val updatedAt: Long,
        val revision: Long,
        val deletedAt: Long?,
        val originDeviceId: String,
        val schema: Int,
    ) : SyncRecord
}

enum class PreferenceKey { HapticsEnabled, VolumeButtonsControlTv, NavigationMode }
```

`schema` is 1. A pulled record with any other schema is not applied. Local data is left as it is. A redacted diagnostic `sync.unknownSchema` is recorded.

Non-correlatable television rows are not uploaded and not created from a remote record the phone cannot match. Preferences and favourites for a television the phone does not have remain in Room but are hidden until that television is known locally, so a pull cannot invent a controllable card. Hiding those unsynced-until-known favourites is not the cross-device unpair decision.

`TvId` derived from the protocol UUID is the correlation key inside the user's private Firestore. It is how a friendly name attaches to the same television on another phone. It is not shown in the UI and is excluded from Crashlytics and export. MAC and IP are not the correlation key.

## Firestore shape

```text
users/{uid}/tvs/{tvId}
users/{uid}/favourites/{favouriteId}
users/{uid}/preferences/{preferenceKey}
```

No other collections. Hard delete is denied. Deletion is `deletedAt`. V1 does not garbage-collect tombstones; household volume is small. A purge job would be a later decision.

Clients write `updatedAt`, `revision`, and `deletedAt` as integers (millis, or null for `deletedAt`). They do not write Firestore `Timestamp` values. The worker, the merge comparison, and Security Rules share that representation.

Path versus fields: `tvId`, favourite `id`, and preference `key` are document paths. They are not duplicated as fields. `lastOpenedAt`, `pendingSync`, and `localUnpairPending` are not cloud fields. The worker maps between `SyncRecord` and that document shape.

## Conflict and deletion

Last committed write wins per small record. The whole record is replaced. Fields are not merged. Merging fields would resurrect a cleared name or a removed favourite.

The winning write is the strictly newer version tuple, not whichever client happens to push first. An unconditional push, or a push followed by a merge, is not this policy: a stale client would commit an older tuple and could overwrite a newer live record or resurrect a newer tombstone. Comparison happens before the write.

```text
higher updatedAt wins
higher revision wins ties
higher originDeviceId wins remaining ties
```

`originDeviceId` is a canonical lowercase UUID. Kotlin `compareTo` and Firestore rules string `>` agree on that alphabet. A tie on all three fields is equal, not a win for either side.

`updatedAt` is client millis taken when the user mutated the record, matching the accepted last-write-wins policy. Clock skew is a residual of that policy, not a reason to switch to a server-authoritative clock in this map. The worker must not replace that stored millis with a fresh timestamp at push time. Restamping would launder a stale mutation into a newer tuple.

Absence is not deletion. A fresh install must not push tombstones for ids it has never stored. Only an explicit local delete sets `deletedAt`, and that tuple is written with the delete, not invented later by the worker.

A tombstone does not outrank the tuple. A newer tombstone wins over an older live record. A newer live record wins over an older tombstone, which is the accepted rule if the user adds the television again. An older live record must not be able to commit over a newer tombstone.

### Compare before write

One Firestore transaction per record. Do not batch the whitelist. A batch has no per-document precondition, so a stale record could commit beside a fresh one.

```text
if lastSyncedUid != null and lastSyncedUid != current uid:
    different-account holding behavior, stop

for each stored whitelist record with pendingSync:
    compare-before-write that record

one-shot get of the three collections, not a snapshot listener:
    for each remote document:
        if local still has an unsettled pending tuple for that id:
            leave it for the next pass
        else if local row is absent or remote tuple is strictly newer:
            apply the remote non-secret record
            if it is a television tombstone:
                cross-device unpair holding behavior
                do not call forget

if this pass was not a holding stop:
    store lastSyncedUid = current uid
```

A failed record write does not block `lastSyncedUid`. The uid is not in conflict. The record stays `pendingSync` and retries.

`compare-before-write` for one record:

```text
local = the persisted tuple
        // updatedAt, revision, originDeviceId, deletedAt, and payload
        // never stamped inside this function

transaction:
    remote = get(document)
    if the local record must not be uploaded:
        write nothing
    else if remote is absent:
        set(local tuple)                 // create
    else if local tuple is strictly newer:
        set(local tuple)                 // update
    else:
        write nothing                    // remote newer or equal

if the transaction retries because the document changed:
    compare again against the new remote version
    do not replay a tuple that lost

if rules deny the write:
    do not restamp
    re-read remote
    apply it only when it is strictly newer than the persisted local tuple
```

After the transaction:

- Re-read the local row.
- If a newer local mutation landed, leave `pendingSync` set and enqueue again. Do not clear it, and do not apply the remote tuple over that newer mutation.
- If the local tuple is still the one just compared and the local write committed, or the remote tuple won, or the tuples were equal: clear `pendingSync`.
- If the remote tuple won, apply that non-secret record in the same local transaction that clears `pendingSync`. A television tombstone still follows the unpair holding behavior.

Records that must not be uploaded: a non-correlatable television. Clear its `pendingSync` without a cloud write so the worker does not retry it forever. Do not create a remote document for it.

A local tombstone is uploaded only when this client has that stored record and the compare says the local tuple may be written. A missing remote document is a create of the stored tuple, not a scan that turns every cloud id this phone lacks into a tombstone.

Security Rules repeat the same order. `allow update` requires the incoming tuple to be strictly newer than the committed remote tuple. A stale client that skips the transaction still cannot overwrite a newer live record or resurrect a newer tombstone. `allow create` applies only when the document is absent. Equal tuples are not updates.

## Worker

WorkManager unique work `appt-sync`, network connected, not expedited. Policy `KEEP`. The worker runs the compare-before-write pass, re-queries once before exit, and runs again if a mutation landed mid-flight. `app` enqueues it after a local whitelist mutation, after sign-in, and on foreground if signed in. Periodic unique work `appt-sync-periodic` every 6 hours while signed in, for the same reconciliation. Sign-out cancels both.

The worker does not run on the command path and does not take a session lock. Failure retries with WorkManager backoff. The remote UI does not show a sync error dialog. V1 has no sync-status surface.

The worker is not the author of a local mutation's version tuple. Room and DataStore write that tuple with the mutation. See [data.md](data.md). After a successful reconcile the worker may clear `pendingSync`, or replace a record with a winning remote tuple. It must not invent `updatedAt`, `revision`, or `originDeviceId` for a mutation it is uploading.

Firebase App Check with Play Integrity is required for production Firestore access. Debug builds use the debug provider. App Check failure is a sync failure. It is not a television failure.

## Security Rules

Implement this ruleset. It is the V1 contract. `strictlyNewer` is the same order as the worker: higher `updatedAt`, then higher `revision`, then higher `originDeviceId`.

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function owns(uid) {
      return request.auth != null && request.auth.uid == uid;
    }
    function noSecrets() {
      return !request.resource.data.keys().hasAny([
        'token', 'pairingToken', 'secret', 'pin', 'password', 'certificate',
        'spki', 'mac', 'wifiMac', 'ip', 'address', 'host', 'ssid', 'wifi',
        'command', 'commandText', 'text'
      ]);
    }
    function metaOk() {
      return request.resource.data.schema == 1
        && request.resource.data.updatedAt is int
        && request.resource.data.revision is int
        && request.resource.data.originDeviceId is string
        && request.resource.data.originDeviceId.matches('^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
        && (request.resource.data.deletedAt == null
            || request.resource.data.deletedAt is int);
    }
    function strictlyNewer() {
      return request.resource.data.updatedAt > resource.data.updatedAt
        || (request.resource.data.updatedAt == resource.data.updatedAt
            && request.resource.data.revision > resource.data.revision)
        || (request.resource.data.updatedAt == resource.data.updatedAt
            && request.resource.data.revision == resource.data.revision
            && request.resource.data.originDeviceId > resource.data.originDeviceId);
    }
    function tvFields() {
      return request.resource.data.keys().hasOnly([
          'friendlyName', 'correlatable', 'nameSource', 'updatedAt', 'revision',
          'deletedAt', 'originDeviceId', 'schema'
        ])
        && request.resource.data.friendlyName is string
        && request.resource.data.friendlyName.size() > 0
        && request.resource.data.friendlyName.size() <= 40
        && request.resource.data.correlatable is bool
        && request.resource.data.nameSource in ['USER', 'TV'];
    }
    function favouriteFields() {
      return request.resource.data.keys().hasOnly([
          'tvId', 'kind', 'target', 'sortOrder', 'updatedAt', 'revision',
          'deletedAt', 'originDeviceId', 'schema'
        ])
        && request.resource.data.tvId is string
        && request.resource.data.kind in ['APP', 'CONTROL']
        && request.resource.data.target is string
        && request.resource.data.target.size() > 0
        && request.resource.data.target.size() <= 128
        && request.resource.data.sortOrder is int;
    }
    function preferenceFields(key) {
      return key in ['HapticsEnabled', 'VolumeButtonsControlTv', 'NavigationMode']
        && request.resource.data.keys().hasOnly([
          'value', 'updatedAt', 'revision', 'deletedAt', 'originDeviceId', 'schema'
        ])
        && (
          (key in ['HapticsEnabled', 'VolumeButtonsControlTv']
            && request.resource.data.value is bool)
          || (key == 'NavigationMode'
            && request.resource.data.value in ['Directional', 'Pointer'])
        );
    }
    match /users/{uid}/tvs/{tvId} {
      allow read: if owns(uid);
      allow create: if owns(uid) && noSecrets() && metaOk() && tvFields();
      allow update: if owns(uid) && noSecrets() && metaOk() && tvFields()
        && strictlyNewer();
      allow delete: if false;
    }
    match /users/{uid}/favourites/{id} {
      allow read: if owns(uid);
      allow create: if owns(uid) && noSecrets() && metaOk() && favouriteFields();
      allow update: if owns(uid) && noSecrets() && metaOk() && favouriteFields()
        && strictlyNewer();
      allow delete: if false;
    }
    match /users/{uid}/preferences/{key} {
      allow read: if owns(uid);
      allow create: if owns(uid) && noSecrets() && metaOk() && preferenceFields(key);
      allow update: if owns(uid) && noSecrets() && metaOk() && preferenceFields(key)
        && strictlyNewer();
      allow delete: if false;
    }
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

Rules tests are part of the sync slice. A write containing `token` is denied. A cross-user read is denied. A hard delete is denied. An update that is not strictly newer is denied, including an older live record against a newer tombstone and an older tombstone against a newer live record. A create of an absent document is allowed. A non-UUID `originDeviceId` is denied. Those tests use the Firestore rules emulator. They are the contract for `strictlyNewer`, not a substitute for the worker tests.

## Multi-phone

Each phone stores its own pairing secret and opens its own session. Sync shares names, favourites, and the three preferences. Phone B can show "Lounge" before it has paired, and cannot command until local approval succeeds. The card state is needs-pairing, not a dead remote that still sends keys.

Phone A offline keeps commanding. Mutations stay in Room or DataStore with `pendingSync` and the tuple written at mutation time. When the network returns, the worker compares each pending record with the current remote version before writing. Firestore downtime does not close Phone A's session.

A delete on Phone A tombstones the non-secret record. Phone B stores that tombstone when it wins the tuple comparison. Phone B does not unpair unless a later human decision says so. Until then, the cross-device unpair holding behavior applies.

## Account and sync lifecycle

```mermaid
stateDiagram-v2
  [*] --> FirstSession: no successful control yet
  FirstSession --> SignedIn: signs in, uid new or same
  FirstSession --> ContinuedUse: first control succeeded, no user
  ContinuedUse --> SignedIn: signs in, uid new or same
  ContinuedUse --> Holding: different uid
  SignedIn --> ContinuedUse: sign out
  SignedIn --> Holding: auth user replaced by different uid
  Holding --> ContinuedUse: sign out
  SignedIn --> SignedIn: cloud down, local user remains
```

`ContinuedUse` means the product requirement now applies: sign-in is required for continued/full use and for sync. The transition is not a cold-start rule, a next-navigation rule, or an interrupt of the remote.

```mermaid
sequenceDiagram
  participant UI
  participant Local as RoomOrDataStore
  participant Worker as SyncWorker
  participant Cloud as Firestore
  participant TV as Television
  UI->>Local: Mutate value and version together
  UI->>TV: command
  Note over UI,TV: command does not wait for Worker
  Local->>Worker: enqueue
  Worker->>Cloud: Read current version in a transaction
  alt Local tuple strictly newer, or remote absent
    Worker->>Cloud: Write the stored tuple
    Cloud-->>Worker: Commit against the version just read
    Worker->>Local: Clear pendingSync if that tuple is still current
  else Remote newer or equal
    Worker->>Local: Apply remote or clear pendingSync
    Note over Worker,Cloud: Stale tuple is not written
  end
  UI->>Local: observe
```

## Client configuration

Commit the Firebase Android client config. Treat the API key as a restricted client key (package name and signing certificate), not as a service-account secret. Service-account private keys and Play upload keys stay in the CI secret store, not in git. See [release.md](release.md).
