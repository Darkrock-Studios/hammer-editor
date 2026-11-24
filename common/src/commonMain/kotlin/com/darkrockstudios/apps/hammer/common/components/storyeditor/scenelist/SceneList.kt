package com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist

import androidx.compose.runtime.Immutable
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.nux.NuxHint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

interface SceneList {
	val state: Value<State>
	fun onSceneSelected(sceneDef: SceneItem)
	suspend fun moveScene(moveRequest: MoveRequest)
	fun loadScenes()
	suspend fun createScene(parent: SceneItem?, sceneName: String)
	suspend fun createGroup(parent: SceneItem?, groupName: String)
	suspend fun deleteScene(scene: SceneItem)
	suspend fun renameScene(scene: SceneItem, newName: String): Boolean

	fun onSceneListUpdate(scenes: SceneSummary)
	fun onSceneBufferUpdate(sceneBuffer: SceneBuffer)
	fun showOutlineOverview()
	fun showReaderMode()

	suspend fun archiveScene(scene: SceneItem)
	suspend fun unarchiveScene(scene: SceneItem)
	fun showArchivedScenes()
	fun dismissArchivedDialog()

	/** Marks [hint], the one of [State.offeredHints] the list showed, as seen; no other tip is offered on this visit unless tips are reset. */
	fun dismissHint(hint: NuxHint)

	/** Turns tips off everywhere, as the Show tips setting does. The tip on screen stays unseen. */
	fun turnOffTips()

	@Immutable
	data class State(
		val projectDef: ProjectDef,
		val selectedSceneItem: SceneItem? = null,
		val sceneSummary: SceneSummary? = null,
		val showArchivedDialog: Boolean = false,
		val archivedScenes: ImmutableList<SceneItem> = persistentListOf(),
		/** Coach marks the list may show on this visit, most wanted first; the UI shows the first it can point at. */
		val offeredHints: ImmutableList<NuxHint> = persistentListOf(),
	)
}
