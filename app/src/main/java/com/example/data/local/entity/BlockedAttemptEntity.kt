package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * What kind of protection decision produced a [BlockedAttemptEntity].
 *
 * These mirror the app's real decision points: the accessibility service's YouTube Study Mode
 * verdicts ([com.example.feature.youtube.engine.YouTubeBlockVerdict]), the blocker manager's app
 * blocks, the notification engine, and the app-limit / strict-mode engines.
 *
 * [LEGACY] is only ever produced by the 12->13 migration for rows written before event types
 * existed; nothing writes it at runtime.
 */
enum class BlockedEventType {
    APP_BLOCKED,
    WEBSITE_BLOCKED,
    KEYWORD_BLOCKED,
    /** Short-form content: YouTube Shorts, Instagram Reels, Facebook Reels. */
    SHORTS_BLOCKED,
    YOUTUBE_UNAPPROVED_CHANNEL,
    YOUTUBE_HOME_FEED,
    YOUTUBE_UNKNOWN_CONTENT,
    YOUTUBE_ALLOWED,
    NOTIFICATION_SILENCED,
    FLOATING_WINDOW_BLOCKED,
    SPLIT_SCREEN_BLOCKED,
    APP_LIMIT_REACHED,
    STRICT_MODE_ENFORCED,
    LEGACY
}

/** Which engine or service observed the event. */
enum class BlockedEventSource {
    ACCESSIBILITY_SERVICE,
    APP_LIMIT_ENGINE,
    NOTIFICATION_ENGINE,
    STRICT_MODE_ENGINE,
    LEGACY
}

/**
 * A single protection event: something FocusShield blocked, silenced, or explicitly allowed.
 *
 * Identity: [id] is a device-local autoincrement surrogate used for Room ordering and deletion.
 * [eventId] is the **stable cross-device identity** and the cloud primary key, so re-uploading a
 * row is idempotent and two devices can never collide on the same event.
 *
 * Payload fields ([domain], [channelId], [channelName], [videoTitle], [matchedKeyword], [ruleRef])
 * are populated only where the app genuinely has the value at block time — e.g. the YouTube branch
 * already carries a title, channel and matched keyword on its block decision. Fields the app does
 * not collect stay null rather than being synthesised.
 *
 * Nothing here can carry notification *content*: the notification engine records that a
 * notification was silenced, never its title or body.
 */
@Entity(
    tableName = "blocked_attempts",
    indices = [
        Index(value = ["eventId"], unique = true),
        Index("timestamp"),
        Index("sessionId"),
        Index("eventType")
    ]
)
data class BlockedAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: BlockedEventType = BlockedEventType.APP_BLOCKED,
    val source: BlockedEventSource = BlockedEventSource.ACCESSIBILITY_SERVICE,
    val packageName: String,
    val appName: String,
    /** Blocked website domain, when the event came from the website rules. */
    val domain: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val videoTitle: String? = null,
    /** Keyword-rule match, when the block was keyword-driven. */
    val matchedKeyword: String? = null,
    /** The blocking rule/category that fired (e.g. a website category or "APP_LIMIT"). */
    val ruleRef: String? = null,
    val sessionId: String? = null,
    val scheduleId: String? = null,
    val subject: String? = null,
    val topic: String? = null,
    /**
     * Stable per-install identifier, for provenance when the same account runs on several devices.
     * Null for rows that predate the 12->13 migration.
     */
    val deviceId: String? = null
)
