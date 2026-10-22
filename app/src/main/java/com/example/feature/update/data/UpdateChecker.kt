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

    /**
     * The remote server returned HTTP 304 Not Modified for our cached ETag.
     */
    data object NotModified : UpdateCheckResult
}

/**
 * Decides whether a newer FocusShield release exists, and how loudly to say so.
 *
 * Owns three separate concerns that are easy to conflate:
 *  - **the comparison** — a release only counts when it is *strictly* newer than the installed
 *    build: by `versionCode` when both sides state one, otherwise component-wise by
 *    `versionName` (prompt.txt §2);
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
    /** The running build's `versionCode`; decisive when the release states its own (see `isNewerRelease`). */
    private val installedVersionCode: Int? = null,
    private val isOnline: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis
) {

    /**
     * Runs a check.
     *
     * @param manual true when the user asked for it (Profile → New Updates). Manual checks ignore the
     *   cooldown and report failures; automatic ones are skipped while offline and stay silent.
     * @param forceForeground true when triggered by app launch or foreground resume (uses a short
     *   2-minute debounce combined with ETag conditional requests so newly published releases are
     *   detected right away when opening the app without exhausting GitHub rate limits).
     */
    suspend fun check(manual: Boolean, forceForeground: Boolean = false): UpdateCheckResult {
        if (!manual && !isOnline()) return UpdateCheckResult.Skipped

        val prefs = preferences.current()
        if (!manual && !isDueForCheck(prefs, forceForeground = forceForeground)) {
            return UpdateCheckResult.Skipped
        }

        // The cooldown is stamped from the moment the request starts, never from its completion:
        // a stamp written after a slow response landed later and later, and the periodic worker
        // fires on a fixed grid — so each stamp could push the "due" moment past the next worker
        // run, skipping every other run and halving the real background cadence.
        val startedAt = now()

        val fetchResponse = try {
            val etagToUse = if (manual) null else prefs.etag
            repository.fetchLatestReleaseConditional(etag = etagToUse)
        } catch (e: UpdateSourceUnavailableException) {
            preferences.recordCheck(latestVersion = null, failed = true, at = startedAt)
            return UpdateCheckResult.Failed(e.reason)
        } catch (e: Exception) {
            preferences.recordCheck(latestVersion = null, failed = true, at = startedAt)
            return UpdateCheckResult.Failed("Couldn't check for updates.")
        }

        if (fetchResponse.etag != null) {
            preferences.setEtag(fetchResponse.etag)
        }

        // HTTP 304 Not Modified: nothing changed on the remote feed since our last ETag.
        if (fetchResponse.notModified) {
            preferences.recordCheck(
                latestVersion = prefs.latestKnownVersion,
                failed = false,
                at = startedAt
            )
            return UpdateCheckResult.NotModified
        }

        val info = fetchResponse.info

        // A release feed with nothing published is not an error — there is simply nothing newer.
        // GitHub's 404 for an empty release list (and for a private repo) lands here too.
        if (info == null) {
            preferences.recordCheck(latestVersion = null, failed = false, at = startedAt)
            return UpdateCheckResult.UpToDate
        }

        return if (SemanticVersion.isNewerRelease(
                candidate = info.versionName,
                candidateVersionCode = info.versionCode,
                installed = installedVersionName,
                installedVersionCode = installedVersionCode
            )
        ) {
            preferences.recordCheck(info.versionName, failed = false, at = startedAt)
            UpdateCheckResult.Available(info)
        } else {
            // Up to date — including the case where the installed build is *ahead* of the newest
            // release, which happens on a local build. Any dismissal still pending for this version
            // is stale now, so it is cleared rather than left to resurface (prompt.txt §23 TEST 10).
            preferences.recordCheck(info.versionName, failed = false, at = startedAt)
            // Clear against the running build, not the remote tag. They differ when a local/dev build
            // is ahead of the latest published release.
            preferences.clearForInstalled(installedVersionName)
            UpdateCheckResult.UpToDate
        }
    }

    /**
     * True when an automatic check is allowed to go out.
     * Periodic background checks run on a 15-minute cadence, while foreground launch/resume checks
     * use a 30-second debounce with ETag conditional requests so opening the app always checks fresh.
     */
    private fun isDueForCheck(prefs: UpdatePrefs, forceForeground: Boolean): Boolean {
        val last = prefs.lastCheckTime
        if (last == 0L) return true
        val interval = when {
            forceForeground -> FOREGROUND_CHECK_DEBOUNCE
            prefs.lastCheckFailed -> AUTO_RETRY_INTERVAL
            else -> AUTO_CHECK_INTERVAL
        }
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
        /** Continuous 15-minute background cadence for periodic checks. */
        const val AUTO_CHECK_INTERVAL = 15 * 60 * 1000L

        /** Shorter backoff after a failed check, so recovery does not wait a full interval. */
        const val AUTO_RETRY_INTERVAL = 5 * 60 * 1000L

        /** Minimum debounce between foreground launch/resume checks (paired with ETag 304). */
        const val FOREGROUND_CHECK_DEBOUNCE = 30 * 1000L
    }
}
