package com.example.core.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Owns the on-disk storage for the user's cropped profile photo.
 *
 * Mirrors the conventions in [ChannelLogoStorageManager]: files live under `filesDir/<dir>`,
 * URIs are handed back as `file://` strings, and writes go to a temp file first so a failed
 * write can never leave a half-written image behind.
 *
 * Crops first go to one staging file. Preparing a save copies that crop to a unique avatar file;
 * the previous avatar is retired only after its replacement URI is persisted. Failed saves keep
 * both the saved photo and the editable crop intact.
 */
object ProfilePhotoStorage {

    private const val TAG = "ProfilePhotoStorage"
    private const val PHOTO_DIR_NAME = "profile_photo"
    private const val STAGED_FILE_NAME = "staged_crop.jpg"
    private const val AVATAR_FILE_NAME = "avatar.jpg"

    private fun getPhotoDirectory(context: Context): File {
        val dir = File(context.filesDir, PHOTO_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun toFileUri(file: File): String = "file://${file.absolutePath}"

    /**
     * Writes a freshly cropped bitmap to the staging file, replacing any previous staged crop.
     * Returns the `file://` URI, or null if the write failed.
     */
    suspend fun writeStagedCrop(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val dir = getPhotoDirectory(context)
            val targetFile = File(dir, STAGED_FILE_NAME)
            val tempFile = File(dir, "$STAGED_FILE_NAME.tmp")

            FileOutputStream(tempFile).use { fos ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)) { "Unable to encode cropped photo" }
                fos.flush()
            }

            if (tempFile.renameTo(targetFile) || (targetFile.delete() && tempFile.renameTo(targetFile))) {
                toFileUri(targetFile)
            } else {
                Log.w(TAG, "Failed to stage cropped photo")
                tempFile.delete()
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Error writing staged crop", e)
            null
        }
    }

    /**
     * True when [uri] points at the staging file rather than a committed avatar.
     */
    fun isStaged(context: Context, uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return uri == toFileUri(File(getPhotoDirectory(context), STAGED_FILE_NAME))
    }

    /**
     * Prepare a candidate without touching the saved avatar or the editable staging file.
     * Throw on failure; never persist a staging URI as a fallback.
     */
    suspend fun preparePhoto(context: Context, selectedUri: String?): String? = withContext(Dispatchers.IO) {
        if (!isStaged(context, selectedUri)) return@withContext selectedUri
        val directory = getPhotoDirectory(context)
        val staged = File(directory, STAGED_FILE_NAME)
        check(staged.isFile && staged.length() > 0L) { "Cropped photo is unavailable. Choose the photo again." }
        val candidate = File(directory, "avatar_${UUID.randomUUID()}.jpg")
        try {
            staged.copyTo(candidate, overwrite = false)
            toFileUri(candidate)
        } catch (exception: Exception) {
            candidate.delete()
            throw exception
        }
    }

    /**
     * Retire only the old file owned by this store. Content URIs and cloud caches belong elsewhere.
     * Cleanup failure wastes space, not saved data, and must not turn a successful save into a retry.
     */
    suspend fun finishPhoto(context: Context, previousUri: String?, savedUri: String?, retainedUris: Set<String> = emptySet()) {
        if (previousUri != savedUri && previousUri !in retainedUris) discardOwnedPhoto(context, previousUri)
        // The editor discards staging on explicit dismissal. If its activity is recreated just
        // after persistence, its restored form can still retry using the original crop.
    }

    suspend fun discardOwnedPhoto(context: Context, uri: String?) = withContext(Dispatchers.IO) {
        try {
            val parsed = uri?.let(Uri::parse) ?: return@withContext
            if (parsed.scheme != "file") return@withContext
            val file = parsed.path?.let(::File)?.canonicalFile ?: return@withContext
            val directory = getPhotoDirectory(context).canonicalFile
            val ownedName = file.name == AVATAR_FILE_NAME ||
                (file.name.startsWith("avatar_") && file.name.endsWith(".jpg"))
            if (file.parentFile == directory && ownedName && file.exists() && !file.delete()) {
                Log.w(TAG, "Unable to retire previous profile photo")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to clean up profile photo", e)
        }
    }

    suspend fun discardStagedCrop(context: Context) = withContext(Dispatchers.IO) {
        try {
            val staged = File(getPhotoDirectory(context), STAGED_FILE_NAME)
            if (staged.exists() && !staged.delete()) Log.w(TAG, "Unable to discard staged profile photo")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to discard staged profile photo", e)
        }
    }
}
