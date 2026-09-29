package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.core.design.FocusShieldTheme
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshot coverage for the home screen.
 *
 * This test used to capture an image and assert nothing, which meant it could only fail if the
 * composition threw: an empty screen, a screen that rendered nothing, or a golden that drifted all
 * passed silently (issue #31). It now makes three claims that can actually fail:
 *
 *  1. the composition produces a displayed root;
 *  2. that root has content (a non-empty semantics tree) — an all-blank screen is a failure;
 *  3. the golden file was really written/compared, so a broken capture pipeline is not mistaken
 *     for a passing screenshot test.
 *
 * Drift itself is caught by `verifyRoborazziDebug`, which `check` depends on once a golden has been
 * recorded (see `app/build.gradle.kts`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun homeScreen_rendersContentAndCapturesGolden() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    composeTestRule.setContent {
      FocusShieldTheme {
        HomeScreen(
          viewModel = HomeViewModel(application),
          onNavigateToStartSession = {},
          onNavigateToActiveSession = {},
          onNavigateToModeSelection = {},
          onNavigateToPlannerTab = {},
          onNavigateToStatsTab = {},
          onNavigateToBlocksTab = {},
          onNavigateToProfile = {}
        )
      }
    }

    composeTestRule.onRoot().assertIsDisplayed()

    val renderedNodes = composeTestRule.onRoot().fetchSemanticsNode().children.size
    assertTrue(
      "HomeScreen rendered an empty semantics tree; the screenshot would be blank",
      renderedNodes > 0
    )

    val golden = File(GOLDEN_PATH)
    composeTestRule.onRoot().captureRoboImage(filePath = GOLDEN_PATH)

    assertTrue(
      "No screenshot was produced at ${golden.absolutePath}; the capture pipeline is broken",
      golden.isFile && golden.length() > 0L
    )
  }

  private companion object {
    const val GOLDEN_PATH = "src/test/screenshots/greeting.png"
  }
}
