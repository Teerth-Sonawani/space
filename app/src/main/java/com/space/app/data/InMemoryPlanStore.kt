package com.space.app.data

import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.model.Plan

/**
 * A [PlanStore] that keeps everything in memory, newest first. It never touches
 * disk, so it is safe on a plain JVM: the view model falls back to it when the
 * file-backed store cannot be built (a unit test, where `Context.filesDir` is
 * not mocked), and tests use it directly to stay deterministic.
 */
class InMemoryPlanStore : PlanStore {

    private val plans = ArrayList<StoredPlan>()
    private var draft: Draft? = null

    override fun loadAll(): List<StoredPlan> = ArrayList(plans)

    override fun mostRecent(): StoredPlan? = plans.firstOrNull()

    override fun save(
        brief: IdeaBrief,
        plan: Plan,
        savedAt: Long,
        id: String
    ): StoredPlan {
        val entry = StoredPlan(id = id, brief = brief, plan = plan, savedAt = savedAt)
        plans.add(0, entry)
        // Keep only the newest MAX_PLANS, dropping the oldest from the tail.
        while (plans.size > MAX_PLANS) plans.removeAt(plans.lastIndex)
        return entry
    }

    override fun rename(id: String, title: String): Boolean {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return false
        val index = plans.indexOfFirst { it.id == id }
        if (index < 0) return false
        plans[index] = plans[index].copy(plan = plans[index].plan.copy(title = trimmed))
        return true
    }

    override fun delete(id: String): StoredPlan? {
        val index = plans.indexOfFirst { it.id == id }
        if (index < 0) return null
        return plans.removeAt(index)
    }

    override fun saveDraft(draft: Draft) {
        this.draft = draft
    }

    override fun loadDraft(): Draft? = draft

    override fun clearDraft() {
        draft = null
    }

    companion object {
        /** Mirrors the file-backed store's retention cap. */
        const val MAX_PLANS = 50
    }
}
