package com.example.feature.youtube.detector

import java.util.Locale

/**
 * Centralized declarative selectors, strings, view IDs, and heuristics for YouTube detection.
 * Isolates fragile accessibility strings in one place so future UI shifts don't require rewriting engine logic.
 *
 * Clearly separates:
 * 1. Active Shorts Playback vs Shorts Shelves / Carousels
 * 2. Active Normal Video Playback vs Recommendation Lists & Home Feed Cards
 * 3. In-App Miniplayer / Floaty Bar (small minimized video on bottom right)
 * 4. Channel Identity & Handles
 * 5. Navigation & Search surfaces
 */
object YouTubeDetectionRules {

    val YOUTUBE_PACKAGES: Set<String> = setOf(
        "com.google.android.youtube",
        "com.google.android.youtube.tv",
        "com.google.android.apps.youtube.kids",
        "com.google.android.apps.youtube.mango",
        "com.google.android.youtube.googletv"
    )

    // =========================================================================
    // 1. SHORTS DETECTION SELECTORS & MARKERS
    // =========================================================================

    /**
     * View IDs or resource name substrings uniquely indicating an ACTIVE YouTube Shorts Player.
     * Note: "shorts_shelf" and "reel_shelf" are intentionally excluded here because shelves appear on Home/Search.
     */
    val ACTIVE_SHORTS_PLAYER_VIEW_IDS: List<String> = listOf(
        "reel_player_page_container",
        "reel_player_view",
        "reel_player_page_view",
        "reel_player_overlay",
        "reel_player_fragment",
        "reel_video_player",
        "reel_watch_fragment",
        "shorts_player_fragment",
        "shorts_player_view",
        "reel_player_container"
    )

    /**
     * Action button descriptors or accessibility labels that exist ONLY in active Shorts player.
     */
    val ACTIVE_SHORTS_ACTION_DESCRIPTIONS: List<String> = listOf(
        "Dislike this Short",
        "Like this Short",
        "Remix this Short",
        "Sound used in this Short",
        "Create a Short with this sound",
        "Share this Short",
        "Remix with this audio",
        "Shorts sound",
        "Shorts remix"
    )

    /**
     * View IDs representing the bottom navigation bar / pivot bar item for Shorts tab.
     */
    val SHORTS_TAB_PIVOT_IDS: List<String> = listOf(
        "pivot_shorts",
        "pivot_bar_shorts_tab",
        "tab_shorts",
        "shorts_pivot_item"
    )

    /**
     * Identifiers representing Shorts Shelves / Carousels / Grids.
     * These indicate that Shorts are present as thumbnails/cards on Home, Search, or Channel pages,
     * but the user is NOT actively consuming or watching a Short.
     */
    val SHORTS_SHELF_VIEW_IDS: List<String> = listOf(
        "shorts_shelf",
        "reel_shelf",
        "shorts_grid",
        "shorts_carousel",
        "rich_shelf_header",
        "reel_shelf_layout",
        "shorts_shelf_container"
    )

    val SHORTS_SHELF_HEADER_TEXTS: List<String> = listOf(
        "Shorts",
        "YouTube Shorts",
        "Latest Shorts",
        "Popular Shorts"
    )

    // =========================================================================
    // 2. NORMAL WATCH PLAYER & VIDEO CONTENT SELECTORS
    // =========================================================================

    /**
     * View IDs representing the main full-screen/expanded video watch container or active player subtree.
     * Note: miniplayers and feed layouts are strictly excluded.
     */
    val ACTIVE_WATCH_PLAYER_VIEW_IDS: List<String> = listOf(
        "watch_panel",
        "watch_player",
        "watch_title_text",
        "watch_metadata_title",
        "watch_header_layout",
        "watch_swipey_item",
        "player_view"
    )

    /**
     * View IDs specifically identifying the in-app bottom-right miniplayer (docked player).
     */
    val MINIPLAYER_VIEW_IDS: List<String> = listOf(
        "miniplayer",
        "floaty_bar",
        "miniplayer_view",
        "mini_player_container",
        "floaty_box",
        "miniplayer_control",
        "docked_player",
        "miniplayer_root",
        "miniplayer_layout",
        "inline_player",
        "touch_area"
    )

    /**
     * View IDs for closing / dismissing the in-app miniplayer.
     */
    val MINIPLAYER_CLOSE_BUTTON_IDS: List<String> = listOf(
        "close_button",
        "miniplayer_close",
        "floaty_close",
        "dismiss_button",
        "close_miniplayer",
        "player_close_button",
        "controls_overlay_close_button",
        "close_video",
        "dismiss_miniplayer",
        "touch_area"
    )

    val MINIPLAYER_CLOSE_DESCRIPTIONS: List<String> = listOf(
        "Close",
        "Close player",
        "Dismiss miniplayer",
        "Close video",
        "Dismiss",
        "Close floating player",
        "Close playback",
        "Close mini-player"
    )

    /**
     * View IDs specifically holding the title of the video currently playing on the watch page.
     */
    val VIDEO_TITLE_VIEW_IDS: List<String> = listOf(
        "watch_title_text",
        "watch_metadata_title",
        "title_and_badge",
        "headline_title"
    )

    /**
     * View IDs containing channel metadata near the active player, ordered by specificity.
     */
    val CHANNEL_IDENTITY_VIEW_IDS: List<String> = listOf(
        "channel_name",
        "owner_name",
        "owner_text",
        "channel_title",
        "channel_avatar",
        "channel_header",
        "channel_handle",
        "byline",
        "subtitle",
        "channel_sub_text",
        "upload_info"
    )

    /**
     * View IDs for elements that are OUTSIDE the active video (feed items, recommendations, comments, related feeds).
     * Scanning must NEVER extract channels from these containers!
     */
    val EXCLUDED_RECOMMENDATION_VIEW_IDS: List<String> = listOf(
        "related_videos_container",
        "related_chip_cloud",
        "suggested_videos_list",
        "recommendation_list",
        "comments_container",
        "comment_thread",
        "comment_item",
        "comment_author",
        "comment_sheet",
        "comment_list",
        "comments",
        "comment",
        "reply",
        "replies",
        "engagement_panel",
        "live_chat",
        "chat_item",
        "chat_message",
        "chat_author",
        "description_sheet",
        "bottom_sheet",
        "panel_header",
        "panel_content",
        "transcript",
        "donation",
        "membership",
        "author_text",
        "feed_container",
        "home_feed",
        "rich_grid",
        "rich_item_renderer",
        "video_card",
        "section_list",
        "results_list",
        "search_results"
    )

    // =========================================================================
    // 3. NAVIGATION & SEARCH SURFACES
    // =========================================================================

    /**
     * View IDs indicating Search query input box, voice search, and filters.
     */
    val SEARCH_INPUT_VIEW_IDS: List<String> = listOf(
        "search_edit_text",
        "search_query",
        "search_box",
        "action_search",
        "search_container",
        "search_clear",
        "voice_search_button"
    )

    /**
     * View IDs indicating Search results list / feed.
     */
    val SEARCH_RESULTS_VIEW_IDS: List<String> = listOf(
        "search_results",
        "results_list",
        "search_filter",
        "search_suggestion_list",
        "search_results_container"
    )

    /**
     * Text keywords indicating Search query or results.
     */
    val SEARCH_TEXT_KEYWORDS: List<String> = listOf(
        "Search YouTube",
        "Search",
        "Clear search query",
        "Voice search",
        "Filters",
        "Search results"
    )

    /**
     * View IDs indicating Channel navigation / channel tabs.
     */
    val CHANNEL_PAGE_VIEW_IDS: List<String> = listOf(
        "channel_header",
        "channel_page",
        "channel_layout",
        "tab_layout",
        "videos_tab",
        "playlists_tab",
        "community_tab",
        "about_tab",
        "channel_profile"
    )

    /**
     * View IDs indicating Home feed / general navigation.
     */
    val HOME_NAVIGATION_VIEW_IDS: List<String> = listOf(
        "home_feed",
        "feed_container",
        "pivot_home",
        "pivot_subscriptions",
        "pivot_you",
        "pane_fragment_container",
        "bottom_navigation",
        "pivot_bar"
    )

    // =========================================================================
    // 4. HELPER UTILITIES & CHANNEL NAME VALIDATION
    // =========================================================================

    private val VIEW_KEYWORDS = listOf(
        "view", "views", "viewers", "watching",
        "visualizaciones", "aufrufe", "vues", "visualizações", "vistas",
        "просмотр", "조회수", "次視聴", "次观看"
    )

    private val STATS_METRIC_REGEX = Regex(
        """(?i)^\s*(\d+[\d,.]*\s*(k|m|b|t|q|lakh|crore|cr|%|thousand|million|billion)?|no|0)\s*$"""
    )

    private val TIME_AGO_REGEX = Regex(
        """(?i).*\b(ago|yesterday|just now|streamed|premiered|premieres|scheduled|live)\b.*"""
    )

    private val DURATION_REGEX = Regex(
        """^\d{1,2}:\d{2}(:\d{2})?$"""
    )

    private val UI_ACTION_LABELS = setOf(
        "subscribe", "subscribed", "join", "download", "save", "share",
        "like", "dislike", "remix", "thanks", "clip", "comments",
        "live chat", "description", "transcript", "show more", "show less",
        "auto-generated by youtube", "music in this video", "more", "search",
        "filters", "home", "shorts", "subscriptions", "library", "you",
        "all", "today", "continue watching", "play all", "report", "no",
        "close", "dismiss", "expand", "minimize", "pause", "play"
    )

    /**
     * Returns true if the given text candidate represents YouTube video statistics (views, upload date),
     * action buttons, subscriber counts, or system UI labels rather than an authentic channel name.
     */
    fun isInvalidChannelName(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return true
        val trimmed = candidate.trim()
        if (trimmed.length < 2 || trimmed.length > 70) return true
        if (trimmed.startsWith("@")) return true // Handles are processed separately

        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Matches view count / viewing words e.g. "1.2M views", "100K views", "No views", "1 view", "12 lakh views"
        for (viewWord in VIEW_KEYWORDS) {
            if (lower.contains(viewWord)) {
                return true
            }
        }

        // 2. Relative timestamps and stream indicators e.g. "2 days ago", "Streamed 3 days ago"
        if (TIME_AGO_REGEX.matches(lower)) return true

        // 3. Video duration e.g. "12:34"
        if (DURATION_REGEX.matches(trimmed)) return true

        // 4. Pure metrics, numbers or subscriber strings without channel name
        if (lower.contains("subscriber") || lower.contains("subscribers") || lower.contains(" subs")) {
            return true
        }
        if (STATS_METRIC_REGEX.matches(trimmed)) {
            return true
        }

        // 5. Standard action button / UI label
        if (UI_ACTION_LABELS.contains(lower)) return true

        // 6. Must contain at least one letter character
        if (!trimmed.any { it.isLetter() }) return true

        return false
    }

    /**
     * Extracts and validates an authentic channel name from compound YouTube UI strings,
     * stripping out subscriber counts, view counts, and timestamps.
     * Returns null if the string only contains video metadata or views.
     */
    fun extractAndValidateChannelName(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val trimmed = rawText.trim()
        if (trimmed.length < 2 || trimmed.length > 100) return null
        if (trimmed.startsWith("@")) return null

        // Check if string contains separator delimiters (•, ·, |, -)
        val delimiters = listOf("•", "·", "|", " - ")
        val matchedDelimiter = delimiters.firstOrNull { trimmed.contains(it) }

        if (matchedDelimiter != null) {
            val parts = trimmed.split(matchedDelimiter).map { it.trim() }
            for (part in parts) {
                if (part.isNotBlank() && !isInvalidChannelName(part)) {
                    val cleaned = part.removePrefix("By ").removePrefix("by ").trim()
                    if (!isInvalidChannelName(cleaned)) {
                        return cleaned
                    }
                }
            }
            return null
        }

        // Plain string without delimiter: validate directly
        if (isInvalidChannelName(trimmed)) {
            return null
        }

        val cleaned = trimmed.removePrefix("By ").removePrefix("by ").trim()
        return if (!isInvalidChannelName(cleaned)) cleaned else null
    }

    /**
     * Checks whether a package belongs to any known YouTube variant.
     */
    fun isYouTubePackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return YOUTUBE_PACKAGES.contains(packageName.trim())
    }

    /**
     * Normalizes a channel name or handle for fuzzy & tolerant matching.
     */
    fun normalizeString(str: String?): String {
        if (str.isNullOrBlank()) return ""
        return str.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }
}
