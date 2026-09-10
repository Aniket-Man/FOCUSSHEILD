package com.example.data.repository

import com.example.cloud.sync.CloudInstallState
import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.data.local.dao.BlockedAttemptDao
import com.example.data.local.entity.BlockedAttemptEntity
import com.example.data.local.entity.BlockedEventSource
import com.example.data.local.entity.BlockedEventType
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * Single write path for protection events (`blocked_attempts`).
 *
 * Every engine that blocks, silences or explicitly allows something records it here, so the table is
 * the one place the app's protection history lives — and the only record that survives a restore.
 * Rows are append-only: an event that happened is never mutated, which is what makes the cloud copy
 * idempotent ([BlockedAttemptEntity.eventId] is the cross-device identity) and lets two devices on
 * the same account merge their histories without either overwriting the other.
 */
class BlockedAttemptRepository(
    private val blockedAttemptDao: BlockedAttemptDao,
    private val cloudInstallState: CloudInstallState
) {
    val allAttempts: Flow<List<BlockedAttemptEntity>> = blockedAttemptDao.getAllAttemptsFlow()

    fun getTodayAttemptsFlow(): Flow<List<BlockedAttemptEntity>> {
        val (startOfDay, _) = getTodayBounds()
        return blockedAttemptDao.getAttemptsSinceFlow(startOfDay)
    }

    fun getTodayAttemptsCountFlow(): Flow<Int> {
        val (startOfDay, _) = getTodayBounds()
        return blockedAttemptDao.getAttemptCountSinceFlow(startOfDay)
    }

    fun getAttemptsForSessionFlow(sessionId: String): Flow<List<BlockedAttemptEntity>> {
        return blockedAttemptDao.getAttemptsForSessionFlow(sessionId)
    }

    /** Today's count of one event type, derived from the events themselves. */
    fun getTodayEventCountFlow(eventType: BlockedEventType): Flow<Int> {
        val (startOfDay, _) = getTodayBounds()
        return blockedAttemptDao.getEventCountSinceFlow(eventType, startOfDay)
    }

    /**
     * Record one protection event. [appName] is the human-readable label the UI shows; everything
     * else is optional and is passed only when the caller genuinely has the value at decision time
     * (the YouTube branch has a channel, title and matched keyword; the website branch has a domain).
     * Nothing is synthesised to fill a column.
     */
    suspend fun recordAttempt(
        packageName: String,
        appName: String,
        eventType: BlockedEventType = BlockedEventType.APP_BLOCKED,
        source: BlockedEventSource = BlockedEventSource.ACCESSIBILITY_SERVICE,
        sessionId: String? = null,
        domain: String? = null,
        channelId: String? = null,
        channelName: String? = null,
        videoTitle: String? = null,
        matchedKeyword: String? = null,
        ruleRef: String? = null,
        scheduleId: String? = null,
        subject: String? = null,
        topic: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val attempt = BlockedAttemptEntity(
            timestamp = timestamp,
            eventType = eventType,
            source = source,
            packageName = packageName,
            appName = appName,
            domain = domain,
            channelId = channelId,
            channelName = channelName,
            videoTitle = videoTitle,
            matchedKeyword = matchedKeyword,
            ruleRef = ruleRef,
            sessionId = sessionId,
            scheduleId = scheduleId,
            subject = subject,
            topic = topic,
            deviceId = cloudInstallState.installId()
        )
        val rowId = blockedAttemptDao.insertAttempt(attempt)
        SyncTracker.enqueueUpsert(
            SyncTables.BLOCKED_ATTEMPTS,
            attempt.eventId,
            CloudJson.blockedAttemptToJson(attempt).toString()
        )
        return rowId
    }

    /**
     * Local-only clear. No cloud delete is enqueued: events are an append-only history, and a device
     * that cleared its copy must not erase the same events from the account's other devices.
     */
    suspend fun clearAttempts() {
        blockedAttemptDao.clearAllAttempts()
    }

    private fun getTodayBounds(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis - 1
        return Pair(startOfDay, endOfDay)
    }
}
