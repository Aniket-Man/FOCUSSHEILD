package com.example.feature.update.engine

import android.content.Context
import com.example.feature.update.data.UpdateChecker
import com.example.feature.update.data.UpdateCheckResult
import com.example.feature.update.data.UpdateDownloadException
import com.example.feature.update.data.UpdateDownloadManager
import com.example.feature.update.data.UpdatePreferences
import com.example.feature.update.domain.DownloadProgress
import com.example.feature.update.domain.SemanticVersion
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
    private val scope: CoroutineScope,
    private val installedVersionName: String
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the small red dot should be showing on Profile → New Updates. */
    private val _showDot = MutableStateFlow(false)
    val showDot: StateFlow<Boolean> = _showDot.asStateFlow()

    /** Serialises checks so a resume-triggered check cannot race a manual one. */
    private val checkMutex = Mutex()

    /** Keeps restore/cache writes from interleaving with a completed network result. */
    private val stateMutex = Mutex()

    /** The update currently on offer, if any. Kept separately from [state] so it survives a failure. */
    private var current: UpdateInfo? = null

    private var downloadJob: Job? = null

    /** Downloaded state retained while Android's installer (or its permission screen) is in front. */
    private var installCandidate: UpdateState.Downloaded? = null

    /** Restores any persisted update state. Safe to call once, from `Application.onCreate`. */
    fun restore() {
        scope.launch {
            val needsFreshCheck = stateMutex.withLock {
                val prefs = preferences.current()
                val rawInfo = prefs.latestInfoJson
                val restored = rawInfo?.let(::decodeInfo)

                if (restored == null) {
                    current = null
                    _showDot.value = false
                    // Invalid JSON cannot be safely offered. Discard it atomically and bypass the
                    // cooldown so the full release metadata is fetched again.
                    if (rawInfo != null) {
                        discardStaleOffer()
                        true
                    } else {
                        false
                    }
                } else if (!isNewerThanInstalled(restored)) {
                    // The app may have been updated through our installer, a file manager, or ADB.
                    // Detect that before restoring any UI so neither the old offer nor its
                    // notification can survive the new process.
                    clearInstalledOffer()
                    false
                } else if (prefs.latestKnownVersion?.let {
                        SemanticVersion.isNewer(it, restored.versionName)
                    } == true
                ) {
                    // recordCheck() is written before the complete UpdateInfo. If the process dies
                    // between those writes, latestKnownVersion is the only evidence that this APK is
                    // obsolete. Remove it and force a complete check instead of offering a downgrade.
                    discardStaleOffer()
                    true
                } else {
                    current = restored

                    // A downloaded APK that is still on disk means the user was one tap from
                    // installing. It is restored only when it belongs to the current cached offer.
                    val path = prefs.downloadedPath
                    if (prefs.downloadedVersion == restored.versionName && path != null) {
                        val file = File(path)
                        if (file.exists() && file.length() > 0) {
                            _state.value = UpdateState.Downloaded(restored, file)
                        } else {
                            preferences.clearDownload()
                            _state.value = UpdateState.UpdateAvailable(restored, showPrompt = false)
                        }
                    } else {
                        if (prefs.downloadedVersion != null || path != null) {
                            preferences.clearDownload()
                            downloadManager.cleanUpObsolete(keepName = null)
                        }
                        _state.value = UpdateState.UpdateAvailable(restored, showPrompt = false)
                    }
                    refreshDot()
                    false
                }
            }

            // Usually Application.onCreate also requests a background check. Calling it here for an
            // inconsistent cache makes restore correct on its own; checkMutex and the cooldown make
            // the duplicate launch harmless.
            if (needsFreshCheck) runBackgroundCheck()
        }
    }

    /**
     * Automatic check — app launch, return to foreground, or connectivity regained.
     *
     * Never reports failure and never raises the popup on its own beyond the one allowed per version
     * (prompt.txt §4/§16).
     */
    fun checkInBackground() {
        scope.launch { runBackgroundCheck() }
    }

    /**
     * The same automatic check, run inline to completion.
     *
     * [checkInBackground] is fire-and-forget, which suits the launch/resume/connectivity triggers but
     * not the periodic worker: WorkManager has to know when the work actually finished, and a worker
     * that returns while its request is still in flight can have its process torn down mid-call.
     * Both paths funnel through here so the cooldown rule still lives in exactly one place.
     */
    suspend fun runBackgroundCheck() {
        checkMutex.withLock {
            val result = checker.check(manual = false)
            stateMutex.withLock { applyResult(result, manual = false) }
        }
    }

    /** User-initiated check from Profile → New Updates. Bypasses the cooldown and may report failure. */
    fun checkManually() {
        scope.launch {
            checkMutex.withLock {
                stateMutex.withLock { _state.value = UpdateState.Checking }
                val result = checker.check(manual = true)
                stateMutex.withLock { applyResult(result, manual = true) }
            }
        }
    }

    private suspend fun applyResult(result: UpdateCheckResult, manual: Boolean) {
        when (result) {
            is UpdateCheckResult.Available -> {
                // UpdateChecker already enforces this comparison. Keep the guard here as a final
                // invariant so a stale/custom checker result can never light the dot for the build
                // that is currently installed.
                if (!isNewerThanInstalled(result.info)) {
                    clearInstalledOffer()
                    return
                }

                current = result.info
                installCandidate = null
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

            UpdateCheckResult.UpToDate -> clearInstalledOffer()

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
        scope.launch {
            stateMutex.withLock {
                val info = current ?: return@withLock
                preferences.markDismissed(info.versionName)
                _state.value = UpdateState.UpdateAvailable(info = info, showPrompt = false)
                refreshDot()
            }
        }
    }

    /** Explicit "mark as read" — one of only two ways the red dot goes away (prompt.txt §7). */
    fun markRead() {
        scope.launch {
            stateMutex.withLock {
                val info = current ?: return@withLock
                preferences.markRead(info.versionName)
                UpdateNotificationHelper.clear(context)
                refreshDot()
            }
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

    /**
     * Moves the state machine into the installer hand-off and prevents duplicate install taps.
     * Android performs the installation in a separate activity; the result callback re-checks the
     * installed version to decide whether this becomes UpToDate or returns to an available update.
     */
    fun beginInstall() {
        val downloaded = _state.value as? UpdateState.Downloaded ?: return
        installCandidate = downloaded
        _state.value = UpdateState.Installing
    }

    /** Returns to the downloaded state when the unknown-sources permission screen is dismissed. */
    fun cancelInstall() {
        val candidate = installCandidate
        installCandidate = null
        if (_state.value is UpdateState.Installing && candidate != null) {
            _state.value = if (candidate.file.exists() && candidate.file.length() > 0) {
                candidate
            } else {
                current?.let { UpdateState.UpdateAvailable(it, showPrompt = false) } ?: UpdateState.Idle
            }
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
            val needsFreshCheck = stateMutex.withLock {
                if (
                    _state.value is UpdateState.Downloaded ||
                    _state.value is UpdateState.Downloading ||
                    _state.value is UpdateState.Installing
                ) {
                    return@withLock false
                }

                val prefs = preferences.current()
                val rawInfo = prefs.latestInfoJson
                val info = current ?: rawInfo?.let(::decodeInfo)

                when {
                    info == null && rawInfo != null -> {
                        current = null
                        discardStaleOffer()
                        true
                    }

                    info != null && !isNewerThanInstalled(info) -> {
                        // Do not trust cache ordering: this check protects the update screen even if
                        // it opens before the asynchronous Application.restore() coroutine finishes.
                        clearInstalledOffer()
                        false
                    }

                    info != null && prefs.latestKnownVersion?.let {
                        SemanticVersion.isNewer(it, info.versionName)
                    } == true -> {
                        current = null
                        discardStaleOffer()
                        true
                    }

                    info != null -> {
                        current = info
                        _state.value = UpdateState.UpdateAvailable(info, showPrompt = false)
                        refreshDot()
                        false
                    }

                    else -> {
                        current = null
                        _showDot.value = false
                        false
                    }
                }
            }

            if (needsFreshCheck) runBackgroundCheck()
        }
    }

    private suspend fun refreshDot() {
        val info = current
        // Defense in depth: stale in-memory/cache state must never advertise a version that the
        // running BuildConfig is already at or beyond.
        if (info == null || !isNewerThanInstalled(info)) {
            _showDot.value = false
            return
        }
        _showDot.value = preferences.current().readVersion != info.versionName
    }

    /** Clears persisted, in-memory, on-disk, and notification state for an installed offer. */
    private suspend fun clearInstalledOffer() {
        current = null
        installCandidate = null
        preferences.clearForInstalled(installedVersionName)
        downloadManager.cleanUpObsolete(keepName = null)
        UpdateNotificationHelper.clear(context)
        _state.value = UpdateState.UpToDate
        _showDot.value = false
    }

    /** Discards mismatched cache records and makes the next check bypass the cooldown. */
    private suspend fun discardStaleOffer() {
        current = null
        installCandidate = null
        downloadManager.cleanUpObsolete(keepName = null)
        preferences.discardStaleOfferForRefresh()
        _state.value = UpdateState.Idle
        _showDot.value = false
    }

    private fun isNewerThanInstalled(info: UpdateInfo): Boolean =
        SemanticVersion.isNewer(info.versionName, installedVersionName)

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
