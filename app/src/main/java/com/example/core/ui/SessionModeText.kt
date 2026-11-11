package com.example.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.SessionMode

/** Localised mode name. The enum keeps stable English keys for storage; only the UI is translated. */
@Composable
fun sessionModeTitle(mode: SessionMode): String = stringResource(
    when (mode) {
        SessionMode.TIMER -> R.string.schedule_mode_timer
        SessionMode.STOPWATCH -> R.string.schedule_mode_stopwatch
        SessionMode.POMODORO -> R.string.schedule_mode_pomodoro
    }
)

@Composable
fun sessionModeSubtitle(mode: SessionMode): String = stringResource(
    when (mode) {
        SessionMode.TIMER -> R.string.session_mode_timer_subtitle
        SessionMode.STOPWATCH -> R.string.session_mode_stopwatch_subtitle
        SessionMode.POMODORO -> R.string.session_mode_pomodoro_subtitle
    }
)

@Composable
fun sessionModeDescription(mode: SessionMode): String = stringResource(
    when (mode) {
        SessionMode.TIMER -> R.string.session_mode_timer_description
        SessionMode.STOPWATCH -> R.string.session_mode_stopwatch_description
        SessionMode.POMODORO -> R.string.session_mode_pomodoro_description
    }
)
