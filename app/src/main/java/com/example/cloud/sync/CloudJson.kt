package com.example.cloud.sync

import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.BlockedAttemptEntity
import com.example.data.local.entity.BlockedEventSource
import com.example.data.local.entity.BlockedEventType
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.data.local.entity.BreakRecordEntity
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.KeywordEntity
import com.example.data.local.entity.ScratchCardEntity
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyActivitySource
import com.example.data.local.entity.StudyActivityType
import com.example.data.local.entity.StudyChannelEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.TopicEntity
import com.example.data.model.SessionMode
import com.example.data.preferences.FocusPreferences
import com.example.feature.session.notification.ScheduleTime
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Entity <-> cloud-JSON conversion for the Room-backed sync tables.
 *
 * Design: the Supabase tables mirror the Room column names and stored values 1:1 (booleans stored
 * as booleans, enums stored as their `name`), so conversions are mechanical and there is no
 * snake_case translation layer to drift. Server-only columns (`user_id`, `synced_at`) are added
 * and stripped by the engine, never by these functions.
 *
 * All functions are pure and safe to call from any thread.
 */
object CloudJson {

    /**
     * Placeholder table name for the type-strict readers, which only know their column. Callers that
     * know the table wrap the conversion (see [SyncEngine]) and re-report the exception with it.
     */
    private const val TABLE_UNKNOWN = "<unknown>"

    // ---- org.json helpers -------------------------------------------------------------

    private fun JSONObject.putString(key: String, value: String?): JSONObject {
        put(key, if (value == null) JSONObject.NULL else value)
        return this
    }

    /** Optional text that treats JSON null and missing the same (null). */
    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() || has(key) }?.let {
            if (isNull(key)) null else optString(key)
        }

    // ---- strict field readers ---------------------------------------------------------
    //
    // `optBoolean`/`optLong`/`optInt` coerce anything they cannot read into the default (a string
    // "yes" becomes `false`, a nested object becomes 0), which hides corrupt payloads. These readers
    // accept only what the column is actually supposed to hold and raise [CloudDataException]
    // otherwise; the engine rejects that single row and records why. See [CloudDataException].
    //
    // A *missing* key is different from a *wrong* value: these tables are declared with nullable
    // columns and partial payloads are legitimate, so absence still falls back to the documented
    // default (`preserveNullDiscovery`-style strictness would reject every older row).

    private fun JSONObject.bool(key: String, default: Boolean): Boolean {
        if (!has(key) || isNull(key)) return default
        return when (val value = opt(key)) {
            is Boolean -> value
            is String -> when (value.lowercase(Locale.ROOT)) {
                "true" -> true
                "false" -> false
                else -> throw CloudDataException(TABLE_UNKNOWN, key, value, "expected a boolean")
            }
            else -> throw CloudDataException(TABLE_UNKNOWN, key, value, "expected a boolean")
        }
    }

    private fun JSONObject.lng(key: String, default: Long): Long {
        if (!has(key) || isNull(key)) return default
        val value = opt(key)
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
                ?: throw CloudDataException(TABLE_UNKNOWN, key, value, "expected an integer")
            else -> throw CloudDataException(TABLE_UNKNOWN, key, value, "expected an integer")
        }
    }

    private fun JSONObject.int(key: String, default: Int): Int {
        if (!has(key) || isNull(key)) return default
        val value = opt(key)
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
                ?: throw CloudDataException(TABLE_UNKNOWN, key, value, "expected an integer")
            else -> throw CloudDataException(TABLE_UNKNOWN, key, value, "expected an integer")
        }
    }

    /**
     * Required text used as (part of) a row's identity. A missing/blank/non-text value is fatal for
     * the row: an empty primary key would collide with every other malformed row in the same table.
     */
    private fun JSONObject.identity(table: String, key: String): String {
        val value = if (isNull(key)) null else opt(key)
        if (value !is String || value.isBlank()) {
            throw CloudDataException(table, key, value, "required identity value is missing or blank")
        }
        return value
    }

    /** Text with a documented default for a genuinely cosmetic/optional column. Never for keys. */
    private fun JSONObject.text(table: String, key: String, default: String): String {
        if (!has(key) || isNull(key)) return default
        val value = opt(key)
        return value as? String
            ?: throw CloudDataException(table, key, value, "expected text")
    }

    /** Enum-by-name parsing with no fallback: an unknown name is a rejected row, not a default. */
    private inline fun <reified E : Enum<E>> JSONObject.enumValue(
        table: String,
        key: String,
        label: String
    ): E {
        val raw = if (isNull(key)) null else opt(key)
        val text = (raw as? String)?.trim()?.uppercase(Locale.ROOT)
            ?: throw CloudDataException(table, key, raw, "expected one of the $label names")
        return enumValues<E>().firstOrNull { it.name == text }
            ?: throw CloudDataException(table, key, raw, "unknown $label value")
    }

    /**
     * Enum-by-name parsing for a column that is allowed to be absent.
     *
     * @param default value used when the column is missing or NULL — never when it is present but
     *   unrecognised, which rejects the row instead.
     */
    private inline fun <reified E : Enum<E>> JSONObject.enumOrDefault(
        table: String,
        key: String,
        label: String,
        default: E
    ): E {
        if (!has(key) || isNull(key)) return default
        return enumValue<E>(table, key, label)
    }

    /** One of a fixed set of uppercase string values (columns that are not Kotlin enums). */
    private fun JSONObject.oneOf(
        table: String,
        key: String,
        allowed: Set<String>,
        default: String?
    ): String {
        if (!has(key) || isNull(key)) {
            return default
                ?: throw CloudDataException(table, key, null, "expected one of $allowed")
        }
        val raw = opt(key)
        val text = (raw as? String)?.trim()
            ?: throw CloudDataException(table, key, raw, "expected one of $allowed")
        return allowed.firstOrNull { it.equals(text, ignoreCase = true) }
            ?: throw CloudDataException(table, key, raw, "expected one of $allowed")
    }

    /** A stored `HH:mm` clock time, validated by the same strict parser the alarm scheduler uses. */
    private fun JSONObject.clockTime(table: String, key: String, default: String?): String {
        if (!has(key) || isNull(key)) {
            return default ?: throw CloudDataException(table, key, null, "expected a HH:mm time")
        }
        val raw = opt(key)
        val text = raw as? String
            ?: throw CloudDataException(table, key, raw, "expected a HH:mm time")
        if (ScheduleTime.parseToMinutesOrNull(text) == null) {
            throw CloudDataException(table, key, raw, "not a valid HH:mm time")
        }
        return text
    }

    // ---- focus_schedules --------------------------------------------------------------

    fun focusScheduleToJson(e: FocusScheduleEntity): JSONObject = JSONObject()
        .put("id", e.id)
        .put("title", e.title)
        .put("daysOfWeek", e.daysOfWeek)
        .put("startTime", e.startTime)
        .put("endTime", e.endTime)
        .put("isEnabled", e.isEnabled)
        .put("isAutoStartSession", e.isAutoStartSession)
        .put("mode", e.mode)
        .put("subjectName", e.subjectName)
        .put("colorHex", e.colorHex)
        .put("repeatEnabled", e.repeatEnabled)
        .put("scheduledDateMillis", e.scheduledDateMillis)
        .put("breakMinutes", e.breakMinutes)
        .put("description", e.description)
        .put("blockedAppPackages", e.blockedAppPackages)
        .put("blockNotifications", e.blockNotifications)
        .put("createdAt", e.createdAt)

    fun scheduleFromCloud(j: JSONObject): FocusScheduleEntity {
        val table = SyncTables.FOCUS_SCHEDULES
        // A schedule's whole purpose is to fire at a specific time on specific days, so these two
        // fields are validated with the same strict parser the alarm scheduler uses. A row whose
        // times/days cannot be understood is rejected here — it would otherwise be stored and then
        // either silently skipped by the scheduler or (previously) fired at a substituted time.
        val startTime = j.clockTime(table, "startTime", "09:00")
        val endTime = j.clockTime(table, "endTime", "12:00")
        val days = if (j.has("daysOfWeek") && !j.isNull("daysOfWeek")) {
            val raw = j.opt("daysOfWeek")
            val text = raw as? String
                ?: throw CloudDataException(table, "daysOfWeek", raw, "expected text")
            if (ScheduleTime.parseDaysOfWeekOrNull(text) == null) {
                throw CloudDataException(table, "daysOfWeek", raw, "no recognisable weekday")
            }
            text
        } else {
            "MON,TUE,WED,THU,FRI"
        }

        return FocusScheduleEntity(
            id = j.identity(table, "id"),
            title = j.text(table, "title", "Study Schedule"),
            daysOfWeek = days,
            startTime = startTime,
            endTime = endTime,
            isEnabled = j.bool("isEnabled", true),
            isAutoStartSession = j.bool("isAutoStartSession", true),
            mode = j.oneOf(table, "mode", setOf("TIMER", "POMODORO", "STOPWATCH"), "TIMER"),
            subjectName = j.text(table, "subjectName", "General Study"),
            colorHex = j.text(table, "colorHex", "#4F46E5"),
            repeatEnabled = j.bool("repeatEnabled", true),
            scheduledDateMillis = j.lng("scheduledDateMillis", 0L),
            breakMinutes = j.int("breakMinutes", 5),
            description = j.text(table, "description", ""),
            blockedAppPackages = j.text(table, "blockedAppPackages", ""),
            blockNotifications = j.bool("blockNotifications", false),
            createdAt = j.lng("createdAt", System.currentTimeMillis())
        )
    }

    // ---- blocked_apps ----------------------------------------------------------------

    fun blockedAppToJson(e: BlockedAppEntity): JSONObject = JSONObject()
        .put("packageName", e.packageName)
        .put("appName", e.appName)
        .put("isEnabled", e.isEnabled)
        .put("createdAt", e.createdAt)

    fun blockedAppFromCloud(j: JSONObject): BlockedAppEntity = BlockedAppEntity(
        packageName = j.identity(SyncTables.BLOCKED_APPS, "packageName"),
        appName = j.optString("appName"),
        isEnabled = j.bool("isEnabled", true),
        createdAt = j.lng("createdAt", System.currentTimeMillis())
    )

    // ---- blocked_websites ------------------------------------------------------------

    fun blockedWebsiteToJson(e: BlockedWebsiteEntity): JSONObject = JSONObject()
        .put("domain", e.domain)
        .put("isEnabled", e.isEnabled)
        .put("category", e.category)
        .put("createdAt", e.createdAt)

    fun blockedWebsiteFromCloud(j: JSONObject): BlockedWebsiteEntity = BlockedWebsiteEntity(
        domain = j.identity(SyncTables.BLOCKED_WEBSITES, "domain"),
        isEnabled = j.bool("isEnabled", true),
        // The block decision is made from this string, so an unrecognised category is a rejected row
        // rather than a silent downgrade to MANUAL.
        category = j.oneOf(
            SyncTables.BLOCKED_WEBSITES,
            "category",
            setOf("MANUAL", "ADULT"),
            "MANUAL"
        ),
        createdAt = j.lng("createdAt", System.currentTimeMillis())
    )

    // ---- app_limits ------------------------------------------------------------------
    //
    // No mapper: local-only. An app limit is not account configuration — the daily limit, enabled
    // flag, strict-mode preference, reminder setting and emergency-allowance count all describe what
    // *this phone* should enforce, so a second device must never inherit them. The row therefore
    // never enters the sync pipeline at all; it is not merely filtered out on the way to the wire.

    // ---- study_channels --------------------------------------------------------------

    fun studyChannelToJson(e: StudyChannelEntity): JSONObject = JSONObject()
        .put("id", e.id)
        .put("channelId", e.channelId)
        .put("channelName", e.channelName)
        .put("channelUrl", e.channelUrl)
        .put("thumbnailUrl", e.thumbnailUrl)
        .put("isApproved", e.isApproved)
        .put("createdAt", e.createdAt)

    fun studyChannelFromCloud(j: JSONObject): StudyChannelEntity = StudyChannelEntity(
        id = j.identity(SyncTables.STUDY_CHANNELS, "id"),
        // Not part of the identity and nullable in the schema: a partial row still restores.
        channelId = j.text(SyncTables.STUDY_CHANNELS, "channelId", ""),
        channelName = j.optString("channelName"),
        channelUrl = j.optString("channelUrl"),
        thumbnailUrl = j.optString("thumbnailUrl", ""),
        isApproved = j.bool("isApproved", true),
        createdAt = j.lng("createdAt", System.currentTimeMillis())
    )

    // ---- study_subjects --------------------------------------------------------------

    fun subjectToJson(e: SubjectEntity): JSONObject = JSONObject()
        .put("id", e.id)
        .put("name", e.name)
        .put("colorHex", e.colorHex)
        .put("iconIdentifier", e.iconIdentifier)
        .put("isArchived", e.isArchived)
        .put("createdAt", e.createdAt)

    fun subjectFromCloud(j: JSONObject): SubjectEntity = SubjectEntity(
        id = j.identity(SyncTables.STUDY_SUBJECTS, "id"),
        name = j.optString("name"),
        colorHex = j.optString("colorHex", "#7C3AED"),
        iconIdentifier = j.optString("iconIdentifier", "default"),
        isArchived = j.bool("isArchived", false),
        createdAt = j.lng("createdAt", System.currentTimeMillis())
    )

    // ---- study_topics ----------------------------------------------------------------

    fun topicToJson(e: TopicEntity): JSONObject = JSONObject()
        .put("id", e.id)
        .put("subjectId", e.subjectId)
        .put("name", e.name)
        .put("createdAt", e.createdAt)

    fun topicFromCloud(j: JSONObject): TopicEntity = TopicEntity(
        id = j.identity(SyncTables.STUDY_TOPICS, "id"),
        // Nullable in the schema (a topic can arrive before its subject); not an identity field.
        subjectId = j.text(SyncTables.STUDY_TOPICS, "subjectId", ""),
        name = j.optString("name"),
        createdAt = j.lng("createdAt", System.currentTimeMillis())
    )

    // ---- study_plans ---------------------------------------------------------------

    fun studyPlanToJson(e: StudyPlanEntity): JSONObject = JSONObject()
        .put("id", e.id)
        .put("subjectId", e.subjectId)
        .put("subjectName", e.subjectName)
        .put("topicName", e.topicName)
        .put("plannedDurationMinutes", e.plannedDurationMinutes)
        .put("targetDate", e.targetDate)
        .put("isCompleted", e.isCompleted)
        .put("colorHex", e.colorHex)
        .put("createdAt", e.createdAt)
        .put("dateString", e.dateString)
        .put("startTime", e.startTime)
        .put("endTime", e.endTime)
        .put("startMinutes", e.startMinutes)
        .put("endMinutes", e.endMinutes)
        .put("status", e.status)
        .put("notes", e.notes)

    fun studyPlanFromCloud(j: JSONObject): StudyPlanEntity = StudyPlanEntity(
        id = j.identity(SyncTables.STUDY_PLANS, "id"),
        subjectId = j.optString("subjectId"),
        subjectName = j.optString("subjectName"),
        topicName = j.optString("topicName"),
        plannedDurationMinutes = j.int("plannedDurationMinutes", 60),
        targetDate = j.lng("targetDate", System.currentTimeMillis()),
        isCompleted = j.bool("isCompleted", false),
        colorHex = j.optString("colorHex", "#7C3AED"),
        createdAt = j.lng("createdAt", System.currentTimeMillis()),
        dateString = j.optString("dateString", ""),
        // Times are validated exactly like a focus schedule's: a plan reminder must fire at the
        // time the plan says, or not at all.
        startTime = j.clockTime(SyncTables.STUDY_PLANS, "startTime", "08:00"),
        endTime = j.clockTime(SyncTables.STUDY_PLANS, "endTime", "09:00"),
        startMinutes = j.int("startMinutes", 480),
        endMinutes = j.int("endMinutes", 540),
        // study_plans.status is written only by StudyPlanDao (PLANNED / COMPLETED).
        status = j.oneOf(
            SyncTables.STUDY_PLANS,
            "status",
            setOf("PLANNED", "COMPLETED"),
            "PLANNED"
        ),
        notes = j.optString("notes", "")
    )

    // ---- keywords (natural key; Room autogen id is NOT synced) ----------------------

    fun keywordToJson(e: KeywordEntity): JSONObject = JSONObject()
        .put("keyword_type", e.type)
        .put("keyword_value", e.keyword)
        .put("is_active", e.isActive)
        .put("created_at", e.createdAt)

    /** Build the local Room row for a cloud keyword row. Room id is 0; caller resolves the real row. */
    fun keywordFromCloud(j: JSONObject): KeywordEntity = KeywordEntity(
        id = 0L,
        keyword = j.identity(SyncTables.BLOCKED_KEYWORDS, "keyword_value"),
        // "allow" and "block" are opposite behaviours; guessing one would silently invert the rule.
        type = j.oneOf(SyncTables.BLOCKED_KEYWORDS, "keyword_type", setOf("allow", "block"), "block"),
        isActive = j.bool("is_active", true),
        createdAt = j.lng("created_at", System.currentTimeMillis())
    )

    // ---- session_records -------------------------------------------------------------

    fun sessionRecordToJson(e: SessionRecordEntity): JSONObject = JSONObject()
        .put("id", e.id)
        .put("mode", e.mode.name)
        .put("title", e.title)
        .put("subject", e.subject)
        .put("topic", e.topic)
        .put("goal", e.goal)
        .put("plannedDurationMillis", e.plannedDurationMillis)
        .put("actualDurationMillis", e.actualDurationMillis)
        .put("startTime", e.startTime)
        .put("endTime", e.endTime)
        .put("completed", e.completed)
        .put("cancelled", e.cancelled)
        .put("pomodoroCycles", e.pomodoroCycles)
        .put("completedPomodoroCycles", e.completedPomodoroCycles)
        .put("createdAt", e.createdAt)

    fun sessionRecordFromCloud(j: JSONObject): SessionRecordEntity {
        val mode = j.enumOrDefault(
            SyncTables.SESSION_RECORDS,
            "mode",
            "SessionMode",
            SessionMode.TIMER
        )
        return SessionRecordEntity(
            id = j.identity(SyncTables.SESSION_RECORDS, "id"),
            mode = mode,
            title = j.optString("title"),
            subject = j.optString("subject"),
            topic = j.optString("topic"),
            goal = j.optString("goal"),
            plannedDurationMillis = j.lng("plannedDurationMillis", 0L),
            actualDurationMillis = j.lng("actualDurationMillis", 0L),
            startTime = j.lng("startTime", 0L),
            endTime = j.lng("endTime", 0L),
            completed = j.bool("completed", false),
            cancelled = j.bool("cancelled", false),
            pomodoroCycles = j.int("pomodoroCycles", 1),
            completedPomodoroCycles = j.int("completedPomodoroCycles", 0),
            createdAt = j.lng("createdAt", System.currentTimeMillis())
        )
    }

    // ---- study_activities ------------------------------------------------------------

    fun studyActivityToJson(e: StudyActivityEntity): JSONObject = JSONObject()
        .put("id", e.id)
        .put("sessionId", e.sessionId)
        .put("activityType", e.activityType.name)
        .put("source", e.source.name)
        .put("subject", e.subject)
        .put("topic", e.topic)
        .putString("channelName", e.channelName)
        .putString("channelId", e.channelId)
        .putString("videoTitle", e.videoTitle)
        .put("startedAt", e.startedAt)
        .put("endedAt", e.endedAt)
        .put("durationMillis", e.durationMillis)
        .put("createdAt", e.createdAt)

    fun studyActivityFromCloud(j: JSONObject): StudyActivityEntity {
        // Analytics aggregate these two columns, so an unknown value must not be counted as a focus
        // session (previously the fallback) without anyone being able to see that it happened.
        val type = j.enumOrDefault(
            SyncTables.STUDY_ACTIVITIES,
            "activityType",
            "StudyActivityType",
            StudyActivityType.FOCUS_SESSION
        )
        val source = j.enumOrDefault(
            SyncTables.STUDY_ACTIVITIES,
            "source",
            "StudyActivitySource",
            StudyActivitySource.TIMER
        )
        return StudyActivityEntity(
            id = j.identity(SyncTables.STUDY_ACTIVITIES, "id"),
            sessionId = j.optString("sessionId"),
            activityType = type,
            source = source,
            subject = j.optString("subject"),
            topic = j.optString("topic"),
            channelName = j.optNullableString("channelName"),
            channelId = j.optNullableString("channelId"),
            videoTitle = j.optNullableString("videoTitle"),
            startedAt = j.lng("startedAt", 0L),
            endedAt = j.lng("endedAt", 0L),
            durationMillis = j.lng("durationMillis", 0L),
            createdAt = j.lng("createdAt", System.currentTimeMillis())
        )
    }

    // ---- break_records ---------------------------------------------------------------

    fun breakRecordToJson(e: BreakRecordEntity): JSONObject = JSONObject()
        .put("id", e.id)
        .put("sessionId", e.sessionId)
        .put("requestedDurationMillis", e.requestedDurationMillis)
        .put("actualDurationMillis", e.actualDurationMillis)
        .put("startTime", e.startTime)
        .put("endTime", e.endTime)
        .put("completed", e.completed)
        .put("createdAt", e.createdAt)

    fun breakRecordFromCloud(j: JSONObject): BreakRecordEntity = BreakRecordEntity(
        id = j.optString("id"),
        sessionId = j.optString("sessionId"),
        requestedDurationMillis = j.lng("requestedDurationMillis", 0L),
        actualDurationMillis = j.lng("actualDurationMillis", 0L),
        startTime = j.lng("startTime", 0L),
        endTime = j.lng("endTime", 0L),
        completed = j.bool("completed", true),
        createdAt = j.lng("createdAt", System.currentTimeMillis())
    )

    // ---- blocked_attempts ------------------------------------------------------------
    //
    // `eventId` is the cloud identity; the Room autogen `id` is a local-only surrogate and is
    // deliberately not uploaded. Payload columns the app did not actually observe at block time
    // (domain, channel, video title, matched keyword) stay NULL rather than being synthesised.

    fun blockedAttemptToJson(e: BlockedAttemptEntity): JSONObject = JSONObject()
        .put("eventId", e.eventId)
        .put("timestamp", e.timestamp)
        .put("eventType", e.eventType.name)
        .put("source", e.source.name)
        .put("packageName", e.packageName)
        .put("appName", e.appName)
        .putString("domain", e.domain)
        .putString("channelId", e.channelId)
        .putString("channelName", e.channelName)
        .putString("videoTitle", e.videoTitle)
        .putString("matchedKeyword", e.matchedKeyword)
        .putString("ruleRef", e.ruleRef)
        .putString("sessionId", e.sessionId)
        .putString("scheduleId", e.scheduleId)
        .putString("subject", e.subject)
        .putString("topic", e.topic)
        .putString("deviceId", e.deviceId)
        .put("createdAt", e.timestamp)

    fun blockedAttemptFromCloud(j: JSONObject): BlockedAttemptEntity {
        val type = j.enumOrDefault(
            SyncTables.BLOCKED_ATTEMPTS,
            "eventType",
            "BlockedEventType",
            BlockedEventType.LEGACY
        )
        val source = j.enumOrDefault(
            SyncTables.BLOCKED_ATTEMPTS,
            "source",
            "BlockedEventSource",
            BlockedEventSource.LEGACY
        )
        return BlockedAttemptEntity(
            eventId = j.identity(SyncTables.BLOCKED_ATTEMPTS, "eventId"),
            timestamp = j.lng("timestamp", 0L),
            eventType = type,
            source = source,
            packageName = j.optString("packageName"),
            appName = j.optString("appName"),
            domain = j.optNullableString("domain"),
            channelId = j.optNullableString("channelId"),
            channelName = j.optNullableString("channelName"),
            videoTitle = j.optNullableString("videoTitle"),
            matchedKeyword = j.optNullableString("matchedKeyword"),
            ruleRef = j.optNullableString("ruleRef"),
            sessionId = j.optNullableString("sessionId"),
            scheduleId = j.optNullableString("scheduleId"),
            subject = j.optNullableString("subject"),
            topic = j.optNullableString("topic"),
            deviceId = j.optNullableString("deviceId")
        )
    }

    // ---- app_limit_sessions ----------------------------------------------------------
    //
    // No mapper: local-only. These rows describe this device's current allowance/enforcement
    // episodes ("unlocked 5 more minutes"), and the limit configuration they were granted under is
    // device-local too — the entire app-limit system stays on the phone.

    // ---- daily_unlocks ---------------------------------------------------------------
    //
    // No mapper: daily_unlocks is local-only. Per-day phone unlock counts are general device usage
    // statistics (§12), not FocusShield activity, so they never leave the device.

    // ---- scratch_cards ---------------------------------------------------------------
    //
    // The reward text is generated once at creation and is not reconstructible from anything
    // else, so it has to travel with the account or the card reappears empty after a restore.

    fun scratchCardToJson(e: ScratchCardEntity): JSONObject = JSONObject()
        .put("sessionId", e.sessionId)
        .put("createdAt", e.createdAt)
        .put("rewardType", e.rewardType)
        .put("rewardEmoji", e.rewardEmoji)
        .put("rewardTitle", e.rewardTitle)
        .put("rewardMessage", e.rewardMessage)
        .put("studyMinutes", e.studyMinutes)
        .put("isRevealed", e.isRevealed)

    fun scratchCardFromCloud(j: JSONObject): ScratchCardEntity = ScratchCardEntity(
        sessionId = j.identity(SyncTables.SCRATCH_CARDS, "sessionId"),
        createdAt = j.lng("createdAt", 0L),
        rewardType = j.optString("rewardType"),
        rewardEmoji = j.optString("rewardEmoji"),
        rewardTitle = j.optString("rewardTitle"),
        rewardMessage = j.optString("rewardMessage"),
        studyMinutes = j.int("studyMinutes", 0),
        isRevealed = j.bool("isRevealed", false)
    )

    // ---- daily_app_usage -------------------------------------------------------------
    //
    // No mapper: local-only. Per-app foreground usage is Android UsageStats-derived data and must
    // never become cloud history (§12). The local enforcement columns (`emergencyUsesCount`,
    // `isBypassedForToday`) are device enforcement state for the same reason (§11).

    // ---- user_preferences (account-following subset, stored as one jsonb doc) -------

    /**
     * The account-following subset of [FocusPreferences], serialized as a flat object whose keys
     * equal the [FocusPreferences] field names.
     *
     * Sets are written **sorted**, never in iteration order. [SyncedPreferencesObserver] fingerprints
     * this document to decide whether it is dirty, and an unordered set would produce a different
     * fingerprint for identical content — a spurious upload on every emission. Sorting makes the
     * serialization a pure function of the value.
     *
     * Genuinely device-local state stays out: `hasCompletedOnboarding` (a per-install UX gate),
     * `userPhotoUri` (a `content://` reference meaningful only on the device that picked it — the
     * account-level image is `profiles.avatarPath`), and `blockedNotificationsCount` (a runtime
     * aggregate derived from `blocked_attempts`, not a stored source of truth).
     */
    fun accountPreferencesJson(p: FocusPreferences): JSONObject = JSONObject()
        .put("defaultTimerMinutes", p.defaultTimerMinutes)
        .put("pomodoroFocusMinutes", p.pomodoroFocusMinutes)
        .put("pomodoroShortBreakMinutes", p.pomodoroShortBreakMinutes)
        .put("pomodoroLongBreakMinutes", p.pomodoroLongBreakMinutes)
        .put("pomodoroCycles", p.pomodoroCycles)
        .put("defaultSubject", p.defaultSubject)
        .put("defaultTopic", p.defaultTopic)
        .put("isAppBlockingDefault", p.isAppBlockingDefault)
        .put("isStrictModeDefault", p.isStrictModeDefault)
        .put("isStudyChannelsDefault", p.isStudyChannelsDefault)
        .put("isYouTubeShortsBlockingEnabled", p.isYouTubeShortsBlockingEnabled)
        .put("isInstagramReelsBlockingEnabled", p.isInstagramReelsBlockingEnabled)
        .put("isFacebookReelsBlockingEnabled", p.isFacebookReelsBlockingEnabled)
        .put("isShortsReelsAlwaysBlocked", p.isShortsReelsAlwaysBlocked)
        .put("dailyGoalMinutes", p.dailyGoalMinutes)
        .put("minimumStreakThresholdMinutes", p.minimumStreakThresholdMinutes)
        .put("themeMode", p.themeMode)
        .put("isAutoAdultWebsiteBlockingEnabled", p.isAutoAdultWebsiteBlockingEnabled)
        .put("isManualWebsiteBlockingEnabled", p.isManualWebsiteBlockingEnabled)
        .put("isBlockUninstallEnabled", p.isBlockUninstallEnabled)
        .put("isBlockSplitScreenEnabled", p.isBlockSplitScreenEnabled)
        .put("isBlockFloatingWindowEnabled", p.isBlockFloatingWindowEnabled)
        .put("isBlockNotificationsEnabled", p.isBlockNotificationsEnabled)
        .put("notificationBlockMode", p.notificationBlockMode)
        // Which apps to silence is protection *configuration* the user chose, so it follows the
        // account; the count of what was silenced is history and lives in `blocked_attempts`.
        .put("blockedNotificationPackages", sortedArray(p.blockedNotificationPackages))
        .put("alwaysBlockedNotificationPackages", sortedArray(p.alwaysBlockedNotificationPackages))
        // Claimed achievement rewards. The badge *unlock* is derived from session history, but a
        // claim is a user action with nothing else recording it — without this the rewards screen
        // shows every already-claimed badge as available again after a restore.
        .put("claimedRewardIds", sortedArray(p.claimedRewardIds))

    private fun sortedArray(values: Set<String>): JSONArray =
        JSONArray(values.sorted())

    // ---- profiles (DataStore-backed; one row per user) ------------------------------

    fun profileToJson(
        displayName: String,
        avatarPreset: String,
        motto: String,
        academicGoal: String,
        avatarPath: String?
    ): JSONObject {
        val o = JSONObject()
            .put("displayName", displayName)
            .put("avatarPreset", avatarPreset)
            .put("motto", motto)
            .put("academicGoal", academicGoal)
        return if (avatarPath == null) o.put("avatarPath", JSONObject.NULL) else o.put("avatarPath", avatarPath)
    }
}
