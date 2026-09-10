package com.example.cloud.sync

/**
 * Canonical identifiers for the tables this app syncs. For mirrored tables the value equals both
 * the Room table name and the Supabase table name. Values are stored in `sync_outbox.tableName`
 * and are therefore treated as stable identifiers — do not rename without a migration of pending
 * outbox rows.
 */
object SyncTables {
    // Config tables (reconcile: full-table, remote deletes propagate).
    const val FOCUS_SCHEDULES = "focus_schedules"
    const val BLOCKED_APPS = "blocked_apps"
    const val BLOCKED_WEBSITES = "blocked_websites"
    const val APP_LIMITS = "app_limits"
    const val STUDY_CHANNELS = "study_channels"
    const val STUDY_SUBJECTS = "study_subjects"
    const val STUDY_TOPICS = "study_topics"
    const val STUDY_PLANS = "study_plans"
    const val BLOCKED_KEYWORDS = "blocked_keywords" // Room table "keywords"

    // History tables (append-only pull; deletes propagate only when performed locally).
    const val SESSION_RECORDS = "session_records"
    const val STUDY_ACTIVITIES = "study_activities"
    const val BREAK_RECORDS = "break_records"

    // Whole-document rows (one row per user, not stored in Room).
    const val PROFILES = "profiles"
    const val USER_PREFERENCES = "user_preferences"
}

/** How a table's local copy is reconciled against the cloud copy during a pull. */
enum class PullMode {
    /** Full-table LWW. Local rows missing remotely are deleted (remote delete propagates). */
    RECONCILE,

    /** Append-only since a watermark; rows are never deleted by a pull. */
    HISTORY
}

/**
 * Static metadata for one Room-backed synced table. The single implementation lives in
 * [SyncTableHandlers] where the Room DAOs are available.
 */
data class TableMeta(
    /** SyncTables value — equals the Room table name for mirrored tables. */
    val tableKey: String,
    /** PostgREST table name (differs from Room only for keywords). */
    val cloudTable: String,
    /**
     * Room primary-key column used to key the outbox and build cloud delete filters.
     * For most tables this is `id`, `packageName` or `domain`. Keywords use a natural key and
     * are handled specially (see [SyncKeycode]).
     */
    val pkField: String,
    val pullMode: PullMode
)

/**
 * The Room-backed tables that participate in sync, in dependency order (parents first). Study
 * topics depend on subjects, so subjects are pushed/pulled before topics.
 */
val SYNCED_ROOM_TABLES: List<TableMeta> = listOf(
    TableMeta(SyncTables.STUDY_SUBJECTS, SyncTables.STUDY_SUBJECTS, "id", PullMode.RECONCILE),
    TableMeta(SyncTables.STUDY_TOPICS, SyncTables.STUDY_TOPICS, "id", PullMode.RECONCILE),
    TableMeta(SyncTables.STUDY_PLANS, SyncTables.STUDY_PLANS, "id", PullMode.RECONCILE),
    TableMeta(SyncTables.FOCUS_SCHEDULES, SyncTables.FOCUS_SCHEDULES, "id", PullMode.RECONCILE),
    TableMeta(SyncTables.BLOCKED_APPS, SyncTables.BLOCKED_APPS, "packageName", PullMode.RECONCILE),
    TableMeta(SyncTables.BLOCKED_WEBSITES, SyncTables.BLOCKED_WEBSITES, "domain", PullMode.RECONCILE),
    TableMeta(SyncTables.APP_LIMITS, SyncTables.APP_LIMITS, "packageName", PullMode.RECONCILE),
    TableMeta(SyncTables.STUDY_CHANNELS, SyncTables.STUDY_CHANNELS, "id", PullMode.RECONCILE),
    TableMeta(SyncTables.BLOCKED_KEYWORDS, SyncTables.BLOCKED_KEYWORDS, "", PullMode.RECONCILE),
    TableMeta(SyncTables.SESSION_RECORDS, SyncTables.SESSION_RECORDS, "id", PullMode.HISTORY),
    TableMeta(SyncTables.STUDY_ACTIVITIES, SyncTables.STUDY_ACTIVITIES, "id", PullMode.HISTORY),
    TableMeta(SyncTables.BREAK_RECORDS, SyncTables.BREAK_RECORDS, "id", PullMode.HISTORY)
)

/** Keyword natural keys: a keyword row has no stable Room PK, so we key it by type + keyword. */
object SyncKeycode {
    private const val SEP = ":"

    /** Build the stable key for a keyword row. Unambiguous because `type` is a fixed enum value. */
    fun keywordKey(type: String, keyword: String): String = "$type$SEP${keyword.length}$SEP$keyword"

    /**
     * Split a key produced by [keywordKey] back into (type, keyword). Returns null when the key is
     * not a well-formed keyword key.
     */
    fun decodeKeywordKey(key: String): Pair<String, String>? {
        val a = key.indexOf(SEP)
        if (a <= 0) return null
        val type = key.substring(0, a)
        val rest = key.substring(a + 1)
        val b = rest.indexOf(SEP)
        if (b <= 0) return null
        val lenText = rest.substring(0, b)
        val length = lenText.toIntOrNull() ?: return null
        val keyword = rest.substring(b + 1)
        if (keyword.length != length) return null
        return type to keyword
    }
}
