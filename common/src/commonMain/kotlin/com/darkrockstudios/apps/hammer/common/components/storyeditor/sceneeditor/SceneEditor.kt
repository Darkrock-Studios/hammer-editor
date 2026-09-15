package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker

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
	suspend fun saveDraft(draftName: String, newDraftName: String): Boolean
	fun beginDelete()
	fun endDelete()
	fun doDelete()
	fun beginArchive()
	fun endArchive()
	fun doArchive()
	fun beginDiscard()
	fun endDiscard()
	fun doDiscard()
	/** Wide layouts: flips the persisted `GlobalSettings.metadataPanelVisible` preference. */
	fun toggleMetadataPanelVisible()

	/** Narrow layouts: flips the transient modal-state, no persistence. */
	fun toggleMetadataModal()

	/** Informs the component whether the current layout is wide (sidebar) or narrow (modal). */
	fun setLayoutMode(isWide: Boolean)

	fun decreaseTextSize()
	fun increaseTextSize()
	fun resetTextSize()

	/** Persists the user's editor max width in dp. Call on drag end, not per frame. */
	fun setEditorMaxWidth(width: Float)
	fun resetEditorMaxWidth()
	fun enterFocusMode()

	/** Adds a flagged word to the project's spell-check dictionary. */
	fun addWordToDictionary(word: String)

	data class State(
		val sceneItem: SceneItem,
		val sceneBuffer: SceneBuffer? = null,
		val isLoading: Boolean = true,
		val isEditingName: Boolean = false,
		val isSavingDraft: Boolean = false,
		val confirmDelete: Boolean = false,
		val confirmArchive: Boolean = false,
		val confirmDiscard: Boolean = false,
		/** Persistent preference; only consulted on wide layouts (in-line aside). */
		val metadataPanelVisible: Boolean = true,
		/** Transient; only consulted on narrow layouts where the panel renders as a Dialog. */
		val showMetadataModal: Boolean = false,
		val menuItems: Set<MenuItemDescriptor> = emptySet(),
		val textSize: Float = GlobalSettings.DEFAULT_FONT_SIZE,
		val editorMaxWidth: Float = GlobalSettings.DEFAULT_EDITOR_WIDTH,
		val spellChecker: PlatformSpellChecker? = null,
		val spellCheckingEnabled: Boolean = true,
	)
}