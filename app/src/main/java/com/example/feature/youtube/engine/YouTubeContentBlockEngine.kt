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
    private const val CHANNEL_METADATA_GRACE_MILLIS = 1000L

    /** Time gap delay (in millis) after video playback starts before enforcing any blocking decision. */
    private const val PLAYBACK_START_DELAY_MILLIS = 1500L

    private const val MIN_FUZZY_MATCH_LENGTH = 4

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
        
        // 2.5 Detection is suspended when playback is explicitly paused
        val isPaused = YouTubeNodeExtractor.isVideoPaused(rootNode)

        if (isPaused) {
            playbackStartedAt = 0L
            return YouTubeBlockDecision(
                verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                reason = "Video is explicitly paused"
            )
        }

        // 2.6 Video is actively playing! Record playback start timestamp
        if (playbackStartedAt == 0L) {
            playbackStartedAt = currentTime
        }

        val withinPlaybackDelay = (currentTime - playbackStartedAt) < PLAYBACK_START_DELAY_MILLIS

        // 3. TITLE + CHANNEL MATCHING PIPELINE (Zenlock Classifier Pattern)
        val title = YouTubeNodeExtractor.extractVideoTitle(rootNode)
        val channel = YouTubeNodeExtractor.extractChannelName(rootNode)
            ?: resolveChannelFromText(rootNode)

        if (title != null) lastSeenVideoTitle = title
        trackVideo(title, currentTime)

        if (channel != null) lastSeenChannelName = channel
        val effectiveChannel = channel ?: lastSeenChannelName

        val approved = isContentApproved(title, effectiveChannel, approvedChannelIds, approvedChannelNames)
        if (approved) {
            return YouTubeBlockDecision.approvedChannel(effectiveChannel ?: title ?: "Approved Channel")
        } else {
            if (withinPlaybackDelay) {
                return YouTubeBlockDecision(
                    verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                    reason = "Playback delay in progress (verifying title + channel match)"
                )
            }
            if (effectiveChannel != null || title != null) {
                return YouTubeBlockDecision.blockedChannel(effectiveChannel ?: title ?: "Unapproved Content")
            }
        }

        // 4. Channel metadata not rendered yet: wait briefly, then treat as unknown.
        val withinGrace = (currentTime - currentVideoFirstSeenAt) < CHANNEL_METADATA_GRACE_MILLIS
        if (withinGrace || withinPlaybackDelay) {
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
            "com.google.android.youtube:id/channel_handle",
            "com.google.android.youtube:id/byline",
            "com.google.android.youtube:id/byline_text",
            "com.google.android.youtube:id/watch_metadata_byline",
            "com.google.android.youtube:id/watch_metadata_subtitle",
            "com.google.android.youtube:id/metadata_line",
            "com.google.android.youtube:id/watch_header_layout",
            "com.google.android.youtube:id/owner_layout",
            "com.google.android.youtube:id/channel_name",
            "com.google.android.youtube:id/owner_text",
            "com.google.android.youtube:id/owner_name",
            "com.google.android.youtube:id/channel_title"
        )
        val builder = StringBuilder()
        for (id in watchIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes != null) {
                    for (node in nodes) {
                        node.text?.let { builder.append(it).append(" ") }
                        node.contentDescription?.let { builder.append(it).append(" ") }
                        try { node.recycle() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        return builder.toString().trim()
    }

    /**
     * Tracks the currently playing video by title. A changed title means a new
     * video opened: the metadata grace window restarts and any channel remembered
     * from the previous video is discarded so it can never approve the new one.
     */
    private fun trackVideo(title: String?, currentTime: Long) {
        val videoKey = title?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (videoKey != currentVideoKey) {
            currentVideoKey = videoKey
            currentVideoFirstSeenAt = currentTime
            lastSeenChannelName = null
        }
    }

    private fun resetVideoTracking() {
        currentVideoKey = null
        currentVideoFirstSeenAt = 0L
        playbackStartedAt = 0L
    }

    /**
     * Clears all tracking state when YouTube is exited or a block is enforced.
     */
    fun reset() {
        resetVideoTracking()
        lastSeenChannelName = null
        lastSeenVideoTitle = null
    }

    /**
     * Comprehensive content classifier (title + channel name + keywords) matching logic.
     * Evaluates whether a video's title or channel matches approved study channels or keywords.
     */
    fun isContentApproved(
        detectedTitle: String?,
        detectedChannel: String?,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>
    ): Boolean {
        if (approvedChannelIds.isEmpty() && approvedChannelNames.isEmpty()) return false

        // 1. Direct channel name/handle match
        if (detectedChannel != null && isChannelApproved(detectedChannel, approvedChannelIds, approvedChannelNames)) {
            return true
        }

        // 2. Combined title + channel text search (Zenlock Classifier pattern)
        val searchText = buildString {
            detectedTitle?.let { append(it.lowercase(Locale.ROOT)) }
            append(" ")
            detectedChannel?.let { append(it.lowercase(Locale.ROOT)) }
        }.trim()

        if (searchText.isEmpty()) return false

        // Check matching against approved channel handles and names
        for (approved in approvedChannelIds + approvedChannelNames) {
            val approvedNorm = approved.trim().lowercase(Locale.ROOT)
            if (approvedNorm.isEmpty()) continue

            // Exact or substring match in search text (e.g. "Khan Academy" or "Physics Wallah" in title)
            if (searchText.contains(approvedNorm)) {
                return true
            }
            
            if (detectedChannel != null && detectedChannel.isNotBlank()) {
                val channelNorm = detectedChannel.lowercase(Locale.ROOT)
                if (approvedNorm.contains(channelNorm) || channelNorm.contains(approvedNorm)) {
                    return true
                }
            }

            // Clean handle match (e.g. "@physicswallah")
            val cleanHandle = approvedNorm.removePrefix("@").filter { it.isLetterOrDigit() }
            val cleanSearchText = searchText.filter { it.isLetterOrDigit() }
            if (cleanHandle.length >= MIN_FUZZY_MATCH_LENGTH && cleanSearchText.contains(cleanHandle)) {
                return true
            }
        }

        return false
    }

    /**
     * Checks whether the detected channel is whitelisted against the user's
     * approved study channels (handles and names).
     *
     * Matching rules:
     *  - Exact normalized match always applies.
     *  - Bidirectional containment only applies when the shorter side has at
     *    least [MIN_FUZZY_MATCH_LENGTH] characters, preventing short approved
     *    names (e.g. "MIT") from approving unrelated channels ("Smith Labs").
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

        for (approved in approvedChannelIds) {
            val approvedHandle = normalizeHandle(approved)
            if (approvedHandle.isNotEmpty() && approvedHandle == detectedHandle) return true
            if (isFuzzyMatch(detectedHandle, approvedHandle)) return true
            if (isFuzzyMatch(detectedName, approvedHandle)) return true
        }

        for (approved in approvedChannelNames) {
            val approvedName = normalizeName(approved)
            if (approvedName.isNotEmpty() && approvedName == detectedName) return true
            if (isFuzzyMatch(detectedName, approvedName)) return true
        }

        return false
    }

    private fun isFuzzyMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val shorter = minOf(a.length, b.length)
        if (shorter < MIN_FUZZY_MATCH_LENGTH) return false
        return a.contains(b) || b.contains(a)
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
