package com.example.feature.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity

/**
 * Detailed 4x2 Home Screen Widget:
 * Displays session launcher / live timer countdown with Pause/Resume control,
 * focus stats & streak, and a preview of today's study plan items. Tapping opens FocusShield.
 */
class FocusShieldDetailedWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = FocusWidgetSnapshot.load(context)
        val launchIntent = Intent(context, MainActivity::class.java)
        provideContent {
            DetailedWidgetContent(launchIntent, snapshot)
        }
    }

    @Composable
    private fun DetailedWidgetContent(launchIntent: Intent, snapshot: FocusWidgetSnapshot) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(launchIntent))
        ) {
            WidgetCard {
                // Top Row: Timer + Stats
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timer Section
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        if (snapshot.isActive) {
                            WidgetTitle(if (snapshot.isPaused) "PAUSED" else "ACTIVE SESSION")
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = snapshot.subject.ifBlank { "Study Session" },
                                style = TextStyle(
                                    color = ColorProvider(FocusWidgetTheme.value),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = FocusWidgetSnapshot.formatCountdown(snapshot.remainingMillis),
                                style = TextStyle(
                                    color = ColorProvider(if (snapshot.isPaused) FocusWidgetTheme.amber else FocusWidgetTheme.green),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else {
                            WidgetTitle("TODAY'S FOCUS")
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            WidgetValue(FocusWidgetSnapshot.formatDuration(snapshot.todayStudyMillis))
                            WidgetCaption("Goal: ${snapshot.dailyGoalMinutes / 60}h • ${(snapshot.goalProgress * 100).toInt()}%")
                        }
                    }

                    Spacer(modifier = GlanceModifier.width(12.dp))

                    // Streak & Quick Action Controls
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "🔥 ${snapshot.streakDays}d",
                            style = TextStyle(
                                color = ColorProvider(FocusWidgetTheme.amber),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        WidgetCaption("current streak")
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        if (!snapshot.isActive) {
                            Text(
                                text = "▶ Start 25m Focus",
                                style = TextStyle(
                                    color = ColorProvider(FocusWidgetTheme.accent),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = GlanceModifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clickable(actionRunCallback<QuickStartFocusWidgetAction>())
                            )
                        } else if (!snapshot.isStrictMode) {
                            Text(
                                text = if (snapshot.isPaused) "▶ Resume Session" else "⏸ Pause Session",
                                style = TextStyle(
                                    color = ColorProvider(FocusWidgetTheme.accent),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = GlanceModifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clickable(actionRunCallback<TogglePauseWidgetAction>())
                            )
                        }
                    }
                }

                if (snapshot.isActive) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    SegmentedProgress(snapshot.sessionProgress)
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Bottom Section: Daily Study Plan Preview
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetTitle("DAILY STUDY PLAN")
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        if (snapshot.nextPlanSubject != null) {
                            val timeStr = snapshot.nextPlanTime?.let { " @ $it" } ?: ""
                            val topicStr = snapshot.nextPlanTopic?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
                            Text(
                                text = "📖 ${snapshot.nextPlanSubject}$topicStr$timeStr",
                                style = TextStyle(
                                    color = ColorProvider(FocusWidgetTheme.value),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = "No plans scheduled for today",
                                style = TextStyle(
                                    color = ColorProvider(FocusWidgetTheme.title),
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    if (snapshot.totalPlansToday > 0) {
                        Text(
                            text = "${snapshot.completedPlansToday}/${snapshot.totalPlansToday} Done",
                            style = TextStyle(
                                color = ColorProvider(
                                    if (snapshot.completedPlansToday == snapshot.totalPlansToday) FocusWidgetTheme.green
                                    else FocusWidgetTheme.accent
                                ),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

class FocusShieldDetailedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusShieldDetailedWidget()
}
