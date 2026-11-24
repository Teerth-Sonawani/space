package com.space.app.data

import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.model.Plan
import java.io.File
import java.util.UUID

/**
 * The contract for plan storage. Implementations handle persistence.
 *
 * Reads and writes are small (a handful of short plans) but still touch disk,
 * so callers should treat every method as potentially slow and avoid invoking
 * them on the main thread in production code.
 */
interface PlanStore {

    fun loadAll(): List<StoredPlan>

    fun mostRecent(): StoredPlan? = loadAll().firstOrNull()

    fun save(
        brief: IdeaBrief,
        plan: Plan,
        savedAt: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString()
    ): StoredPlan

    /** Retitles a saved plan. Returns true when a plan with [id] existed. */
    fun rename(id: String, title: String): Boolean

    /** Removes a saved plan and returns it, so the caller can offer an undo. */
    fun delete(id: String): StoredPlan?

    fun saveDraft(draft: Draft)

    fun loadDraft(): Draft?

    fun clearDraft()
}

/**
 * File-backed storage for plans, newest first.
 *
 * Takes a lazily-resolved directory rather than a [android.content.Context] so
 * the store can be exercised on the JVM against a temporary folder, and so the
 * directory is only touched inside the guarded read/write paths (the context's
 * `filesDir` can fail on a bare test double). In the app it is constructed with
 * `FilePlanStore { application.filesDir }`.
 *
 * This is deliberately small: it makes generated plans survive a restart now,
 * while a Room-backed library arrives later. All failure modes are swallowed
 * into an empty list on read, because losing the history must never take the
 * app down. Writes go through a temp-file-then-rename so a crash mid-write
 * cannot corrupt the single source of truth.
 */
class FilePlanStore(private val dirProvider: () -> File) : PlanStore {

    constructor(dir: File) : this({ dir })

    private val file: File
        get() = File(dirProvider(), FILE_NAME)

    private val draftFile: File
        get() = File(dirProvider(), DRAFT_FILE_NAME)

    override fun loadAll(): List<StoredPlan> {
        return try {
            val text = file.takeIf { it.exists() }?.readText().orEmpty()
            if (text.isBlank()) emptyList() else PlanCodec.decodeAll(text)
        } catch (_: Exception) {
            emptyList()
        }.sortedByDescending { it.savedAt }
    }

    override fun save(
        brief: IdeaBrief,
        plan: Plan,
        savedAt: Long,
        id: String
    ): StoredPlan {
        val entry = StoredPlan(id = id, brief = brief, plan = plan, savedAt = savedAt)
        // Prepend the new plan, then keep only the newest MAX_PLANS.
        val updated = (listOf(entry) + loadAll()).take(MAX_PLANS)
        writeAll(updated)
        return entry
    }

    override fun rename(id: String, title: String): Boolean {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return false
        val all = loadAll()
        var changed = false
        val updated = all.map { stored ->
            if (stored.id == id) {
                changed = true
                stored.copy(plan = stored.plan.copy(title = trimmed))
            } else {
                stored
            }
        }
        if (changed) writeAll(updated)
        return changed
    }

    override fun delete(id: String): StoredPlan? {
        val all = loadAll()
        val removed = all.firstOrNull { it.id == id } ?: return null
        writeAll(all.filterNot { it.id == id })
        return removed
    }

    override fun saveDraft(draft: Draft) {
        write(draftFile, PlanCodec.encodeDraft(draft))
    }

    override fun loadDraft(): Draft? {
        return try {
            val text = draftFile.takeIf { it.exists() }?.readText().orEmpty()
            if (text.isBlank()) null else PlanCodec.decodeDraft(text)
        } catch (_: Exception) {
            null
        }
    }

    override fun clearDraft() {
        try {
            draftFile.delete()
        } catch (_: Exception) {
            // Never crash while clearing a draft.
        }
    }

    /** Writes the whole plan list atomically; a failure must never crash. */
    private fun writeAll(plans: List<StoredPlan>) {
        write(file, PlanCodec.encodeAll(plans))
    }

    private fun write(target: File, text: String) {
        try {
            val directory = dirProvider()
            if (!directory.exists()) directory.mkdirs()
            val tmp = File(directory, target.name + ".tmp")
            tmp.writeText(text)
            // renameTo replaces atomically on the same filesystem.
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
        } catch (_: Exception) {
            // Writing the history must never crash the app.
        }
    }

    companion object {
        const val FILE_NAME = "plans.json"
        const val DRAFT_FILE_NAME = "draft.json"
        const val MAX_PLANS = 50
    }
}

