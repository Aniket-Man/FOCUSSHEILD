package com.example.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R

/** Display only: persisted tag names remain unchanged for existing schedules and session history. */
@Composable
fun localizedSessionTag(tag: String): String = when (tag) {
    "Untagged" -> stringResource(R.string.schedule_untagged)
    "Study" -> stringResource(R.string.schedule_preset_study)
    "Exam" -> stringResource(R.string.schedule_preset_exam)
    "Revision" -> stringResource(R.string.schedule_preset_revision)
    "Deep Work" -> stringResource(R.string.schedule_preset_deep_work)
    else -> tag
}
