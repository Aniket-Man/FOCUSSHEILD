package com.example.core.permission

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a function that shows the system VPN consent dialog and reports whether the app ended up
 * prepared.
 *
 * `VpnService.prepare()` hands back an intent that Android expects to be started with
 * `startActivityForResult()`: the confirmation dialog resolves the requesting app through
 * `getCallingPackage()`, and that is only populated for result-launched activities. Firing the
 * intent with a plain `startActivity()` — which is what the onboarding flow used to do — makes the
 * platform close the dialog without recording consent, so the permission was never granted and
 * the row stayed unchecked forever.
 *
 * The result callback re-reads the authoritative platform state because some OEM builds report
 * `RESULT_CANCELED` even after the user allowed the connection.
 */
@Composable
fun rememberVpnConsentRequester(
    onResult: (granted: Boolean) -> Unit = {}
): () -> Boolean {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK ||
            FocusPermissionManager.isVpnPermissionGranted(context)
        currentOnResult(granted)
    }

    return remember(context, launcher) {
        {
            val consentIntent = FocusPermissionManager.vpnConsentIntent(context)
            if (consentIntent != null) {
                try {
                    launcher.launch(consentIntent)
                    true
                } catch (_: Exception) {
                    FocusPermissionManager.openVpnSettings(context)
                    false
                }
            } else {
                // Null means either "already prepared" or "no consent intent available on this
                // build". Report an already-granted app immediately; otherwise send the user to
                // Settings and let the caller's await-if-granted poll take over.
                if (FocusPermissionManager.isVpnPermissionGranted(context)) {
                    currentOnResult(true)
                } else {
                    FocusPermissionManager.openVpnSettings(context)
                }
                false
            }
        }
    }
}
