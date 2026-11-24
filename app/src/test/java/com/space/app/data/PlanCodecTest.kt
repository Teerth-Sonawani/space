package com.space.app.data

import com.space.app.domain.model.Answer
import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.sample.SamplePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCodecTest {

    private val stored = StoredPlan(
        id = "plan-1",
        brief = IdeaBrief(
            idea = "Launch a coffee cart",
            answers = listOf(Answer("Who is this for?", "Customers"))
        ),
        plan = SamplePlan.plan,
        savedAt = 1_700_000_000_000L
    )

    @Test
    fun `round trips a stored plan unchanged`() {
        assertEquals(stored, PlanCodec.decode(PlanCodec.encode(stored)))
    }

    @Test
    fun `round trips a list of plans`() {
        val list = listOf(stored, stored.copy(id = "plan-2", savedAt = 2L))
        assertEquals(list, PlanCodec.decodeAll(PlanCodec.encodeAll(list)))
    }

    @Test
    fun `round trips an in-progress draft`() {
        val draft = Draft(
            idea = "Learn to bake sourdough",
            answers = listOf(Answer("Who is this for?", "Just me"))
        )
        assertEquals(draft, PlanCodec.decodeDraft(PlanCodec.encodeDraft(draft)))
    }

    @Test
    fun `a draft with no answers yet still round trips`() {
        val draft = Draft(idea = "half an idea")
        assertEquals(draft, PlanCodec.decodeDraft(PlanCodec.encodeDraft(draft)))
    }

    @Test
    fun `an unknown field from a newer version does not break decoding`() {
        val json = """
            {
              "id": "plan-1",
              "brief": { "idea": "x", "answers": [] },
              "plan": { "title": "t", "nodes": [], "edges": [] },
              "savedAt": 5,
              "somethingAddedLater": { "nested": true }
            }
        """.trimIndent()

        val decoded = PlanCodec.decode(json)
        assertEquals("plan-1", decoded.id)
        assertEquals("t", decoded.plan.title)
        assertTrue(decoded.plan.nodes.isEmpty())
    }
}
