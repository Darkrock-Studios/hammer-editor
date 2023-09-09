package com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.ComponentToasterImpl
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock


class SceneEditorComponent(
	componentContext: ComponentContext,
	originalSceneItem: SceneItem,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val closeSceneEditor: () -> Unit,
	private val showDraftsList: (SceneItem) -> Unit,
) : ProjectComponentBase(originalSceneItem.projectDef, componentContext),
	ComponentToaster by ComponentToasterImpl(),
	SceneEditor {

	private val sceneEditor: SceneEditorRepository by projectInject()
	private val draftsRepository: SceneDraftRepository by projectInject()

	private val mainDispatcher by injectMainDispatcher()

	private val _state = MutableValue(SceneEditor.State(sceneItem = originalSceneItem))
	override val state: Value<SceneEditor.State> = _state

	override var lastForceUpdate = MutableValue<Long>(0)
	private var bufferUpdateSubscription: Job? = null

	private val sceneDef: SceneItem
		get() = state.value.sceneItem

	init {
		loadSceneContent()

		subscribeToBufferUpdates()
	}

	private fun subscribeToBufferUpdates() {
		Napier.d { "SceneEditorComponent start collecting buffer updates" }

		bufferUpdateSubscription?.cancel()

		bufferUpdateSubscription =
			sceneEditor.subscribeToBufferUpdates(sceneDef, scope, ::onBufferUpdate)
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

		val menuItems = setOf(
			renameItem,
			saveItem,
			discardItem,
			deleteItem,
			draftsItem,
			saveDraftItem,
			closeItem
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
		withContext(mainDispatcher) {
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
			withContext(mainDispatcher) {
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