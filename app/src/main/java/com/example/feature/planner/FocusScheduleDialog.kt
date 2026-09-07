package com.example.feature.planner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.design.FocusColors
import com.example.core.design.LocalFocusColors
import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.FocusScheduleEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

private val DEFAULT_EMOJIS = listOf("🌅", "☀️", "🌇", "🌙", "⏳", "📚", "🎯", "💡", "💻", "🧠", "📝", "☕", "🔬", "📖")

internal fun getEmojiForTime(timeStr: String): String {
    val hour = try {
        timeStr.split(":").firstOrNull()?.trim()?.toIntOrNull() ?: 12
    } catch (e: Exception) {
        12
    }
    return when (hour) {
        in 4..11 -> "🌅" // Morning
        in 12..16 -> "☀️" // Afternoon
        in 17..20 -> "🌇" // Evening
        else -> "🌙" // Night (21:00 - 03:59)
    }
}

@Composable
fun FocusScheduleDialog(
    initialSchedule: FocusScheduleEntity?,
    initialBlockNotifications: Boolean = false,
    availableBlockedApps: List<BlockedAppEntity> = emptyList(),
    onOpenBlockedApps: () -> Unit = {},
    onDismiss: () -> Unit,
    onBlockNotificationsChanged: (Boolean) -> Unit = {},
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
    var startTime by remember { mutableStateOf(initialSchedule?.startTime ?: "21:00") }
    var endTime by remember { mutableStateOf(initialSchedule?.endTime ?: "23:00") }
    var selectedEmoji by remember { mutableStateOf(getEmojiForTime(initialSchedule?.startTime ?: "21:00")) }
    var userManuallySelectedEmoji by remember { mutableStateOf(false) }
    var tag by remember { mutableStateOf(initialSchedule?.subjectName.takeIf { !it.isNullOrBlank() } ?: "Untagged") }
    var isAutoStart by remember { mutableStateOf(initialSchedule?.isAutoStartSession ?: true) }
    var blockNotifications by remember { mutableStateOf(initialSchedule?.blockNotifications ?: initialBlockNotifications) }
    var selectedMode by remember { mutableStateOf(initialSchedule?.mode ?: "TIMER") }

    val today = remember { Date() }
    val initialDate = remember(initialSchedule?.scheduledDateMillis) {
        if ((initialSchedule?.scheduledDateMillis ?: 0L) > 0L) Date(initialSchedule!!.scheduledDateMillis) else today
    }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var repeatEnabled by remember { mutableStateOf(initialSchedule?.repeatEnabled ?: true) }
    var breakMinutes by remember { mutableStateOf(initialSchedule?.breakMinutes ?: 5) }
    var description by remember { mutableStateOf(initialSchedule?.description ?: "") }
    var selectedBlockedPackages by remember {
        mutableStateOf(
            initialSchedule?.blockedAppPackages?.split(',')?.filter { it.isNotBlank() }?.toSet()
                ?: availableBlockedApps.map { it.packageName }.take(8).toSet()
        )
    }

    var titleError by remember { mutableStateOf<String?>(null) }
    var activePicker by remember { mutableStateOf<ActivePicker?>(null) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showBreakPicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showBlockedAppsPicker by remember { mutableStateOf(false) }

    val initialSelectedDays = remember {
        val daysStr = initialSchedule?.daysOfWeek ?: "MON,TUE,WED,THU,FRI"
        DAYS.filter { daysStr.contains(it) }.toMutableList()
    }
    val selectedDays = remember { mutableStateListOf<String>().apply { addAll(initialSelectedDays) } }

    val isDark = LocalFocusColors.current.isDark

    // Theme adaptive color tokens
    val bgColor = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF16181D) else Color(0xFFF1F5F9)
    val cardBorder = if (isDark) Color(0xFF26282E) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)
    val dividerColor = if (isDark) Color(0xFF23262E) else Color(0xFFE2E8F0)
    val saveBtnBg = if (isDark) Color.White else Color(0xFF0F172A)
    val saveBtnText = if (isDark) Color.Black else Color.White
    val switchTrackChecked = FocusColors.Primary
    val switchTrackUnchecked = if (isDark) Color(0xFF2C2F36) else Color(0xFFCBD5E1)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .testTag("focus_schedule_dialog"),
            color = bgColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header: "New Schedule" / "Edit Schedule" + ✕ Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialSchedule != null) "Edit Schedule" else "New Schedule",
                        color = textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Row 1: Emoji Box + Name Input Box
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Emoji Container
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                .clickable { showEmojiPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedEmoji,
                                fontSize = 28.sp
                            )
                        }

                        // Name Input Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .border(
                                    1.dp,
                                    if (titleError != null) Color(0xFFEF4444) else cardBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (title.isEmpty()) {
                                Text(
                                    text = "Enter name",
                                    color = textSecondary,
                                    fontSize = 17.sp
                                )
                            }
                            BasicTextField(
                                value = title,
                                onValueChange = {
                                    title = it
                                    titleError = null
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(if (isDark) Color.White else FocusColors.Primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("schedule_title_input")
                            )
                        }
                    }

                    if (titleError != null) {
                        Text(
                            text = titleError!!,
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 70.dp)
                        )
                    }

                    // Row 2: Tag Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTagPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tag",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(textSecondary)
                                )
                                Text(
                                    text = tag,
                                    color = textSecondary,
                                    fontSize = 15.sp
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Section 1: Schedule
                    Text(
                        text = "Schedule",
                        color = textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // Combined Card: From / To
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column {
                            // "From" Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activePicker = ActivePicker.START_TIME }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "From",
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formatTimeDisplay(startTime),
                                        color = textSecondary,
                                        fontSize = 15.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            HorizontalDivider(thickness = 1.dp, color = dividerColor)

                            // "To" Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activePicker = ActivePicker.END_TIME }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "To",
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formatTimeDisplay(endTime),
                                        color = textSecondary,
                                        fontSize = 15.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Card: Date
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activePicker = ActivePicker.DATE },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Date",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = SimpleDateFormat("MMM d", Locale.ENGLISH).format(selectedDate),
                                    color = textSecondary,
                                    fontSize = 15.sp
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Card: Repeat + Day Selector
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Repeat",
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Switch(
                                    checked = repeatEnabled,
                                    onCheckedChange = {
                                        repeatEnabled = it
                                        if (it && selectedDays.isEmpty()) {
                                            selectedDays.addAll(listOf("MON", "TUE", "WED", "THU", "FRI"))
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = switchTrackChecked,
                                        uncheckedThumbColor = textSecondary,
                                        uncheckedTrackColor = switchTrackUnchecked
                                    )
                                )
                            }

                            if (repeatEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    DAYS.forEachIndexed { index, day ->
                                        val isSelected = selectedDays.contains(day)
                                        val dayBg = if (isSelected) {
                                            if (isDark) Color(0xFF1E4620) else Color(0xFFDCFCE7)
                                        } else {
                                            if (isDark) Color(0xFF22252C) else Color(0xFFE2E8F0)
                                        }
                                        val dayBorder = if (isSelected) {
                                            if (isDark) Color(0xFF2A612D) else Color(0xFF86EFAC)
                                        } else {
                                            Color.Transparent
                                        }
                                        val dayTextColor = if (isSelected) {
                                            if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D)
                                        } else {
                                            textSecondary
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(dayBg)
                                                .border(1.dp, dayBorder, CircleShape)
                                                .clickable {
                                                    if (isSelected) selectedDays.remove(day) else selectedDays.add(day)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = DAY_LABELS[index],
                                                color = dayTextColor,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card: Break
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBreakPicker = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Break",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$breakMinutes mins",
                                    color = textSecondary,
                                    fontSize = 15.sp
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Section 2: Block settings
                    Text(
                        text = "Block settings",
                        color = textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // Card: Blocked Apps
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBlockedAppsPicker = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Blocked Apps",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Overlapping mini app badges
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy((-6).dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val badgeColors = listOf(
                                        Color(0xFFEF4444),
                                        Color(0xFF3B82F6),
                                        Color(0xFFF59E0B),
                                        Color(0xFF8B5CF6)
                                    )
                                    val count = selectedBlockedPackages.size.coerceAtMost(4)
                                    for (i in 0 until count) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(badgeColors[i % badgeColors.size])
                                                .border(1.5.dp, cardBg, CircleShape)
                                        )
                                    }
                                }

                                Text(
                                    text = if (selectedBlockedPackages.isEmpty()) "None" else "${selectedBlockedPackages.size} apps",
                                    color = textSecondary,
                                    fontSize = 15.sp
                                )

                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Card: Block Notifications
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Block Notifications",
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Block notification from blocked apps",
                                    color = textSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            Switch(
                                checked = blockNotifications,
                                onCheckedChange = {
                                    blockNotifications = it
                                    onBlockNotificationsChanged(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = switchTrackChecked,
                                    uncheckedThumbColor = textSecondary,
                                    uncheckedTrackColor = switchTrackUnchecked
                                )
                            )
                        }
                    }

                    // Section 3: Description
                    Text(
                        text = "Description",
                        color = textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // Description Input Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (description.isEmpty()) {
                            Text(
                                text = "What do you plan on focusing? Add notes...",
                                color = textSecondary,
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = description,
                            onValueChange = { description = it },
                            textStyle = TextStyle(
                                color = textPrimary,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(if (isDark) Color.White else FocusColors.Primary),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Sticky Bottom Save Button
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
                            title.trim(),
                            days,
                            startTime,
                            endTime,
                            isAutoStart,
                            selectedMode,
                            tag.trim().ifBlank { "Untagged" },
                            repeatEnabled,
                            selectedDate.time,
                            breakMinutes,
                            description.trim(),
                            selectedBlockedPackages,
                            blockNotifications
                        )
                    },
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = saveBtnBg,
                        contentColor = saveBtnText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(56.dp)
                        .testTag("save_schedule_button")
                ) {
                    Text(
                        text = "Save",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = saveBtnText
                    )
                }
            }
        }
    }

    // Pickers / Sub-dialogs
    if (activePicker == ActivePicker.START_TIME || activePicker == ActivePicker.END_TIME) {
        val isStart = activePicker == ActivePicker.START_TIME
        TimePickerDialog(
            initialTime = if (isStart) startTime else endTime,
            title = if (isStart) "Select Start Time" else "Select End Time",
            cardBg = cardBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onDismiss = { activePicker = null },
            onTimeSelected = { newTime ->
                if (isStart) {
                    startTime = newTime
                    if (!userManuallySelectedEmoji) {
                        selectedEmoji = getEmojiForTime(newTime)
                    }
                } else {
                    endTime = newTime
                }
                activePicker = null
            }
        )
    }

    if (activePicker == ActivePicker.DATE) {
        DatePickerModalDialog(
            initialDate = selectedDate,
            cardBg = cardBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onDismiss = { activePicker = null },
            onDateSelected = { newDate ->
                selectedDate = newDate
                activePicker = null
            }
        )
    }

    if (showTagPicker) {
        TagPickerDialog(
            currentTag = tag,
            cardBg = cardBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onDismiss = { showTagPicker = false },
            onSelect = { selected ->
                tag = selected
                showTagPicker = false
            }
        )
    }

    if (showBreakPicker) {
        BreakPickerDialog(
            currentBreakMinutes = breakMinutes,
            cardBg = cardBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onDismiss = { showBreakPicker = false },
            onSelect = { selected ->
                breakMinutes = selected
                showBreakPicker = false
            }
        )
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            currentEmoji = selectedEmoji,
            cardBg = cardBg,
            textPrimary = textPrimary,
            onDismiss = { showEmojiPicker = false },
            onSelect = {
                selectedEmoji = it
                userManuallySelectedEmoji = true
                showEmojiPicker = false
            }
        )
    }

    if (showBlockedAppsPicker) {
        BlockedAppsDialog(
            availableApps = availableBlockedApps,
            selectedPackages = selectedBlockedPackages,
            cardBg = cardBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onSelectionChanged = { selectedBlockedPackages = it },
            onManage = onOpenBlockedApps,
            onDismiss = { showBlockedAppsPicker = false }
        )
    }
}

private enum class ActivePicker { START_TIME, END_TIME, DATE }

private fun formatTimeDisplay(value: String): String {
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).parse(value)?.let {
            SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(it)
        } ?: value
    }.getOrDefault(value)
}

@Composable
private fun TimePickerDialog(
    initialTime: String,
    title: String,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    val parsedHour = initialTime.substringBefore(':').toIntOrNull() ?: 9
    val parsedMinute = initialTime.substringAfter(':').toIntOrNull() ?: 0

    var isAm by remember { mutableStateOf(parsedHour < 12) }
    var hour12 by remember {
        val h = parsedHour % 12
        mutableIntStateOf(if (h == 0) 12 else h)
    }
    var minute by remember { mutableIntStateOf(parsedMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour picker chip
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hour", color = textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { hour12 = if (hour12 <= 1) 12 else hour12 - 1 }) {
                                Text("◀", color = textSecondary)
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", hour12),
                                color = textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { hour12 = if (hour12 >= 12) 1 else hour12 + 1 }) {
                                Text("▶", color = textSecondary)
                            }
                        }
                    }

                    Text(":", color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                    // Minute picker chip
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minute", color = textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { minute = if (minute <= 0) 55 else minute - 5 }) {
                                Text("◀", color = textSecondary)
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", minute),
                                color = textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { minute = if (minute >= 55) 0 else minute + 5 }) {
                                Text("▶", color = textSecondary)
                            }
                        }
                    }
                }

                // AM / PM Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (LocalFocusColors.current.isDark) Color(0xFF22242B) else Color(0xFFE2E8F0))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAm) FocusColors.Primary else Color.Transparent)
                            .clickable { isAm = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("AM", color = if (isAm) Color.White else textSecondary, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isAm) FocusColors.Primary else Color.Transparent)
                            .clickable { isAm = false }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("PM", color = if (!isAm) Color.White else textSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val final24Hour = if (isAm) {
                        if (hour12 == 12) 0 else hour12
                    } else {
                        if (hour12 == 12) 12 else hour12 + 12
                    }
                    val formatted = String.format(Locale.US, "%02d:%02d", final24Hour, minute)
                    onTimeSelected(formatted)
                }
            ) {
                Text("Set Time", color = FocusColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        },
        containerColor = cardBg
    )
}

@Composable
private fun DatePickerModalDialog(
    initialDate: Date,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDismiss: () -> Unit,
    onDateSelected: (Date) -> Unit
) {
    val cal = remember { Calendar.getInstance().apply { time = initialDate } }
    var selectedDayOffset by remember { mutableIntStateOf(0) }

    val daysList = remember {
        val list = mutableListOf<Pair<String, Date>>()
        val tempCal = Calendar.getInstance()
        val fmt = SimpleDateFormat("EEE, MMM d", Locale.ENGLISH)
        for (i in 0 until 14) {
            list.add(Pair(fmt.format(tempCal.time), tempCal.time))
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Date", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                daysList.forEachIndexed { index, (label, date) ->
                    val isSelected = index == selectedDayOffset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) FocusColors.Primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { selectedDayOffset = index }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) FocusColors.Primary else textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = FocusColors.Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDateSelected(daysList[selectedDayOffset].second)
                }
            ) {
                Text("Select", color = FocusColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        },
        containerColor = cardBg
    )
}

@Composable
private fun TagPickerDialog(
    currentTag: String,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val tags = listOf("Untagged", "Study", "Exam", "Revision", "Homework", "Deep Work", "Reading", "Practice")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Tag", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { tagOption ->
                    val isSelected = tagOption.equals(currentTag, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) FocusColors.Primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSelect(tagOption) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = tagOption,
                            color = if (isSelected) FocusColors.Primary else textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = FocusColors.Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        },
        containerColor = cardBg
    )
}

@Composable
private fun BreakPickerDialog(
    currentBreakMinutes: Int,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val breakOptions = listOf(0, 5, 10, 15, 20, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Break Duration", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                breakOptions.forEach { mins ->
                    val isSelected = mins == currentBreakMinutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) FocusColors.Primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSelect(mins) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$mins mins",
                            color = if (isSelected) FocusColors.Primary else textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = FocusColors.Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        },
        containerColor = cardBg
    )
}

@Composable
private fun EmojiPickerDialog(
    currentEmoji: String,
    cardBg: Color,
    textPrimary: Color,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Icon", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val chunked = DEFAULT_EMOJIS.chunked(4)
                chunked.forEach { rowEmojis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowEmojis.forEach { emoji ->
                            val isSelected = emoji == currentEmoji
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) FocusColors.Primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) FocusColors.Primary else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable { onSelect(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = cardBg
    )
}

@Composable
private fun BlockedAppsDialog(
    availableApps: List<BlockedAppEntity>,
    selectedPackages: Set<String>,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSelectionChanged: (Set<String>) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked Apps", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (availableApps.isEmpty()) {
                    Text("No apps in blocklist yet.", color = textSecondary, modifier = Modifier.padding(vertical = 12.dp))
                    TextButton(onClick = {
                        onDismiss()
                        onManage()
                    }) {
                        Text("Manage Apps to Block", color = FocusColors.Primary)
                    }
                } else {
                    availableApps.forEach { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) FocusColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    val next = if (isSelected) selectedPackages - app.packageName else selectedPackages + app.packageName
                                    onSelectionChanged(next)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(app.packageName, color = textSecondary, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = FocusColors.Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = FocusColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        },
        containerColor = cardBg
    )
}
