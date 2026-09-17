package com.example.feature.update.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Device-local persistence for the update checker (prompt.txt §4/§13).
 *
 * Kept in its own DataStore on purpose. `FocusPreferencesRepository` is covered by
 * `SyncedPreferencesObserver` and uploaded to Supabase, and "this handset last checked for an update
 * on Tuesday" is exactly the kind of device state that must not follow the account to another phone
 * — the same separation the App Limit system uses.
 */
private val Context.updateDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "focus_shield_update")

/** Snapshot of everything remembered between update checks. */
data class UpdatePrefs(
    /** Epoch millis of the last *completed* check of any outcome. Drives the cooldown. */
    val lastCheckTime: Long = 0L,
    /** Version string most recently seen published, so the UI can show something before a re-check. */
    val latestKnownVersion: String? = null,
    /** Version the user dismissed with "Later" — suppresses the popup, never the red dot. */
    val dismissedVersion: String? = null,
    /** Version already announced by notification, so it is not announced twice. */
    val notifiedVersion: String? = null,
    /** Downloaded APK on disk, if any, to avoid re-downloading the same release. */
    val downloadedVersion: String? = null,
    val downloadedPath: String? = null,
    /**
     * True when the last check failed for a reason worth retrying. Lets an auto-check retry on the
     * next launch/connectivity change without the cooldown blocking recovery from a transient error.
     */
    val lastCheckFailed: Boolean = false,
    /**
     * Version whose red dot the user explicitly cleared. Opening the update screen does *not* set
     * this — only the "mark as read" action does (prompt.txt §7/§18).
     */
    val readVersion: String? = null,
    /**
     * The last offer, serialized, so the update screen can render the release notes and version
     * correctly after a process death instead of coming back empty (prompt.txt §13).
     */
    val latestInfoJson: String? = null
)

class UpdatePreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val now: () -> Long = System::currentTimeMillis
) {

    constructor(context: Context) : this(context.updateDataStore)

    private object Keys {
        val LAST_CHECK_TIME = longPreferencesKey("last_check_time")
        val LATEST_KNOWN_VERSION = stringPreferencesKey("latest_known_version")
        val DISMISSED_VERSION = stringPreferencesKey("dismissed_version")
        val NOTIFIED_VERSION = stringPreferencesKey("notified_version")
        val DOWNLOADED_VERSION = stringPreferencesKey("downloaded_version")
        val DOWNLOADED_PATH = stringPreferencesKey("downloaded_path")
        val LAST_CHECK_FAILED = booleanPreferencesKey("last_check_failed")
        val READ_VERSION = stringPreferencesKey("read_version")
        val LATEST_INFO_JSON = stringPreferencesKey("latest_info_json")
    }

    val flow: Flow<UpdatePrefs> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UpdatePrefs(
                lastCheckTime = prefs[Keys.LAST_CHECK_TIME] ?: 0L,
                latestKnownVersion = prefs[Keys.LATEST_KNOWN_VERSION],
                dismissedVersion = prefs[Keys.DISMISSED_VERSION],
                notifiedVersion = prefs[Keys.NOTIFIED_VERSION],
                downloadedVersion = prefs[Keys.DOWNLOADED_VERSION],
                downloadedPath = prefs[Keys.DOWNLOADED_PATH],
                lastCheckFailed = prefs[Keys.LAST_CHECK_FAILED] ?: false,
                readVersion = prefs[Keys.READ_VERSION],
                latestInfoJson = prefs[Keys.LATEST_INFO_JSON]
            )
        }

    suspend fun current(): UpdatePrefs = flow.first()

    /** Records the outcome of a check. [latestVersion] is null when the check failed. */
    suspend fun recordCheck(latestVersion: String?, failed: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_CHECK_TIME] = now()
            prefs[Keys.LAST_CHECK_FAILED] = failed
            if (latestVersion != null) prefs[Keys.LATEST_KNOWN_VERSION] = latestVersion
        }
    }

    suspend fun markDismissed(version: String) {
        dataStore.edit { it[Keys.DISMISSED_VERSION] = version }
    }

    suspend fun markNotified(version: String) {
        dataStore.edit { it[Keys.NOTIFIED_VERSION] = version }
    }

    /**
     * Explicit "mark as read" — the only user action, besides a successful install, that clears the
     * Profile red dot (prompt.txt §7/§18).
     */
    suspend fun markRead(version: String) {
        dataStore.edit { it[Keys.READ_VERSION] = version }
    }

    /** Persists (or clears, with null) the serialized offer so it survives process death. */
    suspend fun setLatestInfoJson(json: String?) {
        dataStore.edit { prefs ->
            if (json == null) prefs.remove(Keys.LATEST_INFO_JSON) else prefs[Keys.LATEST_INFO_JSON] = json
        }
    }

    suspend fun recordDownload(version: String, path: String) {
        dataStore.edit { prefs ->
            prefs[Keys.DOWNLOADED_VERSION] = version
            prefs[Keys.DOWNLOADED_PATH] = path
        }
    }

    suspend fun clearDownload() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.DOWNLOADED_VERSION)
            prefs.remove(Keys.DOWNLOADED_PATH)
        }
    }

    /**
     * Clears every piece of release-specific state once the running build is current.
     *
     * These values are intentionally removed unconditionally. The installed build may be ahead of
     * the published build (for example, a local build), in which case keys belonging to the older
     * remote version are just as stale as keys whose version exactly matches [version]. In
     * particular, the serialized offer must go: retaining it can resurrect an installed update on
     * the next process start and turn the Profile red dot back on.
     */
    suspend fun clearForInstalled(version: String) {
        dataStore.edit { prefs ->
            prefs.remove(Keys.DISMISSED_VERSION)
            prefs.remove(Keys.NOTIFIED_VERSION)
            prefs.remove(Keys.READ_VERSION)
            prefs.remove(Keys.DOWNLOADED_VERSION)
            prefs.remove(Keys.DOWNLOADED_PATH)
            prefs.remove(Keys.LATEST_INFO_JSON)
            prefs[Keys.LATEST_KNOWN_VERSION] = version
        }
    }

    /**
     * Drops an internally inconsistent cached offer and makes the next automatic check immediately
     * due. This can happen if the process dies after recording a newly published version but before
     * its full [com.example.feature.update.domain.UpdateInfo] JSON has been persisted.
     */
    suspend fun discardStaleOfferForRefresh() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.DOWNLOADED_VERSION)
            prefs.remove(Keys.DOWNLOADED_PATH)
            prefs.remove(Keys.LATEST_INFO_JSON)
            prefs[Keys.LAST_CHECK_TIME] = 0L
        }
    }
}
