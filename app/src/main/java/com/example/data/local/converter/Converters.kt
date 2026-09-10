package com.example.data.local.converter

import androidx.room.TypeConverter
import com.example.data.local.entity.BlockedEventSource
import com.example.data.local.entity.BlockedEventType
import com.example.data.local.entity.StudyActivitySource
import com.example.data.local.entity.StudyActivityType
import com.example.data.model.SessionMode

class Converters {
    @TypeConverter
    fun fromSessionMode(mode: SessionMode?): String? {
        return mode?.name
    }

    @TypeConverter
    fun toSessionMode(value: String?): SessionMode? {
        return value?.let {
            try {
                SessionMode.valueOf(it)
            } catch (e: Exception) {
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
                BlockedEventSource.LEGACY
            }
        }
    }
}
