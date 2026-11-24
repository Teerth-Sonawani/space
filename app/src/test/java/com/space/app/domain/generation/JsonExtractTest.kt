package com.space.app.domain.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExtractTest {

    @Test
    fun `plain json passes through untouched`() {
        val raw = """{"a":1}"""
        assertEquals(raw, JsonExtract.extract(raw))
    }

    @Test
    fun `json inside a markdown fence is unwrapped`() {
        val raw = "```json\n{\"a\":1}\n```"
        assertEquals("""{"a":1}""", JsonExtract.extract(raw))
    }

    @Test
    fun `fence without a language tag is also unwrapped`() {
        val raw = "```\n{\"a\":1}\n```"
        assertEquals("""{"a":1}""", JsonExtract.extract(raw))
    }

    @Test
    fun `leading prose before the json is dropped`() {
        val raw = "Sure! Here is the plan you asked for:\n{\"a\":1}"
        assertEquals("""{"a":1}""", JsonExtract.extract(raw))
    }

    @Test
    fun `json after a fenced block is not glued to trailing commentary`() {
        val raw = "Here you go:\n```json\n{\"a\":1}\n```\nHope that helps!"
        assertEquals("""{"a":1}""", JsonExtract.extract(raw))
    }

    @Test
    fun `nested braces are matched rather than cut at the first close`() {
        val raw = "noise {\"a\":{\"b\":2}} trailing"
        assertEquals("""{"a":{"b":2}}""", JsonExtract.extract(raw))
    }

    @Test
    fun `array payloads are extracted too`() {
        val raw = "answer: [1,2,3] done"
        assertEquals("[1,2,3]", JsonExtract.extract(raw))
    }

    @Test
    fun `text with no json at all is returned as-is`() {
        val raw = "I cannot help with that."
        assertEquals(raw, JsonExtract.extract(raw))
    }

    @Test
    fun `unterminated json is returned from the opening brace rather than throwing`() {
        val out = JsonExtract.extract("{\"a\":1")
        assertTrue(out.startsWith("{\"a\":1"))
    }
}
