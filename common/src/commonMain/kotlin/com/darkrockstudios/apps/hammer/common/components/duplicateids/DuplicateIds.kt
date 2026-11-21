package com.darkrockstudios.apps.hammer.common.components.duplicateids

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdSet
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface DuplicateIds : HammerComponent {
	val state: Value<State>

	/** For a set that allows only one copy, selects [path] instead of toggling it. */
	fun toggleKeep(path: HPath)
	fun keepSelected()
	fun decideLater()

	data class State(
		val sets: List<DuplicateIdSet> = emptyList(),
		val keep: Set<HPath> = emptySet(),
		val working: Boolean = false,
		val error: Error? = null,
	) {
		val current: DuplicateIdSet? get() = sets.firstOrNull()
	}

	enum class Error {
		BackupFailed,
		Stale,
	}
}
