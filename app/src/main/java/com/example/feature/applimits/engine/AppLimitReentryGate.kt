package com.example.feature.applimits.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers that the user answered an app-limit blocker by **leaving the app**, so the engine does
 * not immediately prompt them again (issue #53).
 *
 * The engine models "the user picked a duration" — that is what `AppLimitManager._activeSession`
 * is — but it had no state at all for "the user pressed *Close <app>*". Because
 * `finalizeExpiredSession` nulls the active session and clears the launch debounce *before* the
 * "time's up" blocker is even raised, and because `AppLimitOverlayActivity.isVisibleFor()` (the
 * only remaining suppressor) is released in `onPause` while the limited app is still on screen
 * behind the translucent blocker, the very next accessibility event from that app re-evaluated the
 * limit from scratch and legitimately concluded `REQUIRE_USAGE_SELECTION` — a second
 * "how long do you want to use?" popup thrown over the home screen the user had just reached.
 *
 * This gate closes that window **without a timer**. It is armed when the user dismisses a blocker
 * by going home, and it is disarmed only by a *genuine re-entry*:
 *
 * ```
 * onBlockerDismissed(pkg)          armed, hasLeft = false
 *      ↓  events that still report pkg (the app is being torn down behind the blocker)
 *                                  still armed — no prompt
 *      ↓  onForegroundPackage(launcher / any other app)
 *                                  armed, hasLeft = true
 *      ↓  onForegroundPackage(pkg)
 *                                  disarmed — the user really came back, enforcement resumes
 * ```
 *
 * The two-phase `hasLeft` flag is the whole point: the foreground events that arrive *during* the
 * dismissal still name the limited package, and treating those as a re-entry would re-open exactly
 * the hole this closes. Enforcement can never be suppressed for longer than "until the app is
 * opened again" — there is no elapsed-time component anywhere in this class, so a limit can never
 * be silently disabled.
 *
 * A previous change added a 1.5 s "teardown window" that disarmed the gate on elapsed time alone.
 * That reintroduced the original bug on slow devices: the home transition can take longer than
 * 1.5 s, and the first teardown event after the window expired was treated as a fresh re-entry and
 * raised the blocker over the home screen. The gate is deliberately timer-free again; leaving is
 * proven by observing another foreground package, never by a clock.
 *
 * Thread-safe: read from the accessibility service's coroutines and written from the main thread.
 */
class AppLimitReentryGate {

    /** Package → has the user been observed somewhere else since the blocker was dismissed. */
    private val armed = ConcurrentHashMap<String, Boolean>()

    /** Arms the gate for [packageName]: the user resolved its blocker by leaving the app. */
    fun onBlockerDismissed(packageName: String) {
        if (packageName.isBlank()) return
        armed[packageName] = false
    }

    /**
     * Feeds the observed foreground package in. Any armed package that is *not* [packageName] has
     * now demonstrably been left; an armed package that the user has left and is now back in is
     * disarmed, so the next decision is taken normally.
     *
     * This must only be called for actual foreground-package changes. In particular it must not
     * be called from [isArmedFor], which is a read used by every gate evaluation: a read must
     * never mutate suppression state.
     */
    fun onForegroundPackage(packageName: String) {
        if (packageName.isBlank()) return
        for (entry in armed.entries) {
            val pkg = entry.key
            if (pkg != packageName) {
                // Somewhere else: the exit is now confirmed for this package.
                if (entry.value != true) armed[pkg] = true
            } else if (entry.value == true) {
                // Back in the app after having genuinely left it.
                armed.remove(pkg)
            }
        }
    }

    /** True while [packageName] must not raise a new blocker. */
    fun isArmedFor(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return armed.containsKey(packageName)
    }

    /**
     * Drops the gate for [packageName] — used when something else already resolved the block
     * (a temporary session was started, the limit was bypassed for today, or it was deleted).
     */
    fun clear(packageName: String) {
        armed.remove(packageName)
    }
}
