package com.example.feature.update.data

import com.example.feature.update.domain.UpdateInfo

/**
 * Turns one HTTP response from the release feed into an [UpdateInfo] (or a user-presentable failure).
 *
 * Split out of [GitHubReleaseRepository] so every branch — private-repo 404, rate limiting, server
 * errors, garbage JSON, an asset that is not an APK — is unit-tested without a network or a socket
 * (issue 2: the updater's failure modes were previously only observable on a real device, and the
 * private-repository 404 was indistinguishable from "no release published").
 */
internal object ReleaseResponseMapper {

    /**
     * @param code HTTP status.
     * @param payload response body as text.
     * @param fromGitHub true when the request went to a `github.com` host (only then does a 404 mean
     *   "private repository, no credential").
     * @param rateLimited true when the response carried a rate-limit signal.
     * @return the release, or null when the feed is healthy and simply has nothing published.
     * @throws UpdateSourceUnavailableException for every other outcome; [UpdateSourceUnavailableException.reason]
     *   is already user-presentable.
     */
    fun map(code: Int, payload: String, fromGitHub: Boolean, rateLimited: Boolean): UpdateInfo? = when {
        code in 200..299 -> parse(payload)

        code == 404 -> throw UpdateSourceUnavailableException(
            if (fromGitHub) {
                // The exact situation this project is in: a private repository answers 404 to an
                // unauthenticated releases/latest, which looks identical to "no releases exist".
                "This build's release feed (GitHub) is not reachable without signing in. " +
                    "The project's releases may be private; use the project's release page instead."
            } else {
                "No published release was found at this build's update feed."
            }
        )

        code == 401 || code == 403 -> throw UpdateSourceUnavailableException(
            if (rateLimited) {
                "GitHub's update API is rate-limiting this device. Try again later."
            } else {
                "The update server refused the request. Try again later."
            }
        )

        else -> throw UpdateSourceUnavailableException("Couldn't check for updates (server said $code).")
    }

    private fun parse(payload: String): UpdateInfo? = try {
        ReleaseJsonParser.parse(payload)
    } catch (e: ReleasePayloadException) {
        throw UpdateSourceUnavailableException("The update server sent an unreadable response.")
    }
}
