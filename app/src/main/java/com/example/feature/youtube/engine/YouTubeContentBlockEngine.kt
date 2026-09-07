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

    private const val MIN_FUZZY_MATCH_LENGTH = 4

    @Volatile
    private var currentVideoKey: String? = null

    @Volatile
    private var currentVideoFirstSeenAt: Long = 0L

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

        // 3. CHANNEL-NAME-ONLY VERIFICATION
        // The title is extracted purely as a per-video tracking key (to reset the
        // metadata grace window and clear stale channels when a new video opens);
        // it never participates in the allow/block decision.
        val title = YouTubeNodeExtractor.extractVideoTitle(rootNode)
        val channel = YouTubeNodeExtractor.extractChannelName(rootNode)
            ?: resolveChannelFromText(rootNode)

        if (title != null) lastSeenVideoTitle = title
        trackVideo(title, currentTime)

        if (channel != null) lastSeenChannelName = channel
        val effectiveChannel = channel ?: lastSeenChannelName

        if (effectiveChannel != null) {
            return if (isChannelApproved(effectiveChannel, approvedChannelIds, approvedChannelNames)) {
                YouTubeBlockDecision.approvedChannel(effectiveChannel)
            } else {
                YouTubeBlockDecision.blockedChannel(effectiveChannel)
            }
        }

        // 4. Channel metadata not rendered yet: wait briefly, then treat as unknown.
        val withinGrace = currentTime - currentVideoFirstSeenAt < CHANNEL_METADATA_GRACE_MILLIS
        if (withinGrace) {
            return YouTubeBlockDecision(
                verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                reason = "Waiting for channel metadata"
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

    private val HANDLE_REGEX = Regex("@[A-Za-z0-9_.\\-]{3,40}")

    private fun extractWatchPanelText(rootNode: AccessibilityNodeInfo): String {
        val watchIds = listOf(
            "com.google.android.youtube:id/watch_panel",
            "com.google.android.youtube:id/watch_player",
            "com.google.android.youtube:id/watch_title_text",
            "com.google.android.youtube:id/watch_metadata_title",
            "com.google.android.youtube:id/watch_header_layout",
            "com.google.android.youtube:id/channel_name",
            "com.google.android.youtube:id/owner_text",
            "com.google.android.youtube:id/owner_name"
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
