package com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface SceneEditor : HammerComponent {
	val state: Value<State>
	var lastDiscarded: MutableValue<Long>

	fun addEditorMenu()
	fun removeEditorMenu()
	fun loadSceneContent()
	suspend fun storeSceneContent(): Boolean
	fun onContentChanged(content: PlatformRichText)
	fun beginSceneNameEdit()
	fun endSceneNameEdit()
	suspend fun changeSceneName(newName: String)
	fun beginSaveDraft()
	fun endSaveDraft()
	suspend fun saveDraft(draftName: String): Boolean

	data class State(
		val sceneItem: SceneItem,
		val sceneBuffer: SceneBuffer? = null,
		val isEditingName: Boolean = false,
		val isSavingDraft: Boolean = false
	)
}