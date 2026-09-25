package dev.anthracite.appt.data

import dev.anthracite.appt.samsung.TvId

/**
 * The application-facing `TvProfile` store (data.md#ownership-rules).
 *
 * Remembering a television is one Room statement: the user selects a controllable card, this upserts
 * the profile, and only then does `ActiveRemoteHost` open the session. Nothing here reads a network
 * source, and nothing here can hold a pairing secret.
 *
 * @property now device millis, injected so tests are not on the wall clock.
 */
class TvProfiles(private val dao: TvProfileDao, private val now: () -> Long = System::currentTimeMillis) {

    /**
     * Upserts the profile row for a television the user selected.
     *
     * A row that already exists keeps its creation time and its `USER` name: a television-reported
     * name never overwrites one the user typed. An `Unsupported` television never reaches here, so no
     * row is created for one.
     *
     * @param friendlyName the name the screen already decided to show, which is the
     *   identifier-checked form of the television's own name (discovery.DisplayLabel).
     */
    suspend fun rememberSelected(tvId: TvId, friendlyName: String, stableIdentity: Boolean) {
        val existing = dao.find(tvId.value)
        val keptName = existing?.takeIf { it.nameSource == NameSource.USER }
        dao.upsert(
            if (keptName != null) keptName.copy(stableIdentity = stableIdentity)
            else
                TvProfile(
                    tvId = tvId.value,
                    friendlyName = storedName(friendlyName),
                    nameSource = NameSource.TV,
                    stableIdentity = stableIdentity,
                    createdAt = existing?.createdAt ?: now(),
                    lastOpenedAt = existing?.lastOpenedAt,
                )
        )
    }

    /** Records that a session for [tvId] reached `Ready` (data.md: written on `Ready`). */
    suspend fun markOpened(tvId: TvId) {
        val existing = dao.find(tvId.value) ?: return
        dao.upsert(existing.copy(lastOpenedAt = now()))
    }

    suspend fun find(tvId: TvId): TvProfile? = dao.find(tvId.value)

    fun observe(tvId: TvId) = dao.observe(tvId.value)

    private companion object {
        /** data.md: 1–40 characters after trim; a name that is only an identifier is not a name. */
        const val NAME_MAX = 40

        fun storedName(reported: String): String? =
            reported.trim().take(NAME_MAX).takeIf { it.isNotEmpty() }
    }
}
