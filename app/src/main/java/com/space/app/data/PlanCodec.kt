package com.space.app.data

import com.space.app.domain.model.Answer
import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.model.Plan
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A plan as it sits on disk: the plan itself plus the brief that produced it,
 * so the question history travels with the plan.
 */
@Serializable
data class StoredPlan(
    val id: String,
    val brief: IdeaBrief,
    val plan: Plan,
    val savedAt: Long
)

/**
 * An in-progress flow: the idea so far and whatever has been answered. Kept
 * separate from [StoredPlan] so a half-finished idea survives a process death
 * without pretending to be a saved plan.
 */
@Serializable
data class Draft(
    val idea: String,
    val answers: List<Answer> = emptyList()
)

/**
 * The single place plans are turned into text and back. Keeping this separate
 * from the storage mechanism means the file store here and the Room store in M2
 * agree on the wire format.
 */
object PlanCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(plan: StoredPlan): String = json.encodeToString(plan)

    fun decode(text: String): StoredPlan = json.decodeFromString(text)

    fun encodeAll(plans: List<StoredPlan>): String = json.encodeToString(plans)

    fun decodeAll(text: String): List<StoredPlan> = json.decodeFromString(text)

    fun encodeDraft(draft: Draft): String = json.encodeToString(draft)

    fun decodeDraft(text: String): Draft = json.decodeFromString(text)
}
