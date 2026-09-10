package com.example.cloud.http

import com.example.cloud.SupabaseConfig
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thrown for non-2xx Supabase responses. [code] mirrors GoTrue's machine-readable
 * `error_code` (e.g. `invalid_credentials`, `email_exists`) when one is present.
 */
class SupabaseApiException(
    val status: Int,
    val code: String?,
    override val message: String?
) : IOException(message)

/** Raised when cloud is not configured (see [SupabaseConfig.isConfigured]). */
class CloudNotConfiguredException : IOException("Supabase is not configured on this build")

/** Minimal, fully-buffered response wrapper — the body is read on the IO thread. */
data class HttpResult(val status: Int, val body: String)

/**
 * Thin OkHttp wrapper for Supabase REST endpoints. Responses are fully buffered on a
 * background thread so callers never block the main thread or leak response bodies.
 *
 * All calls go through the shared [SupabaseConfig]; the anonymous key is attached to every
 * request and a caller-supplied access token (when present) is sent as a Bearer token.
 */
class SupabaseHttp(private val config: SupabaseConfig) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Execute one request and return the buffered result. Throws [CloudNotConfiguredException]
     * if no Supabase project is configured, [SupabaseApiException] for non-2xx responses, and
     * `IOException` for transport-level failures (offline, timeouts, DNS).
     */
    suspend fun call(
        method: String,
        path: String,
        query: String = "",
        jsonBody: String? = null,
        accessToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): HttpResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) throw CloudNotConfiguredException()

        val url = buildUrl(path, query)
        val builder = Request.Builder()
            .url(url)
            .header("apikey", config.anonKey)
            .header("Accept", "application/json")
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
            builder.method(method, jsonBody.toRequestBody(jsonMediaType))
        } else {
            builder.method(method, null)
        }
        if (!accessToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string().orEmpty()
        val code = response.code
        response.close()

        if (code !in 200..299) {
            val (errorCode, message) = parseErrorBody(body)
            throw SupabaseApiException(status = code, code = errorCode, message = message)
        }
        HttpResult(code, body)
    }

    /**
     * Extract a machine-readable code + human message from a Supabase/GoTrue error body.
     * Handles both `{"code":..,"error_code":"..","msg":".."}` and the token-endpoint shape
     * `{"error":"invalid_grant","error_description":".."}`.
     */
    private fun parseErrorBody(body: String): Pair<String?, String?> {
        if (body.isBlank()) return null to null
        return try {
            val json = org.json.JSONObject(body)
            val code = json.optString("error_code").ifBlank {
                json.optString("code").ifBlank { null }
            }
            val message = json.optString("msg").ifBlank {
                json.optString("error_description").ifBlank {
                    json.optString("message").ifBlank { null }
                }
            }
            if (code == null && message == null) {
                val rawError = json.optString("error")
                if (rawError.isBlank()) null to null else rawError to rawError
            } else {
                code to message
            }
        } catch (_: Exception) {
            val truncated = body.take(300)
            null to truncated
        }
    }

    /**
     * Percent-encode a single query-string *value*. PostgREST filter syntax (`eq.`, `=`)
     * is preserved by keeping the surrounding operator string literal in the caller and only
     * encoding the value here.
     */
    fun encodeValue(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun buildUrl(path: String, query: String): HttpUrl {
        val base = config.baseUrl.toHttpUrlOrNull()
            ?: throw CloudNotConfiguredException()
        val b = base.newBuilder()
        if (path.isNotBlank()) b.encodedPath(path)
        if (query.isNotBlank()) b.encodedQuery(query)
        return b.build()
    }
}
