package com.example.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Generic sealed hierarchy representing asynchronous resource states for UI consumption.
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}

/**
 * Base [ViewModel] providing standardized [StateFlow] state management, a dedicated [loading]
 * [StateFlow], and coroutine launching with structured error handling.
 *
 * @param S The immutable UI state type managed by the subclass.
 * @param initialState The initial value emitted by [uiState].
 */
abstract class BaseViewModel<S>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)

    /**
     * Publicly exposed read-only [StateFlow] of the current screen state.
     */
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _loading = MutableStateFlow(false)

    /**
     * Publicly exposed [StateFlow] representing whether an operation is currently loading.
     */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /**
     * Snapshot of the latest state value.
     */
    protected val currentState: S
        get() = _uiState.value

    /**
     * Updates the [loading] state emitted to the UI.
     */
    protected fun setLoading(isLoading: Boolean) {
        _loading.value = isLoading
        onLoadingChanged(isLoading)
    }

    /**
     * Optional hook allowing subclasses to synchronize the [loading] flag inside their custom [S] state.
     */
    protected open fun onLoadingChanged(isLoading: Boolean) = Unit

    /**
     * Atomically updates the current UI state using the provided [reducer].
     */
    protected fun updateState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }

    /**
     * Launches a coroutine in [viewModelScope] with optional automatic [loading] state management
     * and structured exception handling, ensuring [CancellationException] is propagated normally.
     */
    protected fun launchCatching(
        trackLoading: Boolean = true,
        onError: (Throwable) -> Unit = ::onUnhandledError,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            if (trackLoading) setLoading(false)
            onError(throwable)
        }
        return viewModelScope.launch(exceptionHandler) {
            if (trackLoading) setLoading(true)
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                onError(t)
            } finally {
                if (trackLoading) setLoading(false)
            }
        }
    }

    /**
     * Hook for subclasses to handle uncaught coroutine errors.
     */
    protected open fun onUnhandledError(throwable: Throwable) {
        // Default no-op; subclasses can override to log or update error state.
    }
}
