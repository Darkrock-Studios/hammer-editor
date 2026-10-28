package com.darkrockstudios.apps.hammer.common.components.duplicateids

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService.ResolveResult
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdSet
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DuplicateIdsComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val dismissDialog: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), DuplicateIds {

	private val duplicateIdService: DuplicateIdService by projectInject()

	private val _state = MutableValue(DuplicateIds.State())
	override val state: Value<DuplicateIds.State> = _state

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			duplicateIdService.unresolved.collect { sets ->
				withContext(dispatcherMain) { showSets(sets) }
			}
		}
	}

	private fun showSets(sets: List<DuplicateIdSet>) {
		_state.update { old ->
			val next = sets.firstOrNull()
			val sameSet = next != null && old.current?.let { it.kind == next.kind && it.id == next.id } == true
			val keep = when {
				next == null -> emptySet()
				sameSet -> old.keep.filterTo(mutableSetOf()) { path -> next.copies.any { it.path == path } }
				else -> setOf(next.inUse.path)
			}
			old.copy(sets = sets, keep = keep)
		}
	}

	override fun toggleKeep(path: HPath) {
		_state.update { old ->
			val current = old.current ?: return@update old
			val keep = when {
				path in old.keep -> old.keep - path
				else -> old.keep + path
			}
			old.copy(keep = keep, error = null)
		}
	}

	override fun keepSelected() {
		val current = _state.value
		val set = current.current ?: return
		if (current.keep.isEmpty() || current.working) return

		_state.update { it.copy(working = true, error = null) }
		scope.launch {
			val result = duplicateIdService.resolve(set, current.keep)
			withContext(dispatcherMain) {
				_state.update {
					it.copy(
						working = false,
						error = when (result) {
							ResolveResult.Resolved -> null
							ResolveResult.BackupFailed -> DuplicateIds.Error.BackupFailed
							ResolveResult.Stale -> DuplicateIds.Error.Stale
						},
					)
				}
				if (duplicateIdService.unresolved.value.isEmpty()) dismissDialog()
			}
		}
	}

	override fun decideLater() {
		dismissDialog()
	}
}
