package com.space.app.ui.screens

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.space.app.domain.model.ClarifyingQuestion
import com.space.app.ui.QuestionsLoadState
import com.space.app.ui.screens.questions.QuestionsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM UI tests for the questions flow: advancing, skipping, the progress
 * label's TalkBack announcement, and the polite live region added in the
 * UX pass so the next question is read out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuestionsScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun question(n: Int) = ClarifyingQuestion(
        id = "q$n",
        prompt = "Question number $n?",
        suggestions = listOf("Yes", "No")
    )

    private fun setScreen(
        answers: MutableList<Pair<String, String>> = mutableListOf(),
        onDone: () -> Unit = {}
    ) = compose.setContent {
        QuestionsScreen(
            questions = listOf(question(1), question(2)),
            loadState = QuestionsLoadState.Ready(fromOfflinePack = false),
            fromOfflinePack = false,
            onBack = {},
            onAnswer = { q, a -> answers += q to a },
            onDone = onDone
        )
    }

    @Test
    fun firstQuestion_showsProgress_andIsAPoliteLiveRegion() {
        setScreen()

        compose.onNodeWithText("Question number 1?").assertIsDisplayed()
        // The question *container* must be a live region so TalkBack announces
        // the next question when it slides in (the property lives on the
        // AnimatedContent node, which owns the changing subtree).
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite
            )
        ).assertIsDisplayed()
    }

    @Test
    fun next_recordsAnswer_andAdvancesToLast_thenFinishes() {
        val answers = mutableListOf<Pair<String, String>>()
        var done = 0
        setScreen(answers) { done++ }

        compose.onNodeWithText("Yes").performClick()
        compose.onNodeWithText("Next").performClick()

        assertEquals(listOf("Question number 1?" to "Yes"), answers.toList())
        compose.onNodeWithText("Question number 2?").assertIsDisplayed()

        // On the last question the button becomes "Make it".
        compose.onNodeWithText("Make it").performClick()
        assertEquals(1, done)
        assertEquals(2, answers.size)
    }

    @Test
    fun skip_sendsEmptyAnswer_withoutGettingStuck() {
        val answers = mutableListOf<Pair<String, String>>()
        setScreen(answers)

        compose.onNodeWithText("Skip").performClick()
        assertEquals(listOf("Question number 1?" to ""), answers.toList())
        compose.onNodeWithText("Question number 2?").assertIsDisplayed()
    }

    @Test
    fun backArrow_isLabelledForTalkBack() {
        setScreen()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun offlinePack_showsBanner() {
        compose.setContent {
            QuestionsScreen(
                questions = listOf(question(1)),
                loadState = QuestionsLoadState.Ready(fromOfflinePack = true),
                fromOfflinePack = true,
                onBack = {},
                onAnswer = { _, _ -> },
                onDone = {}
            )
        }
        compose.onNodeWithText("Using the offline question pack").assertIsDisplayed()
        // Progress label is still exposed as a content description.
        compose.onNodeWithContentDescription("Question 1 of 1").assertExists()
    }
}
