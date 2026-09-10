package com.example.cloud

import com.example.BuildConfig

/**
 * Central access to the optional Supabase configuration injected at build time from
 * `local.properties` (gitignored) via `buildConfigField`. Both values are empty strings
 * when the developer has not configured cloud — the app then runs fully offline and every
 * cloud/account entry point reports itself as not configured instead of crashing.
 *
 * The anon key is a *publishable* client key (Supabase generates it for in-app use); the
 * service_role key and the database password must never be embedded in the app.
 */
object SupabaseConfig {
    /** e.g. https://<project-ref>.supabase.co */
    val baseUrl: String get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    /** Supabase anonymous / publishable key. */
    val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") && anonKey.isNotBlank()
}
