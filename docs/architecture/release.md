# CI, release, and supply chain

The accepted baseline is GitHub Actions, Gradle, Android App Bundle, Play App Signing, internal testing, then staged production. This file makes that implementable. The workflow files themselves are created by slices, not by the architecture map.

## Gradle

- Version catalog `gradle/libs.versions.toml`. Exact versions are pinned when the skeleton slice runs. This map names the set and does not freeze today's numbers into prose, where they would go stale.
- No `+` ranges. No snapshot dependencies. No dynamic versions.
- `dependencyResolutionManagement.repositoriesMode = FAIL_ON_PROJECT_REPOS`.
- Repositories: `google()` and `mavenCentral()` for dependencies. Plugin portal only under `pluginManagement`.
- No JitPack and no ad-hoc Maven URLs in V1.
- Dependency locking enabled. Lockfiles committed.
- Dependency verification metadata committed. CI uses strict verification.
- Updates arrive as reviewed pull requests. A bot may open those PRs later. Silent upgrades are not allowed. Adding a bot is not an architecture change.

### Dependency set

Runtime: Kotlin, Android Gradle Plugin, Compose BOM, Navigation Compose, kotlinx-serialization, Coroutines, Lifecycle, Hilt, Room, DataStore, OkHttp, WorkManager, Firebase BOM artifacts `firebase-auth`, `firebase-firestore`, `firebase-crashlytics`, and App Check with Play Integrity, Credential Manager.

Test: JUnit, coroutines-test, Compose UI test, AndroidX test, Firestore rules tests as needed.

Not in the graph: `firebase-analytics`, advertising SDKs, ACRA, Whisperlink, Cast, Consumer IR, and any Samsung reference library. Protocol code is written in this repository.

`samsung` depends on OkHttp, Coroutines, and the Android APIs it uses. It does not depend on the Firebase BOM.

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
9. `:samsung` dependency insight does not include Firebase, Play services, or Crashlytics.

`main` stays releasable. A red check is not merged.

### Manifest allowlist

```text
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
CHANGE_WIFI_MULTICAST_STATE
```

Anything else fails CI until the architecture map is changed. In particular the allowlist does not include `AD_ID`, `ACCESS_FINE_LOCATION`, `ACCESS_LOCAL_NETWORK`, `NEARBY_WIFI_DEVICES`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, or install-packages. `NEARBY_WIFI_DEVICES` is added only in the change that adopts a Wi-Fi API which requires it, with `neverForLocation`. A target-37 bump must change this allowlist in the same change that adopts `ACCESS_LOCAL_NETWORK`. See [discovery.md](discovery.md).

## Release path

Release artifact is an Android App Bundle. Production signing is Play App Signing. The upload key used by CI is a GitHub Actions secret or Play's upload mechanism. It is not committed.

Workflows, manual dispatch only:

| Workflow | Result |
|---|---|
| Internal upload | Publishes the AAB to the Play internal testing track |
| Production promote | Promotes a build that has already passed internal testing, using Play staged rollout |

Staged rollout starts below 100 percent. Promotion is a human action. Halt if the crash-free rate drops. Exact percentages are release operations, not product intent, and are chosen at promote time within Play's staged rollout.

Play internal testing and Play production are the V1 channels. F-Droid and GitHub Releases are not committed channels.

Internal testing may proceed before the source-license confirmation. Public production promotion may not. Two external gates sit on production promotion and are not slices:

- Human confirmation of the final source license. The tree contains an MIT `LICENSE`. `docs/HARVEST.md` still records the final license as undecided. Do not treat the file as closing that decision.
- Focused Samsung vendor-terms review, already required before public release.

## Provenance

New significant dependencies need a license and provenance note in the pull request that adds them. Prefer maintained, auditable libraries with narrow network behavior. Reject embedded credentials, tracker SDKs, unnecessary listeners, and opaque proprietary control-path blobs.

Incompatible-license Samsung reference code stays reference. Do not vendor it. The clean-room rule is in [protocol.md](protocol.md).

R8 is on for release. Keep rules cover Room, Firebase, and Kotlin serialization. Crashlytics mapping upload runs in the release workflow.

## Supply-chain checks that protect the invariants

| Check | Protects |
|---|---|
| `samsung` has no Firebase | Local control does not need the cloud |
| Permission allowlist | No surprise LAN listener, location, or ads id |
| Fixture grep | Secrets and addresses do not enter git |
| No `Log` in `samsung` | Tokens do not reach logcat by accident |
| Locked, verified dependencies | Unreviewed protocol or tracker code does not slide in |
| Action SHA pins | CI cannot be retargeted by a moving tag |
