package com.space.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.space.app.data.ApiSource
import com.space.app.data.Draft
import com.space.app.data.EncryptedSettingsRepository
import com.space.app.data.FilePlanStore
import com.space.app.data.InMemoryPlanStore
import com.space.app.data.InMemorySettingsRepository
import com.space.app.data.KeyHealth
import com.space.app.data.PlanStore
import com.space.app.data.ProviderKind
import com.space.app.data.SettingsRepository
import com.space.app.data.StoredPlan
import com.space.app.domain.generation.ChainResult
import com.space.app.domain.generation.ChainedProvider
import com.space.app.domain.generation.ChainedQuestionsProvider
import com.space.app.domain.generation.KeyHealthStore
import com.space.app.domain.generation.LlmPlanGenerator
import com.space.app.domain.generation.OfflineQuestions
import com.space.app.domain.generation.PlanGenerator
import com.space.app.domain.generation.ProviderChain
import com.space.app.domain.generation.QuestionsChain
import com.space.app.domain.generation.QuestionsChainResult
import com.space.app.domain.generation.QuestionsGenerator
import com.space.app.domain.generation.StarterPlanGenerator
import com.space.app.domain.generation.providers.LlmClientFactory
import com.space.app.domain.generation.providers.defaultHttpClient
import com.space.app.domain.model.Answer
import com.space.app.domain.model.ClarifyingQuestion
import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.model.Plan
import com.space.app.domain.validation.PlanValidation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** What the generating screen is doing, so it can render a real state. */
sealed interface GenerationState {
    data object Idle : GenerationState
    data object Running : GenerationState
    data object Ready : GenerationState
    data class Failed(
        val reason: String,
        val canUseStarter: Boolean = true
    ) : GenerationState
}

sealed interface QuestionsLoadState {
    data object Idle : QuestionsLoadState
    data object Loading : QuestionsLoadState
    data class Ready(val fromOfflinePack: Boolean) : QuestionsLoadState
}

/**
 * Holds the whole idea -> questions -> plan flow so nothing is lost between
 * screens. Production builds providers from encrypted settings; unit tests
 * inject [store] / [generator] (any non-[StarterPlanGenerator] uses the
 * injected path).
 */
class PlanFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application

    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var _store: PlanStore? = null

    var store: PlanStore
        get() = _store ?: resolveDefaultStore().also { _store = it }
        set(value) {
            _store = value
            restoreFlowFromStore()
        }

    /**
     * Test seam for the plan call. When null (production) the live
     * [ProviderChain] over configured keys is used.
     */
    var generator: PlanGenerator? = null

    /** Test seam for the questions call; null means the live [QuestionsChain]. */
    var questionsChain: QuestionsChain? = null

    var settings: SettingsRepository = resolveDefaultSettings()
        set(value) {
            field = value
            observeSettings()
        }

    val healthStore: KeyHealthStore = KeyHealthStore()

    var httpClient: OkHttpClient = defaultHttpClient()

    private val apiSources = mutableStateListOf<ApiSource>()
    val sources: List<ApiSource> get() = apiSources.toList()

    var sourceHealth: Map<String, KeyHealth> by mutableStateOf(emptyMap())
        private set

    var generation: GenerationState by mutableStateOf(GenerationState.Idle)

    val recordedAnswers = mutableStateListOf<Answer>()

    var idea by mutableStateOf("")
        private set

    var plan by mutableStateOf<Plan?>(null)
        private set

    var usedStarter by mutableStateOf(false)
        private set

    var recentPlan by mutableStateOf<StoredPlan?>(null)
        private set

    val savedPlans = mutableStateListOf<StoredPlan>()

    val clarifyingQuestions = mutableStateListOf<ClarifyingQuestion>()

    var questionsLoad: QuestionsLoadState by mutableStateOf(QuestionsLoadState.Idle)
        private set

    private var generateJob: Job? = null
    private var questionsJob: Job? = null
    private var settingsJob: Job? = null

    private fun restoreFlowFromStore() {
        viewModelScope.launch {
            reload()
            val draft = withContext(ioDispatcher) { store.loadDraft() }
            if (draft != null && idea.isBlank()) {
                idea = draft.idea
                recordedAnswers.clear()
                recordedAnswers.addAll(draft.answers)
            }
        }
    }

    private suspend fun reload() {
        val all = withContext(ioDispatcher) { store.loadAll() }
        savedPlans.clear()
        savedPlans.addAll(all)
        recentPlan = all.firstOrNull()
    }

    private fun resolveDefaultStore(): PlanStore = try {
        FilePlanStore(app.filesDir)
    } catch (_: Throwable) {
        InMemoryPlanStore()
    }

    private fun resolveDefaultSettings(): SettingsRepository = try {
        EncryptedSettingsRepository(app)
    } catch (_: Throwable) {
        InMemorySettingsRepository()
    }

    init {
        restoreFlowFromStore()
        observeSettings()
        viewModelScope.launch {
            healthStore.health.collectLatest { sourceHealth = it }
        }
    }

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            settings.sources.collectLatest { list ->
                apiSources.clear()
                apiSources.addAll(list.sortedBy { it.order })
            }
        }
    }

    /** Configured keys in priority order; empty when none are usable. */
    private suspend fun liveSources(): List<Pair<ApiSource, String>> =
        apiSources.mapNotNull { source ->
            val key = settings.getApiKey(source.id)?.trim().orEmpty()
            if (key.isEmpty()) null else source to key
        }

    private suspend fun buildLiveProviders(): List<ChainedProvider> =
        liveSources().map { (source, key) ->
            ChainedProvider(
                sourceId = source.id,
                planGenerator = LlmPlanGenerator(
                    LlmClientFactory.create(source.provider, key, httpClient)
                )
            )
        }

    private suspend fun buildLiveQuestionProviders(): List<ChainedQuestionsProvider> =
        liveSources().map { (source, key) ->
            ChainedQuestionsProvider(
                sourceId = source.id,
                generator = QuestionsGenerator(
                    LlmClientFactory.create(source.provider, key, httpClient)
                )
            )
        }

    private fun newLiveQuestionsChain() = QuestionsChain(
        providers = { buildLiveQuestionProviders() },
        health = healthStore
    )

    fun updateIdea(value: String) {
        idea = value
    }

    fun saveDraftNow() {
        viewModelScope.launch {
            val draft = Draft(idea = idea, answers = recordedAnswers.toList())
            withContext(ioDispatcher) { store.saveDraft(draft) }
        }
    }

    fun recordAnswer(question: String, answer: String) {
        recordedAnswers.removeAll { it.question == question }
        if (answer.isNotBlank()) {
            recordedAnswers.add(Answer(question = question, answer = answer))
        }
        saveDraftNow()
    }

    fun startNewIdea() {
        idea = ""
        recordedAnswers.clear()
        clarifyingQuestions.clear()
        questionsLoad = QuestionsLoadState.Idle
        generation = GenerationState.Idle
        usedStarter = false
        generateJob?.cancel()
        questionsJob?.cancel()
        viewModelScope.launch { withContext(ioDispatcher) { store.clearDraft() } }
    }

    /**
     * Loads AI questions, falling back to the offline pack when no key is
     * configured or every key fails. Call when leaving the idea screen.
     */
    fun loadQuestions(onReady: () -> Unit = {}) {
        questionsJob?.cancel()
        questionsLoad = QuestionsLoadState.Loading
        clarifyingQuestions.clear()
        questionsJob = viewModelScope.launch {
            val offline = OfflineQuestions.pack
            val chain = questionsChain ?: newLiveQuestionsChain()
            val result = runCatching { chain.generate(idea) }
            when (val outcome = result.getOrNull()) {
                is QuestionsChainResult.Ok -> {
                    clarifyingQuestions.addAll(outcome.questions)
                    questionsLoad = QuestionsLoadState.Ready(fromOfflinePack = false)
                }

                // Failed, threw, or no keys — the user still gets questions.
                else -> {
                    clarifyingQuestions.addAll(offline)
                    questionsLoad = QuestionsLoadState.Ready(fromOfflinePack = true)
                }
            }
            onReady()
        }
    }

    /** Loads questions on first entry to the questions screen. */
    fun loadQuestionsIfNeeded() {
        if (questionsLoad is QuestionsLoadState.Idle && clarifyingQuestions.isEmpty()) {
            loadQuestions()
        }
    }

    fun cancelQuestionsLoad() {
        questionsJob?.cancel()
        questionsLoad = QuestionsLoadState.Idle
    }

    fun generate() {
        if (generation !is GenerationState.Idle) return
        generation = GenerationState.Running
        usedStarter = false
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val brief = IdeaBrief(idea = idea, answers = recordedAnswers.toList())
            // Unit tests inject a PlanGenerator; production uses the live chain.
            val injected = generator
            if (injected != null) {
                when (val result = injected.generate(brief)) {
                    is PlanValidation.Valid -> finishSuccess(brief, result.plan, starter = false)
                    is PlanValidation.Invalid ->
                        generation = GenerationState.Failed(result.errors.first(), canUseStarter = true)
                }
                return@launch
            }
            val liveChain = ProviderChain(
                providers = { buildLiveProviders() },
                health = healthStore,
                starter = StarterPlanGenerator()
            )
            when (
                val result = liveChain.generateChain(
                    brief = brief,
                    autoStarterWhenEmpty = true,
                    allowStarterAfterExhaustion = false
                )
            ) {
                is ChainResult.Ok -> finishSuccess(brief, result.plan, starter = result.usedStarter)
                is ChainResult.Failed ->
                    generation = GenerationState.Failed(
                        reason = result.reason,
                        canUseStarter = result.canUseStarter
                    )
            }
        }
    }

    fun useStarterPlan() {
        if (generation !is GenerationState.Failed && generation !is GenerationState.Idle) return
        generation = GenerationState.Running
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val brief = IdeaBrief(idea = idea, answers = recordedAnswers.toList())
            when (val result = StarterPlanGenerator().generate(brief)) {
                is PlanValidation.Valid -> finishSuccess(brief, result.plan, starter = true)
                is PlanValidation.Invalid ->
                    generation = GenerationState.Failed(result.errors.first(), canUseStarter = false)
            }
        }
    }

    private suspend fun finishSuccess(brief: IdeaBrief, result: Plan, starter: Boolean) {
        plan = result
        usedStarter = starter
        withContext(ioDispatcher) {
            store.save(brief = brief, plan = result)
            store.clearDraft()
        }
        reload()
        generation = GenerationState.Ready
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        generation = GenerationState.Idle
    }

    fun retryGeneration() {
        if (generation is GenerationState.Failed) {
            generation = GenerationState.Idle
        }
    }

    fun openRecentPlan(onLoaded: (StoredPlan?) -> Unit = {}) {
        viewModelScope.launch {
            val recent = withContext(ioDispatcher) { store.mostRecent() }
            recent?.let { plan = it.plan }
            onLoaded(recent)
        }
    }

    fun openPlan(stored: StoredPlan) {
        plan = stored.plan
        usedStarter = false
    }

    fun renamePlan(id: String, title: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) { store.rename(id, title) }
            reload()
        }
    }

    fun deletePlan(id: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) { store.delete(id) }
            reload()
        }
    }

    fun undoDelete(stored: StoredPlan) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                store.save(stored.brief, stored.plan, stored.savedAt, stored.id)
            }
            reload()
        }
    }

    fun addApiSource(
        provider: ProviderKind,
        label: String,
        apiKey: String,
        onDone: (Result<ApiSource>) -> Unit = {}
    ) {
        viewModelScope.launch {
            onDone(runCatching { settings.addSource(provider, label, apiKey) })
        }
    }

    fun removeApiSource(id: String) {
        viewModelScope.launch {
            settings.removeSource(id)
            healthStore.clear(id)
        }
    }

    fun reorderApiSources(ids: List<String>) {
        viewModelScope.launch { settings.reorder(ids) }
    }

    /**
     * Moves one source up or down in priority (Settings arrows). The list
     * order is the failover order, so this is the only control over which key
     * gets tried first.
     */
    fun moveApiSource(id: String, delta: Int) {
        val current = apiSources.toList()
        val index = current.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in current.indices) return
        val reordered = current.toMutableList()
        val moved = reordered.removeAt(index)
        reordered.add(target, moved)
        reorderApiSources(reordered.map { it.id })
    }
}
