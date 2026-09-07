package com.example.core.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Administrator Receiver for FocusShield.
 * When active, prevents the app from being uninstalled via standard Android launcher/settings
 * without first revoking device admin privilege.
 */
class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "FocusShield Uninstall Protection Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Warning: Disabling Device Admin will remove Uninstall Protection from FocusShield."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "FocusShield Uninstall Protection Disabled", Toast.LENGTH_SHORT).show()
    }
}
