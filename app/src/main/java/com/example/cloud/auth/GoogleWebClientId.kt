package com.example.cloud.auth

import android.content.Context

/**
 * The OAuth **web client ID** the google-services Gradle plugin generates into resources
 * when `app/google-services.json` is present.
 *
 * Deliberately resolved by name at runtime instead of referenced as
 * `R.string.default_web_client_id`: CI must build a fresh clone that has no
 * google-services.json (issue #31's contract), and a static reference to a generated
 * resource does not compile without it. On a developer machine the file is present and
 * this returns the exact same string; everywhere else it returns null and callers take
 * their "configuration missing" path.
 */
object GoogleWebClientId {

    private const val RESOURCE_NAME = "default_web_client_id"

    fun resolve(context: Context): String? {
        val id = context.resources.getIdentifier(RESOURCE_NAME, "string", context.packageName)
        if (id == 0) return null
        return runCatching { context.getString(id) }.getOrNull()
    }
}
