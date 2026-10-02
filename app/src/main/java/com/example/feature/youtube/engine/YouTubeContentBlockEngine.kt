package com.example.feature.youtube.engine

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Central YouTube content block engine.
 *
 * Channel-name-only evaluation pipeline:
 *
 *  1. Shorts player detected                    -> BLOCK_SHORTS (always, any short, any channel)
 *  2. Watch page + approved study channel       -> ALLOW
 *  3. Watch page + channel NOT in approved list -> BLOCK_UNAPPROVED_CHANNEL
 *  4. Watch page + channel not rendered yet     -> short metadata grace, then BLOCK_UNKNOWN_CONTENT (if strict)
 *  5. Home feed                                 -> HOME_FEED (Study Mode nudge popup)
 *  6. Anything else (search, subscriptions...)  -> ALLOW_NAVIGATION
 *
 * The video title is NEVER used to judge content. The only authority is the
 * channel name/handle verified against the user's approved channel list.
 */
object YouTubeContentBlockEngine {

    /** Total budget for channel metadata to render before the video is treated as unknown. */
    private const val CHANNEL_METADATA_GRACE_MILLIS = 5000L

    /** Time gap delay (in millis) after video playback starts before enforcing any blocking decision. */
    private const val PLAYBACK_START_DELAY_MILLIS = 3000L

    private const val MIN_FUZZY_MATCH_LENGTH = 3

    @Volatile
    private var currentVideoKey: String? = null

    @Volatile
    private var currentVideoFirstSeenAt: Long = 0L

    @Volatile
    private var playbackStartedAt: Long = 0L

    @Volatile
    private var lastSeenChannelName: String? = null

    @Volatile
    private var lastSeenVideoTitle: String? = null

    @Volatile
    private var lastApprovedVideoKey: String? = null

    @Volatile
    private var lastApprovedChannel: String? = null

    /**
     * Evaluates the current YouTube screen and returns the block decision.
     * Fully synchronous and in-memory so it completes within milliseconds.
     */
    fun evaluate(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>,
        blockUnknownContent: Boolean = true,
        currentTime: Long = System.currentTimeMillis()
    ): YouTubeBlockDecision {
        if (rootNode == null) return YouTubeBlockDecision.ALLOW_NAVIGATION

        // 1. SHORTS: every short is blocked instantly, regardless of channel or content.
        val isShorts = YouTubeNodeExtractor.detectShorts(rootNode, event)
        if (isShorts) {
            resetVideoTracking()
            return YouTubeBlockDecision.shorts()
        }

        // 2. Watch page detection
        val isWatchPage = YouTubeNodeExtractor.detectWatchPage(rootNode)
        if (!isWatchPage) {
            resetVideoTracking()
            return if (YouTubeNodeExtractor.isHomeFeed(rootNode)) {
                YouTubeBlockDecision.HOME_FEED
            } else {
                YouTubeBlockDecision.ALLOW_NAVIGATION
            }
        }

        // 2.1 AD DETECTION & BYPASS:
        // When an ad is actively playing, never block and do not disturb approved channel state.
        val isAd = YouTubeNodeExtractor.detectAd(rootNode)
        if (isAd) {
            return YouTubeBlockDecision(
                verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                reason = "YouTube Ad is playing"
            )
        }

        // 2.2 Record watch page active timestamp
        if (playbackStartedAt == 0L) {
            playbackStartedAt = currentTime
        }

        // 3. TITLE + CHANNEL MATCHING PIPELINE
        val title = YouTubeNodeExtractor.extractVideoTitle(rootNode)
        val channel = YouTubeNodeExtractor.extractChannelName(rootNode)
            ?: resolveChannelFromText(rootNode)

        if (title != null) lastSeenVideoTitle = title
        trackVideo(title, currentTime)

        // 3.1 ACTIVE SESSION LATCH: If this specific video was already verified and approved,
        // keep it approved while metadata is temporarily occluded.
        if (lastApprovedVideoKey != null && title != null && currentVideoKey == lastApprovedVideoKey && channel == null) {
            return YouTubeBlockDecision.approvedChannel(lastApprovedChannel ?: "Approved Channel")
        }

        if (channel != null) lastSeenChannelName = channel
        // Never allow a new or unverified video to inherit an earlier approved channel name
        val effectiveChannel = channel ?: if (currentVideoKey != null && currentVideoKey == lastApprovedVideoKey) lastSeenChannelName else null

        val approved = isContentApproved(title, effectiveChannel, approvedChannelIds, approvedChannelNames)
        if (approved) {
            lastApprovedVideoKey = currentVideoKey
            lastApprovedChannel = effectiveChannel ?: "Approved Channel"
            return YouTubeBlockDecision.approvedChannel(lastApprovedChannel ?: "Approved Channel")
        } else {
            // Unapproved channel positively identified -> block immediately!
            if (effectiveChannel != null) {
                return YouTubeBlockDecision.blockedChannel(effectiveChannel)
            }
        }

        // 4. Channel metadata not rendered yet: wait briefly, then treat as unknown.
        val withinGrace = (currentTime - currentVideoFirstSeenAt) < CHANNEL_METADATA_GRACE_MILLIS
        if (withinGrace) {
            return YouTubeBlockDecision(
                verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                reason = "Waiting for channel handle metadata"
            )
        }

        if (!blockUnknownContent) {
            return YouTubeBlockDecision(
                verdict = YouTubeBlockVerdict.ALLOW,
                reason = "Unknown content allowed by setting",
                detectedTitle = title
            )
        }
        return YouTubeBlockDecision.unknownContent(title, null)
    }

    private fun effectiveChannelOrFallback(channel: String?): String {
        return channel ?: lastSeenChannelName ?: "Approved Channel"
    }

    /**
     * Extracts a channel handle (@name) from the watch player subtree when the
     * dedicated channel view IDs expose nothing (e.g. plain-text channels).
     * Only scans the watch panel — never recommendations, comments, or descriptions.
     */
    private fun resolveChannelFromText(rootNode: AccessibilityNodeInfo): String? {
        val watchText = extractWatchPanelText(rootNode)
        if (watchText.isBlank()) return null
        val handleMatch = HANDLE_REGEX.find(watchText) ?: return null
        return YouTubeNodeExtractor.cleanChannelName(handleMatch.value)
    }

    private val HANDLE_REGEX = Regex("@[A-Za-z0-9_.\\-]{2,50}")

    private fun extractWatchPanelText(rootNode: AccessibilityNodeInfo): String {
        val watchIds = listOf(
            "channel_handle",
            "byline",
            "byline_text",
            "watch_metadata_byline",
            "watch_metadata_subtitle",
            "metadata_line",
            "watch_header_layout",
            "owner_layout",
            "channel_name",
            "owner_text",
            "owner_name",
            "channel_title"
        )
        val builder = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0
        while (queue.isNotEmpty() && visited < 100) {
            val node = queue.removeFirst()
            visited++
            val resId = node.viewIdResourceName ?: ""
            if (watchIds.any { resId.contains(it, ignoreCase = true) }) {
                node.text?.let { builder.append(it).append(" ") }
                node.contentDescription?.let { builder.append(it).append(" ") }
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }
        return builder.toString().trim()
    }

    /**
     * Tracks the currently playing video by title. A changed title means a new
     * video opened: the metadata grace window restarts and any channel remembered
     * from the previous video is discarded so it can never approve the new one.
     */
    private fun trackVideo(title: String?, currentTime: Long) {
        val videoKey = title?.trim()?.takeIf { it.isNotEmpty() }
        if (videoKey != null) {
            if (videoKey != currentVideoKey) {
                currentVideoKey = videoKey
                currentVideoFirstSeenAt = currentTime
                lastSeenChannelName = null
                lastApprovedVideoKey = null
                lastApprovedChannel = null
            }
        } else {
            if (currentVideoFirstSeenAt == 0L) {
                currentVideoFirstSeenAt = currentTime
            }
        }
    }

    private fun resetVideoTracking() {
        currentVideoKey = null
        currentVideoFirstSeenAt = 0L
        playbackStartedAt = 0L
        lastApprovedVideoKey = null
        lastApprovedChannel = null
    }

    /**
     * Clears all tracking state when YouTube is exited or a block is enforced.
     */
    fun reset() {
        resetVideoTracking()
        lastSeenChannelName = null
        lastSeenVideoTitle = null
        lastApprovedVideoKey = null
        lastApprovedChannel = null
    }

    /**
     * Evaluates whether the detected channel matches the user's approved study channels.
     * The video title is never used to judge approval — only verified channel handles and names.
     */
    fun isContentApproved(
        detectedTitle: String?,
        detectedChannel: String?,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>
    ): Boolean {
        if (approvedChannelIds.isEmpty() && approvedChannelNames.isEmpty()) return false
        if (detectedChannel.isNullOrBlank()) return false
        return isChannelApproved(detectedChannel, approvedChannelIds, approvedChannelNames)
    }

    /**
     * Checks whether the detected channel is whitelisted against the user's
     * approved study channels (handles and names).
     */
    fun isChannelApproved(
        detectedChannel: String,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>
    ): Boolean {
        if (approvedChannelIds.isEmpty() && approvedChannelNames.isEmpty()) return false

        val detected = detectedChannel.trim()
        val detectedHandle = normalizeHandle(detected)
        val detectedName = normalizeName(detected)
        val detectedWords = extractSignificantWords(detected)

        for (approved in approvedChannelIds) {
            val approvedHandle = normalizeHandle(approved)
            if (approvedHandle.isNotEmpty() && approvedHandle == detectedHandle) return true
            if (isFuzzyMatch(detectedHandle, approvedHandle)) return true
            if (isFuzzyMatch(detectedName, approvedHandle)) return true
            if (matchWords(detectedWords, extractSignificantWords(approved))) return true
        }

        for (approved in approvedChannelNames) {
            val approvedName = normalizeName(approved)
            if (approvedName.isNotEmpty() && approvedName == detectedName) return true
            if (isFuzzyMatch(detectedName, approvedName)) return true
            if (isFuzzyMatch(detectedHandle, approvedName)) return true
            if (matchWords(detectedWords, extractSignificantWords(approved))) return true
        }

        // Sub-channel / Brand alias matching:
        // E.g. PW-JEEWallah, PW-NEETWallah, PW Foundation belong to Physics Wallah
        val isPwDetected = detectedHandle.contains("pw") || detectedName.contains("pw") ||
                detectedHandle.contains("physicswallah") || detectedName.contains("physicswallah")
        if (isPwDetected) {
            val isPwApproved = approvedChannelIds.any {
                val h = normalizeHandle(it)
                h.contains("pw") || h.contains("physicswallah")
            } || approvedChannelNames.any {
                val n = normalizeName(it)
                n.contains("pw") || n.contains("physicswallah")
            }
            if (isPwApproved) return true
        }

        return false
    }

    private fun extractSignificantWords(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), " ")
            .split(" ")
            .filter { it.length >= 3 && it !in listOf("the", "and", "official", "channel", "video", "hindi", "english") }
    }

    private fun matchWords(wordsA: List<String>, wordsB: List<String>): Boolean {
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val overlap = wordsA.intersect(wordsB.toSet())
        // If the shorter list has all its words inside the other list, or at least 2 significant words match
        return (overlap.isNotEmpty() && overlap.size == minOf(wordsA.size, wordsB.size)) || overlap.size >= 2
    }

    private fun isFuzzyMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val shorter = minOf(a.length, b.length)
        if (shorter < MIN_FUZZY_MATCH_LENGTH) return false
        if (a.contains(b) || b.contains(a)) return true
        
        // Brand acronym expansion (e.g. pw -> physicswallah)
        val expandedA = a.replace("pw", "physicswallah")
        val expandedB = b.replace("pw", "physicswallah")
        if (expandedA.contains(expandedB) || expandedB.contains(expandedA)) return true
        
        return false
    }

    private fun normalizeHandle(value: String): String {
        var v = value.trim().lowercase(Locale.ROOT)
        if (v.startsWith("@")) v = v.removePrefix("@")
        return v.filter { it.isLetterOrDigit() }
    }

    private fun normalizeName(value: String): String {
        return value.trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }

    val lastDetectedChannel: String? get() = lastSeenChannelName
    val lastDetectedTitle: String? get() = lastSeenVideoTitle
}
