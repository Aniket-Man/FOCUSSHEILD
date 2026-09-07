package com.zenlock.focusguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zenlock.focusguard.data.db.dao.BlockedAppDao
import com.zenlock.focusguard.data.db.dao.FocusSessionDao
import com.zenlock.focusguard.data.db.dao.KeywordDao
import com.zenlock.focusguard.data.db.entity.BlockedAppEntity
import com.zenlock.focusguard.data.db.entity.FocusSessionEntity
import com.zenlock.focusguard.data.db.entity.KeywordEntity

/**
 * Room database for FocusGuard.
 * Stores blocked apps, focus sessions, and keyword rules.
 */
@Database(
    entities = [
        BlockedAppEntity::class,
        FocusSessionEntity::class,
        KeywordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FocusGuardDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun keywordDao(): KeywordDao

    companion object {
        @Volatile
        private var INSTANCE: FocusGuardDatabase? = null

        fun getInstance(context: Context): FocusGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusGuardDatabase::class.java,
                    "focusguard_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
