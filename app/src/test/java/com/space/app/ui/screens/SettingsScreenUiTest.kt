package com.space.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.space.app.data.ApiSource
import com.space.app.data.KeyHealth
import com.space.app.data.ProviderKind
import com.space.app.ui.screens.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM UI tests for the settings screen: empty state, adding a key, health display.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun source(
        id: String = "s1",
        provider: ProviderKind = ProviderKind.GEMINI,
        label: String = "My Gemini"
    ) = ApiSource(
        id = id,
        provider = provider,
        label = label,
        maskedKeyHint = "••••abcd",
        order = 0
    )

    @Test
    fun emptyState_showsNoKeysMessage() {
        compose.setContent {
            SettingsScreen(
                sources = emptyList(),
                health = emptyMap(),
                onBack = {},
                onAdd = { _, _, _ -> },
                onRemove = {},
                onMoveUp = {},
                onMoveDown = {}
            )
        }

        compose.onNodeWithText("No keys yet. Without one, Space uses the offline starter plan.").assertIsDisplayed()
        compose.onNodeWithText("API sources").assertIsDisplayed()
        compose.onNodeWithText("Your keys").assertIsDisplayed()
        compose.onNodeWithText("Add a key").assertIsDisplayed()
    }

    @Test
    fun withSources_showsRows_andHealth() {
        compose.setContent {
            SettingsScreen(
                sources = listOf(source("s1", ProviderKind.GEMINI, "Main key")),
                health = mapOf("s1" to KeyHealth.Cooling(9_999_999_999_999L)),
                onBack = {},
                onAdd = { _, _, _ -> },
                onRemove = {},
                onMoveUp = {},
                onMoveDown = {}
            )
        }

        compose.onNodeWithText("Main key").assertIsDisplayed()
        compose.onNodeWithText("Gemini · ••••abcd").assertIsDisplayed()
        compose.onNodeWithText("Cooling down").assertIsDisplayed()
    }

    @Test
    fun addButton_isClickable() {
        var clicked = false

        compose.setContent {
            SettingsScreen(
                sources = emptyList(),
                health = emptyMap(),
                onBack = {},
                onAdd = { _, _, _ -> clicked = true },
                onRemove = {},
                onMoveUp = {},
                onMoveDown = {}
            )
        }

        compose.onNodeWithText("Add source").assertIsEnabled().performClick()
        // With no key entered, the button shows an error and does not call onAdd.
        org.junit.Assert.assertFalse("onAdd must not fire when key is blank", clicked)
    }

    @Test
    fun withSources_showsRemoveButton() {
        compose.setContent {
            SettingsScreen(
                sources = listOf(source()),
                health = mapOf("s1" to KeyHealth.Active),
                onBack = {},
                onAdd = { _, _, _ -> },
                onRemove = {},
                onMoveUp = {},
                onMoveDown = {}
            )
        }

        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithText("Remove").assertIsDisplayed()
    }
}