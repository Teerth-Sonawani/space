package com.space.app.domain.generation

import kotlinx.coroutines.delay

/**
 * Scripted [LlmClient] for tests: each call consumes the next result, and the
 * last one repeats once the queue is drained. Records the call count so tests
 * can assert "retried once" or "never touched the second key".
 */
class FakeLlmClient(vararg results: LlmResult) : LlmClient {

    private val queue = ArrayDeque(results.map { it })

    var calls: Int = 0
        private set

    var lastRequest: LlmRequest? = null
        private set

    override suspend fun complete(request: LlmRequest): LlmResult {
        calls++
        lastRequest = request
        return queue.removeFirstOrNull() ?: LlmResult.Timeout
    }
}

/** A client that never answers in time, for per-attempt timeout coverage. */
class HangingLlmClient(private val hangMs: Long) : LlmClient {
    var calls: Int = 0
        private set

    override suspend fun complete(request: LlmRequest): LlmResult {
        calls++
        delay(hangMs)
        return LlmResult.Ok("{}")
    }
}

/** A model answer that parses into the given question list. */
fun questionsJson(count: Int = 3): String = buildString {
    append("""{"questions":[""")
    (1..count).forEachIndexed { i, n ->
        if (i > 0) append(',')
        append("""{"id":"q$n","prompt":"Question number $n?","suggestions":["a","b"]}""")
    }
    append("]}")
}

/** A plan that passes [com.space.app.domain.validation.PlanValidator] untouched. */
val validPlanJson: String = """
    {
      "title": "Coffee cart plan",
      "nodes": [
        {"id": "n1", "title": "Pick a spot", "kind": "goal"},
        {"id": "n2", "title": "Check permits", "kind": "step"},
        {"id": "n3", "title": "Buy the cart", "kind": "step"},
        {"id": "n4", "title": "Test the menu", "kind": "step"},
        {"id": "n5", "title": "Watch the risks", "kind": "risk"},
        {"id": "n6", "title": "Open for a day", "kind": "step"}
      ],
      "edges": [
        {"from": "n1", "to": "n2"},
        {"from": "n2", "to": "n3"},
        {"from": "n3", "to": "n4"},
        {"from": "n4", "to": "n5"},
        {"from": "n5", "to": "n6"}
      ]
    }
""".trimIndent()
