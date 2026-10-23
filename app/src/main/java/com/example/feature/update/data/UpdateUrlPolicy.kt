package com.example.feature.update.data

import java.net.URLDecoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where the updater is allowed to fetch from: https only, on hosts this build actually trusts.
 *
 * Three rules, applied wherever a URL enters the update flow (the feed URL, release-payload APK
 * links, and the download request itself):
 *
 *  - **https, never http.** This app supports minSdk 24–27, where the platform's default network
 *    security config still permits cleartext, so an `http://` release URL is a
 *    man-in-the-middle APK swap waiting to happen. The `.apk`-suffix and package-name checks
 *    validate the *file*, not the transport — they do not help here.
 *  - **Exact host membership, never suffix matching.** `host.endsWith("github.com")` also accepts
 *    `evilgithub.com`; [GITHUB_HOSTS] is a set of full host names.
 *  - **The configured feed's own host** may also serve the APK, so a normalised feed keeps working.
 *
 * APK hosts are re-checked at download time even though [ReleaseJsonParser] already filtered them,
 * so a tampered cached offer cannot redirect the bytes.
 */
internal object UpdateUrlPolicy {

    /** Hosts a GitHub release flow legitimately touches. Exact names — no suffix matching. */
    private val GITHUB_HOSTS = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    /**
     * Parses [raw] when it is a well-formed https URL with a host; null for http, ftp, or garbage.
     * Every URL the updater acts on must pass through here first.
     */
    fun httpsUrl(raw: String): HttpUrl? {
        val url = raw.toHttpUrlOrNull() ?: return null
        return url.takeIf { it.scheme == "https" && it.host.isNotEmpty() }
    }

    /** True when [raw] is an https URL on a GitHub host or on one of [allowedHosts]. */
    fun isAllowedApkUrl(raw: String, allowedHosts: Set<String>): Boolean {
        return httpsUrl(raw)?.host?.let { it in GITHUB_HOSTS || it in allowedHosts } == true
    }

    /**
     * Checks whether [rawUrl] or [apkAssetName] points to an `.apk` binary.
     */
    fun isApkResource(rawUrl: String, apkAssetName: String? = null): Boolean {
        if (apkAssetName?.trim()?.endsWith(".apk", ignoreCase = true) == true) return true
        val withoutQuery = rawUrl.substringBefore('?').substringBefore('#')
        if (withoutQuery.endsWith(".apk", ignoreCase = true)) return true
        return try {
            val decoded = URLDecoder.decode(withoutQuery, "UTF-8")
            decoded.endsWith(".apk", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
}
