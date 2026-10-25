package com.example.data.remote

import com.example.data.remote.model.GitHubReleaseDto
import com.example.data.remote.model.QuoteResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * Retrofit interface defining the remote API endpoints used across the application.
 */
interface ApiService {

    /**
     * Fetches the latest published release for a specific GitHub repository.
     *
     * @param owner The organization or user owning the repository (e.g. "Aniket-Man").
     * @param repo The repository name (e.g. "FOCUSSHEILD-releases").
     * @param etag Optional ETag for conditional fetching (HTTP 304).
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("If-None-Match") etag: String? = null
    ): Response<GitHubReleaseDto>

    /**
     * Fetches release information from an arbitrary or custom normalized feed URL.
     *
     * @param feedUrl The complete https URL to the release JSON feed.
     * @param etag Optional ETag for conditional fetching.
     */
    @GET
    suspend fun getReleaseFromFeedUrl(
        @Url feedUrl: String,
        @Header("If-None-Match") etag: String? = null
    ): Response<GitHubReleaseDto>

    /**
     * Fetches a motivational quote from a remote quotes API endpoint.
     */
    @GET("quotes/random")
    suspend fun getRandomQuote(): Response<QuoteResponseDto>
}
