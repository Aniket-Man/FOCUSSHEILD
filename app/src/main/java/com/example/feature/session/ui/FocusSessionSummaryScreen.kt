package com.example.feature.session.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.util.TimeFormatter
import com.example.data.model.SessionMode
import com.example.data.repository.SessionQuote
import com.example.data.repository.SessionQuoteRepository
import com.example.feature.session.domain.SessionSummaryData
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Eye-attractive custom Google Fonts bundled locally via font-util
private val PlayfairDisplayFontFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display, FontWeight.Bold)
)

private val CormorantGaramondFontFamily = FontFamily(
    Font(R.font.cormorant_garamond, FontWeight.Normal),
    Font(R.font.cormorant_garamond, FontWeight.Bold)
)

private val CinzelFontFamily = FontFamily(
    Font(R.font.cinzel, FontWeight.Normal),
    Font(R.font.cinzel, FontWeight.Bold)
)

/**
 * Post-Session Summary Screen matching the user's reference layout:
 * - No gray circular dot at the top.
 * - In that top space, a Live-Writing Animated Quote fetched from Cloud Firestore (`/session_quotes`)
 *   writes itself out live in an eye-catching custom display font.
 * - Clean `TOTAL STUDY TIME RECORDED` hero card, Subject/Topic/Goal card, 2x2 metric grid,
 *   `Protection & Telemetry Breakdown` card, and `Save Session` / `Don't Save` bottom actions.
 */
@Composable
fun FocusSessionSummaryScreen(
    summaryData: SessionSummaryData,
    onSaveSession: () -> Unit,
    onDiscardSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSaving by remember { mutableStateOf(false) }

    // Intercept back button to prompt discard/leave cleanly
    BackHandler {
        if (!isSaving) {
            onDiscardSession()
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val startFormatted = remember(summaryData.startedAt) { timeFormat.format(Date(summaryData.startedAt)) }
    val endFormatted = remember(summaryData.endedAt) { timeFormat.format(Date(summaryData.endedAt)) }

    val formattedStudyDuration = remember(summaryData.pureStudyDurationMillis) {
        TimeFormatter.formatDetailed(summaryData.pureStudyDurationMillis)
    }

    val isCompletedGoal = summaryData.isCompleted && !summaryData.isCancelled

    // Live-writing quote state fetched from Cloud Firestore (/session_quotes)
    var activeQuote by remember(summaryData.sessionId) {
        mutableStateOf<SessionQuote?>(null)
    }

    LaunchedEffect(summaryData.sessionId) {
        activeQuote = SessionQuoteRepository.fetchRandomQuoteForSummary()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (!isSaving) onDiscardSession()
                    },
                    modifier = Modifier.testTag("summary_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }

                // Header title
                Text(
                    text = "Session Summary",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 17.sp
                    )
                )

                // Top right discard icon button for quick navigation
                IconButton(
                    onClick = {
                        if (!isSaving) onDiscardSession()
                    },
                    modifier = Modifier.testTag("summary_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Discard and Exit",
                        tint = FocusColors.TextSecondary
                    )
                }
            }
        },
        bottomBar = {
            // Prominent Bottom Action Bar with "Save Session" and "Don't Save"
            Surface(
                color = FocusColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 16.dp, ambientColor = Color.Black.copy(alpha = 0.2f))
                    .border(1.dp, FocusColors.CardBorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Save Session
                    Button(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                onSaveSession()
                            }
                        },
                        enabled = !isSaving,
                        shape = FocusShapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FocusColors.Primary,
                            contentColor = Color.White,
                            disabledContainerColor = FocusColors.Primary.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_session_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Saving Session...",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save Session",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }
                    }

                    // Option 2: Don't Save
                    OutlinedButton(
                        onClick = {
                            if (!isSaving) {
                                onDiscardSession()
                            }
                        },
                        enabled = !isSaving,
                        shape = FocusShapes.large,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            FocusColors.CoralWarning.copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FocusColors.CoralWarning
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("dont_save_session_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = FocusColors.CoralWarning
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Don't Save",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = FocusColors.CoralWarning
                                )
                            )
                        }
                    }
                }
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier
            .fillMaxSize()
            .testTag("focus_session_summary_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Live-Writing Quote Showcase in the top space (gray circle dot completely removed)
            LiveWritingQuoteHero(
                quote = activeQuote ?: SessionQuoteRepository.PRELOADED_QUOTES.first(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_summary_live_quote")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isCompletedGoal) "Great Job! Session Finished" else "Focus Session Ended",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = FocusColors.TextPrimary,
                    fontSize = 22.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isCompletedGoal)
                    "Review all data taken during this session below. Choose whether to save it to your history and database."
                else
                    "Your session progress was stopped early. You can review the gathered metrics below before saving or discarding.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // TOTAL STUDY TIME RECORDED Hero Card (matches reference screenshot)
            TotalStudyTimeRecordedCard(
                formattedStudyDuration = formattedStudyDuration,
                startFormatted = startFormatted,
                endFormatted = endFormatted
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subject, Topic & Session Goal Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.large)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.large)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subject",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Text(
                            text = summaryData.subject,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Topic",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Text(
                            text = summaryData.topic,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = FocusColors.TextPrimary
                            )
                        )
                    }

                    if (summaryData.goal.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FocusShapes.medium)
                                .background(FocusColors.SurfaceSubtle)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                tint = FocusColors.Primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = summaryData.goal,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2x2 Grid: Key Telemetry Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Metric 1: Mode
                MetricSummaryTile(
                    title = "SESSION MODE",
                    value = summaryData.mode.label,
                    icon = when (summaryData.mode) {
                        SessionMode.TIMER -> Icons.Rounded.HourglassBottom
                        SessionMode.POMODORO -> Icons.Rounded.Timer
                        SessionMode.STOPWATCH -> Icons.Rounded.Schedule
                    },
                    accentColor = FocusColors.Primary,
                    modifier = Modifier.weight(1f)
                )

                // Metric 2: Distractions Blocked
                MetricSummaryTile(
                    title = "DISTRACTIONS BLOCKED",
                    value = "${summaryData.distractionsBlockedCount} blocked",
                    icon = Icons.Rounded.Block,
                    accentColor = if (summaryData.distractionsBlockedCount > 0) FocusColors.CoralWarning else FocusColors.EmeraldSuccess,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Metric 3: YouTube Study Mode Status
                val ytStatusText = if (summaryData.isYouTubeStudyModeEnabled || summaryData.isStudyChannelsEnabled) "Study Mode ON" else "Standard"
                MetricSummaryTile(
                    title = "YOUTUBE STUDY MODE",
                    value = ytStatusText,
                    icon = Icons.Rounded.SmartDisplay,
                    accentColor = if (summaryData.isYouTubeStudyModeEnabled || summaryData.isStudyChannelsEnabled) FocusColors.ChannelsAmber else FocusColors.TextMuted,
                    modifier = Modifier.weight(1f)
                )

                // Metric 4: Break Time / Pomodoro Cycles / Laps
                val secondaryMetricTitle: String
                val secondaryMetricValue: String
                val secondaryMetricIcon: ImageVector

                when (summaryData.mode) {
                    SessionMode.POMODORO -> {
                        secondaryMetricTitle = "POMODORO CYCLES"
                        secondaryMetricValue = "${summaryData.completedPomodoroCycles} of ${summaryData.totalPomodoroCycles}"
                        secondaryMetricIcon = Icons.Rounded.CheckCircle
                    }
                    SessionMode.STOPWATCH -> {
                        secondaryMetricTitle = "LAPS RECORDED"
                        secondaryMetricValue = "${summaryData.lapsCount} recorded"
                        secondaryMetricIcon = Icons.Rounded.Flag
                    }
                    SessionMode.TIMER -> {
                        secondaryMetricTitle = "BREAK TIME"
                        secondaryMetricValue = if (summaryData.totalBreakDurationMillis > 0) {
                            TimeFormatter.formatDigital(summaryData.totalBreakDurationMillis, true)
                        } else {
                            "None (0m)"
                        }
                        secondaryMetricIcon = Icons.Rounded.Schedule
                    }
                }

                MetricSummaryTile(
                    title = secondaryMetricTitle,
                    value = secondaryMetricValue,
                    icon = secondaryMetricIcon,
                    accentColor = FocusColors.CyanBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Protection & Feature Breakdown
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.large)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.large)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Protection & Telemetry Breakdown",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextSecondary
                        )
                    )

                    FeatureCheckRow(
                        label = "Distractions Intercepted & Blocked",
                        isActive = summaryData.distractionsBlockedCount > 0,
                        detail = "${summaryData.distractionsBlockedCount} attempts blocked"
                    )

                    FeatureCheckRow(
                        label = "YouTube Educational Study Mode",
                        isActive = summaryData.isYouTubeStudyModeEnabled || summaryData.isStudyChannelsEnabled,
                        detail = if (summaryData.isYouTubeStudyModeEnabled || summaryData.isStudyChannelsEnabled)
                            "Distracting videos blocked, study channels allowed"
                        else
                            "Not active"
                    )

                    FeatureCheckRow(
                        label = "App Shield & Distraction Blocker",
                        isActive = summaryData.isAppBlockingEnabled,
                        detail = if (summaryData.isAppBlockingEnabled) "${summaryData.blockedAppsCount} apps shielded" else "Not active"
                    )

                    FeatureCheckRow(
                        label = "Safe Browser Study Mode",
                        isActive = summaryData.isBrowserStudyModeEnabled,
                        detail = if (summaryData.isBrowserStudyModeEnabled) "Adult & distracting sites blocked" else "Not active"
                    )

                    FeatureCheckRow(
                        label = "Strict Mode Tamper Guard",
                        isActive = summaryData.isStrictModeEnabled,
                        detail = if (summaryData.isStrictModeEnabled) "Enforced throughout session" else "Standard focus"
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * Renders the Firestore quote in the top space of the Session Summary screen with a live-writing
 * (character-by-character ink reveal) animation and custom editorial/calligraphy typography.
 * Completely gridless: no outer card box, no border frame, and no background grid.
 */
@Composable
private fun LiveWritingQuoteHero(
    quote: SessionQuote,
    modifier: Modifier = Modifier
) {
    var visibleCharCount by remember(quote.id, quote.text) { mutableIntStateOf(0) }
    var isFinishedWriting by remember(quote.id, quote.text) { mutableStateOf(false) }

    LaunchedEffect(quote.id, quote.text) {
        visibleCharCount = 0
        isFinishedWriting = false
        val fullLength = quote.text.length
        val stepDelay = quote.typingSpeedMs.coerceIn(12L, 80L)
        for (i in 1..fullLength) {
            delay(stepDelay)
            visibleCharCount = i
        }
        isFinishedWriting = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "quoteCursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    val selectedFontFamily = remember(quote.fontStyle) {
        when (quote.fontStyle.lowercase()) {
            "calligraphy" -> CormorantGaramondFontFamily
            "classic" -> CinzelFontFamily
            else -> PlayfairDisplayFontFamily
        }
    }

    val displayedText = remember(quote.text, visibleCharCount) {
        quote.text.take(visibleCharCount.coerceIn(0, quote.text.length))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Live-writing quote text with glowing ink cursor (gridless, open layout)
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = FocusColors.Primary,
                        fontFamily = PlayfairDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                ) {
                    append("“")
                }
                withStyle(
                    SpanStyle(
                        color = FocusColors.TextPrimary,
                        fontFamily = selectedFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        fontSize = if (quote.fontStyle.equals("calligraphy", ignoreCase = true)) 22.sp else 20.sp
                    )
                ) {
                    append(displayedText)
                }
                if (isFinishedWriting) {
                    withStyle(
                        SpanStyle(
                            color = FocusColors.Primary,
                            fontFamily = PlayfairDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    ) {
                        append("”")
                    }
                } else {
                    withStyle(
                        SpanStyle(
                            color = FocusColors.Primary.copy(alpha = cursorAlpha),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    ) {
                        append(" ▍")
                    }
                }
            },
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("session_summary_live_quote_text")
        )

        AnimatedVisibility(
            visible = isFinishedWriting && quote.author.isNotBlank(),
            enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 2 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "— ${quote.author}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextSecondary,
                        fontSize = 12.5.sp,
                        letterSpacing = 1.1.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("session_summary_live_quote_author")
                )
            }
        }
    }
}

/**
 * Gridless `TOTAL STUDY TIME RECORDED` hero section (no outer box, no border, no grid lines).
 */
@Composable
private fun TotalStudyTimeRecordedCard(
    formattedStudyDuration: String,
    startFormatted: String,
    endFormatted: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp, horizontal = 16.dp)
            .testTag("total_study_time_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = FocusColors.Primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "TOTAL STUDY TIME RECORDED",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.Primary,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.sp
                )
            )
        }

        Text(
            text = formattedStudyDuration,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = FocusColors.TextPrimary,
                fontSize = 38.sp
            )
        )

        Text(
            text = "$startFormatted  →  $endFormatted",
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextSecondary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        )
    }
}

@Composable
private fun MetricSummaryTile(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(FocusShapes.large)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.large)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary,
                    fontSize = 15.sp
                )
            )
        }
    }
}

@Composable
private fun FeatureCheckRow(
    label: String,
    isActive: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) FocusColors.EmeraldSuccess.copy(alpha = 0.15f)
                    else FocusColors.TextMuted.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (isActive) FocusColors.EmeraldSuccess else FocusColors.TextMuted,
                modifier = Modifier.size(13.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = FocusColors.TextPrimary,
                    fontSize = 13.5.sp
                )
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isActive) FocusColors.TextSecondary else FocusColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }
    }
}
