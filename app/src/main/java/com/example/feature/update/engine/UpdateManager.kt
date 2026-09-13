package com.example.feature.update.engine

import android.content.Context
import com.example.feature.update.data.UpdateChecker
import com.example.feature.update.data.UpdateCheckResult
import com.example.feature.update.data.UpdateDownloadException
import com.example.feature.update.data.UpdateDownloadManager
import com.example.feature.update.data.UpdatePreferences
import com.example.feature.update.data.UpdateSourceUnavailableException
import com.example.feature.update.domain.DownloadProgress
import com.example.feature.update.domain.UpdateInfo
import com.example.feature.update.domain.UpdateState
import com.example.feature.update.notification.UpdateNotificationHelper
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * The single owner of "is there an update, and what is happening with it" for the whole app.
 *
 * Exists as an application-scoped coordinator rather than living inside the ViewModel because three
 * lifetimes have to agree:
 *  - the **automatic check** fires from the application scope on launch/resume/connectivity;
 *  - the **Profile red dot** must be correct on a screen that never opens the update screen;
 *  - the **download** outlives the screen that started it (prompt.txt §13).
 *
 * The ViewModel is a thin projection of this; it holds no state of its own. Rules that are easy to get
 * wrong and are therefore enforced here and nowhere else:
 *  - "Later" suppresses the *dialog* for that version and nothing else — the red dot and the
 *    notification stay (prompt.txt §6/§7/§18);
 *  - the red dot clears only on a successful install or an explicit mark-as-read, never merely
 *    because the update screen was opened (prompt.txt §18);
 *  - a failed automatic check is silent; only a manual check may report an error (prompt.txt §16).
 */
class UpdateManager(
    private val context: Context,
    private val checker: UpdateChecker,
    private val preferences: UpdatePreferences,
    private val downloadManager: UpdateDownloadManager,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the small red dot should be showing on Profile → New Updates. */
    private val _showDot = MutableStateFlow(false)
    val showDot: StateFlow<Boolean> = _showDot.asStateFlow()

    /** Serialises checks so a resume-triggered check cannot race a manual one. */
    private val checkMutex = Mutex()

    /** The update currently on offer, if any. Kept separately from [state] so it survives a failure. */
    private var current: UpdateInfo? = null

    private var downloadJob: Job? = null

    /** Restores any persisted update state. Safe to call once, from `Application.onCreate`. */
    fun restore() {
        scope.launch {
            val prefs = preferences.current()
            val restored = prefs.latestInfoJson?.let(::decodeInfo)
            if (restored == null) return@launch

            // A downloaded APK that is still on disk means the user was one tap from installing.
            val path = prefs.downloadedPath
            if (prefs.downloadedVersion == restored.versionName && path != null) {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    current = restored
                    _state.value = UpdateState.Downloaded(restored, file)
                    refreshDot()
                    return@launch
                }
                preferences.clearDownload()
            }
            refreshDot()
        }
    }

    /**
     * Automatic check — app launch, return to foreground, or connectivity regained.
     *
     * Never reports failure and never raises the popup on its own beyond the one allowed per version
     * (prompt.txt §4/§16).
     */
    fun checkInBackground() {
        scope.launch {
            val result = checkMutex.withLock { checker.check(manual = false) }
            applyResult(result, manual = false)
        }
    }

    /** User-initiated check from Profile → New Updates. Bypasses the cooldown and may report failure. */
    fun checkManually() {
        scope.launch {
            _state.value = UpdateState.Checking
            val result = checkMutex.withLock { checker.check(manual = true) }
            applyResult(result, manual = true)
        }
    }

    private suspend fun applyResult(result: UpdateCheckResult, manual: Boolean) {
        when (result) {
            is UpdateCheckResult.Available -> {
                current = result.info
                val prefs = preferences.current()

                if (checker.shouldNotify(prefs, result.info)) {
                    UpdateNotificationHelper.notifyUpdateAvailable(context, result.info.versionName)
                    preferences.markNotified(result.info.versionName)
                }
                persistInfo(result.info)

                _state.value = UpdateState.UpdateAvailable(
                    info = result.info,
                    // The blocking popup is raised once per version. "Later" sets dismissedVersion,
                    // which is what stops it coming back on every navigation (prompt.txt §6).
                    showPrompt = checker.shouldPrompt(prefs, result.info)
                )
                refreshDot()
            }

            UpdateCheckResult.UpToDate -> {
                current = null
                persistInfo(null)
                UpdateNotificationHelper.clear(context)
                _state.value = UpdateState.UpToDate
                refreshDot()
            }

            is UpdateCheckResult.Failed -> {
                // An automatic failure leaves whatever we already knew on screen, and says nothing.
                _state.value = if (manual) {
                    UpdateState.Failed(result.message, isCheckFailure = true)
                } else {
                    // Fall back to the cached offer rather than blanking the UI out.
                    current?.let { UpdateState.UpdateAvailable(it, showPrompt = false) }
                        ?: UpdateState.Idle
                }
                refreshDot()
            }

            UpdateCheckResult.Skipped -> {
                // Cooldown or offline. Leave the current state exactly as it was.
                refreshDot()
            }
        }
    }

    /** "Later" on the popup: hides the dialog for this version, keeps everything else. */
    fun dismissPrompt() {
        val info = current ?: return
        scope.launch {
            preferences.markDismissed(info.versionName)
            _state.value = UpdateState.UpdateAvailable(info = info, showPrompt = false)
            refreshDot()
        }
    }

    /** Explicit "mark as read" — one of only two ways the red dot goes away (prompt.txt §7). */
    fun markRead() {
        val info = current ?: return
        scope.launch {
            preferences.markRead(info.versionName)
            UpdateNotificationHelper.clear(context)
            refreshDot()
        }
    }

    /** Starts (or restarts) the download. Progress is published through [state]. */
    fun download() {
        val info = current ?: return
        if (downloadJob?.isActive == true) return

        downloadJob = scope.launch {
            _state.value = UpdateState.Downloading(DownloadProgress(percent = 0))
            try {
                val file = downloadManager.download(info) { progress ->
                    _state.value = UpdateState.Downloading(progress)
                }
                preferences.recordDownload(info.versionName, file.absolutePath)
                _state.value = UpdateState.Downloaded(info, file)
            } catch (e: CancellationException) {
                // cancelDownload() already moved the state back to the offer — surface it unchanged.
                throw e
            } catch (e: UpdateDownloadException) {
                _state.value = UpdateState.Failed(e.message ?: "Download failed.", isCheckFailure = false)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Download failed. Try again.", isCheckFailure = false)
            }
            refreshDot()
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val info = current
        _state.value = if (info != null) {
            UpdateState.UpdateAvailable(info, showPrompt = false)
        } else {
            UpdateState.Idle
        }
    }

    /**
     * Hands the downloaded APK to the system installer.
     *
     * Returns the intent to launch, or null when there is nothing to install. This deliberately stops
     * at the installer screen: the user confirms there (prompt.txt §9).
     */
    fun createInstallIntent(): android.content.Intent? {
        val downloaded = _state.value as? UpdateState.Downloaded ?: return null
        return try {
            downloadManager.createInstallIntent(downloaded.file)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Couldn't open the installer. Try downloading again.")
            null
        }
    }

    /** True when the OS still needs the user to grant "install unknown apps" for FocusShield. */
    fun canInstallPackages(): Boolean = downloadManager.canInstallPackages()

    /** Clears a download error so the screen returns to the offer. */
    fun clearError() {
        val info = current
        _state.value = if (info != null) {
            UpdateState.UpdateAvailable(info, showPrompt = false)
        } else {
            UpdateState.Idle
        }
    }

    /** Called by the ViewModel when the screen opens, so a cached offer is shown without a re-check. */
    fun refreshFromCache() {
        scope.launch {
            if (_state.value is UpdateState.Downloaded || _state.value is UpdateState.Downloading) return@launch
            val info = current ?: preferences.current().latestInfoJson?.let(::decodeInfo)
            current = info
            _state.value = when {
                info != null -> UpdateState.UpdateAvailable(info, showPrompt = false)
                else -> _state.value
            }
            refreshDot()
        }
    }

    private suspend fun refreshDot() {
        val info = current
        _showDot.value = info != null && preferences.current().readVersion != info.versionName
    }

    private suspend fun persistInfo(info: UpdateInfo?) {
        preferences.setLatestInfoJson(info?.let(::encodeInfo))
    }

    private fun encodeInfo(info: UpdateInfo): String = JSONObject().apply {
        put("versionName", info.versionName)
        info.versionCode?.let { put("versionCode", it) }
        put("tagName", info.tagName)
        put("title", info.title)
        put("releaseNotes", info.releaseNotes)
        put("apkUrl", info.apkUrl)
        put("apkAssetName", info.apkAssetName)
        put("releasePageUrl", info.releasePageUrl)
        put("publishedAt", info.publishedAt)
    }.toString()

    private fun decodeInfo(raw: String): UpdateInfo? = try {
        val json = JSONObject(raw)
        val versionName = json.optString("versionName").takeIf { it.isNotBlank() }
        if (versionName == null) {
            null
        } else {
            UpdateInfo(
                versionName = versionName,
                versionCode = json.optInt("versionCode", 0).takeIf { json.has("versionCode") && it != 0 },
                tagName = json.optString("tagName"),
                title = json.optString("title"),
                releaseNotes = json.optString("releaseNotes"),
                apkUrl = json.optString("apkUrl").takeIf { it.isNotBlank() },
                apkAssetName = json.optString("apkAssetName").takeIf { it.isNotBlank() },
                releasePageUrl = json.optString("releasePageUrl"),
                publishedAt = json.optString("publishedAt").takeIf { it.isNotBlank() }
            )
        }
    } catch (_: Exception) {
        null
    }
}
