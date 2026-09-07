package com.example.feature.planner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.design.FocusColors
import com.example.core.design.LocalFocusColors
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.BlockedAppEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun FocusScheduleDialog(
    initialSchedule: FocusScheduleEntity? = null,
    initialBlockNotifications: Boolean = false,
    onBlockNotificationsChanged: (Boolean) -> Unit = {},
    onOpenBlockedApps: () -> Unit = {},
    availableBlockedApps: List<BlockedAppEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        daysOfWeek: String,
        startTime: String,
        endTime: String,
        isAutoStart: Boolean,
        mode: String,
        subjectName: String,
        repeatEnabled: Boolean,
        scheduledDateMillis: Long,
        breakMinutes: Int,
        description: String,
        blockedAppPackages: Set<String>,
        blockNotifications: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialSchedule?.title ?: "") }
    var tag by remember { mutableStateOf(initialSchedule?.subjectName ?: "Untagged") }
    var startTime by remember { mutableStateOf(initialSchedule?.startTime ?: "21:00") }
    var endTime by remember { mutableStateOf(initialSchedule?.endTime ?: "23:00") }
    var isAutoStart by remember { mutableStateOf(initialSchedule?.isAutoStartSession ?: true) }
    var blockNotifications by remember { mutableStateOf(initialBlockNotifications) }
    var selectedMode by remember { mutableStateOf(initialSchedule?.mode ?: "TIMER") }

    val today = remember { Date() }
    val initialDate = remember(initialSchedule?.scheduledDateMillis) {
        if ((initialSchedule?.scheduledDateMillis ?: 0L) > 0L) Date(initialSchedule!!.scheduledDateMillis) else today
    }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var repeatEnabled by remember { mutableStateOf(initialSchedule?.repeatEnabled ?: true) }
    var breakMinutes by remember { mutableStateOf(initialSchedule?.breakMinutes ?: 5) }
    var description by remember { mutableStateOf(initialSchedule?.description ?: "") }
    var selectedBlockedPackages by remember { mutableStateOf(initialSchedule?.blockedAppPackages?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) }
    var showBlockedAppsPicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showBreakPicker by remember { mutableStateOf(false) }

    val initialSelectedDays = remember {
        val daysStr = initialSchedule?.daysOfWeek ?: "MON,TUE,WED,THU,FRI"
        DAYS.filter { daysStr.contains(it) }.toMutableList()
    }
    val selectedDays = remember { mutableStateListOf<String>().apply { addAll(initialSelectedDays) } }

    val surface = FocusColors.Surface
    val card = FocusColors.SurfaceVariant
    val subtle = FocusColors.SurfaceSubtle
    val border = FocusColors.CardBorderSubtle
    val primary = FocusColors.Primary
    val text = FocusColors.TextPrimary
    val secondary = FocusColors.TextSecondary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("focus_schedule_dialog"),
            color = FocusColors.Background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 34.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialSchedule != null) "Edit Schedule" else "New Schedule",
                        color = text,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = text,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onDismiss)
                            .padding(3.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(2.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(card),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⌛", fontSize = 30.sp)
                        }
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it; titleError = null },
                            placeholder = { Text("Enter name", color = secondary, fontSize = 20.sp) },
                            singleLine = true,
                            isError = titleError != null,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = text, fontSize = 19.sp),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .testTag("schedule_title_input")
                        )
                    }
                    titleError?.let { Text(it, color = FocusColors.CoralWarning, fontSize = 12.sp) }

                    ScheduleRow(
                        label = "Tag",
                        value = tag,
                        leadingDot = true,
                        onClick = { showTagPicker = true }
                    )

                    SectionTitle("Schedule")

                    ScheduleRow("From", formatTime(startTime), onClick = { picker = Picker.START })
                    ScheduleRow("To", formatTime(endTime), onClick = { picker = Picker.END })
                    ScheduleRow(
                        "Date",
                        SimpleDateFormat("MMM d", Locale.getDefault()).format(selectedDate),
                        leadingIcon = Icons.Rounded.CalendarToday,
                        onClick = { picker = Picker.DATE }
                    )
                    SwitchRow(
                        label = "Repeat",
                        checked = repeatEnabled,
                        onCheckedChange = {
                            repeatEnabled = it
                            if (it && selectedDays.isEmpty()) selectedDays.addAll(listOf("MON", "TUE", "WED", "THU", "FRI"))
                        }
                    )

                    if (repeatEnabled) {
                        DaySelector(selectedDays = selectedDays)
                    }

                    ScheduleRow("Break", "$breakMinutes mins", onClick = { showBreakPicker = true })

                    SectionTitle("Block settings")

                    ScheduleRow(
                        label = "Blocked Apps",
                        value = if (selectedBlockedPackages.isEmpty()) "None" else "${selectedBlockedPackages.size} apps",
                        trailing = true,
                        onClick = { showBlockedAppsPicker = true }
                    )

                    SwitchDescriptionRow(
                        label = "Block Notifications",
                        description = "Block notification from blocked apps",
                        checked = blockNotifications,
                        onCheckedChange = {
                            blockNotifications = it
                            onBlockNotificationsChanged(it)
                        }
                    )

                    SectionTitle("Description")
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("What do you plan on focusing? Add notes...", color = secondary, fontSize = 18.sp) },
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = text, fontSize = 18.sp),
                        colors = fieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                    )

                    // Keep existing schedule options available without adding a second visible block.
                    Column(modifier = Modifier.height(1.dp)) {
                        @Suppress("UNUSED_VARIABLE")
                        val ignoredMode = selectedMode
                        @Suppress("UNUSED_VARIABLE")
                        val ignoredAutoStart = isAutoStart
                        @Suppress("UNUSED_VARIABLE")
                        val ignoredBreak = breakMinutes
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick = {
                        if (title.isBlank()) {
                            titleError = "Enter a schedule name"
                            return@Button
                        }
                        val days = if (repeatEnabled) {
                            selectedDays.joinToString(",")
                        } else {
                            SimpleDateFormat("EEE", Locale.ENGLISH).format(selectedDate).uppercase(Locale.ENGLISH)
                        }
                        if (repeatEnabled && selectedDays.isEmpty()) {
                            titleError = "Select at least one repeat day"
                            return@Button
                        }
                        onSave(
                            title.trim(), days, startTime, endTime, isAutoStart, selectedMode,
                            tag.trim().ifBlank { "Untagged" }, repeatEnabled, selectedDate.time, breakMinutes,
                            description.trim(), selectedBlockedPackages, blockNotifications
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = if (LocalFocusColors.current.isDark) Color(0xFFF8F8F5) else primary, contentColor = if (LocalFocusColors.current.isDark) Color.Black else Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 16.dp)
                        .height(68.dp)
                        .testTag("save_schedule_button")
                ) {
                    Text("Save", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (picker != null) {
        PickerDialog(
            picker = picker!!,
            currentTime = if (picker == Picker.START) startTime else endTime,
            currentDate = selectedDate,
            onDismiss = { picker = null },
            onTimeSelected = { value ->
                if (picker == Picker.START) startTime = value else endTime = value
                picker = null
            },
            onDateSelected = { value ->
                selectedDate = value
                picker = null
            }
        )
    }

    if (showTagPicker) {
        ChoiceDialog(
            title = "Tag",
            options = listOf("Untagged", "Study", "Exam", "Revision", "Practice"),
            selected = tag,
            onSelect = { tag = it; showTagPicker = false },
            onDismiss = { showTagPicker = false }
        )
    }

    if (showBreakPicker) {
        ChoiceDialog(
            title = "Break",
            options = listOf("0 mins", "5 mins", "10 mins", "15 mins", "20 mins"),
            selected = "$breakMinutes mins",
            onSelect = { breakMinutes = it.substringBefore(' ').toInt(); showBreakPicker = false },
            onDismiss = { showBreakPicker = false }
        )
    }

    if (showBlockedAppsPicker) {
        BlockedAppsPickerDialog(
            apps = availableBlockedApps,
            selectedPackages = selectedBlockedPackages,
            onSelectionChanged = { selectedBlockedPackages = it },
            onManage = onOpenBlockedApps,
            onDismiss = { showBlockedAppsPicker = false }
        )
    }
}

private enum class Picker { START, END, DATE }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = FocusColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 1.dp)
    )
}

@Composable
private fun ScheduleRow(
    label: String,
    value: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    leadingDot: Boolean = false,
    trailing: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        color = FocusColors.SurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (leadingDot) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(FocusColors.TextSecondary))
                }
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = FocusColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
                Text(label, color = FocusColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(value, color = FocusColors.TextSecondary, fontSize = 18.sp)
                if (trailing || !leadingDot) {
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = FocusColors.TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(color = FocusColors.SurfaceVariant, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = FocusColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FocusColors.Primary,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = FocusColors.SurfaceSubtle,
                    uncheckedBorderColor = FocusColors.TextSecondary
                )
            )
        }
    }
}

@Composable
private fun SwitchDescriptionRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(color = FocusColors.SurfaceVariant, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = FocusColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text(description, color = FocusColors.TextSecondary, fontSize = 14.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FocusColors.Primary,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = FocusColors.SurfaceSubtle,
                    uncheckedBorderColor = FocusColors.TextSecondary
                )
            )
        }
    }
}

@Composable
private fun DaySelector(selectedDays: MutableList<String>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DAYS.forEachIndexed { index, day ->
            val selected = selectedDays.contains(day)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (selected) FocusColors.Primary else FocusColors.SurfaceVariant)
                    .border(1.dp, if (selected) FocusColors.Primary else FocusColors.CardBorder, CircleShape)
                    .clickable {
                        if (selected) selectedDays.remove(day) else selectedDays.add(day)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(DAY_LABELS[index], color = if (selected) Color.White else FocusColors.TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PickerDialog(
    picker: Picker,
    currentTime: String,
    currentDate: Date,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit,
    onDateSelected: (Date) -> Unit
) {
    var value by remember {
        mutableStateOf(
            if (picker == Picker.DATE) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate) else currentTime
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (picker == Picker.DATE) "Select date" else "Select time", color = FocusColors.TextPrimary) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (picker == Picker.DATE) KeyboardType.Number else KeyboardType.Text),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (picker == Picker.DATE) {
                    runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }.parse(value) }
                        .getOrNull()?.let(onDateSelected)
                } else if (value.matches(Regex("\\d{2}:\\d{2}"))) {
                    onTimeSelected(value)
                }
            }) { Text("Done", color = FocusColors.Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FocusColors.TextSecondary) } },
        containerColor = FocusColors.Surface
    )
}

@Composable
private fun BlockedAppsPickerDialog(
    apps: List<BlockedAppEntity>,
    selectedPackages: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked Apps", color = FocusColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (apps.isEmpty()) {
                    Text("No blocked apps are configured yet.", color = FocusColors.TextSecondary)
                    TextButton(onClick = onManage) { Text("Manage blocked apps", color = FocusColors.Primary) }
                } else {
                    apps.forEach { app ->
                        val selected = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                val next = if (selected) selectedPackages - app.packageName else selectedPackages + app.packageName
                                onSelectionChanged(next)
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, color = FocusColors.TextPrimary, fontSize = 16.sp)
                                Text(app.packageName, color = FocusColors.TextSecondary, fontSize = 11.sp)
                            }
                            Text(if (selected) "✓" else "○", color = if (selected) FocusColors.Primary else FocusColors.TextSecondary, fontSize = 22.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = FocusColors.Primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FocusColors.TextSecondary) } },
        containerColor = FocusColors.Surface
    )
}


@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = FocusColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(option) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(option, color = FocusColors.TextPrimary, fontSize = 17.sp)
                        if (option == selected) Text("✓", color = FocusColors.Primary, fontSize = 20.sp)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = FocusColors.Surface
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FocusColors.TextPrimary,
    unfocusedTextColor = FocusColors.TextPrimary,
    focusedContainerColor = FocusColors.SurfaceVariant,
    unfocusedContainerColor = FocusColors.SurfaceVariant,
    focusedBorderColor = FocusColors.Primary,
    unfocusedBorderColor = FocusColors.CardBorderSubtle,
    cursorColor = FocusColors.Primary,
    focusedPlaceholderColor = FocusColors.TextSecondary,
    unfocusedPlaceholderColor = FocusColors.TextSecondary
)

private fun formatTime(value: String): String {
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).parse(value)?.let {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
        } ?: value
    }.getOrDefault(value)
}
