package com.example.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isBrowser: Boolean = false,
    val isSystemApp: Boolean = false,
    val hasAppLimit: Boolean = false
)

object InstalledAppsProvider {

    fun Drawable.toImageBitmap(): ImageBitmap {
        if (this is BitmapDrawable && this.bitmap != null && !this.bitmap.isRecycled) {
            return this.bitmap.asImageBitmap()
        }
        val w = if (intrinsicWidth > 0) intrinsicWidth else 96
        val h = if (intrinsicHeight > 0) intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap.asImageBitmap()
    }

    /**
     * Dynamically queries all browser applications installed on the user's phone.
     */
    fun getInstalledBrowsers(context: Context): List<InstalledAppItem> {
        val pm = context.packageManager
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(browserIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val browserApps = mutableListOf<InstalledAppItem>()
        val seenPackages = mutableSetOf<String>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && seenPackages.add(pkg)) {
                val label = info.loadLabel(pm).toString()
                val icon = try { info.loadIcon(pm) } catch (e: Exception) { null }
                browserApps.add(
                    InstalledAppItem(
                        packageName = pkg,
                        appName = label,
                        icon = icon,
                        isBrowser = true
                    )
                )
            }
        }
        return browserApps.sortedBy { it.appName.lowercase() }
    }

    /**
     * Dynamically queries user installed apps (or launcher apps) on the device.
     */
    fun getInstalledUserApps(
        context: Context,
        limitAppPackages: Set<String> = emptySet()
    ): List<InstalledAppItem> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val knownBrowserPackages = getInstalledBrowsers(context).map { it.packageName }.toSet()
        val userApps = mutableListOf<InstalledAppItem>()
        val seenPackages = mutableSetOf<String>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && !knownBrowserPackages.contains(pkg) && pkg != "com.google.android.youtube" && seenPackages.add(pkg)) {
                val label = info.loadLabel(pm).toString()
                val icon = try { info.loadIcon(pm) } catch (e: Exception) { null }
                val isSystem = try {
                    (info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } catch (e: Exception) { false }
                val hasLimit = limitAppPackages.contains(pkg)

                userApps.add(
                    InstalledAppItem(
                        packageName = pkg,
                        appName = label,
                        icon = icon,
                        isBrowser = false,
                        isSystemApp = isSystem,
                        hasAppLimit = hasLimit
                    )
                )
            }
        }

        return userApps.sortedWith(compareByDescending<InstalledAppItem> { it.hasAppLimit }.thenBy { it.appName.lowercase() })
    }
}
