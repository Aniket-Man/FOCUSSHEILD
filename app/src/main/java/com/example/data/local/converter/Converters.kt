package com.example.data.local.converter

import android.util.Log
import androidx.room.TypeConverter
import com.example.data.local.entity.BlockedEventSource
import com.example.data.local.entity.BlockedEventType
import com.example.data.local.entity.StudyActivitySource
import com.example.data.local.entity.StudyActivityType
import com.example.data.model.SessionMode

class Converters {

    private companion object {
        const val TAG = "Converters"
    }

    @TypeConverter
    fun fromSessionMode(mode: SessionMode?): String? {
        return mode?.name
    }

    @TypeConverter
    fun toSessionMode(value: String?): SessionMode? {
        return value?.let {
            try {
                SessionMode.valueOf(it)
            } catch (e: IllegalArgumentException) {
                // A row written by a newer/older schema, or corrupted. Room cannot fail the read
                // (that would take the whole query down), so the documented default is used — but it
                // is logged, because "SessionMode" is a behavioural downgrade, not a cosmetic one.
                Log.w(TAG, "Unknown SessionMode '$it'; falling back to TIMER", e)
                SessionMode.TIMER
            }
        }
    }

    @TypeConverter
    fun fromStudyActivityType(type: StudyActivityType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toStudyActivityType(value: String?): StudyActivityType? {
        return value?.let {
            try {
                StudyActivityType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                // A row written by a newer/older schema, or corrupted. Room cannot fail the read
                // (that would take the whole query down), so the documented default is used — but it
                // is logged, because "StudyActivityType" is a behavioural downgrade, not a cosmetic one.
                Log.w(TAG, "Unknown StudyActivityType '$it'; falling back to FOCUS_SESSION", e)
                StudyActivityType.FOCUS_SESSION
            }
        }
    }

    @TypeConverter
    fun fromStudyActivitySource(source: StudyActivitySource?): String? {
        return source?.name
    }

    @TypeConverter
    fun toStudyActivitySource(value: String?): StudyActivitySource? {
        return value?.let {
            try {
                StudyActivitySource.valueOf(it)
            } catch (e: IllegalArgumentException) {
                // A row written by a newer/older schema, or corrupted. Room cannot fail the read
                // (that would take the whole query down), so the documented default is used — but it
                // is logged, because "StudyActivitySource" is a behavioural downgrade, not a cosmetic one.
                Log.w(TAG, "Unknown StudyActivitySource '$it'; falling back to TIMER", e)
                StudyActivitySource.TIMER
            }
        }
    }

    @TypeConverter
    fun fromBlockedEventType(type: BlockedEventType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toBlockedEventType(value: String?): BlockedEventType? {
        return value?.let {
            try {
                BlockedEventType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                // A row written by a newer/older schema, or corrupted. Room cannot fail the read
                // (that would take the whole query down), so the documented default is used — but it
                // is logged, because "BlockedEventType" is a behavioural downgrade, not a cosmetic one.
                Log.w(TAG, "Unknown BlockedEventType '$it'; falling back to LEGACY", e)
                BlockedEventType.LEGACY
            }
        }
    }

    @TypeConverter
    fun fromBlockedEventSource(source: BlockedEventSource?): String? {
        return source?.name
    }

    @TypeConverter
    fun toBlockedEventSource(value: String?): BlockedEventSource? {
        return value?.let {
            try {
                BlockedEventSource.valueOf(it)
            } catch (e: IllegalArgumentException) {
                // A row written by a newer/older schema, or corrupted. Room cannot fail the read
                // (that would take the whole query down), so the documented default is used — but it
                // is logged, because "BlockedEventSource" is a behavioural downgrade, not a cosmetic one.
                Log.w(TAG, "Unknown BlockedEventSource '$it'; falling back to LEGACY", e)
                BlockedEventSource.LEGACY
            }
        }
    }
}
