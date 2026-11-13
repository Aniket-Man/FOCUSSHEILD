package com.example.feature.youtube.ai

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.FocusShieldApp
import com.example.feature.youtube.engine.YouTubeContentBlockEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Context-Aware YouTube AI Study Mode Filter Policy.
 */
enum class YouTubeAiFilterMode(val displayName: String, val subtitle: String) {
    SMART_HYBRID(
        displayName = "Smart Hybrid",
        subtitle = "Matches active session subject when set; otherwise allows any educational study video"
    ),
    STRICT_SUBJECT(
        displayName = "Strict Subject Match",
        subtitle = "Only allows videos directly related to your active session subject & topic"
    ),
    GENERAL_ACADEMIC(
        displayName = "Any Educational",
        subtitle = "Allows any academic lecture, tutorial, or coding/science video; blocks entertainment"
    )
}

/**
 * Identifies which layer produced the YouTube verdict and its latency profile.
 */
enum class YouTubeAiVerdictSource(val badgeLabel: String) {
    CHANNEL_CACHE("Learned Channel • 0ms"),
    SESSION_BYPASS("Allowed This Session • 0ms"),
    CHANNEL_OVERRIDE("Channel Override • 0ms"),
    KEYWORD_RULE("Keyword Rule • 0ms"),
    CACHE_HIT("Cached Verdict • 0ms"),
    LOCAL_FAST_HEURISTIC("Instant Heuristic • 0ms"),
    AI_PREWARMED("Pre-Warmed AI • 0ms"),
    AI_LIVE("Gemini Flash-Lite"),
    OFFLINE_FALLBACK("Offline Smart Fallback • 0ms")
}

/**
 * Tracks real-world AI credit efficiency across the Auto-Pilot Channel Guard.
 */
data class AiGuardStats(
    val totalChecks: Int = 0,
    val savedAiCalls: Int = 0
) {
    val savedPercent: Int
        get() = if (totalChecks <= 0) 0 else ((savedAiCalls * 100f) / totalChecks).toInt().coerceIn(0, 100)
}

/**
 * Immutable classification verdict for a YouTube channel / video.
 */
data class YouTubeAiVerdict(
    val videoKey: String,
    val videoTitle: String,
    val channelName: String,
    val sessionSubject: String,
    val filterMode: YouTubeAiFilterMode,
    val isAllowed: Boolean,
    val category: String,
    val reason: String,
    val latencyMs: Long,
    val source: YouTubeAiVerdictSource,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Synchronous return value from [YouTubeAiStudyClassifier.evaluateWatchVideo] for the AccessibilityService loop.
 */
sealed interface YouTubeAiEvaluationOutcome {
    data class Decided(val verdict: YouTubeAiVerdict) : YouTubeAiEvaluationOutcome
    data class VerifyingAsync(val videoKey: String, val startedAt: Long) : YouTubeAiEvaluationOutcome
}

/**
 * Ultra-Low-Latency Channel-Learning YouTube Study Guard.
 *
 * Classifies unseen YouTube channels once as Educational vs Entertainment using
 * `gemini-3.5-flash-lite` and persists the result into Room (`study_channels`) so all
 * subsequent visits to any video from that channel resolve locally in 0ms with 0 AI credits,
 * paired with an instant 0ms obvious-distraction safety check on mixed-content channels.
 */
object YouTubeAiStudyClassifier {

    private const val TAG = "YouTubeAiClassifier"
    private const val PREFS_NAME = "youtube_ai_verdict_cache"
    private const val KEY_FILTER_MODE = "ai_filter_mode"
    private const val KEY_CACHED_VERDICTS = "cached_verdicts_v1"
    private const val KEY_TOTAL_CHECKS = "ai_guard_total_checks"
    private const val KEY_SAVED_CALLS = "ai_guard_saved_calls"
    private const val MAX_L1_CACHE_SIZE = 500
    private const val MAX_RECENT_VERDICTS = 40
    private const val WATCH_ASYNC_DEADLINE_MS = 1_500L
    private const val AI_NETWORK_TIMEOUT_MS = 30_000L
    private const val PREWARM_THROTTLE_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // L1 0ms in-memory verdict cache keyed by normalized channel/title
    private val l1VerdictCache = ConcurrentHashMap<String, YouTubeAiVerdict>()

    // Tracks in-flight live AI jobs per videoKey to deduplicate rapid AccessibilityEvents
    private val inFlightJobs = ConcurrentHashMap<String, InFlightEvaluation>()

    // Session-scoped "Allow Anyway" temporary bypasses (cleared when focus session ends or changes)
    private val sessionBypassedChannels = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var currentActiveSessionId: String? = null

    @Volatile
    private var lastStatSignature: String = ""

    @Volatile
    private var lastStatTimestamp: Long = 0L

    // Cached user keywords from Room KeywordDao for 0ms synchronous lookup
    private val cachedAllowKeywords = ConcurrentHashMap.newKeySet<String>()
    private val cachedBlockKeywords = ConcurrentHashMap.newKeySet<String>()
    private val keywordObserverStarted = AtomicBoolean(false)
    private val isModelWarmedUp = AtomicBoolean(false)

    @Volatile
    private var lastPrewarmTimestamp: Long = 0L

    @Volatile
    private var lastPrewarmSignature: String = ""

    private val _filterMode = MutableStateFlow(YouTubeAiFilterMode.GENERAL_ACADEMIC)
    val filterMode: StateFlow<YouTubeAiFilterMode> = _filterMode.asStateFlow()

    private val _recentVerdicts = MutableStateFlow<List<YouTubeAiVerdict>>(emptyList())
    val recentVerdicts: StateFlow<List<YouTubeAiVerdict>> = _recentVerdicts.asStateFlow()

    private val _aiGuardStats = MutableStateFlow(AiGuardStats())
    val aiGuardStats: StateFlow<AiGuardStats> = _aiGuardStats.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val verdictListeners = CopyOnWriteArrayList<(YouTubeAiVerdict) -> Unit>()

    /**
     * Optional test or custom hook invoked whenever AI finishes learning a new channel.
     */
    @Volatile
    var onChannelLearnedListener: (suspend (
        channelName: String,
        channelHandle: String?,
        isApproved: Boolean,
        category: String,
        reason: String
    ) -> Unit)? = null

    private data class InFlightEvaluation(
        val startedAt: Long,
        val job: Job
    )

    // Compact 3-field schema for single video classification
    private val singleVerdictSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "allow" to Schema.boolean(description = "True if educational study content allowed for this session"),
                "cat" to Schema.string(description = "Short category: STUDY, SUBJECT_MATCH, ENTERTAINMENT, GAMING, MUSIC, VLOG, DISTRACTION"),
                "reason" to Schema.string(description = "Brief reason under 12 words")
            )
        )
    }

    // Compact batch schema for speculative search-results pre-warming
    private val batchVerdictSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "items" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "idx" to Schema.integer(description = "0-based index of the candidate video"),
                            "allow" to Schema.boolean(description = "True if educational study content allowed"),
                            "cat" to Schema.string(description = "Short category"),
                            "reason" to Schema.string(description = "Brief reason under 10 words")
                        )
                    )
                )
            )
        )
    }

    private val singleClassifierModel by lazy {
        val config = generationConfig {
            temperature = 0f
            maxOutputTokens = 80
            responseMimeType = "application/json"
            responseSchema = singleVerdictSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = config
        )
    }

    private val batchClassifierModel by lazy {
        val config = generationConfig {
            temperature = 0f
            maxOutputTokens = 260
            responseMimeType = "application/json"
            responseSchema = batchVerdictSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = config
        )
    }

    /**
     * Optional override hook for deterministic JVM unit tests.
     */
    @Volatile
    var testRemoteInferenceOverride: (suspend (
        videoTitle: String,
        channelName: String,
        effectiveSubject: String,
        mode: YouTubeAiFilterMode
    ) -> Triple<Boolean, String, String>)? = null

    @Volatile
    var testNetworkAvailableOverride: Boolean? = null

    init {
        initializeFromAppIfAvailable()
    }

    private fun initializeFromAppIfAvailable() {
        try {
            val app = FocusShieldApp.instance
            loadPersistedState(app)
            ensureKeywordObserverStarted(app)
        } catch (_: Exception) {
            // Safe no-op during pure JVM unit tests where FocusShieldApp is not initialized
        }
    }

    private fun ensureKeywordObserverStarted(app: FocusShieldApp) {
        if (keywordObserverStarted.compareAndSet(false, true)) {
            scope.launch {
                try {
                    app.database.keywordDao().getAllKeywordsFlow().collectLatest { keywords ->
                        val allowSet = mutableSetOf<String>()
                        val blockSet = mutableSetOf<String>()
                        for (kw in keywords) {
                            if (!kw.isActive) continue
                            val norm = kw.keyword.trim().lowercase(Locale.ROOT)
                            if (norm.length < 2) continue
                            if (kw.type.equals("allow", ignoreCase = true)) {
                                allowSet.add(norm)
                            } else if (kw.type.equals("block", ignoreCase = true)) {
                                blockSet.add(norm)
                            }
                        }
                        cachedAllowKeywords.clear()
                        cachedAllowKeywords.addAll(allowSet)
                        cachedBlockKeywords.clear()
                        cachedBlockKeywords.addAll(blockSet)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun registerAsyncVerdictListener(listener: (YouTubeAiVerdict) -> Unit) {
        if (!verdictListeners.contains(listener)) {
            verdictListeners.add(listener)
        }
    }

    fun unregisterAsyncVerdictListener(listener: (YouTubeAiVerdict) -> Unit) {
        verdictListeners.remove(listener)
    }

    fun setFilterMode(mode: YouTubeAiFilterMode) {
        _filterMode.value = mode
        try {
            val prefs = getPrefs() ?: return
            prefs.edit().putString(KEY_FILTER_MODE, mode.name).apply()
        } catch (_: Exception) {}
    }

    fun clearVerdictCache() {
        l1VerdictCache.clear()
        _recentVerdicts.value = emptyList()
        lastStatSignature = ""
        lastStatTimestamp = 0L
        try {
            getPrefs()?.edit()?.remove(KEY_CACHED_VERDICTS)?.apply()
        } catch (_: Exception) {}
    }

    fun clearSessionBypasses() {
        sessionBypassedChannels.clear()
        currentActiveSessionId = null
    }

    /**
     * Synchronizes temporary "Allow Anyway" bypasses with the active Focus Session ID.
     * Automatically clears session bypasses when a session ends or a new session starts.
     */
    fun syncActiveSessionId(sessionId: String?) {
        val clean = sessionId?.takeIf { it.isNotBlank() }
        if (clean != currentActiveSessionId) {
            sessionBypassedChannels.clear()
            currentActiveSessionId = clean
        }
    }

    /**
     * Grants a temporary exception for the current Focus Session only ("Allow Anyway").
     */
    fun allowChannelForCurrentSession(
        channelId: String?,
        channelName: String?,
        sessionId: String? = currentActiveSessionId
    ) {
        if (!sessionId.isNullOrBlank()) {
            if (currentActiveSessionId != sessionId) {
                sessionBypassedChannels.clear()
            }
            currentActiveSessionId = sessionId
        }
        listOfNotNull(channelId, channelName)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { raw ->
                val norm = raw.lowercase(Locale.ROOT).removePrefix("@").replace(Regex("[^a-z0-9]"), "")
                if (norm.length >= 2) {
                    sessionBypassedChannels.add(norm)
                }
            }
    }

    /**
     * Returns true if the channel was temporarily allowed via "Allow Anyway" for the active session.
     */
    fun isChannelTemporarilyAllowed(channelId: String?, channelName: String?): Boolean {
        if (sessionBypassedChannels.isEmpty()) return false
        return listOfNotNull(channelId, channelName)
            .map { it.trim().lowercase(Locale.ROOT).removePrefix("@").replace(Regex("[^a-z0-9]"), "") }
            .any { norm -> norm.length >= 2 && sessionBypassedChannels.contains(norm) }
    }

    /**
     * 0ms Obvious Distraction Safety Check for mixed-content approved channels.
     * Returns the matched distraction marker if the video title is clearly entertainment/gaming/vlog
     * without academic lecture signals, or null if safe.
     */
    fun checkObviousDistractionSafety(videoTitle: String?, channelName: String? = null): String? {
        val title = videoTitle?.trim().orEmpty()
        if (title.isBlank()) return null
        val lower = title.lowercase(Locale.ROOT)
        val words = lower.replace(Regex("[^a-z0-9+]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        if (hasStrongAcademicSignal(lower, words)) return null
        return HIGH_CONFIDENCE_DISTRACTION_MARKERS.firstOrNull { marker ->
            if (marker.contains(" ")) lower.contains(marker) else words.contains(marker)
        }
    }

    /**
     * Records a 0ms local channel cache hit so the AI Calls Saved % counter updates accurately.
     */
    fun recordLocalChannelHit(
        channelName: String?,
        videoTitle: String?,
        isAllowed: Boolean,
        currentTime: Long = System.currentTimeMillis()
    ) {
        val sig = "${channelName.orEmpty().trim().lowercase(Locale.ROOT)}|${videoTitle.orEmpty().trim().lowercase(Locale.ROOT)}|$isAllowed"
        recordGuardEfficiencyStat(savedLocally = true, signature = sig, currentTime = currentTime)
    }

    private fun recordGuardEfficiencyStat(
        savedLocally: Boolean,
        signature: String,
        currentTime: Long = System.currentTimeMillis()
    ) {
        if (signature == lastStatSignature && (currentTime - lastStatTimestamp) < 4_000L) {
            return
        }
        lastStatSignature = signature
        lastStatTimestamp = currentTime

        val current = _aiGuardStats.value
        val updated = AiGuardStats(
            totalChecks = current.totalChecks + 1,
            savedAiCalls = current.savedAiCalls + if (savedLocally) 1 else 0
        )
        _aiGuardStats.value = updated
        try {
            getPrefs()?.edit()
                ?.putInt(KEY_TOTAL_CHECKS, updated.totalChecks)
                ?.putInt(KEY_SAVED_CALLS, updated.savedAiCalls)
                ?.apply()
        } catch (_: Exception) {}
    }

    fun getCachedVerdictCount(): Int = l1VerdictCache.size

    fun getAverageLatencyMs(): Long {
        val list = _recentVerdicts.value
        if (list.isEmpty()) return 0L
        return list.map { it.latencyMs }.average().toLong()
    }

    /**
     * Layer 0: Pre-warms the `gemini-3.5-flash-lite` model instance, TLS session, and App Check token
     * on `Dispatchers.IO` as soon as a Focus Session starts or YouTube opens.
     */
    fun warmUpModel() {
        initializeFromAppIfAvailable()
        if (!isModelWarmedUp.compareAndSet(false, true)) return
        scope.launch {
            try {
                // Touch lazy model to initialize Firebase AI Logic & App Check token pipeline ahead of time
                val model = singleClassifierModel
                if (isNetworkAvailable()) {
                    withTimeout(15_000L) {
                        model.generateContent(
                            buildSinglePrompt(
                                title = "Electrostatics Lecture 1 Coulomb Law JEE Physics",
                                channel = "Physics Wallah",
                                effectiveSubject = "Physics",
                                mode = YouTubeAiFilterMode.SMART_HYBRID
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Warm-up completed with non-fatal note: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Evaluates a YouTube watch page channel/video using Channel-Level Auto-Learning:
     *  1. Session "Allow Anyway" temporary bypass -> 0ms Allow
     *  2. Learned/Manual Blocked channel -> 0ms Block
     *  3. Learned/Manual Approved channel -> Obvious distraction safety check -> 0ms Allow/Block
     *  4. User keyword rules & L1 verdict cache -> 0ms
     *  5. Live Gemini 3.5 Flash-Lite once per unseen channel -> persists Educational/Entertainment to Room!
     */
    fun evaluateWatchVideo(
        videoTitle: String,
        channelName: String?,
        channelHandle: String?,
        activeSubject: String,
        activeTopic: String,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>,
        blockedChannelIds: Set<String> = emptySet(),
        blockedChannelNames: Set<String> = emptySet(),
        currentTime: Long = System.currentTimeMillis()
    ): YouTubeAiEvaluationOutcome {
        val cleanTitle = videoTitle.trim()
        val displayChannel = channelName?.trim()?.takeIf { it.isNotEmpty() }
            ?: channelHandle?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Unknown Channel"
        val mode = _filterMode.value
        val effectiveSubject = "Educational vs Entertainment"
        val cacheKey = buildCacheKey(cleanTitle, displayChannel, effectiveSubject, mode)

        // 0. Check Session-Scoped "Allow Anyway" Bypass (0ms)
        if (isChannelTemporarilyAllowed(channelHandle, displayChannel)) {
            val verdict = YouTubeAiVerdict(
                videoKey = cacheKey,
                videoTitle = cleanTitle,
                channelName = displayChannel,
                sessionSubject = effectiveSubject,
                filterMode = mode,
                isAllowed = true,
                category = "SESSION_BYPASS",
                reason = "Allowed '$displayChannel' for this focus session",
                latencyMs = 0L,
                source = YouTubeAiVerdictSource.SESSION_BYPASS,
                timestamp = currentTime
            )
            recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = currentTime)
            recordVerdict(verdict, persistToL1 = false)
            return YouTubeAiEvaluationOutcome.Decided(verdict)
        }

        // 1. Check Blocked Channel Cache / Manual Override (0ms)
        val isBlockedOverride = isChannelMatchingSet(channelHandle, channelName, blockedChannelIds, blockedChannelNames)
        if (isBlockedOverride) {
            val verdict = YouTubeAiVerdict(
                videoKey = cacheKey,
                videoTitle = cleanTitle,
                channelName = displayChannel,
                sessionSubject = effectiveSubject,
                filterMode = mode,
                isAllowed = false,
                category = "CHANNEL_BLOCKED",
                reason = "Entertainment channel '$displayChannel' is blocked",
                latencyMs = 0L,
                source = YouTubeAiVerdictSource.CHANNEL_CACHE,
                timestamp = currentTime
            )
            recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = currentTime)
            recordVerdict(verdict, persistToL1 = false)
            return YouTubeAiEvaluationOutcome.Decided(verdict)
        }

        // 2. Check Approved Channel Cache + Obvious Distraction Safety Check (0ms)
        val isTrustedOverride = isChannelMatchingSet(channelHandle, channelName, approvedChannelIds, approvedChannelNames)
        if (isTrustedOverride) {
            val distractionSignal = checkObviousDistractionSafety(cleanTitle, displayChannel)
            if (distractionSignal != null) {
                val verdict = YouTubeAiVerdict(
                    videoKey = cacheKey,
                    videoTitle = cleanTitle,
                    channelName = displayChannel,
                    sessionSubject = effectiveSubject,
                    filterMode = mode,
                    isAllowed = false,
                    category = "DISTRACTION_SAFETY",
                    reason = "Distraction upload ('$distractionSignal') blocked on $displayChannel",
                    latencyMs = 0L,
                    source = YouTubeAiVerdictSource.LOCAL_FAST_HEURISTIC,
                    timestamp = currentTime
                )
                recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = currentTime)
                recordVerdict(verdict, persistToL1 = false)
                return YouTubeAiEvaluationOutcome.Decided(verdict)
            }
            val verdict = YouTubeAiVerdict(
                videoKey = cacheKey,
                videoTitle = cleanTitle,
                channelName = displayChannel,
                sessionSubject = effectiveSubject,
                filterMode = mode,
                isAllowed = true,
                category = "LEARNED_STUDY",
                reason = "Verified study channel ($displayChannel)",
                latencyMs = 0L,
                source = YouTubeAiVerdictSource.CHANNEL_CACHE,
                timestamp = currentTime
            )
            recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = currentTime)
            recordVerdict(verdict, persistToL1 = false)
            return YouTubeAiEvaluationOutcome.Decided(verdict)
        }

        // 3. Check User Keyword Rules (0ms)
        val keywordVerdict = evaluateUserKeywordRules(cleanTitle, displayChannel, effectiveSubject, mode, cacheKey, currentTime)
        if (keywordVerdict != null) {
            recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = currentTime)
            recordVerdict(keywordVerdict, persistToL1 = true)
            return YouTubeAiEvaluationOutcome.Decided(keywordVerdict)
        }

        // 4. Check L1/L2 Verdict Cache (0ms, includes speculative search pre-warmed verdicts!)
        val cached = l1VerdictCache[cacheKey]
        if (cached != null) {
            val returnedVerdict = if (cached.source == YouTubeAiVerdictSource.AI_PREWARMED) {
                cached
            } else {
                cached.copy(latencyMs = 0L, source = YouTubeAiVerdictSource.CACHE_HIT)
            }
            recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = currentTime)
            return YouTubeAiEvaluationOutcome.Decided(returnedVerdict)
        }

        // 5. If offline, run deterministic 0ms fallback without permanently saving an unverified channel
        if (!isNetworkAvailable()) {
            val offlineVerdict = evaluateOfflineFallback(
                videoTitle = cleanTitle,
                channelName = displayChannel,
                effectiveSubject = effectiveSubject,
                mode = mode,
                cacheKey = cacheKey,
                latencyMs = 0L,
                currentTime = currentTime
            )
            recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = currentTime)
            recordVerdict(offlineVerdict, persistToL1 = true)
            return YouTubeAiEvaluationOutcome.Decided(offlineVerdict)
        }

        // 6. Check High-Confidence Local Fast Heuristics when channel identity is missing
        val hasRealChannel = displayChannel != "Unknown Channel"
        if (!hasRealChannel) {
            val fastHeuristic = evaluateFastLocalHeuristic(
                videoTitle = cleanTitle,
                channelName = displayChannel,
                effectiveSubject = effectiveSubject,
                mode = mode,
                cacheKey = cacheKey,
                currentTime = currentTime
            )
            if (fastHeuristic != null) {
                recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = currentTime)
                recordVerdict(fastHeuristic, persistToL1 = true)
                return YouTubeAiEvaluationOutcome.Decided(fastHeuristic)
            }
        }

        // 7. Launch non-blocking `gemini-3.5-flash-lite` channel evaluation once per unseen channel
        val channelJobKey = buildChannelLearnKey(channelHandle, displayChannel, cleanTitle)
        val existingInFlight = inFlightJobs[channelJobKey]
        if (existingInFlight != null) {
            val elapsed = currentTime - existingInFlight.startedAt
            if (elapsed >= WATCH_ASYNC_DEADLINE_MS) {
                val fallback = evaluateOfflineFallback(
                    videoTitle = cleanTitle,
                    channelName = displayChannel,
                    effectiveSubject = effectiveSubject,
                    mode = mode,
                    cacheKey = cacheKey,
                    latencyMs = elapsed,
                    currentTime = currentTime
                )
                recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = currentTime)
                recordVerdict(fallback, persistToL1 = true)
                return YouTubeAiEvaluationOutcome.Decided(fallback)
            }
            return YouTubeAiEvaluationOutcome.VerifyingAsync(channelJobKey, existingInFlight.startedAt)
        }

        val job = scope.launch {
            val callStart = System.currentTimeMillis()
            try {
                val (allowed, category, reason) = invokeSingleAiClassifier(
                    videoTitle = cleanTitle,
                    channelName = if (!channelHandle.isNullOrBlank() && !displayChannel.equals(channelHandle, true)) {
                        "$displayChannel ($channelHandle)"
                    } else {
                        displayChannel
                    },
                    effectiveSubject = effectiveSubject,
                    mode = mode
                )
                val latency = (System.currentTimeMillis() - callStart).coerceAtLeast(1L)
                val liveVerdict = YouTubeAiVerdict(
                    videoKey = cacheKey,
                    videoTitle = cleanTitle,
                    channelName = displayChannel,
                    sessionSubject = effectiveSubject,
                    filterMode = mode,
                    isAllowed = allowed,
                    category = category,
                    reason = reason,
                    latencyMs = latency,
                    source = YouTubeAiVerdictSource.AI_LIVE,
                    timestamp = System.currentTimeMillis()
                )
                _lastError.value = null
                recordGuardEfficiencyStat(savedLocally = false, signature = cacheKey, currentTime = System.currentTimeMillis())
                recordVerdict(liveVerdict, persistToL1 = true)
                if (hasRealChannel) {
                    persistAiLearnedChannel(
                        channelName = displayChannel,
                        channelHandle = channelHandle,
                        isApproved = allowed,
                        category = category,
                        reason = reason
                    )
                }
                notifyVerdictListeners(liveVerdict)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI classification failed; applying deterministic fallback", e)
                _lastError.value = e.localizedMessage ?: "AI classification failed"
                val latency = (System.currentTimeMillis() - callStart).coerceAtLeast(1L)
                val fallbackVerdict = evaluateOfflineFallback(
                    videoTitle = cleanTitle,
                    channelName = displayChannel,
                    effectiveSubject = effectiveSubject,
                    mode = mode,
                    cacheKey = cacheKey,
                    latencyMs = latency,
                    currentTime = System.currentTimeMillis()
                )
                recordGuardEfficiencyStat(savedLocally = true, signature = cacheKey, currentTime = System.currentTimeMillis())
                recordVerdict(fallbackVerdict, persistToL1 = true)
                notifyVerdictListeners(fallbackVerdict)
            } finally {
                inFlightJobs.remove(channelJobKey)
            }
        }

        inFlightJobs[channelJobKey] = InFlightEvaluation(startedAt = currentTime, job = job)
        return YouTubeAiEvaluationOutcome.VerifyingAsync(channelJobKey, currentTime)
    }

    private fun buildChannelLearnKey(channelHandle: String?, channelName: String, videoTitle: String): String {
        val chan = channelHandle?.trim()?.takeIf { it.isNotBlank() } ?: channelName.trim()
        return if (chan.isNotBlank() && chan != "Unknown Channel") {
            "chan:${chan.lowercase(Locale.ROOT)}"
        } else {
            "title:${videoTitle.trim().lowercase(Locale.ROOT)}"
        }
    }

    private suspend fun persistAiLearnedChannel(
        channelName: String,
        channelHandle: String?,
        isApproved: Boolean,
        category: String,
        reason: String
    ) {
        onChannelLearnedListener?.let { listener ->
            runCatching { listener(channelName, channelHandle, isApproved, category, reason) }
            return
        }
        try {
            val repo = FocusShieldApp.instance.studyChannelRepository
            repo.recordAiLearnedChannel(
                channelName = channelName,
                channelHandle = channelHandle,
                isApproved = isApproved,
                category = category,
                aiReason = reason
            )
        } catch (_: Exception) {
            // Safe no-op in isolated JVM tests
        }
    }

    /**
     * Layer 3: Speculative Pre-Warming on YouTube Search Results / Channel Pages.
     * Pre-classifies up to 5 visible video titles in a single background batch call so tapping
     * any search result resolves in 0ms from [l1VerdictCache].
     */
    fun prewarmSearchCandidates(
        candidates: List<Pair<String, String?>>,
        activeSubject: String,
        activeTopic: String,
        currentTime: Long = System.currentTimeMillis()
    ) {
        if (candidates.isEmpty()) return
        if (currentTime - lastPrewarmTimestamp < PREWARM_THROTTLE_MS) return

        val mode = _filterMode.value
        val effectiveSubject = "Educational vs Entertainment"

        // Filter to candidates not already cached
        val uncached = candidates
            .map { (title, chan) -> title.trim() to (chan?.trim()?.takeIf { it.isNotEmpty() } ?: "YouTube") }
            .filter { (title, _) -> title.length >= 5 }
            .distinctBy { (title, chan) -> buildCacheKey(title, chan, effectiveSubject, mode) }
            .filter { (title, chan) ->
                val key = buildCacheKey(title, chan, effectiveSubject, mode)
                !l1VerdictCache.containsKey(key)
            }
            .take(5)

        if (uncached.isEmpty()) return

        val signature = uncached.joinToString("|") { "${it.first}:${it.second}" } + "|$effectiveSubject|${mode.name}"
        if (signature == lastPrewarmSignature) return
        lastPrewarmTimestamp = currentTime
        lastPrewarmSignature = signature

        // First apply 0ms fast heuristics to any candidate that qualifies immediately
        val remainingForAi = mutableListOf<Pair<String, String>>()
        for ((title, chan) in uncached) {
            val key = buildCacheKey(title, chan, effectiveSubject, mode)
            val fast = evaluateFastLocalHeuristic(
                videoTitle = title,
                channelName = chan,
                effectiveSubject = effectiveSubject,
                mode = mode,
                cacheKey = key,
                currentTime = currentTime
            )
            if (fast != null) {
                l1VerdictCache[key] = fast
            } else {
                remainingForAi.add(title to chan)
            }
        }

        if (remainingForAi.isEmpty() || !isNetworkAvailable()) return

        scope.launch {
            val startMs = System.currentTimeMillis()
            try {
                val prompt = buildBatchPrompt(remainingForAi, effectiveSubject, mode)
                val response = withTimeout(AI_NETWORK_TIMEOUT_MS) {
                    batchClassifierModel.generateContent(prompt)
                }
                val jsonText = response.text ?: return@launch
                val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val root = JSONObject(jsonText)
                val items = root.optJSONArray("items") ?: return@launch
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    val idx = obj.optInt("idx", -1)
                    if (idx !in remainingForAi.indices) continue
                    val (title, chan) = remainingForAi[idx]
                    val allow = obj.optBoolean("allow", false)
                    val cat = obj.optString("cat", if (allow) "STUDY" else "DISTRACTION")
                    val reason = obj.optString("reason", if (allow) "Pre-warmed study match" else "Non-study video")
                    val key = buildCacheKey(title, chan, effectiveSubject, mode)
                    val verdict = YouTubeAiVerdict(
                        videoKey = key,
                        videoTitle = title,
                        channelName = chan,
                        sessionSubject = effectiveSubject,
                        filterMode = mode,
                        isAllowed = allow,
                        category = cat,
                        reason = reason,
                        latencyMs = latency,
                        source = YouTubeAiVerdictSource.AI_PREWARMED
                    )
                    l1VerdictCache[key] = verdict
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Search pre-warm skipped: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Interactive AI Speed & Verdict Tester invoked from StudyChannelsScreen so the user can
     * test real-time latency and accuracy on any sample video title + channel.
     */
    suspend fun runInteractiveTestCheck(
        videoTitle: String,
        channelName: String,
        activeSubject: String,
        activeTopic: String,
        approvedChannelIds: Set<String> = emptySet(),
        approvedChannelNames: Set<String> = emptySet(),
        blockedChannelIds: Set<String> = emptySet(),
        blockedChannelNames: Set<String> = emptySet()
    ): YouTubeAiVerdict {
        val cleanTitle = videoTitle.trim()
        val cleanChan = channelName.trim().ifEmpty { "YouTube Channel" }
        val mode = _filterMode.value
        val effectiveSubject = resolveEffectiveSubject(activeSubject, activeTopic)
        val cacheKey = buildCacheKey(cleanTitle, cleanChan, effectiveSubject, mode)
        val now = System.currentTimeMillis()

        // 1. Check Channel Overrides
        if (isChannelMatchingSet(cleanChan, cleanChan, blockedChannelIds, blockedChannelNames)) {
            return YouTubeAiVerdict(
                videoKey = cacheKey,
                videoTitle = cleanTitle,
                channelName = cleanChan,
                sessionSubject = effectiveSubject,
                filterMode = mode,
                isAllowed = false,
                category = "CHANNEL_BLOCKED",
                reason = "Channel '$cleanChan' is in your Blocked Overrides",
                latencyMs = 0L,
                source = YouTubeAiVerdictSource.CHANNEL_OVERRIDE,
                timestamp = now
            ).also { recordVerdict(it, persistToL1 = false) }
        }

        if (isChannelMatchingSet(cleanChan, cleanChan, approvedChannelIds, approvedChannelNames)) {
            return YouTubeAiVerdict(
                videoKey = cacheKey,
                videoTitle = cleanTitle,
                channelName = cleanChan,
                sessionSubject = effectiveSubject,
                filterMode = mode,
                isAllowed = true,
                category = "TRUSTED_CHANNEL",
                reason = "Channel '$cleanChan' is in your Trusted Overrides",
                latencyMs = 0L,
                source = YouTubeAiVerdictSource.CHANNEL_OVERRIDE,
                timestamp = now
            ).also { recordVerdict(it, persistToL1 = false) }
        }

        // 2. Check L1 Cache
        l1VerdictCache[cacheKey]?.let { cached ->
            val hit = cached.copy(latencyMs = 0L, source = YouTubeAiVerdictSource.CACHE_HIT, timestamp = now)
            recordVerdict(hit, persistToL1 = false)
            return hit
        }

        // 3. Live Gemini 3.5 Flash-Lite Call (or Offline Fallback if disconnected)
        if (!isNetworkAvailable()) {
            return evaluateOfflineFallback(
                videoTitle = cleanTitle,
                channelName = cleanChan,
                effectiveSubject = effectiveSubject,
                mode = mode,
                cacheKey = cacheKey,
                latencyMs = 0L,
                currentTime = now
            ).also { recordVerdict(it, persistToL1 = true) }
        }

        val startMs = System.currentTimeMillis()
        return try {
            val (allowed, category, reason) = invokeSingleAiClassifier(
                videoTitle = cleanTitle,
                channelName = cleanChan,
                effectiveSubject = effectiveSubject,
                mode = mode
            )
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            _lastError.value = null
            YouTubeAiVerdict(
                videoKey = cacheKey,
                videoTitle = cleanTitle,
                channelName = cleanChan,
                sessionSubject = effectiveSubject,
                filterMode = mode,
                isAllowed = allowed,
                category = category,
                reason = reason,
                latencyMs = latency,
                source = YouTubeAiVerdictSource.AI_LIVE,
                timestamp = System.currentTimeMillis()
            ).also { recordVerdict(it, persistToL1 = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Interactive AI check timed out", e)
            _lastError.value = "AI request timed out after 30s; used local fallback."
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            evaluateOfflineFallback(
                videoTitle = cleanTitle,
                channelName = cleanChan,
                effectiveSubject = effectiveSubject,
                mode = mode,
                cacheKey = cacheKey,
                latencyMs = latency,
                currentTime = System.currentTimeMillis()
            ).also { recordVerdict(it, persistToL1 = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Interactive AI check failed", e)
            _lastError.value = e.localizedMessage ?: "AI classification failed"
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            evaluateOfflineFallback(
                videoTitle = cleanTitle,
                channelName = cleanChan,
                effectiveSubject = effectiveSubject,
                mode = mode,
                cacheKey = cacheKey,
                latencyMs = latency,
                currentTime = System.currentTimeMillis()
            ).also { recordVerdict(it, persistToL1 = true) }
        }
    }

    private suspend fun invokeSingleAiClassifier(
        videoTitle: String,
        channelName: String,
        effectiveSubject: String,
        mode: YouTubeAiFilterMode
    ): Triple<Boolean, String, String> {
        testRemoteInferenceOverride?.let { overrideFn ->
            return overrideFn(videoTitle, channelName, effectiveSubject, mode)
        }

        val prompt = buildSinglePrompt(
            title = videoTitle,
            channel = channelName,
            effectiveSubject = effectiveSubject,
            mode = mode
        )
        val response = withTimeout(AI_NETWORK_TIMEOUT_MS) {
            singleClassifierModel.generateContent(prompt)
        }
        val rawJson = response.text ?: throw IllegalStateException("Model returned empty response.")
        return parseSingleVerdictJson(rawJson)
    }

    internal fun parseSingleVerdictJson(rawJson: String): Triple<Boolean, String, String> {
        try {
            val json = JSONObject(rawJson)
            val cat = json.optString("cat", "")
            val reason = json.optString("reason", "")
            if (cat.isNotEmpty() || reason.isNotEmpty()) {
                val allow = json.optBoolean("allow", false)
                return Triple(
                    allow,
                    cat.ifEmpty { if (allow) "STUDY" else "DISTRACTION" },
                    reason.ifEmpty { if (allow) "Verified study content" else "Non-study distraction" }
                )
            }
        } catch (_: Exception) {}

        // Lightweight fallback parser for JVM unit tests where android.jar JSONObject is a stub
        val allowMatch = Regex("\"allow\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE).find(rawJson)
        val catMatch = Regex("\"cat\"\\s*:\\s*\"([^\"]*)\"").find(rawJson)
        val reasonMatch = Regex("\"reason\"\\s*:\\s*\"([^\"]*)\"").find(rawJson)
        val allow = allowMatch?.groupValues?.get(1)?.equals("true", ignoreCase = true) ?: false
        val cat = catMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: if (allow) "STUDY" else "DISTRACTION"
        val reason = reasonMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: if (allow) "Verified study video" else "Non-study content blocked"
        return Triple(allow, cat, reason)
    }

    private fun buildSinglePrompt(
        title: String,
        channel: String,
        effectiveSubject: String,
        mode: YouTubeAiFilterMode
    ): String {
        return """
            YouTube Channel Study Guard (No subject-specific restriction).
            Classify whether this channel/video is EDUCATIONAL or ENTERTAINMENT for a student:
            - ALLOW (allow=true): Any educational lectures, tutorials, coding, science or engineering experiments, math, exam prep (JEE/NEET/UPSC/SAT), academic documentaries, or skill learning of ANY subject.
            - BLOCK (allow=false): Gaming, vlogs, pranks, comedy, memes, music videos, movie clips/trailers, drama, celebrity gossip, or pure entertainment.
            Choose a short readable category (e.g., "Science & Experiments", "Math & Lectures", "Coding & Tech", "Exam Prep", "Documentary & Learning", "Gaming", "Vlog & Lifestyle", "Music & Movies", "Entertainment").
            Channel: "$channel"
            Sample Video Title: "$title"
            Respond with JSON {allow, cat, reason}.
        """.trimIndent()
    }

    private fun buildBatchPrompt(
        candidates: List<Pair<String, String>>,
        effectiveSubject: String,
        mode: YouTubeAiFilterMode
    ): String {
        val listText = candidates.mapIndexed { idx, (t, c) ->
            "$idx: title=\"$t\" channel=\"$c\""
        }.joinToString("\n")
        return "Classify YouTube channels/videos as Educational (lectures, coding, experiments, any study topic -> allow=true) or Entertainment (gaming, vlogs, pranks, music, movies -> allow=false).\n$listText"
    }

    /**
     * Layer 2: 0ms High-Confidence Local Fast Heuristics.
     * Broadly evaluates Educational vs Entertainment without subject restrictions.
     */
    internal fun evaluateFastLocalHeuristic(
        videoTitle: String,
        channelName: String,
        effectiveSubject: String,
        mode: YouTubeAiFilterMode,
        cacheKey: String,
        currentTime: Long = System.currentTimeMillis()
    ): YouTubeAiVerdict? {
        val combined = "$videoTitle $channelName".lowercase(Locale.ROOT)
        val words = combined.replace(Regex("[^a-z0-9+]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

        // 1. Unambiguous entertainment / distraction markers -> 0ms BLOCK
        val matchedDistraction = HIGH_CONFIDENCE_DISTRACTION_MARKERS.firstOrNull { marker ->
            if (marker.contains(" ")) {
                combined.contains(marker)
            } else {
                words.contains(marker)
            }
        }
        if (matchedDistraction != null && !hasStrongAcademicSignal(combined, words)) {
            return YouTubeAiVerdict(
                videoKey = cacheKey,
                videoTitle = videoTitle,
                channelName = channelName,
                sessionSubject = effectiveSubject,
                filterMode = mode,
                isAllowed = false,
                category = "Entertainment",
                reason = "Blocked distraction signal ('$matchedDistraction')",
                latencyMs = 0L,
                source = YouTubeAiVerdictSource.LOCAL_FAST_HEURISTIC,
                timestamp = currentTime
            )
        }

        // 2. Unambiguous educational / experiment / lecture match -> 0ms ALLOW
        if (hasStrongAcademicSignal(combined, words)) {
            return YouTubeAiVerdict(
                videoKey = cacheKey,
                videoTitle = videoTitle,
                channelName = channelName,
                sessionSubject = effectiveSubject,
                filterMode = mode,
                isAllowed = true,
                category = "Educational",
                reason = "Verified educational lecture / experiment / tutorial signal",
                latencyMs = 0L,
                source = YouTubeAiVerdictSource.LOCAL_FAST_HEURISTIC,
                timestamp = currentTime
            )
        }

        return null
    }

    /**
     * Deterministic Offline / Slow-Network Fallback (`0ms`).
     */
    internal fun evaluateOfflineFallback(
        videoTitle: String,
        channelName: String,
        effectiveSubject: String,
        mode: YouTubeAiFilterMode,
        cacheKey: String,
        latencyMs: Long = 0L,
        currentTime: Long = System.currentTimeMillis()
    ): YouTubeAiVerdict {
        val combined = "$videoTitle $channelName".lowercase(Locale.ROOT)
        val words = combined.replace(Regex("[^a-z0-9+]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

        val academicSignal = hasStrongAcademicSignal(combined, words) ||
                BROAD_EDUCATIONAL_TERMS.any { term -> if (term.contains(" ")) combined.contains(term) else words.contains(term) }
        val distractionSignal = HIGH_CONFIDENCE_DISTRACTION_MARKERS.any { marker ->
            if (marker.contains(" ")) combined.contains(marker) else words.contains(marker)
        }

        val allowed = academicSignal && !distractionSignal

        val reason = when {
            allowed -> "Offline match: educational keywords detected"
            distractionSignal -> "Offline block: entertainment/distraction keywords detected"
            else -> "Offline block: connect to internet so AI can verify this new channel"
        }

        return YouTubeAiVerdict(
            videoKey = cacheKey,
            videoTitle = videoTitle,
            channelName = channelName,
            sessionSubject = effectiveSubject,
            filterMode = mode,
            isAllowed = allowed,
            category = if (allowed) "Educational" else "Entertainment",
            reason = reason,
            latencyMs = latencyMs,
            source = YouTubeAiVerdictSource.OFFLINE_FALLBACK,
            timestamp = currentTime
        )
    }

    private fun evaluateUserKeywordRules(
        videoTitle: String,
        channelName: String,
        effectiveSubject: String,
        mode: YouTubeAiFilterMode,
        cacheKey: String,
        currentTime: Long
    ): YouTubeAiVerdict? {
        val lowerTitle = videoTitle.lowercase(Locale.ROOT)
        for (blockKw in cachedBlockKeywords) {
            if (blockKw.isNotBlank() && lowerTitle.contains(blockKw)) {
                return YouTubeAiVerdict(
                    videoKey = cacheKey,
                    videoTitle = videoTitle,
                    channelName = channelName,
                    sessionSubject = effectiveSubject,
                    filterMode = mode,
                    isAllowed = false,
                    category = "KEYWORD_BLOCK",
                    reason = "Matched blocked keyword '$blockKw'",
                    latencyMs = 0L,
                    source = YouTubeAiVerdictSource.KEYWORD_RULE,
                    timestamp = currentTime
                )
            }
        }
        for (allowKw in cachedAllowKeywords) {
            if (allowKw.isNotBlank() && lowerTitle.contains(allowKw)) {
                return YouTubeAiVerdict(
                    videoKey = cacheKey,
                    videoTitle = videoTitle,
                    channelName = channelName,
                    sessionSubject = effectiveSubject,
                    filterMode = mode,
                    isAllowed = true,
                    category = "KEYWORD_ALLOW",
                    reason = "Matched study keyword '$allowKw'",
                    latencyMs = 0L,
                    source = YouTubeAiVerdictSource.KEYWORD_RULE,
                    timestamp = currentTime
                )
            }
        }
        return null
    }

    private fun isChannelMatchingSet(
        channelHandle: String?,
        channelName: String?,
        targetIds: Set<String>,
        targetNames: Set<String>
    ): Boolean {
        if (targetIds.isEmpty() && targetNames.isEmpty()) return false
        if (!channelHandle.isNullOrBlank() &&
            YouTubeContentBlockEngine.isChannelApproved(channelHandle, targetIds, targetNames)
        ) {
            return true
        }
        if (!channelName.isNullOrBlank() &&
            YouTubeContentBlockEngine.isChannelApproved(channelName, targetIds, targetNames)
        ) {
            return true
        }
        return false
    }

    internal fun resolveEffectiveSubject(subject: String, topic: String): String {
        val cleanSub = subject.trim()
        val cleanTop = topic.trim()
        return when {
            cleanSub.isNotEmpty() && cleanTop.isNotEmpty() && !cleanTop.equals(cleanSub, ignoreCase = true) ->
                "$cleanSub ($cleanTop)"
            cleanSub.isNotEmpty() -> cleanSub
            cleanTop.isNotEmpty() -> cleanTop
            else -> "General Study"
        }
    }

    internal fun isGenericStudySubject(subject: String): Boolean {
        val norm = subject.trim().lowercase(Locale.ROOT)
        return norm.isEmpty() ||
                norm in GENERIC_SESSION_SUBJECTS ||
                norm.startsWith("general study") ||
                norm.startsWith("focus session") ||
                norm.startsWith("study session") ||
                norm.startsWith("deep work") ||
                norm.startsWith("pomodoro")
    }

    internal fun buildCacheKey(
        videoTitle: String,
        channelName: String,
        effectiveSubject: String,
        mode: YouTubeAiFilterMode
    ): String {
        val normTitle = videoTitle.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val normChan = channelName.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val normSub = if (mode == YouTubeAiFilterMode.GENERAL_ACADEMIC || isGenericStudySubject(effectiveSubject)) {
            "__any_study__"
        } else {
            effectiveSubject.trim().lowercase(Locale.ROOT)
        }
        return "$normTitle|$normChan|$normSub|${mode.name}"
    }

    private fun matchesSubjectTokens(combinedLower: String, titleWords: Set<String>, effectiveSubject: String): Boolean {
        val subjectTokens = effectiveSubject.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9+]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in GENERIC_SESSION_SUBJECTS }

        if (subjectTokens.isEmpty()) return true
        for (token in subjectTokens) {
            if (titleWords.contains(token) || combinedLower.contains(token)) return true
            // Check domain synonyms (e.g. physics -> mechanics, electrostatics, optics, thermodynamics, kinematics)
            val synonyms = SUBJECT_DOMAIN_SYNONYMS[token]
            if (synonyms != null && synonyms.any { syn -> titleWords.contains(syn) || combinedLower.contains(syn) }) {
                return true
            }
        }
        return false
    }

    private fun hasStrongAcademicSignal(combinedLower: String, words: Set<String>): Boolean {
        return STRONG_ACADEMIC_MARKERS.any { marker ->
            if (marker.contains(" ")) combinedLower.contains(marker) else words.contains(marker)
        }
    }

    private fun recordVerdict(verdict: YouTubeAiVerdict, persistToL1: Boolean) {
        if (persistToL1) {
            if (l1VerdictCache.size >= MAX_L1_CACHE_SIZE) {
                val oldestKey = l1VerdictCache.entries.minByOrNull { it.value.timestamp }?.key
                if (oldestKey != null) l1VerdictCache.remove(oldestKey)
            }
            l1VerdictCache[verdict.videoKey] = verdict
            persistVerdictToPrefs(verdict)
        }

        val currentList = _recentVerdicts.value
        val deduplicated = currentList.filterNot {
            it.videoTitle.equals(verdict.videoTitle, ignoreCase = true) &&
                    it.channelName.equals(verdict.channelName, ignoreCase = true)
        }
        _recentVerdicts.value = (listOf(verdict) + deduplicated).take(MAX_RECENT_VERDICTS)
    }

    private fun notifyVerdictListeners(verdict: YouTubeAiVerdict) {
        for (listener in verdictListeners) {
            try {
                listener(verdict)
            } catch (_: Exception) {}
        }
    }

    private fun isNetworkAvailable(): Boolean {
        testNetworkAvailableOverride?.let { return it }
        return try {
            val app = FocusShieldApp.instance
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun getPrefs(): SharedPreferences? {
        return try {
            FocusShieldApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadPersistedState(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val total = prefs.getInt(KEY_TOTAL_CHECKS, 0)
            val saved = prefs.getInt(KEY_SAVED_CALLS, 0)
            _aiGuardStats.value = AiGuardStats(totalChecks = total, savedAiCalls = saved)
            val modeStr = prefs.getString(KEY_FILTER_MODE, null)
            if (!modeStr.isNullOrBlank()) {
                runCatching { YouTubeAiFilterMode.valueOf(modeStr) }.getOrNull()?.let {
                    _filterMode.value = it
                }
            }
        } catch (_: Exception) {}
    }

    private fun persistVerdictToPrefs(verdict: YouTubeAiVerdict) {
        try {
            val prefs = getPrefs() ?: return
            prefs.edit().putString("last_verdict_title", verdict.videoTitle).apply()
        } catch (_: Exception) {}
    }

    private val GENERIC_SESSION_SUBJECTS = setOf(
        "study", "studying", "general", "focus", "session", "work", "reading", "homework",
        "revision", "exam", "prep", "preparation", "learning", "default", "none", "custom"
    )

    private val STRONG_ACADEMIC_MARKERS = setOf(
        "lecture", "lectures", "tutorial", "course", "full course", "crash course",
        "derivation", "numericals", "ncert", "jee", "neet", "upsc", "gate", "cat exam",
        "pyq", "pyqs", "previous year", "one shot", "oneshot", "chapter", "syllabus",
        "experiment", "science experiment", "lab", "chemistry lab", "physics lab", "documentary",
        "calculus", "linear algebra", "differential equations", "organic chemistry",
        "inorganic chemistry", "physical chemistry", "electrostatics", "thermodynamics",
        "kinematics", "electromagnetism", "quantum", "data structures", "algorithms",
        "leetcode", "dynamic programming", "system design", "operating systems",
        "computer networks", "dbms", "sql", "kotlin", "python tutorial", "java tutorial",
        "c++ tutorial", "machine learning", "deep learning", "neural networks",
        "mit opencourseware", "khan academy", "nptel", "stanford", "harvard",
        "physics wallah", "unacademy", "byjus", "vedantu", "apni kaksha", "freecodecamp"
    )

    private val BROAD_EDUCATIONAL_TERMS = setOf(
        "explained", "explanation", "introduction to", "basics of", "how to solve",
        "problem solving", "theorem", "formula", "proof", "lesson", "class 10",
        "class 11", "class 12", "engineering", "mathematics", "math", "physics",
        "chemistry", "biology", "anatomy", "physiology", "pharmacology", "economics",
        "microeconomics", "macroeconomics", "history of", "geography", "civics",
        "polity", "accounting", "finance", "statistics", "probability", "programming",
        "coding", "software engineering", "web development", "android development",
        "science", "how does", "why does"
    )

    private val HIGH_CONFIDENCE_DISTRACTION_MARKERS = setOf(
        "prank", "pranks", "vlog", "daily vlog", "my Morning routine", "grwm",
        "minecraft", "valorant", "bgmi", "pubg", "free fire", "fortnite", "gta 5", "gta v",
        "roblox", "gameplay", "let's play", "gaming live", "esports", "walkthrough",
        "funny moments", "try not to laugh", "memes", "meme compilation", "roast",
        "diss track", "official music video", "lyric video", "full song", "jukebox",
        "movie trailer", "official trailer", "teaser", "web series", "episode 1",
        "unboxing", "haul", "mukbang", "asmr eating", "reacting to", "reaction video",
        "gossip", "celebrity", "bigg boss", "ipl highlights", "match highlights",
        "standup comedy", "stand up comedy", "skit", "parody"
    )

    private val SUBJECT_DOMAIN_SYNONYMS: Map<String, Set<String>> = mapOf(
        "physics" to setOf(
            "mechanics", "kinematics", "dynamics", "electrostatics", "magnetism",
            "electromagnetism", "optics", "thermodynamics", "waves", "oscillations",
            "gravitation", "rotational", "modern physics", "semiconductors", "capacitance",
            "current electricity", "ac circuits", "ray optics", "wave optics", "newton", "coulomb"
        ),
        "chemistry" to setOf(
            "organic", "inorganic", "physical chemistry", "stoichiometry", "thermochemistry",
            "equilibrium", "electrochemistry", "kinetics", "coordination", "aldehydes",
            "ketones", "carboxylic", "amines", "polymers", "biomolecules", "periodic table",
            "chemical bonding", "atomic structure", "hydrocarbons", "haloalkanes"
        ),
        "math" to setOf(
            "mathematics", "calculus", "algebra", "trigonometry", "geometry", "matrices",
            "determinants", "probability", "statistics", "integration", "differentiation",
            "derivatives", "limits", "vectors", "conic sections", "permutations", "combinations"
        ),
        "mathematics" to setOf(
            "math", "calculus", "algebra", "trigonometry", "geometry", "matrices",
            "determinants", "probability", "statistics", "integration", "differentiation",
            "derivatives", "limits", "vectors"
        ),
        "biology" to setOf(
            "botany", "zoology", "genetics", "ecology", "cell", "anatomy", "physiology",
            "evolution", "biotechnology", "reproduction", "photosynthesis", "respiration",
            "human physiology", "plant physiology", "dna", "rna"
        ),
        "coding" to setOf(
            "programming", "computer science", "dsa", "algorithms", "data structures",
            "leetcode", "python", "java", "kotlin", "javascript", "typescript", "react",
            "android", "c++", "golang", "rust", "sql", "database", "system design", "git"
        ),
        "programming" to setOf(
            "coding", "computer science", "dsa", "algorithms", "data structures",
            "leetcode", "python", "java", "kotlin", "javascript", "typescript", "c++"
        ),
        "cs" to setOf(
            "computer science", "coding", "programming", "dsa", "algorithms",
            "data structures", "operating systems", "dbms", "computer networks"
        )
    )
}
