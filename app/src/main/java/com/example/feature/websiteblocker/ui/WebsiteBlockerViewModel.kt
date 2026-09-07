package com.example.feature.websiteblocker.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.BlockedWebsiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebsiteBlockerUiState(
    val isAutoAdultBlockingEnabled: Boolean = true,
    val isManualBlockingEnabled: Boolean = true,
    val manualBlockedWebsites: List<BlockedWebsiteEntity> = emptyList(),
    val isAccessibilityEnabled: Boolean = false,
    val addWebsiteInput: String = "",
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
                WebsiteBlockerUiState(
                    isAutoAdultBlockingEnabled = prefs.isAutoAdultWebsiteBlockingEnabled,
                    isManualBlockingEnabled = prefs.isManualWebsiteBlockingEnabled,
                    manualBlockedWebsites = websites,
                    isAccessibilityEnabled = checkAccessibilityServiceEnabled(getApplication()),
                    addWebsiteInput = _uiState.value.addWebsiteInput,
                    errorMessage = _uiState.value.errorMessage,
                    successMessage = _uiState.value.successMessage
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun checkPermissions() {
        _uiState.update {
            it.copy(isAccessibilityEnabled = checkAccessibilityServiceEnabled(getApplication()))
        }
    }

    fun toggleAutoAdultBlocking(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateAutoAdultWebsiteBlocking(enabled)
            _uiState.update {
                it.copy(
                    successMessage = if (enabled) "Automatic Adult Website Blocker Enabled" else "Automatic Adult Filter Disabled"
                )
            }
        }
    }

    fun toggleManualBlocking(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateManualWebsiteBlocking(enabled)
        }
    }

    fun updateAddWebsiteInput(input: String) {
        _uiState.update { it.copy(addWebsiteInput = input) }
    }

    fun addWebsite(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid website link or domain") }
            return
        }

        viewModelScope.launch {
            val normalizedDomain = BlockedWebsiteRepository.normalizeDomain(trimmed)
            if (normalizedDomain.isBlank() || !normalizedDomain.contains(".")) {
                _uiState.update { it.copy(errorMessage = "Invalid website domain. Example: facebook.com or https://example.com") }
                return@launch
            }

            val added = repository.addWebsite(normalizedDomain, category = "MANUAL")
            if (added) {
                _uiState.update {
                    it.copy(
                        addWebsiteInput = "",
                        successMessage = "Added \"$normalizedDomain\" to manual block list",
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to add domain") }
            }
        }
    }

    fun deleteWebsite(domain: String) {
        viewModelScope.launch {
            repository.removeWebsite(domain)
            _uiState.update { it.copy(successMessage = "Removed \"$domain\" from block list") }
        }
    }

    fun toggleWebsite(domain: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleWebsite(domain, isEnabled)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
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
