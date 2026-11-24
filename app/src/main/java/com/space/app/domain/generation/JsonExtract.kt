package com.space.app.domain.generation

/**
 * Pulls a JSON object/array out of model text that may include markdown fences
 * or leading prose.
 */
object JsonExtract {
    fun extract(text: String): String {
        val trimmed = text.trim()
        val fenced = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
            .find(trimmed)
        val candidate = fenced?.groupValues?.getOrNull(1)?.trim() ?: trimmed
        val startObj = candidate.indexOf('{')
        val startArr = candidate.indexOf('[')
        val start = when {
            startObj < 0 -> startArr
            startArr < 0 -> startObj
            else -> minOf(startObj, startArr)
        }
        if (start < 0) return candidate
        val open = candidate[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        for (i in start until candidate.length) {
            when (candidate[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return candidate.substring(start, i + 1)
                }
            }
        }
        return candidate.substring(start)
    }
}
