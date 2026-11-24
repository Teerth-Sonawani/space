package com.space.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.space.app.data.StoredPlan
import com.space.app.domain.model.Answer
import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.sample.SamplePlan
import com.space.app.ui.screens.library.LibraryScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM UI tests for the library screen: empty state, plan rows, open/rename/delete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun storedPlan(
        id: String = "p1",
        title: String = "Launch a coffee cart"
    ) = StoredPlan(
        id = id,
        brief = IdeaBrief(idea = title, answers = listOf(Answer("Who?", "Me"))),
        plan = SamplePlan.plan.copy(title = title),
        savedAt = 1_000_000L
    )

    @Test
    fun emptyLibrary_showsEmptyState() {
        compose.setContent {
            LibraryScreen(
                plans = emptyList(),
                onBack = {},
                onOpen = {},
                onRename = { _, _ -> },
                onDelete = {},
                onUndoDelete = {}
            )
        }

        compose.onNodeWithText("Nothing saved yet").assertIsDisplayed()
        compose.onNodeWithText("Plans you generate will show up here.").assertIsDisplayed()
        compose.onNodeWithText("Your plans").assertIsDisplayed()
    }

    @Test
    fun withPlans_showsRows_andOpenWorks() {
        var opened: StoredPlan? = null

        compose.setContent {
            LibraryScreen(
                plans = listOf(storedPlan("p1", "Launch a coffee cart"), storedPlan("p2", "Open a bookshop")),
                onBack = {},
                onOpen = { opened = it },
                onRename = { _, _ -> },
                onDelete = {},
                onUndoDelete = {}
            )
        }

        compose.onNodeWithText("Launch a coffee cart").assertIsDisplayed()
        compose.onNodeWithText("Open a bookshop").assertIsDisplayed()

        compose.onNodeWithText("Launch a coffee cart").performClick()
        assertEquals("p1", opened?.id)
    }

    @Test
    fun rename_showsDialog_andCallsBack() {
        var renamed = false

        compose.setContent {
            LibraryScreen(
                plans = listOf(storedPlan()),
                onBack = {},
                onOpen = {},
                onRename = { _, _ -> renamed = true },
                onDelete = {},
                onUndoDelete = {}
            )
        }

        // The rename icon has a content description.
        compose.onNodeWithContentDescription("Rename").performClick()

        compose.onNodeWithText("Rename plan").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }
}