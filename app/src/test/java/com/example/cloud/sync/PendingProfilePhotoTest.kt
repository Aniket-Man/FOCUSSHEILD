package com.example.cloud.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PendingProfilePhotoTest {
    @Test
    fun `a removal is distinct from no pending edit and survives recreation`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = CloudInstallState(context)
        state.setPendingProfilePhoto("removal-account", null)
        val restored = CloudInstallState(context).pendingProfilePhoto("removal-account")
        assertNotNull(restored)
        assertNull(restored!!.photoUri)
        assertNull(state.pendingProfilePhoto("other-account"))
        state.clearPendingProfilePhoto(restored)
        assertNull(state.pendingProfilePhoto("removal-account"))
    }

    @Test
    fun `finishing an old upload cannot erase a newer edit`() = runTest {
        val state = CloudInstallState(ApplicationProvider.getApplicationContext<Context>())
        state.setPendingProfilePhoto("editing-account", "file://first.jpg")
        val first = state.pendingProfilePhoto("editing-account")!!
        state.setPendingProfilePhoto("editing-account", "file://second.jpg")
        state.clearPendingProfilePhoto(first)
        assertEquals("file://second.jpg", state.pendingProfilePhoto("editing-account")!!.photoUri)
        state.clearPendingProfilePhoto(state.pendingProfilePhoto("editing-account")!!)
        assertNull(state.pendingProfilePhoto("editing-account"))
    }
}
