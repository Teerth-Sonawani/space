package com.space.app.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.space.app.data.Draft
import com.space.app.data.InMemoryPlanStore
import com.space.app.data.InMemorySettingsRepository
import com.space.app.data.PlanStore
import com.space.app.data.ProviderKind
import com.space.app.data.StoredPlan
import com.space.app.domain.generation.ChainedQuestionsProvider
import com.space.app.domain.generation.FakeLlmClient
import com.space.app.domain.generation.LlmResult
import com.space.app.domain.generation.OfflineQuestions
import com.space.app.domain.generation.PlanGenerator
import com.space.app.domain.generation.QuestionsChain
import com.space.app.domain.generation.QuestionsGenerator
import com.space.app.domain.generation.StarterPlanGenerator
import com.space.app.domain.generation.ValidatingPlanGenerator
import com.space.app.domain.generation.questionsJson
import com.space.app.domain.model.Answer
import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.model.Plan
import com.space.app.domain.sample.SamplePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

/**
 * Exercises [PlanFlowViewModel] through its PUBLIC API only. It does not touch
 * private @Composable state, and it does not add a Mockito dependency (this
 * project only declares JUnit). The data layer it sits on ([Draft],
 * [PlanStore]) is covered for serialization by [PlanCodecTest].
 */
class PlanFlowViewModelTest {

    @Test
    fun `new viewModel starts Idle with empty idea and no plan`() {
        val viewModel = makeViewModel()
        assertEquals("", viewModel.idea)
        assertEquals(0, viewModel.recordedAnswers.size)
        assertEquals(GenerationState.Idle, viewModel.generation)
        assertNull(viewModel.plan)
    }

    @Test
    fun `recordAnswer trims, de-dupes and saves a draft`() {
        val viewModel = makeViewModel()
        viewModel.updateIdea("Launch a coffee cart")
        viewModel.recordAnswer("Who is this for?", "Customers")
        viewModel.recordAnswer("Who is this for?", "Friends and family")

        assertEquals(1, viewModel.recordedAnswers.size)
        assertEquals("Friends and family", viewModel.recordedAnswers[0].answer)

        val expectedDraft = Draft(
            idea = "Launch a coffee cart",
            answers = listOf(Answer("Who is this for?", "Friends and family"))
        )
        assertEquals(expectedDraft, viewModel.store.loadDraft())
    }

    @Test
    fun `generate from Idle writes a plan, clears the draft, becomes Ready`() {
        val viewModel = makeViewModel()
        viewModel.updateIdea("Open a bookshop")
        viewModel.recordAnswer("Who is this for?", "Just me")

        val expectedPlan = SamplePlan.plan
        viewModel.generator = object : ValidatingPlanGenerator() {
            override fun buildPlan(brief: IdeaBrief): Plan = expectedPlan
        }

        viewModel.generate()

        assertEquals(GenerationState.Ready, viewModel.generation)
        assertEquals(expectedPlan, viewModel.plan)
        assertNull(viewModel.store.loadDraft())
        assertNotNull(viewModel.recentPlan)
        assertEquals("Open a bookshop", viewModel.recentPlan!!.brief.idea)
    }

    @Test
    fun `re-entering generating with Ready does not re-run or save a duplicate`() {
        val viewModel = makeViewModel()
        viewModel.updateIdea("Second idea")
        viewModel.recordAnswer("Who is this for?", "Customers")

        val firstPlan = SamplePlan.plan
        viewModel.generator = object : ValidatingPlanGenerator() {
            override fun buildPlan(brief: IdeaBrief): Plan = firstPlan
        }
        viewModel.generate()
        assertEquals(GenerationState.Ready, viewModel.generation)

        val secondPlan = SamplePlan.plan.copy(title = "Different")
        viewModel.generator = object : ValidatingPlanGenerator() {
            override fun buildPlan(brief: IdeaBrief): Plan = secondPlan
        }
        viewModel.generation = GenerationState.Ready
        viewModel.generate()

        assertEquals(GenerationState.Ready, viewModel.generation)
        assertEquals(firstPlan, viewModel.plan) // not re-run
        assertEquals(1, viewModel.store.loadAll().size) // no duplicate saved
        assertEquals(firstPlan, viewModel.recentPlan?.plan)
    }

    @Test
    fun `retryGeneration resets Failed to Idle for a new attempt`() {
        val viewModel = makeViewModel()
        viewModel.generation = GenerationState.Failed("Something went wrong")
        viewModel.retryGeneration()
        assertEquals(GenerationState.Idle, viewModel.generation)
    }

    @Test
    fun `startNewIdea clears the flow and discards the draft`() {
        val viewModel = makeViewModel()
        viewModel.updateIdea("First idea")
        viewModel.recordAnswer("Who is this for?", "Customers")

        viewModel.startNewIdea()

        assertEquals("", viewModel.idea)
        assertTrue(viewModel.recordedAnswers.isEmpty())
        assertEquals(GenerationState.Idle, viewModel.generation)
        assertNull(viewModel.store.loadDraft())
    }

    @Test
    fun `openRecentPlan puts the most recent plan into view`() {
        val viewModel = makeViewModel()
        val stored = StoredPlan(
            id = "plan-1",
            brief = IdeaBrief(idea = "Open a bookshop", answers = emptyList()),
            plan = SamplePlan.plan,
            savedAt = 1L
        )
        viewModel.store = makeStore(saved = stored)
        viewModel.openRecentPlan()
        assertEquals(SamplePlan.plan, viewModel.plan)
    }

    @Test
    fun `draft restored on launch only when the idea is blank`() {
        val viewModel = makeViewModel()
        val seeded = Draft(
            idea = "Restore me",
            answers = listOf(Answer("Who is this for?", "Friends and family"))
        )
        viewModel.store = makeStore(draft = seeded)
        assertEquals("Restore me", viewModel.idea)
        assertEquals(1, viewModel.recordedAnswers.size)
        assertEquals("Friends and family", viewModel.recordedAnswers[0].answer)

        val viewModel2 = makeViewModel()
        viewModel2.updateIdea("Some pre-filled idea")
        viewModel2.store = makeStore(draft = seeded)
        assertEquals("Some pre-filled idea", viewModel2.idea)
        assertTrue(viewModel2.recordedAnswers.isEmpty())
    }

    @Test
    fun `a fresh draft is written through saveDraftNow`() {
        val viewModel = makeViewModel()
        viewModel.updateIdea("Blank canvas")
        viewModel.recordAnswer("What matters?", "People")
        assertNotNull(viewModel.store.loadDraft())
        assertEquals("Blank canvas", viewModel.store.loadDraft()!!.idea)
        assertEquals(1, viewModel.store.loadDraft()!!.answers.size)
    }

    @Test
    fun `generating a plan also records it in savedPlans`() {
        val viewModel = makeViewModel()
        viewModel.updateIdea("Start a blog")
        viewModel.generate()

        assertEquals(GenerationState.Ready, viewModel.generation)
        assertEquals(1, viewModel.savedPlans.size)
        assertEquals("Start a blog", viewModel.savedPlans.first().brief.idea)
    }

    @Test
    fun `renamePlan retitles the stored plan and refreshes recentPlan`() {
        val viewModel = makeViewModel()
        val stored = StoredPlan(
            id = "plan-1",
            brief = IdeaBrief(idea = "Open a bookshop", answers = emptyList()),
            plan = SamplePlan.plan,
            savedAt = 1L
        )
        viewModel.store = makeStore(saved = stored)

        viewModel.renamePlan("plan-1", "My Bookshop")

        assertEquals("My Bookshop", viewModel.recentPlan?.plan?.title)
        assertEquals("My Bookshop", viewModel.savedPlans.first().plan.title)
    }

    @Test
    fun `deletePlan removes the plan from the library`() {
        val viewModel = makeViewModel()
        val stored = StoredPlan(
            id = "plan-1",
            brief = IdeaBrief(idea = "Open a bookshop", answers = emptyList()),
            plan = SamplePlan.plan,
            savedAt = 1L
        )
        viewModel.store = makeStore(saved = stored)
        assertEquals(1, viewModel.savedPlans.size)

        viewModel.deletePlan("plan-1")

        assertTrue(viewModel.savedPlans.isEmpty())
        assertNull(viewModel.recentPlan)
    }

    @Test
    fun `undoDelete restores a deleted plan to the library`() {
        val viewModel = makeViewModel()
        val stored = StoredPlan(
            id = "plan-1",
            brief = IdeaBrief(idea = "Open a bookshop", answers = emptyList()),
            plan = SamplePlan.plan,
            savedAt = 1L
        )
        viewModel.store = makeStore(saved = stored)

        viewModel.deletePlan("plan-1")
        assertTrue(viewModel.savedPlans.isEmpty())

        viewModel.undoDelete(stored)

        assertEquals(1, viewModel.savedPlans.size)
        assertEquals("plan-1", viewModel.savedPlans.first().id)
    }

    @Test
    fun `openPlan puts the chosen plan on the chart`() {
        val viewModel = makeViewModel()
        val stored = StoredPlan(
            id = "plan-1",
            brief = IdeaBrief(idea = "Open a bookshop", answers = emptyList()),
            plan = SamplePlan.plan.copy(title = "Bookshop"),
            savedAt = 1L
        )

        viewModel.openPlan(stored)

        assertEquals("Bookshop", viewModel.plan?.title)
    }

    @Test
    fun `loadQuestions with no configured keys falls back to the offline pack`() {
        val viewModel = makeViewModel()
        viewModel.settings = InMemorySettingsRepository()
        viewModel.updateIdea("Open a coffee cart")

        viewModel.loadQuestions()

        assertEquals(OfflineQuestions.pack, viewModel.clarifyingQuestions.toList())
        assertEquals(QuestionsLoadState.Ready(fromOfflinePack = true), viewModel.questionsLoad)
    }

    @Test
    fun `loadQuestions uses the configured chain when a key answers`() {
        val viewModel = makeViewModel()
        viewModel.updateIdea("Open a coffee cart")
        viewModel.questionsChain = QuestionsChain(
            providers = {
                listOf(
                    ChainedQuestionsProvider(
                        sourceId = "s1",
                        generator = QuestionsGenerator(FakeLlmClient(LlmResult.Ok(questionsJson(3))))
                    )
                )
            }
        )

        viewModel.loadQuestions()

        assertEquals(3, viewModel.clarifyingQuestions.size)
        assertEquals("Question number 1?", viewModel.clarifyingQuestions.first().prompt)
        assertEquals(QuestionsLoadState.Ready(fromOfflinePack = false), viewModel.questionsLoad)
    }

    @Test
    fun `loadQuestionsIfNeeded keeps already-loaded questions`() {
        val viewModel = makeViewModel()
        viewModel.updateIdea("Same idea both times")
        viewModel.questionsChain = QuestionsChain(
            providers = {
                listOf(
                    ChainedQuestionsProvider(
                        sourceId = "s1",
                        generator = QuestionsGenerator(FakeLlmClient(LlmResult.Ok(questionsJson(2))))
                    )
                )
            }
        )
        viewModel.loadQuestions()
        val loaded = viewModel.clarifyingQuestions.toList()

        // A second trigger must not clear and re-run the load.
        viewModel.loadQuestionsIfNeeded()

        assertEquals(loaded, viewModel.clarifyingQuestions.toList())
    }

    @Test
    fun `a questions chain that throws still lands on the offline pack`() {
        val viewModel = makeViewModel()
        viewModel.updateIdea("Anything")
        viewModel.questionsChain = QuestionsChain(providers = { error("boom") })

        viewModel.loadQuestions()

        assertEquals(OfflineQuestions.pack, viewModel.clarifyingQuestions.toList())
        assertEquals(QuestionsLoadState.Ready(fromOfflinePack = true), viewModel.questionsLoad)
    }

    @Test
    fun `moveApiSource moves a source up in the failover order`() {
        val viewModel = makeViewModel()
        val settings = InMemorySettingsRepository()
        viewModel.settings = settings
        val first = runBlocking { settings.addSource(ProviderKind.GEMINI, "Gemini", "key-one") }
        val second = runBlocking { settings.addSource(ProviderKind.GROQ, "Groq", "key-two") }

        viewModel.moveApiSource(second.id, delta = -1)

        assertEquals(listOf(second.id, first.id), viewModel.sources.map { it.id })
    }

    @Test
    fun `moveApiSource ignores moves past the ends of the list`() {
        val viewModel = makeViewModel()
        val settings = InMemorySettingsRepository()
        viewModel.settings = settings
        val first = runBlocking { settings.addSource(ProviderKind.GEMINI, "Gemini", "key-one") }
        val second = runBlocking { settings.addSource(ProviderKind.GROQ, "Groq", "key-two") }

        viewModel.moveApiSource(first.id, delta = -1)
        viewModel.moveApiSource(second.id, delta = 1)

        assertEquals(listOf(first.id, second.id), viewModel.sources.map { it.id })
    }

    @Test
    fun `cancelGeneration returns the generating screen to Idle`() {
        val viewModel = makeViewModel()
        viewModel.generation = GenerationState.Running

        viewModel.cancelGeneration()

        assertEquals(GenerationState.Idle, viewModel.generation)
    }


    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Before
    fun `set up dispatchers for coroutines`() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @After
    fun `tear down dispatchers`() {
        Dispatchers.resetMain()
    }

    /** Build a [PlanFlowViewModel] with an in-memory [PlanStore]. */
    private fun makeViewModel(): PlanFlowViewModel {
        val application = TestApplication()
        val viewModel = ViewModelProvider(
            application,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return modelClass.cast(PlanFlowViewModel(application)) as T
                }
            }
        )[PlanFlowViewModel::class.java]
        // Run storage I/O unconfined so the asynchronous view-model flow settles
        // synchronously and the assertions below stay deterministic.
        viewModel.ioDispatcher = Dispatchers.Unconfined
        viewModel.store = makeStore()
        viewModel.generator = StarterPlanGenerator()
        return viewModel
    }

    /** A fresh in-memory store, optionally seeded with one plan and/or a draft. */
    private fun makeStore(
        saved: StoredPlan? = null,
        draft: Draft? = null
    ): PlanStore {
        val impl = InMemoryPlanStore()
        if (saved != null) impl.save(saved.brief, saved.plan, saved.savedAt, saved.id)
        if (draft != null) impl.saveDraft(draft)
        return impl
    }
}

/** A bare [Application] subclass used only to construct the ViewModel. */
private class TestApplication : Application(), ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}
