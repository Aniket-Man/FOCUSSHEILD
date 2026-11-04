package com.example.cloud.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks current internet connectivity so the sync engine can debounce retries and only attempt
 * work while a real (metered-or-not) internet-capable network is present.
 */
open class ConnectivityMonitor(context: Context) {

    private val connectivityManager by lazy {
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }

    private val _isOnline by lazy { MutableStateFlow(isCurrentlyOnline()) }
    open val isOnline: StateFlow<Boolean> by lazy { _isOnline.asStateFlow() }

    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Register a default-network listener. Safe to call more than once. */
    fun start() {
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                _isOnline.value = isCurrentlyOnline()
            }
        }
        callback = cb
        try {
            connectivityManager.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {
            // Some OEMs/edge cases reject registration; monitor just stays at the initial value.
        }
    }

    fun stop() {
        callback?.let { try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        callback = null
    }

    private fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
