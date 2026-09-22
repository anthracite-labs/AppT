# CI, release, environments, and supply chain

The accepted baseline is GitHub Actions, Gradle, Android App Bundle, Play App Signing, internal testing, then staged production. This file makes that implementable and adds the environment separation the account/licensing architecture needs. Workflow files themselves are created by slices, not by the architecture map.

## Gradle

- Version catalog `gradle/libs.versions.toml`. Exact versions are pinned when the skeleton slice runs. This map names the set and does not freeze today's numbers into prose, where they would go stale.
- No `+` ranges. No snapshot dependencies. No dynamic versions.
- `dependencyResolutionManagement.repositoriesMode = FAIL_ON_PROJECT_REPOS`.
- Repositories: `google()` and `mavenCentral()`. Plugin portal only under `pluginManagement`.
- No JitPack and no ad-hoc Maven URLs in V1.
- Dependency locking enabled. Lockfiles committed.
- Dependency verification metadata committed. CI uses strict verification.
- Updates arrive as reviewed pull requests. Silent upgrades are not allowed.

### Dependency set

Runtime: Kotlin, Android Gradle Plugin, Compose BOM, Navigation Compose, kotlinx-serialization, Coroutines, Lifecycle, Hilt, Room, DataStore, OkHttp, WorkManager, Firebase BOM artifacts `firebase-auth`, `firebase-appcheck-playintegrity`, `firebase-crashlytics`, Play Billing, Play Integrity, Credential Manager.

Test: JUnit, coroutines-test, Compose UI test, AndroidX test, Macrobenchmark, Robolectric for DataStore and migration tests, Firebase emulator suite for backend functions.

Not in the graph: `firebase-firestore` (server-only; the client never talks to Firestore directly), `firebase-analytics`, advertising SDKs, ACRA, Whisperlink, Cast, Consumer IR, and any Samsung reference library. Protocol code is written in this repository.

`samsung` stays free of the Firebase BOM, Play Billing, and Play Integrity. The dependency boundary itself is owned by [modules.md](modules.md#dependency-set-boundaries) and enforced by the CI checks in that file.

## Environments

Three environments, three isolated Google Cloud and Firebase projects, one Play app.

| Concern | `dev` | `internal` | `production` |
|---|---|---|---|
| Purpose | Local development, emulator suite, unit and function tests | Internal-testing track builds and end-to-end checks on real devices | Customer builds and real purchases |
| Firebase / Google Cloud project | `appt-dev` | `appt-internal` | `appt-prod` |
| Firebase Authentication | Emulator plus dev project providers | Internal project providers | Production providers, hardened settings |
| Firestore instance | Emulator plus dev project | Internal project | Production project, daily backups enabled |
| Entitlement service | Emulator plus dev functions | Internal functions | Production functions |
| Marker and fingerprint keys (Secret Manager) | Development key versions | Internal key versions | Production key versions, separate rotation schedule |
| Proof signing key (Cloud KMS) | Development key | Internal key | Production key, non-exportable, distinct key set |
| App Check | Debug provider with registered debug tokens | Play Integrity provider, enforcement on | Play Integrity provider, enforcement on |
| Play Billing | Fake adapter; no real billing | Play internal testing track with licence testers | Play production |
| Play Integrity | Fake verdicts | Real verdicts, internal package | Real verdicts, production package |
| Crashlytics | Disabled; no upload | Internal project, opt-in required in app | Production project, opt-in required in app |
| Backend URL resolved by app | `dev` | `internal` | `production` |

Isolation rules:

- A debug build can never resolve production identifiers, and a release build can never resolve development identifiers. A CI check fails if either is possible.
- Production credentials never appear in the repository. CI authenticates to Google Cloud with Workload Identity Federation; repository secrets hold no long-lived Google key.
- Each environment has its own service accounts with least privilege: the verification function holds only the Play publisher scope it needs, and the notification handler can read and write only its own collections.
- Development and internal environments may use narrower integrity enforcement so engineers are not blocked; production enforcement is not weakened.

### Play and RTDN are shared by design

The Play app and its product catalogue are single: there is no separate Play product set for development, internal testing, or production.

- One Play app means one package name and one product id for the lifetime unlock everywhere.
- Real-time developer notifications are configured once per Play app and evaluate one Pub/Sub topic, so one-time product and voided purchase events land in the production notification pipeline regardless of the track that generated them.
- The Android application id is therefore the same across environments. Environment separation happens in Firebase/Cloud project, backend URL, and signing configuration, not in the Play product.
- Licence-test purchases are identified through the Developer API `purchaseType` field and never grant a durable production Lifetime Entitlement, so an internal tester cannot accidentally create a paid production account.
- A test purchase made through a licence-tester account still produces real Play records. The pipeline treats them as verification evidence with a test verdict, which is exactly the case `testPurchaseDoesNotGrantLifetime` covers.

Needs validation before implementation relies on it: the current Play Console model for RTDN topics and whether multiple topics per app are supported, and the exact `purchaseType` behaviour for each non-standard flow. Both are listed in [README.md](README.md#needs-validation).

## Backend deployment and rollback

| Aspect | Approach |
|---|---|
| Deploy mechanism | GitHub Actions deploys functions, Firestore rules, and indexes with Workload Identity Federation. Manual dispatch for production, automatic or manual for `dev` and `internal` |
| Artifact identity | A deployment records the commit SHA and function revision; production deploys are tagged |
| Rollout | Cloud Functions revisions with traffic split. A new revision starts at 0% traffic in production, then moves after the smoke checks pass |
| Rollback | Shift traffic back to the previous revision; no code change and no data migration required. If a rollback still needs a data fix, the two-step migration below supplies it |
| Rules and indexes | Deployed as versioned artifacts in the same pipeline as the code that depends on them |
| Schema changes | Additive first: add the field, deploy readers, backfill, then stop writing the old field. A destructive change is a separate, later deployment |
| Secrets | Secret Manager versions; a rotation adds a version and retires the old one only after the new one is proven |
| Backups | Firestore scheduled backups for the production instance, with a documented restore drill |
| Observability | Function error rate, verification failures, and revocation events are alerting signals. No behavioral analytics, no per-user event stream |
| Incident response | Revocation state can be corrected by an audited support action; an outage degrades to cached-proof behavior by design, not to broken remotes |

## Pull-request checks

Workflow on pull request and on `main`, actions pinned to immutable commit SHAs with the version tag in a comment:

1. Assemble debug.
2. Unit tests.
3. Android lint.
4. Dependency lock check.
5. Strict dependency verification.
6. Manifest permission allowlist.
7. Fixture redaction grep.
8. `samsung` source does not reference `android.util.Log` or Firebase packages.
9. `:samsung` dependency insight excludes Firebase, Play services, and Crashlytics.
10. `:app` dependency insight excludes any Firestore client artifact.
11. No `firebase-analytics`, advertising SDK, or `AD_ID` in the merged manifest.
12. Production sources contain no sync record, tombstone, or mutation queue.
13. Environment guard: debug cannot resolve production identifiers; release cannot resolve development identifiers.
14. Secret scanning over the tree and the diff.
15. Backend function tests and rules tests against the emulator suite.
16. Accessibility assertions on controls touched by the change.

`main` stays releasable. A red check is not merged.

### Manifest allowlist

```text
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
CHANGE_WIFI_MULTICAST_STATE
com.android.vending.BILLING
```

Anything else fails CI until the architecture map is changed. In particular the allowlist does not include `AD_ID`, `ACCESS_FINE_LOCATION`, `ACCESS_LOCAL_NETWORK`, `NEARBY_WIFI_DEVICES`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, or install-packages. `NEARBY_WIFI_DEVICES` is added only in the change that adopts a Wi-Fi API which requires it, with `neverForLocation`. A target-37 bump must change this allowlist in the same change that adopts `ACCESS_LOCAL_NETWORK`. See [discovery.md](discovery.md).

## Release path

The release artifact is an Android App Bundle. Production signing is Play App Signing. The upload key used by CI is a GitHub Actions secret or Play's upload mechanism. It is not committed.

Workflows, manual dispatch only:

| Workflow | Result |
|---|---|
| Internal upload | Publishes the AAB to the Play internal testing track, pointed at the `internal` backend |
| Production promote | Promotes a build that has already passed internal testing, pointed at the `production` backend, using Play staged rollout |

Staged rollout starts below 100 percent. Promotion is a human action. Halt if the crash-free rate drops or an entitlement error rate rises. Exact percentages are release operations, not product intent, and are chosen at promote time within Play's staged rollout.

Play internal testing and Play production are the V1 channels. F-Droid and GitHub Releases are not committed channels.

R8 is on for release. Keep rules cover Room, Firebase, Kotlin serialization, Play Billing, and Credential Manager. Crashlytics mapping upload runs in the release workflow; it contains no user data.

Crash-free measurement caveat: opt-in collection reduces crash-free metric accuracy, because users who have not opted in are unrepresented. The release checklist records this so a low reported rate is not mistaken for a regression.

## Two external gates

Two gates sit on public production promotion and are not slices:

- Human confirmation of the final source license. The tree contains an MIT `LICENSE`. `docs/HARVEST.md` still records the final license as undecided. Do not treat the file as closing that decision.
- Focused Samsung vendor-terms review, already required before public release.

Internal testing may proceed before those gates. Public production promotion may not.

## Provenance

New significant dependencies need a license and provenance note in the pull request that adds them. Prefer maintained, auditable libraries with narrow network behavior. Reject embedded credentials, tracker SDKs, unnecessary listeners, and opaque proprietary control-path blobs.

Incompatible-license Samsung reference code stays reference. Do not vendor it. The clean-room rule is in [protocol.md](protocol.md).

## Supply-chain checks that protect the invariants

| Check | Protects |
|---|---|
| `samsung` has no Firebase or Play artifact | Local control does not need the cloud |
| `app` has no Firestore client | No client-side account or television data path exists |
| Permission allowlist | No surprise LAN listener, location, or ads id |
| Fixture grep | Secrets and addresses do not enter git |
| No `Log` in `samsung` | Tokens do not reach logcat by accident |
| Locked, verified dependencies | Unreviewed protocol or tracker code does not slide in |
| Action SHA pins | CI cannot be retargeted by a moving tag |
| Environment guard | A test build cannot touch production data or purchases |
| Secret scanning | No long-lived credential enters the tree |
| Backend schema test on forbidden fields | No television or personalization field can be added silently |
