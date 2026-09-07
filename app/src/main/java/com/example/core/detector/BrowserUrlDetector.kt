package com.example.core.detector

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Real-time Browser URL & Domain Detector.
 * Traverses AccessibilityNodeInfo trees to extract active browser URLs/domains across
 * Chrome, Firefox, Edge, Brave, Opera, Samsung Internet, DuckDuckGo, Vivaldi, Kiwi, etc.
 * Based on accessibility browser URL filtering architectures.
 */
object BrowserUrlDetector {

    private const val TAG = "BrowserUrlDetector"

    val KNOWN_BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.touch",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.ucmobile.intl",
        "com.yandex.browser",
        "com.aloha.browser",
        "mark.via.gp",
        "com.cloudmosa.puffinFree"
    )

    fun isBrowserPackage(packageName: String): Boolean {
        if (KNOWN_BROWSER_PACKAGES.contains(packageName)) return true
        val lower = packageName.lowercase()
        return lower.contains("browser") || lower.endsWith(".browser.android")
    }

    fun extractUrlAndDomain(packageName: String, rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?): ExtractedUrlInfo? {
        if (rootNode == null) return null

        var rawUrlText: String? = null

        // 1. Package-specific target node lookups for high accuracy
        when {
            packageName == "com.android.chrome" -> {
                rawUrlText = findTextByViewId(rootNode, "com.android.chrome:id/url_bar")
                    ?: findTextByViewId(rootNode, "com.android.chrome:id/location_bar")
                    ?: findTextByViewId(rootNode, "com.android.chrome:id/search_box_text")
                    ?: findTextByViewId(rootNode, "com.android.chrome:id/title")
                    ?: findTextByViewId(rootNode, "com.android.chrome:id/line1")
                    ?: findTextByViewId(rootNode, "com.android.chrome:id/line_1")
            }
            packageName.startsWith("org.mozilla.firefox") || packageName.contains("fenix") -> {
                rawUrlText = findTextByViewId(rootNode, "org.mozilla.firefox:id/mozac_browser_toolbar_url_view")
                    ?: findTextByViewId(rootNode, "org.mozilla.firefox:id/url_bar_title")
                    ?: findTextByViewId(rootNode, "org.mozilla.fenix:id/mozac_browser_toolbar_url_view")
            }
            packageName == "com.sec.android.app.sbrowser" -> {
                rawUrlText = findTextByViewId(rootNode, "com.sec.android.app.sbrowser:id/location_bar_edit_text")
                    ?: findTextByViewId(rootNode, "com.sec.android.app.sbrowser:id/location_bar_text")
            }
            packageName == "com.microsoft.emmx" -> {
                rawUrlText = findTextByViewId(rootNode, "com.microsoft.emmx:id/url_bar")
                    ?: findTextByViewId(rootNode, "com.microsoft.emmx:id/search_box")
            }
            packageName.contains("opera") -> {
                rawUrlText = findTextByViewId(rootNode, "$packageName:id/url_field")
                    ?: findTextByViewId(rootNode, "$packageName:id/address")
            }
            packageName == "com.duckduckgo.mobile.android" -> {
                rawUrlText = findTextByViewId(rootNode, "com.duckduckgo.mobile.android:id/omnibarTextField")
            }
            packageName == "com.brave.browser" -> {
                rawUrlText = findTextByViewId(rootNode, "com.brave.browser:id/url_bar")
                    ?: findTextByViewId(rootNode, "com.brave.browser:id/location_bar")
            }
        }

        // 2. Generic tree fallback search if target lookup yielded null
        if (rawUrlText.isNull_or_blank()) {
            rawUrlText = searchUrlNodeGeneric(rootNode, maxDepth = 15)
        }

        val nonNullUrl = rawUrlText ?: return null
        val normalizedDomain = parseDomainFromUrl(nonNullUrl) ?: return null
        return ExtractedUrlInfo(
            rawUrl = nonNullUrl,
            domain = normalizedDomain
        )
    }

    private fun findTextByViewId(rootNode: AccessibilityNodeInfo, viewId: String): String? {
        return try {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString()
                    if (!text.isNullOrBlank()) {
                        return text
                    }
                    val desc = node.contentDescription?.toString()
                    if (!desc.isNullOrBlank() && isLikelyUrlOrDomain(desc)) {
                        return desc
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun searchUrlNodeGeneric(node: AccessibilityNodeInfo, maxDepth: Int, currentDepth: Int = 0): String? {
        if (currentDepth > maxDepth) return null

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""

        if (viewId.contains("url") || viewId.contains("location") || viewId.contains("omnibar") || viewId.contains("address") || viewId.contains("search")) {
            if (text.isNotBlank() && isLikelyUrlOrDomain(text)) {
                return text
            }
            if (desc.isNotBlank() && isLikelyUrlOrDomain(desc)) {
                return desc
            }
        }

        if (node.isEditable && text.isNotBlank() && isLikelyUrlOrDomain(text)) {
            return text
        }

        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("www.")) {
            return text
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val result = searchUrlNodeGeneric(child, maxDepth, currentDepth + 1)
            if (!result.isNullOrBlank()) {
                return result
            }
        }
        return null
    }

    private fun isLikelyUrlOrDomain(text: String): Boolean {
        val lower = text.trim().lowercase()
        if (lower.isBlank()) return false
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return true

        // Domain pattern: contains dot, no spaces, valid top-level domain ending (2-12 letters)
        if (lower.contains('.') && !lower.contains(' ') && !lower.contains('\n')) {
            val lastPart = lower.substringAfterLast('.')
            val slashIdx = lastPart.indexOf('/')
            val tld = if (slashIdx != -1) lastPart.substring(0, slashIdx) else lastPart
            if (tld.length in 2..12 && tld.all { it.isLetter() || it.isDigit() }) {
                return true
            }
        }
        return false
    }

    fun parseDomainFromUrl(input: String): String? {
        var clean = input.trim().lowercase()
        if (clean.isBlank()) return null

        // Ignore generic UI placeholder texts
        if (clean.contains("search or type") || clean.contains("search or enter") || clean.contains("type url")) {
            return null
        }

        if (clean.startsWith("https://")) clean = clean.substring(8)
        else if (clean.startsWith("http://")) clean = clean.substring(7)

        if (clean.startsWith("www.")) clean = clean.substring(4)

        val slashIdx = clean.indexOf('/')
        if (slashIdx != -1) clean = clean.substring(0, slashIdx)

        val queryIdx = clean.indexOf('?')
        if (queryIdx != -1) clean = clean.substring(0, queryIdx)

        val colonIdx = clean.indexOf(':')
        if (colonIdx != -1) clean = clean.substring(0, colonIdx)

        clean = clean.trim()
        if (clean.isBlank() || !clean.contains(".")) return null

        return clean
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}

data class ExtractedUrlInfo(
    val rawUrl: String,
    val domain: String
)
