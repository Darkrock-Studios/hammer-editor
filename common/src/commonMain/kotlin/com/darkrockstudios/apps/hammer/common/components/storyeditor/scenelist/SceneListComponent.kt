package com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.retainedInstance
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.nux.NuxHint
import com.darkrockstudios.apps.hammer.common.data.nux.NuxRepository
import com.darkrockstudios.apps.hammer.common.data.nux.NuxVisit
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class SceneListComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	selectedSceneItem: SharedFlow<SceneItem?>,
	private val sceneSelected: (sceneDef: SceneItem) -> Unit,
	private val openSceneEditor: (sceneDef: SceneItem) -> Unit,
	private val showOutlineOverviewDialog: () -> Unit,
	private val showReader: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), SceneList {

	private val projectEditor: SceneEditorService by projectInject()
	private val nuxRepository: NuxRepository by inject()

	private val _state = MutableValue(SceneList.State(projectDef = projectDef))
	override val state: Value<SceneList.State> = _state

	private val hasRows = MutableStateFlow(false)

	/** Retained so a configuration change neither rolls again nor forgets a dismissal. */
	private val visit = retainedInstance(key = TIPS_VISIT_KEY) { TipsVisit(nuxRepository.startVisit()) }

	init {
		Napier.d { "Project editor: " + projectEditor.projectDef.name }

		loadScenes()
		watchHints()

		watchSelectedScene(selectedSceneItem)

		projectEditor.subscribeToSceneUpdates(scope, ::onSceneListUpdate)
		projectEditor.subscribeToBufferUpdates(null, scope, ::onSceneBufferUpdate)
	}

	private fun watchSelectedScene(selectedSceneItem: SharedFlow<SceneItem?>) {
		scope.launch {
			selectedSceneItem.collect { scene ->
				withContext(dispatcherMain) {
					Napier.d("Scene Selected: $scene")
					_state.getAndUpdate { it.copy(selectedSceneItem = scene) }
				}
			}
		}
	}

	override fun onSceneSelected(sceneDef: SceneItem) {
		sceneSelected(sceneDef)
		_state.getAndUpdate {
			it.copy(selectedSceneItem = sceneDef)
		}
	}

	override suspend fun moveScene(moveRequest: MoveRequest) {
		projectEditor.moveScene(moveRequest)
	}

	override fun loadScenes() {
		setSummary(projectEditor.getSceneSummaries())
	}

	private fun setSummary(scenes: SceneSummary) {
		_state.getAndUpdate { it.copy(sceneSummary = scenes) }
		hasRows.value = scenes.sceneTree.root.children.isNotEmpty()
	}

	private fun watchHints() {
		val offer = combine(nuxRepository.visitHints(LIST_HINTS, visit.nuxVisit), hasRows) { unseen, rows ->
			if (rows) unseen else emptyList()
		}

		scope.launch {
			offer.distinctUntilChanged().collect { hints ->
				withContext(dispatcherMain) {
					// Only a reset makes a dismissed hint unseen again; an update worked out before the dismissal does not.
					val dismissed = visit.dismissedHint
					if (dismissed != null && dismissed in hints && nuxRepository.isUnseen(dismissed)) visit.dismissedHint = null
					val offered = if (visit.dismissedHint == null) hints.toImmutableList() else persistentListOf()
					_state.getAndUpdate { it.copy(offeredHints = offered) }
				}
			}
		}
	}

	override fun dismissHint(hint: NuxHint) {
		if (visit.dismissedHint != null || hint !in _state.value.offeredHints) return
		visit.dismissedHint = hint
		_state.getAndUpdate { it.copy(offeredHints = persistentListOf()) }
		nuxRepository.markSeen(hint)
	}

	override fun turnOffTips() {
		_state.getAndUpdate { it.copy(offeredHints = persistentListOf()) }
		nuxRepository.setTipsEnabled(false)
	}

	override suspend fun createScene(parent: SceneItem?, sceneName: String) {
		val foundParent = if (parent?.isRootScene == true) {
			null
		} else {
			parent
		}

		val newSceneItem = projectEditor.createScene(foundParent, sceneName)
		if (newSceneItem != null) {
			Napier.i("Scene created: $sceneName")
			openSceneEditor(newSceneItem)
		} else {
			Napier.w("Failed to create Scene: $sceneName")
		}
	}

	override suspend fun createGroup(parent: SceneItem?, groupName: String) {
		val foundParent = if (parent?.isRootScene == true) {
			null
		} else {
			parent
		}

		val newSceneItem = projectEditor.createGroup(foundParent, groupName)
		if (newSceneItem != null) {
			Napier.i("Group created: $groupName")
		} else {
			Napier.w("Failed to create Group: $groupName")
		}
	}

	override suspend fun deleteScene(scene: SceneItem) {
		when (scene.type) {
			SceneItem.Type.Scene -> {
				if (!projectEditor.deleteScene(scene)) {
					Napier.e("Failed to delete Scene: ${scene.id} - ${scene.name}")
				}
			}

			SceneItem.Type.Group -> {
				if (!projectEditor.deleteGroup(scene)) {
					Napier.e("Failed to delete Scene: ${scene.id} - ${scene.name}")
				}
			}

			SceneItem.Type.Root -> throw IllegalStateException("Cannot delete Root")
		}
	}

	override suspend fun renameScene(scene: SceneItem, newName: String): Boolean {
		return if (!projectEditor.renameScene(scene, newName)) {
			Napier.e("Failed to rename Scene: ${scene.id} - ${scene.name} to $newName")
			false
		} else {
			true
		}
	}

	override fun onSceneListUpdate(scenes: SceneSummary) {
		setSummary(scenes)
	}

	override fun onSceneBufferUpdate(sceneBuffer: SceneBuffer) {
		_state.getAndUpdate { oldState ->
			val summary = oldState.sceneSummary ?: return@getAndUpdate oldState
			val updated = if (sceneBuffer.dirty) {
				summary.hasDirtyBuffer.add(sceneBuffer.content.scene.id)
			} else {
				summary.hasDirtyBuffer.remove(sceneBuffer.content.scene.id)
			}

			oldState.copy(
				sceneSummary = summary.copy(
					hasDirtyBuffer = updated
				)
			)
		}
	}

	override fun showOutlineOverview() {
		showOutlineOverviewDialog()
	}

	override fun showReaderMode() {
		showReader()
	}

	override suspend fun archiveScene(scene: SceneItem) {
		if (projectEditor.archiveScene(scene)) {
			Napier.i("Scene archived: ${scene.name}")
		} else {
			Napier.w("Failed to archive scene: ${scene.name}")
		}
	}

	override suspend fun unarchiveScene(scene: SceneItem) {
		val unarchived = projectEditor.unarchiveScene(scene)
		if (unarchived != null) {
			Napier.i("Scene unarchived: ${unarchived.name}")
			openSceneEditor(unarchived)
			dismissArchivedDialog()
		} else {
			Napier.w("Failed to unarchive scene: ${scene.name}")
		}
	}

	override fun showArchivedScenes() {
		val archived = projectEditor.getArchivedScenes().toImmutableList()
		_state.getAndUpdate {
			it.copy(
				showArchivedDialog = true,
				archivedScenes = archived
			)
		}
	}

	override fun dismissArchivedDialog() {
		_state.getAndUpdate {
			it.copy(showArchivedDialog = false)
		}
	}
}

/** The scene list's tips, most wanted first; only the menu tip for this platform is offered. */
private val LIST_HINTS = listOf(NuxHint.SceneListSwipeMenu, NuxHint.SceneListContextMenu, NuxHint.SceneListReorder)

private const val TIPS_VISIT_KEY = "SceneListComponent.tipsVisit"

/** One visit's tips state. [dismissedHint] blocks any other tip until a reset, so a visit shows at most one. Main thread only. */
private class TipsVisit(val nuxVisit: NuxVisit) : InstanceKeeper.Instance {
	var dismissedHint: NuxHint? = null
}
