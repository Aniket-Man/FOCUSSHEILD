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
    private val cachedBlockedChannelIds = ConcurrentHashMap.newKeySet<String>()
    private val cachedBlockedNormalizedNames = ConcurrentHashMap.newKeySet<String>()

    init {
        coroutineScope.launch {
            try {
                val initialAll = studyChannelDao.getAllChannels()
                updateCache(initialAll)
            } catch (_: Exception) {}

            studyChannelDao.getAllChannelsFlow().collectLatest { channels ->
                updateCache(channels)
            }
        }
    }

    private fun updateCache(channels: List<StudyChannelEntity>) {
        val newApprovedIds = mutableSetOf<String>()
        val newApprovedNames = mutableSetOf<String>()
        val newBlockedIds = mutableSetOf<String>()
        val newBlockedNames = mutableSetOf<String>()

        channels.forEach { ch ->
            val targetIds = if (ch.isApproved) newApprovedIds else newBlockedIds
            val targetNames = if (ch.isApproved) newApprovedNames else newBlockedNames

            val normId = ch.channelId.trim().lowercase(Locale.ROOT)
            targetIds.add(normId)
            val rawHandle = normId.removePrefix("@")
            targetIds.add(rawHandle)
            targetIds.add("@$rawHandle")
            val cleanAlnumHandle = normalizeChannelName(rawHandle)
            if (cleanAlnumHandle.isNotBlank()) {
                targetIds.add(cleanAlnumHandle)
                targetNames.add(cleanAlnumHandle)
            }

            val trimmedName = ch.channelName.trim()
            targetNames.add(trimmedName)
            val normName = normalizeChannelName(trimmedName)
            if (normName.isNotBlank()) {
                targetNames.add(normName)
                targetIds.add(normName)
                targetIds.add("@$normName")
            }
            // Index distinct brand clauses separated by hyphen/pipe/bullet (e.g. "Physics Wallah - Alakh Pandey")
            val clauses = trimmedName.split(Regex("\\s*[\\-|•·]+\\s*"))
                .map { normalizeChannelName(it) }
                .filter { it.length >= 4 }
            targetNames.addAll(clauses)
        }
        cachedApprovedChannelIds.clear()
        cachedApprovedChannelIds.addAll(newApprovedIds)
        cachedApprovedNormalizedNames.clear()
        cachedApprovedNormalizedNames.addAll(newApprovedNames)

        cachedBlockedChannelIds.clear()
        cachedBlockedChannelIds.addAll(newBlockedIds)
        cachedBlockedNormalizedNames.clear()
        cachedBlockedNormalizedNames.addAll(newBlockedNames)
    }

    suspend fun refreshApprovedCache() {
        try {
            val all = studyChannelDao.getAllChannels()
            updateCache(all)
        } catch (_: Exception) {}
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
        thumbnailUrl: String = "",
        verifiedByAi: Boolean = false,
        category: String = "",
        aiReason: String = ""
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
            if (existing.isApproved != isApproved) {
                studyChannelDao.setApprovalStatusAndManual(existing.id, isApproved)
                val updated = existing.copy(isApproved = isApproved, verifiedByAi = false)
                SyncTracker.enqueueUpsert(
                    SyncTables.STUDY_CHANNELS,
                    updated.id,
                    CloudJson.studyChannelToJson(updated).toString()
                )
                try {
                    val all = studyChannelDao.getAllChannels()
                    updateCache(all)
                } catch (_: Exception) {}
                return Result.success(updated)
            }
            return Result.failure(IllegalStateException("Channel is already added: ${existing.channelName}"))
        }

        val entity = StudyChannelEntity(
            id = UUID.randomUUID().toString(),
            channelId = channelId,
            channelName = trimmedName,
            channelUrl = formattedUrl,
            thumbnailUrl = thumbnailUrl,
            isApproved = isApproved,
            createdAt = System.currentTimeMillis(),
            verifiedByAi = verifiedByAi,
            category = category,
            aiReason = aiReason
        )

        studyChannelDao.insertChannel(entity)

        SyncTracker.enqueueUpsert(
            SyncTables.STUDY_CHANNELS,
            entity.id,
            CloudJson.studyChannelToJson(entity).toString()
        )

        try {
            val all = studyChannelDao.getAllChannels()
            updateCache(all)
        } catch (_: Exception) {
            val targetIds = if (isApproved) cachedApprovedChannelIds else cachedBlockedChannelIds
            val targetNames = if (isApproved) cachedApprovedNormalizedNames else cachedBlockedNormalizedNames
            val normId = channelId.trim().lowercase(Locale.ROOT)
            targetIds.add(normId)
            val rawHandle = normId.removePrefix("@")
            targetIds.add(rawHandle)
            targetIds.add("@$rawHandle")
            targetNames.add(trimmedName)
            if (normInputName.isNotBlank()) {
                targetNames.add(normInputName)
            }
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
        try {
            val all = studyChannelDao.getAllChannels()
            updateCache(all)
        } catch (_: Exception) {}
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
        cachedBlockedChannelIds.clear()
        cachedBlockedNormalizedNames.clear()
        channels.forEach { channel ->
            SyncTracker.enqueueDelete(SyncTables.STUDY_CHANNELS, channel.id)
        }
    }

    /**
     * Enables or disables approval status for a channel (marks as manual user choice).
     */
    suspend fun toggleChannelApproval(id: String, isApproved: Boolean) {
        studyChannelDao.setApprovalStatusAndManual(id, isApproved)
        try {
            val all = studyChannelDao.getAllChannels()
            updateCache(all)
        } catch (_: Exception) {}
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
     * Automatically records an AI-verified Educational or Entertainment channel so repeat visits
     * resolve locally in 0ms using 0 AI credits. Never overwrites a user's existing channel entry.
     */
    suspend fun recordAiLearnedChannel(
        channelName: String,
        channelHandle: String? = null,
        isApproved: Boolean,
        category: String,
        aiReason: String
    ): StudyChannelEntity? {
        val rawName = channelName.trim()
        val rawHandle = channelHandle?.trim().orEmpty()
        val displayName = rawName.ifBlank { rawHandle }
        if (displayName.isBlank() || displayName.lowercase(Locale.ROOT) in IGNORED_GENERIC_CHANNEL_NAMES) {
            return null
        }

        val identityInput = rawHandle.ifBlank { displayName }
        val (channelId, formattedUrl) = extractChannelIdentity(identityInput)
        val normName = normalizeChannelName(displayName)

        val existingById = studyChannelDao.getChannelByChannelId(channelId)
        if (existingById != null) return existingById

        val existingByName = try {
            studyChannelDao.getAllChannels().firstOrNull { ch ->
                val chNorm = normalizeChannelName(ch.channelName)
                val chIdNorm = normalizeChannelName(ch.channelId.removePrefix("@"))
                normName.isNotBlank() && (chNorm == normName || chIdNorm == normName)
            }
        } catch (_: Exception) {
            null
        }
        if (existingByName != null) return existingByName

        val cleanCategory = category.trim().ifBlank {
            if (isApproved) "Educational" else "Entertainment"
        }
        val entity = StudyChannelEntity(
            id = UUID.randomUUID().toString(),
            channelId = channelId,
            channelName = displayName,
            channelUrl = formattedUrl,
            thumbnailUrl = "",
            isApproved = isApproved,
            createdAt = System.currentTimeMillis(),
            verifiedByAi = true,
            category = cleanCategory,
            aiReason = aiReason.trim()
        )
        studyChannelDao.insertChannel(entity)
        SyncTracker.enqueueUpsert(
            SyncTables.STUDY_CHANNELS,
            entity.id,
            CloudJson.studyChannelToJson(entity).toString()
        )
        try {
            val all = studyChannelDao.getAllChannels()
            updateCache(all)
        } catch (_: Exception) {}

        if (context != null) {
            coroutineScope.launch {
                ChannelLogoStorageManager.cacheAndPersistToDatabase(context, entity, studyChannelDao)
            }
        }
        return entity
    }

    /**
     * One-tap helper from the Recent AI Verdicts feed to mark a channel as Trusted (Always Allow)
     * or Blocked (Always Deny) override.
     */
    suspend fun setChannelOverride(channelName: String, isTrusted: Boolean): Result<StudyChannelEntity> {
        val trimmed = channelName.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Channel name cannot be empty"))
        }
        val (channelId, formattedUrl) = extractChannelIdentity(trimmed)
        val existing = studyChannelDao.getChannelByChannelId(channelId)
        if (existing != null) {
            toggleChannelApproval(existing.id, isTrusted)
            return Result.success(existing.copy(isApproved = isTrusted, verifiedByAi = false))
        }
        return addChannel(
            channelName = trimmed,
            channelUrl = formattedUrl,
            isApproved = isTrusted,
            verifiedByAi = false,
            category = if (isTrusted) "Manual Study" else "Manual Block"
        )
    }

    companion object {
        private val IGNORED_GENERIC_CHANNEL_NAMES = setOf(
            "unknown channel",
            "youtube",
            "youtube channel",
            "youtube creator",
            "youtube video",
            "ai verified study",
            "approved channel",
            "blocked channel",
            "unapproved channel"
        )
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
        if (cachedApprovedChannelIds.isEmpty() && cachedBlockedChannelIds.isEmpty()) {
            try {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    val all = studyChannelDao.getAllChannels()
                    if (all.isNotEmpty()) {
                        updateCache(all)
                    }
                }
            } catch (_: Exception) {}
        }
        return cachedApprovedChannelIds.toSet()
    }

    fun getApprovedChannelNames(): Set<String> {
        if (cachedApprovedNormalizedNames.isEmpty() && cachedBlockedNormalizedNames.isEmpty()) {
            try {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    val all = studyChannelDao.getAllChannels()
                    if (all.isNotEmpty()) {
                        updateCache(all)
                    }
                }
            } catch (_: Exception) {}
        }
        return cachedApprovedNormalizedNames.toSet()
    }

    fun getBlockedChannelIds(): Set<String> = cachedBlockedChannelIds.toSet()

    fun getBlockedChannelNames(): Set<String> = cachedBlockedNormalizedNames.toSet()

    /**
     * Normalizes channel names for tolerant comparison (case, spaces, symbols).
     */
    private fun normalizeChannelName(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    /**
     * Context-Aware AI Study Mode is now the primary authority for YouTube videos.
     * Cleans up the legacy auto-seeded 'chan_pw' entry so the channel override list starts clean.
     */
    suspend fun initializeDefaultChannelsIfEmpty() {
        try {
            val legacyDefault = studyChannelDao.getChannelById("chan_pw")
            if (legacyDefault != null) {
                studyChannelDao.deleteChannelById("chan_pw")
                val remaining = studyChannelDao.getAllChannels()
                updateCache(remaining)
            }
        } catch (_: Exception) {}
    }
}

