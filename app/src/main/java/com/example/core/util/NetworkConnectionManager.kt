package com.example.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors real-time device internet connectivity using [ConnectivityManager] and exposes
 * the status as a hot [StateFlow] of [Boolean].
 */
@Singleton
class NetworkConnectionManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())

    /**
     * Real-time internet connectivity state (`true` when an active network with internet
     * capability is available, `false` otherwise).
     */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.value = true
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
            _isConnected.value = hasInternet
        }

        override fun onLost(network: Network) {
            _isConnected.value = checkCurrentConnectivity()
        }

        override fun onUnavailable() {
            _isConnected.value = false
        }
    }

    init {
        registerCallback()
    }

    private fun registerCallback() {
        val manager = connectivityManager ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(networkCallback)
        }.onFailure {
            runCatching {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                manager.registerNetworkCallback(request, networkCallback)
            }
        }
    }

    /**
     * Synchronously queries [ConnectivityManager] for current active internet connectivity.
     */
    fun checkCurrentConnectivity(): Boolean {
        val manager = connectivityManager ?: return false
        return runCatching {
            val activeNetwork = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }
}
