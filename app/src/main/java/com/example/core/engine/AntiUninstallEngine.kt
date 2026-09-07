package com.example.core.engine

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.core.receiver.FocusDeviceAdminReceiver

/**
 * Engine for managing Anti-Uninstall and Device Admin protection.
 */
class AntiUninstallEngine(private val context: Context) {

    private val dpm: DevicePolicyManager? by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, FocusDeviceAdminReceiver::class.java)
    }

    /**
     * Checks if FocusShield is currently an active Device Administrator.
     */
    fun isDeviceAdminActive(): Boolean {
        return try {
            dpm?.isAdminActive(adminComponent) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an intent to launch the system prompt requesting Device Admin activation.
     */
    fun createEnableAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable Device Admin to activate Anti-Uninstall protection for FocusShield. This prevents FocusShield from being uninstalled or tampered with during focus sessions."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AntiUninstallEngine? = null

        fun getInstance(context: Context): AntiUninstallEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AntiUninstallEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
