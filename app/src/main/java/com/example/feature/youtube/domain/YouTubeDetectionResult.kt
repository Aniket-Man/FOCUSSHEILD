package com.example.feature.youtube.domain

/**
 * Categorization of YouTube UI state detected by the accessibility parser.
 * Separates Application Navigation & Discovery surfaces from Active Content Consumption.
 */
enum class YouTubeContentType {
    NOT_YOUTUBE,
    YOUTUBE_NAVIGATION,
    YOUTUBE_HOME,
    SUBSCRIPTIONS,
    SEARCH_QUERY,
    SEARCH_RESULTS,
    CHANNEL_PAGE,
    VIDEO_PLAYBACK,
    VIDEO,
    SHORT,
    UNKNOWN
}

/**
 * Confidence level of the accessibility inspection.
 */
enum class DetectionConfidence {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Structured content state describing currently visible YouTube UI.
 * Purely declarative — does NOT make the final blocking policy decision.
 */
data class YouTubeDetectionResult(
    val contentType: YouTubeContentType = YouTubeContentType.UNKNOWN,
    val channelId: String? = null,
    val channelName: String? = null,
    val videoTitle: String? = null,
    val isShort: Boolean = false,
    val confidence: DetectionConfidence = DetectionConfidence.LOW,
    val matchedRule: String = ""
) {
    val isIdentifiableChannel: Boolean
        get() = !channelId.isNullOrBlank() || !channelName.isNullOrBlank()

    companion object {
        val NOT_YOUTUBE = YouTubeDetectionResult(
            contentType = YouTubeContentType.NOT_YOUTUBE,
            confidence = DetectionConfidence.HIGH,
            matchedRule = "package_mismatch"
        )

        val SHORTS_CONFIDENT = YouTubeDetectionResult(
            contentType = YouTubeContentType.SHORT,
            isShort = true,
            confidence = DetectionConfidence.HIGH,
            matchedRule = "shorts_signature"
        )

        val UNKNOWN_CONTENT = YouTubeDetectionResult(
            contentType = YouTubeContentType.UNKNOWN,
            isShort = false,
            confidence = DetectionConfidence.LOW,
            matchedRule = "unverified_fallback"
        )
    }
}
