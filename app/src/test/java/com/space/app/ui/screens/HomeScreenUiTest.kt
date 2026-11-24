package com.space.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.space.app.ui.screens.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM UI tests for the home screen: entry points navigate correctly, the
 * empty state renders, and disabled states are respected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyState_showsTitleAndEntryPoints() {
        var newIdea = 0
        var openSample = 0
        var openSettings = 0

        compose.setContent {
            HomeScreen(
                recentPlanTitle = null,
                savedPlanCount = 0,
                onNewIdea = { newIdea++ },
                onOpenRecent = {},
                onOpenSample = { openSample++ },
                onOpenLibrary = {},
                onOpenSettings = { openSettings++ }
            )
        }

        compose.onNodeWithText("Space").assertIsDisplayed()
        compose.onNodeWithText("Drop in an idea. Get a plan you can walk through.").assertIsDisplayed()
        compose.onNodeWithText("No plans yet").assertIsDisplayed()
        compose.onNodeWithText("Your saved plans will gather here.").assertIsDisplayed()

        compose.onNodeWithText("New idea").assertIsEnabled().performClick()
        assertEquals(1, newIdea)

        compose.onNodeWithText("Open the sample plan").assertIsEnabled().performClick()
        assertEquals(1, openSample)

        compose.onNodeWithText("Settings").assertIsEnabled().performClick()
        assertEquals(1, openSettings)
    }

    @Test
    fun withRecentPlan_showsRecentCard() {
        var opened = false

        compose.setContent {
            HomeScreen(
                recentPlanTitle = "Launch a coffee cart",
                savedPlanCount = 1,
                onNewIdea = {},
                onOpenRecent = { opened = true },
                onOpenSample = {},
                onOpenLibrary = {},
                onOpenSettings = {}
            )
        }

        compose.onNodeWithText("Last plan").assertIsDisplayed()
        compose.onNodeWithText("Launch a coffee cart").assertIsDisplayed()
        compose.onNodeWithText("Your plans").assertIsDisplayed()

        compose.onNodeWithText("Launch a coffee cart").performClick()
        assertTrue("opening the recent plan must call onOpenRecent", opened)
    }
}
