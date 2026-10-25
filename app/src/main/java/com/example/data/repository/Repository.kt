package com.example.data.repository

import com.example.data.remote.ApiService
import com.example.data.remote.model.GitHubReleaseDto
import com.example.data.remote.model.QuoteResponseDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response

/**
 * Repository that abstracts remote data operations performed via [ApiService].
 */
@Singleton
class Repository @Inject constructor(
    private val apiService: ApiService
) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Fetches the latest release for the given GitHub repository [owner] and [repo].
     */
    suspend fun fetchLatestRelease(
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO,
        etag: String? = null
    ): Result<GitHubReleaseDto> = safeApiCall {
        apiService.getLatestRelease(owner = owner, repo = repo, etag = etag)
    }

    /**
     * Fetches release details from a custom normalized HTTPS [feedUrl].
     */
    suspend fun fetchReleaseFromFeed(
        feedUrl: String,
        etag: String? = null
    ): Result<GitHubReleaseDto> = safeApiCall {
        apiService.getReleaseFromFeedUrl(feedUrl = feedUrl, etag = etag)
    }

    /**
     * Fetches a random motivational quote from the remote endpoint.
     */
    suspend fun fetchRandomQuote(): Result<QuoteResponseDto> = safeApiCall {
        apiService.getRandomQuote()
    }

    private suspend fun <T> safeApiCall(
        call: suspend () -> Response<T>
    ): Result<T> = withContext(ioDispatcher) {
        runCatching {
            val response = call()
            if (response.isSuccessful) {
                response.body() ?: throw IllegalStateException("Response body was null (HTTP ${response.code()})")
            } else {
                throw HttpException(response)
            }
        }
    }

    companion object {
        const val DEFAULT_OWNER = "Aniket-Man"
        const val DEFAULT_REPO = "FOCUSSHEILD-releases"
    }
}
