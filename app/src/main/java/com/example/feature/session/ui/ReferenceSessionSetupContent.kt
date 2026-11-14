package com.example.feature.session.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusStatusMessage
import com.example.data.model.SessionMode
import com.example.feature.session.SessionSetupState

/**
 * Compact setup surface matching the supplied reference while retaining FocusShield's live session
 * draft as the single source of truth.
 */
@Composable
internal fun ReferenceSessionSetupContent(
    setupState: SessionSetupState,
    onModeSelected: (SessionMode) -> Unit,
    onOpenFocusTimePicker: () -> Unit,
    onOpenBreakPicker: () -> Unit,
    onOpenSelectApps: () -> Unit,
    onOpenUltraFocus: () -> Unit,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.84f)
            .background(FocusColors.Surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FocusSpacing.screenHorizontal)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(FocusColors.ControlBorder)
        )

        SessionModeTabs(
            selected = setupState.selectedMode,
            onSelected = onModeSelected
        )

        Surface(
            color = FocusColors.SurfaceVariant,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                SetupActionRow(
                    label = stringResource(R.string.session_sheet_focus_time),
                    value = if (setupState.selectedMode == SessionMode.STOPWATCH) {
                        stringResource(R.string.session_sheet_open_ended)
                    } else {
                        pluralStringResource(
                            R.plurals.session_sheet_minutes,
                            if (setupState.selectedMode == SessionMode.POMODORO) setupState.pomodoroFocusMinutes else setupState.durationMinutes,
                            if (setupState.selectedMode == SessionMode.POMODORO) setupState.pomodoroFocusMinutes else setupState.durationMinutes
                        )
                    },
                    onClick = onOpenFocusTimePicker
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(FocusColors.CardBorderSubtle)
                )
                SetupActionRow(
                    label = stringResource(R.string.session_sheet_breaks),
                    value = pluralStringResource(
                        R.plurals.session_sheet_break_count,
                        setupState.numberOfBreaks,
                        setupState.numberOfBreaks
                    ),
                    onClick = onOpenBreakPicker
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BlockedAppsSetupCard(
                blockedCount = setupState.blockedAppPackages.size,
                onClick = onOpenSelectApps,
                modifier = Modifier.weight(1f)
            )
            UltraFocusSetupCard(
                onClick = onOpenUltraFocus,
                modifier = Modifier.weight(1f)
            )
        }

        FocusStatusMessage(
            message = errorMessage,
            isError = true,
            onDismiss = onDismissError
        )

        ReferencePrimaryButton(
            text = stringResource(R.string.session_sheet_continue),
            onClick = onContinue,
            enabled = !setupState.isLoadingPreferences,
            testTag = "session_setup_continue_button"
        )
    }
}

@Composable
private fun SessionModeTabs(selected: SessionMode, onSelected: (SessionMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .clip(RoundedCornerShape(18.dp))
            .background(FocusColors.SurfaceVariant)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            SessionMode.TIMER to R.string.schedule_mode_timer,
            SessionMode.STOPWATCH to R.string.schedule_mode_stopwatch,
            SessionMode.POMODORO to R.string.schedule_mode_pomodoro
        ).forEach { (mode, labelRes) ->
            val checked = selected == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (checked) FocusColors.EmeraldLight else Color.Transparent)
                    .selectable(
                        selected = checked,
                        role = Role.Tab,
                        onClick = { onSelected(mode) }
                    )
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(labelRes),
                    color = if (checked) FocusColors.EmeraldText else FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SetupActionRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 19.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = FocusColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = FocusColors.TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = FocusColors.TextMuted)
    }
}

@Composable
private fun BlockedAppsSetupCard(
    blockedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = FocusColors.EmeraldLight,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .height(190.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("blocked_apps_setup_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(FocusColors.EmeraldSuccess),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Security, null, tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = FocusColors.EmeraldText)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(R.plurals.session_sheet_blocked_app_count, blockedCount, blockedCount),
                    color = FocusColors.EmeraldText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(FocusColors.Surface.copy(alpha = 0.75f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
                Text(
                    stringResource(R.string.session_sheet_blocked_apps),
                    color = FocusColors.EmeraldText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
private fun UltraFocusSetupCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = FocusColors.ProGold,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .height(190.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("ultra_focus_setup_card")
    ) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            Text(
                text = stringResource(R.string.pro_label),
                color = FocusColors.ProGold,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Icon(
                Icons.Rounded.Bolt,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.10f),
                modifier = Modifier
                    .size(118.dp)
                    .align(Alignment.CenterEnd)
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.session_ultra_focus),
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 23.sp
                )
                Text(
                    stringResource(R.string.session_ultra_focus_body),
                    color = Color.Black.copy(alpha = 0.66f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
internal fun UltraFocusSettingsContent(
    setupState: SessionSetupState,
    isSaving: Boolean,
    onToggleStrict: (Boolean) -> Unit,
    onToggleStudyMode: (Boolean) -> Unit,
    onToggleUninstall: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleHomeScreen: (Boolean) -> Unit,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onStartFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .background(FocusColors.Surface)
            .padding(horizontal = FocusSpacing.screenHorizontal)
            .padding(top = 14.dp, bottom = 24.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(FocusColors.ControlBorder)
        )
        Spacer(Modifier.height(30.dp))
        Text(
            text = stringResource(R.string.session_ultra_focus_settings),
            color = FocusColors.TextPrimary,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (setupState.selectedMode != SessionMode.STOPWATCH) {
                UltraFocusToggleCard(
                    icon = Icons.Rounded.Bolt,
                    title = stringResource(R.string.session_ultra_strict),
                    body = stringResource(R.string.session_ultra_strict_body),
                    checked = setupState.isStrictModeEnabled,
                    enabled = !isSaving,
                    onCheckedChange = onToggleStrict
                )
            }
            UltraFocusToggleCard(
                icon = Icons.Rounded.SmartDisplay,
                title = stringResource(R.string.session_ultra_youtube),
                badge = stringResource(R.string.session_ultra_favorite),
                checked = setupState.isYouTubeStudyModeEnabled,
                enabled = !isSaving,
                onCheckedChange = onToggleStudyMode
            )
            UltraFocusToggleCard(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.session_ultra_uninstall),
                body = stringResource(R.string.session_ultra_uninstall_body),
                checked = setupState.isBlockUninstallEnabled,
                enabled = !isSaving,
                onCheckedChange = onToggleUninstall
            )
            UltraFocusToggleCard(
                icon = Icons.Rounded.NotificationsOff,
                title = stringResource(R.string.session_ultra_notifications),
                body = stringResource(R.string.session_ultra_notifications_body),
                checked = setupState.blockNotifications,
                enabled = !isSaving,
                onCheckedChange = onToggleNotifications
            )
            UltraFocusToggleCard(
                icon = Icons.Rounded.Home,
                title = stringResource(R.string.session_ultra_home),
                checked = setupState.isBlockHomeScreenEnabled,
                enabled = !isSaving,
                onCheckedChange = onToggleHomeScreen
            )
        }
        Spacer(Modifier.height(18.dp))
        FocusStatusMessage(
            message = errorMessage,
            isError = true,
            onDismiss = onDismissError
        )
        ReferencePrimaryButton(
            text = stringResource(R.string.session_sheet_start_now),
            onClick = onStartFocus,
            enabled = !isSaving && !setupState.isLoadingPreferences,
            testTag = "ultra_focus_start_button"
        )
    }
}

@Composable
private fun ReferencePrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = FocusColors.OverlayTextPrimary,
            contentColor = FocusColors.OverlayBackground
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .testTag(testTag)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun UltraFocusToggleCard(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    body: String? = null,
    badge: String? = null
) {
    Surface(
        color = FocusColors.SurfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FocusColors.TextMuted,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                if (body != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(body, color = FocusColors.TextSecondary, fontSize = 14.sp)
                }
                if (badge != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF9D174D))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = FocusColors.SwitchColors,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}
