package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Keeps a project's sync and the write operations on it from overlapping. Writes run alongside each
 * other; a sync waits for those in progress, then runs alone. A write never waits: one that finds
 * its project syncing is refused, so nothing a sync triggers can deadlock on it.
 *
 * Keyed by path, as project scopes are, so an id healed or re-minted meanwhile changes nothing.
 */
class ProjectSyncGateRepository {

	private val state = MutableStateFlow(Gate())

	/** Runs [block] once no write on [projectDef] is in progress, refusing new ones until it returns. */
	suspend fun <T> withSync(projectDef: ProjectDef, block: suspend () -> T): T {
		val key = keyOf(projectDef)
		do {
			val free = state.first { key !in it.syncing && key !in it.writes }
		} while (!state.compareAndSet(free, free.copy(syncing = free.syncing + key)))

		try {
			return block()
		} finally {
			state.update { it.copy(syncing = it.syncing - key) }
		}
	}

	/** Counts a write on [projectDef] as in progress, or returns false, counting nothing, while it syncs. */
	fun beginWrite(projectDef: ProjectDef): Boolean {
		val key = keyOf(projectDef)
		var admitted = false
		state.update { gate ->
			admitted = key !in gate.syncing
			if (admitted) gate.copy(writes = gate.writes + (key to (gate.writes[key] ?: 0) + 1)) else gate
		}
		return admitted
	}

	/** Ends a write [beginWrite] admitted. */
	fun endWrite(projectDef: ProjectDef) {
		val key = keyOf(projectDef)
		state.update { gate ->
			val left = (gate.writes[key] ?: 0) - 1
			gate.copy(writes = if (left > 0) gate.writes + (key to left) else gate.writes - key)
		}
	}

	private fun keyOf(projectDef: ProjectDef): String = projectDef.path.path

	private data class Gate(
		val syncing: Set<String> = emptySet(),
		/** Writes in progress per project; a project with none is absent. */
		val writes: Map<String, Int> = emptyMap(),
	)
}
