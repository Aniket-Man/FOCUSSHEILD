package com.example.feature.planner

import androidx.compose.runtime.saveable.SaverScope
import com.example.data.local.entity.FocusScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleEditorStateTest {
    @Test
    fun `editor identity and every saved field survive restoration including an empty app set`() {
        val original = FocusScheduleEntity(id = "stable-id", title = "Night study", daysOfWeek = "MON,SUN",
            startTime = "23:00", endTime = "01:00", isEnabled = false, isAutoStartSession = true,
            mode = "POMODORO", subjectName = "Custom tag", colorHex = "#123456", repeatEnabled = false,
            scheduledDateMillis = 12345L, breakMinutes = 12, description = "Revision", blockedAppPackages = "",
            blockNotifications = true, createdAt = 9876L)
        val scope = object : SaverScope { override fun canBeSaved(value: Any): Boolean = true }
        val saved = with(FocusScheduleSnapshotSaver) { scope.save(original) }!!
        assertEquals(original, FocusScheduleSnapshotSaver.restore(saved))
    }
}
