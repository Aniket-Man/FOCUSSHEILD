package com.example.core.util

import android.os.Build
import androidx.annotation.RawRes
import com.example.R
import java.util.Locale

/**
 * Device-specific guidance for granting the accessibility permission. Detects the
 * device manufacturer and returns the matching Lottie walkthrough animation plus
 * the exact Settings path on that OEM's skin.
 */
data class DevicePermissionGuide(
    val manufacturerLabel: String,
    @RawRes val animationRes: Int,
    val imageAssetsFolder: String? = null,
    val accessibilitySteps: List<String>
)

object DeviceGuideFactory {

    /**
     * Detects the current device OEM and returns its tailored permission guide.
     * Falls back to the generic guide (and generic animation) for unknown OEMs.
     */
    fun detect(): DevicePermissionGuide {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault()).trim()
        val brand = Build.BRAND.lowercase(Locale.getDefault()).trim()

        return when {
            manufacturer.contains("samsung") || brand.contains("samsung") -> samsungGuide()
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                brand.contains("xiaomi") || brand.contains("redmi") ||
                manufacturer.contains("poco") || brand.contains("poco") -> xiaomiGuide()

            manufacturer.contains("oppo") || brand.contains("oppo") ||
                manufacturer.contains("oneplus") || brand.contains("oneplus") ||
                manufacturer.contains("realme") || brand.contains("realme") -> oppoGuide()

            manufacturer.contains("vivo") || brand.contains("vivo") ||
                manufacturer.contains("iqoo") || brand.contains("iqoo") -> vivoGuide()

            manufacturer.contains("huawei") || brand.contains("huawei") ||
                manufacturer.contains("honor") || brand.contains("honor") -> huaweiGuide()

            else -> defaultGuide()
        }
    }

    private fun samsungGuide() = DevicePermissionGuide(
        manufacturerLabel = "Samsung (One UI)",
        animationRes = R.raw.accessibility_permission_samsung,
        imageAssetsFolder = "oem_samsung",
        accessibilitySteps = listOf(
            "Open Settings",
            "Tap Accessibility",
            "Tap Installed apps",
            "Find and tap FocusShield",
            "Tap On, then Allow"
        )
    )

    private fun xiaomiGuide() = DevicePermissionGuide(
        manufacturerLabel = "Xiaomi / Redmi / Poco (MIUI · HyperOS)",
        animationRes = R.raw.accessibility_permission_redmi,
        imageAssetsFolder = "oem_redmi",
        accessibilitySteps = listOf(
            "Open Settings",
            "Tap Additional settings",
            "Tap Accessibility",
            "Tap Downloaded apps",
            "Find and tap FocusShield",
            "Tap On, then Allow"
        )
    )

    private fun oppoGuide() = DevicePermissionGuide(
        manufacturerLabel = "OPPO / OnePlus / Realme (ColorOS)",
        animationRes = R.raw.accessibility_permission,
        accessibilitySteps = listOf(
            "Open Settings",
            "Tap Additional Settings (or System Settings)",
            "Tap Accessibility",
            "Tap Downloaded apps",
            "Find and tap FocusShield",
            "Tap On, then Allow"
        )
    )

    private fun vivoGuide() = DevicePermissionGuide(
        manufacturerLabel = "Vivo / iQOO (Funtouch · OriginOS)",
        animationRes = R.raw.accessibility_permission,
        accessibilitySteps = listOf(
            "Open Settings",
            "Tap Shortcuts & Accessibility",
            "Tap Accessibility",
            "Tap Downloaded services",
            "Find and tap FocusShield",
            "Tap On, then Allow"
        )
    )

    private fun huaweiGuide() = DevicePermissionGuide(
        manufacturerLabel = "Huawei / Honor (EMUI · MagicOS)",
        animationRes = R.raw.accessibility_permission,
        accessibilitySteps = listOf(
            "Open Settings",
            "Tap Accessibility",
            "Tap Accessibility again (Services)",
            "Tap Downloaded apps",
            "Find and tap FocusShield",
            "Tap On, then Allow"
        )
    )

    private fun defaultGuide() = DevicePermissionGuide(
        manufacturerLabel = "Android",
        animationRes = R.raw.accessibility_permission,
        accessibilitySteps = listOf(
            "Open Settings",
            "Tap Accessibility",
            "Find FocusShield under Downloaded apps/services",
            "Tap FocusShield",
            "Tap On, then Allow"
        )
    )
}
