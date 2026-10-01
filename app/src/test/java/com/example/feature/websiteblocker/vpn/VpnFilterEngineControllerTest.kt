package com.example.feature.websiteblocker.vpn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reactive-contract tests between the **existing** 18+ blocking preference and the engine:
 * toggle ON → engine starts, toggle OFF → engine stops, process restart with the toggle ON
 * restores the engine, VPN permission is prompted UI-lessly and never from the background.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VpnFilterEngineControllerTest {

    private class RecordingCommander(var startResult: Boolean = true) : VpnServiceCommander {
        val starts = mutableListOf<String>()
        val stops = mutableListOf<String>()
        val consentPrompts = mutableListOf<String>()

        override fun startVpnService(context: Context): Boolean {
            starts += context.packageName
            return startResult
        }

        override fun stopVpnService(context: Context) {
            stops += context.packageName
        }

        override fun prepareIntent(context: Context): Intent? = null

        override fun launchConsent(context: Context) {
            consentPrompts += context.packageName
        }
    }

    private class FakePermissionChecker(var granted: Boolean) : VpnPermissionChecker {
        override fun isGranted(context: Context): Boolean = granted
    }

    private fun controller(
        flow: MutableStateFlow<Boolean>,
        commander: RecordingCommander,
        permission: FakePermissionChecker,
        scope: CoroutineScope
    ): VpnFilterEngineController =
        VpnFilterEngineController(
            appContext = ApplicationProvider.getApplicationContext(),
            desiredStateFlow = flow,
            commander = commander,
            permissionChecker = permission,
            scope = scope
        )

    /** Unconfined dispatcher so preference emissions are collected synchronously in-runTest. */
    private fun TestScope.eagerScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `toggle ON with consent starts the engine`() = runTest {
        val commander = RecordingCommander()
        val controller = controller(MutableStateFlow(true), commander, FakePermissionChecker(true), eagerScope())
        controller.start()

        assertEquals(1, commander.starts.size)
        assertEquals(0, commander.stops.size)
        assertEquals(0, commander.consentPrompts.size)
        assertTrue(controller.desiredEnabledSnapshot)
    }

    @Test
    fun `app restart with blocking enabled restores the engine`() = runTest {
        // The preference was already true before this process started (DataStore default true) —
        // the first emission alone must bring the engine up.
        val commander = RecordingCommander()
        controller(MutableStateFlow(true), commander, FakePermissionChecker(true), eagerScope()).start()
        assertEquals(1, commander.starts.size)
    }

    @Test
    fun `toggle OFF stops a running engine`() = runTest {
        val commander = RecordingCommander()
        val flow = MutableStateFlow(true)
        val controller = controller(flow, commander, FakePermissionChecker(true), eagerScope())
        controller.start()
        controller.onEngineRunningChanged(true)
        assertEquals(1, commander.starts.size)

        flow.value = false
        assertEquals(1, commander.stops.size)
        assertFalse(controller.desiredEnabledSnapshot)
    }

    @Test
    fun `toggle OFF while already stopped is a no-op`() = runTest {
        val commander = RecordingCommander()
        val flow = MutableStateFlow(false)
        val controller = controller(flow, commander, FakePermissionChecker(true), eagerScope())
        controller.start()
        assertEquals(0, commander.starts.size)
        flow.value = false // re-emission of the same value must not double-fire
        assertEquals(0, commander.stops.size)
    }

    @Test
    fun `missing consent in the foreground prompts once per process - not on every evaluation`() = runTest {
        val commander = RecordingCommander()
        val controller = controller(
            MutableStateFlow(true),
            commander,
            FakePermissionChecker(false),
            backgroundScope
        )
        controller.start() // background: no prompt yet
        assertEquals(0, commander.consentPrompts.size)

        controller.onAppForeground() // first visible evaluation prompts
        assertEquals(1, commander.consentPrompts.size)

        controller.onEngineRunningChanged(false) // re-evaluation while still visible: no nagging
        assertEquals(1, commander.consentPrompts.size)
    }

    @Test
    fun `missing consent in the background defers the prompt until the app is visible`() = runTest {
        val commander = RecordingCommander()
        val controller = controller(MutableStateFlow(true), commander, FakePermissionChecker(false), eagerScope())
        controller.start()
        assertEquals(0, commander.consentPrompts.size)
        assertEquals(0, commander.starts.size)

        controller.onAppForeground()
        assertEquals(1, commander.consentPrompts.size)
    }

    @Test
    fun `explicit OFF to ON transition always prompts again`() = runTest {
        val commander = RecordingCommander()
        val flow = MutableStateFlow(false)
        val controller = controller(flow, commander, FakePermissionChecker(false), eagerScope())
        controller.start()
        controller.onAppForeground()

        flow.value = true // fresh user intent — even if a prompt already happened this process
        assertEquals(1, commander.consentPrompts.size)
        flow.value = false
        flow.value = true // toggling OFF/ON is again a fresh intent
        assertEquals(2, commander.consentPrompts.size)
    }

    @Test
    fun `engine stopped while still desired is restarted`() = runTest {
        val commander = RecordingCommander()
        val controller = controller(MutableStateFlow(true), commander, FakePermissionChecker(true), eagerScope())
        controller.start()
        assertEquals(1, commander.starts.size)

        controller.onEngineRunningChanged(true)
        assertEquals(1, commander.starts.size) // running: nothing to do

        controller.onEngineRunningChanged(false) // e.g. system killed it: reconcile -> restart
        assertEquals(2, commander.starts.size)
    }

    @Test
    fun `failed background start is retried when the app becomes visible`() = runTest {
        val commander = RecordingCommander(startResult = false)
        val controller = controller(MutableStateFlow(true), commander, FakePermissionChecker(true), eagerScope())
        controller.start()
        assertEquals(1, commander.starts.size)

        controller.onAppForeground() // start failed before; retry on foreground
        assertEquals(2, commander.starts.size)
    }
}
