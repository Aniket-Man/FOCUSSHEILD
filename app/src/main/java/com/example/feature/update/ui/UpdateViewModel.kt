package com.example.feature.update.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.example.feature.update.domain.UpdateState
import com.example.feature.update.engine.UpdateManager
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing projection of [UpdateManager].
 *
 * Intentionally stateless: every value comes from the application-scoped manager, so the update
 * screen, the Profile red dot, and a background download can never disagree, and nothing is lost
 * when the screen is recreated (prompt.txt §13). See [UpdateManager] for the actual rules.
 */
class UpdateViewModel(
    private val manager: UpdateManager,
    val installedVersionName: String
) : ViewModel() {

    val state: StateFlow<UpdateState> = manager.state

    /** Drives the small red dot on Profile → New Updates. */
    val showDot: StateFlow<Boolean> = manager.showDot

    init {
        // Show a cached offer (or a completed download) immediately, without re-hitting the network.
        manager.refreshFromCache()
    }

    /** Manual "Check for updates" — ignores the cooldown and may report a failure. */
    fun checkForUpdates() = manager.checkManually()

    /** "Later" on the popup. The update stays available and the red dot stays lit. */
    fun onLater() = manager.dismissPrompt()

    /** Explicit mark-as-read; clears the red dot and the notification. */
    fun markAsRead() = manager.markRead()

    fun download() = manager.download()

    fun cancelDownload() = manager.cancelDownload()

    fun clearError() = manager.clearError()

    /** True when Android still needs "install unknown apps" granted for FocusShield. */
    fun canInstallPackages(): Boolean = manager.canInstallPackages()

    /** The installer intent, or null if there is nothing downloaded. */
    fun createInstallIntent(): Intent? = manager.createInstallIntent()
}
