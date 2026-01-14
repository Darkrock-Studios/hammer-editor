package com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.*

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

	suspend fun archiveScene(scene: SceneItem)
	suspend fun unarchiveScene(scene: SceneItem)
	fun showArchivedScenes()
	fun dismissArchivedDialog()

	data class State(
		val projectDef: ProjectDef,
		val selectedSceneItem: SceneItem? = null,
		val sceneSummary: SceneSummary? = null,
		val showArchivedDialog: Boolean = false,
		val archivedScenes: List<SceneItem> = emptyList()
	)
}
