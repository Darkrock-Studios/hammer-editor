package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.ComponentToasterImpl
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanelComponent
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings.Companion.DEFAULT_FONT_SIZE
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.koin.core.component.inject

class SceneEditorComponent(
	componentContext: ComponentContext,
	originalSceneItem: SceneItem,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val closeSceneEditor: () -> Unit,
	private val showDraftsList: (SceneItem) -> Unit,
	private val showFocusMode: (SceneItem) -> Unit,
) : ProjectComponentBase(originalSceneItem.projectDef, componentContext),
	ComponentToaster by ComponentToasterImpl(),
	SceneEditor {

	private val settingsRepository: GlobalSettingsRepository by inject()
	private val sceneEditor: SceneEditorRepository by projectInject()
	private val draftsRepository: SceneDraftRepository by projectInject()

	private val _state = MutableValue(SceneEditor.State(sceneItem = originalSceneItem))
	override val state: Value<SceneEditor.State> = _state

	override var lastForceUpdate = MutableValue<Long>(0)
	private var bufferUpdateSubscription: Job? = null

	override val sceneMetadataComponent: SceneMetadataPanel = SceneMetadataPanelComponent(
		componentContext = childContext("scene-${originalSceneItem.id}-metadata"),
		originalSceneItem = originalSceneItem
	)

	private val sceneDef: SceneItem = state.value.sceneItem

	override fun onCreate() {
		super.onCreate()

		loadSceneContent()
		subscribeToBufferUpdates()
		watchSettings()
		sceneEditor.subscribeToSceneUpdates(scope, ::onSceneTreeUpdate)
	}

	private fun onSceneTreeUpdate(sceneSummary: SceneSummary) {
		val newSceneItem = sceneSummary.sceneTree.findBy { it.id == sceneDef.id }
		if (newSceneItem != null) {
			_state.getAndUpdate {
				it.copy(
					sceneItem = newSceneItem.value
				)
			}
		} else {
			Napier.e("Scene ${sceneDef.id} no longer exists in the tree, this are probably going to break.")
		}
	}

	private fun watchSettings() {
		scope.launch {
			settingsRepository.globalSettingsUpdates.collect { settings ->
				if (settings.editorFontSize != _state.value.textSize) {
					withContext(dispatcherMain) {
						_state.getAndUpdate {
							it.copy(
								textSize = settings.editorFontSize
							)
						}
					}
				}
			}
		}
	}

	private fun subscribeToBufferUpdates() {
		Napier.d { "SceneEditorComponent start collecting buffer updates" }

		bufferUpdateSubscription?.cancel()
		bufferUpdateSubscription =
			sceneEditor.subscribeToBufferUpdates(sceneDef, scope, ::onBufferUpdate)
	}

	override fun onDestroy() {
		super.onDestroy()
		bufferUpdateSubscription?.cancel()
		bufferUpdateSubscription = null
	}

	private suspend fun onBufferUpdate(sceneBuffer: SceneBuffer) = withContext(dispatcherMain) {
		_state.getAndUpdate {
			it.copy(sceneBuffer = sceneBuffer)
		}

		if (sceneBuffer.source != UpdateSource.Editor) {
			forceUpdate()
		}
	}

	override fun loadSceneContent() {
		_state.getAndUpdate {
			val buffer = sceneEditor.loadSceneBuffer(sceneDef)
			it.copy(sceneBuffer = buffer)
		}
	}

	override suspend fun storeSceneContent() =
		sceneEditor.storeSceneBuffer(sceneDef)

	override fun onContentChanged(content: PlatformRichText) {
		sceneEditor.onContentChanged(
			SceneContent(
				scene = sceneDef,
				platformRepresentation = content
			),
			UpdateSource.Editor
		)
	}

	override fun addEditorMenu() {
		val closeItem = MenuItemDescriptor(
			"scene-editor-close",
			MR.strings.scene_editor_menu_item_close,
			""
		) {
			Napier.d("Scene close selected")
			closeSceneEditor()
		}

		val saveItem = MenuItemDescriptor(
			"scene-editor-save",
			MR.strings.scene_editor_menu_item_save,
			"",
			KeyShortcut(83, ctrl = true)
		) {
			Napier.d("Scene save selected")
			scope.launch { storeSceneContent() }
		}

		val discardItem = MenuItemDescriptor(
			"scene-editor-discard",
			MR.strings.scene_editor_menu_item_discard,
			""
		) {
			Napier.d("Scene buffer discard selected")
			sceneEditor.discardSceneBuffer(sceneDef)
			forceUpdate()
		}

		val renameItem = MenuItemDescriptor(
			"scene-editor-rename",
			MR.strings.scene_editor_menu_item_rename,
			""
		) {
			Napier.d("Scene rename selected")
			beginSceneNameEdit()
		}

		val deleteItem = MenuItemDescriptor(
			"scene-editor-delete",
			MR.strings.scene_editor_menu_item_delete,
			""
		) {
			Napier.i("Scene delete selected")
			beginDelete()
		}

		val draftsItem = MenuItemDescriptor(
			"scene-editor-view-drafts",
			MR.strings.scene_editor_menu_item_view_drafts,
			""
		) {
			Napier.i("View drafts")
			showDraftsList(sceneDef)
		}

		val saveDraftItem = MenuItemDescriptor(
			"scene-editor-save-draft",
			MR.strings.scene_editor_menu_item_save_draft,
			""
		) {
			Napier.i("Save draft")
			beginSaveDraft()
		}

		val metadataItem = MenuItemDescriptor(
			"scene-editor-toggle-metadata",
			MR.strings.scene_editor_metadata_button,
			""
		) {
			Napier.i("Toggle Metadata")
			toggleMetadataVisibility()
		}

		val focusModeItem = MenuItemDescriptor(
			"scene-editor-focus-mode",
			MR.strings.scene_editor_focus_mode_button,
			""
		) {
			Napier.i("Enter Focus Mode")
			enterFocusMode()
		}

		val menuItems = setOf(
			renameItem,
			saveItem,
			discardItem,
			deleteItem,
			draftsItem,
			saveDraftItem,
			metadataItem,
			focusModeItem,
			closeItem,
		)
		val menu = MenuDescriptor(
			getMenuId(),
			MR.strings.scene_editor_menu_group,
			menuItems.toList()
		)
		addMenu(menu)
		_state.getAndUpdate {
			it.copy(
				menuItems = menuItems
			)
		}
	}

	private fun forceUpdate() {
		lastForceUpdate.value = Clock.System.now().epochSeconds
	}

	override fun removeEditorMenu() {
		removeMenu(getMenuId())
		_state.getAndUpdate {
			it.copy(
				menuItems = emptySet()
			)
		}
	}

	override fun beginSceneNameEdit() {
		_state.getAndUpdate { it.copy(isEditingName = true) }
	}

	override fun endSceneNameEdit() {
		_state.getAndUpdate { it.copy(isEditingName = false) }
	}

	override suspend fun changeSceneName(newName: String) {
		withContext(dispatcherMain) {
			val result = ProjectsRepository.validateFileName(newName)

			if (isSuccess(result)) {
				endSceneNameEdit()
				sceneEditor.renameScene(sceneDef, newName)

				_state.getAndUpdate {
					it.copy(
						sceneItem = it.sceneItem.copy(name = newName)
					)
				}
			} else {
				result.displayMessage?.let { msg ->
					showToast(msg)
				}
			}
		}
	}

	override fun beginDelete() {
		_state.getAndUpdate { it.copy(confirmDelete = true) }
	}

	override fun endDelete() {
		_state.getAndUpdate { it.copy(confirmDelete = false) }
	}

	override fun doDelete() {
		scope.launch {
			sceneEditor.deleteScene(state.value.sceneItem)
			withContext(dispatcherMain) {
				endDelete()
				closeSceneEditor()
			}
		}
	}

	override fun beginSaveDraft() {
		_state.getAndUpdate { it.copy(isSavingDraft = true) }
	}

	override fun endSaveDraft() {
		_state.getAndUpdate { it.copy(isSavingDraft = false) }
	}

	override suspend fun saveDraft(draftName: String): Boolean {
		return if (SceneDraftRepository.validDraftName(draftName)) {
			val draftDef = draftsRepository.saveDraft(
				sceneDef,
				draftName
			)
			if (draftDef != null) {
				Napier.i { "Draft Saved: ${draftDef.draftTimestamp}" }
				true
			} else {
				Napier.e { "Failed to save Draft!" }
				false
			}
		} else {
			Napier.w { "Failed to save Draft, invalid name: $draftName" }
			false
		}
	}

	override fun closeEditor() {
		closeSceneEditor()
	}

	override fun toggleMetadataVisibility() {
		_state.getAndUpdate {
			it.copy(
				showMetadata = it.showMetadata.not()
			)
		}
	}

	override fun resetTextSize() {
		scope.launch {
			settingsRepository.updateSettings {
				it.copy(
					editorFontSize = DEFAULT_FONT_SIZE
				)
			}
		}
	}

	override fun enterFocusMode() {
		showFocusMode(sceneDef)
	}

	override fun decreaseTextSize() {
		scope.launch {
			val size = decreaseEditorTextSize(state.value.textSize)
			settingsRepository.updateSettings {
				it.copy(
					editorFontSize = size
				)
			}
		}
	}

	override fun increaseTextSize() {
		scope.launch {
			val size = increaseEditorTextSize(state.value.textSize)
			settingsRepository.updateSettings {
				it.copy(
					editorFontSize = size
				)
			}
		}
	}

	private fun getMenuId(): String {
		return "scene-editor-${sceneDef.id}-${sceneDef.name}"
	}

	override fun onStart() {
		addEditorMenu()
	}

	override fun onStop() {
		removeEditorMenu()
	}
}