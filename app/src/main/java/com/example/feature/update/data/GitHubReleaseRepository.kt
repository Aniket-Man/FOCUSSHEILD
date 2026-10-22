package com.example.feature.update.data

import com.example.BuildConfig
import com.example.feature.update.domain.UpdateInfo
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Reads the newest release from the public FocusShield releases repository or a feed URL.
 *
 * ### Where releases come from
 *
 * The source repository stays private; signed APKs and their metadata are published to the public
 * `Aniket-Man/FOCUSSHEILD-releases` repository. Its `releases/latest` endpoint is readable without
 * authentication, which is deliberate: this app ships **no** GitHub credential, because a token
 * inside an APK can be extracted and a leaked token is worse than a disabled feature.
 *
 * Two build-time options exist:
 *
 *  1. **Default:** the public releases repository configured by [DEFAULT_REPO_SLUG].
 *  2. **A feed URL** that serves the release JSON — a small backend, a Supabase Edge Function, or any
 *     static host. Set `UPDATE_FEED_URL` in `.env` and rebuild; the payload may be GitHub-shaped
 *     (`tag_name`, `assets[]`) or normalised (`versionName`, `apkUrl`, `releaseNotes`,
 *     `releasePageUrl`). The URL must be **https** — see [UpdateUrlPolicy].
 *
 * GitHub answers **404** to `releases/latest` both for an empty release list and for a repository
 * the caller cannot read. A 404 is therefore mapped to "nothing published" (→ UpToDate), never to a
 * failure — a release published only to the private source repository is silently invisible.
 *
 * `UPDATE_REPO_SLUG` overrides the repository for forks. Both are read at build time (see
 * `app/build.gradle.kts`), never at runtime — nothing about the update source is user-configurable,
 * so a malicious app or intent cannot redirect the updater.
 *
 * The HTTP client stays [OkHttpClient] — already a project dependency — and the request stays
 * unauthenticated in both modes.
 */
class GitHubReleaseRepository(
    private val repoSlug: String = DEFAULT_REPO_SLUG,
    /** Blank unless configured; when set, this URL replaces the GitHub API call entirely. */
    private val feedUrl: String = "",
    private val client: OkHttpClient = defaultClient()
) : UpdateRepository {

    override suspend fun fetchLatestRelease(): UpdateInfo? =
        fetchLatestReleaseConditional(etag = null).info

    override suspend fun fetchLatestReleaseConditional(etag: String?): ReleaseFetchResponse =
        withContext(Dispatchers.IO) {
            val url = buildUrl()
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                // GitHub rejects requests without a User-Agent.
                .header("User-Agent", "FocusShield-Android")
                .get()

            if (!etag.isNullOrBlank()) {
                requestBuilder.header("If-None-Match", etag)
            }

            val response = try {
                client.newCall(requestBuilder.build()).execute()
            } catch (e: IOException) {
                throw UpdateSourceUnavailableException(
                    "Couldn't reach the update server. Check your connection and try again."
                )
            }

            response.use { resp ->
                val responseEtag = resp.header("ETag")?.takeIf { it.isNotBlank() } ?: etag
                if (resp.code == 304) {
                    ReleaseFetchResponse(
                        info = null,
                        etag = responseEtag,
                        notModified = true
                    )
                } else {
                    ReleaseFetchResponse(
                        info = resp.toResult(),
                        etag = responseEtag,
                        notModified = false
                    )
                }
            }
        }

    /** The URL actually requested: the configured feed, or the GitHub API for [repoSlug]. */
    internal fun buildUrl(): String {
        val configured = feedUrl.trim()
        if (configured.isEmpty()) return "$API_BASE/repos/$repoSlug/releases/latest"
        // A malformed or cleartext override must fail loudly at the point of use rather than
        // silently falling back to GitHub (which would report "no release" for a private repo and
        // hide the misconfiguration) — and http:// would hand the update check to a
        // man-in-the-middle on the minSdk 24–27 devices still allowed to send cleartext.
        return UpdateUrlPolicy.httpsUrl(configured)?.toString()
            ?: throw UpdateSourceUnavailableException(
                "The update feed URL configured for this build is not a valid https URL."
            )
    }

    /**
     * Hosts, beyond GitHub's built-in set, this build may download an APK from: the configured
     * feed's own host, so a normalised feed served from its own domain stays installable. Empty for
     * the default GitHub source — its assets live on github.com, which [UpdateUrlPolicy] already
     * trusts.
     */
    fun apkHostAllowList(): Set<String> =
        UpdateUrlPolicy.httpsUrl(feedUrl.trim())?.host?.let { setOf(it) } ?: emptySet()

    /** Delegates the status/body decisions to [ReleaseResponseMapper] (unit-tested without a socket). */
    private fun Response.toResult(): UpdateInfo? = ReleaseResponseMapper.map(
        code = code,
        payload = body?.string().orEmpty(),
        rateLimited = header("X-RateLimit-Remaining") == "0" || code == 429,
        feedHost = request.url.host
    )

    companion object {
        /**
         * Owner/repo of the public release feed. The source repository is private, so releases are
         * published here and read unauthenticated. Overridable per build (`UPDATE_REPO_SLUG`).
         */
        const val DEFAULT_REPO_SLUG = "Aniket-Man/FOCUSSHEILD-releases"

        private const val API_BASE = "https://api.github.com"

        /**
         * Builds the repository the app should use for this build: the configured feed URL (if any)
         * and repository slug, read from `BuildConfig` (populated from `.env`).
         */
        fun fromConfig(): GitHubReleaseRepository = GitHubReleaseRepository(
            repoSlug = BuildConfig.UPDATE_REPO_SLUG.trim().ifBlank { DEFAULT_REPO_SLUG },
            feedUrl = BuildConfig.UPDATE_FEED_URL.trim()
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
