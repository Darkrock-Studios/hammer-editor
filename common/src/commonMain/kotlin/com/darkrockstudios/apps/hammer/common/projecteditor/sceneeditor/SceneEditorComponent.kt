package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.datetime.Clock
import org.koin.core.component.inject


class SceneEditorComponent(
    componentContext: ComponentContext,
    originalSceneItem: SceneItem,
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
    private val closeSceneEditor: () -> Unit,
    private val showDraftsList: (SceneItem) -> Unit,
) : ComponentBase(componentContext), SceneEditor {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(originalSceneItem.projectDef)

    private val draftsRepository: SceneDraftRepository by inject()

    private val _state = MutableValue(SceneEditor.State(sceneItem = originalSceneItem))
    override val state: Value<SceneEditor.State> = _state

    override var lastDiscarded = MutableValue<Long>(0)

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
            projectEditor.subscribeToBufferUpdates(sceneDef, scope, ::onBufferUpdate)
    }

    private fun onBufferUpdate(sceneBuffer: SceneBuffer) {
        _state.reduce {
            it.copy(sceneBuffer = sceneBuffer)
        }
    }

    override fun loadSceneContent() {
        _state.reduce {
            val buffer = projectEditor.loadSceneBuffer(sceneDef)
            it.copy(sceneBuffer = buffer)
        }
    }

    override fun storeSceneContent() =
        projectEditor.storeSceneBuffer(sceneDef)

    override fun onContentChanged(content: PlatformRichText) {
        projectEditor.onContentChanged(
            SceneContent(
                scene = sceneDef,
                platformRepresentation = content
            )
        )
    }

    override fun addEditorMenu() {
        val closeItem = MenuItemDescriptor("scene-editor-close", "Close", "") {
            Napier.d("Scene close selected")
            closeSceneEditor()
        }

        val saveItem = MenuItemDescriptor(
            "scene-editor-save",
            "Save",
            "",
            KeyShortcut(83L, ctrl = true)
        ) {
            Napier.d("Scene save selected")
            storeSceneContent()
        }

        val discardItem = MenuItemDescriptor(
            "scene-editor-discard",
            "Discard",
            ""
        ) {
            Napier.d("Scene buffer discard selected")
            projectEditor.discardSceneBuffer(sceneDef)
            lastDiscarded.value = Clock.System.now().epochSeconds
        }

        val renameItem = MenuItemDescriptor(
            "scene-editor-rename",
            "Rename",
            ""
        ) {
            Napier.d("Scene rename selected")
            beginSceneNameEdit()
        }

        val draftsItem = MenuItemDescriptor(
            "scene-editor-view-drafts",
            "Drafts",
            ""
        ) {
            Napier.i("View drafts")
            showDraftsList(sceneDef)
        }

        val saveDraftItem = MenuItemDescriptor(
            "scene-editor-save-draft",
            "Save Draft",
            ""
        ) {
            Napier.i("Save draft")
            beginSaveDraft()
        }

        val menu = MenuDescriptor(
            getMenuId(),
            "Scene",
            listOf(renameItem, saveItem, discardItem, draftsItem, saveDraftItem, closeItem)
        )
        addMenu(menu)
    }

    override fun removeEditorMenu() {
        removeMenu(getMenuId())
    }

    override fun beginSceneNameEdit() {
        _state.reduce { it.copy(isEditingName = true) }
    }

    override fun endSceneNameEdit() {
        _state.reduce { it.copy(isEditingName = false) }
    }

    override fun changeSceneName(newName: String) {
        endSceneNameEdit()
        projectEditor.renameScene(sceneDef, newName)

        _state.reduce {
            it.copy(
                sceneItem = it.sceneItem.copy(name = newName)
            )
        }
    }

    override fun beginSaveDraft() {
        _state.reduce { it.copy(isSavingDraft = true) }
    }

    override fun endSaveDraft() {
        _state.reduce { it.copy(isSavingDraft = false) }
    }

    override fun saveDraft(draftName: String): Boolean {
        return if (SceneDraftRepository.validDraftName(draftName)) {
            val draftDef = draftsRepository.saveDraft(
                sceneDef,
                draftName
            )
            if (draftDef != null) {
                Napier.i { "Draft Saved: ${draftDef.draftSequence}" }
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