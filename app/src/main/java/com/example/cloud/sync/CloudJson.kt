package com.example.cloud.sync

import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.data.local.entity.BreakRecordEntity
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.KeywordEntity
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

    private fun JSONObject.bool(key: String, default: Boolean): Boolean =
        if (isNull(key)) default else optBoolean(key, default)

    private fun JSONObject.lng(key: String, default: Long): Long =
        if (isNull(key)) default else optLong(key, default)

    private fun JSONObject.int(key: String, default: Int): Int =
        if (isNull(key)) default else optInt(key, default)

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

    fun scheduleFromCloud(j: JSONObject): FocusScheduleEntity = FocusScheduleEntity(
        id = j.optString("id"),
        title = j.optString("title"),
        daysOfWeek = j.optString("daysOfWeek", "MON,TUE,WED,THU,FRI"),
        startTime = j.optString("startTime", "09:00"),
        endTime = j.optString("endTime", "12:00"),
        isEnabled = j.bool("isEnabled", true),
        isAutoStartSession = j.bool("isAutoStartSession", true),
        mode = j.optString("mode", "TIMER"),
        subjectName = j.optString("subjectName", "General Study"),
        colorHex = j.optString("colorHex", "#4F46E5"),
        repeatEnabled = j.bool("repeatEnabled", true),
        scheduledDateMillis = j.lng("scheduledDateMillis", 0L),
        breakMinutes = j.int("breakMinutes", 5),
        description = j.optString("description", ""),
        blockedAppPackages = j.optString("blockedAppPackages", ""),
        blockNotifications = j.bool("blockNotifications", false),
        createdAt = j.lng("createdAt", System.currentTimeMillis())
    )

    // ---- blocked_apps ----------------------------------------------------------------

    fun blockedAppToJson(e: BlockedAppEntity): JSONObject = JSONObject()
        .put("packageName", e.packageName)
        .put("appName", e.appName)
        .put("isEnabled", e.isEnabled)
        .put("createdAt", e.createdAt)

    fun blockedAppFromCloud(j: JSONObject): BlockedAppEntity = BlockedAppEntity(
        packageName = j.optString("packageName"),
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
        domain = j.optString("domain"),
        isEnabled = j.bool("isEnabled", true),
        category = j.optString("category", "MANUAL"),
        createdAt = j.lng("createdAt", System.currentTimeMillis())
    )

    // ---- app_limits ------------------------------------------------------------------

    fun appLimitToJson(e: AppLimitEntity): JSONObject = JSONObject()
        .put("packageName", e.packageName)
        .put("appName", e.appName)
        .put("dailyLimitMinutes", e.dailyLimitMinutes)
        .put("isEnabled", e.isEnabled)
        .put("isStrictOverride", e.isStrictOverride)
        .put("showRemindersBeforeLimit", e.showRemindersBeforeLimit)
        .put("emergencyUsesAllowed", e.emergencyUsesAllowed)
        .put("streakDays", e.streakDays)
        .put("lastStreakDate", e.lastStreakDate)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)

    fun appLimitFromCloud(j: JSONObject): AppLimitEntity = AppLimitEntity(
        packageName = j.optString("packageName"),
        appName = j.optString("appName"),
        dailyLimitMinutes = j.int("dailyLimitMinutes", 0),
        isEnabled = j.bool("isEnabled", true),
        isStrictOverride = j.bool("isStrictOverride", false),
        showRemindersBeforeLimit = j.bool("showRemindersBeforeLimit", true),
        emergencyUsesAllowed = j.int("emergencyUsesAllowed", 1),
        streakDays = j.int("streakDays", 0),
        lastStreakDate = j.optString("lastStreakDate", ""),
        createdAt = j.lng("createdAt", System.currentTimeMillis()),
        updatedAt = j.lng("updatedAt", System.currentTimeMillis())
    )

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
        id = j.optString("id"),
        channelId = j.optString("channelId"),
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
        id = j.optString("id"),
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
        id = j.optString("id"),
        subjectId = j.optString("subjectId"),
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
        id = j.optString("id"),
        subjectId = j.optString("subjectId"),
        subjectName = j.optString("subjectName"),
        topicName = j.optString("topicName"),
        plannedDurationMinutes = j.int("plannedDurationMinutes", 60),
        targetDate = j.lng("targetDate", System.currentTimeMillis()),
        isCompleted = j.bool("isCompleted", false),
        colorHex = j.optString("colorHex", "#7C3AED"),
        createdAt = j.lng("createdAt", System.currentTimeMillis()),
        dateString = j.optString("dateString", ""),
        startTime = j.optString("startTime", "08:00"),
        endTime = j.optString("endTime", "09:00"),
        startMinutes = j.int("startMinutes", 480),
        endMinutes = j.int("endMinutes", 540),
        status = j.optString("status", "PLANNED"),
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
        keyword = j.optString("keyword_value"),
        type = j.optString("keyword_type", "block"),
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
        val mode = try {
            SessionMode.valueOf(j.optString("mode", "TIMER"))
        } catch (ex: Exception) {
            SessionMode.TIMER
        }
        return SessionRecordEntity(
            id = j.optString("id"),
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
        val type = try {
            StudyActivityType.valueOf(j.optString("activityType", "FOCUS_SESSION"))
        } catch (ex: Exception) {
            StudyActivityType.FOCUS_SESSION
        }
        val source = try {
            StudyActivitySource.valueOf(j.optString("source", "TIMER"))
        } catch (ex: Exception) {
            StudyActivitySource.TIMER
        }
        return StudyActivityEntity(
            id = j.optString("id"),
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

    // ---- user_preferences (account-following subset, stored as one jsonb doc) -------

    /**
     * The account-following subset of [FocusPreferences], serialized as a flat object whose keys
     * equal the [FocusPreferences] field names. Device-local preferences (notification package
     * lists, reward claims, onboarding, per-device counters) are intentionally excluded.
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
