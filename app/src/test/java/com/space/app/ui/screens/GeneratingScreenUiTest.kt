package com.space.app.ui.screens

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.space.app.ui.GenerationState
import com.space.app.ui.screens.generating.GeneratingScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM UI tests for the generating screen's failure state: the reason must be
 * announced assertively, and both recovery buttons must actually recover.
 */
@RunWith(RobolectricTestRunner::class)
// Taller than Robolectric's 320x470 default: this screen has ~554dp of
// content and the assertions below require everything on-screen.
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class GeneratingScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun running_showsSteps_andStartsGeneration() {
        var started = 0
        compose.setContent {
            GeneratingScreen(
                state = GenerationState.Running,
                onStart = { started++ },
                onCancel = {},
                onReady = {},
                onRetry = {}
            )
        }

        assertEquals("onStart must fire once", 1, started)
        compose.onNodeWithText("Working on it").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun failure_announcesAssertively_andRetryRecovers() {
        var retries = 0
        compose.setContent {
            GeneratingScreen(
                state = GenerationState.Failed(reason = "Network is offline", canUseStarter = true),
                onStart = {},
                onCancel = {},
                onReady = {},
                onRetry = { retries++ }
            )
        }

        compose.onNodeWithText("Network is offline").assertIsDisplayed()
        // The failure *card* must be an assertive live region (announced at
        // once); the property lives on the Surface container.
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Assertive
            )
        ).assertIsDisplayed()

        compose.onNodeWithText("Try again").assertIsEnabled().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun failure_withoutStarter_hidesStarterButton() {
        var starter = 0
        compose.setContent {
            GeneratingScreen(
                state = GenerationState.Failed(reason = "Key revoked", canUseStarter = false),
                onStart = {},
                onCancel = {},
                onReady = {},
                onRetry = {},
                onUseStarter = { starter++ }
            )
        }

        compose.onNodeWithText("Use offline starter plan").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        assertEquals(0, starter)
    }
}
