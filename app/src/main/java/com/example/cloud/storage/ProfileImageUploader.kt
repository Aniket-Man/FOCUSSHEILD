package com.example.cloud.storage

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.R
import com.example.cloud.auth.AuthRepository
import com.example.cloud.firestore.OperationType
import com.example.cloud.firestore.handleFirestoreError
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Handles avatar image optimization and cloud persistence via Cloud Firestore.
 * Profile pictures are compressed and synchronized under /users/{userId} document,
 * enabling automatic download and local cache restoration on any new device login.
 */
class ProfileImageUploader(
    private val context: Context,
    private val authRepository: AuthRepository
) {
    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    }

    private fun avatarCacheFile(userId: String): File =
        File(File(context.filesDir, "cloud_avatar"), "${userId}_avatar.jpg")

    /**
     * Uploads the picked image as the signed-in user's profile avatar to Firestore.
     * The image is safely scaled to 256x256 and base64 encoded.
     */
    suspend fun uploadOwnImage(localPhotoUri: String, expectedUserId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val uid = authRepository.currentUserId() ?: return@withContext false
        if (expectedUserId != null && expectedUserId != uid) return@withContext false
        val bytes = encodeAsJpeg(context.contentResolver, Uri.parse(localPhotoUri))
            ?: return@withContext false
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        try {
            val docRef = db.collection("users").document(uid)
            val payload = hashMapOf<String, Any>(
                "userId" to uid,
                "photoBase64" to base64,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload, SetOptions.merge()).await()

            // Save locally to cache file
            try {
                val cacheFile = avatarCacheFile(uid)
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeBytes(bytes)
            } catch (exception: Exception) {
                android.util.Log.w("ProfileImageUploader", "Avatar uploaded but cache write failed", exception)
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            false
        }
    }

    /** Deletes the signed-in user's cloud avatar. */
    suspend fun deleteOwnImage(expectedUserId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val uid = authRepository.currentUserId() ?: return@withContext false
        if (expectedUserId != null && expectedUserId != uid) return@withContext false
        try {
            val docRef = db.collection("users").document(uid)
            val payload = hashMapOf<String, Any?>(
                "photoBase64" to null,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload, SetOptions.merge()).await()
            runCatching { avatarCacheFile(uid).delete() }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            false
        }
    }

    /**
     * Downloads the user's avatar from Firestore into a private cache file.
     * Returns the file path or null if none is saved.
     */
    suspend fun downloadOwnAvatarToCache(userId: String): String? = withContext(Dispatchers.IO) {
        try {
            val docRef = db.collection("users").document(userId)
            val snapshot = docRef.get().await()
            val base64 = snapshot.getString("photoBase64")
            if (base64.isNullOrBlank()) return@withContext null

            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val file = avatarCacheFile(userId)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            file.absolutePath
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId")
            null
        }
    }

    private fun encodeAsJpeg(resolver: ContentResolver, uri: Uri): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        // inJustDecodeBounds intentionally returns no bitmap; dimensions live in Options.
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val scaled = scaleDown(decoded, 256)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
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
