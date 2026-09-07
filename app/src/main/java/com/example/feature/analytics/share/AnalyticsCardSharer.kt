package com.example.feature.analytics.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.example.feature.analytics.domain.PeriodAnalyticsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders the analytics summary card, saves it to the app's share cache, and fires
 * a share sheet (ACTION_SEND, image/png) via FileProvider.
 */
object AnalyticsCardSharer {

    /**
     * Renders + shares the card. Safe to call from a coroutine on any dispatcher;
     * bitmap work runs on IO, the share sheet launches on Main.
     */
    suspend fun shareSummaryCard(
        context: Context,
        summary: PeriodAnalyticsSummary,
        dailyGoalMinutes: Int
    ) {
        val bitmap = withContext(Dispatchers.Default) {
            AnalyticsShareCardRenderer.render(summary, dailyGoalMinutes)
        }

        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val output = File(dir, "focusshield_analytics_${System.currentTimeMillis()}.png")
            output.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            // Keep the cache small: delete any cards older than a day
            dir.listFiles()?.forEach { old ->
                if (old != output && System.currentTimeMillis() - old.lastModified() > 24 * 60 * 60 * 1000L) {
                    old.delete()
                }
            }
            output
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Study Progress")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}
