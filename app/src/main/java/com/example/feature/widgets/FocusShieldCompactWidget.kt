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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity

/**
 * Compact 2x2 Home Screen Widget:
 * Provides a quick launcher for focus sessions, displaying active timer countdown
 * with interactive Pause/Resume or today's study minutes and streak. Tapping opens FocusShield.
 */
class FocusShieldCompactWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = FocusWidgetSnapshot.load(context)
        val launchIntent = Intent(context, MainActivity::class.java)
        provideContent {
            CompactWidgetContent(launchIntent, snapshot)
        }
    }

    @Composable
    private fun CompactWidgetContent(launchIntent: Intent, snapshot: FocusWidgetSnapshot) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(launchIntent))
        ) {
            WidgetCard {
                if (snapshot.isActive) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            WidgetTitle(if (snapshot.isPaused) "PAUSED" else "IN FOCUS")
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = snapshot.subject.ifBlank { "Study Session" },
                                style = TextStyle(
                                    color = ColorProvider(FocusWidgetTheme.value),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                        if (!snapshot.isStrictMode) {
                            Text(
                                text = if (snapshot.isPaused) "▶ Resume" else "⏸ Pause",
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
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = FocusWidgetSnapshot.formatCountdown(snapshot.remainingMillis),
                        style = TextStyle(
                            color = ColorProvider(if (snapshot.isPaused) FocusWidgetTheme.amber else FocusWidgetTheme.green),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    SegmentedProgress(snapshot.sessionProgress)
                } else {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            WidgetTitle("TODAY'S FOCUS")
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            WidgetValue(FocusWidgetSnapshot.formatDuration(snapshot.todayStudyMillis))
                        }
                        Text(
                            text = "🔥 ${snapshot.streakDays}",
                            style = TextStyle(
                                color = ColorProvider(FocusWidgetTheme.amber),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionRunCallback<QuickStartFocusWidgetAction>())
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "▶  Start 25m Focus",
                            style = TextStyle(
                                color = ColorProvider(FocusWidgetTheme.accent),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

class FocusShieldCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusShieldCompactWidget()
}
