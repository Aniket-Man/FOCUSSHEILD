package com.example.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import com.example.data.local.dao.StudyChannelDao
import com.example.data.local.entity.StudyChannelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object ChannelLogoStorageManager {

    private const val TAG = "ChannelLogoStorage"
    private const val LOGO_DIR_NAME = "channel_logos"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Returns the dedicated directory for stored channel logos on internal storage.
     */
    fun getLogosDirectory(context: Context): File {
        val dir = File(context.filesDir, LOGO_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Generates a safe file name for a given channel identity.
     */
    private fun getSanitizedFileName(channelId: String): String {
        val safe = channelId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            .trim('_')
            .ifBlank { "channel_${abs(channelId.hashCode())}" }
        return "logo_$safe.png"
    }

    /**
     * Checks if a local logo file already exists and is non-empty for this channel.
     */
    fun getLocalLogoFile(context: Context, channelId: String): File? {
        val dir = getLogosDirectory(context)
        val file = File(dir, getSanitizedFileName(channelId))
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Resolves the best image model to display:
     * 1. Existing local file
     * 2. Existing thumbnailUrl (if file:// or http://)
     * 3. Unavatar / YouTube URL fallback
     */
    fun getEffectiveLogoUri(
        context: Context,
        channelId: String,
        thumbnailUrl: String = "",
        handle: String = ""
    ): String {
        // 1. Check if stored locally on disk
        val localFile = getLocalLogoFile(context, channelId)
        if (localFile != null) {
            return "file://${localFile.absolutePath}"
        }

        val normalizedThumb = when {
            thumbnailUrl.startsWith("//") -> "https:$thumbnailUrl"
            else -> thumbnailUrl
        }

        // 2. Check if thumbnailUrl is already a valid file URI
        if (normalizedThumb.isNotBlank() && normalizedThumb.startsWith("file://")) {
            val path = normalizedThumb.removePrefix("file://")
            if (File(path).exists()) return normalizedThumb
        }

        // 3. Check if thumbnailUrl is a remote URL
        if (normalizedThumb.isNotBlank() && (normalizedThumb.startsWith("http://") || normalizedThumb.startsWith("https://"))) {
            return normalizedThumb
        }

        // 4. Check handle or channel ID for fallback
        val targetHandle = when {
            handle.isNotBlank() -> handle
            channelId.startsWith("@") -> channelId
            else -> ""
        }
        if (targetHandle.isNotBlank()) {
            return "https://unavatar.io/youtube/$targetHandle"
        }

        return ""
    }

    /**
     * Downloads or generates the channel logo and persists it permanently to internal storage.
     * Returns the local file:// URI.
     */
    suspend fun saveAndCacheLogo(
        context: Context,
        channelId: String,
        channelName: String,
        remoteUrl: String = "",
        handle: String = ""
    ): String = withContext(Dispatchers.IO) {
        val targetFile = File(getLogosDirectory(context), getSanitizedFileName(channelId))

        // If file already exists with non-zero size, return it immediately
        if (targetFile.exists() && targetFile.length() > 200) {
            return@withContext "file://${targetFile.absolutePath}"
        }

        val candidateUrls = mutableListOf<String>()
        val normalizedRemote = when {
            remoteUrl.startsWith("//") -> "https:$remoteUrl"
            else -> remoteUrl
        }
        if (normalizedRemote.isNotBlank() && (normalizedRemote.startsWith("http://") || normalizedRemote.startsWith("https://"))) {
            candidateUrls.add(normalizedRemote)
        }

        val effectiveHandle = when {
            handle.isNotBlank() -> handle
            channelId.startsWith("@") -> channelId
            else -> ""
        }
        if (effectiveHandle.isNotBlank()) {
            candidateUrls.add("https://unavatar.io/youtube/$effectiveHandle")
        }

        var downloadSuccess = false

        // Attempt download from candidate URLs
        for (url in candidateUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/115.0 Firefox/115.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bytes = response.body!!.bytes()
                        if (bytes.size > 200) { // Valid image payload
                            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                            FileOutputStream(tempFile).use { fos ->
                                fos.write(bytes)
                                fos.flush()
                            }
                            if (tempFile.renameTo(targetFile) || (targetFile.delete() && tempFile.renameTo(targetFile))) {
                                downloadSuccess = true
                                return@use
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed downloading logo from $url: ${e.message}")
            }

            if (downloadSuccess) break
        }

        // If network download was not successful, generate a clean high-resolution branded monogram bitmap
        if (!downloadSuccess) {
            try {
                val generatedBitmap = generateMonogramBitmap(channelName)
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                FileOutputStream(tempFile).use { fos ->
                    generatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                }
                tempFile.renameTo(targetFile)
                downloadSuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Error generating fallback monogram bitmap: ${e.message}")
            }
        }

        return@withContext "file://${targetFile.absolutePath}"
    }

    /**
     * Asynchronously downloads/caches the logo and updates the Room database with the local URI.
     */
    suspend fun cacheAndPersistToDatabase(
        context: Context,
        channel: StudyChannelEntity,
        dao: StudyChannelDao
    ) = withContext(Dispatchers.IO) {
        try {
            val localUri = saveAndCacheLogo(
                context = context,
                channelId = channel.channelId,
                channelName = channel.channelName,
                remoteUrl = channel.thumbnailUrl,
                handle = if (channel.channelId.startsWith("@")) channel.channelId else ""
            )

            if (localUri.isNotBlank() && localUri != channel.thumbnailUrl) {
                dao.updateThumbnailUrl(channel.id, localUri)
                Log.d(TAG, "Cached logo for ${channel.channelName} -> $localUri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed caching logo for ${channel.channelName}: ${e.message}")
        }
    }

    /**
     * Seeds and caches logos for all approved channels that do not have a local URI yet.
     */
    suspend fun precacheAllChannels(
        context: Context,
        dao: StudyChannelDao
    ) = withContext(Dispatchers.IO) {
        try {
            val channels = dao.getApprovedChannels()
            for (ch in channels) {
                if (ch.thumbnailUrl.isBlank() || !ch.thumbnailUrl.startsWith("file://")) {
                    cacheAndPersistToDatabase(context, ch, dao)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error precaching channels: ${e.message}")
        }
    }

    /**
     * Generates a high-quality circular monogram bitmap with consistent vibrant gradient colors.
     */
    private fun generateMonogramBitmap(channelName: String): Bitmap {
        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Palette pairs
        val palettes = listOf(
            Pair(Color.parseColor("#4F46E5"), Color.parseColor("#7C3AED")), // Indigo to Purple
            Pair(Color.parseColor("#059669"), Color.parseColor("#10B981")), // Emerald
            Pair(Color.parseColor("#DC2626"), Color.parseColor("#EF4444")), // YouTube Red
            Pair(Color.parseColor("#D97706"), Color.parseColor("#F59E0B")), // Amber
            Pair(Color.parseColor("#2563EB"), Color.parseColor("#38BDF8")), // Sky Blue
            Pair(Color.parseColor("#7E22CE"), Color.parseColor("#EC4899")), // Violet to Pink
            Pair(Color.parseColor("#0D9488"), Color.parseColor("#14B8A6"))  // Teal
        )

        val hash = abs(channelName.trim().hashCode())
        val (startColor, endColor) = palettes[hash % palettes.size]

        // Background Circle with Gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                startColor, endColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        // Monogram Lettering
        val initials = channelName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { channelName.take(1).uppercase() }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (initials.length > 1) 58f else 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textBounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, textBounds)
        val yOffset = (size / 2f) - textBounds.exactCenterY()

        canvas.drawText(initials, size / 2f, yOffset, textPaint)

        return bitmap
    }
}
