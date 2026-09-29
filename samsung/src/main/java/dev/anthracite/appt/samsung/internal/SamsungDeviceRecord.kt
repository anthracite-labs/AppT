package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvId

/**
 * The samsung-private device record for one television
 * (docs/architecture/data.md#samsung-private-device-record).
 *
 * It is how a remembered television is reopened at its last address after process death, and how
 * address changes are remembered, without putting an address, MAC, UUID or display name into Room.
 * It never contains the token or the pin. The file carries a format version; unknown members are
 * ignored on read and missing ones fall back to safe defaults
 * (data.md#samsung-private-file-migration).
 *
 * The capability evidence this slice can honestly write is the adopted control path itself: the TLS
 * flag, the adopted-channel flag, and whether the television exposes a stable identity. The rest of
 * the record's documented members — rejected keys, wake-failure count, model, firmware, MAC — are
 * written by the slices that produce and read that evidence (S11, S12, S13); the format version and
 * `ignoreUnknownKeys` are exactly what lets those members arrive without a migration.
 *
 * @property uuid the television's normalized protocol UUID, or null when it exposes none. The
 *   plaintext channel's saved security identity (connection.md#security-identity).
 * @property lastAddress the address the television last answered on. Internal only.
 * @property tls true when the pairing was approved on the adopted TLS remote channel, so a saved
 *   pin exists or is expected; false when the pairing rides the plaintext fallback.
 * @property adoptedChannel true when the television speaks the adopted control channel at all.
 * @property displayName the candidate display name from device-info, scrubbed, or null.
 * @property stableIdentity true when the television exposed a protocol UUID.
 */
internal data class SamsungDeviceRecord(
    val uuid: String?,
    val lastAddress: String,
    val tls: Boolean,
    val adoptedChannel: Boolean,
    val displayName: String?,
    val stableIdentity: Boolean,
    val version: Int = DEVICE_RECORD_VERSION,
) {
    companion object {
        /** data.md#samsung-private-file-migration: the file format this writer owns. */
        const val DEVICE_RECORD_VERSION = 1
    }
}

/**
 * The durable Samsung pairing store seam (docs/architecture/data.md#storage-classes).
 *
 * Production is [KeystoreSamsungStore]; the scripted test adapter is in-memory, with a mode that
 * fails decryption (docs/architecture/testing.md#fake-adapters). This is internal to the `samsung`
 * module: `app` has no API that accepts the ciphertext type and does not build these paths
 * (data.md#samsung-secret-record).
 */
internal interface SamsungSecretStore {
    /**
     * Reads one television's saved secret. A missing file is [StoredSecret.Absent]; an
     * undecryptable one is [StoredSecret.Unavailable], never an exception and never a fabricated
     * empty pairing.
     */
    fun loadSecret(tvId: TvId): StoredSecret

    /**
     * Encrypts and atomically replaces the saved secret (temporary file in the same directory,
     * fsync, rename). Plaintext is never staged outside this call.
     */
    fun saveSecret(tvId: TvId, secret: PairingSecret)

    /**
     * Deletes the saved secret for one television. Used by the explicit `confirmRepair` re-pair.
     * The samsung-private device record is kept: address continuity is not approval material.
     * Idempotent. Throws only when deletion genuinely failed, so the caller stays fail-closed.
     */
    fun discardSecret(tvId: TvId)

    /** Reads one television's samsung-private device record, or null when there is none. */
    fun loadDevice(tvId: TvId): SamsungDeviceRecord?

    /** Atomically replaces one television's samsung-private device record. */
    fun saveDevice(tvId: TvId, record: SamsungDeviceRecord)

    /**
     * Removes this phone's saved Samsung relationship for one television: the secret and the
     * samsung-private device record. Idempotent. Throws only when deletion genuinely failed.
     */
    fun forget(tvId: TvId)

    /** The ids that have a samsung-private record. Not a UI list and not an account concept. */
    fun rememberedIds(): Set<TvId>
}
