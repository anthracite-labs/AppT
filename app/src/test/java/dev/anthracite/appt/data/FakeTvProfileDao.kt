package dev.anthracite.appt.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [TvProfileDao], so the profile behaviour is a unit test rather than a Room test.
 *
 * The real DAO is exercised against a real in-memory database by [TvProfileDaoTest]; this one keeps
 * the ownership rules (name source, timestamps, last-opened) testable without SQLite.
 */
class FakeTvProfileDao(initial: List<TvProfile> = emptyList()) : TvProfileDao {
    private val rows = MutableStateFlow(initial.associateBy { it.tvId })

    val upserted = mutableListOf<TvProfile>()

    override suspend fun upsert(profile: TvProfile) {
        upserted += profile
        rows.value = rows.value + (profile.tvId to profile)
    }

    override suspend fun find(tvId: String): TvProfile? = rows.value[tvId]

    override fun observe(tvId: String): Flow<TvProfile?> = rows.map { it[tvId] }

    fun current(): List<TvProfile> = rows.value.values.toList()
}
