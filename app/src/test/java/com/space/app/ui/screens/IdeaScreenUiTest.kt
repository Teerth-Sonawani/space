package com.space.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.space.app.ui.screens.idea.IdeaScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests running on the JVM via Robolectric — no emulator needed.
 *
 * Covers the disabled-Continue explanation added in the UX pass: the button
 * must be disabled while the idea is too short, must say what is missing,
 * and must enable once the minimum length is reached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdeaScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shortIdea_disablesContinue_andExplainsWhy() {
        var continued = 0
        compose.setContent {
            IdeaScreen(
                idea = "hi",
                onIdeaChange = {},
                onBack = {},
                onContinue = { continued++ }
            )
        }

        compose.onNodeWithText("Continue").assertExists().assertIsNotEnabled()
        // 8 required, 2 typed -> plural "6 more characters to continue".
        compose.onNodeWithText("6 more characters to continue").assertIsDisplayed()

        compose.onNodeWithText("Continue").performClick()
        assertEquals("disabled button must not fire", 0, continued)
    }

    @Test
    fun longEnoughIdea_enablesContinue() {
        var continued = 0
        compose.setContent {
            IdeaScreen(
                idea = "a valid little idea",
                onIdeaChange = {},
                onBack = {},
                onContinue = { continued++ }
            )
        }

        compose.onNodeWithText("Continue").assertIsEnabled()
        compose.onNodeWithText("6 more characters to continue").assertDoesNotExist()

        compose.onNodeWithText("Continue").performClick()
        assertEquals("enabled button must fire exactly once", 1, continued)
    }
}
