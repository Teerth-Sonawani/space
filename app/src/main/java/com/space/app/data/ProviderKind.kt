package com.space.app.data

import kotlinx.serialization.Serializable

/** Which vendor backs an API source. Offline starter is not a key source. */
@Serializable
enum class ProviderKind {
    GEMINI,
    GROQ,
    OPENROUTER
}

/** Display name for Settings and health chips. */
fun ProviderKind.displayName(): String = when (this) {
    ProviderKind.GEMINI -> "Gemini"
    ProviderKind.GROQ -> "Groq"
    ProviderKind.OPENROUTER -> "OpenRouter"
}
