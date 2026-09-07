package com.example.feature.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity

/**
 * Focus & Streak widget: shows the active session countdown (or today's focus time
 * with streak flame) directly on the home screen. Tapping opens FocusShield.
 */
class FocusShieldFocusWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 60.dp),
            DpSize(180.dp, 70.dp),
            DpSize(250.dp, 80.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = FocusWidgetSnapshot.load(context)
        val launchIntent = Intent(context, MainActivity::class.java)
        provideContent {
            FocusWidgetContent(launchIntent, snapshot)
        }
    }

    @Composable
    private fun FocusWidgetContent(launchIntent: Intent, snapshot: FocusWidgetSnapshot) {
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                        Text(
                            text = FocusWidgetSnapshot.formatCountdown(snapshot.remainingMillis),
                            style = TextStyle(
                                color = ColorProvider(if (snapshot.isPaused) FocusWidgetTheme.amber else FocusWidgetTheme.green),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(8.dp))
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
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "🔥 ${snapshot.streakDays}",
                                style = TextStyle(
                                    color = ColorProvider(FocusWidgetTheme.amber),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            WidgetCaption("day streak")
                        }
                    }
                }
            }
        }
    }
}

class FocusShieldFocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusShieldFocusWidget()
}
