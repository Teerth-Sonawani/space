package com.space.app.domain.generation

import com.space.app.domain.model.NodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanJsonDecoderTest {

    @Test
    fun `decodes a plain plan payload`() {
        val plan = PlanJsonDecoder.decode(validPlanJson).getOrThrow()

        assertEquals("Coffee cart plan", plan.title)
        assertEquals(6, plan.nodes.size)
        assertEquals(5, plan.edges.size)
    }

    @Test
    fun `decodes a fenced payload the way a chatty model answers`() {
        val plan = PlanJsonDecoder.decode("```json\n$validPlanJson\n```").getOrThrow()

        assertEquals("Coffee cart plan", plan.title)
    }

    @Test
    fun `maps node kinds and falls back to step for unknown values`() {
        val json = """
            {"title":"T","nodes":[
              {"id":"a","title":"Start","kind":"goal"},
              {"id":"b","title":"Weigh options","kind":"decision"},
              {"id":"c","title":"Watch out","kind":"risk"},
              {"id":"d","title":"Do a thing","kind":"banana"},
              {"id":"e","title":"Do more","kind":"step"},
              {"id":"f","title":"Finish","kind":"step"}
            ],"edges":[
              {"from":"a","to":"b"},{"from":"b","to":"c"},{"from":"c","to":"d"},
              {"from":"d","to":"e"},{"from":"e","to":"f"}
            ]}
        """.trimIndent()

        val nodes = PlanJsonDecoder.decode(json).getOrThrow().nodes

        assertEquals(
            listOf(
                NodeKind.GOAL,
                NodeKind.DECISION,
                NodeKind.RISK,
                NodeKind.STEP,
                NodeKind.STEP,
                NodeKind.STEP
            ),
            nodes.map { it.kind }
        )
    }

    @Test
    fun `ignores keys it does not know about`() {
        val json = """{"title":"T","futureField":42,"nodes":[],"edges":[]}"""
        val plan = PlanJsonDecoder.decode(json).getOrThrow()

        assertEquals("T", plan.title)
        assertTrue(plan.nodes.isEmpty())
    }

    @Test
    fun `an empty title falls back to Untitled plan`() {
        val json = """{"title":"   ","nodes":[],"edges":[]}"""
        assertEquals("Untitled plan", PlanJsonDecoder.decode(json).getOrThrow().title)
    }

    @Test
    fun `edge labels are preserved when present`() {
        val json = """
            {"title":"T","nodes":[
              {"id":"a","title":"A"},{"id":"b","title":"B"},{"id":"c","title":"C"},
              {"id":"d","title":"D"},{"id":"e","title":"E"},{"id":"f","title":"F"}
            ],"edges":[
              {"from":"a","to":"b","label":"then"},{"from":"b","to":"c"},
              {"from":"c","to":"d"},{"from":"d","to":"e"},{"from":"e","to":"f"}
            ]}
        """.trimIndent()

        val plan = PlanJsonDecoder.decode(json).getOrThrow()
        assertEquals("then", plan.edges.first().label)
    }

    @Test
    fun `garbage in is a failed result, not an exception`() {
        assertTrue(PlanJsonDecoder.decode("no json here").isFailure)
        assertTrue(PlanJsonDecoder.decode("").isFailure)
    }
}
