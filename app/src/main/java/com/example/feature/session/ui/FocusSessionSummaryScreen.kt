package com.example.feature.session.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
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
import com.example.core.design.LocalFocusMotion
import com.example.core.design.rememberFocusInfiniteFloat
import com.example.core.ui.FocusDestructiveDialog
import com.example.core.ui.FocusScreenHeader
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.sessionModeTitle
import com.example.core.util.TimeFormatter
import com.example.data.model.SessionMode
import com.example.data.repository.SessionQuote
import com.example.data.repository.SessionQuoteRepository
import com.example.feature.session.domain.SessionSummaryData
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

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
 * Post-session summary screen. A quote opens the page, then the recorded study time, the session
 * tags, and a truthful breakdown of what protection was actually active. Saving and discarding are
 * explicit decisions: the session is never deleted without first naming that consequence.
 */
@Composable
fun FocusSessionSummaryScreen(
    summaryData: SessionSummaryData,
    onSaveSession: () -> Unit,
    onDiscardSession: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    saveError: String? = null,
    onDismissSaveError: () -> Unit = {}
) {
    val context = LocalContext.current
    val timeFormat: DateFormat = remember(context) {
        android.text.format.DateFormat.getTimeFormat(context)
    }
    val startFormatted = remember(summaryData.startedAt, timeFormat) {
        timeFormat.format(Date(summaryData.startedAt))
    }
    val endFormatted = remember(summaryData.endedAt, timeFormat) {
        timeFormat.format(Date(summaryData.endedAt))
    }

    val formattedStudyDuration = studyDurationText(summaryData.pureStudyDurationMillis)
    val modeTitle = sessionModeTitle(summaryData.mode)

    val isCompletedGoal = summaryData.isCompleted && !summaryData.isCancelled
    val isSessionStrict = summaryData.isStrictModeEnabled && summaryData.mode != SessionMode.STOPWATCH
    val isYouTubeStudyActive =
        summaryData.isYouTubeStudyModeEnabled || summaryData.isStudyChannelsEnabled
    val distractionsDetail =
        if (summaryData.distractionsBlockedCount > 0) {
            stringResource(
                R.string.session_summary_distractions_detail,
                summaryData.distractionsBlockedCount
            )
        } else {
            stringResource(R.string.session_summary_distractions_none_detail)
        }
    val appShieldDetail = when {
        !summaryData.isAppBlockingEnabled -> stringResource(R.string.session_summary_not_active)
        summaryData.blockedAppsCount > 0 -> pluralStringResource(
            R.plurals.session_summary_apps_shielded,
            summaryData.blockedAppsCount,
            summaryData.blockedAppsCount
        )
        else -> stringResource(R.string.session_summary_app_shield_none)
    }

    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestDiscard: () -> Unit = {
        if (!isSaving) showDiscardDialog = true
    }

    BackHandler(enabled = !isSaving) {
        showDiscardDialog = true
    }

    // Live-writing quote state fetched from Cloud Firestore (/session_quotes)
    var activeQuote by remember(summaryData.sessionId) {
        mutableStateOf<SessionQuote?>(null)
    }

    LaunchedEffect(summaryData.sessionId) {
        activeQuote = SessionQuoteRepository.fetchRandomQuoteForSummary()
    }

    if (showDiscardDialog) {
        FocusDestructiveDialog(
            title = stringResource(R.string.session_summary_discard_title),
            message = stringResource(R.string.session_summary_discard_body),
            onConfirm = { onDiscardSession() },
            onDismiss = { showDiscardDialog = false },
            confirmLabel = stringResource(R.string.session_summary_discard_confirm)
        )
    }

    Scaffold(
        topBar = {
            FocusScreenHeader(
                title = stringResource(R.string.session_summary_title),
                onBack = requestDiscard,
                modifier = Modifier.statusBarsPadding(),
                backButtonModifier = Modifier.testTag("summary_back_button"),
                actions = {
                    IconButton(
                        onClick = requestDiscard,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("summary_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.session_summary_discard_desc),
                            tint = FocusColors.TextSecondary
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Every exit from this screen is an explicit save or a confirmed discard.
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
                        .padding(
                            horizontal = FocusSpacing.screenHorizontal,
                            vertical = FocusSpacing.base
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (saveError != null) {
                        FocusStatusMessage(
                            message = saveError,
                            isError = true,
                            onDismiss = onDismissSaveError
                        )
                    }

                    Button(
                        onClick = {
                            if (!isSaving) onSaveSession()
                        },
                        enabled = !isSaving,
                        shape = FocusShapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FocusColors.Primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = FocusColors.Primary.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
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
                                text = stringResource(R.string.session_summary_saving),
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
                                    text = stringResource(R.string.session_summary_save),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = requestDiscard,
                        enabled = !isSaving,
                        shape = FocusShapes.large,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            FocusColors.CoralWarning.copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FocusColors.CoralText
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
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
                                text = stringResource(R.string.session_summary_discard),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = FocusColors.CoralText
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
                .padding(horizontal = FocusSpacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Live-writing quote showcase in the top space.
            LiveWritingQuoteHero(
                quote = activeQuote ?: SessionQuoteRepository.PRELOADED_QUOTES.first(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_summary_live_quote")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(
                    if (isCompletedGoal) {
                        R.string.session_summary_complete_title
                    } else {
                        R.string.session_summary_ended_title
                    }
                ),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = FocusColors.TextPrimary,
                    fontSize = 22.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    if (isCompletedGoal) {
                        R.string.session_summary_complete_body
                    } else {
                        R.string.session_summary_ended_body
                    }
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            TotalStudyTimeRecordedCard(
                formattedStudyDuration = formattedStudyDuration,
                startFormatted = startFormatted,
                endFormatted = endFormatted
            )

            Spacer(modifier = Modifier.height(16.dp))

            val hasSubjectOrTopicOrGoal = summaryData.subject.isNotBlank() ||
                summaryData.topic.isNotBlank() ||
                summaryData.goal.isNotBlank()

            if (hasSubjectOrTopicOrGoal) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FocusShapes.large)
                        .background(FocusColors.Surface)
                        .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.large)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (summaryData.subject.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.session_summary_subject),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = summaryData.subject,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.TextPrimary
                                    ),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (summaryData.topic.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.session_summary_topic),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = summaryData.topic,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = FocusColors.TextPrimary
                                    ),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
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
            }

            // 2x2 key metrics. Intrinsic sizing keeps both tiles in a row equal when a
            // translated value wraps to a second line.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricSummaryTile(
                    title = stringResource(R.string.session_summary_mode),
                    value = modeTitle,
                    icon = when (summaryData.mode) {
                        SessionMode.TIMER -> Icons.Rounded.HourglassBottom
                        SessionMode.POMODORO -> Icons.Rounded.Timer
                        SessionMode.STOPWATCH -> Icons.Rounded.Schedule
                    },
                    accentColor = FocusColors.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                MetricSummaryTile(
                    title = stringResource(R.string.session_summary_distractions),
                    value = if (summaryData.distractionsBlockedCount > 0) {
                        stringResource(
                            R.string.session_summary_distractions_value,
                            summaryData.distractionsBlockedCount
                        )
                    } else {
                        stringResource(R.string.session_summary_distractions_none_value)
                    },
                    icon = Icons.Rounded.Block,
                    accentColor = if (summaryData.distractionsBlockedCount > 0) {
                        FocusColors.CoralWarning
                    } else {
                        FocusColors.EmeraldSuccess
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricSummaryTile(
                    title = stringResource(R.string.session_summary_youtube_mode),
                    value = stringResource(
                        if (isYouTubeStudyActive) {
                            R.string.session_summary_study_on
                        } else {
                            R.string.session_summary_standard
                        }
                    ),
                    icon = Icons.Rounded.SmartDisplay,
                    accentColor = if (isYouTubeStudyActive) {
                        FocusColors.ChannelsAmber
                    } else {
                        FocusColors.TextMuted
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                val secondaryMetricTitle: String
                val secondaryMetricValue: String
                val secondaryMetricIcon: ImageVector

                when (summaryData.mode) {
                    SessionMode.POMODORO -> {
                        secondaryMetricTitle = stringResource(R.string.session_summary_pomodoro_cycles)
                        secondaryMetricValue = stringResource(
                            R.string.session_summary_cycles_value,
                            summaryData.completedPomodoroCycles,
                            summaryData.totalPomodoroCycles
                        )
                        secondaryMetricIcon = Icons.Rounded.CheckCircle
                    }
                    SessionMode.STOPWATCH -> {
                        secondaryMetricTitle = stringResource(R.string.session_summary_laps)
                        secondaryMetricValue = pluralStringResource(
                            R.plurals.session_summary_laps_value,
                            summaryData.lapsCount,
                            summaryData.lapsCount
                        )
                        secondaryMetricIcon = Icons.Rounded.Flag
                    }
                    SessionMode.TIMER -> {
                        secondaryMetricTitle = stringResource(R.string.session_summary_break_time)
                        secondaryMetricValue = if (summaryData.totalBreakDurationMillis > 0) {
                            TimeFormatter.formatDigital(summaryData.totalBreakDurationMillis, true)
                        } else {
                            stringResource(R.string.session_summary_none_value)
                        }
                        secondaryMetricIcon = Icons.Rounded.Schedule
                    }
                }

                MetricSummaryTile(
                    title = secondaryMetricTitle,
                    value = secondaryMetricValue,
                    icon = secondaryMetricIcon,
                    accentColor = FocusColors.CyanBlue,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.large)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.large)
                    .padding(FocusSpacing.base)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.session_summary_protection),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextSecondary
                        )
                    )

                    FeatureCheckRow(
                        label = stringResource(R.string.session_summary_distractions_label),
                        isActive = summaryData.distractionsBlockedCount > 0,
                        detail = distractionsDetail
                    )

                    FeatureCheckRow(
                        label = stringResource(R.string.session_summary_youtube_label),
                        isActive = isYouTubeStudyActive,
                        detail = stringResource(
                            if (isYouTubeStudyActive) {
                                R.string.session_summary_youtube_active
                            } else {
                                R.string.session_summary_not_active
                            }
                        )
                    )

                    FeatureCheckRow(
                        label = stringResource(R.string.session_summary_app_shield),
                        isActive = summaryData.isAppBlockingEnabled,
                        detail = appShieldDetail
                    )

                    FeatureCheckRow(
                        label = stringResource(R.string.session_summary_browser),
                        isActive = summaryData.isBrowserStudyModeEnabled,
                        detail = stringResource(
                            if (summaryData.isBrowserStudyModeEnabled) {
                                R.string.session_summary_browser_active
                            } else {
                                R.string.session_summary_not_active
                            }
                        )
                    )

                    FeatureCheckRow(
                        label = stringResource(R.string.session_summary_strict),
                        isActive = isSessionStrict,
                        detail = stringResource(
                            if (isSessionStrict) {
                                R.string.session_summary_strict_active
                            } else {
                                R.string.session_summary_not_active
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/** Localised compact study duration, e.g. "1 hour 30 minutes". */
@Composable
private fun studyDurationText(millis: Long): String {
    val totalMinutes = millis.coerceAtLeast(0L) / 60_000L
    val hours = (totalMinutes / 60L).toInt()
    val minutes = (totalMinutes % 60L).toInt()
    val hourText = if (hours > 0) {
        pluralStringResource(R.plurals.session_picker_hours, hours, hours)
    } else {
        null
    }
    val minuteText = if (minutes > 0 || hours == 0) {
        pluralStringResource(R.plurals.session_picker_minutes, minutes, minutes)
    } else {
        null
    }
    return listOfNotNull(hourText, minuteText).joinToString(" ")
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
    val reduceMotion = LocalFocusMotion.current.reduceMotion
    val entryOffsetPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    var visibleCharCount by remember(quote.id, quote.text) { mutableIntStateOf(0) }
    var isFinishedWriting by remember(quote.id, quote.text) { mutableStateOf(false) }

    LaunchedEffect(quote.id, quote.text, reduceMotion) {
        visibleCharCount = 0
        isFinishedWriting = false
        val fullLength = quote.text.length
        if (reduceMotion || fullLength == 0) {
            visibleCharCount = fullLength
            isFinishedWriting = true
            return@LaunchedEffect
        }
        // This decorative reveal must never withhold the quote for more than five seconds.
        val stepDelay = minOf(quote.typingSpeedMs.coerceIn(12L, 80L), 5_000L / fullLength)
        for (i in 1..fullLength) {
            delay(stepDelay)
            visibleCharCount = i
        }
        isFinishedWriting = true
    }

    val cursorAlpha by rememberFocusInfiniteFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha",
        staticValue = 1f
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
                    append("\u201C")
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
                        append("\u201D")
                    }
                } else {
                    withStyle(
                        SpanStyle(
                            color = FocusColors.Primary.copy(alpha = cursorAlpha),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    ) {
                        append(" \u258D")
                    }
                }
            },
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("session_summary_live_quote_text")
                .clearAndSetSemantics { text = AnnotatedString(quote.text) }
        )

        AnimatedVisibility(
            visible = isFinishedWriting && quote.author.isNotBlank(),
            enter = if (reduceMotion) {
                fadeIn(tween(120))
            } else {
                fadeIn(tween(300)) + slideInVertically(tween(300)) { entryOffsetPx }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\u2014 ${quote.author}",
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

/** Gridless total study time hero section, with the recorded start and end time. */
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
                text = stringResource(R.string.session_summary_total_study),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.Primary,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.sp
                ),
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = formattedStudyDuration,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = FocusColors.TextPrimary,
                fontSize = 32.sp
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(
                R.string.session_summary_time_range,
                startFormatted,
                endFormatted
            ),
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextSecondary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            ),
            textAlign = TextAlign.Center
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
