package com.example.core.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class YouTubeChannelSearchResult(
    val channelId: String,
    val channelName: String,
    val handle: String,
    val avatarUrl: String = "",
    val subscriberCount: String = "",
    val videoCount: String = "",
    val description: String = "",
    val isVerified: Boolean = false,
    val category: String = "Educational"
)

object YouTubeChannelSearchEngine {
    private const val TAG = "YouTubeSearchEngine"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // Comprehensive curated catalog for instant category browsing and offline fallback
    val CURATED_CATALOG: List<YouTubeChannelSearchResult> = listOf(
        // JEE & NEET & Foundation
        YouTubeChannelSearchResult(
            channelId = "@PhysicsWallah",
            channelName = "Physics Wallah - Alakh Pandey",
            handle = "@PhysicsWallah",
            avatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_k6P07VvjV81tP4z9K82vK9Y-xM5oA=s176-c-k-c0x00ffffff-no-rj",
            subscriberCount = "12.8M subscribers",
            description = "India's top online education platform for JEE, NEET, and CBSE board exams.",
            isVerified = true,
            category = "JEE / NEET"
        ),
        YouTubeChannelSearchResult(
            channelId = "@UnacademyJEE",
            channelName = "Unacademy JEE",
            handle = "@UnacademyJEE",
            avatarUrl = "https://unavatar.io/youtube/@UnacademyJEE",
            subscriberCount = "2.4M subscribers",
            description = "Comprehensive JEE Main & Advanced lectures and mock tests.",
            isVerified = true,
            category = "JEE / NEET"
        ),
        YouTubeChannelSearchResult(
            channelId = "@VedantuJEE",
            channelName = "Vedantu JEE",
            handle = "@VedantuJEE",
            avatarUrl = "https://unavatar.io/youtube/@VedantuJEE",
            subscriberCount = "1.8M subscribers",
            description = "Live interactive masterclasses for JEE preparations.",
            isVerified = true,
            category = "JEE / NEET"
        ),
        YouTubeChannelSearchResult(
            channelId = "@Competishun",
            channelName = "Mohit Tyagi (Competishun)",
            handle = "@MohitTyagi",
            avatarUrl = "https://unavatar.io/youtube/@MohitTyagi",
            subscriberCount = "1.1M subscribers",
            description = "Complete IIT-JEE Mathematics and Science foundational lectures.",
            isVerified = true,
            category = "JEE / NEET"
        ),
        YouTubeChannelSearchResult(
            channelId = "@MathonGo",
            channelName = "MathonGo",
            handle = "@MathonGo",
            avatarUrl = "https://unavatar.io/youtube/@MathonGo",
            subscriberCount = "750K subscribers",
            description = "JEE Main & Advanced Mathematics question breakdowns and PYQ analysis.",
            isVerified = true,
            category = "JEE / NEET"
        ),

        // Coding & Tech
        YouTubeChannelSearchResult(
            channelId = "@freecodecamp",
            channelName = "freeCodeCamp.org",
            handle = "@freecodecamp",
            avatarUrl = "https://unavatar.io/youtube/@freecodecamp",
            subscriberCount = "9.8M subscribers",
            description = "Full-length developer certification courses in Python, JS, SQL, Web, and AI.",
            isVerified = true,
            category = "Coding"
        ),
        YouTubeChannelSearchResult(
            channelId = "@chaiaurcode",
            channelName = "Chai aur Code",
            handle = "@chaiaurcode",
            avatarUrl = "https://unavatar.io/youtube/@chaiaurcode",
            subscriberCount = "750K subscribers",
            description = "Programming concepts, JavaScript, React, and Backend explained simply in Hindi.",
            isVerified = true,
            category = "Coding"
        ),
        YouTubeChannelSearchResult(
            channelId = "@CodeWithHarry",
            channelName = "CodeWithHarry",
            handle = "@CodeWithHarry",
            avatarUrl = "https://unavatar.io/youtube/@CodeWithHarry",
            subscriberCount = "5.5M subscribers",
            description = "Python, Web Development, DSA, and Android coding tutorials in Hindi.",
            isVerified = true,
            category = "Coding"
        ),
        YouTubeChannelSearchResult(
            channelId = "@ApnaCollegeOfficial",
            channelName = "Apna College",
            handle = "@ApnaCollegeOfficial",
            avatarUrl = "https://unavatar.io/youtube/@ApnaCollegeOfficial",
            subscriberCount = "5.2M subscribers",
            description = "Data Structures & Algorithms, Java, C++, and Full Stack Development.",
            isVerified = true,
            category = "Coding"
        ),
        YouTubeChannelSearchResult(
            channelId = "@TraversyMedia",
            channelName = "Traversy Media",
            handle = "@TraversyMedia",
            avatarUrl = "https://unavatar.io/youtube/@TraversyMedia",
            subscriberCount = "2.2M subscribers",
            description = "Practical web development crash courses, frameworks, and modern tooling.",
            isVerified = true,
            category = "Coding"
        ),
        YouTubeChannelSearchResult(
            channelId = "@Fireship",
            channelName = "Fireship",
            handle = "@Fireship",
            avatarUrl = "https://unavatar.io/youtube/@Fireship",
            subscriberCount = "3.4M subscribers",
            description = "High-intensity code tutorials and modern technology breakdowns.",
            isVerified = true,
            category = "Coding"
        ),
        YouTubeChannelSearchResult(
            channelId = "@ThePrimeTimeagen",
            channelName = "ThePrimeagen",
            handle = "@ThePrimeTimeagen",
            avatarUrl = "https://unavatar.io/youtube/@ThePrimeTimeagen",
            subscriberCount = "600K subscribers",
            description = "Algorithms, software engineering paradigms, and Vim mastery.",
            isVerified = true,
            category = "Coding"
        ),

        // Science & Math
        YouTubeChannelSearchResult(
            channelId = "@3blue1brown",
            channelName = "3Blue1Brown",
            handle = "@3blue1brown",
            avatarUrl = "https://unavatar.io/youtube/@3blue1brown",
            subscriberCount = "6.4M subscribers",
            description = "Visual mathematics, calculus, linear algebra, and neural networks with Grant Sanderson.",
            isVerified = true,
            category = "Math & Science"
        ),
        YouTubeChannelSearchResult(
            channelId = "@veritasium",
            channelName = "Veritasium",
            handle = "@veritasium",
            avatarUrl = "https://unavatar.io/youtube/@veritasium",
            subscriberCount = "16.5M subscribers",
            description = "An element of truth - videos about science, physics, education, and interesting phenomena.",
            isVerified = true,
            category = "Math & Science"
        ),
        YouTubeChannelSearchResult(
            channelId = "@kurzgesagt",
            channelName = "Kurzgesagt – In a Nutshell",
            handle = "@kurzgesagt",
            avatarUrl = "https://unavatar.io/youtube/@kurzgesagt",
            subscriberCount = "22.6M subscribers",
            description = "Beautiful animated scientific explanations of biology, astronomy, physics, and philosophy.",
            isVerified = true,
            category = "Math & Science"
        ),
        YouTubeChannelSearchResult(
            channelId = "@minutephysics",
            channelName = "MinutePhysics",
            handle = "@minutephysics",
            avatarUrl = "https://unavatar.io/youtube/@minutephysics",
            subscriberCount = "5.7M subscribers",
            description = "Simply explained physics and relativity concepts in quick, engaging hand-drawn sketches.",
            isVerified = true,
            category = "Math & Science"
        ),
        YouTubeChannelSearchResult(
            channelId = "@Numberphile",
            channelName = "Numberphile",
            handle = "@Numberphile",
            avatarUrl = "https://unavatar.io/youtube/@Numberphile",
            subscriberCount = "4.5M subscribers",
            description = "Deep dives into fascinating numbers, mathematical puzzles, and conjectures.",
            isVerified = true,
            category = "Math & Science"
        ),
        YouTubeChannelSearchResult(
            channelId = "@SteveMould",
            channelName = "Steve Mould",
            handle = "@SteveMould",
            avatarUrl = "https://unavatar.io/youtube/@SteveMould",
            subscriberCount = "3.2M subscribers",
            description = "Hands-on physics and chemistry experiments demonstrating strange science.",
            isVerified = true,
            category = "Math & Science"
        ),

        // Academics & Universities
        YouTubeChannelSearchResult(
            channelId = "@KhanAcademy",
            channelName = "Khan Academy",
            handle = "@KhanAcademy",
            avatarUrl = "https://unavatar.io/youtube/@KhanAcademy",
            subscriberCount = "8.3M subscribers",
            description = "Free, world-class education for anyone, anywhere: Math, Science, Humanities, SAT.",
            isVerified = true,
            category = "Academics"
        ),
        YouTubeChannelSearchResult(
            channelId = "@mitocw",
            channelName = "MIT OpenCourseWare",
            handle = "@mitocw",
            avatarUrl = "https://unavatar.io/youtube/@mitocw",
            subscriberCount = "5.3M subscribers",
            description = "Complete undergraduate and graduate lecture series from MIT professors.",
            isVerified = true,
            category = "Academics"
        ),
        YouTubeChannelSearchResult(
            channelId = "@stanfordonline",
            channelName = "Stanford Online",
            handle = "@stanfordonline",
            avatarUrl = "https://unavatar.io/youtube/@stanfordonline",
            subscriberCount = "1.9M subscribers",
            description = "Full Stanford lectures on Computer Science, AI, and Engineering.",
            isVerified = true,
            category = "Academics"
        ),
        YouTubeChannelSearchResult(
            channelId = "@crashcourse",
            channelName = "CrashCourse",
            handle = "@crashcourse",
            avatarUrl = "https://unavatar.io/youtube/@crashcourse",
            subscriberCount = "15.4M subscribers",
            description = "High-energy educational courses in World History, Biology, Literature, Psychology, and Economics.",
            isVerified = true,
            category = "Academics"
        ),
        YouTubeChannelSearchResult(
            channelId = "@TEDEd",
            channelName = "TED-Ed",
            handle = "@TEDEd",
            avatarUrl = "https://unavatar.io/youtube/@TEDEd",
            subscriberCount = "19.5M subscribers",
            description = "Short, animated educational lessons crafted by world-class educators and animators.",
            isVerified = true,
            category = "Academics"
        ),
        YouTubeChannelSearchResult(
            channelId = "@nptelhrd",
            channelName = "NPTEL-NOC IITM",
            handle = "@nptelhrd",
            avatarUrl = "https://unavatar.io/youtube/@nptelhrd",
            subscriberCount = "2.8M subscribers",
            description = "Official national video courses across engineering, sciences, and humanities by IITs & IISc.",
            isVerified = true,
            category = "Academics"
        ),

        // UPSC & Civil Services
        YouTubeChannelSearchResult(
            channelId = "@StudyIQEducation",
            channelName = "StudyIQ Education",
            handle = "@StudyIQEducation",
            avatarUrl = "https://unavatar.io/youtube/@StudyIQEducation",
            subscriberCount = "16.1M subscribers",
            description = "Daily current affairs, international relations, and UPSC Civil Services exam preparation.",
            isVerified = true,
            category = "UPSC & Govt"
        ),
        YouTubeChannelSearchResult(
            channelId = "@DrishtiIASvideos",
            channelName = "Drishti IAS",
            handle = "@DrishtiIASvideos",
            avatarUrl = "https://unavatar.io/youtube/@DrishtiIASvideos",
            subscriberCount = "12.3M subscribers",
            description = "India's premier UPSC CSE preparation institute lectures and editorial analysis.",
            isVerified = true,
            category = "UPSC & Govt"
        ),
        YouTubeChannelSearchResult(
            channelId = "@VisionIAS_UPSC",
            channelName = "Vision IAS",
            handle = "@VisionIAS_UPSC",
            avatarUrl = "https://unavatar.io/youtube/@VisionIAS_UPSC",
            subscriberCount = "2.1M subscribers",
            description = "UPSC prelims and mains strategy, GS paper discussions, and topper talks.",
            isVerified = true,
            category = "UPSC & Govt"
        ),

        // Medical & Biology
        YouTubeChannelSearchResult(
            channelId = "@osmosis",
            channelName = "Osmosis from Elsevier",
            handle = "@osmosis",
            avatarUrl = "https://unavatar.io/youtube/@osmosis",
            subscriberCount = "3.2M subscribers",
            description = "Illustrated medical, pathology, pharmacology, and physiology videos for healthcare students.",
            isVerified = true,
            category = "Medical"
        ),
        YouTubeChannelSearchResult(
            channelId = "@NinjaNerdLectures",
            channelName = "Ninja Nerd",
            handle = "@NinjaNerdLectures",
            avatarUrl = "https://unavatar.io/youtube/@NinjaNerdLectures",
            subscriberCount = "3.1M subscribers",
            description = "In-depth whiteboard medical science and anatomy lectures by Zach Murphy.",
            isVerified = true,
            category = "Medical"
        ),
        YouTubeChannelSearchResult(
            channelId = "@ArmandoHasudungan",
            channelName = "Armando Hasudungan",
            handle = "@ArmandoHasudungan",
            avatarUrl = "https://unavatar.io/youtube/@ArmandoHasudungan",
            subscriberCount = "2.4M subscribers",
            description = "Hand-drawn biological systems, immunology, endocrinology, and cardiology tutorials.",
            isVerified = true,
            category = "Medical"
        ),

        // Productivity & Deep Learning
        YouTubeChannelSearchResult(
            channelId = "@hubermanlab",
            channelName = "Andrew Huberman",
            handle = "@hubermanlab",
            avatarUrl = "https://unavatar.io/youtube/@hubermanlab",
            subscriberCount = "5.9M subscribers",
            description = "Neuroscience protocols, focus, learning, memory, and cognitive performance.",
            isVerified = true,
            category = "Self-Improvement"
        ),
        YouTubeChannelSearchResult(
            channelId = "@aliabdaal",
            channelName = "Ali Abdaal",
            handle = "@aliabdaal",
            avatarUrl = "https://unavatar.io/youtube/@aliabdaal",
            subscriberCount = "5.6M subscribers",
            description = "Evidence-based study techniques, active recall, spaced repetition, and productivity.",
            isVerified = true,
            category = "Self-Improvement"
        )
    )

    /**
     * Live search for YouTube channels by query.
     * Uses public YouTube search filter with fallback to curated library and query normalization.
     */
    suspend fun searchChannels(query: String, categoryFilter: String = "All"): List<YouTubeChannelSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()

        // 1. If query is empty, return curated catalog filtered by category
        if (trimmed.isEmpty()) {
            return@withContext if (categoryFilter == "All") {
                CURATED_CATALOG
            } else {
                CURATED_CATALOG.filter { it.category.equals(categoryFilter, ignoreCase = true) }
            }
        }

        val results = mutableListOf<YouTubeChannelSearchResult>()
        val seenIds = mutableSetOf<String>()

        // 2. Perform live network YouTube Channel search (Direct In-App without API key)
        try {
            val liveNetworkResults = fetchYouTubeChannelsFromWeb(trimmed)
            for (res in liveNetworkResults) {
                val key = res.channelId.lowercase()
                if (seenIds.add(key)) {
                    results.add(res)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Live search web query encountered error, using local matcher: ${e.message}")
        }

        // 3. Match against curated catalog for query keywords
        val queryLower = trimmed.lowercase()
        val localMatches = CURATED_CATALOG.filter {
            it.channelName.lowercase().contains(queryLower) ||
                    it.handle.lowercase().contains(queryLower) ||
                    it.category.lowercase().contains(queryLower) ||
                    it.description.lowercase().contains(queryLower)
        }
        for (match in localMatches) {
            val key = match.channelId.lowercase()
            if (seenIds.add(key)) {
                results.add(match)
            }
        }

        // 4. Always provide an instant custom-add item if not already found
        val isExactHandle = trimmed.startsWith("@") || trimmed.startsWith("UC")
        val cleanHandle = if (trimmed.startsWith("@")) trimmed else "@${trimmed.replace(" ", "")}"
        val hasExactHandleMatch = results.any { it.handle.equals(cleanHandle, ignoreCase = true) }

        if (!hasExactHandleMatch && trimmed.length >= 2) {
            val customItem = YouTubeChannelSearchResult(
                channelId = cleanHandle,
                channelName = if (isExactHandle) trimmed.removePrefix("@") else trimmed,
                handle = cleanHandle,
                avatarUrl = "https://unavatar.io/youtube/$cleanHandle",
                subscriberCount = "Custom Study Channel",
                description = "Custom whitelisted YouTube channel: $cleanHandle",
                isVerified = false,
                category = "Custom"
            )
            // Put custom channel item at end of search results
            results.add(customItem)
        }

        // Filter by category if specific category is selected
        if (categoryFilter != "All") {
            val categoryFiltered = results.filter {
                it.category.equals(categoryFilter, ignoreCase = true) || it.category == "Custom"
            }
            if (categoryFiltered.isNotEmpty()) return@withContext categoryFiltered
        }

        results
    }

    /**
     * Queries YouTube's public search endpoint filtered to channels (sp=EgIQAg%3D%3D)
     * and extracts channelRenderer metadata from ytInitialData.
     */
    private fun fetchYouTubeChannelsFromWeb(query: String): List<YouTubeChannelSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        // sp=EgIQAg%253D%253D filters search exclusively to Type: Channel
        val url = "https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAg%253D%253D"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            return emptyList()
        }

        val html = response.body?.string() ?: return emptyList()
        return parseYtInitialDataForChannels(html)
    }

    /**
     * Extracts and parses the ytInitialData JSON structure embedded in YouTube HTML response.
     */
    private fun parseYtInitialDataForChannels(html: String): List<YouTubeChannelSearchResult> {
        val results = mutableListOf<YouTubeChannelSearchResult>()

        try {
            // Locate ytInitialData in the HTML script tag
            val jsonString = extractJsonFromHtml(html) ?: return emptyList()
            val root = JSONObject(jsonString)

            // Traverse YouTube contents hierarchy
            val contents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer") ?: continue
                val items = section.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val channelRenderer = item.optJSONObject("channelRenderer") ?: continue

                    val channelId = channelRenderer.optString("channelId")
                    if (channelId.isBlank()) continue

                    // Channel Name
                    val titleObj = channelRenderer.optJSONObject("title")
                    val title = titleObj?.optString("simpleText")
                        ?: titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: "Channel"

                    // Handle / Canonical URL
                    val navUrl = channelRenderer.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("commandMetadata")
                        ?.optJSONObject("webCommandMetadata")
                        ?.optString("url") ?: ""
                    val handle = when {
                        navUrl.contains("/@") -> "@" + navUrl.substringAfter("/@").substringBefore("/")
                        navUrl.startsWith("/channel/") -> "@" + title.replace(" ", "")
                        channelRenderer.optJSONObject("subscriberCountText")?.optString("simpleText")?.startsWith("@") == true ->
                            channelRenderer.optJSONObject("subscriberCountText")?.optString("simpleText") ?: "@$channelId"
                        else -> "@" + title.replace(" ", "")
                    }

                    // Avatar Thumbnail (choose the highest quality available)
                    val thumbnails = channelRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    var avatarUrl = ""
                    if (thumbnails != null && thumbnails.length() > 0) {
                        val lastThumb = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
                        avatarUrl = if (lastThumb.startsWith("//")) "https:$lastThumb" else lastThumb
                    }
                    if (avatarUrl.isBlank() && handle.startsWith("@")) {
                        avatarUrl = "https://unavatar.io/youtube/$handle"
                    }

                    // Subscriber count & video count
                    val subCount = channelRenderer.optJSONObject("subscriberCountText")?.optString("simpleText")
                        ?: channelRenderer.optJSONObject("videoCountText")?.optString("simpleText")
                        ?: ""

                    // Description snippet
                    val descRuns = channelRenderer.optJSONObject("descriptionSnippet")?.optJSONArray("runs")
                    val descBuilder = StringBuilder()
                    if (descRuns != null) {
                        for (k in 0 until descRuns.length()) {
                            descBuilder.append(descRuns.optJSONObject(k)?.optString("text") ?: "")
                        }
                    }

                    // Verified badge check
                    var isVerified = false
                    val badges = channelRenderer.optJSONArray("ownerBadges")
                    if (badges != null) {
                        for (k in 0 until badges.length()) {
                            val badgeStyle = badges.optJSONObject(k)
                                ?.optJSONObject("metadataBadgeRenderer")
                                ?.optString("style")
                            if (badgeStyle?.contains("VERIFIED") == true) {
                                isVerified = true
                                break
                            }
                        }
                    }

                    results.add(
                        YouTubeChannelSearchResult(
                            channelId = if (handle.startsWith("@")) handle else channelId,
                            channelName = title,
                            handle = handle,
                            avatarUrl = avatarUrl,
                            subscriberCount = subCount,
                            description = descBuilder.toString().trim(),
                            isVerified = isVerified,
                            category = "YouTube Search"
                        )
                    )

                    if (results.size >= 15) break
                }
                if (results.size >= 15) break
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing ytInitialData: ${e.message}")
        }

        return results
    }

    private fun extractJsonFromHtml(html: String): String? {
        val pattern = Pattern.compile("var ytInitialData\\s*=\\s*(\\{.+?\\});</script>", Pattern.DOTALL)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }

        val patternAlt = Pattern.compile("window\\[\"ytInitialData\"\\]\\s*=\\s*(\\{.+?\\});</script>", Pattern.DOTALL)
        val matcherAlt = patternAlt.matcher(html)
        if (matcherAlt.find()) {
            return matcherAlt.group(1)
        }

        return null
    }
}
