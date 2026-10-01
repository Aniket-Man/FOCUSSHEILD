package com.example.core.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusAlertSoundManagerTest {

    @Test
    fun testPlaySound_doesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Test getting notification sound uri
        val uri = FocusAlertSoundManager.getNotificationSoundUri(context)
        assertNotNull(uri)

        // Verify audio play triggers execute without exceptions
        FocusAlertSoundManager.playSessionChime(context)
        FocusAlertSoundManager.playWarningBuzzer(context)
    }
}
