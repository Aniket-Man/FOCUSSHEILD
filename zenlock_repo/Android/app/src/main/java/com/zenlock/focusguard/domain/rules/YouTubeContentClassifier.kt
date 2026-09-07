package com.zenlock.focusguard.domain.rules

import com.zenlock.focusguard.domain.model.ClassificationResult
import com.zenlock.focusguard.domain.model.ContentVerdict
import com.zenlock.focusguard.domain.repository.KeywordRepository

/**
 * Rule-based content classification engine for YouTube videos.
 *
 * Classification logic:
 * 1. Check if content is YouTube Shorts → always BLOCK
 * 2. Check video title/channel against block keywords → BLOCK if match
 * 3. Check video title/channel against allow keywords → ALLOW if match
 * 4. No match → UNKNOWN (defers to user's default setting)
 *
 * Block keywords are checked FIRST to be conservative (safety-first approach).
 */
class YouTubeContentClassifier(
    private val keywordRepository: KeywordRepository
) {
    // Cache keywords to avoid DB hits on every accessibility event
    private var allowKeywords: List<String> = emptyList()
    private var blockKeywords: List<String> = emptyList()
    private var channelKeywords: List<String> = emptyList()
    private var lastRefreshTime: Long = 0
    private val CACHE_DURATION_MS = 30_000L // Refresh cache every 30 seconds

    /**
     * Refreshes the keyword cache from the database if stale.
     */
    suspend fun refreshKeywordsIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime > CACHE_DURATION_MS) {
            allowKeywords = keywordRepository.getAllowKeywords().map { it.keyword.lowercase() }
            blockKeywords = keywordRepository.getBlockKeywords().map { it.keyword.lowercase() }
            channelKeywords = keywordRepository.getChannelKeywords().map { it.keyword.lowercase() }
            lastRefreshTime = now
        }
    }

    /**
     * Classify YouTube content based on extracted text from the UI.
     *
     * @param title The video title text (if available)
     * @param channelName The channel name (if available)
     * @param isShorts Whether Shorts UI pattern was detected
     * @return ClassificationResult with verdict and reasoning
     */
    suspend fun classify(
        title: String?,
        channelName: String?,
        isShorts: Boolean
    ): ClassificationResult {
        refreshKeywordsIfNeeded()

        // Rule 1: Shorts are always blocked during focus sessions
        if (isShorts) {
            return ClassificationResult(
                verdict = ContentVerdict.BLOCK,
                reason = "YouTube Shorts detected",
                isShorts = true
            )
        }

        val searchText = buildString {
            title?.let { append(it.lowercase()) }
            append(" ")
            channelName?.let { append(it.lowercase()) }
        }.trim()

        if (searchText.isEmpty()) {
            return ClassificationResult(
                verdict = ContentVerdict.UNKNOWN,
                reason = "No text available for classification"
            )
        }

        // Rule 1.5: Whitelisted Channels override everything (Education mode)
        if (channelName != null) {
            val lowerChannelName = channelName.lowercase()
            for (channel in channelKeywords) {
                if (lowerChannelName.contains(channel) || channel.contains(lowerChannelName)) {
                    return ClassificationResult(
                        verdict = ContentVerdict.ALLOW,
                        reason = "Whitelisted education channel: $channelName",
                        matchedKeyword = channel
                    )
                }
            }
        }

        // Rule 2: Check block keywords first (conservative approach)
        for (keyword in blockKeywords) {
            if (searchText.contains(keyword)) {
                return ClassificationResult(
                    verdict = ContentVerdict.BLOCK,
                    reason = "Matched block keyword: $keyword",
                    matchedKeyword = keyword
                )
            }
        }

        // Rule 3: Check allow keywords
        for (keyword in allowKeywords) {
            if (searchText.contains(keyword)) {
                return ClassificationResult(
                    verdict = ContentVerdict.ALLOW,
                    reason = "Matched allow keyword: $keyword",
                    matchedKeyword = keyword
                )
            }
        }

        // Rule 4: No match - return UNKNOWN
        return ClassificationResult(
            verdict = ContentVerdict.UNKNOWN,
            reason = "No keywords matched"
        )
    }

    /**
     * Force refresh keyword cache (e.g., when user updates keywords).
     */
    fun invalidateCache() {
        lastRefreshTime = 0
    }
}
