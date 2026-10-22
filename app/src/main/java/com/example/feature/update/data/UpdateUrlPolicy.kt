package com.example.feature.update.data

import java.net.URLDecoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where the updater is allowed to fetch from: https only, on hosts this build actually trusts,
 * plus `firebase-chunked://` internal descriptors when downloading chunked APKs from an external
 * Firebase Firestore project.
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
 *  - **The configured feed's own host or external Firebase Storage host** may also serve the APK,
 *    so a normalised feed or external Firebase project keeps working.
 *
 * APK hosts are re-checked at download time even though [ReleaseJsonParser] already filtered them,
 * so a tampered cached offer cannot redirect the bytes.
 */
internal object UpdateUrlPolicy {

    const val FIREBASE_CHUNKED_SCHEME = "firebase-chunked://"

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

    /** True when [raw] is a Firestore chunked binary URI (`firebase-chunked://{projectId}/{releaseId}`). */
    fun isFirebaseChunkedUri(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(FIREBASE_CHUNKED_SCHEME, ignoreCase = true)) return false
        val rest = trimmed.substring(FIREBASE_CHUNKED_SCHEME.length).trim('/')
        val segments = rest.split('/').filter { it.isNotBlank() }
        return segments.size >= 2
    }

    /** True when [raw] is an https URL on a GitHub host, on one of [allowedHosts], or a valid chunked URI. */
    fun isAllowedApkUrl(raw: String, allowedHosts: Set<String>): Boolean {
        if (isFirebaseChunkedUri(raw) && allowedHosts.isNotEmpty()) return true
        return httpsUrl(raw)?.host?.let { it in GITHUB_HOSTS || it in allowedHosts } == true
    }

    /**
     * Checks whether [rawUrl] or [apkAssetName] points to an `.apk` binary, accounting for
     * URL-encoded Firebase Storage object paths (`...%2FFocusShield-v1.5.0.apk?alt=media&token=...`).
     */
    fun isApkResource(rawUrl: String, apkAssetName: String? = null): Boolean {
        if (isFirebaseChunkedUri(rawUrl)) return true
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
