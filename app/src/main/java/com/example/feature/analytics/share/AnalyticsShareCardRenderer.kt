package com.example.feature.analytics.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.feature.analytics.domain.PeriodAnalyticsSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Renders a shareable analytics summary card (1080x1440 PNG) from a
 * [PeriodAnalyticsSummary] using a fixed premium dark design, independent of the
 * app's current theme. Follows the Canvas+Paint bitmap precedent set by
 * ChannelLogoStorageManager.generateMonogramBitmap.
 */
object AnalyticsShareCardRenderer {

    private const val WIDTH = 1080
    private const val HEIGHT = 1440
    private const val PADDING = 72f
    private const val CONTENT_WIDTH = WIDTH - 2 * PADDING

    // Palette (premium dark — always looks right on any messenger's background)
    private val brandIndigo = Color.parseColor("#818CF8")
    private val chipBg = Color.parseColor("#312E4A")
    private val statCellBg = Color.parseColor("#221F35")
    private val textWhite = Color.parseColor("#FFFFFF")
    private val textMuted = Color.parseColor("#9CA3C4")
    private val textSoft = Color.parseColor("#E0E7FF")
    private val emerald = Color.parseColor("#34D399")
    private val amber = Color.parseColor("#F59E0B")

    fun render(summary: PeriodAnalyticsSummary, dailyGoalMinutes: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)

        var y = drawBrandRow(canvas, summary)

        y = drawHeroMetric(canvas, summary, startY = y + 110f)

        y = drawStatsGrid(canvas, summary, startY = y + 90f)

        y = drawDailyChart(canvas, summary, dailyGoalMinutes, startY = y + 100f)

        drawBestDayAndFooter(canvas, summary, startY = y + 70f)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                Color.parseColor("#0A0A12"),
                Color.parseColor("#1C1B2E"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgPaint)

        // Subtle decorative glows
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(28, 129, 140, 248)
        }
        canvas.drawCircle(WIDTH - 60f, 120f, 220f, glowPaint)
        canvas.drawCircle(40f, HEIGHT - 200f, 260f, glowPaint)
    }

    private fun drawBrandRow(canvas: Canvas, summary: PeriodAnalyticsSummary): Float {
        // Brand name
        val brandPaint = textPaint(
            size = 40f,
            color = brandIndigo,
            bold = true,
            letterSpacing = 0.25f
        )
        canvas.drawText("FOCUSSHIELD", PADDING, 120f, brandPaint)

        // Date range under the brand
        val rangePaint = textPaint(size = 26f, color = textMuted)
        canvas.drawText(summary.dateRangeLabel, PADDING, 165f, rangePaint)

        // Period chip on the right
        val label = summary.period.label.uppercase(Locale.getDefault())
        val chipTextPaint = textPaint(size = 26f, color = Color.parseColor("#A5B4FC"), bold = true)
        val textWidth = chipTextPaint.measureText(label)
        val chipRect = RectF(
            WIDTH - PADDING - textWidth - 48f,
            80f,
            WIDTH - PADDING,
            128f
        )
        canvas.drawRoundRect(chipRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chipBg })
        canvas.drawText(
            label,
            chipRect.left + 24f,
            chipRect.centerY() - (chipTextPaint.ascent() + chipTextPaint.descent()) / 2f,
            chipTextPaint
        )
        return 180f
    }

    private fun drawHeroMetric(canvas: Canvas, summary: PeriodAnalyticsSummary, startY: Float): Float {
        val valuePaint = textPaint(size = 120f, color = textWhite, bold = true)
        canvas.drawText(summary.formattedTotalStudyTime, PADDING, startY + 100f, valuePaint)

        val captionPaint = textPaint(size = 28f, color = textMuted, letterSpacing = 0.2f)
        canvas.drawText("TOTAL FOCUS TIME", PADDING, startY + 150f, captionPaint)

        return startY + 150f
    }

    private fun drawStatsGrid(canvas: Canvas, summary: PeriodAnalyticsSummary, startY: Float): Float {
        val gap = 24f
        val cellWidth = (CONTENT_WIDTH - gap) / 2f
        val cellHeight = 170f
        val radius = 28f

        val cells = listOf(
            summary.completedSessions.toString() to "COMPLETED SESSIONS",
            "${summary.streakInfo.currentStreak}" to "DAY STREAK",
            summary.blockedAttemptsCount.toString() to "DISTRACTIONS BLOCKED",
            "${summary.consistencyPercentage}%" to "CONSISTENCY"
        )

        cells.forEachIndexed { index, (value, label) ->
            val col = index % 2
            val row = index / 2
            val left = PADDING + col * (cellWidth + gap)
            val top = startY + row * (cellHeight + gap)
            val rect = RectF(left, top, left + cellWidth, top + cellHeight)

            canvas.drawRoundRect(
                rect, radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statCellBg }
            )

            val valuePaint = textPaint(size = 56f, color = textWhite, bold = true)
            canvas.drawText(value, left + 32f, top + 82f, valuePaint)

            val labelPaint = textPaint(size = 24f, color = textMuted)
            canvas.drawText(label, left + 32f, top + 126f, labelPaint)
        }
        return startY + 2 * cellHeight + gap
    }

    private fun drawDailyChart(
        canvas: Canvas,
        summary: PeriodAnalyticsSummary,
        dailyGoalMinutes: Int,
        startY: Float
    ): Float {
        val titlePaint = textPaint(size = 28f, color = textMuted, letterSpacing = 0.2f)
        canvas.drawText("DAILY FOCUS", PADDING, startY, titlePaint)

        val days = summary.dailyChart
        if (days.isEmpty()) return startY

        val chartTop = startY + 40f
        val chartBottom = chartTop + 300f
        val chartHeight = chartBottom - chartTop
        val maxMillis = max(
            days.maxOf { it.studyTimeMillis },
            dailyGoalMinutes * 60_000L
        ).coerceAtLeast(1L)

        val n = days.size
        val barGap = if (n > 20) 6f else 12f
        val barWidth = ((CONTENT_WIDTH - barGap * (n - 1)) / n).coerceAtLeast(6f)

        // Dashed goal line
        val goalRatio = (dailyGoalMinutes * 60_000f / maxMillis).coerceIn(0f, 1f)
        if (dailyGoalMinutes > 0 && goalRatio > 0f) {
            val goalY = chartBottom - chartHeight * goalRatio
            val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = amber
                strokeWidth = 4f
                pathEffect = DashPathEffect(floatArrayOf(20f, 16f), 0f)
                style = Paint.Style.STROKE
            }
            canvas.drawLine(PADDING, goalY, WIDTH - PADDING, goalY, goalPaint)
        }

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelPaint = textPaint(size = 22f, color = textMuted)
        val labelStride = if (n > 10) 2 else 1

        days.forEachIndexed { index, day ->
            val barLeft = PADDING + index * (barWidth + barGap)
            val barHeight = (day.studyTimeMillis.toFloat() / maxMillis) * chartHeight
            val barTop = chartBottom - barHeight

            barPaint.color = when {
                day.isToday -> emerald
                day.isGoalMet -> brandIndigo
                else -> Color.parseColor("#5B5F97")
            }
            val radius = (barWidth / 2f).coerceAtMost(10f)
            canvas.drawRoundRect(
                RectF(barLeft, barTop, barLeft + barWidth, chartBottom),
                radius, radius,
                barPaint
            )

            if (index % labelStride == 0) {
                val dayLabel = day.dayLabel.take(3)
                val cx = barLeft + barWidth / 2f - labelPaint.measureText(dayLabel) / 2f
                canvas.drawText(dayLabel, cx, chartBottom + 40f, labelPaint)
            }
        }
        return chartBottom + 60f
    }

    private fun drawBestDayAndFooter(canvas: Canvas, summary: PeriodAnalyticsSummary, startY: Float) {
        // Best day callout
        if (!summary.bestStudyDayLabel.isNullOrBlank() && summary.bestStudyDayTimeMillis > 0) {
            val bestPaint = textPaint(size = 30f, color = textSoft)
            val text = "Best day: ${summary.bestStudyDayLabel} · ${formatDuration(summary.bestStudyDayTimeMillis)}"
            canvas.drawText(text, PADDING, startY, bestPaint)
        }

        // Footer
        val footerPaint = textPaint(size = 26f, color = textMuted)
        val dateText = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
        val footer = "Tracked with FocusShield · $dateText"
        val footerWidth = footerPaint.measureText(footer)
        canvas.drawText(footer, (WIDTH - footerWidth) / 2f, HEIGHT - 60f, footerPaint)
    }

    private fun textPaint(
        size: Float,
        color: Int,
        bold: Boolean = false,
        letterSpacing: Float = 0f
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = size
        this.letterSpacing = letterSpacing
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
