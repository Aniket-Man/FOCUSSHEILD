package com.example.feature.websiteblocker.engine

import android.content.Context
import android.util.Log
import com.example.data.preferences.FocusPreferences
import com.example.data.repository.BlockedWebsiteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class WebsiteBlockDecision {
    object Allowed : WebsiteBlockDecision()
    data class Blocked(
        val domain: String,
        val engineType: String, // "ADULT_AUTOMATIC" or "MANUAL_CUSTOM"
        val reason: String
    ) : WebsiteBlockDecision()
}

/**
 * Two-Engine Website Blocker Core Engine.
 * Engine 1: Automatic Adult Website Blocker using DNS & Pattern Matching rules.
 * Engine 2: Manual Website Blocker matching user custom blocked domain list.
 */
class WebsiteBlockerEngine private constructor(
    private val appContext: Context,
    private val websiteRepository: BlockedWebsiteRepository,
    private val preferencesRepository: com.example.data.preferences.FocusPreferencesRepository
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentPreferences = FocusPreferences()
    private var activeManualDomains = setOf<String>()

    init {
        // Observe preferences
        engineScope.launch {
            preferencesRepository.preferencesFlow.collectLatest { prefs ->
                currentPreferences = prefs
            }
        }

        // Observe manual active blocked domains
        engineScope.launch {
            websiteRepository.activeBlockedWebsites.collectLatest { list ->
                activeManualDomains = list.map { it.domain.lowercase() }.toSet()
                Log.d(TAG, "Loaded ${activeManualDomains.size} manual blocked website domains into memory.")
            }
        }
    }

    /**
     * Evaluate extracted domain against Engine 1 (Auto Adult) and Engine 2 (Manual List).
     */
    fun evaluateDomain(rawUrl: String, domain: String): WebsiteBlockDecision {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isBlank()) return WebsiteBlockDecision.Allowed

        val isSessionActive = isStudySessionActive()

        // 1. ENGINE 1: Automatic Adult Website Blocker (Lexical + Corpus + DNS Classifier)
        if (currentPreferences.isAutoAdultWebsiteBlockingEnabled || isSessionActive) {
            // Check Lexical, TLD, Corpus, and Search Query Classifier
            val lexicalResult = AdultLexicalClassifier.isAdultContent(rawUrl, cleanDomain)
            if (lexicalResult is AdultClassificationResult.Adult) {
                Log.w(TAG, "[Engine 1: Auto Adult] Blocked adult domain via ${lexicalResult.matchedRule}: $cleanDomain (URL: $rawUrl)")
                return WebsiteBlockDecision.Blocked(
                    domain = cleanDomain,
                    engineType = "ADULT_AUTOMATIC",
                    reason = "Adult Content Filter (${lexicalResult.matchedRule})"
                )
            }

            // Check Live DNS Family Filter Classifier
            if (AdultDnsClassifier.isDomainBlockedByDns(cleanDomain)) {
                Log.w(TAG, "[Engine 1: Auto Adult] Blocked adult domain via Live DNS Classifier: $cleanDomain (URL: $rawUrl)")
                return WebsiteBlockDecision.Blocked(
                    domain = cleanDomain,
                    engineType = "ADULT_AUTOMATIC",
                    reason = "Family DNS Filter (Adult Content Blocked)"
                )
            }
        }

        // 2. ENGINE 2: Manual Website Blocker
        if ((currentPreferences.isManualWebsiteBlockingEnabled || isSessionActive) && activeManualDomains.isNotEmpty()) {
            for (blockedDomain in activeManualDomains) {
                if (cleanDomain == blockedDomain || cleanDomain.endsWith(".$blockedDomain")) {
                    Log.w(TAG, "[Engine 2: Manual] Blocked user-added domain: $cleanDomain (Matched: $blockedDomain)")
                    return WebsiteBlockDecision.Blocked(
                        domain = cleanDomain,
                        engineType = "MANUAL_CUSTOM",
                        reason = "Manually Blocked Website ($blockedDomain)"
                    )
                }
            }
        }

        return WebsiteBlockDecision.Allowed
    }

    private fun isStudySessionActive(): Boolean {
        return try {
            val sessionManager = com.example.feature.session.engine.FocusSessionManager.instance
            val active = sessionManager.activeSession.value
            active != null && (active.isRunning || active.isPaused)
        } catch (e: Exception) {
            false
        }
    }

    private fun isAdultDomain(domain: String): Boolean {
        // Exact top adult domains list
        if (TOP_ADULT_DOMAINS.contains(domain)) return true

        // Check subdomains
        for (topDomain in TOP_ADULT_DOMAINS) {
            if (domain.endsWith(".$topDomain")) return true
        }

        // Adult keyword pattern matching in hostname
        for (keyword in ADULT_KEYWORDS) {
            if (domain.contains(keyword)) {
                return true
            }
        }

        return false
    }

    companion object {
        private const val TAG = "WebsiteBlockerEngine"

        @Volatile
        private var INSTANCE: WebsiteBlockerEngine? = null

        fun initialize(
            appContext: Context,
            websiteRepository: BlockedWebsiteRepository,
            preferencesRepository: com.example.data.preferences.FocusPreferencesRepository
        ): WebsiteBlockerEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = WebsiteBlockerEngine(appContext, websiteRepository, preferencesRepository)
                INSTANCE = instance
                instance
            }
        }

        val instance: WebsiteBlockerEngine?
            get() = INSTANCE

        // Built-in Adult Domain List (Engine 1)
        private val TOP_ADULT_DOMAINS = setOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
            "youporn.com", "brazzers.com", "thumbzilla.com", "spankbang.com", "rule34.xxx",
            "chaturbate.com", "onlyfans.com", "stripchat.com", "livejasmin.com", "beeg.com",
            "eporner.com", "xhamster2.com", "tub8.com", "sunporno.com", "tnaflix.com",
            "drtuber.com", "eroprofile.com", "motherless.com", "heavy-r.com", "redytube.com",
            "faphouse.com", "camsoda.com", "cam4.com", "bongacams.com", "myfreecams.com",
            "flirt4free.com", "javhd.com", "porntrex.com", "adultfriendfinder.com", "fetlife.com",
            "hqporner.com", "porn.com", "xrated.com", "hentaihaven.org", "nhentai.net",
            "hanime.tv", "multporn.net", "gelbooru.com", "e-hentai.org", "luscious.net"
        )

        // Adult Keyword Patterns for Engine 1
        private val ADULT_KEYWORDS = listOf(
            "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "brazzers",
            "thumbzilla", "spankbang", "eporner", "chaturbate", "bongacams", "camsoda",
            "stripchat", "hentai", "nhentai", "rule34", "xrated", "erotica", "fetlife",
            "adultfriendfinder", "myfreecams", "javhd", "porntrex"
        )
    }
}
