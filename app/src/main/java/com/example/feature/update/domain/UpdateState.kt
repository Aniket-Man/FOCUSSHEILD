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

    data class Downloading(val progress: DownloadProgress) : UpdateState

    data class Downloaded(val info: UpdateInfo, val file: File) : UpdateState

    /** The validated APK has been handed to Android's permission/installer flow. */
    data object Installing : UpdateState

    /** @param isCheckFailure true when the *check* failed (offline, private repo) rather than a download. */
    data class Failed(val message: String, val isCheckFailure: Boolean = false) : UpdateState
}

/**
 * Byte-level progress for one in-flight download.
 *
 * [percent] is **null** when the server never declared a `Content-Length` — a chunked or
 * transparently-gzipped CDN response. In that case there is no honest percentage to show, so the UI
 * renders an indeterminate indicator plus [bytesReceived] instead of a number. Fabricating one, or
 * letting the bar sit at a motionless 0%, is exactly the bug this type exists to prevent.
 */
data class DownloadProgress(
    val percent: Int?,
    val bytesReceived: Long = 0L,
    val totalBytes: Long = -1L
) {
    /** True when a real percentage is available and the UI may show a determinate bar. */
    val isDeterminate: Boolean get() = percent != null

    /** 0f..1f for the determinate bar; 0f (unused) while indeterminate. */
    val fraction: Float get() = ((percent ?: 0) / 100f).coerceIn(0f, 1f)
}
