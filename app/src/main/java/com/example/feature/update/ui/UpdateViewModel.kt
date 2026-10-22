package com.example.feature.update.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.example.feature.update.data.ExternalFirebaseUpdateConfig
import com.example.feature.update.domain.UpdateInfo
import com.example.feature.update.domain.UpdateState
import com.example.feature.update.engine.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing projection of [UpdateManager].
 *
 * Intentionally stateless: every value comes from the application-scoped manager, so the update
 * screen, the Profile red dot, and a background download can never disagree, and nothing is lost
 * when the screen is recreated (prompt.txt §13). See [UpdateManager] for the actual rules.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager,
    @javax.inject.Named("installedVersionName") val installedVersionName: String
) : ViewModel() {

    val state: StateFlow<UpdateState> = manager.state

    /** Drives the small red dot on Profile → New Updates and Home header icons. */
    val showDot: StateFlow<Boolean> = manager.showDot

    /** Non-null after the app restarts onto a newly installed update until dismissed. */
    val completedUpdate: StateFlow<UpdateInfo?> = manager.completedUpdate

    /** Active external Firebase update server configuration (from in-app token or .env Secrets). */
    val externalFirebaseConfig: StateFlow<ExternalFirebaseUpdateConfig?> =
        manager.externalFirebaseConfig

    /** All published releases fetched from the external Firebase project (newest first). */
    val releaseHistory: StateFlow<List<UpdateInfo>> = manager.releaseHistory

    init {
        // Show a cached offer (or a completed download) immediately, without re-hitting the network.
        manager.refreshFromCache()
    }

    /** Manual "Check for updates" — ignores the cooldown and may report a failure. */
    fun checkForUpdates() = manager.checkManually()

    /** Saves a pasted external Firebase connection token (`fsupd_...`) or JSON config and checks immediately. */
    fun applyExternalFirebaseToken(
        rawInput: String,
        onResult: (Result<ExternalFirebaseUpdateConfig>) -> Unit = {}
    ) = manager.applyExternalFirebaseToken(rawInput, onResult)

    /** Clears an in-app pasted Firebase connection token. */
    fun clearExternalFirebaseToken() = manager.clearExternalFirebaseToken()

    /** "Later" on the popup. The update stays available and the red dot stays lit. */
    fun onLater() = manager.dismissPrompt()

    /** Hides the modal during an active download or post-download prompt while keeping the Home banner active. */
    fun hideDownloadModal() = manager.hideDownloadModal()

    /** Re-opens the modal dialog from the Home banner. */
    fun showModalPrompt() = manager.showModalPrompt()

    /** Dismisses the post-restart "What's New" dialog. */
    fun dismissCompletedUpdate() = manager.dismissCompletedUpdate()

    /** Explicit mark-as-read; clears the red dot and the notification. */
    fun markAsRead() = manager.markRead()

    fun download() = manager.download()

    fun cancelDownload() = manager.cancelDownload()

    fun clearError() = manager.clearError()

    /** True when Android still needs "install unknown apps" granted for FocusShield. */
    fun canInstallPackages(): Boolean = manager.canInstallPackages()

    /** The installer intent, or null if there is nothing downloaded. */
    fun createInstallIntent(): Intent? = manager.createInstallIntent()

    /** Transitions Downloaded → Installing immediately before handing control to Android. */
    fun beginInstall() = manager.beginInstall()

    /** Restores Downloaded when the user declines the unknown-sources permission. */
    fun cancelInstall() = manager.cancelInstall()
}
