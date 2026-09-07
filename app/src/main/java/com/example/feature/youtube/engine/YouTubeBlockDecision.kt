package com.example.feature.youtube.engine

/**
 * Verdict produced by the YouTube Content Block Engine for the current screen.
 */
enum class YouTubeBlockVerdict {
    ALLOW,
    ALLOW_NAVIGATION,
    HOME_FEED,
    BLOCK_SHORTS,
    BLOCK_UNAPPROVED_CHANNEL,
    BLOCK_UNKNOWN_CONTENT
}

/**
 * Result of evaluating the current YouTube screen against Study Mode rules,
 * approved channels, and user keyword lists.
 */
data class YouTubeBlockDecision(
    val verdict: YouTubeBlockVerdict,
    val reason: String,
    val detectedTitle: String? = null,
    val detectedChannel: String? = null,
    val matchedKeyword: String? = null,
    val isShorts: Boolean = false
) {
    companion object {
        val ALLOW_NAVIGATION = YouTubeBlockDecision(
            verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
            reason = "Navigation surface"
        )

        val HOME_FEED = YouTubeBlockDecision(
            verdict = YouTubeBlockVerdict.HOME_FEED,
            reason = "Home feed detected"
        )

        fun shorts(): YouTubeBlockDecision = YouTubeBlockDecision(
            verdict = YouTubeBlockVerdict.BLOCK_SHORTS,
            reason = "YouTube Shorts player detected",
            isShorts = true
        )

        fun approvedChannel(channel: String): YouTubeBlockDecision = YouTubeBlockDecision(
            verdict = YouTubeBlockVerdict.ALLOW,
            reason = "Approved study channel",
            detectedChannel = channel
        )

        fun blockedChannel(channel: String?, keyword: String? = null): YouTubeBlockDecision = YouTubeBlockDecision(
            verdict = YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL,
            reason = keyword?.let { "Matched block keyword: $it" } ?: "Channel not on approved list",
            detectedChannel = channel,
            matchedKeyword = keyword
        )

        fun blockedKeyword(title: String?, keyword: String): YouTubeBlockDecision = YouTubeBlockDecision(
            verdict = YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL,
            reason = "Matched block keyword: $keyword",
            detectedTitle = title,
            matchedKeyword = keyword
        )

        fun unknownContent(title: String?, channel: String?): YouTubeBlockDecision = YouTubeBlockDecision(
            verdict = YouTubeBlockVerdict.BLOCK_UNKNOWN_CONTENT,
            reason = "Content could not be verified against approved list",
            detectedTitle = title,
            detectedChannel = channel
        )
    }

    val isBlocking: Boolean
        get() = verdict == YouTubeBlockVerdict.BLOCK_SHORTS ||
                verdict == YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL ||
                verdict == YouTubeBlockVerdict.BLOCK_UNKNOWN_CONTENT
}
