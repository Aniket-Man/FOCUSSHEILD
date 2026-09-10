package com.example.data.repository

import android.content.Context
import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.core.util.ChannelLogoStorageManager
import com.example.data.local.dao.StudyChannelDao
import com.example.data.local.entity.StudyChannelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StudyChannelRepository(
    private val studyChannelDao: StudyChannelDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val context: Context? = null
) {
    val approvedChannels: Flow<List<StudyChannelEntity>> = studyChannelDao.getApprovedChannelsFlow()
    val allChannels: Flow<List<StudyChannelEntity>> = studyChannelDao.getAllChannelsFlow()

    // Fast in-memory sets for non-blocking AccessibilityService lookups without Room disk hits
    private val cachedApprovedChannelIds = ConcurrentHashMap.newKeySet<String>()
    private val cachedApprovedNormalizedNames = ConcurrentHashMap.newKeySet<String>()

    init {
        coroutineScope.launch {
            studyChannelDao.getApprovedChannelsFlow().collectLatest { channels ->
                cachedApprovedChannelIds.clear()
                cachedApprovedNormalizedNames.clear()
                channels.forEach { ch ->
                    if (ch.isApproved) {
                        val normId = ch.channelId.trim().lowercase(Locale.ROOT)
                        cachedApprovedChannelIds.add(normId)
                        if (normId.startsWith("@")) {
                            cachedApprovedChannelIds.add(normId.removePrefix("@"))
                        } else {
                            cachedApprovedChannelIds.add("@$normId")
                        }
                        val normName = normalizeChannelName(ch.channelName)
                        if (normName.isNotBlank()) {
                            cachedApprovedNormalizedNames.add(normName)
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolves and normalizes a user-entered URL, handle, or plain channel name into an authoritative channelId and formatted URL.
     */
    fun extractChannelIdentity(rawUrlOrHandle: String): Pair<String, String> {
        val trimmed = rawUrlOrHandle.trim()
        val cleanUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            if (trimmed.startsWith("@")) "https://www.youtube.com/$trimmed"
            else if (trimmed.startsWith("UC") && trimmed.length >= 20) "https://www.youtube.com/channel/$trimmed"
            else "https://www.youtube.com/@${trimmed.replace(" ", "")}"
        } else {
            trimmed
        }

        val channelId = when {
            // Standard channel ID URL: https://www.youtube.com/channel/UCxxxxxxxxxxxx
            cleanUrl.contains("/channel/") -> {
                val after = cleanUrl.substringAfter("/channel/").substringBefore("/").substringBefore("?").trim()
                if (after.isNotBlank()) after else "UC_${cleanUrl.hashCode()}"
            }
            // Handle URL: https://www.youtube.com/@PhysicsWallah
            cleanUrl.contains("/@") -> {
                val handle = cleanUrl.substringAfter("/@").substringBefore("/").substringBefore("?").trim()
                if (handle.isNotBlank()) "@$handle" else "UC_${cleanUrl.hashCode()}"
            }
            // Custom URL: https://www.youtube.com/c/KhanAcademy
            cleanUrl.contains("/c/") -> {
                val custom = cleanUrl.substringAfter("/c/").substringBefore("/").substringBefore("?").trim()
                if (custom.isNotBlank()) "c/$custom" else "UC_${cleanUrl.hashCode()}"
            }
            // User URL: https://www.youtube.com/user/KhanAcademy
            cleanUrl.contains("/user/") -> {
                val user = cleanUrl.substringAfter("/user/").substringBefore("/").substringBefore("?").trim()
                if (user.isNotBlank()) "user/$user" else "UC_${cleanUrl.hashCode()}"
            }
            // Video URL: https://www.youtube.com/watch?v=xxx or https://youtu.be/xxx
            cleanUrl.contains("watch?v=") || cleanUrl.contains("youtu.be/") -> {
                val videoId = if (cleanUrl.contains("watch?v=")) {
                    cleanUrl.substringAfter("watch?v=").substringBefore("&").substringBefore("?")
                } else {
                    cleanUrl.substringAfter("youtu.be/").substringBefore("?")
                }
                "video_$videoId"
            }
            trimmed.startsWith("@") -> trimmed
            trimmed.startsWith("UC") && trimmed.length >= 16 -> trimmed
            else -> "@${trimmed.replace(" ", "")}"
        }

        return Pair(channelId, cleanUrl)
    }

    /**
     * Adds an approved educational channel. Accepts channel name alone or with URL/handle and optional thumbnail.
     */
    suspend fun addChannel(
        channelName: String,
        channelUrl: String = "",
        isApproved: Boolean = true,
        thumbnailUrl: String = ""
    ): Result<StudyChannelEntity> {
        val trimmedName = channelName.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Channel name cannot be empty"))
        }

        val urlOrHandleToUse = if (channelUrl.isBlank()) trimmedName else channelUrl
        val (channelId, formattedUrl) = extractChannelIdentity(urlOrHandleToUse)
        
        // Check duplicate by channelId or normalized name
        val normInputName = normalizeChannelName(trimmedName)
        val existing = studyChannelDao.getChannelByChannelId(channelId)
        if (existing != null) {
            return Result.failure(IllegalStateException("Channel is already added: ${existing.channelName}"))
        }

        val entity = StudyChannelEntity(
            id = UUID.randomUUID().toString(),
            channelId = channelId,
            channelName = trimmedName,
            channelUrl = formattedUrl,
            thumbnailUrl = thumbnailUrl,
            isApproved = isApproved,
            createdAt = System.currentTimeMillis()
        )

        studyChannelDao.insertChannel(entity)

        SyncTracker.enqueueUpsert(
            SyncTables.STUDY_CHANNELS,
            entity.id,
            CloudJson.studyChannelToJson(entity).toString()
        )

        if (isApproved) {
            cachedApprovedChannelIds.add(channelId.trim().lowercase(Locale.ROOT))
            if (normInputName.isNotBlank()) cachedApprovedNormalizedNames.add(normInputName)
        }

        // Cache logo to local storage and update Room with permanent local URI
        if (context != null) {
            coroutineScope.launch {
                ChannelLogoStorageManager.cacheAndPersistToDatabase(context, entity, studyChannelDao)
            }
        }

        return Result.success(entity)
    }

    /**
     * Deletes a study channel by ID.
     */
    suspend fun removeChannel(id: String) {
        studyChannelDao.deleteChannelById(id)
        SyncTracker.enqueueDelete(SyncTables.STUDY_CHANNELS, id)
    }

    /**
     * Deletes all study channels.
     */
    suspend fun clearAllChannels() {
        // Read the row ids first so each channel's delete can be propagated to the cloud
        // (a whole-table DELETE has no single cloud key to target).
        val channels = try {
            studyChannelDao.getAllChannels()
        } catch (e: Exception) {
            emptyList()
        }
        studyChannelDao.deleteAllChannels()
        cachedApprovedChannelIds.clear()
        cachedApprovedNormalizedNames.clear()
        channels.forEach { channel ->
            SyncTracker.enqueueDelete(SyncTables.STUDY_CHANNELS, channel.id)
        }
    }

    /**
     * Enables or disables approval status for a channel.
     */
    suspend fun toggleChannelApproval(id: String, isApproved: Boolean) {
        studyChannelDao.setApprovalStatus(id, isApproved)
        // Read back the full row so the reconcile pull never reverts an unpushed toggle.
        val updated = studyChannelDao.getChannelById(id)
        if (updated != null) {
            SyncTracker.enqueueUpsert(
                SyncTables.STUDY_CHANNELS,
                updated.id,
                CloudJson.studyChannelToJson(updated).toString()
            )
        }
    }

    /**
     * Fast in-memory check without touching Room SQLite storage.
     */
    fun isChannelApproved(channelId: String?, channelName: String?): Boolean {
        if (!channelId.isNullOrBlank()) {
            val normId = channelId.trim().lowercase(Locale.ROOT)
            if (cachedApprovedChannelIds.contains(normId)) return true
            // Check without @ prefix
            if (normId.startsWith("@") && cachedApprovedChannelIds.contains(normId.removePrefix("@"))) return true
            if (cachedApprovedChannelIds.contains("@$normId")) return true
        }

        if (!channelName.isNullOrBlank()) {
            val normName = normalizeChannelName(channelName)
            if (cachedApprovedNormalizedNames.contains(normName)) return true
            // Also check partial word matches for common educational brandings
            if (cachedApprovedNormalizedNames.any { it.isNotBlank() && (normName.contains(it) || it.contains(normName)) }) {
                return true
            }
        }

        return false
    }

    fun getApprovedChannelIds(): Set<String> {
        return cachedApprovedChannelIds.toSet()
    }

    fun getApprovedChannelNames(): Set<String> {
        return cachedApprovedNormalizedNames.toSet()
    }

    /**
     * Normalizes channel names for tolerant comparison (case, spaces, symbols).
     */
    private fun normalizeChannelName(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    /**
     * Seeds initial curated educational channels if empty on first launch.
     */
    suspend fun initializeDefaultChannelsIfEmpty() {
        if (studyChannelDao.getChannelCount() == 0) {
            val defaults = listOf(
                StudyChannelEntity(
                    id = "chan_pw",
                    channelId = "@PhysicsWallah",
                    channelName = "Physics Wallah - Alakh Pandey",
                    channelUrl = "https://www.youtube.com/@PhysicsWallah",
                    thumbnailUrl = "https://yt3.googleusercontent.com/ytc/AIdro_k6P07VvjV81tP4z9K82vK9Y-xM5oA=s176-c-k-c0x00ffffff-no-rj",
                    isApproved = true
                )
            )
            studyChannelDao.insertAll(defaults)
            defaults.forEach {
                SyncTracker.enqueueUpsert(
                    SyncTables.STUDY_CHANNELS,
                    it.id,
                    CloudJson.studyChannelToJson(it).toString()
                )
                cachedApprovedChannelIds.add(it.channelId.trim().lowercase(Locale.ROOT))
                cachedApprovedNormalizedNames.add(normalizeChannelName(it.channelName))
            }
        }
    }
}

