package com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface SceneEditor : HammerComponent, ComponentToaster {
	val state: Value<State>
	var lastForceUpdate: MutableValue<Long>

	val sceneMetadataComponent: SceneMetadataPanel

	fun closeEditor()
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
	fun beginDelete()
	fun endDelete()
	fun doDelete()
	fun toggleMetadataVisibility()
	fun decreaseTextSize()
	fun increaseTextSize()
	fun resetTextSize()

	data class State(
		val sceneItem: SceneItem,
		val sceneBuffer: SceneBuffer? = null,
		val isEditingName: Boolean = false,
		val isSavingDraft: Boolean = false,
		val confirmDelete: Boolean = false,
		val showMetadata: Boolean = false,
		val menuItems: Set<MenuItemDescriptor> = emptySet(),
		val textSize: Float = GlobalSettings.DEFAULT_FONT_SIZE
	)
}