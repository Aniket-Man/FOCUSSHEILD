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

    /** Maximum time to wait for channel metadata while a pre-roll ad is actively playing and hiding the channel header. */
    private const val AD_MAX_WAIT_FOR_CHANNEL_MILLIS = 60_000L

    private const val MIN_FUZZY_MATCH_LENGTH = 3

    @Volatile
    private var currentVideoKey: String? = null

    @Volatile
    private var currentVideoFirstSeenAt: Long = 0L

    @Volatile
    private var playbackStartedAt: Long = 0L

    @Volatile
    private var wasAdPlayingOnLastTick: Boolean = false

    @Volatile
    private var lastSeenChannelName: String? = null

    @Volatile
    private var lastSeenVideoTitle: String? = null

    @Volatile
    private var lastApprovedVideoKey: String? = null

    @Volatile
    private var lastApprovedChannel: String? = null

    @Volatile
    var isCurrentlyOnApprovedWatchPage: Boolean = false
        private set

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

        // 2.1 Ad detection (Sponsor / Skip Ad / Ad countdown / Visit advertiser)
        val isAd = YouTubeNodeExtractor.detectAd(rootNode)

        // When a pre-roll ad finishes and transitions to the main video, reset the metadata grace timer
        // so the video's watch header has a full grace window to finish rendering.
        if (wasAdPlayingOnLastTick && !isAd) {
            currentVideoFirstSeenAt = currentTime
        }
        wasAdPlayingOnLastTick = isAd

        // 2.2 Record watch page active timestamp
        if (playbackStartedAt == 0L) {
            playbackStartedAt = currentTime
        }

        // 3. DUAL CHANNEL IDENTITY EXTRACTION (@handle under title + Channel Name from Subscribe/Avatar/Owner)
        val title = YouTubeNodeExtractor.extractVideoTitle(rootNode)
        val identity = YouTubeNodeExtractor.extractChannelIdentity(rootNode)
        val fallbackChannel = if (!identity.isIdentifiable) resolveChannelFromText(rootNode) else null

        if (title != null) lastSeenVideoTitle = title
        trackVideo(title, currentTime)

        val isHandleApproved = !identity.channelHandle.isNullOrBlank() &&
                isChannelApproved(identity.channelHandle, approvedChannelIds, approvedChannelNames)
        val isNameApproved = !identity.channelName.isNullOrBlank() &&
                isChannelApproved(identity.channelName, approvedChannelIds, approvedChannelNames)
        val isFallbackApproved = !fallbackChannel.isNullOrBlank() &&
                isChannelApproved(fallbackChannel, approvedChannelIds, approvedChannelNames)

        val hasPositiveChannelIdentification = identity.isIdentifiable || !fallbackChannel.isNullOrBlank()
        val isPositivelyApproved = isHandleApproved || isNameApproved || isFallbackApproved

        // 3.1 ACTIVE SESSION LATCH & SAFE PLAYBACK SANDBOX: If this specific video was already verified and approved,
        // or the Safe Playback Sandbox is active, keep it approved while metadata, scrolling, or mid-roll ads
        // temporarily occlude the watch header.
        // However, if a new unapproved channel is positively identified on screen, exit sandbox and never override it.
        val isSandboxActiveForPlayback = com.example.feature.youtube.detector.YouTubePlaybackSandbox.isSandboxActive
        val isLatchedApproved = !hasPositiveChannelIdentification && (
            (lastApprovedVideoKey != null && title != null && currentVideoKey == lastApprovedVideoKey) ||
            isSandboxActiveForPlayback
        )

        val approved = isPositivelyApproved || isLatchedApproved
        val displayChannel = when {
            isNameApproved -> identity.channelName
            isHandleApproved -> identity.channelName ?: identity.channelHandle
            isFallbackApproved -> fallbackChannel
            hasPositiveChannelIdentification -> identity.preferredDisplay ?: fallbackChannel
            isLatchedApproved -> lastApprovedChannel ?: lastSeenChannelName ?: com.example.feature.youtube.detector.YouTubePlaybackSandbox.sandboxedChannel?.channelName
            else -> null
        }
        if (displayChannel != null) {
            lastSeenChannelName = displayChannel
        }

        // 3.2 SMART AD DETECTION & HANDLING RULES:
        // - If an ad is playing on an APPROVED channel: ALLOW the ad to run so the video can play after the ad.
        // - If an ad is playing and the channel is already identified as UNAPPROVED: STOP and step back immediately.
        // - If a pre-roll ad temporarily hides channel metadata: ALLOW the ad until the channel is identified.
        if (isAd) {
            if (approved) {
                lastApprovedVideoKey = currentVideoKey
                lastApprovedChannel = displayChannel ?: lastApprovedChannel ?: "Approved Channel"
                isCurrentlyOnApprovedWatchPage = true
                return YouTubeBlockDecision.approvedChannel(
                    channel = lastApprovedChannel ?: "Approved Channel",
                    title = title,
                    isAdPlaying = true
                )
            } else if (hasPositiveChannelIdentification) {
                // Ad is playing on a positively identified UNAPPROVED channel -> stop ad & step back immediately!
                isCurrentlyOnApprovedWatchPage = false
                return YouTubeBlockDecision.blockedChannel(displayChannel ?: "Unapproved Channel")
            } else {
                // Pre-roll ad is hiding the channel header before the video starts -> allow ad while waiting for channel
                val withinAdWait = (currentTime - currentVideoFirstSeenAt) < AD_MAX_WAIT_FOR_CHANNEL_MILLIS
                if (withinAdWait) {
                    return YouTubeBlockDecision(
                        verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                        reason = "Pre-roll ad playing; waiting for channel identification",
                        detectedTitle = title,
                        isAdPlaying = true
                    )
                } else {
                    isCurrentlyOnApprovedWatchPage = false
                    return YouTubeBlockDecision.blockedChannel("Unapproved Video Ad")
                }
            }
        }

        // 3.3 NORMAL VIDEO PLAYBACK (No Ad):
        if (approved) {
            lastApprovedVideoKey = currentVideoKey
            lastApprovedChannel = displayChannel ?: lastApprovedChannel ?: "Approved Channel"
            isCurrentlyOnApprovedWatchPage = true
            com.example.feature.youtube.detector.YouTubePlaybackSandbox.enterSandbox(
                channel = com.example.feature.youtube.detector.ChannelIdentityInfo(
                    channelName = lastApprovedChannel,
                    channelHandle = identity.channelHandle
                ),
                title = title ?: lastSeenVideoTitle
            )
            return YouTubeBlockDecision.approvedChannel(
                channel = lastApprovedChannel ?: "Approved Channel",
                title = title,
                isAdPlaying = false
            )
        } else if (hasPositiveChannelIdentification) {
            // Unapproved channel positively identified -> exit sandbox and block
            com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
            isCurrentlyOnApprovedWatchPage = false
            return YouTubeBlockDecision.blockedChannel(displayChannel)
        }

        // 4. Channel metadata not rendered yet: wait briefly, then treat as unknown.
        val withinGrace = (currentTime - currentVideoFirstSeenAt) < CHANNEL_METADATA_GRACE_MILLIS
        if (withinGrace) {
            return YouTubeBlockDecision(
                verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                reason = "Waiting for channel handle metadata"
            )
        }

        isCurrentlyOnApprovedWatchPage = false
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
                isCurrentlyOnApprovedWatchPage = false
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
        wasAdPlayingOnLastTick = false
        lastApprovedVideoKey = null
        lastApprovedChannel = null
        isCurrentlyOnApprovedWatchPage = false
        com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
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
        isCurrentlyOnApprovedWatchPage = false
    }

    /**
     * Evaluates whether a dual WatchChannelIdentity (@handle + channelName) matches the user's approved channels.
     */
    fun isChannelIdentityApproved(
        identity: YouTubeNodeExtractor.WatchChannelIdentity,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>
    ): Boolean {
        val handleApproved = !identity.channelHandle.isNullOrBlank() &&
                isChannelApproved(identity.channelHandle, approvedChannelIds, approvedChannelNames)
        val nameApproved = !identity.channelName.isNullOrBlank() &&
                isChannelApproved(identity.channelName, approvedChannelIds, approvedChannelNames)
        return handleApproved || nameApproved
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
     * Checks whether the detected channel (either @handle or display name) is whitelisted
     * against the user's approved study channels (both handles and names).
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
        if (detectedHandle.isEmpty() && detectedName.isEmpty()) return false
        val detectedWords = extractSignificantWords(detected)

        for (approved in approvedChannelIds) {
            val approvedHandle = normalizeHandle(approved)
            if (approvedHandle.isNotEmpty() && (approvedHandle == detectedHandle || approvedHandle == detectedName)) return true
            if (isFuzzyMatch(detectedHandle, approvedHandle)) return true
            if (isFuzzyMatch(detectedName, approvedHandle)) return true
            if (matchWords(detectedWords, extractSignificantWords(approved))) return true
        }

        for (approved in approvedChannelNames) {
            val approvedName = normalizeName(approved)
            if (approvedName.isNotEmpty() && (approvedName == detectedName || approvedName == detectedHandle)) return true
            if (isFuzzyMatch(detectedName, approvedName)) return true
            if (isFuzzyMatch(detectedHandle, approvedName)) return true
            if (matchWords(detectedWords, extractSignificantWords(approved))) return true
        }

        // Sub-channel / Brand alias matching:
        // E.g. PW-JEEWallah, PW-NEETWallah, PW Foundation belong to Physics Wallah
        val isPwDetected = detectedWords.contains("pw") ||
                detectedHandle.startsWith("pw") ||
                detectedHandle.contains("physicswallah") ||
                detectedName.contains("physicswallah")
        if (isPwDetected) {
            val isPwApproved = approvedChannelIds.any {
                val h = normalizeHandle(it)
                val w = extractSignificantWords(it)
                w.contains("pw") || h.startsWith("pw") || h.contains("physicswallah")
            } || approvedChannelNames.any {
                val n = normalizeName(it)
                val w = extractSignificantWords(it)
                w.contains("pw") || n.startsWith("pw") || n.contains("physicswallah")
            }
            if (isPwApproved) return true
        }

        return false
    }

    private fun extractSignificantWords(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), " ")
            .split(" ")
            .filter { it.length >= 2 && it !in listOf("the", "and", "official", "channel", "video", "hindi", "english") }
    }

    private fun matchWords(wordsA: List<String>, wordsB: List<String>): Boolean {
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val overlap = wordsA.intersect(wordsB.toSet())
        return (overlap.isNotEmpty() && overlap.size == minOf(wordsA.size, wordsB.size) && overlap.any { it.length >= 3 }) ||
                overlap.count { it.length >= 3 } >= 2
    }

    private fun isFuzzyMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        if (shorter < 4) return false
        if (a.contains(b) || b.contains(a)) return true

        // Brand acronym expansion (e.g. pw -> physicswallah)
        val expandedA = if (a.startsWith("pw")) a.replaceFirst("pw", "physicswallah") else a
        val expandedB = if (b.startsWith("pw")) b.replaceFirst("pw", "physicswallah") else b
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
