package com.example.feature.applimits.ui

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.ui.FocusLottieAnimation
import com.example.feature.applimits.engine.AppLimitOverlayMode
import com.example.feature.applimits.model.FocusQuote
import com.example.feature.applimits.model.FocusQuotesProvider
import kotlinx.coroutines.delay

@Composable
fun AppLimitOverlayScreen(
    packageName: String,
    appName: String,
    mode: AppLimitOverlayMode,
    selectedMinutes: Int,
    usedMinutes: Int,
    remainingDailyMinutes: Int,
    dailyLimitMinutes: Int,
    emergencyUsesCount: Int,
    emergencyUsesAllowed: Int = 1,
    isStrict: Boolean,
    streakDays: Int,
    onSelectDuration: (Int) -> Unit,
    onUseEmergency: () -> Unit,
    onEnableStrictMode: () -> Unit,
    onTurnOffAndResetStreak: () -> Unit,
    onGoToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var appIconDrawable by remember { mutableStateOf<Drawable?>(null) }
    var strictModeEnabledState by remember { mutableStateOf(isStrict) }
    var showMotivationalQuitDialog by remember { mutableStateOf(false) }
    var showStrictModeInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) {
        try {
            val pm = context.packageManager
            appIconDrawable = pm.getApplicationIcon(packageName)
        } catch (e: Exception) {
            appIconDrawable = null
        }
    }

    val totalDailyLimit = dailyLimitMinutes.coerceAtLeast(1)
    val actualUsedMinutes = usedMinutes.coerceAtLeast(0)
    val actualRemainingDailyMinutes = if (mode == AppLimitOverlayMode.DAILY_LIMIT_REACHED || remainingDailyMinutes <= 0) {
        0
    } else {
        (totalDailyLimit - actualUsedMinutes).coerceIn(0, totalDailyLimit)
    }

    val isExhausted = actualRemainingDailyMinutes <= 0 || mode == AppLimitOverlayMode.DAILY_LIMIT_REACHED

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xEE090A0D))
            .testTag("app_limit_overlay_screen"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Minimalist Hourglass + "Turn off" button (Hidden in Strict Mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minimalist Hourglass Symbol (exact style from image)
                Box(
                    modifier = Modifier
                        .size(36.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassEmpty,
                        contentDescription = "Focus Limit",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Top-Right: Turn off button (Disabled/Hidden when Strict Mode is active)
                if (strictModeEnabledState) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF1E2024))
                            .border(1.dp, Color(0xFF2C2F36), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Strict Mode Enforced",
                                tint = FocusColors.CoralWarning,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Strict Locked",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = FocusColors.CoralWarning,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF202227))
                            .border(1.dp, Color(0xFF2E3138), CircleShape)
                            .clickable { showMotivationalQuitDialog = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("turn_off_limit_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = "Turn off",
                                tint = FocusColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Turn off",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = FocusColors.TextSecondary,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Strict Mode Banner (Amber "Introducing Strict Mode" / "Strict Mode Active")
            if (!strictModeEnabledState) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF382A12))
                        .border(1.dp, Color(0xFF5C431A), RoundedCornerShape(20.dp))
                        .clickable { showStrictModeInfoDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("strict_mode_promo_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = Color(0xFFFACC15),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Introducing Strict Mode",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Try now",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFACC15),
                                    fontSize = 13.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFFFACC15),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E281F))
                        .border(1.dp, Color(0xFF2E4D32), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Strict Mode Active",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        Text(
                            text = "Enforced",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ADE80),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // App Limit Status Card (Exact UI Layout from Image)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF15161A))
                    .border(1.dp, Color(0xFF24262E), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top: App Icon + "Xm limit" on Left, Flame Streak "🔥 X days" on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (appIconDrawable != null) {
                                Image(
                                    bitmap = appIconDrawable!!.toBitmap(64, 64).asImageBitmap(),
                                    contentDescription = appName,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Apps,
                                    contentDescription = null,
                                    tint = FocusColors.Primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Text(
                                text = "${formatMinutesLabel(totalDailyLimit)} limit",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = FocusColors.TextSecondary,
                                    fontSize = 14.sp
                                )
                            )
                        }

                // Lottie Fire Animation for Streak
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FocusLottieAnimation(
                        rawRes = R.raw.fire_animation,
                        modifier = Modifier.size(24.dp),
                        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever
                    )
                    Text(
                        text = "$streakDays days",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFBA834),
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Progress Bar
                    val progress = (actualUsedMinutes.toFloat() / totalDailyLimit.toFloat()).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color(0xFF22242B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = progress)
                                .height(26.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (progress >= 1f) FocusColors.CoralWarning else FocusColors.Primary
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Bottom Stats: Spent today vs Limit left
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = formatMinutesLabel(actualUsedMinutes),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "Spent today",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    color = FocusColors.TextMuted,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatMinutesLabel(actualRemainingDailyMinutes),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "Limit left",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    color = FocusColors.TextMuted,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Subtitle Prompt
            if (!isExhausted) {
                Text(
                    text = if (mode == AppLimitOverlayMode.SESSION_COMPLETE) {
                        "Time's up! Choose time to continue or exit"
                    } else {
                        "How long do you want to use?"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic options based on actual remaining daily minutes
                val durationOptions = remember(actualRemainingDailyMinutes) {
                    if (actualRemainingDailyMinutes <= 1) {
                        listOf(1)
                    } else if (actualRemainingDailyMinutes < 5) {
                        listOf(minOf(2, actualRemainingDailyMinutes), actualRemainingDailyMinutes).distinct()
                    } else if (actualRemainingDailyMinutes < 10) {
                        listOf(2, 5, actualRemainingDailyMinutes).distinct()
                    } else if (actualRemainingDailyMinutes < 20) {
                        listOf(2, 5, 10, actualRemainingDailyMinutes).distinct()
                    } else {
                        listOf(2, 5, 10, 20)
                    }
                }

                if (durationOptions.size == 4) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DurationPillButton(
                                label = "${durationOptions[0]} mins",
                                onClick = { onSelectDuration(durationOptions[0]) },
                                modifier = Modifier.weight(1f)
                            )
                            DurationPillButton(
                                label = "${durationOptions[1]} mins",
                                onClick = { onSelectDuration(durationOptions[1]) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DurationPillButton(
                                label = "${durationOptions[2]} mins",
                                onClick = { onSelectDuration(durationOptions[2]) },
                                modifier = Modifier.weight(1f)
                            )
                            DurationPillButton(
                                label = if (durationOptions[3] == actualRemainingDailyMinutes && actualRemainingDailyMinutes < 20) {
                                    "${durationOptions[3]}m (Max)"
                                } else {
                                    "${durationOptions[3]} mins"
                                },
                                onClick = { onSelectDuration(durationOptions[3]) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        durationOptions.forEach { opt ->
                            DurationPillButton(
                                label = if (opt == 1) "1 min" else if (opt == actualRemainingDailyMinutes && actualRemainingDailyMinutes < 20) "${opt}m (Max)" else "$opt mins",
                                onClick = { onSelectDuration(opt) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // Daily Limit Exhausted state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1B1B20))
                        .border(1.dp, Color(0xFF282932), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Daily Limit Reached",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.CoralWarning,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val emergencyRemaining = (emergencyUsesAllowed - emergencyUsesCount).coerceAtLeast(0)
                    Text(
                        text = if (strictModeEnabledState && emergencyRemaining == 0) {
                            "Strict Mode is active and your emergency passes are exhausted. No extensions available until tomorrow."
                        } else if (strictModeEnabledState) {
                            "Daily allowance reached. You have $emergencyRemaining emergency pass${if (emergencyRemaining > 1) "es" else ""} available before strict lockdown."
                        } else {
                            "You have exhausted today's allowance. Take a deep breath and close the app to protect your discipline streak."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    )

                    // Emergency Pass if available (supported in both normal and strict mode)
                    if (emergencyRemaining > 0) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onUseEmergency,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, FocusColors.Primary.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = FocusColors.Primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("use_emergency_button")
                        ) {
                            Text(
                                text = "Use Emergency Pass (5m) • $emergencyRemaining remaining",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Bottom Button: "Close <AppName>" (Dark Green Pill from Image)
            Button(
                onClick = onGoToHome,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F4D1F),
                    contentColor = Color(0xFF4ADE80)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("close_app_button")
            ) {
                Text(
                    text = "Close $appName",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                )
            }
        }
    }

    // Motivational Reflection Dialog before Turning Off / Quitting Limit in Normal Mode
    if (showMotivationalQuitDialog) {
        MotivationalQuitReflectionDialog(
            appName = appName,
            currentStreakDays = streakDays,
            onDismiss = { showMotivationalQuitDialog = false },
            onConfirmQuit = {
                showMotivationalQuitDialog = false
                onTurnOffAndResetStreak()
            }
        )
    }

    // Strict Mode Info Dialog
    if (showStrictModeInfoDialog) {
        AlertDialog(
            onDismissRequest = { showStrictModeInfoDialog = false },
            containerColor = Color(0xFF191A20),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = Color(0xFFFACC15),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Enable Strict Mode for $appName",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "When Strict Mode is active:",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = FocusColors.TextPrimary
                        )
                    )
                    Text(
                        text = "• The 'Turn off' option will be completely disabled.\n• Once the daily limit is exhausted, the app is strictly locked until tomorrow.\n• No emergency extensions are permitted.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            lineHeight = 20.sp
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        strictModeEnabledState = true
                        onEnableStrictMode()
                        showStrictModeInfoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFACC15),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Turn On Strict Mode", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStrictModeInfoDialog = false }) {
                    Text("Cancel", color = FocusColors.TextMuted)
                }
            }
        )
    }
}

@Composable
private fun DurationPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(Color(0xFF22242B))
            .border(1.dp, Color(0xFF2F323C), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp
            )
        )
    }
}

/**
 * Mindful Reflection & Motivational Process before quitting or bypassing an app limit.
 * Enforces a 5-second mindfulness pause, displays discipline quotes, and warns of streak loss.
 */
@Composable
private fun MotivationalQuitReflectionDialog(
    appName: String,
    currentStreakDays: Int,
    onDismiss: () -> Unit,
    onConfirmQuit: () -> Unit
) {
    var countdownSeconds by remember { mutableIntStateOf(5) }
    var quote by remember { mutableStateOf<FocusQuote?>(null) }

    LaunchedEffect(Unit) {
        quote = FocusQuotesProvider.getNextQuote()
        while (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141519),
        icon = {
            Icon(
                imageVector = Icons.Rounded.FormatQuote,
                contentDescription = null,
                tint = FocusColors.Primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Discipline Check",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Motivational Quote Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1D1F26))
                        .border(1.dp, Color(0xFF2C2F3A), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "\"${quote?.text ?: "Discipline is choosing between what you want now and what you want most."}\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 20.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "— ${quote?.author ?: "Abraham Lincoln"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.Primary
                            ),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                // Streak Reset Warning
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2B181A))
                        .border(1.dp, FocusColors.CoralWarning.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🔥", fontSize = 20.sp)
                        Column {
                            Text(
                                text = "Streak Reset Warning",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = FocusColors.CoralWarning,
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = "Turning off the limit will reset your 🔥 $currentStreakDays days discipline streak back to 0 days.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                // Mindfulness Breath Countdown
                if (countdownSeconds > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { (5 - countdownSeconds) / 5f },
                            modifier = Modifier.size(20.dp),
                            color = FocusColors.Primary,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Take a mindful breath... ($countdownSeconds s)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontStyle = FontStyle.Italic
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.Primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Stay Disciplined & Keep Streak",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onConfirmQuit,
                enabled = countdownSeconds == 0,
                border = BorderStroke(
                    1.dp,
                    if (countdownSeconds == 0) FocusColors.CoralWarning.copy(alpha = 0.6f) else Color(0xFF333333)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = FocusColors.CoralWarning,
                    disabledContentColor = Color(0xFF666666)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (countdownSeconds > 0) "Turn Off ($countdownSeconds s)" else "Turn Off Limit & Reset Streak",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

private fun formatMinutesLabel(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
