package com.example.feature.update.data

import com.example.feature.update.domain.UpdateInfo

/**
 * Turns one HTTP response from the release feed into an [UpdateInfo] (or a user-presentable failure).
 *
 * Split out of [GitHubReleaseRepository] so every branch — rate limiting, server errors, garbage
 * JSON, an asset that is not an APK — is unit-tested without a network or a socket (issue 2: the
 * updater's failure modes were previously only observable on a real device).
 */
internal object ReleaseResponseMapper {

    /**
     * @param code HTTP status.
     * @param payload response body as text.
     * @param rateLimited true when the response carried a rate-limit signal.
     * @param feedHost host the request actually went to; APK assets on it are trusted alongside
     *   GitHub's own hosts (see [UpdateUrlPolicy]).
     * @return the release, or null when the feed is healthy and simply has nothing published.
     *   A **404** lands here too: GitHub answers 404 for an empty release list, and also for a
     *   private repository this credential-less build cannot read — either way there is nothing to
     *   install, so the checker reports UpToDate. Throwing instead marked every such check failed
     *   and drove a 15-minute retry loop whose only visible symptom was a failed manual check.
     * @throws UpdateSourceUnavailableException for every other outcome; [UpdateSourceUnavailableException.reason]
     *   is already user-presentable.
     */
    fun map(code: Int, payload: String, rateLimited: Boolean, feedHost: String = ""): UpdateInfo? = when {
        code in 200..299 -> parse(payload, feedHost)

        code == 404 -> null

        code == 401 || code == 403 -> throw UpdateSourceUnavailableException(
            if (rateLimited) {
                "GitHub's update API is rate-limiting this device. Try again later."
            } else {
                "The update server refused the request. Try again later."
            }
        )

        else -> throw UpdateSourceUnavailableException("Couldn't check for updates (server said $code).")
    }

    private fun parse(payload: String, feedHost: String): UpdateInfo? = try {
        ReleaseJsonParser.parse(payload, feedHost)
    } catch (e: ReleasePayloadException) {
        throw UpdateSourceUnavailableException("The update server sent an unreadable response.")
    }
}
