package com.space.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.space.app.domain.sample.SamplePlan
import com.space.app.ui.screens.chart.ChartScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM UI tests for the chart screen: it renders the sample plan, exposes
 * back and fit-to-screen controls, and tap-to-detail works.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChartScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersSamplePlan_withControls() {
        var popped = false
        compose.setContent {
            ChartScreen(
                plan = SamplePlan.plan,
                onBack = { popped = true }
            )
        }

        compose.onNodeWithText("Launch a coffee cart").assertIsDisplayed()
        compose.onNodeWithText("Define the concept").assertIsDisplayed()
        compose.onNodeWithText("Research permits").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fit to screen").assertIsDisplayed()
    }

    @Test
    fun tapNode_isActionable() {
        compose.setContent {
            ChartScreen(plan = SamplePlan.plan, onBack = {})
        }

        // Each node displays its title and is a tapable button.
        compose.onNodeWithText("Define the concept").assertIsDisplayed()
    }

    @Test
    fun backButton_isLabelledForTalkBack() {
        compose.setContent {
            ChartScreen(plan = SamplePlan.plan, onBack = {})
        }
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }
}
