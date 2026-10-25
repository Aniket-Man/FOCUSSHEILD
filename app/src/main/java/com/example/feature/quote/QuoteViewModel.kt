package com.example.feature.quote

import androidx.lifecycle.viewModelScope
import com.example.cloud.sync.SyncWorker
import com.example.cloud.sync.SyncWorkerStatus
import com.example.core.ui.BaseViewModel
import com.example.core.ui.UiState
import com.example.core.util.NetworkConnectionManager
import com.example.data.remote.model.GitHubReleaseDto
import com.example.data.remote.model.QuoteResponseDto
import com.example.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * UI state exposed by [QuoteViewModel] via [StateFlow].
 */
data class QuoteUiState(
    val isLoading: Boolean = false,
    val isConnected: Boolean = true,
    val quoteState: UiState<QuoteResponseDto> = UiState.Idle,
    val latestReleaseState: UiState<GitHubReleaseDto> = UiState.Idle,
    val syncStatus: SyncWorkerStatus = SyncWorkerStatus(),
    val isRefreshing: Boolean = false
)

/**
 * Feature ViewModel extending [BaseViewModel] that uses [Repository], [NetworkConnectionManager],
 * and [SyncWorker] to fetch remote quote and release data while tracking real-time connectivity,
 * loading state, and synchronization freshness.
 */
@HiltViewModel
class QuoteViewModel @Inject constructor(
    private val repository: Repository,
    private val networkConnectionManager: NetworkConnectionManager
) : BaseViewModel<QuoteUiState>(
    QuoteUiState(
        isConnected = networkConnectionManager.checkCurrentConnectivity(),
        syncStatus = SyncWorker.syncStatus.value
    )
) {

    /**
     * Real-time internet connectivity stream exposed directly from [NetworkConnectionManager].
     */
    val isConnected: StateFlow<Boolean> = networkConnectionManager.isConnected

    /**
     * Real-time synchronization status stream exposed from [SyncWorker].
     */
    val syncStatus: StateFlow<SyncWorkerStatus> = SyncWorker.syncStatus

    init {
        observeConnectivity()
        observeSyncStatus()
        if (networkConnectionManager.checkCurrentConnectivity()) {
            fetchRandomQuote()
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            networkConnectionManager.isConnected.collect { connected ->
                val wasOffline = !currentState.isConnected
                updateState { it.copy(isConnected = connected) }

                if (connected && wasOffline &&
                    (currentState.quoteState is UiState.Idle || currentState.quoteState is UiState.Error)
                ) {
                    fetchRandomQuote()
                }
            }
        }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            SyncWorker.syncStatus.collect { status ->
                updateState { it.copy(syncStatus = status) }
            }
        }
    }

    override fun onLoadingChanged(isLoading: Boolean) {
        updateState { it.copy(isLoading = isLoading) }
    }

    /**
     * Fetches a random motivational quote from the repository and updates [uiState].
     */
    fun fetchRandomQuote() {
        if (!networkConnectionManager.checkCurrentConnectivity()) {
            updateState {
                it.copy(
                    isConnected = false,
                    quoteState = UiState.Error("No internet connection available"),
                    isRefreshing = false
                )
            }
            return
        }

        updateState { it.copy(quoteState = UiState.Loading) }
        launchCatching(
            trackLoading = true,
            onError = { error ->
                updateState {
                    it.copy(
                        quoteState = UiState.Error(
                            message = error.localizedMessage ?: "Failed to load quote",
                            cause = error
                        ),
                        isRefreshing = false
                    )
                }
            }
        ) {
            repository.fetchRandomQuote()
                .onSuccess { quote ->
                    SyncWorker.recordSyncSuccess()
                    updateState {
                        it.copy(
                            quoteState = UiState.Success(quote),
                            isRefreshing = false
                        )
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            quoteState = UiState.Error(
                                message = error.localizedMessage ?: "Failed to load quote",
                                cause = error
                            ),
                            isRefreshing = false
                        )
                    }
                }
        }
    }

    /**
     * Fetches the latest GitHub release metadata from the repository and updates [uiState].
     */
    fun fetchLatestRelease(
        owner: String = Repository.DEFAULT_OWNER,
        repo: String = Repository.DEFAULT_REPO
    ) {
        if (!networkConnectionManager.checkCurrentConnectivity()) {
            updateState {
                it.copy(
                    isConnected = false,
                    latestReleaseState = UiState.Error("No internet connection available"),
                    isRefreshing = false
                )
            }
            return
        }

        updateState { it.copy(latestReleaseState = UiState.Loading) }
        launchCatching(
            trackLoading = true,
            onError = { error ->
                updateState {
                    it.copy(
                        latestReleaseState = UiState.Error(
                            message = error.localizedMessage ?: "Failed to check latest release",
                            cause = error
                        ),
                        isRefreshing = false
                    )
                }
            }
        ) {
            repository.fetchLatestRelease(owner = owner, repo = repo)
                .onSuccess { release ->
                    SyncWorker.recordSyncSuccess()
                    updateState {
                        it.copy(
                            latestReleaseState = UiState.Success(release),
                            isRefreshing = false
                        )
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            latestReleaseState = UiState.Error(
                                message = error.localizedMessage ?: "Failed to check latest release",
                                cause = error
                            ),
                            isRefreshing = false
                        )
                    }
                }
        }
    }

    /**
     * Triggers a reload of quote and release data.
     */
    fun refresh() {
        updateState { it.copy(isRefreshing = true) }
        fetchRandomQuote()
        fetchLatestRelease()
    }

    override fun onUnhandledError(throwable: Throwable) {
        updateState {
            it.copy(
                quoteState = UiState.Error(
                    message = throwable.localizedMessage ?: "An unexpected error occurred",
                    cause = throwable
                ),
                isRefreshing = false
            )
        }
    }
}
