package com.powerclock.alarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flows through the real MainActivity with Hilt DI:
 * onboarding -> home -> create/edit/delete an alarm -> sound selection.
 *
 * Requires an emulator or device.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hilt.inject()
    }

    private fun completeOnboardingIfShown() {
        compose.waitForIdle()
        val continueButtons = compose.onAllNodesWithText("Continue").fetchSemanticsNodes()
        if (continueButtons.isEmpty()) return
        repeat(6) {
            compose.onNodeWithText("Continue").performScrollTo().performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Let's go").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun onboarding_completesAndShowsDashboard() {
        completeOnboardingIfShown()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Set alarm").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Set alarm").assertIsDisplayed()
    }

    @Test
    fun alarm_createEditDeleteRoundtrip() {
        completeOnboardingIfShown()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Set alarm").fetchSemanticsNodes().isNotEmpty()
        }

        // Create.
        compose.onNodeWithText("Set alarm").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save alarm").performScrollTo().performClick()
        compose.waitForIdle()

        // Back on the dashboard; open the alarms tab.
        compose.onNodeWithText("Alarms").performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("One time").fetchSemanticsNodes().isNotEmpty()
        }

        // Edit: open the alarm and change its sound via the library.
        compose.onAllNodesWithText("One time")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sound library").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Heavy Bell").performClick()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNodeWithText("Save alarm").performScrollTo().performClick()
        compose.waitForIdle()

        // Delete.
        compose.onNodeWithText("Alarms").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Delete alarm")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("No alarms yet").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
