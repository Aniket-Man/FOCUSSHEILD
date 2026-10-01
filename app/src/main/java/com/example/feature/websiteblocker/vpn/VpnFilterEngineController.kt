package com.example.feature.websiteblocker.vpn

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.example.data.preferences.FocusPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * How the controller drives the [AdultContentVpnService]. An interface so the reactive logic is
 * unit-testable with a recording fake instead of real Android service starts.
 */
interface VpnServiceCommander {

    /** @return false when the platform refused the (background) service start. */
    fun startVpnService(context: Context): Boolean

    fun stopVpnService(context: Context)

    /** The system's VPN-consent launch intent, or null when consent is already granted. */
    fun prepareIntent(context: Context): Intent?

    /** Shows the system VPN-consent dialog (no app UI of its own). */
    fun launchConsent(context: Context)
}

/** Production [VpnServiceCommander]: real service starts, stops and consent intents. */
class AndroidVpnServiceCommander : VpnServiceCommander {

    override fun startVpnService(context: Context): Boolean {
        val intent = Intent(context, AdultContentVpnService::class.java)
            .setAction(AdultContentVpnService.ACTION_START)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: IllegalStateException) {
            // Android 8+ background-execution limits / Android 12+ background FGS restrictions.
            // The engine retries on the next foreground evaluation (activity start / toggle flip).
            Log.e(TAG, "Platform refused the VPN service start (background restriction): ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start the adult-content VPN service", e)
            false
        }
    }

    override fun stopVpnService(context: Context) {
        try {
            context.stopService(Intent(context, AdultContentVpnService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop the adult-content VPN service: ${e.message}")
        }
    }

    override fun prepareIntent(context: Context): Intent? = try {
        VpnService.prepare(context)
    } catch (e: Exception) {
        Log.w(TAG, "VpnService.prepare() failed: ${e.message}")
        null
    }

    override fun launchConsent(context: Context) {
        val intent = prepareIntent(context) ?: return
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch the VPN consent dialog: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "AdultVpnCommander"
    }
}

/** Checks whether the user has granted VPN consent for this app. */
fun interface VpnPermissionChecker {
    fun isGranted(context: Context): Boolean
}

/**
 * Reactive glue between the **existing** 18+ blocking state and the DNS-level filtering engine.
 *
 * It owns **no** state of its own that duplicates the toggle: the single source of truth is
 * `FocusPreferences.isAutoAdultWebsiteBlockingEnabled` (DataStore key `auto_adult_website_blocking`,
 * written by `WebsiteBlockerViewModel.toggleAutoAdultBlocking` from the existing switch). This
 * controller only *observes* that flow and reconciles the VPN with it:
 *
 * ```
 * Existing 18+ toggle → DataStore preference → [VpnEnginePolicy] → Start/Stop/Prompt → VpnService
 * ```
 *
 * Because it reacts to the preference flow (not to UI events), the engine follows the toggle no
 * matter who flips it — the settings screen, strict mode, or a cloud-sync pull — and it restores
 * the engine on process start ("app restart with blocking enabled") for free.
 *
 * VPN permission handling is fully UI-less: when the toggle is ON and consent is missing, the
 * *system* consent dialog (`VpnService.prepare`) is launched while the app is visible — once per
 * process on foreground re-evaluations, and on every explicit OFF→ON toggle transition.
 */
class VpnFilterEngineController internal constructor(
    private val appContext: Context,
    private val desiredStateFlow: Flow<Boolean>,
    private val commander: VpnServiceCommander,
    private val permissionChecker: VpnPermissionChecker,
    private val scope: CoroutineScope
) {

    /** Last value of the existing 18+ preference; `true` (the DataStore default) until the first emission. */
    @Volatile
    private var desiredEnabled: Boolean = true

    @Volatile
    private var engineRunning: Boolean = false

    @Volatile
    private var appInForeground: Boolean = false

    private var consentPromptedThisProcess: Boolean = false
    private val lock = Any()

    /** Read-only view for the service (sticky restart / recovery decisions). */
    val desiredEnabledSnapshot: Boolean
        get() = desiredEnabled

    /** Starts observing the existing preference. Call once per process. */
    fun start() {
        scope.launch {
            desiredStateFlow.collect { enabled ->
                val previous = synchronized(lock) { desiredEnabled }
                desiredEnabled = enabled
                val explicitEnableFlip = enabled && !previous
                // An explicit OFF→ON flip is a fresh user intent: prompting is handled below
                // (forced, once per flip), so this evaluation must not also consume the
                // once-per-process prompt — otherwise the dialog is launched twice.
                evaluate("preference", allowPrompt = !explicitEnableFlip)
                if (explicitEnableFlip && !isPermissionGranted()) {
                    promptConsent(force = true)
                }
            }
        }
    }

    /** Called by the activity-lifecycle tracker whenever the app becomes visible. */
    fun onAppForeground() {
        appInForeground = true
        evaluate("app-foreground")
    }

    /** Called by the activity-lifecycle tracker when the app is no longer visible. */
    fun onAppBackground() {
        appInForeground = false
    }

    /** Engine state feedback from the service (started, stopped, revoked, failed). */
    fun onEngineRunningChanged(running: Boolean) {
        engineRunning = running
        if (!running) {
            // e.g. the system revoked the VPN — reconcile again (may re-prompt when visible).
            evaluate("engine-stopped")
        }
    }

    internal fun evaluate(reason: String, allowPrompt: Boolean = true) {
        try {
            synchronized(lock) {
                val command = VpnEnginePolicy.resolve(
                    desiredEnabled = desiredEnabled,
                    vpnPermissionGranted = isPermissionGranted(),
                    engineRunning = engineRunning,
                    appInForeground = appInForeground
                )
                when (command) {
                    VpnEnginePolicy.Command.StartVpn -> commander.startVpnService(appContext)
                    VpnEnginePolicy.Command.StopVpn -> commander.stopVpnService(appContext)
                    // When the caller prompts explicitly (an OFF→ON flip), skip the prompt here.
                    VpnEnginePolicy.Command.RequestVpnPermission ->
                        if (allowPrompt) promptConsent(force = false)
                    VpnEnginePolicy.Command.Noop -> Unit
                }
                Log.d(TAG, "evaluate($reason) desired=$desiredEnabled running=$engineRunning -> $command")
            }
        } catch (e: Exception) {
            // The controller runs inside every Robolectric test's application startup too; it must
            // never be able to break app start.
            Log.e(TAG, "Engine evaluation failed", e)
        }
    }

    private fun promptConsent(force: Boolean) {
        if (!force && consentPromptedThisProcess) return
        if (!appInForeground) return // never launch the system dialog from the background
        consentPromptedThisProcess = true
        commander.launchConsent(appContext)
    }

    private fun isPermissionGranted(): Boolean = try {
        permissionChecker.isGranted(appContext)
    } catch (e: Exception) {
        false
    }

    companion object {
        private const val TAG = "VpnFilterEngineCtrl"

        @Volatile
        private var instanceRef: VpnFilterEngineController? = null

        /** The running controller, if [initialize] succeeded for this process. */
        val instance: VpnFilterEngineController?
            get() = instanceRef

        /**
         * Wires the engine to the **existing** auto-adult-website-blocking preference and starts
         * observing it. Safe to call from `Application.onCreate` on every API level: every platform
         * interaction is exception-guarded so the app can never fail to start because of it.
         */
        fun initialize(
            app: Application,
            scope: CoroutineScope,
            preferencesRepository: FocusPreferencesRepository
        ): VpnFilterEngineController? {
            return try {
                instanceRef?.let { return it }
                val commander = AndroidVpnServiceCommander()
                val controller = VpnFilterEngineController(
                    appContext = app.applicationContext,
                    desiredStateFlow = preferencesRepository.preferencesFlow
                        .map { it.isAutoAdultWebsiteBlockingEnabled }
                        .distinctUntilChanged(),
                    commander = commander,
                    permissionChecker = VpnPermissionChecker { context ->
                        commander.prepareIntent(context) == null
                    },
                    scope = scope
                )
                instanceRef = controller
                app.registerActivityLifecycleCallbacks(ForegroundTracker(controller))
                controller.start()
                Log.i(TAG, "18+ DNS filtering engine controller initialized (observing the existing toggle)")
                controller
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize the 18+ DNS filtering engine controller", e)
                null
            }
        }
    }

    /**
     * Tracks process visibility via activity lifecycle callbacks, so consent prompts are only
     * launched while the app is on screen (a background activity launch would be blocked by the
     * OS anyway) and the engine can start the moment the user returns from the system dialog.
     */
    private class ForegroundTracker(
        private val controller: VpnFilterEngineController
    ) : Application.ActivityLifecycleCallbacks {

        private val startedActivities = AtomicInteger(0)

        override fun onActivityStarted(activity: Activity) {
            if (startedActivities.incrementAndGet() == 1) {
                controller.onAppForeground()
            }
        }

        override fun onActivityStopped(activity: Activity) {
            if (startedActivities.decrementAndGet() == 0) {
                controller.onAppBackground()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
