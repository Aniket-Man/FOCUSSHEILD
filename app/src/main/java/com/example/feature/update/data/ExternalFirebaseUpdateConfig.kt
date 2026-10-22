package com.example.feature.update.data

import android.util.Base64
import com.example.BuildConfig
import com.example.feature.update.domain.UpdateChannel
import org.json.JSONObject

/**
 * Configuration model for connecting FocusShield's update system to the release server.
 */
data class ExternalFirebaseUpdateConfig(
    val projectId: String = DEFAULT_PROJECT_ID,
    val apiKey: String = "",
    val storageBucket: String = DEFAULT_STORAGE_BUCKET,
    val appId: String = DEFAULT_APP_ID,
    val databaseId: String = DEFAULT_DATABASE_ID,
    val channelToken: String = DEFAULT_CHANNEL,
    val sourceLabel: String = "Release Server"
) {
    val isValid: Boolean
        get() = projectId.isNotBlank() && projectId != "remixed-project-id"

    val normalizedDatabaseId: String
        get() = databaseId.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_DATABASE_ID

    val normalizedStorageBucket: String
        get() = storageBucket.trim()
            .removePrefix("gs://")
            .removeSuffix("/")
            .ifBlank { "$projectId.firebasestorage.app" }

    val normalizedChannel: String
        get() = UpdateChannel.normalizeKey(channelToken)

    val selectedUpdateChannel: UpdateChannel
        get() = UpdateChannel.fromKey(channelToken)

    fun withChannel(channel: UpdateChannel): ExternalFirebaseUpdateConfig =
        copy(channelToken = channel.key)

    fun withChannelKey(channelKey: String): ExternalFirebaseUpdateConfig =
        copy(channelToken = UpdateChannel.normalizeKey(channelKey))

    /** Short fingerprint of the connection token for internal diagnostics. */
    val tokenPreview: String
        get() {
            val token = toConnectionToken()
            return if (token.length > 22) {
                "${token.take(14)}…${token.takeLast(6)}"
            } else {
                token
            }
        }

    fun toJsonString(): String = JSONObject().apply {
        put("projectId", projectId.trim())
        put("apiKey", apiKey.trim())
        put("storageBucket", normalizedStorageBucket)
        put("appId", appId.trim())
        put("databaseId", normalizedDatabaseId)
        put("channel", normalizedChannel)
        put("sourceLabel", sourceLabel)
    }.toString()

    /** Encodes this configuration into a single portable `fsupd_...` connection token. */
    fun toConnectionToken(): String {
        val payload = JSONObject().apply {
            put("projectId", projectId.trim())
            put("apiKey", apiKey.trim())
            put("storageBucket", normalizedStorageBucket)
            put("appId", appId.trim())
            put("databaseId", normalizedDatabaseId)
            put("channel", normalizedChannel)
        }.toString()
        val encoded = try {
            Base64.encodeToString(
                payload.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        } catch (_: Throwable) {
            java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toByteArray(Charsets.UTF_8))
        }
        return "$TOKEN_PREFIX$encoded"
    }

    /** Additional HTTPS hosts allowed to serve APK binaries for this release server. */
    fun apkHostAllowList(): Set<String> = buildSet {
        add("firebasestorage.googleapis.com")
        add("storage.googleapis.com")
        if (projectId.isNotBlank()) {
            add("$projectId.firebasestorage.app")
            add("$projectId.appspot.com")
            add("$projectId.web.app")
            add("$projectId.firebaseapp.com")
        }
        val bucket = normalizedStorageBucket
        if (bucket.isNotBlank() && '.' in bucket) {
            add(bucket)
        }
    }

    companion object {
        const val TOKEN_PREFIX = "fsupd_"
        const val DEFAULT_PROJECT_ID = "eminent-physics-z8p5g"
        const val DEFAULT_STORAGE_BUCKET = "eminent-physics-z8p5g.firebasestorage.app"
        const val DEFAULT_APP_ID = "1:676324793849:web:a1523e5318fd333719b128"
        const val DEFAULT_DATABASE_ID = "ai-studio-focusshieldrelea-3363add2-5434-4c84-81c2-0bc738e40dc8"
        const val DEFAULT_CHANNEL = "production"

        /**
         * Reads the compile-time configuration from `BuildConfig` (populated from `.env` / Secrets)
         * and falls back to the built-in release server defaults so the update server remains
         * permanently connected.
         */
        fun fromBuildConfig(channelOverride: String? = null): ExternalFirebaseUpdateConfig {
            val resolvedChannel = channelOverride?.takeIf { it.isNotBlank() }?.let(UpdateChannel::normalizeKey)

            val connectionToken = BuildConfig.UPDATE_CONNECTION_TOKEN.trim()
            if (connectionToken.isNotEmpty()) {
                parseFlexibleInput(connectionToken, sourceLabel = "Release Server")?.let { parsed ->
                    if (parsed.isValid) {
                        return if (resolvedChannel != null) parsed.withChannelKey(resolvedChannel) else parsed
                    }
                }
            }

            val projectId = BuildConfig.UPDATE_FIREBASE_PROJECT_ID.trim().ifBlank { DEFAULT_PROJECT_ID }
            val storageBucket = BuildConfig.UPDATE_FIREBASE_STORAGE_BUCKET.trim().ifBlank { DEFAULT_STORAGE_BUCKET }
            val appId = BuildConfig.UPDATE_FIREBASE_APP_ID.trim().ifBlank { DEFAULT_APP_ID }
            val databaseId = BuildConfig.UPDATE_FIREBASE_DATABASE_ID.trim().ifBlank { DEFAULT_DATABASE_ID }
            val defaultChannel = BuildConfig.UPDATE_CHANNEL_TOKEN.trim().ifBlank { DEFAULT_CHANNEL }

            return ExternalFirebaseUpdateConfig(
                projectId = projectId,
                apiKey = BuildConfig.UPDATE_FIREBASE_API_KEY.trim(),
                storageBucket = storageBucket,
                appId = appId,
                databaseId = databaseId,
                channelToken = resolvedChannel ?: UpdateChannel.normalizeKey(defaultChannel),
                sourceLabel = "Release Server"
            )
        }

        /**
         * Parses a flexible user-provided credential string:
         * - `fsupd_<base64>` portable connection token
         * - Raw JSON object (`firebase-applet-config.json` or Firebase Web SDK config)
         * - `.env` key=value lines (`UPDATE_FIREBASE_PROJECT_ID=...`)
         */
        fun parseFlexibleInput(
            rawInput: String,
            sourceLabel: String = "In-App Token"
        ): ExternalFirebaseUpdateConfig? {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return null

            // 1. Check for `fsupd_` prefixed Base64 token
            if (trimmed.startsWith(TOKEN_PREFIX, ignoreCase = true)) {
                val base64Part = trimmed.substring(TOKEN_PREFIX.length).trim()
                val decoded = decodeBase64Flexible(base64Part) ?: return null
                return parseJsonConfig(decoded, sourceLabel)
            }

            // 2. Check if the input contains a JSON block `{ ... }`
            val firstBrace = trimmed.indexOf('{')
            val lastBrace = trimmed.lastIndexOf('}')
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                val jsonCandidate = trimmed.substring(firstBrace, lastBrace + 1)
                parseJsonConfig(jsonCandidate, sourceLabel)?.let { return it }
            }

            // 3. Check if the input is `.env` style key=value pairs
            if (trimmed.contains("=") && (
                    trimmed.contains("PROJECT_ID", ignoreCase = true) ||
                        trimmed.contains("projectId", ignoreCase = true)
                    )
            ) {
                parseEnvKeyValues(trimmed, sourceLabel)?.let { return it }
            }

            // 4. Check if it is raw unprefixed Base64 JSON
            decodeBase64Flexible(trimmed)?.let { decoded ->
                if (decoded.trim().startsWith("{")) {
                    parseJsonConfig(decoded, sourceLabel)?.let { return it }
                }
            }

            return null
        }

        fun fromStoredJson(rawJson: String?): ExternalFirebaseUpdateConfig? {
            if (rawJson.isNullOrBlank()) return null
            return parseJsonConfig(rawJson, defaultSourceLabel = "In-App Token")
        }

        private fun parseJsonConfig(jsonString: String, defaultSourceLabel: String): ExternalFirebaseUpdateConfig? {
            return try {
                val json = JSONObject(jsonString)
                val projectId = json.optString("projectId")
                    .ifBlank { json.optString("project_id") }
                    .ifBlank { json.optString("UPDATE_FIREBASE_PROJECT_ID") }
                    .trim()
                if (projectId.isBlank()) return null

                val apiKey = json.optString("apiKey")
                    .ifBlank { json.optString("api_key") }
                    .ifBlank { json.optString("UPDATE_FIREBASE_API_KEY") }
                    .trim()
                val storageBucket = json.optString("storageBucket")
                    .ifBlank { json.optString("storage_bucket") }
                    .ifBlank { json.optString("UPDATE_FIREBASE_STORAGE_BUCKET") }
                    .trim()
                val appId = json.optString("appId")
                    .ifBlank { json.optString("app_id") }
                    .ifBlank { json.optString("UPDATE_FIREBASE_APP_ID") }
                    .trim()
                val databaseId = json.optString("databaseId")
                    .ifBlank { json.optString("firestoreDatabaseId") }
                    .ifBlank { json.optString("database_id") }
                    .ifBlank { json.optString("UPDATE_FIREBASE_DATABASE_ID") }
                    .trim()
                    .ifBlank { DEFAULT_DATABASE_ID }
                val channel = json.optString("channel")
                    .ifBlank { json.optString("channelToken") }
                    .ifBlank { json.optString("UPDATE_CHANNEL_TOKEN") }
                    .trim()
                    .ifBlank { DEFAULT_CHANNEL }
                val source = json.optString("sourceLabel").trim().ifBlank { defaultSourceLabel }

                ExternalFirebaseUpdateConfig(
                    projectId = projectId,
                    apiKey = apiKey,
                    storageBucket = storageBucket,
                    appId = appId,
                    databaseId = databaseId,
                    channelToken = channel,
                    sourceLabel = source
                ).takeIf { it.isValid }
            } catch (_: Exception) {
                null
            }
        }

        private fun parseEnvKeyValues(raw: String, sourceLabel: String): ExternalFirebaseUpdateConfig? {
            val map = mutableMapOf<String, String>()
            raw.lines().forEach { line ->
                val cleaned = line.trim().removePrefix("export ").trim()
                if (cleaned.isEmpty() || cleaned.startsWith("#")) return@forEach
                val eq = cleaned.indexOf('=')
                if (eq > 0) {
                    val key = cleaned.substring(0, eq).trim()
                    val value = cleaned.substring(eq + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                    map[key] = value
                }
            }
            val token = map["UPDATE_CONNECTION_TOKEN"]
            if (!token.isNullOrBlank()) {
                parseFlexibleInput(token, sourceLabel)?.let { return it }
            }
            val projectId = (map["UPDATE_FIREBASE_PROJECT_ID"] ?: map["FIREBASE_PROJECT_ID"] ?: map["projectId"]).orEmpty().trim()
            if (projectId.isBlank()) return null

            return ExternalFirebaseUpdateConfig(
                projectId = projectId,
                apiKey = (map["UPDATE_FIREBASE_API_KEY"] ?: map["FIREBASE_API_KEY"] ?: map["apiKey"]).orEmpty().trim(),
                storageBucket = (map["UPDATE_FIREBASE_STORAGE_BUCKET"] ?: map["FIREBASE_STORAGE_BUCKET"] ?: map["storageBucket"]).orEmpty().trim(),
                appId = (map["UPDATE_FIREBASE_APP_ID"] ?: map["FIREBASE_APP_ID"] ?: map["appId"]).orEmpty().trim(),
                databaseId = (map["UPDATE_FIREBASE_DATABASE_ID"] ?: map["FIREBASE_DATABASE_ID"] ?: map["databaseId"]).orEmpty().trim().ifBlank { DEFAULT_DATABASE_ID },
                channelToken = (map["UPDATE_CHANNEL_TOKEN"] ?: map["channel"]).orEmpty().trim().ifBlank { DEFAULT_CHANNEL },
                sourceLabel = sourceLabel
            ).takeIf { it.isValid }
        }

        private fun decodeBase64Flexible(encoded: String): String? {
            val sanitized = encoded.trim()
            if (sanitized.isEmpty()) return null
            return try {
                val bytes = try {
                    Base64.decode(sanitized, Base64.URL_SAFE or Base64.NO_WRAP)
                } catch (_: Throwable) {
                    val urlNormalized = sanitized.replace('-', '+').replace('_', '/')
                    val padded = when (urlNormalized.length % 4) {
                        2 -> "$urlNormalized=="
                        3 -> "$urlNormalized="
                        else -> urlNormalized
                    }
                    java.util.Base64.getDecoder().decode(padded)
                }
                String(bytes, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }
}
