package com.example.feature.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusTimerText

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FloatingTimerOverlay(
    expanded: Boolean,
    dockedRight: Boolean,
    formattedTime: String,
    paused: Boolean,
    strict: Boolean,
    stopwatch: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onTogglePause: () -> Unit,
    onOpenApp: () -> Unit,
    onMove: (FloatingTimerMove) -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {}
) {
    val status = stringResource(if (paused) R.string.floating_timer_paused else R.string.floating_timer_running)
    val movements = listOf(
        Triple(FloatingTimerMove.UP, Icons.Rounded.ArrowUpward, stringResource(R.string.floating_timer_move_up)),
        Triple(FloatingTimerMove.DOWN, Icons.Rounded.ArrowDownward, stringResource(R.string.floating_timer_move_down)),
        Triple(FloatingTimerMove.LEFT_EDGE, Icons.Rounded.KeyboardArrowLeft, stringResource(R.string.floating_timer_dock_left)),
        Triple(FloatingTimerMove.RIGHT_EDGE, Icons.Rounded.KeyboardArrowRight, stringResource(R.string.floating_timer_dock_right))
    )
    // Docking is physical screen geometry and deliberately does not mirror with reading direction.
    val shape = if (dockedRight) AbsoluteRoundedCornerShape(topLeft = 16.dp, bottomLeft = 16.dp)
        else AbsoluteRoundedCornerShape(topRight = 16.dp, bottomRight = 16.dp)
    val background = FocusColors.HeroGradientStart
    val foreground = FocusColors.HeroOnColor

    Box(
        modifier = modifier.fillMaxSize().clip(shape).background(background)
            .onPreviewKeyEvent {
                if (expanded && it.type == KeyEventType.KeyUp && (it.key == Key.Escape || it.key == Key.Back)) {
                    onCollapse()
                    true
                } else false
            }
    ) {
        if (!expanded) {
            IconButton(
                onClick = onExpand,
                colors = IconButtonDefaults.iconButtonColors(contentColor = foreground),
                modifier = Modifier.fillMaxSize().then(dragModifier).semantics {
                    stateDescription = status
                    customActions = movements.map { (move, _, label) -> CustomAccessibilityAction(label) { onMove(move); true } }
                }.testTag("floating_timer_edge_tab")
            ) {
                Icon(if (dockedRight) Icons.Rounded.KeyboardArrowLeft else Icons.Rounded.KeyboardArrowRight, stringResource(R.string.floating_timer_expand))
            }
        } else {
            val closeFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FocusSpacing.md).testTag("floating_timer_overlay_card"),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                    Text(status, color = foreground, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).then(dragModifier))
                    IconButton(onClick = onCollapse, colors = IconButtonDefaults.iconButtonColors(contentColor = foreground),
                        modifier = Modifier.focusRequester(closeFocus).testTag("floating_timer_collapse_button")) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.floating_timer_collapse))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                    IconButton(
                        onClick = onTogglePause,
                        enabled = !strict,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = foreground, contentColor = background),
                        modifier = Modifier.testTag("floating_timer_pause_resume_button")
                    ) {
                        Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, stringResource(if (paused) R.string.action_resume else R.string.action_pause))
                    }
                    val openLabel = stringResource(R.string.action_open_timer)
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpenApp)
                            .heightIn(min = 48.dp).padding(FocusSpacing.sm).testTag("floating_timer_open_app_area")
                    ) {
                        FocusTimerText(formattedTime, style = MaterialTheme.typography.headlineSmall.copy(color = foreground), modifier = Modifier.fillMaxWidth())
                        Text(stringResource(if (stopwatch) R.string.floating_timer_elapsed else R.string.floating_timer_remaining), color = foreground, style = MaterialTheme.typography.bodySmall)
                        Text(openLabel, color = foreground, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (strict) Text(stringResource(R.string.floating_timer_strict_hint), color = foreground, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.floating_timer_position_hint), color = foreground, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                    movements.forEach { (move, icon, label) ->
                        IconButton(onClick = { onMove(move) }, colors = IconButtonDefaults.iconButtonColors(containerColor = foreground, contentColor = background)) {
                            Icon(icon, label)
                        }
                    }
                }
                FocusStatusMessage(errorMessage, isError = true, onDismiss = onDismissError)
            }
        }
    }
}
