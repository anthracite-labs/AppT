package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvId

/**
 * The scripted [SamsungSecretStore] (docs/architecture/testing.md#fake-adapters): in-memory, with a
 * mode that fails decryption, a mode that fails writes, and a mode that fails deletion, so the
 * session machine's fail-closed paths are reachable without a Keystore.
 */
internal class InMemorySamsungStore(
    var failDecryption: Boolean = false,
    var failWrites: Boolean = false,
    var failDeletion: Boolean = false,
) : SamsungSecretStore {

    /** Every [saveSecret] payload, in order. */
    val savedSecrets = mutableListOf<Pair<TvId, PairingSecret>>()

    /** Every [saveDevice] record, in order. */
    val savedDevices = mutableListOf<Pair<TvId, SamsungDeviceRecord>>()

    /** Ids whose secret was discarded through [discardSecret]. */
    val discarded = mutableListOf<TvId>()

    /** Ids passed to [forget]. */
    val forgotten = mutableListOf<TvId>()

    private val secrets = mutableMapOf<TvId, PairingSecret>()
    private val devices = mutableMapOf<TvId, SamsungDeviceRecord>()
    private var corrupt: MutableSet<TvId> = mutableSetOf()

    fun corruptSecret(tvId: TvId) {
        corrupt += tvId
    }

    fun repairSecret(tvId: TvId) {
        corrupt -= tvId
    }

    fun saveRawSecret(tvId: TvId, secret: PairingSecret) {
        secrets[tvId] = secret
    }

    fun saveRawDevice(tvId: TvId, record: SamsungDeviceRecord) {
        devices[tvId] = record
    }

    override fun loadSecret(tvId: TvId): StoredSecret =
        when {
            tvId in corrupt -> StoredSecret.Unavailable
            else ->
                secrets[tvId]?.let { secret ->
                    if (failDecryption) StoredSecret.Unavailable
                    else StoredSecret.Available(secret)
                } ?: StoredSecret.Absent
        }

    override fun saveSecret(tvId: TvId, secret: PairingSecret) {
        if (failWrites) throw SecretStoreException("scripted write failure")
        secrets[tvId] = secret
        savedSecrets += tvId to secret
    }

    override fun discardSecret(tvId: TvId) {
        if (failDeletion) throw SecretStoreException("scripted deletion failure")
        secrets.remove(tvId)
        discarded += tvId
    }

    override fun loadDevice(tvId: TvId): SamsungDeviceRecord? = devices[tvId]

    override fun saveDevice(tvId: TvId, record: SamsungDeviceRecord) {
        if (failWrites) throw SecretStoreException("scripted write failure")
        devices[tvId] = record
        savedDevices += tvId to record
    }

    override fun forget(tvId: TvId) {
        if (failDeletion) throw SecretStoreException("scripted deletion failure")
        secrets.remove(tvId)
        devices.remove(tvId)
        corrupt -= tvId
        forgotten += tvId
    }

    override fun rememberedIds(): Set<TvId> = devices.keys.toSet()
}
