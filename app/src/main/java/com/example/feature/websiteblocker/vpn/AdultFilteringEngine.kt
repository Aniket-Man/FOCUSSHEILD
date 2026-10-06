package com.example.feature.websiteblocker.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.BlockedWebsiteRepository
import com.example.feature.websiteblocker.engine.AdultClassificationResult
import com.example.feature.websiteblocker.engine.AdultDnsClassifier
import com.example.feature.websiteblocker.engine.AdultDnsVerdict
import com.example.feature.websiteblocker.engine.AdultLexicalClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.DatagramSocket

/**
 * Core coordinator for the 18+ website-blocking engine and local VPN DNS filtering.
 */
class AdultFilteringEngine private constructor(
    private val appContext: Context,
    private val preferencesRepository: FocusPreferencesRepository,
    private val websiteRepository: BlockedWebsiteRepository
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var domainMatcher: DomainMatcher = AdultBlocklistProvider.createMatcher()

    val dnsCache = DnsCache()

    @Volatile
    var dnsProxy: DnsProxy = DnsProxy()
        private set

    @Volatile
    var isEngineActive: Boolean = false
        private set

    init {
        // Observe custom blocked domains from the existing repository
        engineScope.launch {
            websiteRepository.activeBlockedWebsites.collectLatest { list ->
                val customDomains = list.map { it.domain }
                rebuildMatcher(customDomains)
            }
        }

        // Observe existing 18+ toggle preference
        engineScope.launch {
            preferencesRepository.preferencesFlow.collectLatest { prefs ->
                val shouldBeEnabled = prefs.isAutoAdultWebsiteBlockingEnabled
                Log.d(TAG, "Observed 18+ blocking state change: $shouldBeEnabled")
                if (shouldBeEnabled) {
                    if (isVpnPrepared(appContext)) {
                        ensureVpnRunning()
                    } else {
                        Log.w(TAG, "18+ blocking enabled in prefs but VPN permission not yet granted")
                    }
                } else {
                    stopVpn()
                }
            }
        }

        // Initial sync on startup
        syncWithPreferences()
    }

    /**
     * Re-synchronizes the engine and VPN status with the current stored preferences.
     * Useful on app resume, boot, and permission grant transitions.
     */
    fun syncWithPreferences() {
        engineScope.launch {
            try {
                val prefs = preferencesRepository.preferencesFlow.first()
                if (prefs.isAutoAdultWebsiteBlockingEnabled) {
                    if (isVpnPrepared(appContext)) {
                        ensureVpnRunning()
                    } else {
                        Log.w(TAG, "Sync: 18+ blocking enabled in prefs, awaiting VPN permission")
                    }
                } else {
                    stopVpn()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing with preferences: ${e.message}")
            }
        }
    }

    private fun rebuildMatcher(customBlocked: Collection<String>) {
        val newMatcher = AdultBlocklistProvider.createMatcher(customBlocked)
        domainMatcher = newMatcher
        Log.d(TAG, "Rebuilt domain matcher with ${customBlocked.size} custom rules.")
    }

    /**
     * Determines whether a domain matches 18+ content or active block rules.
     *
     * @param includeDynamicVerdict when false, step 5 (the live family-DNS lookup) is
     * skipped so callers that run their own verdict step (e.g. `WebsiteBlockerEngine`)
     * keep their own block-reason attribution; static steps 1–4 always apply.
     */
    fun shouldBlockDomain(rawDomain: String, includeDynamicVerdict: Boolean = true): Boolean {
        val cleanDomain = DomainMatcher.normalize(rawDomain) ?: return false

        // 1. Allowlist precedence
        if (domainMatcher.isAllowed(cleanDomain)) {
            return false
        }

        // 2. Direct blocklist or subdomain match
        if (domainMatcher.isBlocked(cleanDomain)) {
            Log.d(TAG, "[Blocked] Domain matched 18+ blocklist: $cleanDomain")
            return true
        }

        // 3. Adult TLD check (.xxx, .porn, .adult, .sex, etc.)
        if (AdultBlocklistProvider.hasAdultTld(cleanDomain)) {
            Log.d(TAG, "[Blocked] Domain has adult TLD: $cleanDomain")
            return true
        }

        // 4. Lexical / semantic classifier fast path
        val lexicalResult = AdultLexicalClassifier.isAdultContent("", cleanDomain)
        if (lexicalResult is AdultClassificationResult.Adult) {
            Log.d(TAG, "[Blocked] Domain matched adult lexical rules: $cleanDomain (${lexicalResult.matchedRule})")
            return true
        }

        // 5. Dynamic family-DNS verdict (issue #63): catch novel adult sites that are not
        // on any preloaded list. Cache-first with a bounded first-lookup wait; only
        // genuinely unknown domains pay it. SAFE/UNKNOWN fail open by contract.
        if (includeDynamicVerdict &&
            AdultDnsClassifier.verdict(cleanDomain) == AdultDnsVerdict.ADULT
        ) {
            Log.d(TAG, "[Blocked] Family DNS classified domain as adult: $cleanDomain")
            return true
        }

        return false
    }

    /**
     * Processes an incoming raw IP packet received on the TUN interface.
     * Returns an IP packet ready to write back to TUN, or null if the packet was ignored/dropped.
     */
    fun processPacket(packetData: ByteArray, packetLength: Int): ByteArray? {
        val ipInfo = DnsPacketParser.parseIpPacket(packetData, packetLength) ?: return null
        val query = DnsPacketParser.parseDnsQuery(packetData, ipInfo) ?: return null
        val question = query.questions.firstOrNull() ?: return null

        val domain = question.qname
        val isBlocked = shouldBlockDomain(domain)

        return if (isBlocked) {
            // Block system-wide: synthesize sinkhole response (0.0.0.0 for A, :: for AAAA)
            DnsPacketParser.buildBlockedResponsePacket(query)
        } else {
            // Allowed: Check cache first
            val cachedResponse = dnsCache.get(domain, question.qtype, query.header.id)
            if (cachedResponse != null) {
                DnsPacketParser.buildIpPacket(ipInfo, cachedResponse)
            } else {
                // Forward query to upstream DNS
                val upstreamResponse = dnsProxy.forwardQuery(query.dnsPayload)
                if (upstreamResponse != null) {
                    dnsCache.put(domain, question.qtype, upstreamResponse)
                    DnsPacketParser.buildIpPacket(ipInfo, upstreamResponse)
                } else {
                    null
                }
            }
        }
    }

    /**
     * Wires socket protector from the active VpnService instance.
     */
    fun setSocketProtector(protector: ((DatagramSocket) -> Boolean)?) {
        dnsProxy = DnsProxy(socketProtector = protector)
    }

    /**
     * Starts the VPN service if permission is prepared and not already running.
     */
    fun ensureVpnRunning() {
        if (!isVpnPrepared(appContext)) {
            Log.w(TAG, "Cannot start VPN service: VPN permission has not been granted by user")
            return
        }

        try {
            AdultDnsVpnService.start(appContext)
            isEngineActive = true
            Log.i(TAG, "Started AdultDnsVpnService for 18+ website blocking")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AdultDnsVpnService: ${e.message}")
        }
    }

    /**
     * Stops the VPN service and restores normal network routing.
     */
    fun stopVpn() {
        try {
            AdultDnsVpnService.stop(appContext)
            isEngineActive = false
            dnsCache.clear()
            Log.i(TAG, "Stopped AdultDnsVpnService; normal network behavior restored")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AdultDnsVpnService: ${e.message}")
        }
    }

    fun onVpnServiceStarted() {
        isEngineActive = true
        // Pre-warm the family-DNS classifier off the packet loop (issue #63). The DoH
        // fallback resolves its own hostname through the system DNS — which is this VPN —
        // so prime the transport and caches now, while the TUN loop is still free to
        // carry that bootstrap query, instead of during a classification wait.
        engineScope.launch {
            try {
                AdultDnsClassifier.verdict("example.com")
            } catch (e: Exception) {
                Log.w(TAG, "Family-DNS classifier pre-warm failed: ${e.message}")
            }
        }
    }

    fun onVpnServiceStopped() {
        isEngineActive = false
    }

    companion object {
        private const val TAG = "AdultFilteringEngine"

        @Volatile
        private var INSTANCE: AdultFilteringEngine? = null

        fun initialize(
            appContext: Context,
            preferencesRepository: FocusPreferencesRepository,
            websiteRepository: BlockedWebsiteRepository
        ): AdultFilteringEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = AdultFilteringEngine(appContext, preferencesRepository, websiteRepository)
                INSTANCE = instance
                instance
            }
        }

        val instance: AdultFilteringEngine?
            get() = INSTANCE

        fun isVpnPrepared(context: Context): Boolean {
            return try {
                VpnService.prepare(context) == null
            } catch (_: Exception) {
                false
            }
        }
    }
}
