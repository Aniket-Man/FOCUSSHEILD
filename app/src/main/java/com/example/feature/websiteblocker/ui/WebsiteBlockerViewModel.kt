package com.example.feature.websiteblocker.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.R
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.BlockedWebsiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class WebsiteBlockerUiState(
    val isAdultBlockingEnabled: Boolean = false,
    val isManualBlockingEnabled: Boolean = true,
    val manualBlockedWebsites: List<BlockedWebsiteEntity> = emptyList(),
    val isAccessibilityEnabled: Boolean = false,
    val addWebsiteInput: String = "",
    val inputErrorMessage: String? = null,
    val isAdding: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class WebsiteBlockerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: BlockedWebsiteRepository = (application as FocusShieldApp).blockedWebsiteRepository
    private val preferencesRepository: FocusPreferencesRepository = (application as FocusShieldApp).preferencesRepository

    private val _uiState = MutableStateFlow(WebsiteBlockerUiState())
    val uiState: StateFlow<WebsiteBlockerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.preferencesFlow,
                repository.allBlockedWebsites
            ) { prefs, websites ->
                prefs to websites
            }.collect { (prefs, websites) ->
                _uiState.update { current -> current.copy(
                    isAdultBlockingEnabled = prefs.isAutoAdultWebsiteBlockingEnabled,
                    isManualBlockingEnabled = prefs.isManualWebsiteBlockingEnabled,
                    manualBlockedWebsites = websites,
                    isAccessibilityEnabled = checkAccessibilityServiceEnabled(getApplication())
                ) }
            }
        }
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(isAccessibilityEnabled = checkAccessibilityServiceEnabled(context))
        }
    }

    private suspend fun isStrictProtectionLocked(): Boolean {
        return try {
            com.example.feature.applimits.engine.AppLimitStrictModeEngine.instance.isStrictProtectionLocked()
        } catch (_: Exception) {
            val active = com.example.feature.session.engine.FocusSessionManager.instance.activeSession.value
            active != null && (active.isRunning || active.isPaused) && active.isStrictModeEnabled
        }
    }

    fun toggleAdultBlocking(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isStrictProtectionLocked()) {
                _uiState.update {
                    it.copy(
                        errorMessage = message(R.string.website_strict_adult_error),
                        successMessage = null
                    )
                }
                return@launch
            }
            applyChange(R.string.website_change_error) {
                preferencesRepository.updateAutoAdultWebsiteBlocking(enabled)
            }
        }
    }

    fun toggleManualBlocking(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isStrictProtectionLocked()) {
                _uiState.update {
                    it.copy(
                        errorMessage = message(R.string.website_strict_manual_error),
                        successMessage = null
                    )
                }
                return@launch
            }
            applyChange(R.string.website_change_error) {
                preferencesRepository.updateManualWebsiteBlocking(enabled)
            }
        }
    }

    fun updateAddWebsiteInput(input: String) {
        _uiState.update { it.copy(addWebsiteInput = input, inputErrorMessage = null) }
    }

    fun addWebsite(rawInput: String) {
        if (_uiState.value.isAdding) return
        val normalizedDomain = BlockedWebsiteRepository.normalizeDomain(rawInput)
        if (normalizedDomain.isBlank() || !normalizedDomain.contains(".") ||
            normalizedDomain.any { it.isWhitespace() } || normalizedDomain.split('.').any { it.isBlank() }
        ) {
            _uiState.update {
                it.copy(inputErrorMessage = message(R.string.website_url_invalid), successMessage = null)
            }
            return
        }
        if (_uiState.value.manualBlockedWebsites.any { it.domain.equals(normalizedDomain, ignoreCase = true) }) {
            _uiState.update {
                it.copy(inputErrorMessage = message(R.string.website_already_added, normalizedDomain), successMessage = null)
            }
            return
        }
        _uiState.update { it.copy(isAdding = true, inputErrorMessage = null, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                val added = repository.addWebsite(normalizedDomain, category = "MANUAL")
                _uiState.update {
                    if (added) it.copy(
                        addWebsiteInput = "",
                        successMessage = message(R.string.website_added, normalizedDomain),
                        errorMessage = null
                    ) else it.copy(inputErrorMessage = message(R.string.website_save_error))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("WebsiteBlocker", "Unable to add website", error)
                _uiState.update { it.copy(inputErrorMessage = message(R.string.website_save_error)) }
            } finally {
                _uiState.update { it.copy(isAdding = false) }
            }
        }
    }

    fun deleteWebsite(domain: String) {
        viewModelScope.launch {
            if (isStrictProtectionLocked()) {
                _uiState.update {
                    it.copy(
                        errorMessage = message(R.string.website_strict_remove_error, domain),
                        successMessage = null
                    )
                }
                return@launch
            }
            applyChange(R.string.website_remove_error) {
                repository.removeWebsite(domain)
                _uiState.update { it.copy(successMessage = message(R.string.website_removed, domain), errorMessage = null) }
            }
        }
    }

    fun toggleWebsite(domain: String, isEnabled: Boolean) {
        viewModelScope.launch {
            if (!isEnabled && isStrictProtectionLocked()) {
                _uiState.update {
                    it.copy(
                        errorMessage = message(R.string.website_strict_disable_error, domain),
                        successMessage = null
                    )
                }
                return@launch
            }
            applyChange(R.string.website_change_error) { repository.toggleWebsite(domain, isEnabled) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, inputErrorMessage = null) }
    }

    fun reportPermissionLaunchFailure() {
        _uiState.update { it.copy(errorMessage = message(R.string.website_permission_open_error), successMessage = null) }
    }

    private fun message(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private suspend fun applyChange(@StringRes errorMessage: Int, action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("WebsiteBlocker", "Unable to change website blocking", error)
            _uiState.update { it.copy(errorMessage = message(errorMessage), successMessage = null) }
        }
    }

    private fun checkAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }
}
