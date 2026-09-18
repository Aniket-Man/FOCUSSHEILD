package com.example.feature.session.service

import android.content.pm.ServiceInfo
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue 3 regression suite: the `foregroundServiceType` handed to `startForeground` must always be a
 * subset of what the manifest declares, on every API level.
 *
 * The historical bug: the manifest declared only `specialUse` (an API 34 constant) while the code
 * passed `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 29–33, so `startForeground` threw
 * `IllegalArgumentException` on every Android 10–13 device. The cases below pin the mapping and the
 * "declared mask wins" rule that prevents the same class of mismatch from returning.
 *
 * Pure logic, no Robolectric: `Build.VERSION_CODES` and `ServiceInfo` types are compile-time values.
 */
class ForegroundServiceTypesTest {

    private val dataSync = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    private val specialUse = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    private val both = dataSync or specialUse

    @Test
    fun `pre Android 10 uses the two argument startForeground`() {
        for (sdk in intArrayOf(24, 25, 26, 27, 28)) {
            val resolution = ForegroundServiceTypes.resolve(sdk, declaredTypes = 0)
            assertEquals(ForegroundServiceTypes.Choice.Untyped, resolution.choice)
            assertNull(resolution.warning)
        }
    }

    @Test
    fun `Android 10 to 13 uses dataSync, which those releases actually have`() {
        for (sdk in intArrayOf(29, 30, 31, 32, 33)) {
            val resolution = ForegroundServiceTypes.resolve(sdk, declaredTypes = both)
            assertEquals(
                ForegroundServiceTypes.Choice.Typed(dataSync),
                resolution.choice
            )
            assertNull(resolution.warning)
        }
    }

    @Test
    fun `Android 14 and up uses specialUse`() {
        for (sdk in intArrayOf(34, 35, 36)) {
            val resolution = ForegroundServiceTypes.resolve(sdk, declaredTypes = both)
            assertEquals(ForegroundServiceTypes.Choice.Typed(specialUse), resolution.choice)
            assertNull(resolution.warning)
        }
    }

    @Test
    fun `an undeclared preferred type falls back to a declared one and warns`() {
        // The exact pre-fix manifest: only specialUse declared, running on Android 10-13.
        val resolution = ForegroundServiceTypes.resolve(33, declaredTypes = specialUse)
        assertEquals(ForegroundServiceTypes.Choice.Typed(specialUse), resolution.choice)
        assertNotNull("a type substitution must be reported", resolution.warning)

        // Only dataSync declared, running on Android 14+: dataSync is what gets used, with a warning.
        val onAndroid14 = ForegroundServiceTypes.resolve(34, declaredTypes = dataSync)
        assertEquals(ForegroundServiceTypes.Choice.Typed(dataSync), onAndroid14.choice)
        assertNotNull(onAndroid14.warning)
    }

    @Test
    fun `nothing declared means the caller must handle the failure`() {
        // The service still tries the documented type (a manifest that cannot be read is a build
        // problem) and logs the assumption.
        val resolution = ForegroundServiceTypes.resolve(34, declaredTypes = 0)
        assertEquals(ForegroundServiceTypes.Choice.Typed(specialUse), resolution.choice)
        assertTrue(resolution.warning!!.contains("could not be read"))
    }

    @Test
    fun `the chosen type is always a subset of the declared mask`() {
        val masks = listOf(dataSync, specialUse, both)
        for (sdk in 29..36) {
            for (mask in masks) {
                val choice = ForegroundServiceTypes.resolve(sdk, mask).choice
                if (choice is ForegroundServiceTypes.Choice.Typed) {
                    assertEquals(
                        "sdk=$sdk mask=$mask chose ${choice.type}",
                        choice.type,
                        choice.type and mask
                    )
                }
            }
        }
    }

    @Test
    fun `constants mirror the platform values`() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, ForegroundServiceTypes.TYPE_DATA_SYNC)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, ForegroundServiceTypes.TYPE_SPECIAL_USE)
        // sanity: the two types are distinct bits, so an ORed mask is meaningful
        assertTrue(ForegroundServiceTypes.TYPE_DATA_SYNC != ForegroundServiceTypes.TYPE_SPECIAL_USE)
        assertTrue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE == 34)
    }
}
