package com.example.cloud.sync

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Per-install cloud-sync bookkeeping, stored in a private SharedPreferences file.
 *
 * This is the *device* side of the account-boundary state machine (spec §20–21). It records which
 * account (if any) currently owns this install's local data, which accounts were already migrated
 * from this install (so a re-login never re-pushes a snapshot into an existing cloud account), and
 * every account that ever authenticated here (so signing into a *different* account can be detected
 * and surfaced as an explicit, destructive boundary choice rather than silently mixing data).
 *
 * It also stores the per-history-table pull watermarks (server `synced_at` values, kept as opaque
 * ISO text so the client never has to reason about server clock skew), the stable [installId], and
 * the dirty flags for the two non-Room documents (`profiles`, `user_preferences`) that are pushed
 * as whole rows.
 *
 * Reads/writes are synchronized on a process-local [Mutex]; `apply()` updates the in-memory map
 * synchronously so a later read in the same process sees the write.
 */
open class CloudInstallState(context: Context) {

    private val prefs by lazy {
        context.applicationContext
            .getSharedPreferences("focus_shield_cloud_state", Context.MODE_PRIVATE)
    }
    private val mutex = Mutex()

    private companion object {
        const val KEY_OWNER = "dataOwnerAccountId"
        const val KEY_MIGRATED = "accountsMigratedFromThisInstall"
        const val KEY_KNOWN = "knownAuthenticatedAccounts"
        const val KEY_PROFILE_DIRTY = "dirty_profile"
        const val KEY_PREFS_DIRTY = "dirty_preferences"
        const val KEY_INSTALL_ID = "installId"
        const val WATERMARK_PREFIX = "wm:"
    }

    open suspend fun installId(): String = mutex.withLock {
        prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY_INSTALL_ID, generated).apply()
        }
    }

    /** The account whose cloud data this install's local data currently belongs to (null = none). */
    open suspend fun dataOwnerAccountId(): String? = mutex.withLock {
        prefs.getString(KEY_OWNER, null)
    }

    open suspend fun setDataOwnerAccountId(uid: String?) = mutex.withLock {
        prefs.edit().apply {
            if (uid == null) remove(KEY_OWNER) else putString(KEY_OWNER, uid)
        }.commit()
    }

    /** Accounts whose snapshot was already pushed from this install (never push twice). */
    open suspend fun migratedAccounts(): Set<String> = mutex.withLock {
        prefs.getStringSet(KEY_MIGRATED, emptySet()) ?: emptySet()
    }

    open suspend fun markMigrated(uid: String) = mutex.withLock {
        val current = prefs.getStringSet(KEY_MIGRATED, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(uid)
        prefs.edit().putStringSet(KEY_MIGRATED, current).commit()
    }

    open suspend fun knownAccounts(): Set<String> = mutex.withLock {
        prefs.getStringSet(KEY_KNOWN, emptySet()) ?: emptySet()
    }

    open suspend fun rememberAccount(uid: String) = mutex.withLock {
        val current = prefs.getStringSet(KEY_KNOWN, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(uid)
        prefs.edit().putStringSet(KEY_KNOWN, current).apply()
    }

    /** Remove the account list so a reinstall of the same binary starts from a clean slate. */
    open suspend fun forgetAllAccounts() = mutex.withLock {
        prefs.edit().remove(KEY_KNOWN).apply()
    }

    // ---- history pull watermarks (server `synced_at`, opaque ISO text) --------------

    open suspend fun historyWatermark(tableKey: String): String? = mutex.withLock {
        prefs.getString(WATERMARK_PREFIX + tableKey, null)
    }

    open suspend fun setHistoryWatermark(tableKey: String, iso: String?) = mutex.withLock {
        prefs.edit().apply {
            val key = WATERMARK_PREFIX + tableKey
            if (iso == null) remove(key) else putString(key, iso)
        }.apply()
    }

    // ---- whole-document dirty flags (profiles / user_preferences) -------------------

    open suspend fun profileDirty(): Boolean = mutex.withLock {
        prefs.getBoolean(KEY_PROFILE_DIRTY, false)
    }

    open suspend fun setProfileDirty(dirty: Boolean) = mutex.withLock {
        prefs.edit().putBoolean(KEY_PROFILE_DIRTY, dirty).apply()
    }

    open suspend fun prefsDirty(): Boolean = mutex.withLock {
        prefs.getBoolean(KEY_PREFS_DIRTY, false)
    }

    open suspend fun setPrefsDirty(dirty: Boolean) = mutex.withLock {
        prefs.edit().putBoolean(KEY_PREFS_DIRTY, dirty).apply()
    }

    open suspend fun resetTransient() = mutex.withLock {
        val keys = prefs.all.keys.filter { it.startsWith(WATERMARK_PREFIX) }
        prefs.edit().apply {
            putBoolean(KEY_PROFILE_DIRTY, false)
            putBoolean(KEY_PREFS_DIRTY, false)
            keys.forEach { remove(it) }
        }.apply()
    }
}
