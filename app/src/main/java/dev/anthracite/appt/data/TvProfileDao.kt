package dev.anthracite.appt.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The `TvProfile` store (data.md#room). */
@Dao
interface TvProfileDao {
    /**
     * Inserts or replaces the profile for [TvProfile.tvId]. An upsert is what makes re-selecting a
     * card idempotent, and it is one statement, so no intermediate state re-exposes the row.
     */
    @Upsert suspend fun upsert(profile: TvProfile)

    @Query("SELECT * FROM tv_profiles WHERE tvId = :tvId")
    suspend fun find(tvId: String): TvProfile?

    @Query("SELECT * FROM tv_profiles WHERE tvId = :tvId")
    fun observe(tvId: String): Flow<TvProfile?>
}
