package com.example.feature.planner

import androidx.compose.runtime.saveable.listSaver
import com.example.data.local.entity.FocusScheduleEntity

/** Keep the editing identity as well as the form when the activity is recreated. */
internal val FocusScheduleSnapshotSaver = listSaver<FocusScheduleEntity?, Any>(
    save = { schedule ->
        schedule?.let {
            listOf(it.id, it.title, it.daysOfWeek, it.startTime, it.endTime, it.isEnabled,
                it.isAutoStartSession, it.mode, it.subjectName, it.colorHex, it.repeatEnabled,
                it.scheduledDateMillis, it.breakMinutes, it.description, it.blockedAppPackages,
                it.blockNotifications, it.createdAt)
        }.orEmpty()
    },
    restore = { values ->
        if (values.size != 17) null else FocusScheduleEntity(
            id = values[0] as String,
            title = values[1] as String,
            daysOfWeek = values[2] as String,
            startTime = values[3] as String,
            endTime = values[4] as String,
            isEnabled = values[5] as Boolean,
            isAutoStartSession = values[6] as Boolean,
            mode = values[7] as String,
            subjectName = values[8] as String,
            colorHex = values[9] as String,
            repeatEnabled = values[10] as Boolean,
            scheduledDateMillis = values[11] as Long,
            breakMinutes = values[12] as Int,
            description = values[13] as String,
            blockedAppPackages = values[14] as String,
            blockNotifications = values[15] as Boolean,
            createdAt = values[16] as Long
        )
    }
)
