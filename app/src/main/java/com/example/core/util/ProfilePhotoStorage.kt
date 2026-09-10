package com.example.core.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Owns the on-disk storage for the user's cropped profile photo.
 *
 * Mirrors the conventions in [ChannelLogoStorageManager]: files live under `filesDir/<dir>`,
 * URIs are handed back as `file://` strings, and writes go to a temp file first so a failed
 * write can never leave a half-written image behind.
 *
 * Two files are kept:
 *  - `staged_crop.jpg` — the result of the crop screen, before the user taps Save.
 *  - `avatar.jpg`      — the committed avatar that is actually persisted and uploaded.
 *
 * Staging exists because the crop result reaches the sheet *before* the user saves: writing
 * straight to `avatar.jpg` would overwrite the saved avatar even when the sheet is cancelled.
 * A single fixed staging name also means crop-then-cancel can never orphan files.
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
            }

            if (tempFile.renameTo(targetFile) || (targetFile.delete() && tempFile.renameTo(targetFile))) {
                toFileUri(targetFile)
            } else {
                Log.w(TAG, "Failed to stage cropped photo")
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing staged crop: ${e.message}")
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
     * Promotes the staged crop to the committed avatar, discarding the previous one.
     * Returns the `file://` URI of the committed avatar, or null when there is nothing staged.
     */
    fun commitStagedCrop(context: Context): String? {
        return try {
            val staged = File(getPhotoDirectory(context), STAGED_FILE_NAME)
            if (!staged.exists()) return null

            val avatar = File(getPhotoDirectory(context), AVATAR_FILE_NAME)
            if (avatar.exists()) avatar.delete()

            if (staged.renameTo(avatar)) {
                toFileUri(avatar)
            } else {
                Log.w(TAG, "Failed to commit staged crop")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error committing staged crop: ${e.message}")
            null
        }
    }

    /**
     * Removes both the staged and the committed photo. Called only when the user saves with the
     * photo removed, so cancelling the sheet never destroys the saved avatar.
     */
    fun deleteStoredPhotos(context: Context) {
        try {
            val dir = getPhotoDirectory(context)
            File(dir, STAGED_FILE_NAME).delete()
            File(dir, AVATAR_FILE_NAME).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting stored photos: ${e.message}")
        }
    }
}
