package dev.anthracite.appt.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One remembered television (docs/architecture/data.md#tvprofile).
 *
 * The row is device-local application data: a friendly name, where that name came from, whether the
 * television supplied a stable identity, and when the row was created and last opened. It holds no
 * address, host, port, MAC, SSID, token, certificate, command or text — the forbidden-column list in
 * data.md is enforced by `schemaContainsNoForbiddenColumn` against the exported schema.
 *
 * Samsung-private reconnect evidence (address, MAC, protocol UUID, capability evidence) lives in
 * `samsung`'s own device record, not here, and the pairing token and SPKI pin live in the keystore
 * secret store that arrives with S04.
 *
 * @property tvId the opaque identity assigned inside `samsung`; the primary key.
 * @property friendlyName 1–40 characters after trim, or null while no name exists.
 * @property nameSource `TV` when the row was created from the television's own name, `USER` once a
 *   rename has happened. A later television-reported name never overwrites `USER`.
 * @property stableIdentity true when the television supplied the identity, so the same television
 *   returns as the same `TvId` after rediscovery.
 * @property createdAt device millis, set when the row is first inserted.
 * @property lastOpenedAt device millis, written when a session for this television reaches `Ready`.
 */
@Entity(tableName = "tv_profiles")
data class TvProfile(
    @PrimaryKey @ColumnInfo(name = "tvId") val tvId: String,
    @ColumnInfo(name = "friendlyName") val friendlyName: String?,
    @ColumnInfo(name = "nameSource") val nameSource: NameSource,
    @ColumnInfo(name = "stableIdentity") val stableIdentity: Boolean,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "lastOpenedAt") val lastOpenedAt: Long?,
)

/** Where a profile's friendly name came from (data.md: `nameSource` starts as `TV`). */
enum class NameSource {
    TV,
    USER,
}
