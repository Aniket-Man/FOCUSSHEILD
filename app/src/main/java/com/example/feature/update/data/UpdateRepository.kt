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
interface UpdateRepository {

    /**
     * The newest published release, or null when the source reports no releases at all.
     *
     * @throws UpdateSourceUnavailableException when the source could not be read — offline, rate
     *   limited, or (for a private repository reached without credentials) not found.
     */
    suspend fun fetchLatestRelease(): UpdateInfo?
}

/**
 * The release feed could not be read. [reason] is already user-presentable; callers surface it only
 * when the user asked for it explicitly, never as a spontaneous popup (prompt.txt §16).
 */
class UpdateSourceUnavailableException(val reason: String) : Exception(reason)
