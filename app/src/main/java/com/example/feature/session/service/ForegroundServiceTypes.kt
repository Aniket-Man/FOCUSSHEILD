package com.example.feature.session.service

import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Chooses the `foregroundServiceType` value passed to
 * [android.app.Service.startForeground] and validates it against what the manifest declares.
 *
 * The contract Android enforces
 * -----------------------------
 * From Android 10 (API 29) `startForeground(id, notification, type)` requires every bit of `type` to
 * be **declared** on the `<service>` element (`foregroundServiceType`), otherwise the call throws
 * `IllegalArgumentException: foregroundServiceType 0x… is not a subset of foregroundServiceType
 * attribute 0x…`. From Android 14 (API 34) each used type additionally requires a matching
 * `FOREGROUND_SERVICE_<TYPE>` permission, and omitting the type parameter altogether throws.
 *
 * That is exactly the trap this app was in: the manifest declared only `specialUse` (an API 34+
 * constant), while the code passed `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 29–33 — a bit that was
 * never declared on those releases — so the foreground service could fail to start on every Android
 * 10–13 device. The manifest now declares `dataSync|specialUse`, and this resolver picks the right
 * one per API level and verifies it against the *actual* declared mask read from the platform
 * (`ServiceInfo.foregroundServiceType`), so a future manifest edit cannot reintroduce a silent
 * mismatch: an undeclared bit is dropped with a warning instead of being passed to the OS.
 *
 * Deliberately free of Android calls (only compile-time constants and the `sdkInt` passed in), so
 * every branch is unit-tested in `ForegroundServiceTypesTest`.
 */
object ForegroundServiceTypes {

    /** What to pass to `startForeground`. */
    sealed interface Choice {

        /** Pass [type] as the third argument. */
        data class Typed(val type: Int) : Choice

        /** Use the two-argument `startForeground(id, notification)` (pre-Android 10 only). */
        data object Untyped : Choice
    }

    /** The resolved choice plus any explanation worth logging. */
    data class Resolution(
        val choice: Choice,
        /** Non-null when something had to be worked around; logged at warning level by the caller. */
        val warning: String? = null
    )

    /**
     * @param sdkInt `Build.VERSION.SDK_INT`.
     * @param declaredTypes `ServiceInfo.foregroundServiceType` for this service — the mask the
     *   manifest actually declares. Pass 0 when it could not be read.
     */
    fun resolve(sdkInt: Int, declaredTypes: Int): Resolution {
        if (sdkInt < Build.VERSION_CODES.Q) {
            // Types do not exist before Android 10; the two-argument call is the only correct one.
            return Resolution(Choice.Untyped)
        }

        val preferred = if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: specialUse is the declared category for "study timer + blocker".
            TYPE_SPECIAL_USE
        } else {
            // Android 10–13: specialUse does not exist; dataSync is the declared type.
            TYPE_DATA_SYNC
        }

        // A manifest whose type mask could not be read is treated as "unknown": prefer the platform
        // behaviour (Android 14+ also accepts the type with no declaration? it does not — but an
        // unreadable mask is a manifest/build problem, and failing to start the service at all is
        // worse than trying the documented value).
        if (declaredTypes == 0) {
            return Resolution(
                Choice.Typed(preferred),
                warning = "ServiceInfo.foregroundServiceType could not be read; using the type " +
                    "documented for API $sdkInt without manifest validation."
            )
        }

        if (declaredTypes and preferred == preferred) {
            return Resolution(Choice.Typed(preferred))
        }

        val fallback = listOf(TYPE_SPECIAL_USE, TYPE_DATA_SYNC)
            .firstOrNull { declaredTypes and it == it }
        if (fallback != null) {
            return Resolution(
                Choice.Typed(fallback),
                warning = "Preferred FGS type 0x${preferred.toString(16)} is not declared in the " +
                    "manifest (declared=0x${declaredTypes.toString(16)}); using declared type " +
                    "0x${fallback.toString(16)} instead."
            )
        }

        // Nothing usable is declared. Ask for the two-argument form only where it still works; on
        // Android 14+ it throws, so the caller's failure path handles that (stopSelf + log) rather
        // than pretending the service is protected.
        return Resolution(
            Choice.Untyped,
            warning = "No usable foregroundServiceType is declared in the manifest " +
                "(declared=0x${declaredTypes.toString(16)})."
        )
    }

    /** `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` (API 29+). */
    const val TYPE_DATA_SYNC: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

    /**
     * `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`. The platform constant is API 34+, but its
     * value is a compile-time integer that older releases simply ignore, so it can be declared in
     * the manifest and compared here on every API level.
     */
    const val TYPE_SPECIAL_USE: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
}
