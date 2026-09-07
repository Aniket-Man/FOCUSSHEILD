package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.vector.ImageVector

enum class SessionMode(
    val title: String,
    val subtitle: String,
    val description: String,
    val defaultMinutes: Int
) {
    TIMER(
        title = "Timer",
        subtitle = "Fixed-duration countdown",
        description = "Set a goal and study with zero distractions until the countdown ends.",
        defaultMinutes = 60
    ),
    STOPWATCH(
        title = "Stopwatch",
        subtitle = "Track open-ended study time",
        description = "Track continuous study time with no pressure. Stop when you're done.",
        defaultMinutes = 0
    ),
    POMODORO(
        title = "Pomodoro",
        subtitle = "Focus and break cycles",
        description = "Classic 25 min study + 5 min rest intervals to maintain high energy.",
        defaultMinutes = 25
    );

    val label: String
        get() = title

    val icon: ImageVector
        get() = when (this) {
            TIMER -> Icons.Default.HourglassBottom
            STOPWATCH -> Icons.Default.PlayArrow
            POMODORO -> Icons.Default.Sync
        }
}
