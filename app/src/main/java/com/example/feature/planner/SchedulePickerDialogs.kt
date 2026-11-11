package com.example.feature.planner

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusChoiceRow
import com.example.feature.session.notification.ScheduleTime
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Honors Android's locale and 12/24-hour preference without changing the stored HH:mm value. */
@Composable
internal fun scheduleTimeDisplay(value: String): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val is24Hour = DateFormat.is24HourFormat(context)
    val minutes = ScheduleTime.parseToMinutesOrNull(value) ?: return value
    val formatter = remember(locale, is24Hour) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
    }
    return formatter.format(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60)
        set(Calendar.MINUTE, minutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time)
}

@Composable
internal fun scheduleDaysDisplay(value: String): String {
    val days = ScheduleTime.parseDaysOfWeekOrNull(value) ?: return stringResource(R.string.planner_invalid_days)
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val symbols = remember(locale) { DateFormatSymbols(locale) }
    val names = (listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)).filter { it in days }.map { symbols.weekdays[it] }
    return android.icu.text.ListFormatter.getInstance(locale).format(names)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleClockDialog(initialTime: String, title: String, onDismiss: () -> Unit, onTimeSelected: (String) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val is24Hour = DateFormat.is24HourFormat(context)
    val initialMinutes = remember(initialTime) { ScheduleTime.parseToMinutesOrNull(initialTime) }
    val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false } }
    var isAm by rememberSaveable(initialTime) { mutableStateOf(initialMinutes == null || initialMinutes / 60 < 12) }
    var hourText by rememberSaveable(initialTime, is24Hour) {
        mutableStateOf(initialMinutes?.let {
            val hour = it / 60
            numberFormat.format(if (is24Hour) hour else (hour % 12).let { h -> if (h == 0) 12 else h })
        }.orEmpty())
    }
    var minuteText by rememberSaveable(initialTime) { mutableStateOf(initialMinutes?.let { numberFormat.format(it % 60) }.orEmpty()) }
    var hourError by remember { mutableStateOf(false) }
    var minuteError by remember { mutableStateOf(false) }
    val hourFocus = remember { FocusRequester() }
    val minuteFocus = remember { FocusRequester() }
    val hourErrorMessage = stringResource(if (is24Hour) R.string.schedule_time_hour_24_error else R.string.schedule_time_hour_12_error)
    val minuteErrorMessage = stringResource(R.string.schedule_time_minute_error)
    val validated = ScheduleClockInput.validate(hourText, minuteText, is24Hour, isAm)
    val selectedMinute = ScheduleTime.parseToMinutesOrNull(ScheduleClockInput.validate("0", minuteText, true, true).time)

    LaunchedEffect(hourError, minuteError) {
        if (hourError) hourFocus.requestFocus() else if (minuteError) minuteFocus.requestFocus()
    }

    fun shiftMinutes(delta: Int) {
        val current = ScheduleTime.parseToMinutesOrNull(validated.time) ?: return
        val newTime = ScheduleTime.format(current + delta)
        val newMinutes = ScheduleTime.parseToMinutesOrNull(newTime) ?: return
        val hour = newMinutes / 60
        isAm = hour < 12
        hourText = numberFormat.format(if (is24Hour) hour else (hour % 12).let { if (it == 0) 12 else it })
        minuteText = numberFormat.format(newMinutes % 60)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.base)
            ) {
                if (initialMinutes == null) Text(stringResource(R.string.schedule_time_invalid_saved), color = FocusColors.BlockedText)
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { hourText = it; hourError = false },
                    label = { Text(stringResource(if (is24Hour) R.string.schedule_time_hour_24 else R.string.schedule_time_hour_12)) },
                    supportingText = { if (hourError) Text(hourErrorMessage) },
                    isError = hourError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.fillMaxWidth().focusRequester(hourFocus).semantics { if (hourError) error(hourErrorMessage) }
                )
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { minuteText = it; minuteError = false },
                    label = { Text(stringResource(R.string.schedule_time_minute)) },
                    supportingText = { if (minuteError) Text(minuteErrorMessage) },
                    isError = minuteError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.fillMaxWidth().focusRequester(minuteFocus).semantics { if (minuteError) error(minuteErrorMessage) }
                )
                if (!is24Hour) {
                    val periods = remember(locale) { DateFormatSymbols(locale).amPmStrings }
                    FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                        periods.forEachIndexed { index, period ->
                            FilterChip(selected = isAm == (index == 0), onClick = { isAm = index == 0 }, label = { Text(period) })
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm), verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                    listOf(0, 15, 30, 45).forEach { minute ->
                        val name = stringResource(R.string.schedule_time_minute_preset, minute)
                        FilterChip(
                            selected = selectedMinute == minute,
                            onClick = { minuteText = numberFormat.format(minute); minuteError = false },
                            label = { Text(String.format(locale, ":%02d", minute)) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = name }
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                    OutlinedButton(onClick = { shiftMinutes(-5) }, enabled = validated.time != null) { Text(stringResource(R.string.schedule_time_subtract_five)) }
                    OutlinedButton(onClick = { shiftMinutes(5) }, enabled = validated.time != null) { Text(stringResource(R.string.schedule_time_add_five)) }
                }
                validated.time?.let { Text(scheduleTimeDisplay(it), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val result = ScheduleClockInput.validate(hourText, minuteText, is24Hour, isAm)
                hourError = !result.hourValid
                minuteError = !result.minuteValid
                result.time?.let(onTimeSelected)
            }) { Text(stringResource(R.string.schedule_time_set)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
internal fun ScheduleBreakDialog(currentBreakMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    var selectedMinutes by rememberSaveable(currentBreakMinutes) { mutableStateOf(currentBreakMinutes) }
    var invalid by remember { mutableStateOf(false) }
    val options = remember(currentBreakMinutes) { (listOf(0, 5, 10, 15, 20, 30) + currentBreakMinutes).filter { it in 0..180 }.distinct().sorted() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_break_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                Text(stringResource(R.string.schedule_break_hint), style = MaterialTheme.typography.bodyMedium)
                if (invalid) Text(stringResource(R.string.schedule_break_choice_error), color = FocusColors.BlockedText)
                options.forEach { minutes ->
                    FocusChoiceRow(
                        title = if (minutes == 0) stringResource(R.string.schedule_no_breaks) else pluralStringResource(R.plurals.schedule_break_minutes, minutes, minutes),
                        selected = selectedMinutes == minutes,
                        onClick = { selectedMinutes = minutes; invalid = false }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (selectedMinutes in options) onSelect(selectedMinutes) else invalid = true }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleEmojiDialog(currentEmoji: String, emojis: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_choose_icon)) },
        text = {
            FlowRow(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)
            ) {
                emojis.forEach { emoji ->
                    val label = stringResource(R.string.schedule_icon_choice, emoji)
                    FilledTonalIconButton(
                        onClick = { onSelect(emoji) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (emoji == currentEmoji) FocusColors.PrimaryContainer else FocusColors.SurfaceVariant),
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = label; selected = emoji == currentEmoji }
                    ) { Text(emoji, modifier = Modifier.padding(FocusSpacing.sm), style = MaterialTheme.typography.headlineSmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}
