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
 * Unlock widget: today's phone unlock count with comparison against the trailing
 * 7-day average. Tapping opens FocusShield.
 */
class FocusShieldUnlockWidget : GlanceAppWidget() {

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
            UnlockWidgetContent(launchIntent, snapshot)
        }
    }

    @Composable
    private fun UnlockWidgetContent(launchIntent: Intent, snapshot: FocusWidgetSnapshot) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(launchIntent))
        ) {
            WidgetCard {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetTitle("PHONE UNLOCKS")
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        WidgetValue(
                            text = "${snapshot.unlockCount}",
                            color = when {
                                snapshot.unlockCount <= snapshot.avgUnlocks7d -> FocusWidgetTheme.green
                                else -> FocusWidgetTheme.amber
                            }
                        )
                    }
                    Column {
                        WidgetCaption("7-day avg")
                        WidgetCaption(
                            text = "${snapshot.avgUnlocks7d}",
                            color = FocusWidgetTheme.accent
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(4.dp))
                WidgetCaption(captionFor(snapshot))
            }
        }
    }

    private fun captionFor(snapshot: FocusWidgetSnapshot): String {
        return when {
            snapshot.avgUnlocks7d > 0 && snapshot.unlockCount < snapshot.avgUnlocks7d ->
                "Below your weekly average. Keep it up!"
            snapshot.unlockCount == 0 -> "No unlocks yet today. Stay focused!"
            else -> "Above your weekly average. Time to focus?"
        }
    }
}

class FocusShieldUnlockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusShieldUnlockWidget()
}
