package com.space.app.data

import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.model.Plan
import com.space.app.domain.model.PlanNode
import com.space.app.domain.sample.SamplePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises [FilePlanStore] against a real temporary directory. The store no
 * longer depends on [android.content.Context], so this runs on the plain JVM.
 */
class FilePlanStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store() = FilePlanStore(tempFolder.newFolder())

    private fun plan(title: String): Plan = SamplePlan.plan.copy(title = title)

    private fun save(
        store: FilePlanStore,
        id: String,
        title: String,
        savedAt: Long
    ) = store.save(
        brief = IdeaBrief(idea = "idea for $title"),
        plan = plan(title),
        savedAt = savedAt,
        id = id
    )

    @Test
    fun `a fresh store has no plans and no draft`() {
        val store = store()
        assertTrue(store.loadAll().isEmpty())
        assertNull(store.mostRecent())
        assertNull(store.loadDraft())
    }

    @Test
    fun `saved plans survive a new store over the same directory`() {
        val dir = tempFolder.newFolder()
        FilePlanStore(dir).save(IdeaBrief("keep me"), plan("Kept"), savedAt = 10L, id = "p1")

        val reopened = FilePlanStore(dir)
        assertEquals(1, reopened.loadAll().size)
        assertEquals("p1", reopened.loadAll().first().id)
    }

    @Test
    fun `plans come back newest first`() {
        val store = store()
        save(store, "old", "Old", savedAt = 100L)
        save(store, "new", "New", savedAt = 200L)

        val ids = store.loadAll().map { it.id }
        assertEquals(listOf("new", "old"), ids)
        assertEquals("new", store.mostRecent()?.id)
    }

    @Test
    fun `rename retitles a plan and leaves the rest alone`() {
        val store = store()
        save(store, "a", "Alpha", savedAt = 100L)
        save(store, "b", "Beta", savedAt = 200L)

        assertTrue(store.rename("a", "Alpha renamed"))
        assertTrue(store.rename("missing", "nope").not())

        val renamed = store.loadAll().first { it.id == "a" }
        assertEquals("Alpha renamed", renamed.plan.title)
        assertEquals("Beta", store.loadAll().first { it.id == "b" }.plan.title)
    }

    @Test
    fun `rename ignores a blank title`() {
        val store = store()
        save(store, "a", "Alpha", savedAt = 100L)
        assertTrue(store.rename("a", "   ").not())
        assertEquals("Alpha", store.loadAll().first().plan.title)
    }

    @Test
    fun `delete removes a plan and returns it for undo`() {
        val store = store()
        save(store, "a", "Alpha", savedAt = 100L)
        save(store, "b", "Beta", savedAt = 200L)

        val removed = store.delete("a")
        assertEquals("a", removed?.id)
        assertEquals(listOf("b"), store.loadAll().map { it.id })
        assertNull(store.delete("a"))
    }

    @Test
    fun `undo re-saves a deleted plan under its original id`() {
        val store = store()
        val entry = save(store, "a", "Alpha", savedAt = 100L)
        store.delete("a")

        val restored = store.save(entry.brief, entry.plan, entry.savedAt, entry.id)
        assertEquals("a", restored.id)
        assertEquals(listOf("a"), store.loadAll().map { it.id })
    }

    @Test
    fun `only the newest plans are kept past the cap`() {
        val store = store()
        val cap = FilePlanStore.MAX_PLANS
        // Save cap + 5 plans; ids n0..n(cap+4) with increasing savedAt.
        for (i in 0 until cap + 5) {
            save(store, "n$i", "Plan $i", savedAt = i.toLong())
        }

        val kept = store.loadAll()
        assertEquals(cap, kept.size)
        // The five oldest (n0..n4) should have been dropped.
        assertTrue(kept.none { it.id == "n0" })
        assertTrue(kept.none { it.id == "n4" })
        // Newest is at the head.
        assertEquals("n${cap + 4}", kept.first().id)
    }

    @Test
    fun `a corrupt plans file reads back as empty instead of throwing`() {
        val dir = tempFolder.newFolder()
        val file = java.io.File(dir, FilePlanStore.FILE_NAME)
        file.writeText("{ this is not valid json ")
        assertTrue(FilePlanStore(dir).loadAll().isEmpty())
    }

    @Test
    fun `draft round trips through the store`() {
        val store = store()
        assertNull(store.loadDraft())
        store.saveDraft(Draft(idea = "half an idea"))
        assertEquals("half an idea", store.loadDraft()?.idea)
        store.clearDraft()
        assertNull(store.loadDraft())
    }

    @Test
    fun `writing leaves no temp file behind`() {
        val dir = tempFolder.newFolder()
        save(FilePlanStore(dir), "a", "Alpha", savedAt = 1L)
        val leftovers = dir.listFiles()?.map { it.name } ?: emptyList()
        assertTrue("unexpected temp files: $leftovers", leftovers.none { it.endsWith(".tmp") })
    }

    @Test
    fun `saved plan nodes are preserved through the round trip`() {
        val store = store()
        store.save(IdeaBrief("x"), SamplePlan.plan, savedAt = 1L, id = "p")
        val nodes: List<PlanNode> = store.loadAll().first().plan.nodes
        assertEquals(SamplePlan.plan.nodes, nodes)
    }
}
