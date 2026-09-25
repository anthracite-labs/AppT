package dev.anthracite.appt.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The device-local application database (data.md#room): `appt.db`, version 1.
 *
 * Version 1 has one entity, so there is no migration yet; every later version ships an explicit
 * `Migration` and a migration test. `fallbackToDestructiveMigration` is deliberately absent: losing
 * a user's television names is a visible loss, not a repair.
 */
@Database(entities = [TvProfile::class], version = AppTDatabase.VERSION, exportSchema = true)
abstract class AppTDatabase : RoomDatabase() {
    abstract fun tvProfileDao(): TvProfileDao

    companion object {
        /** data.md: the current schema version. Bumps require an explicit migration. */
        const val VERSION = 1

        /** data.md: the database file name in the application database directory. */
        const val NAME = "appt.db"
    }
}
