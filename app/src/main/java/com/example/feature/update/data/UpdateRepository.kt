package com.example.feature.update.data

import com.example.feature.update.domain.UpdateInfo

/**
 * Where FocusShield learns about newer releases.
 *
 * This interface exists because of a hard constraint: the FocusShield repository is **private**, and
 * GitHub's unauthenticated `releases/latest` endpoint returns 404 for private repositories. Embedding
 * a personal access token in the APK to work around that is not acceptable — anything inside an APK
 * can be extracted — so no credential is shipped (prompt.txt §15).
 *
 * The source is therefore swappable: [GitHubReleaseRepository] today, and a FocusShield backend
 * endpoint later once one exists, with no change to the checker or the UI. See
 * `docs/UPDATE_SYSTEM.md` for what to change when the repository is made public.
 */
/**
 * Outcome of fetching the latest release, with optional HTTP ETag conditional-check metadata.
 *
 * When [notModified] is true, the server responded with HTTP 304 (`Not Modified`), which does not
 * consume GitHub's 60 requests/hour unauthenticated rate limit and means the previously cached
 * release is still current.
 */
data class ReleaseFetchResponse(
    val info: UpdateInfo?,
    val etag: String? = null,
    val notModified: Boolean = false
)

interface UpdateRepository {

    /**
     * The newest published release, or null when the source reports no releases at all — including
     * GitHub's 404 for an empty release list or a private repository this build cannot read.
     *
     * @throws UpdateSourceUnavailableException when the source could not be read — offline, rate
     *   limited, or otherwise unreadable (but never a plain "not found": that is `null`).
     */
    suspend fun fetchLatestRelease(): UpdateInfo?

    /**
     * Conditional fetch supporting `If-None-Match` ([etag]).
     * Defaults to calling [fetchLatestRelease] for custom/test implementations.
     */
    suspend fun fetchLatestReleaseConditional(etag: String?): ReleaseFetchResponse =
        ReleaseFetchResponse(info = fetchLatestRelease(), etag = null, notModified = false)

    /**
     * Returns all published releases (newest-first) for the Release History view.
     * Defaults to wrapping [fetchLatestRelease] when only a single release endpoint exists.
     */
    suspend fun fetchReleaseHistory(): List<UpdateInfo> =
        listOfNotNull(fetchLatestRelease())
}

/**
 * The release feed could not be read. [reason] is already user-presentable; callers surface it only
 * when the user asked for it explicitly, never as a spontaneous popup (prompt.txt §16).
 */
class UpdateSourceUnavailableException(val reason: String) : Exception(reason)

