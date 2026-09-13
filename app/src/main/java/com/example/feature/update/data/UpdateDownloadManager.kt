package com.example.feature.update.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.core.content.FileProvider
import com.example.feature.update.domain.UpdateInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Raised for every download/validation problem. The message is already user-presentable. */
class UpdateDownloadException(message: String) : Exception(message)

/**
 * Fetches the release APK and hands it to Android's installer.
 *
 * Three deliberate properties:
 *  - **The user always confirms.** The APK is downloaded, validated and then passed to the system
 *    package installer, which shows its own consent screen. Nothing here installs silently, and
 *    nothing touches Play Protect (prompt.txt §9/§21).
 *  - **Validated before it is offered.** A response that is not an APK for *this* application is
 *    rejected rather than handed to the installer (prompt.txt §20). No checksum is asserted — the
 *    GitHub API publishes none, and inventing one would be worse than omitting it (prompt.txt §20
 *    "Do NOT invent a checksum").
 *  - **A content URI, never `file://`** (prompt.txt §9). The APK lives in the app's cache and is
 *    shared through the existing `FileProvider`.
 */
class UpdateDownloadManager(
    private val context: Context,
    private val client: OkHttpClient = defaultClient()
) {

    /**
     * Downloads [info]'s APK into app-private cache, reporting integer progress.
     *
     * @param onProgress called with 0..100 on the calling dispatcher's thread; never 100 until the
     *   bytes are on disk and validated.
     * @return the validated APK file.
     * @throws UpdateDownloadException on any failure, including a cancelled coroutine.
     */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = info.apkUrl
            ?: throw UpdateDownloadException(
                "Update is available, but the APK is not available for download yet."
            )
        if (!url.endsWith(".apk", ignoreCase = true)) {
            throw UpdateDownloadException("The update link does not point to an APK file.")
        }

        val dir = updatesDir()
        cleanUpObsolete(dir, keepName = null)
        ensureSpaceFor(dir, MIN_FREE_BYTES)

        val target = File(dir, safeFileName(info))
        val partial = File(dir, "${target.name}.part")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.android.package-archive")
            // GitHub release assets are served from a CDN that is happy without this, but sending it
            // keeps the call consistent with the API client and avoids opaque CDN rejections.
            .header("User-Agent", "FocusShield-Android")
            .get()
            .build()

        try {
            onProgress(0)
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw UpdateDownloadException("Download failed. Check your connection and try again.")
            }

            response.use { res ->
                if (!res.isSuccessful) {
                    throw UpdateDownloadException("Download failed (server said ${res.code}).")
                }

                val body = res.body ?: throw UpdateDownloadException("Download failed: the server sent no data.")
                val declaredLength = body.contentLength()
                if (declaredLength == 0L) {
                    throw UpdateDownloadException("Download failed: the file is empty.")
                }
                // Reject an HTML error page or a JSON blob before writing anything to disk.
                val contentType = res.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("text/html") || contentType.contains("application/json")) {
                    throw UpdateDownloadException("Download failed: the server did not return an app file.")
                }
                // If the server declares a size, make sure it fits before streaming (prompt.txt §9).
                if (declaredLength > 0) ensureSpaceFor(dir, declaredLength + MIN_FREE_BYTES)

                var total = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            total += read
                            if (declaredLength > 0) {
                                val percent = ((total * 100) / declaredLength).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (total == 0L) {
                    throw UpdateDownloadException("Download failed: the file is empty.")
                }
            }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                throw UpdateDownloadException("Couldn't save the downloaded update.")
            }
        } catch (e: Throwable) {
            partial.delete()
            throw e
        }

        // Validate before the file is ever offered to the installer.
        validateApk(target)

        onProgress(100)
        cleanUpObsolete(dir, keepName = target.name)
        target
    }

    /**
     * Confirms the downloaded file really is an installable APK of *this* application.
     *
     * The ZIP signature check is first because it is cheap and catches a truncated transfer; the
     * package-name check is what makes the answer meaningful, since a well-formed APK for some other
     * app is exactly the failure this guards against (prompt.txt §20).
     */
    fun validateApk(file: File) {
        if (!file.exists() || file.length() <= 0L) {
            throw UpdateDownloadException("The downloaded update file is empty.")
        }

        val header = ByteArray(4)
        val read = try {
            file.inputStream().use { it.read(header) }
        } catch (e: IOException) {
            throw UpdateDownloadException("Couldn't read the downloaded update file.")
        }
        // Every APK is a ZIP archive, and every ZIP starts with "PK".
        if (read < 4 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
            throw UpdateDownloadException("The downloaded file is not a valid APK.")
        }

        val archiveInfo = packageArchiveInfo(file)
            ?: throw UpdateDownloadException("The downloaded file is not a valid APK.")

        val archivePackage = archiveInfo.packageName
        if (archivePackage != context.packageName) {
            throw UpdateDownloadException(
                "The downloaded file is not a FocusShield update and was not installed."
            )
        }
    }

    /** True when Android will let us hand an APK to the installer for this app. */
    fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /**
     * Opens the system installer for [file]. The user still has to confirm — this only launches the
     * screen (prompt.txt §9).
     */
    fun createInstallIntent(file: File): Intent {
        // A content:// URI through the app's own FileProvider. A raw file:// URI would trip
        // FileUriExposedException on API 24+ and is forbidden by the spec regardless.
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Deletes any previously downloaded APKs, keeping at most [keepName]. */
    fun cleanUpObsolete(dir: File = updatesDir(), keepName: String?) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name == keepName) continue
            f.delete()
        }
    }

    /** The directory holding downloaded APKs; matches the `updates/` entry in `file_paths.xml`. */
    fun updatesDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun safeFileName(info: UpdateInfo): String {
        val fromAsset = info.apkAssetName
            ?.substringAfterLast('/')
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
        if (fromAsset != null) return fromAsset.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "FocusShield-v${info.versionName}.apk"
    }

    private fun ensureSpaceFor(dir: File, requiredBytes: Long) {
        val stat = StatFs(dir.absolutePath)
        val available = stat.availableBytes
        if (available < requiredBytes) {
            throw UpdateDownloadException("Not enough storage to download the update. Free up some space and try again.")
        }
    }

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(file: File): android.content.pm.PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Free space kept in reserve beyond the download itself. */
        private const val MIN_FREE_BYTES = 20L * 1024 * 1024

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Generous read timeout: an APK over a slow connection must not be cut off mid-transfer.
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
