package com.example.cloud.storage

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.cloud.SupabaseConfig
import com.example.cloud.auth.AuthRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Uploads / deletes / downloads this account's profile image against the private `profile-images`
 * Storage bucket.
 *
 * [SupabaseHttp] is JSON-only, so this component builds its own OkHttp calls for raw image bytes.
 * The object path is deterministic (`profile-images/{user_id}/avatar.jpg`), which means replacing a
 * photo upserts the same object (no stale avatars accumulate) and deleting means deleting exactly one
 * object. Supabase Storage enforces object ownership server-side (RLS), and every request is sent
 * with the authenticated Bearer session from [AuthRepository.requireFreshSession].
 *
 * All public methods are fail-safe: they return `false` / `null` (never throw) when the cloud layer is
 * not configured, no session exists, the network fails, or the image cannot be decoded. Callers fall
 * back to the local image / preset exactly as before. No tokens are ever logged.
 */
class ProfileImageUploader(
    private val context: Context,
    private val authRepository: AuthRepository
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BUCKET = "profile-images"

        /** Deterministic within-bucket object path for a user's avatar. */
        fun objectPath(userId: String): String = "$BUCKET/$userId/avatar.jpg"

        private fun objectUrl(userId: String): String =
            "${SupabaseConfig.baseUrl}/storage/v1/object/${objectPath(userId)}"

        /** Folder derived by the storage ownership policy is the first path segment (the user id). */
        fun ownerFolder(userId: String): String = userId
    }

    private fun avatarCacheFile(userId: String): File =
        File(File(context.filesDir, "cloud_avatar"), "${userId}_avatar.jpg")

    /**
     * Upload (or replace) the image at a picked-content URI as the signed-in user's avatar.
     * Returns true on success. The image is decoded and re-encoded as a capped JPEG so a huge
     * gallery photo never uploads megabytes.
     */
    suspend fun uploadOwnImage(localPhotoUri: String): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext false
        val session = authRepository.requireFreshSession() ?: return@withContext false
        val bytes = encodeAsJpeg(context.contentResolver, Uri.parse(localPhotoUri))
            ?: return@withContext false
        request(method = "POST", userId = session.userId, token = session.accessToken, bytes = bytes)
    }

    /** Delete the signed-in user's cloud avatar (and its local cache copy). Fail-safe. */
    suspend fun deleteOwnImage(): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext false
        val session = authRepository.requireFreshSession() ?: return@withContext false
        val ok = request(method = "DELETE", userId = session.userId, token = session.accessToken, bytes = null)
        runCatching { avatarCacheFile(session.userId).delete() }
        ok
    }

    /**
     * Download the signed-in user's own avatar into a private cache file and return its path, or null
     * when there is none / it cannot be fetched. Used by a restored device that has no local photo.
     */
    suspend fun downloadOwnAvatarToCache(userId: String): String? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext null
        val session = authRepository.requireFreshSession()
        if (session == null || session.userId != userId) return@withContext null
        val bytes = requestBytes(userId = userId, token = session.accessToken) ?: return@withContext null
        val file = avatarCacheFile(userId)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }.getOrNull() ?: return@withContext null
        file.absolutePath
    }

    // ---- low-level HTTP -----------------------------------------------------------------

    private fun request(method: String, userId: String, token: String, bytes: ByteArray?): Boolean {
        val code = runCatching {
            newCall(method, userId, token, bytes).execute().use { it.code }
        }.getOrNull() ?: return false
        // Deleting an object that is already gone (404) is still a successful delete.
        if (method == "DELETE") return code == 200 || code == 204 || code == 404
        return code in 200..299
    }

    private fun requestBytes(userId: String, token: String): ByteArray? = runCatching {
        newCall("GET", userId, token, null).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes() else null
        }
    }.getOrNull()

    private fun newCall(method: String, userId: String, token: String, bytes: ByteArray?) =
        Request.Builder()
            .url(objectUrl(userId))
            .method(method, bytes?.toRequestBody("image/jpeg".toMediaType()))
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .apply {
                if (method == "POST") header("x-upsert", "true")
                if (method != "GET") header("Content-Type", "image/jpeg")
            }
            .build()
            .let { client.newCall(it) }

    // ---- image decoding ------------------------------------------------------------------

    private fun encodeAsJpeg(resolver: ContentResolver, uri: Uri): ByteArray? = runCatching {
        // First pass: decode bounds only so we can subsample without loading a huge bitmap.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1600 && bounds.outHeight / (sample * 2) >= 1600) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val scaled = scaleDown(decoded, 1600)
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        out.toByteArray()
    }.getOrNull()

    private fun scaleDown(src: Bitmap, max: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= max && h <= max) return src
        val scale = max.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }
}
