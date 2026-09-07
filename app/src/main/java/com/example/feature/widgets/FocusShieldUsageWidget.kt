package com.example.feature.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.example.MainActivity

/**
 * Usage widget: today's total focus time with a ten-segment progress bar toward the
 * daily study goal. Tapping opens FocusShield.
 */
class FocusShieldUsageWidget : GlanceAppWidget() {

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
            UsageWidgetContent(launchIntent, snapshot)
        }
    }

    @Composable
    private fun UsageWidgetContent(launchIntent: Intent, snapshot: FocusWidgetSnapshot) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(launchIntent))
        ) {
            WidgetCard {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetTitle("TODAY'S GOAL")
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        WidgetValue(FocusWidgetSnapshot.formatDuration(snapshot.todayStudyMillis))
                    }
                    Column {
                        WidgetCaption(
                            text = "of ${snapshot.dailyGoalMinutes / 60}h ${snapshot.dailyGoalMinutes % 60}m",
                            color = FocusWidgetTheme.accent
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                SegmentedProgress(snapshot.goalProgress)
                Spacer(modifier = GlanceModifier.height(4.dp))
                WidgetCaption(
                    text = goalCaption(snapshot),
                    color = if (snapshot.goalProgress >= 1f) FocusWidgetTheme.green else FocusWidgetTheme.title
                )
            }
        }
    }

    private fun goalCaption(snapshot: FocusWidgetSnapshot): String {
        val percent = (snapshot.goalProgress * 100).toInt()
        return when {
            snapshot.goalProgress >= 1f -> "Daily goal complete! ($percent%)"
            else -> "$percent% of daily goal"
        }
    }
}

class FocusShieldUsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusShieldUsageWidget()
}
