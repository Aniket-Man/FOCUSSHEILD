package com.example.feature.update.domain

import java.io.File

/**
 * Everything the update UI can be showing, as one value.
 *
 * Held by [com.example.feature.update.ui.UpdateViewModel] and backed by
 * [com.example.feature.update.data.UpdatePreferences], so the important parts survive process death
 * and screen recreation rather than living only in Compose state (prompt.txt §13).
 */
sealed interface UpdateState {

    /** Nothing checked yet this session; the screen shows the installed version and offers a check. */
    data object Idle : UpdateState

    /** A check is in flight. */
    data object Checking : UpdateState

    /** Checked, and the installed build is current. */
    data object UpToDate : UpdateState

    /**
     * A newer release exists.
     *
     * @param showPrompt whether to raise the blocking dialog. The update stays available (and the
     *   Profile red dot stays lit) regardless — pressing "Later" only clears this flag
     *   (prompt.txt §6/§18).
     */
    data class UpdateAvailable(
        val info: UpdateInfo,
        val showPrompt: Boolean = true
    ) : UpdateState

    data class Downloading(val percent: Int) : UpdateState

    data class Downloaded(val info: UpdateInfo, val file: File) : UpdateState

    data object Installing : UpdateState

    /** @param isCheckFailure true when the *check* failed (offline, private repo) rather than a download. */
    data class Failed(val message: String, val isCheckFailure: Boolean = false) : UpdateState
}
