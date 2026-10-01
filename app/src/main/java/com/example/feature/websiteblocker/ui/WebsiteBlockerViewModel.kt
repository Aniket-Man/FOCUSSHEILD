package com.example.feature.websiteblocker.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.core.permission.FocusPermissionManager
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.BlockedWebsiteRepository
import com.example.feature.websiteblocker.vpn.AdultDnsVpnService
import com.example.feature.websiteblocker.vpn.AdultFilteringEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebsiteBlockerUiState(
    val isAutoAdultBlockingEnabled: Boolean = false,
    val isManualBlockingEnabled: Boolean = true,
    val manualBlockedWebsites: List<BlockedWebsiteEntity> = emptyList(),
    val isAccessibilityEnabled: Boolean = false,
    val isVpnPermissionGranted: Boolean = false,
    val isAwaitingVpnPermission: Boolean = false,
    val vpnPrepareIntent: Intent? = null,
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
        // Observe combined UI state
        viewModelScope.launch {
            combine(
                preferencesRepository.preferencesFlow,
                repository.allBlockedWebsites
            ) { prefs, websites ->
                val context = getApplication<Application>()
                val vpnGranted = FocusPermissionManager.isVpnPermissionGranted(context)
                WebsiteBlockerUiState(
                    isAutoAdultBlockingEnabled = prefs.isAutoAdultWebsiteBlockingEnabled,
                    isManualBlockingEnabled = prefs.isManualWebsiteBlockingEnabled,
                    manualBlockedWebsites = websites,
                    isAccessibilityEnabled = checkAccessibilityServiceEnabled(context),
                    isVpnPermissionGranted = vpnGranted,
                    isAwaitingVpnPermission = _uiState.value.isAwaitingVpnPermission,
                    vpnPrepareIntent = _uiState.value.vpnPrepareIntent,
                    addWebsiteInput = _uiState.value.addWebsiteInput,
                    errorMessage = _uiState.value.errorMessage,
                    successMessage = _uiState.value.successMessage
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }

        // Observe 18+ blocking toggle state changes to trigger VpnService tunnel start/stop
        viewModelScope.launch {
            preferencesRepository.preferencesFlow
                .map { it.isAutoAdultWebsiteBlockingEnabled }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    handleVpnTunnel(enabled)
                }
        }
    }

    /**
     * Controls the local VpnService tunnel based on the 18+ blocking toggle value.
     */
    private fun handleVpnTunnel(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            val isPrepared = FocusPermissionManager.isVpnPermissionGranted(context)
            if (isPrepared) {
                AdultDnsVpnService.start(context)
                AdultFilteringEngine.instance?.onVpnServiceStarted()
            }
        } else {
            AdultDnsVpnService.stop(context)
            AdultFilteringEngine.instance?.onVpnServiceStopped()
        }
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val accessibility = checkAccessibilityServiceEnabled(context)
        val vpnGranted = FocusPermissionManager.isVpnPermissionGranted(context)
        _uiState.update {
            it.copy(
                isAccessibilityEnabled = accessibility,
                isVpnPermissionGranted = vpnGranted
            )
        }
        if (_uiState.value.isAutoAdultBlockingEnabled && vpnGranted) {
            handleVpnTunnel(true)
        }
    }

    fun toggleAutoAdultBlocking(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            val isPrepared = FocusPermissionManager.isVpnPermissionGranted(context)
            if (!isPrepared) {
                // Must obtain VPN permission first!
                // Redirect user to system VPN confirmation dialog / settings.
                val prepareIntent = try {
                    VpnService.prepare(context)
                } catch (_: Exception) {
                    null
                }
                _uiState.update {
                    it.copy(
                        isAwaitingVpnPermission = true,
                        vpnPrepareIntent = prepareIntent
                    )
                }
            } else {
                // Already prepared/granted: proceed to enable immediately!
                viewModelScope.launch {
                    preferencesRepository.updateAutoAdultWebsiteBlocking(true)
                    AdultDnsVpnService.start(context)
                    AdultFilteringEngine.instance?.onVpnServiceStarted()
                    _uiState.update {
                        it.copy(
                            isAwaitingVpnPermission = false,
                            vpnPrepareIntent = null,
                            isVpnPermissionGranted = true,
                            successMessage = "18+ Adult Website Shield Enabled"
                        )
                    }
                }
            }
        } else {
            // Toggling OFF
            viewModelScope.launch {
                preferencesRepository.updateAutoAdultWebsiteBlocking(false)
                AdultDnsVpnService.stop(context)
                AdultFilteringEngine.instance?.onVpnServiceStopped()
                _uiState.update {
                    it.copy(
                        isAwaitingVpnPermission = false,
                        vpnPrepareIntent = null,
                        successMessage = "18+ Adult Website Shield Disabled"
                    )
                }
            }
        }
    }

    fun onVpnPermissionGranted() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            preferencesRepository.updateAutoAdultWebsiteBlocking(true)
            AdultDnsVpnService.start(context)
            AdultFilteringEngine.instance?.onVpnServiceStarted()
            _uiState.update {
                it.copy(
                    isAwaitingVpnPermission = false,
                    vpnPrepareIntent = null,
                    isVpnPermissionGranted = true,
                    errorMessage = null,
                    successMessage = "VPN Permission Granted: 18+ Shield Active"
                )
            }
        }
    }

    fun onVpnPermissionDenied() {
        viewModelScope.launch {
            preferencesRepository.updateAutoAdultWebsiteBlocking(false)
            _uiState.update {
                it.copy(
                    isAwaitingVpnPermission = false,
                    vpnPrepareIntent = null,
                    isVpnPermissionGranted = false,
                    errorMessage = "VPN permission is required to enable 18+ Adult Website Shield."
                )
            }
        }
    }

    fun consumeVpnPrepareIntent() {
        _uiState.update { it.copy(vpnPrepareIntent = null) }
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
