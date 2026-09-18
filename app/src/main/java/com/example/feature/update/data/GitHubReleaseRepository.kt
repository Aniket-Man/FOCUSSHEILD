package com.example.feature.update.data

import com.example.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Reads the newest release from either the GitHub Releases API or a FocusShield-owned feed URL.
 *
 * ### Why there are two modes
 *
 * GitHub answers **404** to `releases/latest` for a private repository when the caller is
 * unauthenticated, and this app ships **no** GitHub credential: a token inside an APK can be
 * extracted, and a leaked token is worse than a disabled feature. So while
 * `Aniket-Man/FOCUSSHEILD` is private there is exactly one honest option — report that the feed is
 * unavailable, which the UI already does ("No published release was found"), and give the project a
 * supported way to make the feature live:
 *
 *  1. **Make the repository public**, or
 *  2. **Point the app at a feed URL** that serves the release JSON — a small backend, a Supabase
 *     Edge Function, or any static host. Set `UPDATE_FEED_URL` in `.env` and rebuild; the payload may
 *     be GitHub-shaped (`tag_name`, `assets[]`) or normalised (`versionName`, `apkUrl`,
 *     `releaseNotes`, `releasePageUrl`).
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

    override suspend fun fetchLatestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = buildUrl()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            // GitHub rejects requests without a User-Agent.
            .header("User-Agent", "FocusShield-Android")
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw UpdateSourceUnavailableException(
                "Couldn't reach the update server. Check your connection and try again."
            )
        }

        response.use { it.toResult() }
    }

    /** The URL actually requested: the configured feed, or the GitHub API for [repoSlug]. */
    internal fun buildUrl(): String {
        val configured = feedUrl.trim()
        if (configured.isEmpty()) return "$API_BASE/repos/$repoSlug/releases/latest"
        // A malformed override must fail loudly at the point of use rather than silently falling back
        // to GitHub (which would report "no release" for a private repo and hide the misconfiguration).
        return configured.toHttpUrlOrNull()?.toString()
            ?: throw UpdateSourceUnavailableException(
                "The update feed URL configured for this build is not a valid URL."
            )
    }

    /** Delegates the status/body decisions to [ReleaseResponseMapper] (unit-tested without a socket). */
    private fun Response.toResult(): UpdateInfo? = ReleaseResponseMapper.map(
        code = code,
        payload = body?.string().orEmpty(),
        fromGitHub = request.url.host.endsWith("github.com"),
        rateLimited = header("X-RateLimit-Remaining") == "0" || code == 429
    )

    companion object {
        /** Owner/repo of the default release feed. Overridable per build (`UPDATE_REPO_SLUG`). */
        const val DEFAULT_REPO_SLUG = "Aniket-Man/FOCUSSHEILD"

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
