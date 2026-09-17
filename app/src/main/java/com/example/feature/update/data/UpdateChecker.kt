package com.example.feature.update.data

import com.example.feature.update.domain.SemanticVersion
import com.example.feature.update.domain.UpdateInfo

/** Outcome of a single update check. */
sealed interface UpdateCheckResult {

    /** Nothing to install: either no releases are published, or we are already current. */
    data object UpToDate : UpdateCheckResult

    /** A strictly newer release exists. */
    data class Available(val info: UpdateInfo) : UpdateCheckResult

    /** The check could not complete. [message] is written for the user but is not shown unprompted. */
    data class Failed(val message: String) : UpdateCheckResult

    /**
     * The check was deliberately not attempted — cooldown still active, or offline on an automatic
     * check. Not an error, and never surfaced (prompt.txt §4/§16).
     */
    data object Skipped : UpdateCheckResult
}

/**
 * Decides whether a newer FocusShield release exists, and how loudly to say so.
 *
 * Owns three separate concerns that are easy to conflate:
 *  - **the comparison** — a release only counts when it is *strictly* newer than the installed
 *    `versionName`, compared component-wise (prompt.txt §2);
 *  - **the throttle** — automatic checks back off for [AUTO_CHECK_INTERVAL] and never run offline,
 *    while an explicit user-initiated check always goes out (prompt.txt §4/§17);
 *  - **the announcement rules** — "Later" and "already notified" are per *version*, so they survive
 *    navigation and process death, and neither of them touches the Profile red dot (prompt.txt §18).
 *
 * Deliberately holds no Android UI state: the automatic path uses it from the application scope, the
 * manual path from the ViewModel, and both must agree exactly.
 */
class UpdateChecker(
    private val repository: UpdateRepository,
    private val preferences: UpdatePreferences,
    private val installedVersionName: String,
    private val isOnline: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis
) {

    /**
     * Runs a check.
     *
     * @param manual true when the user asked for it (Profile → New Updates). Manual checks ignore the
     *   cooldown and report failures; automatic ones are skipped while offline and stay silent.
     */
    suspend fun check(manual: Boolean): UpdateCheckResult {
        if (!manual && !isOnline()) return UpdateCheckResult.Skipped

        val prefs = preferences.current()
        if (!manual && !isDueForCheck(prefs)) return UpdateCheckResult.Skipped

        val info = try {
            repository.fetchLatestRelease()
        } catch (e: UpdateSourceUnavailableException) {
            preferences.recordCheck(latestVersion = null, failed = true)
            return UpdateCheckResult.Failed(e.reason)
        } catch (e: Exception) {
            preferences.recordCheck(latestVersion = null, failed = true)
            return UpdateCheckResult.Failed("Couldn't check for updates.")
        }

        // A release feed with nothing published is not an error — there is simply nothing newer.
        if (info == null) {
            preferences.recordCheck(latestVersion = null, failed = false)
            return UpdateCheckResult.UpToDate
        }

        return if (SemanticVersion.isNewer(info.versionName, installedVersionName)) {
            preferences.recordCheck(info.versionName, failed = false)
            UpdateCheckResult.Available(info)
        } else {
            // Up to date — including the case where the installed build is *ahead* of the newest
            // release, which happens on a local build. Any dismissal still pending for this version
            // is stale now, so it is cleared rather than left to resurface (prompt.txt §23 TEST 10).
            preferences.recordCheck(info.versionName, failed = false)
            // Clear against the running build, not the remote tag. They differ when a local/dev build
            // is ahead of the latest published release.
            preferences.clearForInstalled(installedVersionName)
            UpdateCheckResult.UpToDate
        }
    }

    /**
     * True when an automatic check is allowed to go out. A previous failure shortens the wait so a
     * transient outage does not cost the user six hours of staleness (prompt.txt §4).
     */
    private fun isDueForCheck(prefs: UpdatePrefs): Boolean {
        val last = prefs.lastCheckTime
        if (last == 0L) return true
        val interval = if (prefs.lastCheckFailed) AUTO_RETRY_INTERVAL else AUTO_CHECK_INTERVAL
        return now() - last >= interval
    }

    /**
     * Whether the blocking "update available" popup may be raised for [info]. False once the user has
     * pressed "Later" for this exact version (prompt.txt §6) — the update itself stays available.
     */
    fun shouldPrompt(prefs: UpdatePrefs, info: UpdateInfo): Boolean =
        prefs.dismissedVersion != info.versionName

    /**
     * Whether a notification may be posted for [info]. One per version, so relaunching the app does
     * not re-announce the same release (prompt.txt §5).
     */
    fun shouldNotify(prefs: UpdatePrefs, info: UpdateInfo): Boolean =
        prefs.notifiedVersion != info.versionName

    companion object {
        /** How long an automatic check stays quiet after a successful one (prompt.txt §4). */
        const val AUTO_CHECK_INTERVAL = 6 * 60 * 60 * 1000L

        /** Shorter backoff after a failed check, so recovery does not wait a full interval. */
        const val AUTO_RETRY_INTERVAL = 15 * 60 * 1000L
    }
}
