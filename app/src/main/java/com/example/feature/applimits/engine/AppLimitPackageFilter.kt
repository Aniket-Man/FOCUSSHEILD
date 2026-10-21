package com.example.feature.applimits.engine

/**
 * The package-name rules the App Limit pipeline shares between its two observers:
 *
 *  - the accessibility service, which decides whether to **evaluate the blocker gate**
 *    for a package (would this package ever have a limit row worth checking?), and
 *  - [AppLimitManager.onForegroundPackageChanged], which decides whether to **track**
 *    a package as the foreground app (should its pause/resume movement count?).
 *
 * Two predicates, one object, on purpose. The two lists used to be hand-maintained in
 * two files and had drifted (the gate and the tracker disagreed on systemui,
 * permissioncontroller and launchers) — everything is derived here now so they cannot
 * drift again. They are also deliberately *not* the same predicate:
 *
 *  - [shouldTrackForeground] is the movement signal and **keeps launchers trackable**:
 *    stepping out to the home screen is the single most common way to leave a limited
 *    app, and excluding launchers here would keep the session timer running while the
 *    user is not in the app at all.
 *  - [shouldEvaluateForLimits] is the gate's union (tracking exclusions plus launchers,
 *    telecom and the package installers). Evaluating a launcher would be harmless — a
 *    system shell can never have a limit row — but it would pointlessly spin the
 *    decision loop on every shell switch, so the gate keeps them out.
 */
object AppLimitPackageFilter {

    /**
     * True when a foreground change to [pkg] should move the session clock (pause on
     * leave, resume on return). False for FocusShield itself, the Android/system shells,
     * IMEs and keyboards, SystemUI and the permission controller — and for blank input.
     */
    fun shouldTrackForeground(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (isOwnApp(pkg)) return false
        val lower = pkg.lowercase()
        return lower != "android" &&
                !lower.contains("inputmethod") &&
                !lower.contains("keyboard") &&
                !lower.contains("honeyboard") &&
                !lower.contains("swiftkey") &&
                !lower.contains("systemui") &&
                !lower.contains("permissioncontroller")
    }

    /**
     * True when the app-limit gate should evaluate [pkg]. Never true where
     * [shouldTrackForeground] is false, plus launchers, telecom and the package
     * installers. (`contains("launcher")` also covers the historical `nexuslauncher`
     * and `launcher3` entries.)
     */
    fun shouldEvaluateForLimits(pkg: String): Boolean {
        if (!shouldTrackForeground(pkg)) return false
        val lower = pkg.lowercase()
        return !lower.contains("launcher") &&
                lower != "com.android.server.telecom" &&
                lower != "com.google.android.packageinstaller" &&
                lower != "com.android.packageinstaller"
    }

    /**
     * FocusShield itself. The applicationId (`com.aistudio.focusshield...`) is covered by
     * the same prefixes as the namespace, so both observers agree on "our own screen" even
     * though neither can see the other's context.
     *
     * Public because the foreground observer needs to distinguish "our own screen" (pause the
     * session clock, dismiss the blocker) from the transient shells that must be ignored.
     */
    fun isOwnApp(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()
        return lower.startsWith("com.example") || lower.startsWith("com.aistudio.focusshield")
    }
}
