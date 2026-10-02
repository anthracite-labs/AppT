package dev.anthracite.appt.samsung.internal

/**
 * The saved protocol UUID compared against the television's current one
 * (connection.md#security-identity: the plaintext channel's identity is the UUID saved with the
 * pairing, so a different television answering for the same id fails closed).
 */
internal fun savedUuidIdentityChanged(
    secrets: SamsungSecretStore,
    television: ConfirmedTelevision,
): Boolean {
    val savedUuid = secrets.loadDevice(television.id)?.uuid ?: return false
    val presentUuid = television.uuid ?: return false
    return savedUuid != presentUuid
}

/**
 * The approval write: the samsung-private device record first, then the token and pin as one atomic
 * secret write (data.md#samsung-secret-record). A completed secret write therefore always has a
 * device record beside it, so reopen and address continuity work.
 */
internal fun persistPairing(
    secrets: SamsungSecretStore,
    television: ConfirmedTelevision,
    token: String?,
    pin: String?,
) {
    secrets.saveDevice(
        television.id,
        SamsungDeviceRecord(
            uuid = television.uuid,
            lastAddress = television.host,
            tls = television.tls,
            adoptedChannel = television.adoptedChannel,
            displayName = television.displayName,
            stableIdentity = television.uuid != null,
        ),
    )
    secrets.saveSecret(television.id, PairingSecret(token = token, pin = pin))
}
